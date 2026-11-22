package com.example.captionstudio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.captionstudio.media.AutoTranscribeUiState
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralLight
import com.example.ui.theme.StudioError
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.StudioWarning
import kotlin.math.max

/**
 * Displays the extracted video audio waveform with playhead scrubbing and one-tap
 * Offline / Online Gemini millisecond-accurate auto-transcription controls.
 */
@Composable
fun AutoTranscribeControlCard(
  hasVideo: Boolean,
  hasAudio: Boolean,
  geminiEnabled: Boolean,
  transcribeState: AutoTranscribeUiState,
  playheadMs: Long,
  totalDurationMs: Long,
  onSeekMs: (Long) -> Unit,
  onAutoTranscribeOffline: () -> Unit,
  onAutoTranscribeOnline: () -> Unit,
  modifier: Modifier = Modifier,
  activePackLabel: String? = null,
  isOfflinePackInstalled: Boolean = true,
  isDownloadingPack: Boolean = false,
  packDownloadProgress: Float = 0f,
  onDownloadOfflinePack: (() -> Unit)? = null,
) {
  StudioGlassCard(modifier = modifier.fillMaxWidth(), cornerRadius = 20.dp) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(
            Icons.Filled.GraphicEq,
            contentDescription = null,
            tint = StudioCoral,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text = "Auto-Transcribe Audio",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = StudioTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (transcribeState.waveformBins.isNotEmpty()) {
          StudioSubtlePill(text = "10ms sync", tint = StudioSuccess)
        } else if (isOfflinePackInstalled) {
          StudioSubtlePill(text = "Offline ready", tint = StudioSuccess)
        }
      }

      if (!isOfflinePackInstalled && onDownloadOfflinePack != null) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(StudioObsidian)
              .border(1.dp, StudioCoral.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
              .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = activePackLabel ?: "Offline Voice Pack",
              style = MaterialTheme.typography.labelLarge,
              color = StudioCoralLight,
            )
            Text(
              text =
                if (isDownloadingPack) "Downloading (${(packDownloadProgress * 100).toInt()}%)..."
                else "One-time install (~40 MB) for instant offline transcription.",
              style = MaterialTheme.typography.bodySmall,
              color = StudioTextSecondary,
            )
          }
          OutlinedButton(
            onClick = onDownloadOfflinePack,
            enabled = !isDownloadingPack,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.height(38.dp),
          ) {
            Text(
              text = if (isDownloadingPack) "..." else "Install",
              style = MaterialTheme.typography.labelMedium,
              color = StudioTextPrimary,
              maxLines = 1,
              softWrap = false,
            )
          }
        }
      }

      if (transcribeState.waveformBins.isNotEmpty()) {
        AudioWaveformStrip(
          bins = transcribeState.waveformBins,
          playheadMs = playheadMs,
          totalDurationMs = max(1L, totalDurationMs),
          onSeekMs = onSeekMs,
        )
      }

      AnimatedVisibility(visible = transcribeState.isRunning) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = transcribeState.stageLabel.ifBlank { "Transcribing audio..." },
            style = MaterialTheme.typography.labelSmall,
            color = StudioCoralLight,
          )
          LinearProgressIndicator(
            progress = { transcribeState.progressFraction.coerceIn(0f, 1f) },
            color = StudioCoral,
            trackColor = StudioObsidian,
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
          )
        }
      }

      if (!transcribeState.isRunning && !transcribeState.lastError.isNullOrBlank()) {
        Text(
          text = transcribeState.lastError,
          style = MaterialTheme.typography.bodySmall,
          color = StudioError,
        )
      } else if (!transcribeState.isRunning && !transcribeState.lastSummary.isNullOrBlank()) {
        Text(
          text = transcribeState.lastSummary,
          style = MaterialTheme.typography.bodySmall,
          color = StudioTextSecondary,
        )
      } else if (!hasVideo) {
        Text(
          text = "Attach a video to automatically extract speech and millisecond word timings.",
          style = MaterialTheme.typography.bodySmall,
          color = StudioTextSecondary,
        )
      } else if (!hasAudio) {
        Text(
          text = "This video does not contain an audio track.",
          style = MaterialTheme.typography.bodySmall,
          color = StudioWarning,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = onAutoTranscribeOffline,
          enabled = hasVideo && hasAudio && !transcribeState.isRunning,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = StudioCoral,
              contentColor = Color.White,
            ),
          shape = CircleShape,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier.weight(1f).height(44.dp).testTag("auto_transcribe_offline_button"),
        ) {
          Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(
            "Transcribe Offline",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
          )
        }

        OutlinedButton(
          onClick = onAutoTranscribeOnline,
          enabled = hasVideo && hasAudio && geminiEnabled && !transcribeState.isRunning,
          shape = CircleShape,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier.weight(1f).height(44.dp).testTag("auto_transcribe_online_button"),
        ) {
          Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = if (geminiEnabled) StudioCoral else StudioTextSecondary,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(
            "AI Sync",
            style = MaterialTheme.typography.labelMedium,
            color = if (geminiEnabled) StudioTextPrimary else StudioTextSecondary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
fun AudioWaveformStrip(
  bins: List<Float>,
  playheadMs: Long,
  totalDurationMs: Long,
  onSeekMs: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val progress = (playheadMs.toFloat() / totalDurationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(38.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(StudioObsidian)
        .border(1.dp, StudioHairline, RoundedCornerShape(12.dp))
        .pointerInput(totalDurationMs) {
          detectTapGestures { offset ->
            if (size.width > 0) {
              val frac = (offset.x / size.width).coerceIn(0f, 1f)
              onSeekMs((frac * totalDurationMs).toLong())
            }
          }
        }
        .padding(horizontal = 8.dp, vertical = 6.dp),
  ) {
    Canvas(modifier = Modifier.fillMaxWidth().height(26.dp)) {
      val count = bins.size.coerceAtLeast(1)
      val slotW = size.width / count
      val barW = (slotW * 0.54f).coerceAtLeast(2f)
      val midY = size.height / 2f

      for (i in 0 until count) {
        val norm = bins[i].coerceIn(0.08f, 1f)
        val barH = max(3f, size.height * norm)
        val x = i * slotW + (slotW - barW) / 2f
        val barFrac = i.toFloat() / count
        val color =
          if (barFrac <= progress) StudioCoral else StudioTextSecondary.copy(alpha = 0.35f)
        drawRoundRect(
          color = color,
          topLeft = Offset(x, midY - barH / 2f),
          size = Size(barW, barH),
          cornerRadius = CornerRadius(4f, 4f),
        )
      }

      val cursorX = (progress * size.width).coerceIn(0f, size.width)
      drawLine(
        color = Color.White,
        start = Offset(cursorX, 0f),
        end = Offset(cursorX, size.height),
        strokeWidth = 2.5f,
      )
    }
  }
}
