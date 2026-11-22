package com.example.captionstudio.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * On-device ML Kit Selfie Segmentation processor for behind-subject kinetic typography.
 * Produces compact feathered alpha masks so `DST_OUT` compositing erases text behind the
 * speaker's head/shoulders while keeping native full-res video crisp on top.
 */
object SubjectMaskProcessor {

  private const val MASK_MAX_DIM = 224
  private const val CACHE_BUCKET_MS = 50L

  private val streamSegmenter: Segmenter by lazy {
    val options =
      SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
        .build()
    Segmentation.getClient(options)
  }

  private val stillSegmenter: Segmenter by lazy {
    val options =
      SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
        .build()
    Segmentation.getClient(options)
  }

  private val maskCache =
    object : LruCache<String, Bitmap>(28) {
      override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bitmap?, newValue: Bitmap?) {
        // Let GC collect evicted small mask bitmaps safely (avoid recycling while Canvas might draw)
      }
    }

  fun bucketForPosition(positionMs: Long): Long = (positionMs.coerceAtLeast(0L)) / CACHE_BUCKET_MS

  fun getCachedMask(videoKey: String, positionMs: Long): Bitmap? {
    val bucket = bucketForPosition(positionMs)
    maskCache.get("$videoKey:$bucket")?.let { return it }
    // Look up neighboring 50ms bucket to keep scrubbing/playback jitter-free
    return maskCache.get("$videoKey:${(bucket - 1).coerceAtLeast(0L)}")
      ?: maskCache.get("$videoKey:${bucket + 1}")
  }

  fun clearCache() {
    maskCache.evictAll()
  }

  suspend fun generateAndCacheMask(
    videoKey: String,
    positionMs: Long,
    frameBitmap: Bitmap,
  ): Bitmap? =
    withContext(Dispatchers.Default) {
      val bucket = bucketForPosition(positionMs)
      val cacheId = "$videoKey:$bucket"
      maskCache.get(cacheId)?.let { return@withContext it }

      val downscaled = downscaleForSegmentation(frameBitmap, MASK_MAX_DIM)
      val mask = runSegmenterAsync(streamSegmenter, downscaled)
      if (downscaled !== frameBitmap) downscaled.recycle()
      if (mask != null) {
        maskCache.put(cacheId, mask)
      }
      mask
    }

  /**
   * Synchronous mask helper for VideoAnalysisService & Media3 export pipeline threads.
   */
  fun generateMaskBlocking(frameBitmap: Bitmap, useStillMode: Boolean = true): Bitmap? {
    val downscaled = downscaleForSegmentation(frameBitmap, MASK_MAX_DIM)
    val latch = CountDownLatch(1)
    var outMask: Bitmap? = null
    val segmenter = if (useStillMode) stillSegmenter else streamSegmenter

    try {
      val input = InputImage.fromBitmap(downscaled, 0)
      segmenter.process(input)
        .addOnSuccessListener { segMask ->
          outMask = buildFeatheredAlphaBitmap(
            maskBuffer = segMask.buffer,
            width = segMask.width,
            height = segMask.height,
          )
          latch.countDown()
        }
        .addOnFailureListener { latch.countDown() }
      latch.await(1500L, TimeUnit.MILLISECONDS)
    } catch (_: Throwable) {
      // Safe fallback on Robolectric or unsupported devices
    } finally {
      if (downscaled !== frameBitmap) downscaled.recycle()
    }
    return outMask
  }

  fun estimateSubjectCoverage(frameBitmap: Bitmap): Float {
    val downscaled = downscaleForSegmentation(frameBitmap, 160)
    val latch = CountDownLatch(1)
    var coverage = 0f
    try {
      val input = InputImage.fromBitmap(downscaled, 0)
      stillSegmenter.process(input)
        .addOnSuccessListener { segMask ->
          val buf = segMask.buffer
          buf.rewind()
          val total = segMask.width * segMask.height
          var fg = 0
          for (i in 0 until total) {
            if (buf.float >= 0.55f) fg++
          }
          coverage = if (total > 0) fg.toFloat() / total else 0f
          latch.countDown()
        }
        .addOnFailureListener { latch.countDown() }
      latch.await(1200L, TimeUnit.MILLISECONDS)
    } catch (_: Throwable) {
      coverage = 0f
    } finally {
      if (downscaled !== frameBitmap) downscaled.recycle()
    }
    return coverage
  }

  private suspend fun runSegmenterAsync(segmenter: Segmenter, bitmap: Bitmap): Bitmap? =
    suspendCancellableCoroutine { cont ->
      try {
        val image = InputImage.fromBitmap(bitmap, 0)
        segmenter.process(image)
          .addOnSuccessListener { segMask ->
            val result =
              buildFeatheredAlphaBitmap(
                maskBuffer = segMask.buffer,
                width = segMask.width,
                height = segMask.height,
              )
            if (cont.isActive) cont.resume(result)
          }
          .addOnFailureListener {
            if (cont.isActive) cont.resume(null)
          }
      } catch (_: Throwable) {
        if (cont.isActive) cont.resume(null)
      }
    }

  private fun buildFeatheredAlphaBitmap(
    maskBuffer: java.nio.ByteBuffer,
    width: Int,
    height: Int,
  ): Bitmap? {
    if (width <= 0 || height <= 0) return null
    maskBuffer.rewind()
    val count = width * height
    val pixels = IntArray(count)
    var anyForeground = false

    for (i in 0 until count) {
      val conf = maskBuffer.float
      // Soft feathering ramp between 0.34 and 0.74 for clean hair & shoulder occlusion
      val normalized = ((conf - 0.34f) / 0.40f).coerceIn(0f, 1f)
      val alpha = (normalized * 255f).toInt()
      if (alpha > 16) anyForeground = true
      pixels[i] = Color.argb(alpha, 255, 255, 255)
    }
    if (!anyForeground) return null

    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
      setPixels(pixels, 0, width, 0, 0, width, height)
    }
  }

  private fun downscaleForSegmentation(src: Bitmap, maxDim: Int): Bitmap {
    val w = src.width.coerceAtLeast(1)
    val h = src.height.coerceAtLeast(1)
    if (max(w, h) <= maxDim) return src
    val scale = maxDim.toFloat() / max(w, h).toFloat()
    val targetW = max(16, (w * scale).toInt())
    val targetH = max(16, (h * scale).toInt())
    return Bitmap.createScaledBitmap(src, targetW, targetH, true)
  }

  /**
   * Dedicated MediaMetadataRetriever + LRU mask provider for Media3 CanvasOverlay export.
   */
  class ExportMaskProvider(context: Context, videoUri: Uri) {
    private val retriever = MediaMetadataRetriever()
    private var isOpen = false
    private val exportCache = LruCache<Long, Bitmap>(16)

    init {
      try {
        retriever.setDataSource(context, videoUri)
        isOpen = true
      } catch (_: Exception) {
        isOpen = false
      }
    }

    fun getMaskAtTimeUs(presentationTimeUs: Long): Bitmap? {
      if (!isOpen) return null
      val bucket = (presentationTimeUs.coerceAtLeast(0L) / 66_000L) // ~15fps mask cadence
      exportCache.get(bucket)?.let { return it }

      val frameBmp =
        try {
          val sampleTimeUs = bucket * 66_000L
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
              sampleTimeUs,
              MediaMetadataRetriever.OPTION_CLOSEST,
              256,
              455,
            )
          } else {
            retriever.getFrameAtTime(sampleTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)?.let { raw ->
              val scale = 256f / max(1, max(raw.width, raw.height))
              val tw = max(16, (raw.width * scale).toInt())
              val th = max(16, (raw.height * scale).toInt())
              Bitmap.createScaledBitmap(raw, tw, th, true).also {
                if (it !== raw) raw.recycle()
              }
            }
          }
        } catch (_: Exception) {
          null
        } ?: return null

      val mask = generateMaskBlocking(frameBmp, useStillMode = false)
      frameBmp.recycle()
      if (mask != null) {
        exportCache.put(bucket, mask)
      }
      return mask
    }

    fun release() {
      isOpen = false
      exportCache.evictAll()
      runCatching { retriever.release() }
    }
  }
}
