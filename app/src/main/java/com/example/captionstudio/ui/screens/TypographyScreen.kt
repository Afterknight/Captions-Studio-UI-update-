package com.example.captionstudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.captionstudio.domain.model.ApplyScope
import com.example.captionstudio.domain.model.CaptionProject
import com.example.captionstudio.domain.model.ElementStylePatch
import com.example.captionstudio.domain.model.FontRole
import com.example.captionstudio.typography.CuratedFontPairing
import com.example.captionstudio.typography.FontRegistry
import com.example.captionstudio.ui.components.ActiveLayerSelectorBar
import com.example.captionstudio.ui.components.HorizontalFontSelectorStrip
import com.example.captionstudio.ui.components.LiveStylePreviewCard
import com.example.captionstudio.ui.components.StudioGlassCard
import com.example.captionstudio.ui.components.StudioMinimalTopBar
import com.example.captionstudio.ui.components.StudioSubtlePill
import com.example.captionstudio.ui.components.TypographyInspectorDeck
import com.example.ui.theme.StudioBorder
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
fun TypographyScreen(
  project: CaptionProject?,
  selectedCompositionId: String?,
  selectedElementId: String?,
  fontsVersion: Int = 0,
  canUndo: Boolean = false,
  onUndo: () -> Unit = {},
  onSelectElement: (String, String) -> Unit = { _, _ -> },
  onUpdateElementText: (String, String, String) -> Unit = { _, _, _ -> },
  onDeleteElement: (String, String) -> Unit = { _, _ -> },
  onPatchStyle: (
    compId: String,
    elementId: String,
    scope: ApplyScope,
    patch: ElementStylePatch,
    recordHistory: Boolean,
  ) -> Unit,
  onResetElementStyle: (String, String) -> Unit = { _, _ -> },
  onToggleDepthMask: (String) -> Unit = {},
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
  var previewReplayNonce by remember { mutableIntStateOf(0) }
  var searchQuery by remember { mutableStateOf("") }
  var roleFilter by remember { mutableStateOf<FontRole?>(null) }
  var customFontInput by remember { mutableStateOf("") }

  val filteredFonts =
    remember(searchQuery, roleFilter, fontsVersion) { FontRegistry.search(searchQuery, roleFilter) }

  val chipColors =
    FilterChipDefaults.filterChipColors(
      selectedContainerColor = StudioCoralContainer,
      selectedLabelColor = StudioCoralLight,
    )

  Scaffold(
    containerColor = StudioObsidian,
    topBar = {
      StudioMinimalTopBar(
        title = "Typography Studio",
        subtitle = activeEl?.let { "Editing \"${it.text}\"" } ?: "Fonts & global pairings",
        onBack = onBack,
        backButtonTag = "typography_back_button",
        trailingContent = {
          if (canUndo) {
            IconButton(
              onClick = onUndo,
              modifier = Modifier.size(40.dp).testTag("typography_undo_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo typography change",
                tint = StudioTextPrimary,
                modifier = Modifier.size(19.dp),
              )
            }
          }
          Button(
            onClick = onBack,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = StudioObsidian),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            modifier = Modifier.height(38.dp).testTag("apply_typography_button"),
          ) {
            Text("Done", fontWeight = FontWeight.SemiBold)
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
        // 1. Live Rendered Typography Preview + Layer Selector + Horizontal Font Selector
        if (activeComp != null && activeEl != null) {
          item {
            StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ActiveLayerSelectorBar(
                  activeComp = activeComp,
                  activeEl = activeEl,
                  applyScope = scope,
                  onSelectElement = { elId -> onSelectElement(activeComp.id, elId) },
                  onScopeChange = { scope = it },
                  onResetStyle = { onResetElementStyle(activeComp.id, activeEl.id) },
                )

                LiveStylePreviewCard(
                  activeComp = activeComp,
                  activeEl = activeEl,
                  replayTrigger = previewReplayNonce,
                )

                Text(
                  text = "Typeface",
                  style = MaterialTheme.typography.titleSmall,
                  color = StudioTextPrimary,
                )

                HorizontalFontSelectorStrip(
                  selectedFontId = activeEl.fontFamilyId,
                  onSelectFont = { fontId ->
                    onPatchStyle(
                      activeComp.id,
                      activeEl.id,
                      scope,
                      ElementStylePatch(fontFamilyId = fontId),
                      true,
                    )
                  },
                  fontsVersion = fontsVersion,
                  showRoleFilters = true,
                  compact = false,
                  previewItalic = activeEl.isItalic,
                  previewUppercase = activeEl.uppercase,
                )
              }
            }
          }

          // 2. Full Organized Typography Inspector (`Text | Color | Motion | Effects | Layout`)
          item {
            StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                  text = "Customize Typography & Motion",
                  style = MaterialTheme.typography.titleSmall,
                  color = StudioTextPrimary,
                )
                TypographyInspectorDeck(
                  activeComp = activeComp,
                  activeEl = activeEl,
                  onPatchStyle = { patch, record ->
                    onPatchStyle(activeComp.id, activeEl.id, scope, patch, record)
                  },
                  onUpdateText = { newText ->
                    onUpdateElementText(activeComp.id, activeEl.id, newText)
                  },
                  onDeleteElement =
                    if (activeComp.elements.size > 1) {
                      { onDeleteElement(activeComp.id, activeEl.id) }
                    } else null,
                  onToggleCaptionDepthMask = { onToggleDepthMask(activeComp.id) },
                  showInlinePreview = false,
                  onExternalReplayTrigger = { previewReplayNonce++ },
                )
              }
            }
          }
        }

        // 3. Curated Pairings
        item {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Curated Pairings", style = MaterialTheme.typography.titleMedium, color = StudioTextPrimary)
            Row(
              modifier = Modifier.horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              FontRegistry.curatedPairings.forEach { pairing ->
                StudioGlassCard(
                  modifier = Modifier.width(210.dp).clickable { onApplyPairing(pairing) },
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

        // 4. Searchable Full Font Library & Custom Google Font Downloader
        item {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Font Library & Downloads", style = MaterialTheme.typography.titleMedium, color = StudioTextPrimary)
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              placeholder = { Text("Search Anton, Syne, Montserrat, Playfair...") },
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
                placeholder = { Text("Download Google Font (e.g. Poppins)") },
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
          val isChosen = font.id == activeEl?.fontFamilyId
          StudioGlassCard(
            modifier =
              Modifier.fillMaxWidth().clickable {
                if (activeComp != null && activeEl != null) {
                  onPatchStyle(
                    activeComp.id,
                    activeEl.id,
                    scope,
                    ElementStylePatch(fontFamilyId = font.id),
                    true,
                  )
                }
              },
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
