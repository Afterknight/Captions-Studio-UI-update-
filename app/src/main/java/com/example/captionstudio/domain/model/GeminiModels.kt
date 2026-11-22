package com.example.captionstudio.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class GeminiUsageMode(val label: String, val subtitle: String) {
  OFFLINE("Offline Mode", "Zero Gemini requests. 100% local rules and on-device layout."),
  HYBRID("Hybrid Mode", "Local engine first, optional Gemini semantic boost when requested."),
  GEMINI_ASSISTED("Gemini Assisted", "Use Gemini for semantic hierarchy and layout suggestions."),
}

@Serializable
enum class ConnectionStatus(val label: String) {
  NOT_CONFIGURED("Not Configured"),
  CONFIGURED_UNVERIFIED("Key Saved (Unverified)"),
  TESTING("Testing Connection..."),
  CONNECTED("Connected"),
  ERROR("Connection Error"),
}

@Serializable
data class GeminiModelInfo(
  val name: String,
  val displayName: String,
  val description: String = "",
  val supportedGenerationMethods: List<String> = emptyList(),
  val supportsText: Boolean = true,
  val supportsImageInput: Boolean = false,
  val availabilityStatus: String = "Available",
  val isLikelyPaidOrRestricted: Boolean = false,
  val inputTokenLimit: Int = 0,
  val outputTokenLimit: Int = 0,
) {
  val shortId: String
    get() = name.removePrefix("models/")
}

@Serializable
data class GeminiSettings(
  val enabled: Boolean = false,
  val usageMode: GeminiUsageMode = GeminiUsageMode.HYBRID,
  val selectedModelName: String = "",
  val confirmBeforeRequest: Boolean = true,
  val tokenBudget: Int = 2048,
  val transcriptOnly: Boolean = false,
  val allowFrameSharing: Boolean = true,
  val cachedModels: List<GeminiModelInfo> = emptyList(),
  val connectionStatus: ConnectionStatus = ConnectionStatus.NOT_CONFIGURED,
  val lastStatusMessage: String = "",
)
