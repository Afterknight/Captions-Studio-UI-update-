package com.example.captionstudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.captionstudio.domain.model.ApplyScope
import com.example.captionstudio.domain.model.CaptionProject
import com.example.captionstudio.domain.model.FontRole
import com.example.captionstudio.domain.model.SafeRegion
import com.example.captionstudio.domain.model.StudioPalette
import com.example.captionstudio.domain.model.TextAlignment
import com.example.captionstudio.typography.CuratedFontPairing
import com.example.captionstudio.typography.FontRegistry
import com.example.captionstudio.ui.components.StudioGlassCard
import com.example.captionstudio.ui.components.StudioMinimalTopBar
import com.example.captionstudio.ui.components.StudioSubtlePill
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralContainer
import com.example.ui.theme.StudioCoralLight
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioHairlineStrong
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypographyScreen(
  project: CaptionProject?,
  selectedCompositionId: String?,
  selectedElementId: String?,
  onApplyStyle: (
    compId: String,
    elementId: String,
    scope: ApplyScope,
    fontFamilyId: String?,
    fontWeight: Int?,
    isItalic: Boolean?,
    fontSizeSp: Float?,
    colorHex: String?,
    alignment: TextAlignment?,
    uppercase: Boolean?,
    safeRegion: SafeRegion?,
  ) -> Unit,
  onApplyPairing: (CuratedFontPairing) -> Unit,
  onDownloadFont: (String, FontRole) -> Unit,
  onBack: () -> Unit,
) {
  val activeComp =
    project?.compositions?.firstOrNull { it.id == selectedCompositionId }
      ?: project?.compositions?.firstOrNull()
  val activeEl =
    activeComp?.elements?.firstOrNull { it.id == selectedElementId }
      ?: activeComp?.elements?.firstOrNull()

  var scope by remember { mutableStateOf(ApplyScope.SINGLE_ELEMENT) }
  var selectedFontId by remember(activeEl?.id) { mutableStateOf(activeEl?.fontFamilyId ?: "anton") }
  var selectedWeight by remember(activeEl?.id) { mutableIntStateOf(activeEl?.fontWeight ?: 700) }
  var isItalic by remember(activeEl?.id) { mutableStateOf(activeEl?.isItalic ?: false) }
  var isUppercase by remember(activeEl?.id) { mutableStateOf(activeEl?.uppercase ?: false) }
  var fontSizeSp by remember(activeEl?.id) { mutableFloatStateOf(activeEl?.fontSizeSp ?: 28f) }
  var selectedColor by remember(activeEl?.id) { mutableStateOf(activeEl?.colorHex ?: StudioPalette.WHITE) }
  var selectedAlignment by remember(activeEl?.id) { mutableStateOf(activeEl?.alignment ?: TextAlignment.CENTER) }
  var selectedRegion by remember(activeComp?.id) { mutableStateOf(activeComp?.region ?: SafeRegion.LOWER_CENTER) }

  var searchQuery by remember { mutableStateOf("") }
  var roleFilter by remember { mutableStateOf<FontRole?>(null) }
  var customFontInput by remember { mutableStateOf("") }

  val filteredFonts =
    remember(searchQuery, roleFilter) { FontRegistry.search(searchQuery, roleFilter) }

  val chipColors =
    FilterChipDefaults.filterChipColors(
      selectedContainerColor = StudioCoralContainer,
      selectedLabelColor = StudioCoralLight,
    )

  Scaffold(
    containerColor = StudioObsidian,
    topBar = {
      StudioMinimalTopBar(
        title = "Typography",
        subtitle = activeEl?.let { "Editing \"${it.text}\"" } ?: "Fonts & global pairings",
        onBack = onBack,
        backButtonTag = "typography_back_button",
        trailingContent = {
          if (activeComp != null && activeEl != null) {
            Button(
              onClick = {
                onApplyStyle(
                  activeComp.id,
                  activeEl.id,
                  scope,
                  selectedFontId,
                  selectedWeight,
                  isItalic,
                  fontSizeSp,
                  selectedColor,
                  selectedAlignment,
                  isUppercase,
                  selectedRegion,
                )
                onBack()
              },
              shape = CircleShape,
              colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = StudioObsidian),
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
              modifier = Modifier.height(38.dp).testTag("apply_typography_button"),
            ) {
              Text("Apply", fontWeight = FontWeight.SemiBold)
            }
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
        // Scope & Live Sample
        item {
          StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
              Text("Apply To", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
              Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                ApplyScope.entries.forEach { s ->
                  FilterChip(
                    selected = scope == s,
                    onClick = { scope = s },
                    shape = CircleShape,
                    colors = chipColors,
                    label = { Text(s.label) },
                  )
                }
              }

              val previewFont = FontRegistry.findById(selectedFontId)
              Box(
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(StudioObsidian)
                    .border(1.dp, StudioHairline, RoundedCornerShape(16.dp))
                    .padding(vertical = 26.dp, horizontal = 14.dp),
                contentAlignment = Alignment.Center,
              ) {
                val sample = activeEl?.text ?: "Creators Craft Text"
                Text(
                  text = if (isUppercase) sample.uppercase() else sample,
                  fontFamily = previewFont.composeFontFamily,
                  fontSize = fontSizeSp.coerceIn(16f, 44f).sp,
                  fontWeight = if (selectedWeight >= 700) FontWeight.Bold else FontWeight.Normal,
                  fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                  color = Color(android.graphics.Color.parseColor(selectedColor)),
                )
              }
            }
          }
        }

        // Color, Size, Weight & Placement
        item {
          StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
              Text("Color Palette", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
              Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(
                  StudioPalette.WHITE to "White",
                  StudioPalette.YELLOW to "Yellow",
                  StudioPalette.ORANGE to "Amber",
                  StudioPalette.RED to "Red",
                  StudioPalette.CYAN to "Cyan",
                ).forEach { (hex, label) ->
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                      modifier =
                        Modifier.size(40.dp)
                          .clip(CircleShape)
                          .background(Color(android.graphics.Color.parseColor(hex)))
                          .border(
                            width = if (selectedColor.equals(hex, true)) 3.dp else 1.dp,
                            color = if (selectedColor.equals(hex, true)) StudioCoral else StudioHairlineStrong,
                            shape = CircleShape,
                          )
                          .clickable { selectedColor = hex }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = StudioTextSecondary)
                  }
                }
              }

              Column {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text("Size", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
                  Text("${fontSizeSp.toInt()} sp", style = MaterialTheme.typography.labelMedium, color = StudioCoralLight)
                }
                Slider(
                  value = fontSizeSp,
                  onValueChange = { fontSizeSp = it },
                  valueRange = 14f..64f,
                  colors =
                    SliderDefaults.colors(
                      thumbColor = Color.White,
                      activeTrackColor = StudioCoral,
                      inactiveTrackColor = StudioElevated,
                    ),
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Italic Style", color = StudioTextPrimary)
                Switch(
                  checked = isItalic,
                  onCheckedChange = { isItalic = it },
                  colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = StudioCoral),
                )
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Uppercase", color = StudioTextPrimary)
                Switch(
                  checked = isUppercase,
                  onCheckedChange = { isUppercase = it },
                  colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = StudioCoral),
                )
              }

              Text("Weight & Alignment", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(400 to "Regular", 600 to "SemiBold", 900 to "Black").forEach { (w, lbl) ->
                  FilterChip(
                    selected = selectedWeight == w,
                    onClick = { selectedWeight = w },
                    shape = CircleShape,
                    colors = chipColors,
                    label = { Text(lbl) },
                  )
                }
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextAlignment.entries.forEach { align ->
                  FilterChip(
                    selected = selectedAlignment == align,
                    onClick = { selectedAlignment = align },
                    shape = CircleShape,
                    colors = chipColors,
                    label = { Text(align.name.lowercase().replaceFirstChar { it.uppercase() }) },
                  )
                }
              }

              Text("Placement Region", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SafeRegion.entries.filter { it != SafeRegion.CUSTOM }.forEach { reg ->
                  FilterChip(
                    selected = selectedRegion == reg,
                    onClick = { selectedRegion = reg },
                    shape = CircleShape,
                    colors = chipColors,
                    label = { Text(reg.label) },
                  )
                }
              }
            }
          }
        }

        // Curated Pairings
        item {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Curated Pairings", style = MaterialTheme.typography.titleMedium, color = StudioTextPrimary)
            Row(
              modifier = Modifier.horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              FontRegistry.curatedPairings.forEach { pairing ->
                StudioGlassCard(
                  modifier =
                    Modifier.width(210.dp).clickable { onApplyPairing(pairing) },
                  cornerRadius = 18.dp,
                  containerColor = StudioElevated,
                ) {
                  Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(pairing.name, style = MaterialTheme.typography.titleSmall, color = StudioCoralLight)
                    Text(pairing.subtitle, style = MaterialTheme.typography.bodySmall, color = StudioTextSecondary)
                  }
                }
              }
            }
          }
        }

        // Font Browser & Downloader
        item {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Font Library", style = MaterialTheme.typography.titleMedium, color = StudioTextPrimary)
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              placeholder = { Text("Search Anton, Inter, Playfair...") },
              leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
              singleLine = true,
              shape = RoundedCornerShape(16.dp),
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = StudioCoral,
                  unfocusedBorderColor = StudioBorder,
                  cursorColor = StudioCoral,
                ),
              modifier = Modifier.fillMaxWidth().testTag("font_search_input"),
            )
            Row(
              modifier = Modifier.horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilterChip(
                selected = roleFilter == null,
                onClick = { roleFilter = null },
                shape = CircleShape,
                colors = chipColors,
                label = { Text("All") },
              )
              FontRole.entries.forEach { role ->
                FilterChip(
                  selected = roleFilter == role,
                  onClick = { roleFilter = role },
                  shape = CircleShape,
                  colors = chipColors,
                  label = { Text(role.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedTextField(
                value = customFontInput,
                onValueChange = { customFontInput = it },
                placeholder = { Text("Download Google Font (e.g. Syne)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors =
                  OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StudioCoral,
                    unfocusedBorderColor = StudioBorder,
                    cursorColor = StudioCoral,
                  ),
                modifier = Modifier.weight(1f),
              )
              Button(
                onClick = {
                  if (customFontInput.isNotBlank()) {
                    onDownloadFont(customFontInput, roleFilter ?: FontRole.SUPPORTING)
                    customFontInput = ""
                  }
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = StudioElevated, contentColor = StudioCoralLight),
                modifier = Modifier.height(52.dp),
              ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = "Download and cache font")
              }
            }
          }
        }

        items(filteredFonts, key = { it.id }) { font ->
          val isChosen = font.id == selectedFontId
          StudioGlassCard(
            modifier =
              Modifier.fillMaxWidth().clickable { selectedFontId = font.id },
            cornerRadius = 18.dp,
            containerColor = if (isChosen) StudioElevated else StudioCard,
            borderColor = if (isChosen) StudioCoral else StudioHairline,
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = font.displayName,
                  fontFamily = font.composeFontFamily,
                  fontSize = 22.sp,
                  color = if (isChosen) StudioCoralLight else StudioTextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                  text = "${font.role.name.lowercase().replaceFirstChar { it.uppercase() }} • ${font.styleNote}",
                  style = MaterialTheme.typography.bodySmall,
                  color = StudioTextSecondary,
                )
              }
              StudioSubtlePill(
                text = if (font.isBundledOffline) "Built-in" else "Cached",
                tint = if (isChosen) StudioCoralLight else StudioTextSecondary,
              )
            }
          }
        }
      }
    }
  }
}
