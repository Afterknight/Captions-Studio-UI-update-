package com.example.captionstudio.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.ExportPreferences
import com.example.captionstudio.domain.model.FaceAnchorSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Process-scoped coordinator bridging [VideoExportService], [VideoExportForegroundService], and
 * the Compose UI so hardware Media3 exports survive screen locking and background app switching.
 */
object VideoExportCoordinator {
  @Volatile private var serviceInstance: VideoExportService? = null

  data class PendingExportJob(
    val videoUri: Uri,
    val compositions: List<CaptionComposition>,
    val presetId: String,
    val exportPrefs: ExportPreferences,
    val outputTitle: String,
    val faceAnchorSamples: List<FaceAnchorSample> = emptyList(),
    val audioEnergyEnvelope10ms: FloatArray? = null,
  )

  @Volatile var pendingJob: PendingExportJob? = null

  fun engine(context: Context): VideoExportService {
    return serviceInstance
      ?: synchronized(this) {
        serviceInstance
          ?: VideoExportService(context.applicationContext).also { serviceInstance = it }
      }
  }

  fun state(context: Context): StateFlow<ExportUiState> = engine(context).state

  fun startForegroundExport(
    context: Context,
    videoUri: Uri,
    compositions: List<CaptionComposition>,
    presetId: String,
    exportPrefs: ExportPreferences,
    outputTitle: String,
    faceAnchorSamples: List<FaceAnchorSample> = emptyList(),
    audioEnergyEnvelope10ms: FloatArray? = null,
  ) {
    val appCtx = context.applicationContext
    val job =
      PendingExportJob(
        videoUri = videoUri,
        compositions = compositions,
        presetId = presetId,
        exportPrefs = exportPrefs,
        outputTitle = outputTitle,
        faceAnchorSamples = faceAnchorSamples,
        audioEnergyEnvelope10ms = audioEnergyEnvelope10ms,
      )
    pendingJob = job

    val intent =
      Intent(appCtx, VideoExportForegroundService::class.java).apply {
        action = VideoExportForegroundService.ACTION_START_EXPORT
      }
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        appCtx.startForegroundService(intent)
      } else {
        appCtx.startService(intent)
      }
    } catch (_: Throwable) {
      // Safe fallback (e.g., Robolectric or restricted background start): execute directly in-process
      pendingJob = null
      engine(appCtx).startExport(
        videoUri = videoUri,
        compositions = compositions,
        presetId = presetId,
        exportPrefs = exportPrefs,
        outputTitle = outputTitle,
        faceAnchorSamples = faceAnchorSamples,
        audioEnergyEnvelope10ms = audioEnergyEnvelope10ms,
      )
    }
  }

  fun cancelExport(context: Context) {
    val appCtx = context.applicationContext
    pendingJob = null
    engine(appCtx).cancelExport()
    runCatching {
      appCtx.stopService(Intent(appCtx, VideoExportForegroundService::class.java))
    }
  }

  fun resetState(context: Context) {
    engine(context.applicationContext).resetState()
  }
}

/**
 * Play-Policy compliant `mediaProcessing` ForegroundService keeping Media3 Transformer exports
 * alive with a live progress + cancel notification when the user switches apps or locks the screen.
 */
