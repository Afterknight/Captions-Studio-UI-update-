package com.example.captionstudio.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.captionstudio.domain.model.ColorSwatchOption
import com.example.captionstudio.domain.model.StudioPalette
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralLight
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioHairlineStrong
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Full-spectrum color studio unifying:
 * 1. GPU-cached Circular Color Wheel (360° hue + radial saturation + brightness bar for ALL colors)
 * 2. Curated 24-color Brand Palette Grid (4 balanced rows x 6 accessible swatches)
 * 3. Always-visible Custom Hex (#RRGGBB) input field with live preview dot
 *
 * Emits `commitHistory = false` during continuous wheel/brightness drags and `commitHistory = true`
 * on release or swatch tap so low-end devices stay silky smooth without DB write churn.
 */
@Composable
fun FullSpectrumColorStudio(
  selectedHex: String,
  onColorChange: (hex: String, commitHistory: Boolean) -> Unit,
  modifier: Modifier = Modifier,
  initialMode: Int = 0, // 0 = Circular Wheel, 1 = Palette Grid
  tagPrefix: String = "studio_color",
) {
  var pickerMode by remember { mutableIntStateOf(initialMode) }
  val initHsv = remember(selectedHex) { hexToHsvSafe(selectedHex) }
  var hue by remember { mutableFloatStateOf(initHsv[0]) }
  var saturation by remember { mutableFloatStateOf(initHsv[1]) }
  var brightness by remember { mutableFloatStateOf(initHsv[2]) }
  var hexDraft by remember { mutableStateOf(normalizeHexOrDefault(selectedHex)) }

  // Keep wheel/sliders/hex synchronized when color changes externally (e.g. preset or undo)
  LaunchedEffect(selectedHex) {
    val normalized = normalizeHexOrDefault(selectedHex)
    val currentHsvHex = hsvToHex(hue, saturation, brightness)
    if (!normalized.equals(currentHsvHex, ignoreCase = true)) {
      val parsed = hexToHsvSafe(normalized)
      hue = parsed[0]
      saturation = parsed[1]
      brightness = parsed[2]
    }
    hexDraft = normalized
  }

  val latestOnColorChange by rememberUpdatedState(onColorChange)

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(StudioObsidian)
        .border(1.dp, StudioHairline, RoundedCornerShape(18.dp))
        .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Sleek 2-pill mode switcher: Color Wheel | Palette Grid
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clip(CircleShape)
          .background(StudioElevated)
          .border(1.dp, StudioHairline, CircleShape)
          .padding(3.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      val modes =
        listOf(
          Triple(0, stringResource(R.string.color_picker_wheel_tab), Icons.Rounded.ColorLens),
          Triple(1, stringResource(R.string.color_picker_palette_tab), Icons.Rounded.GridView),
        )
      modes.forEach { (idx, title, icon) ->
        val isChosen = pickerMode == idx
        Row(
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .clip(CircleShape)
              .background(if (isChosen) StudioObsidian else Color.Transparent)
              .border(
                width = if (isChosen) 1.dp else 0.dp,
                color = if (isChosen) StudioCoral.copy(alpha = 0.65f) else Color.Transparent,
                shape = CircleShape,
              )
              .clickable(role = Role.Tab) { pickerMode = idx }
              .semantics { selected = isChosen }
              .padding(vertical = 8.dp)
              .testTag("${tagPrefix}_mode_${if (idx == 0) "wheel" else "palette"}"),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isChosen) StudioCoralLight else StudioTextSecondary,
            modifier = Modifier.size(15.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isChosen) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isChosen) StudioCoralLight else StudioTextSecondary,
            maxLines = 1,
          )
        }
      }
    }

    AnimatedContent(
      targetState = pickerMode,
      transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(110)) },
      label = "color_studio_mode",
    ) { mode ->
      if (mode == 0) {
        // Circular Full-Spectrum Color Wheel + Brightness Gradient Bar
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          CircularColorWheel(
            hue = hue,
            saturation = saturation,
            brightness = brightness,
            wheelDiameter = 178.dp,
            onHueSatChange = { nextH, nextS, commit ->
              hue = nextH
              saturation = nextS
              val hex = hsvToHex(nextH, nextS, brightness)
              hexDraft = hex
              latestOnColorChange(hex, commit)
            },
            modifier = Modifier.testTag("${tagPrefix}_circular_wheel"),
          )

          // Brightness / Lightness Bar (reaches pure black through full chroma to pure white)
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = stringResource(R.string.color_picker_brightness_label),
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextSecondary,
              )
              Text(
                text = "${(brightness * 100f).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = StudioCoralLight,
              )
            }
            val fullBrightColor =
              remember(hue, saturation) {
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f)))
              }
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .height(10.dp)
                  .clip(CircleShape)
                  .background(Brush.horizontalGradient(listOf(Color.Black, fullBrightColor)))
                  .border(0.8.dp, StudioHairline, CircleShape)
            )
            Slider(
              value = brightness,
              onValueChange = { v ->
                brightness = v
                val hex = hsvToHex(hue, saturation, v)
                hexDraft = hex
                latestOnColorChange(hex, false)
              },
              onValueChangeFinished = {
                val hex = hsvToHex(hue, saturation, brightness)
                hexDraft = hex
                latestOnColorChange(hex, true)
              },
              valueRange = 0f..1f,
              colors =
                SliderDefaults.colors(
                  thumbColor = Color.White,
                  activeTrackColor = StudioCoral,
                  inactiveTrackColor = StudioElevated,
                ),
              modifier = Modifier.fillMaxWidth().height(28.dp).testTag("${tagPrefix}_brightness_slider"),
            )
          }
        }
      } else {
        // Curated 24-Color Brand Palette Grid (4 rows x 6 columns)
        ColorPaletteGrid(
          selectedHex = selectedHex,
          rows = StudioPalette.brandPaletteGrid,
          onSelectHex = { hex ->
            val parsed = hexToHsvSafe(hex)
            hue = parsed[0]
            saturation = parsed[1]
            brightness = parsed[2]
            hexDraft = hex
            latestOnColorChange(hex, true)
          },
          tagPrefix = "${tagPrefix}_grid",
        )
      }
    }

    // Always-visible Custom Hex Input + Live Color Preview Swatch + Quick Pure White/Black
    val liveColor = remember(selectedHex) { parseComposeHexColor(selectedHex) }
    val isHexValid = remember(hexDraft) { normalizeHexInput(hexDraft) != null }
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(
        modifier =
          Modifier.size(40.dp)
            .clip(CircleShape)
            .background(liveColor)
            .border(1.5.dp, StudioHairlineStrong, CircleShape)
            .semantics { contentDescription = "Current selected color $selectedHex" }
      )

      OutlinedTextField(
        value = hexDraft,
        onValueChange = { raw ->
          val cleaned =
            raw.uppercase()
              .filter { it == '#' || it in '0'..'9' || it in 'A'..'F' }
              .take(7)
          hexDraft = cleaned
          normalizeHexInput(cleaned)?.let { validHex ->
            val parsed = hexToHsvSafe(validHex)
            hue = parsed[0]
            saturation = parsed[1]
            brightness = parsed[2]
            latestOnColorChange(validHex, true)
          }
        },
        label = { Text(stringResource(R.string.color_picker_hex_label)) },
        placeholder = { Text("#EE7E70") },
        isError = hexDraft.length >= 4 && !isHexValid,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = StudioCoral,
            unfocusedBorderColor = StudioBorder,
            focusedLabelColor = StudioCoral,
            cursorColor = StudioCoral,
          ),
        modifier = Modifier.weight(1f).testTag("${tagPrefix}_hex_input"),
      )

      // One-tap Pure White & Pure Black shortcuts
      listOf(
        StudioPalette.WHITE to "White",
        StudioPalette.SHADOW to "Black",
      ).forEach { (quickHex, label) ->
        val isMatch = selectedHex.equals(quickHex, ignoreCase = true)
        val c = remember(quickHex) { parseComposeHexColor(quickHex) }
        Box(
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .size(34.dp)
              .clip(CircleShape)
              .background(c)
              .border(
                width = if (isMatch) 2.2.dp else 1.dp,
                color = if (isMatch) StudioCoral else StudioHairlineStrong,
                shape = CircleShape,
              )
              .clickable(role = Role.Button) {
                val parsed = hexToHsvSafe(quickHex)
                hue = parsed[0]
                saturation = parsed[1]
                brightness = parsed[2]
                hexDraft = quickHex
                latestOnColorChange(quickHex, true)
              }
              .semantics { contentDescription = "Select $label" },
          contentAlignment = Alignment.Center,
        ) {
          if (isMatch) {
            Icon(
              imageVector = Icons.Rounded.Check,
              contentDescription = null,
              tint = if (c.luminance() > 0.5f) StudioObsidian else Color.White,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
    }
  }
}

/**
 * Pure GPU-cached circular HSV color wheel (360° hue sweep + radial saturation to white center).
 * Zero bitmap allocations per frame so dragging is silky 60fps even on older/low-end phones.
 */
@Composable
fun CircularColorWheel(
  hue: Float,
  saturation: Float,
  brightness: Float,
  onHueSatChange: (hue: Float, saturation: Float, commitHistory: Boolean) -> Unit,
  modifier: Modifier = Modifier,
  wheelDiameter: Dp = 176.dp,
) {
  val latestOnChange by rememberUpdatedState(onHueSatChange)
  val selectedColor =
    remember(hue, saturation, brightness) {
      Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))
    }

  fun updateFromTouch(pos: Offset, width: Float, height: Float, commit: Boolean) {
    val cx = width / 2f
    val cy = height / 2f
    val radius = (min(width, height) / 2f).coerceAtLeast(1f)
    val dx = pos.x - cx
    val dy = pos.y - cy
    val dist = hypot(dx, dy)
    val clampedSat = (dist / radius).coerceIn(0f, 1f)
    val angleDeg = ((atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI) + 360.0) % 360.0
    latestOnChange(angleDeg.toFloat(), clampedSat, commit)
  }

  Box(
    modifier =
      modifier
        .size(wheelDiameter)
        .semantics {
          contentDescription = "Circular full-spectrum color wheel, hue ${hue.roundToInt()} degrees"
        }
        .pointerInput(Unit) {
          detectTapGestures { offset ->
            updateFromTouch(offset, size.width.toFloat(), size.height.toFloat(), commit = true)
          }
        }
        .pointerInput(Unit) {
          var lastPos = Offset.Zero
          detectDragGestures(
            onDragStart = { start ->
              lastPos = start
              updateFromTouch(start, size.width.toFloat(), size.height.toFloat(), commit = false)
            },
            onDrag = { change, _ ->
              change.consume()
              lastPos = change.position
              updateFromTouch(change.position, size.width.toFloat(), size.height.toFloat(), commit = false)
            },
            onDragEnd = {
              updateFromTouch(lastPos, size.width.toFloat(), size.height.toFloat(), commit = true)
            },
          )
        }
        .drawWithCache {
          val radius = size.minDimension / 2f
          val center = Offset(size.width / 2f, size.height / 2f)
          val hueSweepBrush =
            Brush.sweepGradient(
              colors =
                listOf(
                  Color(0xFFFF0000), // 0° Red
                  Color(0xFFFFFF00), // 60° Yellow
                  Color(0xFF00FF00), // 120° Green
                  Color(0xFF00FFFF), // 180° Cyan
                  Color(0xFF0000FF), // 240° Blue
                  Color(0xFFFF00FF), // 300° Magenta
                  Color(0xFFFF0000), // 360° Red
                ),
              center = center,
            )
          val satRadialBrush =
            Brush.radialGradient(
              colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
              center = center,
              radius = radius.coerceAtLeast(1f),
            )
          val rimStroke = Stroke(width = 1.5.dp.toPx())
          onDrawBehind {
            drawCircle(brush = hueSweepBrush, radius = radius, center = center)
            drawCircle(brush = satRadialBrush, radius = radius, center = center)
            if (brightness < 0.995f) {
              drawCircle(
                color = Color.Black.copy(alpha = (1f - brightness).coerceIn(0f, 0.94f)),
                radius = radius,
                center = center,
              )
            }
            drawCircle(
              color = Color.White.copy(alpha = 0.22f),
              radius = radius - 0.75.dp.toPx(),
              center = center,
              style = rimStroke,
            )
          }
        },
  ) {
    // Lightweight overlay Canvas for only the draggable thumb indicator (avoids rebuilding shaders)
    Canvas(modifier = Modifier.matchParentSize()) {
      val radius = size.minDimension / 2f
      val center = Offset(size.width / 2f, size.height / 2f)
      val angleRad = hue * PI / 180.0
      val thumbR = (saturation.coerceIn(0f, 1f)) * (radius - 8.dp.toPx())
      val thumbCenter =
        Offset(
          x = center.x + (cos(angleRad) * thumbR).toFloat(),
          y = center.y + (sin(angleRad) * thumbR).toFloat(),
        )
      drawCircle(color = Color.Black.copy(alpha = 0.45f), radius = 12.dp.toPx(), center = thumbCenter)
      drawCircle(color = Color.White, radius = 10.5.dp.toPx(), center = thumbCenter)
      drawCircle(color = selectedColor, radius = 7.5.dp.toPx(), center = thumbCenter)
    }
  }
}

