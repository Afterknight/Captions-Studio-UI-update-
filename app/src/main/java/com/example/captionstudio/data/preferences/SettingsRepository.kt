package com.example.captionstudio.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.captionstudio.domain.model.ConnectionStatus
import com.example.captionstudio.domain.model.GeminiModelInfo
import com.example.captionstudio.domain.model.GeminiSettings
import com.example.captionstudio.domain.model.GeminiUsageMode
import com.example.captionstudio.domain.model.PreviewQualityMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class AsrEnginePreference(
  val label: String,
  val shortLabel: String,
) {
  AUTO("Auto (Whisper / Vosk)", "Auto"),
  WHISPER_SMALL("Whisper Small", "Whisper Small"),
  VOSK_FAST("Vosk Fast", "Vosk Fast"),
}

private val Context.studioDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "caption_studio_prefs")

class SettingsRepository(private val context: Context) {

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  val geminiSettings: Flow<GeminiSettings> =
    context.studioDataStore.data.map { prefs ->
      val modeName = prefs[KEY_GEMINI_MODE] ?: GeminiUsageMode.HYBRID.name
      val statusName = prefs[KEY_CONNECTION_STATUS] ?: ConnectionStatus.NOT_CONFIGURED.name
      val modelsJson = prefs[KEY_CACHED_MODELS] ?: "[]"
      val models =
        runCatching { json.decodeFromString<List<GeminiModelInfo>>(modelsJson) }
          .getOrDefault(emptyList())
      GeminiSettings(
        enabled = prefs[KEY_GEMINI_ENABLED] ?: false,
        usageMode =
          runCatching { GeminiUsageMode.valueOf(modeName) }.getOrDefault(GeminiUsageMode.HYBRID),
        selectedModelName = prefs[KEY_SELECTED_MODEL] ?: "",
        confirmBeforeRequest = prefs[KEY_CONFIRM_BEFORE_REQUEST] ?: true,
        tokenBudget = prefs[KEY_TOKEN_BUDGET] ?: 2048,
        transcriptOnly = prefs[KEY_TRANSCRIPT_ONLY] ?: false,
        allowFrameSharing = prefs[KEY_ALLOW_FRAME_SHARING] ?: true,
        cachedModels = models,
        connectionStatus =
          runCatching { ConnectionStatus.valueOf(statusName) }
            .getOrDefault(ConnectionStatus.NOT_CONFIGURED),
        lastStatusMessage = prefs[KEY_LAST_STATUS_MSG] ?: "",
      )
    }

  val previewQualityMode: Flow<PreviewQualityMode> =
    context.studioDataStore.data.map { prefs ->
      val raw = prefs[KEY_PREVIEW_QUALITY] ?: PreviewQualityMode.HIGH_QUALITY.name
      runCatching { PreviewQualityMode.valueOf(raw) }.getOrDefault(PreviewQualityMode.HIGH_QUALITY)
    }

  val speechLanguageTag: Flow<String> =
    context.studioDataStore.data.map { prefs -> prefs[KEY_SPEECH_LANG] ?: "en-US" }

  val asrEnginePreference: Flow<AsrEnginePreference> =
    context.studioDataStore.data.map { prefs ->
      val raw = prefs[KEY_ASR_ENGINE_PREFERENCE] ?: AsrEnginePreference.AUTO.name
      runCatching { AsrEnginePreference.valueOf(raw) }.getOrDefault(AsrEnginePreference.AUTO)
    }

  val customPresets: Flow<List<com.example.captionstudio.domain.model.PresetDefinition>> =
    context.studioDataStore.data.map { prefs ->
      val raw = prefs[KEY_CUSTOM_PRESETS] ?: "[]"
      runCatching {
          json.decodeFromString<List<com.example.captionstudio.domain.model.PresetDefinition>>(raw)
        }
        .getOrDefault(emptyList())
    }

  val isCreatorMode: Flow<Boolean> =
    context.studioDataStore.data.map { prefs -> prefs[KEY_CREATOR_MODE] ?: true }

  val isProUnlocked: Flow<Boolean> =
    context.studioDataStore.data.map { prefs -> prefs[KEY_PRO_UNLOCKED] ?: false }

  suspend fun saveCustomPreset(preset: com.example.captionstudio.domain.model.PresetDefinition) {
    context.studioDataStore.edit { prefs ->
      val existing =
        runCatching {
            json.decodeFromString<List<com.example.captionstudio.domain.model.PresetDefinition>>(
              prefs[KEY_CUSTOM_PRESETS] ?: "[]"
            )
          }
          .getOrDefault(emptyList())
      val updated = (listOf(preset) + existing.filterNot { it.id == preset.id }).take(30)
      prefs[KEY_CUSTOM_PRESETS] = json.encodeToString(updated)
    }
  }

  suspend fun deleteCustomPreset(presetId: String) {
    context.studioDataStore.edit { prefs ->
      val existing =
        runCatching {
            json.decodeFromString<List<com.example.captionstudio.domain.model.PresetDefinition>>(
              prefs[KEY_CUSTOM_PRESETS] ?: "[]"
            )
          }
          .getOrDefault(emptyList())
      prefs[KEY_CUSTOM_PRESETS] = json.encodeToString(existing.filterNot { it.id == presetId })
    }
  }

