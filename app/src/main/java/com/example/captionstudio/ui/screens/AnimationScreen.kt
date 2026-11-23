package com.example.captionstudio.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
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
import com.example.captionstudio.domain.model.AnimationEasingType
import com.example.captionstudio.domain.model.ApplyScope
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.CaptionProject
import com.example.captionstudio.domain.model.CompositionElement
import com.example.captionstudio.domain.model.EntryAnimationType
import com.example.captionstudio.domain.model.ExitAnimationType
import com.example.captionstudio.domain.model.FontRole
import com.example.captionstudio.domain.model.HierarchyLevel
import com.example.captionstudio.domain.model.SafeRegion
import com.example.captionstudio.domain.model.StudioPalette
import com.example.captionstudio.engine.KineticCompositionRenderer
import com.example.captionstudio.ui.components.StudioGlassCard
import com.example.captionstudio.ui.components.StudioMinimalTopBar
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralContainer
import com.example.ui.theme.StudioCoralLight
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimationScreen(
  project: CaptionProject?,
  selectedCompositionId: String?,
  onApplyAnimation: (
    compId: String,
    scope: ApplyScope,
    entry: EntryAnimationType,
    exit: ExitAnimationType,
    easing: AnimationEasingType,
    entryDurationMs: Int,
    exitDurationMs: Int,
  ) -> Unit,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val renderer = remember(context) { KineticCompositionRenderer(context) }

  val activeComp =
    project?.compositions?.firstOrNull { it.id == selectedCompositionId }
      ?: project?.compositions?.firstOrNull()

  var scope by remember { mutableStateOf(ApplyScope.ALL_COMPOSITIONS) }
  var entry by remember(activeComp?.id) {
    mutableStateOf(activeComp?.entryAnimation ?: EntryAnimationType.SCALE_RISE)
  }
  var exit by remember(activeComp?.id) {
    mutableStateOf(activeComp?.exitAnimation ?: ExitAnimationType.FADE_DROP)
  }
  var easing by remember(activeComp?.id) {
    mutableStateOf(activeComp?.easing ?: AnimationEasingType.DECELERATE)
  }
  var entryDurationMs by remember(activeComp?.id) {
    mutableIntStateOf(activeComp?.entryDurationMs ?: 160)
  }
  var exitDurationMs by remember(activeComp?.id) {
    mutableIntStateOf(activeComp?.exitDurationMs ?: 130)
  }

  var loopTimeMs by remember { mutableLongStateOf(0L) }
  LaunchedEffect(Unit) {
    val startNanos = System.nanoTime()
    while (true) {
      withFrameMillis {
        val elapsed = (System.nanoTime() - startNanos) / 1_000_000L
        loopTimeMs = elapsed % 2400L
      }
    }
  }

  val previewCompositions =
    remember(entry, exit, easing, entryDurationMs, exitDurationMs, activeComp) {
      val baseElements =
        activeComp?.elements?.take(3)
          ?: listOf(
            CompositionElement(
              id = "p1",
              text = "especially",
              role = FontRole.SUPPORTING,
              hierarchyLevel = HierarchyLevel.SUPPORTING,
              fontFamilyId = "inter",
              fontSizeSp = 19f,
              colorHex = StudioPalette.WHITE,
              staggerDelayMs = 45,
            ),
            CompositionElement(
              id = "p2",
              text = "text",
              role = FontRole.EXPRESSIVE,
              hierarchyLevel = HierarchyLevel.EXPRESSIVE,
              fontFamilyId = "playfair_display",
              isItalic = true,
              fontSizeSp = 30f,
              colorHex = StudioPalette.ORANGE,
              staggerDelayMs = 70,
            ),
            CompositionElement(
              id = "p3",
              text = "THIS",
              role = FontRole.HEAVY,
              hierarchyLevel = HierarchyLevel.DOMINANT,
              fontFamilyId = "anton",
              fontSizeSp = 44f,
              colorHex = StudioPalette.WHITE,
              uppercase = true,
              staggerDelayMs = 0,
            ),
          )
      listOf(
        CaptionComposition(
          id = "anim_preview",
          startMs = 150L,
          endMs = 2150L,
          originalText = "Preview",
          region = SafeRegion.CENTER,
          anchorXFraction = 0.5f,
          anchorYFraction = 0.5f,
          entryAnimation = entry,
          exitAnimation = exit,
          easing = easing,
          entryDurationMs = entryDurationMs,
          exitDurationMs = exitDurationMs,
          elements = baseElements,
        )
      )
    }

  val chipColors =
    FilterChipDefaults.filterChipColors(
      selectedContainerColor = StudioCoralContainer,
      selectedLabelColor = StudioCoralLight,
    )

  Scaffold(
    containerColor = StudioObsidian,
    topBar = {
      StudioMinimalTopBar(
        title = "Motion & Easing",
        onBack = onBack,
        backButtonTag = "animation_back_button",
        trailingContent = {
          val targetId = activeComp?.id ?: ""
          Button(
            onClick = {
              onApplyAnimation(
                targetId,
                scope,
                entry,
                exit,
                easing,
                entryDurationMs,
                exitDurationMs,
              )
              onBack()
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = StudioObsidian),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            modifier = Modifier.height(38.dp).testTag("apply_animation_button"),
          ) {
            Text("Apply", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
          }
        },
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(216.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                  Brush.radialGradient(listOf(Color(0xFF1F1C22), Color(0xFF0E0E13)))
                )
                .border(1.dp, StudioHairline, RoundedCornerShape(22.dp))
          ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
              drawIntoCanvas { canvas ->
                renderer.drawAtTime(
                  canvas = canvas.nativeCanvas,
                  canvasWidth = size.width.toInt(),
                  canvasHeight = size.height.toInt(),
                  compositions = previewCompositions,
                  positionMs = loopTimeMs,
                )
              }
            }
          }
        }

        item {
          StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
              Text("Apply To", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                listOf(ApplyScope.SINGLE_COMPOSITION, ApplyScope.ALL_COMPOSITIONS).forEach { s ->
                  FilterChip(
                    selected = scope == s,
                    onClick = { scope = s },
                    shape = CircleShape,
                    colors = chipColors,
                    label = { Text(s.label, maxLines = 1, softWrap = false) },
                  )
                }
              }

              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Entry Motion", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
                Text("${entryDurationMs}ms", style = MaterialTheme.typography.labelMedium, color = StudioCoralLight)
              }
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EntryAnimationType.entries.forEach { type ->
                  FilterChip(
                    selected = entry == type,
                    onClick = { entry = type },
                    shape = CircleShape,
                    colors = chipColors,
                    label = { Text(type.label, maxLines = 1, softWrap = false) },
                  )
                }
              }
              Slider(
                value = entryDurationMs.toFloat(),
                onValueChange = { entryDurationMs = it.toInt() },
                valueRange = 80f..350f,
                colors =
                  SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = StudioCoral,
                    inactiveTrackColor = StudioElevated,
                  ),
              )

              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Exit Motion", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
                Text("${exitDurationMs}ms", style = MaterialTheme.typography.labelMedium, color = StudioCoralLight)
              }
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ExitAnimationType.entries.forEach { type ->
                  FilterChip(
                    selected = exit == type,
                    onClick = { exit = type },
                    shape = CircleShape,
                    colors = chipColors,
                    label = { Text(type.label, maxLines = 1, softWrap = false) },
                  )
                }
              }
              Slider(
                value = exitDurationMs.toFloat(),
                onValueChange = { exitDurationMs = it.toInt() },
                valueRange = 80f..300f,
                colors =
                  SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = StudioCoral,
                    inactiveTrackColor = StudioElevated,
                  ),
              )

              Text("Easing Curve", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AnimationEasingType.entries.forEach { curve ->
                  FilterChip(
                    selected = easing == curve,
                    onClick = { easing = curve },
                    shape = CircleShape,
                    colors = chipColors,
                    label = { Text(curve.label, maxLines = 1, softWrap = false) },
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
