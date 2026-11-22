package com.example.captionstudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.captionstudio.domain.model.ConnectionStatus
import com.example.captionstudio.domain.model.GeminiSettings
import com.example.captionstudio.domain.model.GeminiUsageMode
import com.example.captionstudio.domain.model.PreviewQualityMode
import com.example.captionstudio.media.OfflineVoicePackInfo
import com.example.captionstudio.media.OfflineVoicePackStatus
import com.example.captionstudio.ui.components.StudioGlassCard
import com.example.captionstudio.ui.components.StudioMinimalTopBar
import com.example.captionstudio.ui.components.StudioSubtlePill
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralContainer
import com.example.ui.theme.StudioCoralLight
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioError
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioInfo
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.StudioWarning

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSettingsScreen(
  settings: GeminiSettings,
  hasSavedApiKey: Boolean,
  previewQualityMode: PreviewQualityMode,
  speechLanguageTag: String,
  onSaveApiKey: (String) -> Unit,
  onRemoveApiKey: () -> Unit,
  onTestConnection: () -> Unit,
  onRefreshModels: () -> Unit,
  onSetGeminiEnabled: (Boolean) -> Unit,
  onSetUsageMode: (GeminiUsageMode) -> Unit,
  onSetSelectedModel: (String) -> Unit,
  onSetConfirmBeforeRequest: (Boolean) -> Unit,
  onSetTokenBudget: (Int) -> Unit,
  onSetTranscriptOnly: (Boolean) -> Unit,
  onSetAllowFrameSharing: (Boolean) -> Unit,
  onSetPreviewQuality: (PreviewQualityMode) -> Unit,
  onSetSpeechLanguage: (String) -> Unit,
  voicePackStatuses: List<OfflineVoicePackStatus> = emptyList(),
  onDownloadVoicePack: (OfflineVoicePackInfo) -> Unit = {},
  onDeleteVoicePack: (String) -> Unit = {},
  onBack: () -> Unit,
) {
  val uriHandler = LocalUriHandler.current
  var apiKeyInput by remember { mutableStateOf("") }
  var showApiKey by remember { mutableStateOf(false) }
  var modelDropdownExpanded by remember { mutableStateOf(false) }
  var modelSearchQuery by remember { mutableStateOf("") }

  val isTesting = settings.connectionStatus == ConnectionStatus.TESTING

  val filteredModels =
    remember(settings.cachedModels, modelSearchQuery) {
      val q = modelSearchQuery.trim().lowercase()
      if (q.isEmpty()) settings.cachedModels
      else
        settings.cachedModels.filter {
          it.displayName.lowercase().contains(q) || it.name.lowercase().contains(q)
        }
    }

  val selectedModelObj =
    remember(settings.cachedModels, settings.selectedModelName) {
      settings.cachedModels.firstOrNull { it.name == settings.selectedModelName }
    }

  val switchColors =
    SwitchDefaults.colors(
      checkedThumbColor = Color.White,
      checkedTrackColor = StudioCoral,
    )

  val chipColors =
    FilterChipDefaults.filterChipColors(
      selectedContainerColor = StudioCoralContainer,
      selectedLabelColor = StudioCoralLight,
    )

  Scaffold(
    containerColor = StudioObsidian,
    topBar = {
      StudioMinimalTopBar(
        title = "Settings",
        subtitle = "Privacy-first on-device studio",
        onBack = onBack,
        backButtonTag = "settings_back_button",
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
        // 0. Engine Status Card
        item {
          StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(18.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                      if (settings.enabled && settings.connectionStatus == ConnectionStatus.CONNECTED)
                        StudioSuccess.copy(alpha = 0.16f)
                      else StudioCoralContainer
                    ),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector =
                    if (settings.connectionStatus == ConnectionStatus.CONNECTED) Icons.Filled.CheckCircle
                    else Icons.Filled.AutoAwesome,
                  contentDescription = null,
                  tint =
                    if (settings.connectionStatus == ConnectionStatus.CONNECTED) StudioSuccess
                    else StudioCoral,
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text =
                    if (settings.enabled && hasSavedApiKey) "AI Mode: ${settings.usageMode.label}"
                    else "On-Device Engine Active",
                  style = MaterialTheme.typography.titleMedium,
                  color = StudioTextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                  text =
                    selectedModelObj?.displayName?.let { "Model: $it" }
                      ?: if (hasSavedApiKey) "API key encrypted on device • Tap Refresh to load models"
                      else "Speech sync, typography, and video export run 100% locally on your device.",
                  style = MaterialTheme.typography.bodySmall,
                  color = StudioTextSecondary,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }

        // 1. Speech Language, Preview Performance & Offline Voice Packs (Consumer-first order!)
        item {
          StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
              Text("Playback & Speech", style = MaterialTheme.typography.titleMedium, color = StudioTextPrimary)

              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Preview Quality", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  PreviewQualityMode.entries.forEach { mode ->
                    FilterChip(
                      selected = previewQualityMode == mode,
                      onClick = { onSetPreviewQuality(mode) },
                      shape = CircleShape,
                      colors = chipColors,
                      label = { Text(mode.label, maxLines = 1, softWrap = false) },
                    )
                  }
                }
              }

              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Transcription Language", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  listOf("en-US", "en-GB", "en-IN", "hi-IN", "es-ES", "fr-FR", "de-DE", "ja-JP").forEach { tag ->
                    FilterChip(
                      selected = speechLanguageTag == tag,
                      onClick = { onSetSpeechLanguage(tag) },
                      shape = CircleShape,
                      colors = chipColors,
                      label = { Text(tag, maxLines = 1, softWrap = false) },
                    )
                  }
                }
              }

              if (voicePackStatuses.isNotEmpty()) {
                HorizontalDivider(color = StudioHairline)
                Text("Offline Voice Packs", style = MaterialTheme.typography.titleSmall, color = StudioTextPrimary)
                Text(
                  "Download language packs (~36–45 MB) for instant offline speech recognition without internet.",
                  style = MaterialTheme.typography.bodySmall,
                  color = StudioTextSecondary,
                )
                voicePackStatuses.forEach { status ->
                  Surface(
                    color = StudioSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, StudioHairline, RoundedCornerShape(16.dp)),
                  ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                      ) {
                        Column(modifier = Modifier.weight(1f)) {
                          Text(
                            text = status.pack.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = StudioTextPrimary,
                          )
                          Text(
                            text =
                              when {
                                status.isInstalled -> "Installed offline (${status.pack.approxSizeMb} MB)"
                                status.isDownloading -> "Downloading ${(status.downloadProgress * 100).toInt()}%..."
                                status.errorMessage != null -> status.errorMessage
                                else -> "Available • ${status.pack.approxSizeMb} MB"
                              },
                            style = MaterialTheme.typography.labelSmall,
                            color =
                              when {
                                status.isInstalled -> StudioSuccess
                                status.errorMessage != null -> StudioError
                                else -> StudioTextSecondary
                              },
                          )
                        }
                        if (status.isInstalled) {
                          IconButton(
                            onClick = { onDeleteVoicePack(status.pack.id) },
                            modifier = Modifier.size(40.dp),
                          ) {
                            Icon(
                              Icons.Filled.DeleteOutline,
                              contentDescription = "Remove ${status.pack.displayName} pack",
                              tint = StudioError,
                            )
                          }
                        } else {
                          OutlinedButton(
                            onClick = { onDownloadVoicePack(status.pack) },
                            enabled = !status.isDownloading,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp),
                          ) {
                            Text(
                              if (status.isDownloading) "Installing" else "Download",
                              color = StudioTextPrimary,
                              maxLines = 1,
                              softWrap = false,
                            )
                          }
                        }
                      }
                      if (status.isDownloading) {
                        LinearProgressIndicator(
                          progress = { status.downloadProgress.coerceIn(0f, 1f) },
                          color = StudioCoral,
                          trackColor = StudioObsidian,
                          modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }

        // 2. Optional Cloud AI Enhancement
        item {
          StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                  Text("Optional AI Assistance", style = MaterialTheme.typography.titleMedium, color = StudioTextPrimary)
                  Spacer(Modifier.height(2.dp))
                  Text(
                    "Connect your own Gemini API key for AI-directed styling and cloud transcription.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioTextSecondary,
                  )
                }
                Switch(
                  checked = settings.enabled,
                  onCheckedChange = onSetGeminiEnabled,
                  colors = switchColors,
                  modifier = Modifier.testTag("gemini_enable_switch"),
                )
              }

              HorizontalDivider(color = StudioHairline)
              Text("Operating Mode", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)

              GeminiUsageMode.entries.forEach { mode ->
                val selected = settings.usageMode == mode
                Surface(
                  color = if (selected) StudioElevated else StudioSurface,
                  shape = RoundedCornerShape(16.dp),
                  modifier =
                    Modifier.fillMaxWidth()
                      .border(
                        width = if (selected) 1.4.dp else 1.dp,
                        color = if (selected) StudioCoral else StudioHairline,
                        shape = RoundedCornerShape(16.dp),
                      )
                      .clickable { onSetUsageMode(mode) }
                      .testTag("usage_mode_${mode.name}"),
                ) {
                  Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                      text = mode.label + if (mode == GeminiUsageMode.HYBRID) " (Recommended)" else "",
                      style = MaterialTheme.typography.titleSmall,
                      color = if (selected) StudioCoralLight else StudioTextPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(mode.subtitle, style = MaterialTheme.typography.bodySmall, color = StudioTextSecondary)
                  }
                }
              }
            }
          }
        }

        // 3. Encrypted API Key Vault
        item {
          StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                  Icon(Icons.Filled.Security, contentDescription = null, tint = StudioSuccess, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  Text("Personal API Key", style = MaterialTheme.typography.titleSmall, color = StudioTextPrimary)
                }
                if (hasSavedApiKey) {
                  StudioSubtlePill(text = "Encrypted", tint = StudioSuccess)
                }
              }

              val statusColor =
                when (settings.connectionStatus) {
                  ConnectionStatus.CONNECTED -> StudioSuccess
                  ConnectionStatus.ERROR -> StudioError
                  ConnectionStatus.TESTING -> StudioCoralLight
                  ConnectionStatus.CONFIGURED_UNVERIFIED -> StudioWarning
                  ConnectionStatus.NOT_CONFIGURED -> StudioTextSecondary
                }

              Surface(
                color = statusColor.copy(alpha = 0.10f),
                shape = RoundedCornerShape(14.dp),
                modifier =
                  Modifier.fillMaxWidth()
                    .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
              ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isTesting) {
                      CircularProgressIndicator(
                        color = StudioCoral,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                      )
                    } else if (settings.connectionStatus == ConnectionStatus.ERROR) {
                      Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = StudioError, modifier = Modifier.size(16.dp))
                    } else {
                      Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                      text =
                        if (hasSavedApiKey) "${settings.connectionStatus.label} (Stored locally)"
                        else "No API key configured",
                      style = MaterialTheme.typography.labelLarge,
                      color = statusColor,
                    )
                  }
                  if (settings.lastStatusMessage.isNotBlank()) {
                    Text(
                      text = settings.lastStatusMessage,
                      style = MaterialTheme.typography.bodySmall,
                      fontFamily =
                        if (settings.connectionStatus == ConnectionStatus.ERROR) FontFamily.Monospace
                        else FontFamily.Default,
                      color = StudioTextSecondary,
                    )
                  }
                }
              }

              OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text(if (hasSavedApiKey) "Replace Gemini API Key" else "Enter Gemini API Key") },
                placeholder = { Text("AIza...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors =
                  OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StudioCoral,
                    unfocusedBorderColor = StudioBorder,
                    focusedLabelColor = StudioCoral,
                    cursorColor = StudioCoral,
                  ),
                visualTransformation =
                  if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                  IconButton(
                    onClick = { showApiKey = !showApiKey },
                    modifier = Modifier.testTag("toggle_key_visibility_button"),
                  ) {
                    Icon(
                      if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                      contentDescription = if (showApiKey) "Hide API key" else "Show API key",
                    )
                  }
                },
                modifier = Modifier.fillMaxWidth().testTag("gemini_api_key_input"),
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Button(
                  onClick = {
                    if (apiKeyInput.isNotBlank()) {
                      onSaveApiKey(apiKeyInput)
                      apiKeyInput = ""
                    }
                  },
                  enabled = apiKeyInput.isNotBlank() && !isTesting,
                  colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = Color.White),
                  shape = CircleShape,
                  modifier = Modifier.weight(1f).height(46.dp).testTag("save_api_key_button"),
                ) {
                  Text("Save Key", maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                  onClick = onTestConnection,
                  enabled = hasSavedApiKey && !isTesting,
                  shape = CircleShape,
                  modifier = Modifier.weight(1f).height(46.dp).testTag("test_connection_button"),
                ) {
                  if (isTesting) {
                    CircularProgressIndicator(
                      color = StudioCoral,
                      strokeWidth = 2.dp,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Testing", color = StudioTextPrimary, maxLines = 1, softWrap = false)
                  } else {
                    Text("Test", color = StudioTextPrimary, maxLines = 1, softWrap = false)
                  }
                }
                if (hasSavedApiKey) {
                  IconButton(
                    onClick = onRemoveApiKey,
                    enabled = !isTesting,
                    modifier = Modifier.size(46.dp).testTag("remove_api_key_button"),
                  ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove API key", tint = StudioError)
                  }
                }
              }
            }
          }
        }

        // 4. Model Selection
        item {
          StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Model Selection", style = MaterialTheme.typography.titleSmall, color = StudioTextPrimary)
                  Text(
                    "${settings.cachedModels.size} available models",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioTextSecondary,
                  )
                }
                OutlinedButton(
                  onClick = onRefreshModels,
                  enabled = hasSavedApiKey && !isTesting,
                  shape = CircleShape,
                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                  modifier = Modifier.height(40.dp).testTag("refresh_models_button"),
                ) {
                  if (isTesting) {
                    CircularProgressIndicator(color = StudioCoral, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                  } else {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = StudioTextPrimary, modifier = Modifier.size(16.dp))
                  }
                  Spacer(Modifier.width(6.dp))
                  Text("Refresh", color = StudioTextPrimary, maxLines = 1, softWrap = false)
                }
              }

              Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                  color = StudioSurface,
                  shape = RoundedCornerShape(16.dp),
                  modifier =
                    Modifier.fillMaxWidth()
                      .border(1.dp, StudioHairline, RoundedCornerShape(16.dp))
                      .clickable(enabled = settings.cachedModels.isNotEmpty()) {
                        modelDropdownExpanded = true
                      }
                      .testTag("model_selector_dropdown"),
                ) {
                  Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = selectedModelObj?.displayName
                          ?: settings.selectedModelName.ifBlank { "No model selected" },
                        style = MaterialTheme.typography.titleSmall,
                        color = StudioTextPrimary,
                      )
                      if (selectedModelObj != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                          text = "${selectedModelObj.shortId} • ${selectedModelObj.availabilityStatus}",
                          style = MaterialTheme.typography.labelSmall,
                          color = StudioCoralLight,
                        )
                      }
                    }
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select model", tint = StudioCoralLight)
                  }
                }

                DropdownMenu(
                  expanded = modelDropdownExpanded,
                  onDismissRequest = { modelDropdownExpanded = false },
                  modifier = Modifier.widthIn(min = 280.dp, max = 420.dp).background(StudioElevated),
                ) {
                  OutlinedTextField(
                    value = modelSearchQuery,
                    onValueChange = { modelSearchQuery = it },
                    placeholder = { Text("Search models...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                  )
                  filteredModels.forEach { model ->
                    DropdownMenuItem(
                      text = {
                        Column {
                          Text(
                            text = model.displayName + if (model.isLikelyPaidOrRestricted) " (Quota watch)" else "",
                            fontWeight = FontWeight.Medium,
                            color = if (model.isLikelyPaidOrRestricted) StudioWarning else StudioTextPrimary,
                          )
                          Text(
                            text = "${model.shortId} • ${if (model.supportsImageInput) "Text + Vision" else "Text only"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioTextSecondary,
                          )
                        }
                      },
                      onClick = {
                        onSetSelectedModel(model.name)
                        modelDropdownExpanded = false
                      },
                    )
                  }
                }
              }

              if (selectedModelObj?.isLikelyPaidOrRestricted == true) {
                Surface(
                  color = StudioWarning.copy(alpha = 0.12f),
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().border(1.dp, StudioWarning.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                ) {
                  Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = StudioWarning)
                    Spacer(Modifier.width(8.dp))
                    Text(
                      "Selected model may require a paid tier or strict quota. Models are never switched silently.",
                      style = MaterialTheme.typography.bodySmall,
                      color = StudioWarning,
                    )
                  }
                }
              }

              TextButton(
                onClick = { uriHandler.openUri("https://ai.google.dev/pricing") },
                contentPadding = PaddingValues(0.dp),
              ) {
                Text("View Gemini API pricing", color = StudioInfo)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = StudioInfo, modifier = Modifier.size(15.dp))
              }
            }
          }
        }

        // 5. Privacy & Request Safeguards
        item {
          StudioGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
              Text("Privacy & Safeguards", style = MaterialTheme.typography.titleMedium, color = StudioTextPrimary)

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                  Text("Confirm Before AI Requests", color = StudioTextPrimary, fontWeight = FontWeight.Medium)
                  Text("Ask before sending any request to your API key", style = MaterialTheme.typography.bodySmall, color = StudioTextSecondary)
                }
                Switch(
                  checked = settings.confirmBeforeRequest,
                  onCheckedChange = onSetConfirmBeforeRequest,
                  colors = switchColors,
                )
              }

              HorizontalDivider(color = StudioHairline)

              Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Max Output Tokens", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
                  Text("${settings.tokenBudget}", style = MaterialTheme.typography.labelMedium, color = StudioCoralLight)
                }
                Slider(
                  value = settings.tokenBudget.toFloat(),
                  onValueChange = { onSetTokenBudget(it.toInt()) },
                  valueRange = 512f..8192f,
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
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                  Text("Transcript Only", color = StudioTextPrimary, fontWeight = FontWeight.Medium)
                  Text("Send text and timings without video frames", style = MaterialTheme.typography.bodySmall, color = StudioTextSecondary)
                }
                Switch(
                  checked = settings.transcriptOnly,
                  onCheckedChange = onSetTranscriptOnly,
                  colors = switchColors,
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                  Text("Visual Frame Context", color = StudioTextPrimary, fontWeight = FontWeight.Medium)
                  Text(
                    "Share up to 8 low-res frames for face-aware placement. Full video is never uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioTextSecondary,
                  )
                }
                Switch(
                  checked = settings.allowFrameSharing && !settings.transcriptOnly,
                  onCheckedChange = onSetAllowFrameSharing,
                  colors = switchColors,
                  modifier = Modifier.testTag("allow_frame_sharing_switch"),
                )
              }
            }
          }
        }

        // Public App Footer
        item {
          Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = "Caption Studio",
              style = MaterialTheme.typography.labelLarge,
              color = StudioTextSecondary,
            )
            Text(
              text = "Your videos and projects stay privately on your device.",
              style = MaterialTheme.typography.bodySmall,
              color = StudioTextMuted,
            )
          }
        }
      }
    }
  }
}
