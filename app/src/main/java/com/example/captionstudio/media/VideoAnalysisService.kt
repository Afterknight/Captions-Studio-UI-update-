package com.example.captionstudio.media

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.FaceDetector
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.example.captionstudio.domain.model.FaceAnchorSample
import com.example.captionstudio.domain.model.NormalizedRect
import com.example.captionstudio.domain.model.SafeRegion
import com.example.captionstudio.domain.model.VideoAnalysisReport
import com.example.captionstudio.domain.model.VideoMetadata
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector as MlKitFaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

object VideoAnalysisService {

  const val LOW_RAM_ANALYSIS_THRESHOLD_BYTES: Long = 2_500_000_000L
  const val LOW_RAM_MAX_FRAMES: Int = 6
  const val NORMAL_RAM_MAX_FRAMES: Int = 16
  const val LOW_RAM_MAX_DIMENSION_PX: Int = 256
  const val NORMAL_RAM_MAX_DIMENSION_PX: Int = 384

  /**
   * Pure memory classification helper for frame-analysis budgeting.
   * Treats Android Go / low-RAM devices OR devices with < 2.5 GB total RAM (2 GB phones)
   * as low-RAM analysis targets.
   */
  internal fun isLowRamAnalysisMemory(isLowRamDevice: Boolean, totalMemBytes: Long): Boolean =
    isLowRamDevice || (totalMemBytes in 1L until LOW_RAM_ANALYSIS_THRESHOLD_BYTES)

  fun isLowRamAnalysisDevice(context: Context): Boolean =
    try {
      val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
      if (activityManager == null) {
        false
      } else {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        isLowRamAnalysisMemory(activityManager.isLowRamDevice, memoryInfo.totalMem)
      }
    } catch (_: Throwable) {
      false
    }

  /**
   * Computes deduplicated frame timestamps for visual analysis.
   * On low-RAM (<= 2 GB) devices, strictly caps sampled frames to [LOW_RAM_MAX_FRAMES] (6)
   * evenly distributed across the timeline to prevent OOM during post-transcription analysis.
   */
  internal fun buildAnalysisSampleTimestamps(
    durationMs: Long,
    targetTimestampsMs: List<Long>,
    isLowRam: Boolean,
  ): List<Long> {
    if (durationMs <= 0L) return emptyList()
    val maxClampedMs = (durationMs - 80L).coerceAtLeast(80L)
    if (isLowRam) {
      val count =
        when {
          durationMs < 2_500L -> min(3, LOW_RAM_MAX_FRAMES)
          durationMs < 6_000L -> min(4, LOW_RAM_MAX_FRAMES)
          else -> LOW_RAM_MAX_FRAMES
        }
      val stepMs = (durationMs / (count + 1)).coerceAtLeast(220L)
      return (1..count)
        .map { i -> (stepMs * i).coerceIn(80L, maxClampedMs) }
        .distinct()
        .sorted()
        .take(LOW_RAM_MAX_FRAMES)
    }

    return buildList {
      val uniformCount =
        when {
          durationMs > 25_000L -> 14
          durationMs > 10_000L -> 12
          else -> 10
        }
      val stepMs = (durationMs / (uniformCount + 1)).coerceAtLeast(220L)
      for (i in 1..uniformCount) {
        add((stepMs * i).coerceIn(80L, maxClampedMs))
      }
      targetTimestampsMs.forEach { ts ->
        val clamped = ts.coerceIn(80L, maxClampedMs)
        if (none { abs(it - clamped) < 350L }) {
          add(clamped)
        }
      }
    }.sorted().take(NORMAL_RAM_MAX_FRAMES)
  }

  private fun downscaleBitmapPreserveAspect(src: Bitmap, maxDimensionPx: Int): Bitmap {
    val w = src.width.coerceAtLeast(1)
    val h = src.height.coerceAtLeast(1)
    val longest = max(w, h)
    if (longest <= maxDimensionPx) return src
    val scale = maxDimensionPx.toFloat() / longest.toFloat()
    val targetW = max(2, (w * scale).toInt())
    val targetH = max(2, (h * scale).toInt())
    return Bitmap.createScaledBitmap(src, targetW, targetH, true)
  }

