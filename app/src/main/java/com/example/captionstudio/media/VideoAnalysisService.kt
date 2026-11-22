package com.example.captionstudio.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.FaceDetector
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.example.captionstudio.domain.model.NormalizedRect
import com.example.captionstudio.domain.model.SafeRegion
import com.example.captionstudio.domain.model.VideoAnalysisReport
import com.example.captionstudio.domain.model.VideoMetadata
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoAnalysisService {

  suspend fun extractMetadata(context: Context, videoUri: Uri): VideoMetadata =
    withContext(Dispatchers.IO) {
      val retriever = MediaMetadataRetriever()
      try {
        retriever.setDataSource(context, videoUri)
        val durationMs =
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            ?: 0L
        val width =
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            ?: 1080
        val height =
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            ?: 1920
        val rotation =
          retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        val fps =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            retriever
              .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
              ?.toFloatOrNull() ?: 30f
          } else {
            30f
          }
        val hasAudio =
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        VideoMetadata(
          durationMs = durationMs,
          width = width,
          height = height,
          rotationDegrees = rotation,
          frameRate = fps,
          hasAudio = hasAudio,
        )
      } catch (_: Exception) {
        VideoMetadata()
      } finally {
        runCatching { retriever.release() }
      }
    }

  suspend fun analyzeLocally(
    context: Context,
    videoUri: Uri,
    metadata: VideoMetadata,
  ): VideoAnalysisReport =
    withContext(Dispatchers.IO) {
      if (metadata.durationMs <= 0L) {
        return@withContext VideoAnalysisReport(
          summary = "No video track available; using editorial safe-zone defaults."
        )
      }

      val retriever = MediaMetadataRetriever()
      val faceRects = mutableListOf<NormalizedRect>()
      val sceneChanges = mutableListOf<Long>()
      // 3x3 grid accumulating busyness/edge score (lower = cleaner negative space)
      val gridComplexity = Array(3) { FloatArray(3) }
      var sampledCount = 0
      var prevHist: FloatArray? = null
      var subjectCoverageAcc = 0f
      var subjectCoverageSamples = 0
      var splitSeamHits = 0

      try {
        retriever.setDataSource(context, videoUri)
        val sampleCount = 8
        val stepUs = (metadata.durationMs * 1000L) / (sampleCount + 1)

        for (i in 1..sampleCount) {
          val timeUs = stepUs * i
          val raw =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
              retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                320,
                568,
              )
            } else {
              retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let {
                val scale = 320f / max(1, it.width)
                val scaled =
                  Bitmap.createScaledBitmap(
                    it,
                    max(2, (it.width * scale).toInt()),
                    max(2, (it.height * scale).toInt()),
                    true,
                  )
                if (scaled !== it) it.recycle()
                scaled
              }
            } ?: continue

          sampledCount++
          if (i in listOf(2, 4, 6)) {
            subjectCoverageAcc += SubjectMaskProcessor.estimateSubjectCoverage(raw)
            subjectCoverageSamples++
          }

          // Ensure even width for FaceDetector
          val evenW = (raw.width / 2) * 2
          val evenH = (raw.height / 2) * 2
          val rgb565 = raw.copy(Bitmap.Config.RGB_565, false)
          val detectorBitmap =
            if (rgb565.width != evenW || rgb565.height != evenH) {
              Bitmap.createBitmap(rgb565, 0, 0, evenW, evenH).also { rgb565.recycle() }
            } else {
              rgb565
            }

          // 1. Face detection
          val faces = arrayOfNulls<FaceDetector.Face>(3)
          val found = FaceDetector(detectorBitmap.width, detectorBitmap.height, 3).findFaces(detectorBitmap, faces)
          var hasBottomHalfFace = false
          for (f in 0 until found) {
            val face = faces[f] ?: continue
            val mid = android.graphics.PointF()
            face.getMidPoint(mid)
            val eyeDist = face.eyesDistance().coerceAtLeast(12f)
            val halfW = (eyeDist * 1.6f) / detectorBitmap.width
            val halfH = (eyeDist * 2.1f) / detectorBitmap.height
            val cx = mid.x / detectorBitmap.width
            val cy = mid.y / detectorBitmap.height
            val rect =
              NormalizedRect(
                left = (cx - halfW).coerceIn(0f, 1f),
                top = (cy - halfH).coerceIn(0f, 1f),
                right = (cx + halfW).coerceIn(0f, 1f),
                bottom = (cy + halfH).coerceIn(0f, 1f),
              )
            faceRects.add(rect)
            if (rect.top > 0.38f) hasBottomHalfFace = true
          }

          // 2. Luminance histogram scene-change check, 3x3 edge-energy map & horizontal split-seam check
          val hist = FloatArray(16)
          val w = detectorBitmap.width
          val h = detectorBitmap.height
          val pixels = IntArray(w * h)
          detectorBitmap.getPixels(pixels, 0, w, 0, 0, w, h)

          val cellCounts = Array(3) { IntArray(3) }
          val rowEdge = FloatArray(h)
          for (y in 0 until h step 2) {
            val gy = min(2, (y * 3) / h)
            var rowDiffSum = 0f
            for (x in 0 until w step 2) {
              val gx = min(2, (x * 3) / w)
              val c = pixels[y * w + x]
              val lum = (Color.red(c) * 0.299f + Color.green(c) * 0.587f + Color.blue(c) * 0.114f)
              val bin = min(15, (lum / 16f).toInt().coerceAtLeast(0))
              hist[bin] += 1f

              if (x + 2 < w && y + 2 < h) {
                val cRight = pixels[y * w + (x + 2)]
                val cDown = pixels[(y + 2) * w + x]
                val lumR = Color.red(cRight) * 0.299f + Color.green(cRight) * 0.587f + Color.blue(cRight) * 0.114f
                val lumD = Color.red(cDown) * 0.299f + Color.green(cDown) * 0.587f + Color.blue(cDown) * 0.114f
                val dY = abs(lum - lumD)
                gridComplexity[gy][gx] += abs(lum - lumR) + dY
                cellCounts[gy][gx]++
                rowDiffSum += dY
              }
            }
            rowEdge[y] = rowDiffSum / max(1, w / 2)
          }

          // Split-screen heuristic: sharp horizontal divider near middle + face in bottom pane
          val midRange = (h * 0.42f).toInt()..(h * 0.58f).toInt()
          val avgRowEdge = rowEdge.average().toFloat().coerceAtLeast(1f)
          val maxMidSeam = midRange.maxOfOrNull { rowEdge.getOrElse(it) { 0f } } ?: 0f
          if (hasBottomHalfFace && maxMidSeam > avgRowEdge * 2.4f) {
            splitSeamHits++
          }

          val totalSamples = hist.sum().coerceAtLeast(1f)
          for (b in hist.indices) hist[b] /= totalSamples
          prevHist?.let { prev ->
            var diff = 0f
            for (b in hist.indices) diff += abs(hist[b] - prev[b])
            if (diff > 0.36f) {
              sceneChanges.add(timeUs / 1000L)
            }
          }
          prevHist = hist

          for (gy in 0..2) {
            for (gx in 0..2) {
              val cnt = cellCounts[gy][gx].coerceAtLeast(1)
              gridComplexity[gy][gx] = gridComplexity[gy][gx] / cnt
            }
          }

          detectorBitmap.recycle()
          raw.recycle()
        }
      } catch (_: Exception) {
        // Fallback safely
      } finally {
        runCatching { retriever.release() }
      }

      val avgSubjectCoverage =
        if (subjectCoverageSamples > 0) subjectCoverageAcc / subjectCoverageSamples else 0f
      val isSplitDetected = splitSeamHits >= 2

      // Penalize cells containing detected faces heavily so captions avoid faces & eyes
      val regionScore =
        mapOf(
          SafeRegion.UPPER_CENTER to scoreRegion(0, 1, gridComplexity, faceRects),
          SafeRegion.UPPER_LEFT to scoreRegion(0, 0, gridComplexity, faceRects),
          SafeRegion.UPPER_RIGHT to scoreRegion(0, 2, gridComplexity, faceRects),
          SafeRegion.LEFT_SIDE to scoreRegion(1, 0, gridComplexity, faceRects),
          SafeRegion.CENTER to scoreRegion(1, 1, gridComplexity, faceRects),
          SafeRegion.RIGHT_SIDE to scoreRegion(1, 2, gridComplexity, faceRects),
          SafeRegion.LOWER_CENTER to scoreRegion(2, 1, gridComplexity, faceRects),
        )

      val ranked = regionScore.entries.sortedBy { it.value }.map { it.key }
      val topRegion =
        if (isSplitDetected) SafeRegion.SEAM_CENTER
        else ranked.firstOrNull() ?: SafeRegion.UPPER_CENTER
      val summary =
        buildString {
          append("Sampled $sampledCount frames")
          if (faceRects.isNotEmpty()) append(" • Face tracked")
          if (avgSubjectCoverage > 0.08f) {
            append(" • Depth Mask ${(avgSubjectCoverage * 100).toInt()}%")
          }
          if (isSplitDetected) append(" • Split-screen detected")
          if (sceneChanges.isNotEmpty()) append(" • ${sceneChanges.size} cuts")
          append(" • Zone: ${topRegion.label}")
        }

      VideoAnalysisReport(
        frameCountSampled = sampledCount,
        sceneChangeTimestampsMs = sceneChanges,
        faceRegions = faceRects.take(6),
        safeRegionsRanked = ranked,
        suggestedAnchorX = topRegion.defaultAnchorX,
        suggestedAnchorY = topRegion.defaultAnchorY,
        summary = summary,
        analyzedWithGemini = false,
        subjectCoverageFraction = avgSubjectCoverage,
        isSplitScreenDetected = isSplitDetected,
      )
    }

  private fun scoreRegion(
    gridY: Int,
    gridX: Int,
    complexity: Array<FloatArray>,
    faces: List<NormalizedRect>,
  ): Float {
    var score = complexity[gridY][gridX]
    val cellLeft = gridX / 3f
    val cellRight = (gridX + 1) / 3f
    val cellTop = gridY / 3f
    val cellBottom = (gridY + 1) / 3f

    for (face in faces) {
      val overlapX = max(0f, min(cellRight, face.right) - max(cellLeft, face.left))
      val overlapY = max(0f, min(cellBottom, face.bottom) - max(cellTop, face.top))
      if (overlapX > 0f && overlapY > 0f) {
        score += 5000f * (overlapX * overlapY * 9f)
      }
    }
    return score
  }

  data class TimestampedFrame(
    val timestampMs: Long,
    val base64Jpeg: String,
  )

  /**
   * Samples up to [maxFrames] compact timestamped JPEG frames so Gemini Vision sees scene changes,
   * negative space, split-screens, and speaker subject position across the whole timeline.
   */
  suspend fun sampleTimestampedFramesAsJpegBase64(
    context: Context,
    videoUri: Uri,
    durationMs: Long,
    maxFrames: Int = 8,
  ): List<TimestampedFrame> =
    withContext(Dispatchers.IO) {
      if (durationMs <= 0L) return@withContext emptyList()
      val retriever = MediaMetadataRetriever()
      val out = mutableListOf<TimestampedFrame>()
      try {
        retriever.setDataSource(context, videoUri)
        val stepUs = (durationMs * 1000L) / (maxFrames + 1)
        for (i in 1..maxFrames) {
          val tsUs = stepUs * i
          val bmp =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
              retriever.getScaledFrameAtTime(
                tsUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                384,
                384,
              )
            } else {
              retriever.getFrameAtTime(tsUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { raw ->
                val scale = 384f / max(1, max(raw.width, raw.height))
                Bitmap.createScaledBitmap(
                  raw,
                  max(16, (raw.width * scale).toInt()),
                  max(16, (raw.height * scale).toInt()),
                  true,
                ).also { if (it !== raw) raw.recycle() }
              }
            } ?: continue
          val baos = ByteArrayOutputStream()
          bmp.compress(Bitmap.CompressFormat.JPEG, 72, baos)
          bmp.recycle()
          out.add(
            TimestampedFrame(
              timestampMs = tsUs / 1000L,
              base64Jpeg = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP),
            )
          )
        }
      } catch (_: Exception) {
        // Ignore gracefully
      } finally {
        runCatching { retriever.release() }
      }
      out
    }

  suspend fun sampleFramesAsJpegBase64(
    context: Context,
    videoUri: Uri,
    durationMs: Long,
    maxFrames: Int = 4,
  ): List<String> = sampleTimestampedFramesAsJpegBase64(context, videoUri, durationMs, maxFrames).map { it.base64Jpeg }
}
