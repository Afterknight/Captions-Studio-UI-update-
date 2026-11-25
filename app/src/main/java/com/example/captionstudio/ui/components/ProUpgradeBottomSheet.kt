package com.example.captionstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.MovieFilter
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralDeep
import com.example.ui.theme.StudioCoralLight
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioHairlineStrong
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary

/**
 * Fair, non-pushy "Caption Studio Pro" bottom sheet.
 * Clearly highlights that all core offline captioning, behind-subject depth, 36 presets,
 * and 1080p export are 100% Free Forever, alongside optional Pro perks and a local
 * "Preview Pro State" debug switch for testing before Google Play Billing is wired.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProUpgradeBottomSheet(
  isProUnlocked: Boolean,
  onToggleProPreview: (Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = StudioCard,
    contentColor = StudioTextPrimary,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    dragHandle = {
      BottomSheetDefaults.DragHandle(color = StudioHairlineStrong)
    },
    modifier = Modifier.testTag("pro_upgrade_bottom_sheet"),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .padding(bottom = 22.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Header Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier =
              Modifier.size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                  Brush.linearGradient(
                    if (isProUnlocked) {
                      listOf(StudioSuccess.copy(alpha = 0.28f), StudioElevated)
                    } else {
                      listOf(StudioCoralLight.copy(alpha = 0.28f), StudioCoralDeep.copy(alpha = 0.16f))
                    }
                  )
                )
                .border(
                  1.dp,
                  if (isProUnlocked) StudioSuccess.copy(alpha = 0.55f) else StudioCoral.copy(alpha = 0.50f),
                  RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = if (isProUnlocked) Icons.Rounded.Verified else Icons.Rounded.AutoAwesome,
              contentDescription = null,
              tint = if (isProUnlocked) StudioSuccess else StudioCoralLight,
              modifier = Modifier.size(22.dp),
            )
          }

          Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
              text = stringResource(R.string.pro_sheet_title),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = StudioTextPrimary,
            )
            StudioSubtlePill(
              text =
                stringResource(
                  if (isProUnlocked) R.string.pro_sheet_badge_active else R.string.pro_sheet_badge_free
                ),
              tint = if (isProUnlocked) StudioSuccess else StudioCoralLight,
              containerColor =
                if (isProUnlocked) StudioSuccess.copy(alpha = 0.14f) else StudioElevated,
            )
          }
        }

        IconButton(
          onClick = onDismiss,
          modifier = Modifier.size(48.dp).testTag("pro_sheet_close_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.pro_sheet_close_cd),
            tint = StudioTextSecondary,
          )
        }
      }

      // Honest Free Forever Guarantee Card
      StudioGlassCard(
        modifier = Modifier.fillMaxWidth().testTag("pro_sheet_free_promise_card"),
        cornerRadius = 18.dp,
        containerColor = StudioSuccess.copy(alpha = 0.08f),
        borderColor = StudioSuccess.copy(alpha = 0.35f),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = StudioSuccess,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
          )
          Text(
            text = stringResource(R.string.pro_sheet_free_promise),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = StudioTextPrimary,
          )
        }
      }

      // Clean List of Optional Pro Perks
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = stringResource(R.string.pro_sheet_perks_header),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = StudioTextSecondary,
        )

        ProPerkRow(
          icon = Icons.Rounded.HideImage,
          title = stringResource(R.string.pro_perk_watermark_title),
          description = stringResource(R.string.pro_perk_watermark_desc),
          unlocked = isProUnlocked,
        )
        ProPerkRow(
          icon = Icons.Rounded.HighQuality,
          title = stringResource(R.string.pro_perk_fps_title),
          description = stringResource(R.string.pro_perk_fps_desc),
          unlocked = isProUnlocked,
        )
        ProPerkRow(
          icon = Icons.Rounded.MovieFilter,
          title = stringResource(R.string.pro_perk_ai_title),
          description = stringResource(R.string.pro_perk_ai_desc),
          unlocked = isProUnlocked,
        )
      }

      // Calm, Non-Pushy Action Area
      Text(
        text = stringResource(R.string.pro_sheet_billing_note),
        style = MaterialTheme.typography.bodySmall,
        color = StudioTextSecondary,
      )

      if (isProUnlocked) {
        StudioCoralPillButton(
          text = stringResource(R.string.pro_sheet_done),
          showTrailingArrow = false,
          onClick = onDismiss,
          modifier = Modifier.testTag("pro_sheet_done_button"),
        )
      } else {
        OutlinedButton(
          onClick = onDismiss,
          shape = CircleShape,
          contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
          modifier =
            Modifier.fillMaxWidth()
              .heightIn(min = 52.dp)
              .testTag("pro_sheet_continue_free_button"),
        ) {
          Text(
            text = stringResource(R.string.pro_sheet_continue_free),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = StudioTextPrimary,
          )
        }
      }

      HorizontalDivider(color = StudioHairline)

      // Subtle Debug Switch to Preview Both Free and Pro States Before Play Billing
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StudioObsidian.copy(alpha = 0.6f))
            .border(1.dp, StudioHairline, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
          Text(
            text = stringResource(R.string.pro_sheet_preview_switch_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = StudioTextPrimary,
          )
          Text(
            text = stringResource(R.string.pro_sheet_preview_switch_desc),
            style = MaterialTheme.typography.labelSmall,
            color = StudioTextSecondary,
          )
        }
        Switch(
          checked = isProUnlocked,
          onCheckedChange = onToggleProPreview,
          colors =
            SwitchDefaults.colors(
              checkedThumbColor = Color.White,
              checkedTrackColor = StudioSuccess,
            ),
          modifier = Modifier.testTag("pro_preview_debug_switch"),
        )
      }
    }
  }
}

@Composable
private fun ProPerkRow(
  icon: ImageVector,
  title: String,
  description: String,
  unlocked: Boolean,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(StudioElevated)
        .border(1.dp, StudioHairline, RoundedCornerShape(16.dp))
        .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier =
        Modifier.size(36.dp)
          .clip(CircleShape)
          .background(if (unlocked) StudioSuccess.copy(alpha = 0.14f) else StudioCoral.copy(alpha = 0.14f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (unlocked) StudioSuccess else StudioCoralLight,
        modifier = Modifier.size(18.dp),
      )
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = StudioTextPrimary,
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = StudioTextSecondary,
      )
    }
    if (unlocked) {
      Spacer(Modifier.width(4.dp))
      Icon(
        imageVector = Icons.Rounded.CheckCircle,
        contentDescription = null,
        tint = StudioSuccess,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}
