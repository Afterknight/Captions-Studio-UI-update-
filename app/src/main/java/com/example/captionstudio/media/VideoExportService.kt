package com.example.captionstudio.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.ExportPreferences
import com.example.captionstudio.domain.model.ExportResolution
import com.example.captionstudio.engine.KineticCompositionRenderer
import com.example.captionstudio.presets.PresetCatalog
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ExportUiState {
  data object Idle : ExportUiState
  data class Exporting(val progressPercent: Int, val stageLabel: String) : ExportUiState
  data class Completed(
    val galleryUri: Uri,
    val fileName: String,
    val fileSizeBytes: Long,
    val verifiedDurationMs: Long,
    val hasAudio: Boolean,
  ) : ExportUiState

  data class Failed(val message: String) : ExportUiState
}

class VideoExportService(private val context: Context) {

  private val _state = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
  val state: StateFlow<ExportUiState> = _state.asStateFlow()

  private var activeTransformer: Transformer? = null
  private var activeTempFile: File? = null
  private var activeMaskProvider: SubjectMaskProcessor.ExportMaskProvider? = null
  private val mainHandler = Handler(Looper.getMainLooper())
  private var progressRunnable: Runnable? = null

  @OptIn(UnstableApi::class)
  fun startExport(
    videoUri: Uri,
    compositions: List<CaptionComposition>,
    presetId: String,
    exportPrefs: ExportPreferences,
    outputTitle: String,
  ) {
    if (videoUri == Uri.EMPTY) {
      _state.value = ExportUiState.Failed("Import a video before exporting an MP4 file.")
      return
    }

    cancelExport()
    val preset = PresetCatalog.findById(presetId)
    val renderer = KineticCompositionRenderer(context)
    val safeName =
      outputTitle.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "CaptionStudio" } +
        "_${System.currentTimeMillis()}.mp4"
    val tempOutFile = File(context.cacheDir, "export_$safeName")
    if (tempOutFile.exists()) tempOutFile.delete()
    activeTempFile = tempOutFile

    val needsSubjectMask = compositions.any { it.hasBehindSubjectElements() }
    val maskProvider =
      if (needsSubjectMask) SubjectMaskProcessor.ExportMaskProvider(context, videoUri) else null
    activeMaskProvider = maskProvider

    _state.value = ExportUiState.Exporting(0, "Preparing hardware video pipeline...")