class VideoExportForegroundService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var observerStarted = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    ensureNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_CANCEL_EXPORT -> {
        VideoExportCoordinator.engine(this).cancelExport()
        stopForegroundCompat(removeNotification = true)
        stopSelf()
        return START_NOT_STICKY
      }

      ACTION_START_EXPORT -> {
        val job = VideoExportCoordinator.pendingJob
        VideoExportCoordinator.pendingJob = null
        if (job == null) {
          stopSelf()
          return START_NOT_STICKY
        }

        val initialNotification =
          buildProgressNotification(
            title = "Exporting ${job.outputTitle.ifBlank { "Video" }}",
            stage = "Preparing hardware video pipeline…",
            percent = 0,
          )
        startForegroundCompat(initialNotification)
        observeExportState(job.outputTitle.ifBlank { "Caption Studio Video" })

        VideoExportCoordinator.engine(this).startExport(
          videoUri = job.videoUri,
          compositions = job.compositions,
          presetId = job.presetId,
          exportPrefs = job.exportPrefs,
          outputTitle = job.outputTitle,
          faceAnchorSamples = job.faceAnchorSamples,
          audioEnergyEnvelope10ms = job.audioEnergyEnvelope10ms,
        )
      }
    }
    return START_NOT_STICKY
  }

  private fun observeExportState(title: String) {
    if (observerStarted) return
    observerStarted = true
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    serviceScope.launch {
      VideoExportCoordinator.state(this@VideoExportForegroundService).collect { st ->
        when (st) {
          is ExportUiState.Exporting -> {
            nm.notify(
              NOTIF_ID_PROGRESS,
              buildProgressNotification(
                title = "Exporting $title",
                stage = st.stageLabel,
                percent = st.progressPercent,
              ),
            )
          }

          is ExportUiState.Completed -> {
            stopForegroundCompat(removeNotification = true)
            nm.notify(
              NOTIF_ID_COMPLETE,
              buildCompletedNotification(
                title = "Saved to Gallery!",
                detail = "${st.fileName} • Ready to share to Reels & TikTok",
              ),
            )
            observerStarted = false
            stopSelf()
          }

          is ExportUiState.Failed -> {
            stopForegroundCompat(removeNotification = true)
            nm.notify(
              NOTIF_ID_COMPLETE,
              buildCompletedNotification(
                title = "Export Interrupted",
                detail = st.message,
              ),
            )
            observerStarted = false
            stopSelf()
          }

          ExportUiState.Idle -> Unit
        }
      }
    }
  }

  private fun startForegroundCompat(notification: Notification) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(
        NOTIF_ID_PROGRESS,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
      )
    } else {
      startForeground(NOTIF_ID_PROGRESS, notification)
    }
  }

  private fun stopForegroundCompat(removeNotification: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
    } else {
      @Suppress("DEPRECATION")
      stopForeground(removeNotification)
    }
  }

  private fun buildProgressNotification(
    title: String,
    stage: String,
    percent: Int,
  ): Notification {
    val openIntent =
      PendingIntent.getActivity(
        this,
        101,
        Intent(this, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val cancelIntent =
      PendingIntent.getService(
        this,
        102,
        Intent(this, VideoExportForegroundService::class.java).apply {
          action = ACTION_CANCEL_EXPORT
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("$title • ${percent.coerceIn(0, 100)}%")
      .setContentText(stage)
      .setOnlyAlertOnce(true)
      .setOngoing(true)
      .setSilent(true)
      .setProgress(100, percent.coerceIn(0, 100), percent <= 1)
      .setContentIntent(openIntent)
      .addAction(0, "Cancel", cancelIntent)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  private fun buildCompletedNotification(title: String, detail: String): Notification {
    val openIntent =
      PendingIntent.getActivity(
        this,
        103,
        Intent(this, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(title)
      .setContentText(detail)
      .setAutoCancel(true)
      .setContentIntent(openIntent)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .build()
  }

  private fun ensureNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          "Video Exports",
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = "Live progress while rendering kinetic typography MP4 videos"
          setShowBadge(false)
        }
      nm.createNotificationChannel(channel)
    }
  }

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }

  companion object {
    const val ACTION_START_EXPORT = "com.example.captionstudio.action.START_EXPORT"
    const val ACTION_CANCEL_EXPORT = "com.example.captionstudio.action.CANCEL_EXPORT"
    private const val CHANNEL_ID = "caption_studio_export_channel"
    private const val NOTIF_ID_PROGRESS = 4401
    private const val NOTIF_ID_COMPLETE = 4402
  }
}
