package com.example.captionstudio.presets

import com.example.captionstudio.domain.model.AnimationEasingType
import com.example.captionstudio.domain.model.EntryAnimationType
import com.example.captionstudio.domain.model.ExitAnimationType
import com.example.captionstudio.domain.model.PresetDefinition
import com.example.captionstudio.domain.model.SafeRegion
import com.example.captionstudio.domain.model.StudioPalette
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Zero-server shareable style link codec.
 * Compresses any PresetDefinition (built-in, customized, or cloned from a reference video)
 * into a compact URL (`captionstudio://style?d=<url-safe-base64-gzip>`) that creators can share
 * via WhatsApp, Instagram DM, TikTok comments, or Discord with $0 backend infrastructure.
 */
object StyleDeepLinkCodec {

  const val SCHEME = "captionstudio"
  const val HOST = "style"
  private const val PREFIX = "$SCHEME://$HOST?d="

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
  }

  @Serializable
  private data class CompactStylePayload(
    val v: Int = 1,
    val n: String,
    val hf: String,
    val sf: String,
    val ef: String,
    val pc: String,
    val dc: String,
    val ec: String,
    val rc: String,
    val ea: String,
    val xa: String,
    val es: String,
    val ed: Int,
    val xd: Int,
    val ds: Float,
    val xs: Float,
    val sh: Float,
    val rg: String,
    val sw: Float = 0f,
    val sc: String = StudioPalette.SHADOW,
    val bg: String? = null,
    val ba: Float = 0.74f,
    val bs: Boolean = false,
    val up: Boolean = false,
  )

  fun encodeToDeepLink(preset: PresetDefinition): String {
    val payload =
      CompactStylePayload(
        n = preset.name.take(28),
        hf = preset.heavyFontId,
        sf = preset.supportingFontId,
        ef = preset.expressiveFontId,
        pc = preset.primaryColorHex,
        dc = preset.dominantColorHex,
        ec = preset.expressiveColorHex,
        rc = preset.dramaticColorHex,
        ea = preset.defaultEntryAnim.name,
        xa = preset.defaultExitAnim.name,
        es = preset.defaultEasing.name,
        ed = preset.entryDurationMs.coerceIn(70, 500),
        xd = preset.exitDurationMs.coerceIn(60, 450),
        ds = preset.dominantScaleMultiplier.coerceIn(0.7f, 1.5f),
        xs = preset.expressiveScaleMultiplier.coerceIn(0.7f, 1.5f),
        sh = preset.shadowRadiusFactor.coerceIn(0f, 2.0f),
        rg = (preset.preferredRegions.firstOrNull() ?: SafeRegion.LOWER_CENTER).name,
        sw = preset.strokeWidthFactor.coerceIn(0f, 0.25f),
        sc = preset.strokeColorHex,
        bg = preset.backgroundPillColorHex,
        ba = preset.backgroundPillAlpha.coerceIn(0.1f, 1f),
        bs = preset.preferBehindSubject,
        up = preset.preferUppercase,
      )
    val rawBytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
    val compressed = gzipCompress(rawBytes)
    val encoded = encodeBase64Url(compressed)
    return "$PREFIX$encoded"
  }

  fun decodeFromDeepLink(rawInput: String): PresetDefinition? {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return null
    val token =
      when {
        trimmed.startsWith(PREFIX, ignoreCase = true) ->
          trimmed.substring(PREFIX.length).substringBefore('&').substringBefore(' ')
        trimmed.contains("d=") ->
          trimmed.substringAfter("d=").substringBefore('&').substringBefore(' ')
        else -> trimmed
      }.trim()
    if (token.length < 8) return null

    return runCatching {
      val compressed = decodeBase64Url(token)
      val jsonStr = gzipDecompress(compressed).toString(Charsets.UTF_8)
      val p = json.decodeFromString<CompactStylePayload>(jsonStr)
      val region = runCatching { SafeRegion.valueOf(p.rg) }.getOrDefault(SafeRegion.LOWER_CENTER)
      val entryAnim =
        runCatching { EntryAnimationType.valueOf(p.ea) }.getOrDefault(EntryAnimationType.SCALE_RISE)
      val exitAnim =
        runCatching { ExitAnimationType.valueOf(p.xa) }.getOrDefault(ExitAnimationType.FADE_DROP)
      val easing =
        runCatching { AnimationEasingType.valueOf(p.es) }
          .getOrDefault(AnimationEasingType.SPRING_OVERSHOOT)

      PresetDefinition(
        id = "shared_${UUID.randomUUID().toString().take(8)}",
        name = p.n.ifBlank { "Shared Style" }.uppercase(),
        tagline = "Imported Style Link • ${p.hf.replace('_', ' ')}",
        description =
          buildString {
            append("Imported via Caption Studio Style Link. ")
            if (p.bs) append("Behind-subject depth enabled. ")
            if (p.bg != null) append("Caption pill background included.")
          }.trim(),
        heavyFontId = p.hf.ifBlank { "bebas_neue" },
        supportingFontId = p.sf.ifBlank { "plus_jakarta_sans" },
        expressiveFontId = p.ef.ifBlank { "caveat" },
        primaryColorHex = sanitizeHex(p.pc, StudioPalette.WHITE),
        dominantColorHex = sanitizeHex(p.dc, StudioPalette.YELLOW),
        expressiveColorHex = sanitizeHex(p.ec, StudioPalette.ORANGE),
        dramaticColorHex = sanitizeHex(p.rc, StudioPalette.RED),
        defaultEntryAnim = entryAnim,
        defaultExitAnim = exitAnim,
        defaultEasing = easing,
        entryDurationMs = p.ed.coerceIn(70, 500),
        exitDurationMs = p.xd.coerceIn(60, 450),
        dominantScaleMultiplier = p.ds.coerceIn(0.7f, 1.5f),
        expressiveScaleMultiplier = p.xs.coerceIn(0.7f, 1.5f),
        shadowRadiusFactor = p.sh.coerceIn(0f, 2.0f),
        preferredRegions = listOf(region, SafeRegion.LOWER_CENTER, SafeRegion.UPPER_CENTER).distinct(),
        sampleSentence = "Why do many creators add crazy text",
        strokeWidthFactor = p.sw.coerceIn(0f, 0.25f),
        strokeColorHex = sanitizeHex(p.sc, StudioPalette.SHADOW),
        backgroundPillColorHex = p.bg?.let { sanitizeHex(it, StudioPalette.CHARCOAL) },
        backgroundPillAlpha = p.ba.coerceIn(0.1f, 1f),
        preferBehindSubject = p.bs,
        preferUppercase = p.up,
        isCustomCloned = true,
      )
    }.getOrNull()
  }

  private fun sanitizeHex(raw: String, fallback: String): String {
    val clean = raw.trim()
    return if (Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$").matches(clean)) clean else fallback
  }

  private fun gzipCompress(input: ByteArray): ByteArray {
    val bos = ByteArrayOutputStream()
    GZIPOutputStream(bos).use { it.write(input) }
    return bos.toByteArray()
  }

  private fun gzipDecompress(compressed: ByteArray): ByteArray {
    val bos = ByteArrayOutputStream()
    GZIPInputStream(ByteArrayInputStream(compressed)).use { gis ->
      val buf = ByteArray(1024)
      var read: Int
      var total = 0
      while (gis.read(buf).also { read = it } != -1) {
        total += read
        require(total <= 32_768) { "Style payload too large" }
        bos.write(buf, 0, read)
      }
    }
    return bos.toByteArray()
  }

  private const val B64_ALPHABET =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

  private fun encodeBase64Url(bytes: ByteArray): String {
    val sb = StringBuilder((bytes.size * 4 + 2) / 3)
    var i = 0
    while (i < bytes.size) {
      val b0 = bytes[i].toInt() and 0xFF
      val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
      val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
      sb.append(B64_ALPHABET[(b0 ushr 2) and 0x3F])
      sb.append(B64_ALPHABET[((b0 and 0x03) shl 4) or ((b1 ushr 4) and 0x0F)])
      if (i + 1 < bytes.size) {
        sb.append(B64_ALPHABET[((b1 and 0x0F) shl 2) or ((b2 ushr 6) and 0x03)])
      }
      if (i + 2 < bytes.size) {
        sb.append(B64_ALPHABET[b2 and 0x3F])
      }
      i += 3
    }
    return sb.toString()
  }

  private fun decodeBase64Url(encoded: String): ByteArray {
    val clean = encoded.trim().trimEnd('=')
    val out = ByteArrayOutputStream((clean.length * 3) / 4)
    var buffer = 0
    var bitsLeft = 0
    for (ch in clean) {
      val value =
        when (ch) {
          in 'A'..'Z' -> ch - 'A'
          in 'a'..'z' -> ch - 'a' + 26
          in '0'..'9' -> ch - '0' + 52
          '-', '+' -> 62
          '_', '/' -> 63
          else -> continue
        }
      buffer = (buffer shl 6) or value
      bitsLeft += 6
      if (bitsLeft >= 8) {
        bitsLeft -= 8
        out.write((buffer ushr bitsLeft) and 0xFF)
      }
    }
    return out.toByteArray()
  }
}
