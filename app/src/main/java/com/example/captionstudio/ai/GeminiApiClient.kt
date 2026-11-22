package com.example.captionstudio.ai

import com.example.captionstudio.domain.model.AnimationEasingType
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.CompositionElement
import com.example.captionstudio.domain.model.CompositionStyleVariant
import com.example.captionstudio.domain.model.DepthLayer
import com.example.captionstudio.domain.model.EntryAnimationType
import com.example.captionstudio.domain.model.ExitAnimationType
import com.example.captionstudio.domain.model.FontRole
import com.example.captionstudio.domain.model.GeminiModelInfo
import com.example.captionstudio.domain.model.HierarchyLevel
import com.example.captionstudio.domain.model.PresetDefinition
import com.example.captionstudio.domain.model.SafeRegion
import com.example.captionstudio.domain.model.StudioPalette
import com.example.captionstudio.domain.model.TextAlignment
import com.example.captionstudio.domain.model.VideoAnalysisReport
import com.example.captionstudio.domain.model.WordTiming
import com.example.captionstudio.media.VideoAnalysisService.TimestampedFrame
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

sealed interface GeminiApiResult<out T> {
  data class Success<T>(val data: T) : GeminiApiResult<T>
  data class Error(val message: String, val isPaidOrQuotaIssue: Boolean = false) :
    GeminiApiResult<Nothing>
}

data class GeminiAudioTranscript(
  val transcript: String,
  val wordTimings: List<WordTiming>,
)

object GeminiApiClient {

  /**
   * Prioritizes IPv4 addresses ahead of IPv6 so mobile/Wi-Fi networks with broken IPv6 port-443
   * routes (e.g., carrier CG-NAT / IPv6 blackholes causing 20s timeouts) connect immediately via IPv4,
   * while keeping IPv6 in the fallback list.
   */
  private val ipv4FirstDns =
    object : Dns {
      override fun lookup(hostname: String): List<InetAddress> {
        val resolved = Dns.SYSTEM.lookup(hostname)
        val v4 = resolved.filterIsInstance<Inet4Address>()
        val v6 = resolved.filterIsInstance<Inet6Address>()
        return (v4 + v6).ifEmpty { resolved }
      }
    }

  private val ipv4OnlyDns =
    object : Dns {
      override fun lookup(hostname: String): List<InetAddress> {
        val resolved = Dns.SYSTEM.lookup(hostname)
        return resolved.filterIsInstance<Inet4Address>().ifEmpty { resolved }
      }
    }

