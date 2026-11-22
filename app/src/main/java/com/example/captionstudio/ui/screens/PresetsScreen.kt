package com.example.captionstudio.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.captionstudio.domain.model.PresetDefinition
import com.example.captionstudio.domain.model.SafeRegion
import com.example.captionstudio.domain.model.VideoAnalysisReport
import com.example.captionstudio.engine.KineticCompositionRenderer
import com.example.captionstudio.engine.LocalCompositionEngine
import com.example.captionstudio.presets.PresetCatalog
import com.example.captionstudio.ui.components.StudioGlassCard
import com.example.captionstudio.ui.components.StudioMinimalTopBar
import com.example.captionstudio.ui.components.studioPressScale
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralContainer
import com.example.ui.theme.StudioCoralLight
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary

@Composable
fun PresetsScreen(
  activePresetId: String,
  onApplyPreset: (String) -> Unit,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val renderer = remember(context) { KineticCompositionRenderer(context) }

  Scaffold(
    containerColor = StudioObsidian,
    topBar = {
      StudioMinimalTopBar(
        title = "Caption Styles",
        subtitle = "Preserves transcript & timings",
        onBack = onBack,
        backButtonTag = "presets_back_button",
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding).navigationBarsPadding(),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 660.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        items(PresetCatalog.presets, key = { it.id }) { preset ->
          PresetPreviewCard(
            preset = preset,
            isSelected = preset.id == activePresetId,
            renderer = renderer,
            onApply = {
              onApplyPreset(preset.id)
              onBack()
            },
          )
        }
      }
    }
  }
}

@Composable
private fun PresetPreviewCard(
  preset: PresetDefinition,
  isSelected: Boolean,
  renderer: KineticCompositionRenderer,
  onApply: () -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val previewComp =
    remember(preset.id) {
      val timings = LocalCompositionEngine.buildProvisionalWordTimings(preset.sampleSentence, 3000L)
      LocalCompositionEngine.generateCompositions(
          transcript = preset.sampleSentence,
          wordTimings = timings,
          totalDurationMs = 3000L,
          preset = preset,
          analysisReport =
            VideoAnalysisReport(safeRegionsRanked = listOf(SafeRegion.CENTER)),
        )
        .map { it.copy(anchorXFraction = 0.5f, anchorYFraction = 0.5f, entryDurationMs = 0) }
    }

  val displayName =
    remember(preset.name) { preset.name.lowercase().replaceFirstChar { it.uppercase() } }

  StudioGlassCard(
    modifier =
      Modifier.fillMaxWidth()
        .studioPressScale(interactionSource = interactionSource)
        .clickable(
          interactionSource = interactionSource,
          indication = ripple(bounded = true, color = Color.White),
          onClick = onApply,
        )
        .testTag("preset_card_${preset.id}"),
    cornerRadius = 22.dp,
    containerColor = if (isSelected) Color(0xFF171417) else StudioCard,
    borderColor = if (isSelected) StudioCoral else StudioHairline,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.width(120.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
              Brush.verticalGradient(listOf(Color(0xFF1F1E26), Color(0xFF0D0D12)))
            )
            .border(1.dp, StudioHairline, RoundedCornerShape(16.dp))
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          drawIntoCanvas { canvas ->
            renderer.drawAtTime(
              canvas = canvas.nativeCanvas,
              canvasWidth = size.width.toInt(),
              canvasHeight = size.height.toInt(),
              compositions = previewComp,
              positionMs = 1500L,
              shadowMultiplier = preset.shadowRadiusFactor,
            )
          }
        }
      }

      Spacer(Modifier.width(16.dp))

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = displayName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) StudioCoralLight else StudioTextPrimary,
          )
          if (isSelected) {
            Surface(color = StudioCoralContainer, shape = CircleShape) {
              Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  Icons.Rounded.Check,
                  contentDescription = null,
                  tint = StudioCoralLight,
                  modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Active", style = MaterialTheme.typography.labelSmall, color = StudioCoralLight)
              }
            }
          }
        }

        Text(
          text = preset.tagline,
          style = MaterialTheme.typography.labelMedium,
          color = StudioCoralLight,
        )
        Text(
          text = preset.description,
          style = MaterialTheme.typography.bodySmall,
          color = StudioTextSecondary,
        )

        Spacer(Modifier.height(2.dp))

        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          listOf(
            preset.primaryColorHex,
            preset.dominantColorHex,
            preset.expressiveColorHex,
            preset.dramaticColorHex,
          ).distinct().forEach { hex ->
            Box(
              modifier =
                Modifier.size(16.dp)
                  .clip(CircleShape)
                  .background(Color(android.graphics.Color.parseColor(hex)))
                  .border(1.dp, StudioHairline, CircleShape)
            )
          }
          Spacer(Modifier.weight(1f))
          Button(
            onClick = onApply,
            shape = CircleShape,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = if (isSelected) StudioElevated else StudioCoral,
                contentColor = Color.White,
              ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            modifier = Modifier.height(36.dp).testTag("apply_preset_${preset.id}"),
          ) {
            Text(
              if (isSelected) "Re-Apply" else "Apply",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }
  }
}
