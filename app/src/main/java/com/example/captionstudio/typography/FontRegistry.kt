package com.example.captionstudio.typography

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.captionstudio.domain.model.FontRole
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class StudioFontDescriptor(
  val id: String,
  val displayName: String,
  val role: FontRole,
  @param:FontRes val resId: Int,
  val composeFontFamily: FontFamily,
  val availableWeights: List<Int>,
  val supportsItalic: Boolean,
  val styleNote: String,
  val isBundledOffline: Boolean = true,
  val cachedFilePath: String? = null,
)

data class CuratedFontPairing(
  val id: String,
  val name: String,
  val subtitle: String,
  val heavyFontId: String,
  val supportingFontId: String,
  val expressiveFontId: String,
)

object FontRegistry {

  val bundledFonts: List<StudioFontDescriptor> =
    listOf(
      StudioFontDescriptor(
        id = "anton",
        displayName = "Anton",
        role = FontRole.HEAVY,
        resId = R.font.anton,
        composeFontFamily = FontFamily(Font(R.font.anton, FontWeight.Normal)),
        availableWeights = listOf(400, 700, 900),
        supportsItalic = false,
        styleNote = "Compressed ultra-bold editorial display",
      ),
      StudioFontDescriptor(
        id = "archivo_black",
        displayName = "Archivo Black",
        role = FontRole.HEAVY,
        resId = R.font.archivo_black,
        composeFontFamily = FontFamily(Font(R.font.archivo_black, FontWeight.Normal)),
        availableWeights = listOf(400, 800, 900),
        supportsItalic = false,
        styleNote = "Wide geometric grotesque for heavy punch",
      ),
      StudioFontDescriptor(
        id = "bebas_neue",
        displayName = "Bebas Neue",
        role = FontRole.HEAVY,
        resId = R.font.bebas_neue,
        composeFontFamily = FontFamily(Font(R.font.bebas_neue, FontWeight.Normal)),
        availableWeights = listOf(400, 700),
        supportsItalic = false,
        styleNote = "Tall condensed kinetic headline face",
      ),
      StudioFontDescriptor(
        id = "oswald",
        displayName = "Oswald",
        role = FontRole.HEAVY,
        resId = R.font.oswald,
        composeFontFamily = FontFamily(Font(R.font.oswald, FontWeight.Normal)),
        availableWeights = listOf(400, 500, 700),
        supportsItalic = false,
        styleNote = "Crisp condensed gothic display",
      ),
      StudioFontDescriptor(
        id = "syne",
        displayName = "Syne",
        role = FontRole.HEAVY,
        resId = R.font.syne,
        composeFontFamily = FontFamily(Font(R.font.syne, FontWeight.Normal)),
        availableWeights = listOf(400, 700, 800),
        supportsItalic = false,
        styleNote = "Avant-garde extended art-direction display",
      ),
      StudioFontDescriptor(
        id = "montserrat",
        displayName = "Montserrat",
        role = FontRole.HEAVY,
        resId = R.font.montserrat,
        composeFontFamily = FontFamily(Font(R.font.montserrat, FontWeight.Normal)),
        availableWeights = listOf(400, 600, 700, 900),
        supportsItalic = true,
        styleNote = "Bold urban geometric headline sans",
      ),
      StudioFontDescriptor(
        id = "inter",
        displayName = "Inter",
        role = FontRole.SUPPORTING,
        resId = R.font.inter,
        composeFontFamily = FontFamily(Font(R.font.inter, FontWeight.Normal)),
        availableWeights = listOf(400, 500, 600, 700),
        supportsItalic = true,
        styleNote = "Neutral high-legibility UI & supporting sans",
      ),
      StudioFontDescriptor(
        id = "plus_jakarta_sans",
        displayName = "Plus Jakarta Sans",
        role = FontRole.SUPPORTING,
        resId = R.font.plus_jakarta_sans,
        composeFontFamily = FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Normal)),
        availableWeights = listOf(400, 500, 600, 700),
        supportsItalic = true,
        styleNote = "Crisp modern creator sans for lead-ins & whispers",
      ),
      StudioFontDescriptor(
        id = "space_grotesk",
        displayName = "Space Grotesk",
        role = FontRole.SUPPORTING,
        resId = R.font.space_grotesk,
        composeFontFamily = FontFamily(Font(R.font.space_grotesk, FontWeight.Normal)),
        availableWeights = listOf(400, 500, 700),
        supportsItalic = false,
        styleNote = "Tech-forward proportional grotesque sans",
      ),
      StudioFontDescriptor(
        id = "jetbrains_mono",
        displayName = "JetBrains Mono",
        role = FontRole.SUPPORTING,
        resId = R.font.jetbrains_mono,
        composeFontFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal)),
        availableWeights = listOf(400, 600, 700),
        supportsItalic = true,
        styleNote = "Crisp editorial monospace for subtitles & data",
      ),
      StudioFontDescriptor(
        id = "dm_sans",
        displayName = "DM Sans",
        role = FontRole.SUPPORTING,
        resId = R.font.dm_sans,
        composeFontFamily = FontFamily(Font(R.font.dm_sans, FontWeight.Normal)),
        availableWeights = listOf(400, 500, 700),
        supportsItalic = true,
        styleNote = "Geometric low-contrast clean sans",
      ),
      StudioFontDescriptor(
        id = "manrope",
        displayName = "Manrope",
        role = FontRole.SUPPORTING,
        resId = R.font.manrope,
        composeFontFamily = FontFamily(Font(R.font.manrope, FontWeight.Normal)),
        availableWeights = listOf(400, 500, 600, 700),
        supportsItalic = false,
        styleNote = "Modern semi-geometric editorial sans",
      ),
      StudioFontDescriptor(
        id = "roboto",
        displayName = "Roboto",
        role = FontRole.SUPPORTING,
        resId = R.font.roboto,
        composeFontFamily = FontFamily(Font(R.font.roboto, FontWeight.Normal)),
        availableWeights = listOf(400, 500, 700),
        supportsItalic = true,
        styleNote = "Balanced humanist neo-grotesque",
      ),
      StudioFontDescriptor(
        id = "caveat",
        displayName = "Caveat Script",
        role = FontRole.EXPRESSIVE,
        resId = R.font.caveat,
        composeFontFamily = FontFamily(Font(R.font.caveat, FontWeight.Normal)),
        availableWeights = listOf(400, 600, 700),
        supportsItalic = true,
        styleNote = "Handwritten creator brush script for expressive hooks",
      ),
      StudioFontDescriptor(
        id = "pacifico",
        displayName = "Pacifico",
        role = FontRole.EXPRESSIVE,
        resId = R.font.pacifico,
        composeFontFamily = FontFamily(Font(R.font.pacifico, FontWeight.Normal)),
        availableWeights = listOf(400),
        supportsItalic = true,
        styleNote = "Smooth flowing marker script for signature accents",
      ),
      StudioFontDescriptor(
        id = "playfair_display",
        displayName = "Playfair Display",
        role = FontRole.EXPRESSIVE,
        resId = R.font.playfair_display,
        composeFontFamily =
          FontFamily(
            Font(R.font.playfair_display, FontWeight.Normal),
            Font(R.font.playfair_display, FontWeight.Normal, FontStyle.Italic),
          ),
        availableWeights = listOf(400, 600, 700),
        supportsItalic = true,
        styleNote = "High-contrast transitional expressive serif",
      ),
      StudioFontDescriptor(
        id = "bodoni_moda",
        displayName = "Bodoni Moda",
        role = FontRole.EXPRESSIVE,
        resId = R.font.bodoni_moda,
        composeFontFamily =
          FontFamily(
            Font(R.font.bodoni_moda, FontWeight.Normal),
            Font(R.font.bodoni_moda, FontWeight.Normal, FontStyle.Italic),
          ),
        availableWeights = listOf(400, 600, 700),
        supportsItalic = true,
        styleNote = "Dramatic magazine-grade modern Didone serif",
      ),
      StudioFontDescriptor(
        id = "libre_baskerville",
        displayName = "Libre Baskerville",
        role = FontRole.EXPRESSIVE,
        resId = R.font.libre_baskerville,
        composeFontFamily =
          FontFamily(
            Font(R.font.libre_baskerville, FontWeight.Normal),
            Font(R.font.libre_baskerville, FontWeight.Normal, FontStyle.Italic),
          ),
        availableWeights = listOf(400, 700),
        supportsItalic = true,
        styleNote = "Classic warm editorial serif",
      ),
      StudioFontDescriptor(
        id = "cormorant_garamond",
        displayName = "Cormorant Garamond",
        role = FontRole.EXPRESSIVE,
        resId = R.font.cormorant_garamond,
        composeFontFamily =
          FontFamily(
            Font(R.font.cormorant_garamond, FontWeight.Normal),
            Font(R.font.cormorant_garamond, FontWeight.Normal, FontStyle.Italic),
          ),
        availableWeights = listOf(400, 600, 700),
        supportsItalic = true,
        styleNote = "Refined theatrical display serif",
      ),
    )

  val curatedPairings: List<CuratedFontPairing> =
    listOf(
      CuratedFontPairing(
        id = "creator_signature",
        name = "Reference Creator Mix",
        subtitle = "Bebas Neue + Plus Jakarta Sans + Caveat Script",
        heavyFontId = "bebas_neue",
        supportingFontId = "plus_jakarta_sans",
        expressiveFontId = "caveat",
      ),
      CuratedFontPairing(
        id = "editorial_classic",
        name = "Editorial Classic",
        subtitle = "Anton + Inter + Playfair Display",
        heavyFontId = "anton",
        supportingFontId = "inter",
        expressiveFontId = "playfair_display",
      ),
      CuratedFontPairing(
        id = "bold_impact",
        name = "Bold Impact",
        subtitle = "Archivo Black + DM Sans + Pacifico",
        heavyFontId = "archivo_black",
        supportingFontId = "dm_sans",
        expressiveFontId = "pacifico",
      ),
      CuratedFontPairing(
        id = "modern_kinetic",
        name = "Modern Kinetic",
        subtitle = "Bebas Neue + Manrope + Cormorant Garamond",
        heavyFontId = "bebas_neue",
        supportingFontId = "manrope",
        expressiveFontId = "cormorant_garamond",
      ),
      CuratedFontPairing(
        id = "clean_studio",
        name = "Clean Studio",
        subtitle = "Oswald + Roboto + Libre Baskerville",
        heavyFontId = "oswald",
        supportingFontId = "roboto",
        expressiveFontId = "libre_baskerville",
      ),
    )

  private val typefaceCache = ConcurrentHashMap<String, Typeface>()
  private val downloadedFonts = ConcurrentHashMap<String, StudioFontDescriptor>()

  fun allFonts(): List<StudioFontDescriptor> = bundledFonts + downloadedFonts.values.sortedBy { it.displayName }

  fun findById(fontId: String): StudioFontDescriptor {
    return downloadedFonts[fontId]
      ?: bundledFonts.firstOrNull { it.id == fontId }
      ?: bundledFonts.first()
  }

  fun search(query: String, role: FontRole? = null): List<StudioFontDescriptor> {
    val q = query.trim().lowercase()
    return allFonts().filter { font ->
      val roleOk = role == null || font.role == role
      val queryOk =
        q.isEmpty() ||
          font.displayName.lowercase().contains(q) ||
          font.styleNote.lowercase().contains(q) ||
          font.role.name.lowercase().contains(q)
      roleOk && queryOk
    }
  }

  fun getTypeface(
    context: Context,
    fontId: String,
    weight: Int = 400,
    isItalic: Boolean = false,
  ): Typeface {
    val key = "$fontId:$weight:$isItalic"
    typefaceCache[key]?.let { return it }

    val descriptor = findById(fontId)
    val base: Typeface =
      try {
        val cachedPath = descriptor.cachedFilePath
        if (cachedPath != null && File(cachedPath).exists()) {
          Typeface.createFromFile(cachedPath)
        } else {
          ResourcesCompat.getFont(context, descriptor.resId) ?: Typeface.DEFAULT
        }
      } catch (_: Exception) {
        Typeface.DEFAULT
      }

    val style =
      when {
        weight >= 700 && isItalic -> Typeface.BOLD_ITALIC
        weight >= 700 -> Typeface.BOLD
        isItalic -> Typeface.ITALIC
        else -> Typeface.NORMAL
      }

    val styled =
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        Typeface.create(base, weight.coerceIn(100, 900), isItalic)
      } else {
        Typeface.create(base, style)
      }
    typefaceCache[key] = styled
    return styled
  }

  fun loadCachedCustomFonts(context: Context) {
    val dir = File(context.filesDir, "cached_fonts")
    if (!dir.exists()) return
    dir.listFiles { f -> f.extension.equals("ttf", ignoreCase = true) }?.forEach { file ->
      val id = file.nameWithoutExtension
      if (bundledFonts.none { it.id == id }) {
        val pretty =
          id.split("_").joinToString(" ") { part ->
            part.replaceFirstChar { it.uppercaseChar() }
          }
        val composeFamily =
          runCatching { FontFamily(Typeface.createFromFile(file)) }.getOrDefault(FontFamily.SansSerif)
        downloadedFonts[id] =
          StudioFontDescriptor(
            id = id,
            displayName = pretty,
            role = FontRole.SUPPORTING,
            resId = R.font.inter,
            composeFontFamily = composeFamily,
            availableWeights = listOf(400, 700),
            supportsItalic = true,
            styleNote = "Cached local font file",
            isBundledOffline = false,
            cachedFilePath = file.absolutePath,
          )
      }
    }
  }

  suspend fun downloadAndCacheFont(
    context: Context,
    familyName: String,
    role: FontRole = FontRole.SUPPORTING,
  ): Result<StudioFontDescriptor> =
    withContext(Dispatchers.IO) {
      runCatching {
        val sanitized = familyName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        require(sanitized.isNotEmpty()) { "Enter a valid font family name." }
        bundledFonts.firstOrNull { it.id == sanitized }?.let { return@runCatching it }

        val dir = File(context.filesDir, "cached_fonts").apply { mkdirs() }
        val target = File(dir, "$sanitized.ttf")
        if (!target.exists()) {
          val encoded = java.net.URLEncoder.encode(familyName.trim(), "UTF-8")
          val cssUrl = "https://fonts.googleapis.com/css2?family=$encoded:wght@400;700"
          val client = OkHttpClient()
          val cssReq =
            Request.Builder()
              .url(cssUrl)
              .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
              .build()
          val cssBody =
            client.newCall(cssReq).execute().use { resp ->
              check(resp.isSuccessful) { "Google Fonts returned HTTP ${resp.code}" }
              resp.body?.string().orEmpty()
            }
          val ttfMatch = Regex("url\\((https://[^)]+\\.ttf)\\)").find(cssBody)
            ?: error("No downloadable TTF found for '$familyName'")
          val fontUrl = ttfMatch.groupValues[1]
          val fontBytes =
            client.newCall(Request.Builder().url(fontUrl).build()).execute().use { resp ->
              check(resp.isSuccessful) { "Font file download failed (${resp.code})" }
              resp.body?.bytes() ?: error("Empty font response")
            }
          target.writeBytes(fontBytes)
        }
        val composeFamily =
          runCatching { FontFamily(Typeface.createFromFile(target)) }.getOrDefault(FontFamily.SansSerif)
        val descriptor =
          StudioFontDescriptor(
            id = sanitized,
            displayName = familyName.trim(),
            role = role,
            resId = R.font.inter,
            composeFontFamily = composeFamily,
            availableWeights = listOf(400, 700),
            supportsItalic = true,
            styleNote = "Downloaded & cached locally",
            isBundledOffline = false,
            cachedFilePath = target.absolutePath,
          )
        downloadedFonts[sanitized] = descriptor
        descriptor
      }
    }
}
