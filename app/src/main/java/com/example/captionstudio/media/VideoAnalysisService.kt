package com.example.captionstudio.media

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

object VideoAnalysisService {

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

      // Build deduplicated sample timestamps (uniformly across timeline + optional caption midpoints)
      val sampleTimesMs =
        buildList {
          val uniformCount =
            when {
              metadata.durationMs > 25_000L -> 18
              metadata.durationMs > 10_000L -> 14
              else -> 12
            }
          val stepMs = (metadata.durationMs / (uniformCount + 1)).coerceAtLeast(220L)
          for (i in 1..uniformCount) {
            add((stepMs * i).coerceIn(80L, (metadata.durationMs - 80L).coerceAtLeast(80L)))
          }
          targetTimestampsMs.forEach { ts ->
            val clamped = ts.coerceIn(80L, (metadata.durationMs - 80L).coerceAtLeast(80L))
            if (none { abs(it - clamped) < 350L }) {
              add(clamped)
            }
          }
        }.sorted().take(24)

      try {
        retriever.setDataSource(context, videoUri)

        for (tsMs in sampleTimesMs) {
          val timeUs = tsMs * 1000L
          val raw =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
              retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                288,
                512,
              )
            } else {
              retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let {
                val scale = 288f / max(1, it.width)
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

          val fingerprint = SubjectMaskProcessor.computeFrameFingerprint16x16(raw)
          val subjectMetrics = SubjectMaskProcessor.analyzeSubjectMetrics(raw)

          // Ensure even width for Android FaceDetector
          val evenW = (raw.width / 2) * 2
          val evenH = (raw.height / 2) * 2
          val rgb565 = raw.copy(Bitmap.Config.RGB_565, false)
          val detectorBitmap =
            if (rgb565.width != evenW || rgb565.height != evenH) {
              Bitmap.createBitmap(rgb565, 0, 0, evenW, evenH).also { rgb565.recycle() }
            } else {
              rgb565
            }

          // 1. ML Kit FaceDetector (primary: exact boundingBox + headEulerAngleZ roll angle for parallax)
          val frameFaces = mutableListOf<NormalizedRect>()
          var hasBottomHalfFace = false
          val rawW = raw.width.coerceAtLeast(1).toFloat()
          val rawH = raw.height.coerceAtLeast(1).toFloat()
          val mlFaces = detectMlKitFaces(raw)
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
          } else {
            // Fallback to Android FaceDetector if ML Kit finds no frontal face on this frame
            val faces = arrayOfNulls<FaceDetector.Face>(3)
            val found = FaceDetector(detectorBitmap.width, detectorBitmap.height, 3).findFaces(detectorBitmap, faces)
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
          }

          val mergedHeadVeto = mergeHeadVetoRects(frameFaces, subjectMetrics.headVetoRect)

          // 2. Per-zone edge variance (clutter), mean luminance, histogram, and split-seam check
          val w = detectorBitmap.width
          val h = detectorBitmap.height
          val pixels = IntArray(w * h)
          detectorBitmap.getPixels(pixels, 0, w, 0, 0, w, h)

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

          detectorBitmap.recycle()
          raw.recycle()
        }
      } catch (_: Exception) {
        // Fallback safely
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