  private val primaryClient =
    OkHttpClient.Builder()
      .dns(ipv4FirstDns)
      .connectTimeout(12, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .writeTimeout(45, TimeUnit.SECONDS)
      .retryOnConnectionFailure(true)
      .build()

  private val ipv4FallbackClient =
    primaryClient
      .newBuilder()
      .dns(ipv4OnlyDns)
      .connectTimeout(12, TimeUnit.SECONDS)
      .build()

  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
  private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

  private fun executeWithNetworkFallback(request: Request): Response {
    return try {
      primaryClient.newCall(request).execute()
    } catch (firstErr: IOException) {
      // Retry once explicitly forcing IPv4 if primary dual-stack socket timed out/failed
      try {
        ipv4FallbackClient.newCall(request).execute()
      } catch (_: IOException) {
        throw firstErr
      }
    }
  }

  private fun formatNetworkError(e: IOException): GeminiApiResult.Error {
    val detail =
      when (e) {
        is UnknownHostException ->
          "Cannot resolve generativelanguage.googleapis.com. Check your internet connection or Private DNS."
        is SocketTimeoutException ->
          "Timed out connecting to Google AI API (IPv4 & IPv6 attempted). Check Wi-Fi/mobile data, VPN, or Private DNS."
        else ->
          "Network error reaching Google AI API (${e.javaClass.simpleName}): ${e.localizedMessage ?: "Connection failed"}"
      }
    return GeminiApiResult.Error(detail)
  }

  suspend fun fetchAvailableModels(apiKey: String): GeminiApiResult<List<GeminiModelInfo>> =
    withContext(Dispatchers.IO) {
      val trimmed = apiKey.trim()
      if (trimmed.isEmpty()) {
        return@withContext GeminiApiResult.Error("API key is empty.")
      }

      val request =
        Request.Builder()
          .url("$BASE_URL/models?pageSize=100")
          .addHeader("x-goog-api-key", trimmed)
          .get()
          .build()

      try {
        executeWithNetworkFallback(request).use { response ->
          val body = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            return@withContext mapHttpError(response.code, body)
          }

          val root = JSONObject(body)
          val arr = root.optJSONArray("models") ?: JSONArray()
          val parsed = mutableListOf<GeminiModelInfo>()
          for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val name = item.optString("name", "")
            val methodsArr = item.optJSONArray("supportedGenerationMethods") ?: JSONArray()
            val methods = (0 until methodsArr.length()).mapNotNull { methodsArr.optString(it) }
            if ("generateContent" !in methods) continue

            val displayName = item.optString("displayName", name.removePrefix("models/"))
            val description = item.optString("description", "")
            val lower = "${name.lowercase()} ${description.lowercase()}"
            val supportsImages =
              lower.contains("flash") ||
                lower.contains("pro") ||
                lower.contains("vision") ||
                lower.contains("gemini-2") ||
                lower.contains("gemini-3") ||
                lower.contains("gemini-4")
            val isPaidOrRestricted =
              lower.contains("ultra") ||
                lower.contains("2.5-pro") ||
                lower.contains("exp") ||
                lower.contains("thinking")

            parsed.add(
              GeminiModelInfo(
                name = name,
                displayName = displayName,
                description = description,
                supportedGenerationMethods = methods,
                supportsText = true,
                supportsImageInput = supportsImages,
                availabilityStatus =
                  if (lower.contains("deprecated")) "Deprecated" else "Available",
                isLikelyPaidOrRestricted = isPaidOrRestricted,
                inputTokenLimit = item.optInt("inputTokenLimit", 0),
                outputTokenLimit = item.optInt("outputTokenLimit", 0),
              )
            )
          }
          if (parsed.isEmpty()) {
            GeminiApiResult.Error("No generateContent models were returned for this API key.")
          } else {
            GeminiApiResult.Success(parsed.sortedBy { it.displayName })
          }
        }
      } catch (e: IOException) {
        formatNetworkError(e)
      } catch (e: Exception) {
        GeminiApiResult.Error("Failed parsing model catalog: ${e.localizedMessage}")
      }
    }

  suspend fun testConnection(apiKey: String, modelName: String): GeminiApiResult<String> =
    withContext(Dispatchers.IO) {
      val trimmed = apiKey.trim()
      if (trimmed.isEmpty()) return@withContext GeminiApiResult.Error("No API key saved.")
      val targetModel = modelName.ifBlank {
        return@withContext GeminiApiResult.Error("Select a discovered model before testing (tap Refresh first).")
      }

      val normalizedModel =
        if (targetModel.startsWith("models/")) targetModel else "models/$targetModel"
      val payload =
        JSONObject().apply {
          put(
            "contents",
            JSONArray().put(
              JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "Reply with OK")))
            ),
          )
          put("generationConfig", JSONObject().put("maxOutputTokens", 16).put("temperature", 0.0))
        }

      val request =
        Request.Builder()
          .url("$BASE_URL/$normalizedModel:generateContent")
          .addHeader("x-goog-api-key", trimmed)
          .post(payload.toString().toRequestBody(jsonMediaType))
          .build()

      try {
        executeWithNetworkFallback(request).use { response ->
          val body = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            return@withContext mapHttpError(response.code, body)
          }
          GeminiApiResult.Success("Connected to ${normalizedModel.removePrefix("models/")}")
        }
      } catch (e: IOException) {
        formatNetworkError(e)
      } catch (e: Exception) {
        GeminiApiResult.Error("Test failed: ${e.localizedMessage}")
      }
    }

  /**
   * Sends extracted WAV audio to Gemini and requests verbatim word-by-word millisecond timestamps.
   */
  suspend fun transcribeAudioWithWordTimings(
    apiKey: String,
    modelName: String,
    wavBase64: String,
    languageHint: String,
    offsetMs: Long = 0L,
  ): GeminiApiResult<GeminiAudioTranscript> =
    withContext(Dispatchers.IO) {
      val trimmed = apiKey.trim()
      if (trimmed.isEmpty()) return@withContext GeminiApiResult.Error("API key missing.")
      if (modelName.isBlank()) return@withContext GeminiApiResult.Error("No Gemini model selected.")

      val normalizedModel =
        if (modelName.startsWith("models/")) modelName else "models/$modelName"

      val promptText =
        """
        Transcribe every spoken word in this audio clip with millisecond accuracy.
        Language hint: $languageHint.
        Rules:
        - Output strictly valid JSON matching:
          {"transcript": "full verbatim text with punctuation", "words": [{"word": "exact", "startMs": 120, "endMs": 410}]}
        - Preserve punctuation on each word token and never skip filler/short words.
        - Provide exact spoken startMs and endMs relative to the beginning of this audio clip (0ms).
        - If there is no speech, return {"transcript": "", "words": []}.
        """
          .trimIndent()

      val parts =
        JSONArray()
          .put(JSONObject().put("text", promptText))
          .put(
            JSONObject().put(
              "inline_data",
              JSONObject().put("mime_type", "audio/wav").put("data", wavBase64),
            )
          )

      val payload =
        JSONObject().apply {
          put("contents", JSONArray().put(JSONObject().put("parts", parts)))
          put(
            "generationConfig",
            JSONObject().apply {
              put("responseMimeType", "application/json")
              put("maxOutputTokens", 4096)
              put("temperature", 0.1)
            },
          )
        }

      val request =
        Request.Builder()
          .url("$BASE_URL/$normalizedModel:generateContent")
          .addHeader("x-goog-api-key", trimmed)
          .post(payload.toString().toRequestBody(jsonMediaType))
          .build()

      try {
        executeWithNetworkFallback(request).use { response ->
          val body = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            return@withContext mapHttpError(response.code, body)
          }
          val root = JSONObject(body)
          val textContent =
            root
              .optJSONArray("candidates")
              ?.optJSONObject(0)
              ?.optJSONObject("content")
              ?.optJSONArray("parts")
              ?.optJSONObject(0)
              ?.optString("text", "")
              .orEmpty()

          if (textContent.isBlank()) {
            return@withContext GeminiApiResult.Error("Gemini returned an empty transcription.")
          }

          val cleaned =
            textContent.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
          val parsedObj = JSONObject(cleaned)
          val transcript = parsedObj.optString("transcript", "").trim()
          val wordsArr = parsedObj.optJSONArray("words") ?: JSONArray()
          val words = mutableListOf<WordTiming>()
          for (i in 0 until wordsArr.length()) {
            val wObj = wordsArr.optJSONObject(i) ?: continue
            val wText = wObj.optString("word", "").trim()
            if (wText.isEmpty()) continue
            val s = (wObj.optLong("startMs", 0L) + offsetMs).coerceAtLeast(offsetMs)
            val e = (wObj.optLong("endMs", s + 180L) + offsetMs).coerceAtLeast(s + 60L)
            words.add(
              WordTiming(
                word = wText,
                startMs = s,
                endMs = e,
                isEstimated = false,
              )
            )
          }
          val finalTranscript =
            if (transcript.isNotBlank()) transcript else words.joinToString(" ") { it.word }
          GeminiApiResult.Success(GeminiAudioTranscript(finalTranscript, words))
        }
      } catch (e: IOException) {
        formatNetworkError(e)
      } catch (e: Exception) {
        GeminiApiResult.Error("Audio transcription parse failed: ${e.localizedMessage}")
      }
    }

  suspend fun generateKineticCompositions(
    apiKey: String,
    modelName: String,
    transcript: String,
    wordTimings: List<WordTiming>,
    preset: PresetDefinition,
    analysisReport: VideoAnalysisReport,
    tokenBudget: Int,
    timestampedFrames: List<TimestampedFrame>,
  ): GeminiApiResult<List<CaptionComposition>> =
    withContext(Dispatchers.IO) {
      val trimmed = apiKey.trim()
      if (trimmed.isEmpty()) return@withContext GeminiApiResult.Error("API key missing.")
      if (modelName.isBlank()) return@withContext GeminiApiResult.Error("No Gemini model selected.")

      val normalizedModel =
        if (modelName.startsWith("models/")) modelName else "models/$modelName"

      val timingSummary =
        wordTimings.joinToString(" ") { wt -> "${wt.word}[${wt.startMs}-${wt.endMs}]" }

      val promptText =
        """
        You are an editorial motion graphics & kinetic typography director.
        Observe the attached timestamped video frames carefully alongside the millisecond word timings.
        Reproduce this exact visual language across consecutive phrases:
        1) Visual Style Archetypes (styleVariant — switch dynamically based on what you SEE in each frame):
           - HERO_STACK: Small clean geometric sans lead-in ("plus_jakarta_sans", 15..18sp, #FFFFFF) stacked above a massive condensed uppercase headline ("bebas_neue" or "anton", 48..62sp, #FFFFFF, #FFB81C, or #FF2E2E for urgency/problems) and an optional tilted handwritten brush script modifier ("caveat", 28..36sp, #FFB81C, rotationDeg -4.0, offsetYEm -0.18).
           - WHISPER_NEGATIVE_SPACE: For quiet 2-3 word beat transitions ("on mute", "in a meeting", "way faster!"), use tiny crisp sans (14..16sp, #FFFFFF) placed directly into calm dark clothing or empty wall negative space (`UPPER_RIGHT`, `LOWER_CENTER`, `LEFT_SIDE`) so contrast and breathing room shine. Use `inlineRowGroup: 0` on two inline elements if pairing small sans + expressive brush script (e.g., "and" + "keeps").
           - LEFT_ANCHOR_STACK: Left-aligned vertical staircase (`alignment: LEFT`, `region: LEFT_SIDE`) building short stacked steps toward one glowing condensed bottom punchline.
           - SEAM_CALLOUT: When frames show a horizontal split screen (e.g., UI top half + speaker bottom half), anchor small clean white + warm gold caption right along `SEAM_CENTER` (y ~0.52).
           - GLOW_BANNER: Bold condensed CTA/emphasis ("link in bio", "LOOKS GOOD") with `glowColorHex: "#FFB81C"`.
        2) Behind-Subject Depth Occlusion:
           - Whenever frames show headroom or background wall behind the speaker's head/hair/shoulders and `styleVariant` is HERO_STACK or upper GLOW_BANNER, set `depthLayer: "BEHIND_SUBJECT"` on the composition (and on the giant DOMINANT element) so the subject cleanly occludes the giant background text while keeping small SUPPORTING/EXPRESSIVE text `IN_FRONT`.
        3) Available local fonts:
           - heavyFontIds: "bebas_neue", "anton", "oswald"
           - supportingFontIds: "plus_jakarta_sans", "inter"
           - expressiveFontIds: "caveat", "pacifico", "playfair_italic"
        4) Rules:
           - Never invent or omit spoken words. Keep chronological timings faithful to the word timings below.
           - Output strictly JSON matching:
             {"compositions":[{"startMs":0,"endMs":1800,"originalText":"...","styleVariant":"HERO_STACK","region":"UPPER_CENTER","anchorXFraction":0.5,"anchorYFraction":0.26,"depthLayer":"BEHIND_SUBJECT","entryAnimation":"STAGGER_REVEAL","exitAnimation":"FADE_DROP","elements":[{"text":"...","role":"SUPPORTING","hierarchyLevel":"SUPPORTING","fontFamilyId":"plus_jakarta_sans","fontWeight":600,"isItalic":false,"fontSizeSp":16.5,"colorHex":"#FFFFFF","alignment":"CENTER","uppercase":false,"inlineRowGroup":0,"rotationDeg":0.0,"offsetYEm":0.0,"glowColorHex":null,"depthLayer":"IN_FRONT","staggerDelayMs":0}]}]}
        Local vision summary: ${analysisReport.summary}
        Subject coverage: ${(analysisReport.subjectCoverageFraction * 100).toInt()}%, Split-screen detected: ${analysisReport.isSplitScreenDetected}
        Word timings (ms): $timingSummary
        Transcript: $transcript
        """.trimIndent()

      val parts = JSONArray().put(JSONObject().put("text", promptText))
      timestampedFrames.forEach { frame ->
        parts.put(JSONObject().put("text", "Video frame at ${frame.timestampMs}ms:"))
        parts.put(
          JSONObject().put(
            "inline_data",
            JSONObject().put("mime_type", "image/jpeg").put("data", frame.base64Jpeg),
          )
        )
      }

      val payload =
        JSONObject().apply {
          put("contents", JSONArray().put(JSONObject().put("parts", parts)))
          put(
            "generationConfig",
            JSONObject().apply {
              put("responseMimeType", "application/json")
              put("maxOutputTokens", tokenBudget.coerceIn(512, 8192))
              put("temperature", 0.35)
            },
          )
        }

      val request =
        Request.Builder()
          .url("$BASE_URL/$normalizedModel:generateContent")
          .addHeader("x-goog-api-key", trimmed)
          .post(payload.toString().toRequestBody(jsonMediaType))
          .build()

      try {
        executeWithNetworkFallback(request).use { response ->
          val body = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            return@withContext mapHttpError(response.code, body)
          }
          val root = JSONObject(body)
          val textContent =
            root
              .optJSONArray("candidates")
              ?.optJSONObject(0)
              ?.optJSONObject("content")
              ?.optJSONArray("parts")
              ?.optJSONObject(0)
              ?.optString("text", "")
              .orEmpty()

          if (textContent.isBlank()) {
            return@withContext GeminiApiResult.Error("Model returned an empty response.")
          }
          val compositions = parseCompositionsJson(textContent, preset, wordTimings)
          if (compositions.isEmpty()) {
            GeminiApiResult.Error("Could not parse structured compositions from model output.")
          } else {
            GeminiApiResult.Success(compositions)
          }
        }
      } catch (e: IOException) {
        formatNetworkError(e)
      } catch (e: Exception) {
        GeminiApiResult.Error("AI composition failed: ${e.localizedMessage}")
      }
    }

  private fun parseCompositionsJson(
    rawJson: String,
    preset: PresetDefinition,
    wordTimings: List<WordTiming>,
  ): List<CaptionComposition> {
    val cleaned = rawJson.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val arr =
      if (cleaned.startsWith("[")) {
        JSONArray(cleaned)
      } else {
        JSONObject(cleaned).optJSONArray("compositions") ?: JSONArray()
      }

    val list = mutableListOf<CaptionComposition>()
    for (i in 0 until arr.length()) {
      val obj = arr.optJSONObject(i) ?: continue
      val startMs = obj.optLong("startMs", i * 1800L)
      val endMs = obj.optLong("endMs", (i + 1) * 1800L)
      val regionName = obj.optString("region", SafeRegion.UPPER_CENTER.name)
      val region =
        runCatching { SafeRegion.valueOf(regionName.uppercase()) }
          .getOrDefault(preset.preferredRegions[i % preset.preferredRegions.size])

      val styleVariant =
        runCatching {
          CompositionStyleVariant.valueOf(obj.optString("styleVariant", "HERO_STACK").uppercase())
        }.getOrDefault(CompositionStyleVariant.HERO_STACK)

      val compDepthLayer =
        runCatching {
          DepthLayer.valueOf(obj.optString("depthLayer", "IN_FRONT").uppercase())
        }.getOrDefault(DepthLayer.IN_FRONT)

      // Match spoken word timings falling inside this composition window so word-by-word kinetic motion runs smoothly
      val matchedTimings =
        wordTimings.filter { wt ->
          val mid = (wt.startMs + wt.endMs) / 2L
          mid in (startMs - 80L)..(endMs + 80L)
        }

      var wordCursor = 0
      val elsArr = obj.optJSONArray("elements") ?: JSONArray()
      val elements = mutableListOf<CompositionElement>()
      for (j in 0 until elsArr.length()) {
        val elObj = elsArr.optJSONObject(j) ?: continue
        val text = elObj.optString("text", "").trim()
        if (text.isEmpty()) continue
        val level =
          runCatching {
            HierarchyLevel.valueOf(elObj.optString("hierarchyLevel", "SUPPORTING").uppercase())
          }.getOrDefault(HierarchyLevel.SUPPORTING)
        val role =
          runCatching {
            FontRole.valueOf(elObj.optString("role", "SUPPORTING").uppercase())
          }.getOrDefault(
            when (level) {
              HierarchyLevel.DOMINANT -> FontRole.HEAVY
              HierarchyLevel.EXPRESSIVE -> FontRole.EXPRESSIVE
              else -> FontRole.SUPPORTING
            }
          )
        val defaultFont =
          when (role) {
            FontRole.HEAVY -> preset.heavyFontId
            FontRole.EXPRESSIVE -> preset.expressiveFontId
            FontRole.SUPPORTING -> preset.supportingFontId
          }
        val color =
          elObj.optString("colorHex", "").ifBlank {
            when (level) {
              HierarchyLevel.DOMINANT -> preset.dominantColorHex
              HierarchyLevel.EXPRESSIVE -> preset.expressiveColorHex
              else -> preset.primaryColorHex
            }
          }
        val elDepthLayer =
          runCatching {
            DepthLayer.valueOf(
              elObj.optString(
                "depthLayer",
                if (compDepthLayer == DepthLayer.BEHIND_SUBJECT && level == HierarchyLevel.DOMINANT) {
                  "BEHIND_SUBJECT"
                } else {
                  "IN_FRONT"
                },
              ).uppercase()
            )
          }.getOrDefault(DepthLayer.IN_FRONT)

        val tokenCount = text.split(Regex("\\s+")).count { it.isNotBlank() }.coerceAtLeast(1)
        val wordIdxList =
          if (matchedTimings.isNotEmpty()) {
            (0 until tokenCount).map { k ->
              (wordCursor + k).coerceAtMost(matchedTimings.lastIndex)
            }.also { wordCursor = (wordCursor + tokenCount).coerceAtMost(matchedTimings.size) }
          } else {
            emptyList()
          }

        val glowHexRaw = elObj.optString("glowColorHex", "").takeIf { it.isNotBlank() && it != "null" }

        elements.add(
          CompositionElement(
            id = UUID.randomUUID().toString(),
            text = text,
            role = role,
            hierarchyLevel = level,
            fontFamilyId = elObj.optString("fontFamilyId", defaultFont).ifBlank { defaultFont },
            fontWeight = elObj.optInt("fontWeight", if (role == FontRole.HEAVY) 900 else 600),
            isItalic = elObj.optBoolean("isItalic", false),
            fontSizeSp =
              elObj.optDouble("fontSizeSp", if (level == HierarchyLevel.DOMINANT) 52.0 else 17.5).toFloat(),
            colorHex = color,
            alignment =
              runCatching {
                TextAlignment.valueOf(elObj.optString("alignment", "CENTER").uppercase())
              }.getOrDefault(TextAlignment.CENTER),
            uppercase = elObj.optBoolean("uppercase", level == HierarchyLevel.DOMINANT),
            staggerDelayMs = elObj.optInt("staggerDelayMs", j * 55),
            wordIndices = wordIdxList,
            inlineRowGroup = elObj.optInt("inlineRowGroup", j),
            rotationDeg = elObj.optDouble("rotationDeg", 0.0).toFloat(),
            offsetYEm = elObj.optDouble("offsetYEm", 0.0).toFloat(),
            glowColorHex = glowHexRaw,
            depthLayer = elDepthLayer,
          )
        )
      }
      if (elements.isEmpty()) continue
      list.add(
        CaptionComposition(
          id = UUID.randomUUID().toString(),
          startMs = startMs,
          endMs = endMs,
          originalText = obj.optString("originalText", elements.joinToString(" ") { it.text }),
          region = region,
          anchorXFraction = obj.optDouble("anchorXFraction", region.defaultAnchorX.toDouble()).toFloat(),
          anchorYFraction = obj.optDouble("anchorYFraction", region.defaultAnchorY.toDouble()).toFloat(),
          entryAnimation =
            runCatching {
              EntryAnimationType.valueOf(obj.optString("entryAnimation", preset.defaultEntryAnim.name).uppercase())
            }.getOrDefault(preset.defaultEntryAnim),
          exitAnimation =
            runCatching {
              ExitAnimationType.valueOf(obj.optString("exitAnimation", preset.defaultExitAnim.name).uppercase())
            }.getOrDefault(preset.defaultExitAnim),
          easing = preset.defaultEasing,
          entryDurationMs = obj.optInt("entryDurationMs", preset.entryDurationMs),
          exitDurationMs = obj.optInt("exitDurationMs", preset.exitDurationMs),
          elements = elements,
          wordMappings = matchedTimings,
          depthLayer = compDepthLayer,
          styleVariant = styleVariant,
          backgroundMaskEnabled = compDepthLayer == DepthLayer.BEHIND_SUBJECT,
        )
      )
    }
    return list
  }

  private fun mapHttpError(code: Int, body: String): GeminiApiResult.Error {
    val apiMsg =
      runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
        ?: body.take(180)
    return when (code) {
      400, 401, 403 ->
        GeminiApiResult.Error(
          "API key rejected or lacks permission ($code): $apiMsg",
          isPaidOrQuotaIssue = apiMsg.contains("billing", ignoreCase = true),
        )
      404 ->
        GeminiApiResult.Error(
          "Selected model is unavailable or deprecated (404). Refresh the model list and choose another."
        )
      429 ->
        GeminiApiResult.Error(
          "Rate limit or quota exhausted (429). Free-tier quotas depend on Google's current API policies. $apiMsg",
          isPaidOrQuotaIssue = true,
        )
      else -> GeminiApiResult.Error("Gemini API error ($code): $apiMsg")
    }
  }
}