@Composable
fun ColorPaletteGrid(
  selectedHex: String,
  rows: List<List<ColorSwatchOption>>,
  onSelectHex: (String) -> Unit,
  modifier: Modifier = Modifier,
  tagPrefix: String = "palette_grid",
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    rows.forEach { rowSwatches ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        rowSwatches.forEach { swatch ->
          val isSelected = selectedHex.equals(swatch.hex, ignoreCase = true)
          val colorVal = remember(swatch.hex) { parseComposeHexColor(swatch.hex) }
          val checkTint = if (colorVal.luminance() > 0.52f) StudioObsidian else Color.White

          Box(
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colorVal)
                .border(
                  width = if (isSelected) 2.4.dp else 1.dp,
                  color = if (isSelected) StudioCoral else StudioHairlineStrong,
                  shape = RoundedCornerShape(12.dp),
                )
                .clickable(role = Role.RadioButton) { onSelectHex(swatch.hex) }
                .semantics {
                  selected = isSelected
                  contentDescription = "${swatch.label} (${swatch.hex})" + if (isSelected) ", selected" else ""
                }
                .testTag("${tagPrefix}_${swatch.label.lowercase().replace(" ", "_")}"),
            contentAlignment = Alignment.Center,
          ) {
            if (isSelected) {
              Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = checkTint,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Compact 1-row quick swatch bar with a rainbow wheel button that expands the full
 * Circular Color Wheel + Brand Palette Grid + Custom Hex Studio inline (ideal for Outline,
 * Shadow, Glow, and Backdrop pickers so the inspector stays sleek and uncluttered).
 */
@Composable
fun StudioColorSwatchSelector(
  selectedHex: String,
  swatches: List<ColorSwatchOption>,
  onColorChange: (hex: String, commitHistory: Boolean) -> Unit,
  modifier: Modifier = Modifier,
  swatchTagPrefix: String = "swatch",
) {
  var showFullStudio by remember { mutableStateOf(false) }
  val isPresetSwatch =
    remember(selectedHex, swatches) { swatches.any { it.hex.equals(selectedHex, true) } }
  val rainbowRing =
    remember {
      Brush.sweepGradient(
        listOf(
          Color(0xFFFF453A),
          Color(0xFFFFD60A),
          Color(0xFF32D74B),
          Color(0xFF64D2FF),
          Color(0xFFBF5AF2),
          Color(0xFFFF453A),
        )
      )
    }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Rainbow circular wheel opener pill right at the front for instant discovery
      val customActive = showFullStudio || !isPresetSwatch
      Box(
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .size(38.dp)
            .clip(CircleShape)
            .background(if (!isPresetSwatch) parseComposeHexColor(selectedHex) else StudioElevated)
            .border(
              width = if (customActive) 2.5.dp else 1.8.dp,
              brush = rainbowRing,
              shape = CircleShape,
            )
            .clickable(role = Role.Button) { showFullStudio = !showFullStudio }
            .semantics {
              contentDescription = "Open full circular color wheel and palette picker"
              stateDescription = if (showFullStudio) "Expanded" else "Collapsed"
            }
            .testTag("${swatchTagPrefix}_custom_picker"),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Rounded.Palette,
          contentDescription = stringResource(R.string.typo_custom_color_cd),
          tint =
            if (!isPresetSwatch && parseComposeHexColor(selectedHex).luminance() > 0.55f)
              StudioObsidian
            else Color.White,
          modifier = Modifier.size(18.dp),
        )
      }

      swatches.forEach { swatch ->
        val isSelected = selectedHex.equals(swatch.hex, ignoreCase = true)
        val colorVal = remember(swatch.hex) { parseComposeHexColor(swatch.hex) }
        val checkTint = if (colorVal.luminance() > 0.52f) StudioObsidian else Color.White

        Box(
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .size(38.dp)
              .clip(CircleShape)
              .background(colorVal)
              .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) StudioCoral else StudioHairlineStrong,
                shape = CircleShape,
              )
              .clickable(role = Role.RadioButton) { onColorChange(swatch.hex, true) }
              .semantics {
                selected = isSelected
                contentDescription = "${swatch.label} color" + if (isSelected) ", selected" else ""
              }
              .testTag("${swatchTagPrefix}_${swatch.label.lowercase().replace(" ", "_")}"),
          contentAlignment = Alignment.Center,
        ) {
          if (isSelected) {
            Icon(
              imageVector = Icons.Rounded.Check,
              contentDescription = null,
              tint = checkTint,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }

    AnimatedVisibility(
      visible = showFullStudio,
      enter = expandVertically() + fadeIn(),
      exit = shrinkVertically() + fadeOut(),
    ) {
      FullSpectrumColorStudio(
        selectedHex = selectedHex,
        onColorChange = onColorChange,
        initialMode = 0,
        tagPrefix = "${swatchTagPrefix}_studio",
      )
    }
  }
}

fun normalizeHexInput(raw: String): String? {
  val trimmed = raw.trim().uppercase()
  val prefixed = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
  return if (Regex("^#[0-9A-F]{6}$").matches(prefixed)) prefixed else null
}

fun normalizeHexOrDefault(hex: String): String {
  return normalizeHexInput(hex) ?: StudioPalette.WHITE
}

fun hsvToHex(h: Float, s: Float, v: Float): String {
  val clampedH = ((h % 360f) + 360f) % 360f
  val argb =
    android.graphics.Color.HSVToColor(
      floatArrayOf(clampedH, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
    )
  return String.format("#%06X", 0xFFFFFF and argb)
}

fun hexToHsvSafe(hex: String): FloatArray {
  val hsv = FloatArray(3)
  val argb =
    try {
      android.graphics.Color.parseColor(normalizeHexOrDefault(hex))
    } catch (_: Exception) {
      android.graphics.Color.WHITE
    }
  android.graphics.Color.colorToHSV(argb, hsv)
  return hsv
}