  private fun decodeDownscaledFrame(
    retriever: MediaMetadataRetriever,
    timeUs: Long,
    maxDimensionPx: Int,
  ): Bitmap? {
    val decoded =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        retriever.getScaledFrameAtTime(
          timeUs,
          MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
          maxDimensionPx,
          maxDimensionPx,
        )
      } else {
        val full = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
        try {
          val scaled = downscaleBitmapPreserveAspect(full, maxDimensionPx)
          if (scaled !== full) {
            runCatching { full.recycle() }
          }
          scaled
        } catch (t: Throwable) {
          if (!full.isRecycled) runCatching { full.recycle() }
          throw t
        }
      } ?: return null

    // Double-check bounds in case an OEM MediaMetadataRetriever ignores scaled dimensions
    return if (max(decoded.width, decoded.height) > maxDimensionPx) {
      try {
        val bounded = downscaleBitmapPreserveAspect(decoded, maxDimensionPx)
        if (bounded !== decoded) {
          runCatching { decoded.recycle() }
        }
        bounded
      } catch (t: Throwable) {
        if (!decoded.isRecycled) runCatching { decoded.recycle() }
        throw t
      }
    } else {
      decoded
    }
  }

  private val mlKitFaceDetector: MlKitFaceDetector by lazy {
    val options =
      FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setMinFaceSize(0.12f)
        .build()
    FaceDetection.getClient(options)
  }

  private suspend fun detectMlKitFaces(bitmap: Bitmap): List<Face> =
    suspendCancellableCoroutine { cont ->
      try {
        val image = InputImage.fromBitmap(bitmap, 0)
        mlKitFaceDetector.process(image)
          .addOnSuccessListener { faces ->
            if (cont.isActive) cont.resume(faces)
          }
          .addOnFailureListener {
            if (cont.isActive) cont.resume(emptyList())
          }
      } catch (_: Throwable) {
        if (cont.isActive) cont.resume(emptyList())
      }
    }

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

  private data class FrameSampleVisual(
    val timestampMs: Long,
    val fingerprint: IntArray,
    val hist: FloatArray,
    val headVetoRect: NormalizedRect?,
    val faceRects: List<NormalizedRect>,
    val subjectCentroidX: Float,
    val subjectCentroidY: Float,
    val subjectCoverage: Float,
    val isSplitScreen: Boolean,
    val zoneMetrics: Map<SafeRegion, com.example.captionstudio.domain.model.ZoneVisualMetrics>,
  )

  private val candidateSafeZones =
    listOf(
      SafeRegion.LOWER_CENTER,
      SafeRegion.UPPER_CENTER,
      SafeRegion.CENTER,
      SafeRegion.LEFT_SIDE,
      SafeRegion.RIGHT_SIDE,
      SafeRegion.UPPER_LEFT,
      SafeRegion.UPPER_RIGHT,
    )

  /** Normalized bounding box [left, top, right, bottom] for each candidate safe placement zone. */
  internal fun zoneRectFor(region: SafeRegion): NormalizedRect =
    when (region) {
      SafeRegion.LOWER_CENTER -> NormalizedRect(0.12f, 0.68f, 0.88f, 0.88f)
      SafeRegion.UPPER_CENTER -> NormalizedRect(0.12f, 0.13f, 0.88f, 0.33f)
      SafeRegion.CENTER -> NormalizedRect(0.16f, 0.42f, 0.84f, 0.62f)
      SafeRegion.LEFT_SIDE -> NormalizedRect(0.06f, 0.30f, 0.46f, 0.64f)
      SafeRegion.RIGHT_SIDE -> NormalizedRect(0.54f, 0.30f, 0.94f, 0.64f)
      SafeRegion.UPPER_LEFT -> NormalizedRect(0.06f, 0.13f, 0.50f, 0.34f)
      SafeRegion.UPPER_RIGHT -> NormalizedRect(0.50f, 0.13f, 0.94f, 0.34f)
      SafeRegion.SEAM_CENTER -> NormalizedRect(0.14f, 0.46f, 0.86f, 0.58f)
      SafeRegion.CUSTOM -> NormalizedRect(0.15f, 0.40f, 0.85f, 0.60f)
    }

  suspend fun analyzeLocally(
    context: Context,
    videoUri: Uri,
    metadata: VideoMetadata,
    targetTimestampsMs: List<Long> = emptyList(),
  ): VideoAnalysisReport =
    withContext(Dispatchers.IO) {
      if (metadata.durationMs <= 0L) {
        return@withContext VideoAnalysisReport(
          summary = "No video track available; using editorial safe-zone defaults."
        )
      }

      val retriever = MediaMetadataRetriever()
      val faceRectsAll = mutableListOf<NormalizedRect>()
      val faceAnchorSamples = mutableListOf<FaceAnchorSample>()
      val sceneChanges = mutableListOf<Long>()
      val frameSamples = mutableListOf<FrameSampleVisual>()
      var splitSeamHits = 0

      val isLowRam = isLowRamAnalysisDevice(context)
      val maxDimPx = if (isLowRam) LOW_RAM_MAX_DIMENSION_PX else NORMAL_RAM_MAX_DIMENSION_PX
      val sampleTimesMs =
        buildAnalysisSampleTimestamps(
          durationMs = metadata.durationMs,
          targetTimestampsMs = targetTimestampsMs,
          isLowRam = isLowRam,
        )

      try {
        retriever.setDataSource(context, videoUri)

        for (tsMs in sampleTimesMs) {
          var raw: Bitmap? = null
          var rgb565: Bitmap? = null
          var detectorBitmap: Bitmap? = null
          try {
            val timeUs = tsMs * 1000L
            raw = decodeDownscaledFrame(retriever, timeUs, maxDimPx) ?: continue
            val frameBmp = raw

            val fingerprint = SubjectMaskProcessor.computeFrameFingerprint16x16(frameBmp)
            val subjectMetrics =
              try {
                SubjectMaskProcessor.analyzeSubjectMetrics(frameBmp)
              } catch (_: Throwable) {
                SubjectMaskProcessor.SubjectMaskMetrics(0f, 0.5f, 0.55f, null, null)
              }

            // Ensure even dimensions for Android FaceDetector
            val evenW = (frameBmp.width / 2) * 2
            val evenH = (frameBmp.height / 2) * 2
            if (evenW < 2 || evenH < 2) continue

            rgb565 = frameBmp.copy(Bitmap.Config.RGB_565, false)
            detectorBitmap =
              if (rgb565 != null) {
                if (rgb565.width != evenW || rgb565.height != evenH) {
                  Bitmap.createBitmap(rgb565, 0, 0, evenW, evenH)
                } else {
                  rgb565
                }
              } else {
                null
              }

            // 1. ML Kit FaceDetector (primary: exact boundingBox + headEulerAngleZ roll angle for parallax)
            val frameFaces = mutableListOf<NormalizedRect>()
            var hasBottomHalfFace = false
            val rawW = frameBmp.width.coerceAtLeast(1).toFloat()
            val rawH = frameBmp.height.coerceAtLeast(1).toFloat()
            val mlFaces =
              try {
                detectMlKitFaces(frameBmp)
              } catch (ce: CancellationException) {
                throw ce
              } catch (_: Throwable) {
                emptyList()
              }

            if (mlFaces.isNotEmpty()) {
              var primaryFace: Face? = null
              var primaryArea = -1
              for (face in mlFaces) {
                val b = face.boundingBox
                val area = b.width() * b.height()
                if (area > primaryArea) {
                  primaryArea = area
                  primaryFace = face
                }
                val rect =
                  NormalizedRect(
                    left = (b.left / rawW).coerceIn(0f, 1f),
                    top = (b.top / rawH).coerceIn(0f, 1f),
                    right = (b.right / rawW).coerceIn(0f, 1f),
                    bottom = (b.bottom / rawH).coerceIn(0f, 1f),
                  )
                frameFaces.add(rect)
                faceRectsAll.add(rect)
                if (rect.top > 0.38f) hasBottomHalfFace = true
              }
              primaryFace?.let { pf ->
                val b = pf.boundingBox
                val cx = (b.exactCenterX() / rawW).coerceIn(0f, 1f)
                val cy = (b.exactCenterY() / rawH).coerceIn(0f, 1f)
                val rollZ = pf.headEulerAngleZ.takeIf { it.isFinite() }?.coerceIn(-45f, 45f) ?: 0f
                faceAnchorSamples.add(
                  FaceAnchorSample(
                    timestampMs = tsMs,
                    centerX = cx,
                    centerY = cy,
                    headEulerAngleZ = rollZ,
                  )
                )
              }
            } else if (detectorBitmap != null && !detectorBitmap.isRecycled) {
              // Fallback to Android FaceDetector if ML Kit finds no frontal face on this frame
              try {
                val faces = arrayOfNulls<FaceDetector.Face>(3)
                val found =
                  FaceDetector(detectorBitmap.width, detectorBitmap.height, 3)
                    .findFaces(detectorBitmap, faces)
                var fallbackAddedAnchor = false
                for (f in 0 until found) {
                  val face = faces[f] ?: continue
                  val mid = android.graphics.PointF()
                  face.getMidPoint(mid)
                  val eyeDist = face.eyesDistance().coerceAtLeast(10f)
                  val halfW = (eyeDist * 1.75f) / detectorBitmap.width
                  val halfH = (eyeDist * 2.35f) / detectorBitmap.height
                  val cx = (mid.x / detectorBitmap.width).coerceIn(0f, 1f)
                  val cy = (mid.y / detectorBitmap.height).coerceIn(0f, 1f)
                  val rect =
                    NormalizedRect(
                      left = (cx - halfW).coerceIn(0f, 1f),
                      top = (cy - halfH).coerceIn(0f, 1f),
                      right = (cx + halfW).coerceIn(0f, 1f),
                      bottom = (cy + halfH).coerceIn(0f, 1f),
                    )
                  frameFaces.add(rect)
                  faceRectsAll.add(rect)
                  if (rect.top > 0.38f) hasBottomHalfFace = true
                  if (!fallbackAddedAnchor) {
                    faceAnchorSamples.add(
                      FaceAnchorSample(
                        timestampMs = tsMs,
                        centerX = cx,
                        centerY = cy,
                        headEulerAngleZ = 0f,
                      )
                    )
                    fallbackAddedAnchor = true
                  }
                }
              } catch (_: Throwable) {
                // Swallow FaceDetector native/OOM failures per frame so analysis continues safely
              }
            }

            val mergedHeadVeto = mergeHeadVetoRects(frameFaces, subjectMetrics.headVetoRect)

            // 2. Per-zone edge variance (clutter), mean luminance, histogram, and split-seam check
            val pixelSource = detectorBitmap ?: frameBmp
            val w = pixelSource.width
            val h = pixelSource.height
            val pixels = IntArray(w * h)
            pixelSource.getPixels(pixels, 0, w, 0, 0, w, h)

            val (hist, isSplitFrame, zoneMetrics) =
              analyzeFramePixelsForZones(
                pixels = pixels,
                width = w,
                height = h,
                headVetoRect = mergedHeadVeto,
                subjectBbox = subjectMetrics.boundingBox,
                subjectCentroidX = subjectMetrics.centroidX,
                subjectCoverage = subjectMetrics.coverageFraction,
                hasBottomHalfFace = hasBottomHalfFace,
              )
            if (isSplitFrame) splitSeamHits++

            frameSamples.add(
              FrameSampleVisual(
                timestampMs = tsMs,
                fingerprint = fingerprint,
                hist = hist,
                headVetoRect = mergedHeadVeto,
                faceRects = frameFaces,
                subjectCentroidX = subjectMetrics.centroidX,
                subjectCentroidY = subjectMetrics.centroidY,
                subjectCoverage = subjectMetrics.coverageFraction,
                isSplitScreen = isSplitFrame,
                zoneMetrics = zoneMetrics,
              )
            )
          } catch (ce: CancellationException) {
            throw ce
          } catch (_: Throwable) {
            // Skip frame safely on OutOfMemoryError or codec error without crashing
          } finally {
            if (detectorBitmap != null && detectorBitmap !== rgb565 && detectorBitmap !== raw && !detectorBitmap.isRecycled) {
              runCatching { detectorBitmap.recycle() }
            }
            if (rgb565 != null && rgb565 !== raw && !rgb565.isRecycled) {
              runCatching { rgb565.recycle() }
            }
            if (raw != null && !raw.isRecycled) {
              runCatching { raw.recycle() }
            }
          }
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
        // Fallback safely on any media/OOM error
      } finally {
        runCatching { retriever.release() }
      }

      if (frameSamples.isEmpty()) {
        return@withContext VideoAnalysisReport(
          summary = "Could not decode video frames; using clean lower-center defaults."
        )
      }

      // Partition frame samples into scene-cut-delimited shots for shot-level placement stability
      val shots = mutableListOf<MutableList<FrameSampleVisual>>()
      var currentShot = mutableListOf(frameSamples.first())
      shots.add(currentShot)

      for (i in 1 until frameSamples.size) {
        val prev = frameSamples[i - 1]
        val curr = frameSamples[i]
        val sad = SubjectMaskProcessor.frameDifferenceSad(prev.fingerprint, curr.fingerprint)
        var histDiff = 0f
        for (b in curr.hist.indices) histDiff += abs(curr.hist[b] - prev.hist[b])
        val isCut = sad >= 40.0f || histDiff > 0.40f
        if (isCut) {
          val cutTs = (prev.timestampMs + curr.timestampMs) / 2L
          sceneChanges.add(cutTs)
          currentShot = mutableListOf(curr)
          shots.add(currentShot)
        } else {
          currentShot.add(curr)
        }
      }

      val shotAnalyses =
        shots.mapIndexed { shotIdx, samples ->
          val startMs =
            if (shotIdx == 0) 0L else sceneChanges.getOrElse(shotIdx - 1) { samples.first().timestampMs }
          val endMs =
            if (shotIdx < sceneChanges.size) sceneChanges[shotIdx] else metadata.durationMs.coerceAtLeast(samples.last().timestampMs + 500L)

          val avgCoverage = samples.map { it.subjectCoverage }.average().toFloat()
          val avgCx = samples.map { it.subjectCentroidX }.average().toFloat()
          val avgCy = samples.map { it.subjectCentroidY }.average().toFloat()
          val shotSplit = samples.count { it.isSplitScreen } >= max(1, (samples.size + 1) / 2)
          val shotHeadVeto = mergeHeadVetoRects(samples.mapNotNull { it.headVetoRect }, null)

          val aggregatedZones =
            candidateSafeZones.associateWith { region ->
              val zList = samples.mapNotNull { it.zoneMetrics[region] }
              if (zList.isEmpty()) {
                com.example.captionstudio.domain.model.ZoneVisualMetrics()
              } else {
                com.example.captionstudio.domain.model.ZoneVisualMetrics(
                  edgeClutter = zList.map { it.edgeClutter }.average().toFloat(),
                  meanLuminance = zList.map { it.meanLuminance }.average().toFloat(),
                  isBlockedByFace = zList.any { it.isBlockedByFace },
                  isBlockedBySubject = zList.count { it.isBlockedBySubject } >= max(1, (zList.size + 1) / 2),
                  isWideAndEmpty = zList.all { it.isWideAndEmpty },
                )
              }
            }

          val bestZone =
            selectBestZoneForShot(
              zoneMetrics = aggregatedZones,
              isSplitScreen = shotSplit,
              subjectCoverage = avgCoverage,
            )

          com.example.captionstudio.domain.model.ShotVisualAnalysis(
            startMs = startMs,
            endMs = endMs,
            headVetoRect = shotHeadVeto,
            subjectCentroidX = avgCx,
            subjectCentroidY = avgCy,
            subjectCoverage = avgCoverage,
            isSplitScreen = shotSplit,
            zoneMetrics = aggregatedZones,
            preferredZone = bestZone,
          )
        }

      val avgSubjectCoverage = frameSamples.map { it.subjectCoverage }.average().toFloat()
      val isSplitDetected = splitSeamHits >= 2

      // Rank global safe regions by average shot score
      val globalRanked =
        candidateSafeZones.sortedBy { region ->
          shotAnalyses.map { shot ->
            scoreZoneCandidate(region, shot.zoneMetrics[region])
          }.average()
        }

      val topRegion =
        if (isSplitDetected) SafeRegion.SEAM_CENTER
        else shotAnalyses.firstOrNull()?.preferredZone ?: globalRanked.firstOrNull() ?: SafeRegion.LOWER_CENTER

      val summary =
        buildString {
          append("Analyzed ${frameSamples.size} frames • ${shotAnalyses.size} shot${if (shotAnalyses.size == 1) "" else "s"}")
          if (faceRectsAll.isNotEmpty() || shotAnalyses.any { it.headVetoRect != null }) {
            append(" • Face-safe lock")
          }
          if (avgSubjectCoverage > 0.08f) {
            append(" • Depth ${(avgSubjectCoverage * 100).toInt()}%")
          }
          if (isSplitDetected) append(" • Split-screen")
          append(" • Anchor: ${topRegion.label}")
        }

      VideoAnalysisReport(
        frameCountSampled = frameSamples.size,
        sceneChangeTimestampsMs = sceneChanges,
        faceRegions = (faceRectsAll + shotAnalyses.mapNotNull { it.headVetoRect }).take(6),
        safeRegionsRanked = globalRanked,
        suggestedAnchorX = topRegion.defaultAnchorX,
        suggestedAnchorY = topRegion.defaultAnchorY,
        summary = summary,
        analyzedWithGemini = false,
        subjectCoverageFraction = avgSubjectCoverage,
        isSplitScreenDetected = isSplitDetected,
        shotAnalyses = shotAnalyses,
        faceAnchorSamples = faceAnchorSamples.sortedBy { it.timestampMs },
      )
    }

  internal fun analyzeFramePixelsForZones(
    pixels: IntArray,
    width: Int,
    height: Int,
    headVetoRect: NormalizedRect?,
    subjectBbox: NormalizedRect?,
    subjectCentroidX: Float,
    subjectCoverage: Float,
    hasBottomHalfFace: Boolean,
  ): Triple<FloatArray, Boolean, Map<SafeRegion, com.example.captionstudio.domain.model.ZoneVisualMetrics>> {
    val hist = FloatArray(16)
    val rowEdge = FloatArray(height)
    val lumGrid = FloatArray(width * height)

    for (y in 0 until height step 2) {
      var rowDiffSum = 0f
      val rowOff = y * width
      for (x in 0 until width step 2) {
        val c = pixels[rowOff + x]
        val lum = (Color.red(c) * 0.299f + Color.green(c) * 0.587f + Color.blue(c) * 0.114f)
        lumGrid[rowOff + x] = lum
        val bin = min(15, (lum / 16f).toInt().coerceAtLeast(0))
        hist[bin] += 1f
        if (y + 2 < height) {
          val cDown = pixels[(y + 2) * width + x]
          val lumD = Color.red(cDown) * 0.299f + Color.green(cDown) * 0.587f + Color.blue(cDown) * 0.114f
          rowDiffSum += abs(lum - lumD)
        }
      }
      rowEdge[y] = rowDiffSum / max(1, width / 2)
    }

    val totalHist = hist.sum().coerceAtLeast(1f)
    for (b in hist.indices) hist[b] /= totalHist

    val midRange = (height * 0.42f).toInt()..(height * 0.58f).toInt()
    val avgRowEdge = rowEdge.average().toFloat().coerceAtLeast(1f)
    val maxMidSeam = midRange.maxOfOrNull { rowEdge.getOrElse(it) { 0f } } ?: 0f
    val isSplitFrame = hasBottomHalfFace && maxMidSeam > avgRowEdge * 2.4f

    val zoneMap = mutableMapOf<SafeRegion, com.example.captionstudio.domain.model.ZoneVisualMetrics>()
    for (region in candidateSafeZones) {
      val zr = zoneRectFor(region)
      val x0 = (zr.left * width).toInt().coerceIn(0, width - 1)
      val x1 = (zr.right * width).toInt().coerceIn(x0 + 1, width)
      val y0 = (zr.top * height).toInt().coerceIn(0, height - 1)
      val y1 = (zr.bottom * height).toInt().coerceIn(y0 + 1, height)

      var edgeSum = 0f
      var lumSum = 0f
      var samples = 0
      var y = (y0 / 2) * 2
      while (y < y1 - 2) {
        var x = (x0 / 2) * 2
        val rowOff = y * width
        while (x < x1 - 2) {
          val lum = lumGrid[rowOff + x]
          val lumR = lumGrid[rowOff + x + 2]
          val lumD = lumGrid[(y + 2) * width + x]
          lumSum += lum
          edgeSum += abs(lum - lumR) + abs(lum - lumD)
          samples++
          x += 2
        }
        y += 2
      }
      val avgClutter = if (samples > 0) edgeSum / samples else 12f
      val avgLum = if (samples > 0) lumSum / samples else 96f

      val faceOverlap = headVetoRect?.let { rectOverlapFraction(zr, it) } ?: 0f
      val subjOverlap = subjectBbox?.let { rectOverlapFraction(zr, it) } ?: 0f

      val blockedByFace = faceOverlap > 0.04f
      val blockedBySubj = subjOverlap > 0.28f

      // Side margins are ONLY allowed when truly wide & empty (speaker off-center to opposite side)
      val wideAndEmpty =
        when (region) {
          SafeRegion.LEFT_SIDE, SafeRegion.UPPER_LEFT ->
            !blockedByFace && !blockedBySubj && avgClutter < 19f &&
              (subjectCoverage < 0.08f || subjectCentroidX > 0.56f)
          SafeRegion.RIGHT_SIDE, SafeRegion.UPPER_RIGHT ->
            !blockedByFace && !blockedBySubj && avgClutter < 19f &&
              (subjectCoverage < 0.08f || subjectCentroidX < 0.44f)
          else -> !blockedByFace && avgClutter < 20f
        }

      zoneMap[region] =
        com.example.captionstudio.domain.model.ZoneVisualMetrics(
          edgeClutter = avgClutter,
          meanLuminance = avgLum,
          isBlockedByFace = blockedByFace,
          isBlockedBySubject = blockedBySubj,
          isWideAndEmpty = wideAndEmpty,
        )
    }

    return Triple(hist, isSplitFrame, zoneMap)
  }

  internal fun selectBestZoneForShot(
    zoneMetrics: Map<SafeRegion, com.example.captionstudio.domain.model.ZoneVisualMetrics>,
    isSplitScreen: Boolean,
    subjectCoverage: Float,
  ): SafeRegion {
    if (isSplitScreen) return SafeRegion.SEAM_CENTER

    // Primary editorial anchors: Lower Center, Upper Center, Center Chest.
    // Side zones are only considered if wide & empty.
    val eligible =
      candidateSafeZones.filter { region ->
        val m = zoneMetrics[region]
        when (region) {
          SafeRegion.LEFT_SIDE,
          SafeRegion.RIGHT_SIDE,
          SafeRegion.UPPER_LEFT,
          SafeRegion.UPPER_RIGHT -> m?.isWideAndEmpty == true
          else -> true
        }
      }.ifEmpty { listOf(SafeRegion.LOWER_CENTER, SafeRegion.UPPER_CENTER) }

    return eligible.minByOrNull { region ->
      var score = scoreZoneCandidate(region, zoneMetrics[region])
      // Mild editorial preference for stable Lower Center / Upper Center over dead-center chest when speaking
      if (region == SafeRegion.LOWER_CENTER) score -= 5.0f
      if (region == SafeRegion.UPPER_CENTER) score -= 2.5f
      if (region == SafeRegion.CENTER && subjectCoverage > 0.12f) score += 6.0f
      score
    } ?: SafeRegion.LOWER_CENTER
  }

  internal fun scoreZoneCandidate(
    region: SafeRegion,
    metrics: com.example.captionstudio.domain.model.ZoneVisualMetrics?,
  ): Float {
    if (metrics == null) return 50f
    var score = metrics.edgeClutter
    // Hard veto: captions must NEVER cover eyes/mouth
    if (metrics.isBlockedByFace) score += 10_000f
    // Penalize overlapping active foreground body (unless behind-subject hero intentionally overrides)
    if (metrics.isBlockedBySubject) score += 45f
    // Slightly penalize overly bright backgrounds (white shirts/skies)
    if (metrics.meanLuminance > 185f) score += 14f
    // Side margins without wide empty space get heavy penalty
    if ((region == SafeRegion.LEFT_SIDE || region == SafeRegion.RIGHT_SIDE ||
        region == SafeRegion.UPPER_LEFT || region == SafeRegion.UPPER_RIGHT) && !metrics.isWideAndEmpty
    ) {
      score += 120f
    }
    return score
  }

  private fun rectOverlapFraction(zone: NormalizedRect, obstacle: NormalizedRect): Float {
    val ox = max(0f, min(zone.right, obstacle.right) - max(zone.left, obstacle.left))
    val oy = max(0f, min(zone.bottom, obstacle.bottom) - max(zone.top, obstacle.top))
    val zoneArea = ((zone.right - zone.left) * (zone.bottom - zone.top)).coerceAtLeast(0.0001f)
    return (ox * oy) / zoneArea
  }

  private fun mergeHeadVetoRects(
    rects: List<NormalizedRect>,
    extra: NormalizedRect?,
  ): NormalizedRect? {
    val all = if (extra != null) rects + extra else rects
    if (all.isEmpty()) return null
    return NormalizedRect(
      left = all.minOf { it.left }.coerceIn(0f, 1f),
      top = all.minOf { it.top }.coerceIn(0f, 1f),
      right = all.maxOf { it.right }.coerceIn(0f, 1f),
      bottom = all.maxOf { it.bottom }.coerceIn(0f, 1f),
    )
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
      val isLowRam = isLowRamAnalysisDevice(context)
      val effectiveMaxFrames = if (isLowRam) min(maxFrames, LOW_RAM_MAX_FRAMES) else maxFrames
      val maxDimPx = if (isLowRam) LOW_RAM_MAX_DIMENSION_PX else NORMAL_RAM_MAX_DIMENSION_PX
      val retriever = MediaMetadataRetriever()
      val out = mutableListOf<TimestampedFrame>()
      try {
        retriever.setDataSource(context, videoUri)
        val stepUs = (durationMs * 1000L) / (effectiveMaxFrames + 1)
        for (i in 1..effectiveMaxFrames) {
          var bmp: Bitmap? = null
          try {
            val tsUs = stepUs * i
            bmp = decodeDownscaledFrame(retriever, tsUs, maxDimPx) ?: continue
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 72, baos)
            out.add(
              TimestampedFrame(
                timestampMs = tsUs / 1000L,
                base64Jpeg = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP),
              )
            )
          } catch (ce: CancellationException) {
            throw ce
          } catch (_: Throwable) {
            // Skip frame safely on OOM
          } finally {
            if (bmp != null && !bmp.isRecycled) {
              runCatching { bmp.recycle() }
            }
          }
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
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