  suspend fun setCreatorMode(enabled: Boolean) {
    context.studioDataStore.edit { it[KEY_CREATOR_MODE] = enabled }
  }

  suspend fun setProUnlocked(unlocked: Boolean) {
    context.studioDataStore.edit { it[KEY_PRO_UNLOCKED] = unlocked }
  }

  suspend fun setGeminiEnabled(enabled: Boolean) {
    context.studioDataStore.edit { it[KEY_GEMINI_ENABLED] = enabled }
  }

  suspend fun setUsageMode(mode: GeminiUsageMode) {
    context.studioDataStore.edit { it[KEY_GEMINI_MODE] = mode.name }
  }

  suspend fun setSelectedModel(modelName: String) {
    context.studioDataStore.edit { it[KEY_SELECTED_MODEL] = modelName }
  }

  suspend fun setConfirmBeforeRequest(confirm: Boolean) {
    context.studioDataStore.edit { it[KEY_CONFIRM_BEFORE_REQUEST] = confirm }
  }

  suspend fun setTokenBudget(budget: Int) {
    context.studioDataStore.edit { it[KEY_TOKEN_BUDGET] = budget.coerceIn(512, 8192) }
  }

  suspend fun setTranscriptOnly(transcriptOnly: Boolean) {
    context.studioDataStore.edit {
      it[KEY_TRANSCRIPT_ONLY] = transcriptOnly
      if (transcriptOnly) {
        it[KEY_ALLOW_FRAME_SHARING] = false
      }
    }
  }

  suspend fun setAllowFrameSharing(allowed: Boolean) {
    context.studioDataStore.edit {
      it[KEY_ALLOW_FRAME_SHARING] = allowed
      if (allowed) {
        it[KEY_TRANSCRIPT_ONLY] = false
      }
    }
  }

  suspend fun updateCachedModels(models: List<GeminiModelInfo>) {
    context.studioDataStore.edit {
      it[KEY_CACHED_MODELS] = json.encodeToString(models)
      val currentSelected = it[KEY_SELECTED_MODEL]
      if (currentSelected.isNullOrBlank() && models.isNotEmpty()) {
        val preferred =
          models.firstOrNull { m -> !m.isLikelyPaidOrRestricted && m.supportsText }
            ?: models.first()
        it[KEY_SELECTED_MODEL] = preferred.name
      }
    }
  }

  suspend fun setConnectionStatus(status: ConnectionStatus, message: String = "") {
    context.studioDataStore.edit {
      it[KEY_CONNECTION_STATUS] = status.name
      it[KEY_LAST_STATUS_MSG] = message
    }
  }

  suspend fun setPreviewQualityMode(mode: PreviewQualityMode) {
    context.studioDataStore.edit { it[KEY_PREVIEW_QUALITY] = mode.name }
  }

  suspend fun setSpeechLanguageTag(tag: String) {
    context.studioDataStore.edit { it[KEY_SPEECH_LANG] = tag }
  }

  suspend fun setAsrEnginePreference(preference: AsrEnginePreference) {
    context.studioDataStore.edit { it[KEY_ASR_ENGINE_PREFERENCE] = preference.name }
  }

  companion object {
    private val KEY_GEMINI_ENABLED = booleanPreferencesKey("gemini_enabled")
    private val KEY_GEMINI_MODE = stringPreferencesKey("gemini_mode")
    private val KEY_SELECTED_MODEL = stringPreferencesKey("gemini_selected_model")
    private val KEY_CONFIRM_BEFORE_REQUEST = booleanPreferencesKey("gemini_confirm_request")
    private val KEY_TOKEN_BUDGET = intPreferencesKey("gemini_token_budget")
    private val KEY_TRANSCRIPT_ONLY = booleanPreferencesKey("gemini_transcript_only")
    private val KEY_ALLOW_FRAME_SHARING = booleanPreferencesKey("gemini_allow_frames")
    private val KEY_CACHED_MODELS = stringPreferencesKey("gemini_cached_models")
    private val KEY_CONNECTION_STATUS = stringPreferencesKey("gemini_connection_status")
    private val KEY_LAST_STATUS_MSG = stringPreferencesKey("gemini_last_status_msg")
    private val KEY_PREVIEW_QUALITY = stringPreferencesKey("preview_quality_mode")
    private val KEY_SPEECH_LANG = stringPreferencesKey("speech_language_tag")
    private val KEY_ASR_ENGINE_PREFERENCE = stringPreferencesKey("asr_engine_preference")
    private val KEY_CUSTOM_PRESETS = stringPreferencesKey("custom_cloned_presets")
    private val KEY_CREATOR_MODE = booleanPreferencesKey("editor_creator_mode")
    private val KEY_PRO_UNLOCKED = booleanPreferencesKey("is_pro_unlocked")
  }
}