    mainHandler.post {
      try {
        val canvasOverlay =
          object : CanvasOverlay(/* useInputFrameSize = */ true) {
            private var width = 1080
            private var height = 1920

            override fun configure(videoSize: Size) {
              super.configure(videoSize)
              width = videoSize.width.coerceAtLeast(1)
              height = videoSize.height.coerceAtLeast(1)
            }

            override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
              canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
              val positionMs = presentationTimeUs / 1000L
              val activeComp = compositions.firstOrNull { positionMs in it.startMs..it.endMs }
              val subjectMask =
                if (activeComp?.hasBehindSubjectElements() == true) {
                  maskProvider?.getMaskAtTimeUs(presentationTimeUs)
                } else {
                  null
                }
              renderer.drawWithSubjectMask(
                canvas = canvas,
                canvasWidth = width,
                canvasHeight = height,
                compositions = compositions,
                positionMs = positionMs,
                subjectMaskBitmap = subjectMask,
                wrapInOffscreenLayer = false,
                shadowMultiplier = preset.shadowRadiusFactor,
                drawSelectionGuide = false,
              )
            }
          }

        val videoEffects = mutableListOf<Effect>()
        val targetAspect =
          exportPrefs.aspectRatio.ratioWidth / exportPrefs.aspectRatio.ratioHeight
        videoEffects.add(
          Presentation.createForAspectRatio(targetAspect, Presentation.LAYOUT_SCALE_TO_FIT)
        )
        if (exportPrefs.resolution != ExportResolution.RES_SOURCE) {
          val heightTarget =
            if (exportPrefs.aspectRatio.ratioWidth > exportPrefs.aspectRatio.ratioHeight) {
              exportPrefs.resolution.shortEdgePx
            } else if (exportPrefs.aspectRatio.ratioWidth == exportPrefs.aspectRatio.ratioHeight) {
              exportPrefs.resolution.shortEdgePx
            } else {
              (exportPrefs.resolution.shortEdgePx * 16) / 9
            }
          videoEffects.add(Presentation.createForHeight(heightTarget))
        }
        videoEffects.add(
          FrameDropEffect.createDefaultFrameDropEffect(exportPrefs.frameRate.fps.toFloat())
        )
        videoEffects.add(OverlayEffect(listOf(canvasOverlay)))

        val mediaItem = MediaItem.fromUri(videoUri)
        val editedItem =
          EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val encoderSettings =
          VideoEncoderSettings.Builder()
            .setBitrate(exportPrefs.qualityMode.bitrateMbps * 1_000_000)
            .build()
        val encoderFactory =
          DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(encoderSettings)
            .setEnableFallback(true)
            .build()

        val transformer =
          Transformer.Builder(context)
            .setEncoderFactory(encoderFactory)
            .addListener(
              object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                  stopProgressPolling()
                  activeMaskProvider?.release()
                  activeMaskProvider = null
                  verifyAndSaveToGallery(tempOutFile, safeName)
                }

                override fun onError(
                  composition: Composition,
                  result: ExportResult,
                  exception: ExportException,
                ) {
                  stopProgressPolling()
                  activeMaskProvider?.release()
                  activeMaskProvider = null
                  tempOutFile.delete()
                  _state.value =
                    ExportUiState.Failed(
                      exception.localizedMessage ?: "Hardware MP4 export failed."
                    )
                }
              }
            )
            .build()

        activeTransformer = transformer
        transformer.start(editedItem, tempOutFile.absolutePath)
        startProgressPolling(transformer)
      } catch (e: Exception) {
        activeMaskProvider?.release()
        activeMaskProvider = null
        tempOutFile.delete()
        _state.value =
          ExportUiState.Failed(e.localizedMessage ?: "Failed to initialize video export.")
      }
    }
  }

  private fun startProgressPolling(transformer: Transformer) {
    stopProgressPolling()
    val holder = ProgressHolder()
    val runnable =
      object : Runnable {
        override fun run() {
          val stateCode = transformer.getProgress(holder)
          if (stateCode == Transformer.PROGRESS_STATE_AVAILABLE) {
            _state.value =
              ExportUiState.Exporting(
                progressPercent = holder.progress.coerceIn(0, 99),
                stageLabel = "Rendering kinetic typography & audio...",
              )
          }
          if (stateCode != Transformer.PROGRESS_STATE_NOT_STARTED) {
            mainHandler.postDelayed(this, 250L)
          }
        }
      }
    progressRunnable = runnable
    mainHandler.postDelayed(runnable, 200L)
  }

  private fun stopProgressPolling() {
    progressRunnable?.let { mainHandler.removeCallbacks(it) }
    progressRunnable = null
  }

  private fun verifyAndSaveToGallery(tempFile: File, fileName: String) {
    Thread {
      try {
        if (!tempFile.exists() || tempFile.length() < 1024L) {
          tempFile.delete()
          _state.value = ExportUiState.Failed("Exported MP4 verification failed (empty output).")
          return@Thread
        }

        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var hasAudio = false
        try {
          retriever.setDataSource(tempFile.absolutePath)
          durationMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
              ?: 0L
          hasAudio =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        } finally {
          runCatching { retriever.release() }
        }

        if (durationMs <= 0L) {
          tempFile.delete()
          _state.value = ExportUiState.Failed("Exported file did not contain a valid video track.")
          return@Thread
        }

        val values =
          ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
              put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MOVIES}/CaptionStudio",
              )
              put(MediaStore.Video.Media.IS_PENDING, 1)
            }
          }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
          ?: throw IllegalStateException("Could not create MediaStore entry.")

        resolver.openOutputStream(uri)?.use { out ->
          FileInputStream(tempFile).use { input -> input.copyTo(out) }
        } ?: throw IllegalStateException("Could not write to gallery output stream.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          values.clear()
          values.put(MediaStore.Video.Media.IS_PENDING, 0)
          resolver.update(uri, values, null, null)
        }

        val sizeBytes = tempFile.length()
        tempFile.delete()
        _state.value =
          ExportUiState.Completed(
            galleryUri = uri,
            fileName = fileName,
            fileSizeBytes = sizeBytes,
            verifiedDurationMs = durationMs,
            hasAudio = hasAudio,
          )
      } catch (e: Exception) {
        tempFile.delete()
        _state.value =
          ExportUiState.Failed(e.localizedMessage ?: "Failed saving verified MP4 to Gallery.")
      }
    }.start()
  }

  fun cancelExport() {
    stopProgressPolling()
    activeMaskProvider?.release()
    activeMaskProvider = null
    mainHandler.post {
      runCatching { activeTransformer?.cancel() }
      activeTransformer = null
    }
    activeTempFile?.delete()
    activeTempFile = null
    if (_state.value is ExportUiState.Exporting) {
      _state.value = ExportUiState.Idle
    }
  }

  fun resetState() {
    _state.value = ExportUiState.Idle
  }
}
