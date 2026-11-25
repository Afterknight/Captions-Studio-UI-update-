package com.example.captionstudio.engine

import com.example.captionstudio.domain.model.AnimationEasingType
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.CompositionElement
import com.example.captionstudio.domain.model.CompositionStyleVariant
import com.example.captionstudio.domain.model.DepthLayer
import com.example.captionstudio.domain.model.DesignerFrameStyle
import com.example.captionstudio.domain.model.EntryAnimationType
import com.example.captionstudio.domain.model.ExitAnimationType
import com.example.captionstudio.domain.model.FontRole
import com.example.captionstudio.domain.model.FontRoleOverrides
import com.example.captionstudio.domain.model.HierarchyLevel
import com.example.captionstudio.domain.model.HookPacingTemplate
import com.example.captionstudio.domain.model.KaraokeHighlightMode
import com.example.captionstudio.domain.model.LayerMotionEffect
import com.example.captionstudio.domain.model.LetterAnimationStyle
import com.example.captionstudio.domain.model.PresetCategory
import com.example.captionstudio.domain.model.PresetDefinition
import com.example.captionstudio.domain.model.SafeRegion
import com.example.captionstudio.domain.model.SpatialLayoutMode
import com.example.captionstudio.domain.model.StudioPalette
import com.example.captionstudio.domain.model.TextAlignment
import com.example.captionstudio.domain.model.TextSurfaceStyle
import com.example.captionstudio.domain.model.VectorFlourish
import com.example.captionstudio.domain.model.VideoAnalysisReport
import com.example.captionstudio.domain.model.WordTiming
import com.example.captionstudio.presets.PresetCatalog
import java.util.UUID
import kotlin.math.max

data class HookPacingResult(
  val compositions: List<CaptionComposition>,
  val hookCount: Int,
  val bodyCount: Int,
)

object LocalCompositionEngine {

  private val stopWords =
    setOf(
      "a", "an", "the", "and", "or", "but", "if", "of", "in", "on", "at", "to", "for", "with",
      "is", "are", "was", "were", "be", "been", "being", "it", "its", "it's", "that", "as",
      "by", "from", "so", "do", "does", "did", "here", "here's", "there", "there's", "than",
      "then", "into", "like", "just", "about", "many", "why", "how", "what", "when", "your",
      "you're", "my", "our", "this",
    )

  private val dramaticWords =
    setOf(
      "big", "problem", "stop", "never", "wrong", "mistake", "huge", "critical", "warning",
      "danger", "truth", "secret", "no", "zero", "impossible", "broken", "failed", "must",
      "don't", "dont", "hate", "worst",
    )

  private val expressiveWords =
    setOf(
      "crazy", "text", "captions", "caption", "story", "feel", "design", "style", "beautiful",
      "craft", "voice", "magic", "art", "energy", "emotion", "rhythm", "flow", "editorial",
      "cinema", "moment", "deliberate", "imagine", "truly", "dynamic", "faster",
    )

  private val actionCallouts =
    setOf("link in bio", "watch this", "listen closely", "try this", "looks good", "dynamic captions")

  private val seamTriggerWords = setOf("click", "choose", "select", "tap", "generate", "done")

  fun buildProvisionalWordTimings(
    transcript: String,
    totalDurationMs: Long,
  ): List<WordTiming> {
    val tokens =
      transcript
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()

    val usableDuration = max(totalDurationMs, tokens.size * 380L)
    val startOffset = if (totalDurationMs > 1200L) 200L else 0L
    val availableMs = max(usableDuration - startOffset - 180L, tokens.size * 280L)

    val weights =
      tokens.map { token ->
        val clean = token.trim('.', ',', '!', '?', ';', ':', '"', '\'')
        val punctuationPause =
          when {
            token.endsWith(".") || token.endsWith("!") || token.endsWith("?") -> 2.1f
            token.endsWith(",") || token.endsWith(";") -> 1.45f
            else -> 1.0f
          }
        max(1.8f, clean.length * 0.45f) * punctuationPause
      }
    val totalWeight = weights.sum().coerceAtLeast(1f)

    var cursor = startOffset
    return tokens.mapIndexed { idx, token ->
      val sliceMs = ((weights[idx] / totalWeight) * availableMs).toLong().coerceAtLeast(180L)
      val end = (cursor + sliceMs).coerceAtMost(startOffset + availableMs)
      val timing =
        WordTiming(
          word = token,
          startMs = cursor,
          endMs = max(cursor + 140L, end),
          isEstimated = true,
          emphasisScore = scoreWordSalience(token, idx, tokens.size),
        )
      cursor = timing.endMs
      timing
    }
  }

  fun generateCompositions(
    transcript: String,
    wordTimings: List<WordTiming>,
    totalDurationMs: Long,
    preset: PresetDefinition,
    analysisReport: VideoAnalysisReport,
    fontOverrides: FontRoleOverrides = FontRoleOverrides(),
  ): List<CaptionComposition> {
    val timings =
      if (wordTimings.isNotEmpty()) {
        wordTimings
      } else {
        buildProvisionalWordTimings(transcript, totalDurationMs)
      }
    if (timings.isEmpty()) return emptyList()

    val phrases = segmentIntoNaturalPhrases(timings)
    return generateCompositionsFromPhrases(
      phrases = phrases,
      preset = preset,
      analysisReport = analysisReport,
      fontOverrides = fontOverrides,
    )
  }

  fun generateCompositionsFromPhrases(
    phrases: List<List<WordTiming>>,
    preset: PresetDefinition,
    analysisReport: VideoAnalysisReport,
    fontOverrides: FontRoleOverrides = FontRoleOverrides(),
  ): List<CaptionComposition> {
    val nonEmptyPhrases = phrases.filter { it.isNotEmpty() }
    if (nonEmptyPhrases.isEmpty()) return emptyList()
    val regions =
      (analysisReport.safeRegionsRanked + preset.preferredRegions)
        .distinct()
        .ifEmpty { listOf(SafeRegion.LOWER_CENTER, SafeRegion.UPPER_CENTER) }

    val heavyFont = fontOverrides.heavyFontId ?: preset.heavyFontId
    val supportingFont = fontOverrides.supportingFontId ?: preset.supportingFontId
    val expressiveFont = fontOverrides.expressiveFontId ?: preset.expressiveFontId

    val globalHasSubject =
      analysisReport.subjectCoverageFraction > 0.08f || analysisReport.faceRegions.isNotEmpty()

    // Shot-level stability memory: keep captions anchored in the same clean zone throughout a shot
    // unless a scene cut, behind-subject hero callout, or subject/face obstruction requires a change.
    var lastShotKey = -1
    var anchoredShotRegion: SafeRegion? = null

    return nonEmptyPhrases.mapIndexed { phraseIdx, phraseWords ->
      val startMs = phraseWords.first().startMs
      val endMs = max(phraseWords.last().endMs, startMs + 480L)
      val midMs = (startMs + endMs) / 2L
      val rawSentence = phraseWords.joinToString(" ") { it.word }
      val lowerPhrase = phraseWords.joinToString(" ") { cleanToken(it.word) }.trim()
      val maxSalience =
        phraseWords.indices.maxOfOrNull { idx ->
          scoreWordSalience(phraseWords[idx].word, idx, phraseWords.size)
        } ?: 0f

      val shotIndex = findShotIndexForTime(midMs, analysisReport)
      val shot = analysisReport.shotAnalyses.getOrNull(shotIndex)
      val shotHasSubject =
        if (shot != null) shot.subjectCoverage > 0.08f || shot.headVetoRect != null
        else globalHasSubject
      val isSplitActive = shot?.isSplitScreen ?: analysisReport.isSplitScreenDetected

      // Branch cleanly for flagship Signature Presets (BEAT POLARITY, EDITORIAL HALO, STORY ARC, HANDCRAFTED FRAME)
      if (preset.id in PresetCatalog.signaturePresetIds) {
        if (shotIndex != lastShotKey) {
          lastShotKey = shotIndex
          anchoredShotRegion = null
        }
        val (sigRegion, updatedShotAnchor) =
          chooseFrameAwareRegion(
            styleVariant = CompositionStyleVariant.HERO_STACK,
            shot = shot,
            currentShotAnchor = anchoredShotRegion,
            rankedRegions = regions,
            analysisReport = analysisReport,
            shotHasSubject = shotHasSubject,
          )
        anchoredShotRegion = updatedShotAnchor
        return@mapIndexed composeSignatureComposition(
          id = UUID.randomUUID().toString(),
          startMs = startMs,
          endMs = endMs,
          rawSentence = rawSentence,
          words = phraseWords,
          phraseIndex = phraseIdx,
          preset = preset,
          defaultRegion = sigRegion,
          customRegionCoords = null,
          shot = shot,
          fontOverrides = fontOverrides,
        )
      }

      // Branch cleanly for everyday NORMAL caption presets (uniform 1–2 line subtitle / karaoke block)
      if (preset.category == PresetCategory.NORMAL) {
        if (shotIndex != lastShotKey) {
          lastShotKey = shotIndex
          anchoredShotRegion = null
        }
        val (region, updatedShotAnchor) =
          chooseFrameAwareRegion(
            styleVariant = CompositionStyleVariant.STANDARD,
            shot = shot,
            currentShotAnchor = anchoredShotRegion,
            rankedRegions = listOf(SafeRegion.LOWER_CENTER, SafeRegion.UPPER_CENTER) + regions,
            analysisReport = analysisReport,
            shotHasSubject = shotHasSubject,
          )
        anchoredShotRegion = updatedShotAnchor
        val normalRegion =
          if (region == SafeRegion.UPPER_CENTER || region == SafeRegion.CENTER) region
          else SafeRegion.LOWER_CENTER
        val normalEl =
          composeNormalCaptionElement(
            words = phraseWords,
            preset = preset,
            supportingFont = supportingFont,
            heavyFont = heavyFont,
          )
        val zoneMetrics = shot?.zoneMetrics?.get(normalRegion)
        val guarded = applyAutoReadabilityGuard(listOf(normalEl), zoneMetrics)
        return@mapIndexed CaptionComposition(
          id = UUID.randomUUID().toString(),
          startMs = startMs,
          endMs = endMs,
          originalText = rawSentence,
          region = normalRegion,
          anchorXFraction = normalRegion.defaultAnchorX,
          anchorYFraction = normalRegion.defaultAnchorY,
          entryAnimation = preset.defaultEntryAnim,
          exitAnimation = preset.defaultExitAnim,
          easing = preset.defaultEasing,
          entryDurationMs = preset.entryDurationMs,
          exitDurationMs = preset.exitDurationMs,
          elements = guarded,
          wordMappings = phraseWords,
          depthLayer = DepthLayer.IN_FRONT,
          styleVariant = CompositionStyleVariant.STANDARD,
          backgroundMaskEnabled = preset.preferBehindSubject,
        )
      }

      val stopCount = phraseWords.count { cleanToken(it.word) in stopWords }
      val isWhisperBeat =
        phraseWords.size in 2..3 &&
          lowerPhrase !in actionCallouts &&
          phraseWords.none { cleanToken(it.word) in dramaticWords } &&
          (phraseWords.size == 2 || stopCount >= 2 || maxSalience < 3.6f)

      val isActionGlow = lowerPhrase in actionCallouts || lowerPhrase.contains("link in bio")
      val isNegationStack =
        cleanToken(phraseWords.first().word) == "no" && phraseWords.size >= 3
      val isSeamCallout =
        isSplitActive ||
          (phraseWords.size in 2..4 && cleanToken(phraseWords.first().word) in seamTriggerWords)

      val styleVariant =
        when {
          isSeamCallout -> CompositionStyleVariant.SEAM_CALLOUT
          isActionGlow -> CompositionStyleVariant.GLOW_BANNER
          isNegationStack -> CompositionStyleVariant.LEFT_ANCHOR_STACK
          isWhisperBeat -> CompositionStyleVariant.WHISPER_NEGATIVE_SPACE
          else -> CompositionStyleVariant.HERO_STACK
        }

      // Reset anchored zone only on a genuine scene cut / new shot
      if (shotIndex != lastShotKey) {
        lastShotKey = shotIndex
        anchoredShotRegion = null
      }

      val (region, updatedShotAnchor) =
        chooseFrameAwareRegion(
          styleVariant = styleVariant,
          shot = shot,
          currentShotAnchor = anchoredShotRegion,
          rankedRegions = regions,
          analysisReport = analysisReport,
          shotHasSubject = shotHasSubject,
        )
      anchoredShotRegion = updatedShotAnchor

      val useBehindSubject =
        shotHasSubject &&
          (preset.preferBehindSubject ||
            styleVariant == CompositionStyleVariant.HERO_STACK ||
            (styleVariant == CompositionStyleVariant.GLOW_BANNER && region == SafeRegion.UPPER_CENTER))

      val alignment =
        when (region) {
          SafeRegion.UPPER_LEFT,
          SafeRegion.LEFT_SIDE -> TextAlignment.LEFT
          SafeRegion.UPPER_RIGHT,
          SafeRegion.RIGHT_SIDE -> TextAlignment.RIGHT
          else -> TextAlignment.CENTER
        }

      val rawElements =
        when (styleVariant) {
          CompositionStyleVariant.WHISPER_NEGATIVE_SPACE ->
            composeWhisperBeatElements(
              words = phraseWords,
              supportingFont = supportingFont,
              heavyFont = heavyFont,
              preset = preset,
              alignment = alignment,
            )
          CompositionStyleVariant.LEFT_ANCHOR_STACK ->
            composeLeftStaircaseElements(
              words = phraseWords,
              heavyFont = heavyFont,
              supportingFont = supportingFont,
              preset = preset,
            )
          CompositionStyleVariant.SEAM_CALLOUT ->
            composeSeamCalloutElements(
              words = phraseWords,
              supportingFont = supportingFont,
              preset = preset,
            )
          CompositionStyleVariant.GLOW_BANNER ->
            composeGlowBannerElements(
              words = phraseWords,
              heavyFont = heavyFont,
              supportingFont = supportingFont,
              preset = preset,
              behindSubject = useBehindSubject,
            )
          else ->
            composeHeroStackElements(
              words = phraseWords,
              phraseIndex = phraseIdx,
              preset = preset,
              heavyFont = heavyFont,
              supportingFont = supportingFont,
              expressiveFont = expressiveFont,
              alignment = alignment,
              behindSubject = useBehindSubject,
            )
        }.map { el ->
          el.copy(
            strokeWidthFactor = if (preset.strokeWidthFactor > 0f) preset.strokeWidthFactor else el.strokeWidthFactor,
            strokeColorHex = if (preset.strokeWidthFactor > 0f) preset.strokeColorHex else el.strokeColorHex,
            backgroundColorHex = preset.backgroundPillColorHex ?: el.backgroundColorHex,
            backgroundAlpha = if (preset.backgroundPillColorHex != null) preset.backgroundPillAlpha else el.backgroundAlpha,
            karaokeMode = preset.defaultKaraokeMode,
            karaokeHighlightColorHex = preset.karaokeHighlightColorHex,
            karaokePillColorHex = preset.karaokePillColorHex,
          )
        }

      // Auto-readability contrast guard based on the actual background brightness/clutter behind this zone
      val zoneMetrics = shot?.zoneMetrics?.get(region)
      val elements = applyAutoReadabilityGuard(rawElements, zoneMetrics)

      CaptionComposition(
        id = UUID.randomUUID().toString(),
        startMs = startMs,
        endMs = endMs,
        originalText = rawSentence,
        region = region,
        anchorXFraction = region.defaultAnchorX,
        anchorYFraction = region.defaultAnchorY,
        entryAnimation = preset.defaultEntryAnim,
        exitAnimation = preset.defaultExitAnim,
        easing = preset.defaultEasing,
        entryDurationMs = preset.entryDurationMs,
        exitDurationMs = preset.exitDurationMs,
        elements = elements,
        wordMappings = phraseWords,
        depthLayer = if (useBehindSubject) DepthLayer.BEHIND_SUBJECT else DepthLayer.IN_FRONT,
        styleVariant = styleVariant,
        backgroundMaskEnabled = useBehindSubject,
      )
    }
  }

  /**
   * Builds a clean, uniform 1–2 line everyday subtitle / karaoke element for [PresetCategory.NORMAL].
   */
  private fun composeNormalCaptionElement(
    words: List<WordTiming>,
    preset: PresetDefinition,
    supportingFont: String,
    heavyFont: String,
  ): CompositionElement {
    val phraseText = words.joinToString(" ") { it.word }.trim()
    val fontId = if (preset.preferUppercase) heavyFont else supportingFont
    val weight = if (preset.preferUppercase || preset.defaultKaraokeMode != KaraokeHighlightMode.OFF) 800 else 600
    val baseSize = (22.5f * preset.dominantScaleMultiplier).coerceIn(17f, 34f)
    return CompositionElement(
      id = UUID.randomUUID().toString(),
      text = phraseText,
      role = FontRole.SUPPORTING,
      hierarchyLevel = HierarchyLevel.SUPPORTING,
      fontFamilyId = fontId,
      fontWeight = weight,
      isItalic = false,
      fontSizeSp = baseSize,
      colorHex = preset.primaryColorHex,
      alignment = TextAlignment.CENTER,
      uppercase = preset.preferUppercase,
      wordIndices = words.indices.toList(),
      inlineRowGroup = 0,
      strokeWidthFactor = preset.strokeWidthFactor,
      strokeColorHex = preset.strokeColorHex,
      backgroundColorHex = preset.backgroundPillColorHex,
      backgroundAlpha = preset.backgroundPillAlpha,
      backgroundCornerRadiusDp = 12f,
      shadowEnabled = true,
      shadowIntensity = preset.shadowRadiusFactor.coerceIn(0.6f, 1.5f),
      depthLayer = DepthLayer.IN_FRONT,
      karaokeMode = preset.defaultKaraokeMode,
      karaokeHighlightColorHex = preset.karaokeHighlightColorHex,
      karaokePillColorHex = preset.karaokePillColorHex,
    )
  }

  fun buildNormalElementForComposition(
    comp: CaptionComposition,
    preset: PresetDefinition,
    fontOverrides: FontRoleOverrides = FontRoleOverrides(),
  ): CompositionElement {
    val supportingFont = fontOverrides.supportingFontId ?: preset.supportingFontId
    val heavyFont = fontOverrides.heavyFontId ?: preset.heavyFontId
    val words =
      if (comp.wordMappings.isNotEmpty()) {
        comp.wordMappings
      } else {
        buildProvisionalWordTimings(
          comp.originalText.ifBlank { comp.elements.joinToString(" ") { it.text } },
          max(400L, comp.endMs - comp.startMs),
        ).map { it.copy(startMs = it.startMs + comp.startMs, endMs = it.endMs + comp.startMs) }
      }
    return composeNormalCaptionElement(words, preset, supportingFont, heavyFont)
  }

  fun buildKineticElementsForComposition(
    comp: CaptionComposition,
    phraseIndex: Int,
    preset: PresetDefinition,
    analysisReport: VideoAnalysisReport,
    fontOverrides: FontRoleOverrides = FontRoleOverrides(),
  ): List<CompositionElement> {
    val heavyFont = fontOverrides.heavyFontId ?: preset.heavyFontId
    val supportingFont = fontOverrides.supportingFontId ?: preset.supportingFontId
    val expressiveFont = fontOverrides.expressiveFontId ?: preset.expressiveFontId
    val words =
      if (comp.wordMappings.isNotEmpty()) {
        comp.wordMappings
      } else {
        buildProvisionalWordTimings(
          comp.originalText.ifBlank { comp.elements.joinToString(" ") { it.text } },
          max(400L, comp.endMs - comp.startMs),
        ).map { it.copy(startMs = it.startMs + comp.startMs, endMs = it.endMs + comp.startMs) }
      }
    if (words.isEmpty()) return comp.elements
    val hasSubject =
      preset.preferBehindSubject ||
        analysisReport.subjectCoverageFraction > 0.08f ||
        analysisReport.faceRegions.isNotEmpty()
    return composeHeroStackElements(
      words = words,
      phraseIndex = phraseIndex,
      preset = preset,
      heavyFont = heavyFont,
      supportingFont = supportingFont,
      expressiveFont = expressiveFont,
      alignment = TextAlignment.CENTER,
      behindSubject = hasSubject,
    ).map { el ->
      el.copy(
        strokeWidthFactor = preset.strokeWidthFactor,
        strokeColorHex = preset.strokeColorHex,
        backgroundColorHex = preset.backgroundPillColorHex,
        backgroundAlpha = preset.backgroundPillAlpha,
        karaokeMode = preset.defaultKaraokeMode,
        karaokeHighlightColorHex = preset.karaokeHighlightColorHex,
        karaokePillColorHex = preset.karaokePillColorHex,
      )
    }
  }

  /**
   * Re-styles an existing [CaptionComposition] using one of the 4 flagship Signature Presets while
   * preserving transcript text, exact word timings, composition ID, and custom manual drag coords.
   */
  fun buildSignatureComposition(
    comp: CaptionComposition,
    phraseIndex: Int,
    preset: PresetDefinition,
    analysisReport: VideoAnalysisReport,
    fontOverrides: FontRoleOverrides = FontRoleOverrides(),
  ): CaptionComposition {
    val words =
      if (comp.wordMappings.isNotEmpty()) {
        comp.wordMappings
      } else {
        buildProvisionalWordTimings(
          comp.originalText.ifBlank { comp.elements.joinToString(" ") { it.text } },
          max(400L, comp.endMs - comp.startMs),
        ).map { it.copy(startMs = it.startMs + comp.startMs, endMs = it.endMs + comp.startMs) }
      }
    val midMs = (comp.startMs + comp.endMs) / 2L
    val shot = analysisReport.shotAnalyses.getOrNull(findShotIndexForTime(midMs, analysisReport))
    val customCoords =
      if (comp.region == SafeRegion.CUSTOM) comp.anchorXFraction to comp.anchorYFraction else null
    val fallbackRegion =
      if (comp.region != SafeRegion.CUSTOM) comp.region else SafeRegion.LOWER_CENTER
    return composeSignatureComposition(
      id = comp.id,
      startMs = comp.startMs,
      endMs = comp.endMs,
      rawSentence = comp.originalText.ifBlank { words.joinToString(" ") { it.word } },
      words = words,
      phraseIndex = phraseIndex,
      preset = preset,
      defaultRegion = fallbackRegion,
      customRegionCoords = customCoords,
      shot = shot,
      fontOverrides = fontOverrides,
    )
  }

  private fun composeSignatureComposition(
    id: String,
    startMs: Long,
    endMs: Long,
    rawSentence: String,
    words: List<WordTiming>,
    phraseIndex: Int,
    preset: PresetDefinition,
    defaultRegion: SafeRegion,
    customRegionCoords: Pair<Float, Float>?,
    shot: com.example.captionstudio.domain.model.ShotVisualAnalysis?,
    fontOverrides: FontRoleOverrides,
  ): CaptionComposition {
    val safeWords =
      if (words.isNotEmpty()) words
      else listOf(WordTiming(rawSentence.ifBlank { "Caption" }, startMs, endMs, isEstimated = true))
    val heavyFont = fontOverrides.heavyFontId ?: preset.heavyFontId
    val supportingFont = fontOverrides.supportingFontId ?: preset.supportingFontId
    val expressiveFont = fontOverrides.expressiveFontId ?: preset.expressiveFontId

    return when (preset.id) {
      PresetCatalog.SIGNATURE_BEAT_POLARITY_ID ->
        composeBeatPolarity(
          id = id,
          startMs = startMs,
          endMs = endMs,
          rawSentence = rawSentence,
          words = safeWords,
          phraseIndex = phraseIndex,
          preset = preset,
          groteskFont = supportingFont,
          defaultRegion = defaultRegion,
          customRegionCoords = customRegionCoords,
        )

      PresetCatalog.SIGNATURE_EDITORIAL_HALO_ID ->
        composeEditorialHalo(
          id = id,
          startMs = startMs,
          endMs = endMs,
          rawSentence = rawSentence,
          words = safeWords,
          phraseIndex = phraseIndex,
          preset = preset,
          sansFont = supportingFont,
          serifFont = expressiveFont,
          defaultRegion = defaultRegion,
          customRegionCoords = customRegionCoords,
        )

      PresetCatalog.SIGNATURE_STORY_ARC_ID ->
        composeStoryArc(
          id = id,
          startMs = startMs,
          endMs = endMs,
          rawSentence = rawSentence,
          words = safeWords,
          phraseIndex = phraseIndex,
          preset = preset,
          heavyFont = heavyFont,
          whisperFont = supportingFont,
          customRegionCoords = customRegionCoords,
        )

      else ->
        composeHandcraftedFrame(
          id = id,
          startMs = startMs,
          endMs = endMs,
          rawSentence = rawSentence,
          words = safeWords,
          phraseIndex = phraseIndex,
          preset = preset,
          serifFont = supportingFont,
          markerFont = expressiveFont,
          defaultRegion = defaultRegion,
          customRegionCoords = customRegionCoords,
          shot = shot,
        )
    }
  }

  /**
   * Signature Preset 1: BEAT POLARITY
   * Tight lowercase geometric grotesk (-0.03em tracking) with CHAR_REVEAL beat typing and
   * alternating POLARITY_CARD cream (#EFECE4 / #111318) and midnight navy (#0E1A2B / #EFECE4 + #C8A252 gold) flips.
   */
  private fun composeBeatPolarity(
    id: String,
    startMs: Long,
    endMs: Long,
    rawSentence: String,
    words: List<WordTiming>,
    phraseIndex: Int,
    preset: PresetDefinition,
    groteskFont: String,
    defaultRegion: SafeRegion,
    customRegionCoords: Pair<Float, Float>?,
  ): CaptionComposition {
    val isDarkNavyCard = phraseIndex % 2 != 0
    val inkHex = if (isDarkNavyCard) "#EFECE4" else "#111318"
    val lowerText = words.joinToString(" ") { it.word }.trim().lowercase()
    val region =
      if (customRegionCoords != null) SafeRegion.CUSTOM
      else if (defaultRegion == SafeRegion.UPPER_CENTER || defaultRegion == SafeRegion.CENTER) defaultRegion
      else SafeRegion.LOWER_CENTER

    val element =
      CompositionElement(
        id = UUID.randomUUID().toString(),
        text = lowerText,
        role = FontRole.SUPPORTING,
        hierarchyLevel = HierarchyLevel.SUPPORTING,
        fontFamilyId = groteskFont,
        fontWeight = 700,
        isItalic = false,
        fontSizeSp = (24.5f * preset.dominantScaleMultiplier).coerceIn(19f, 34f),
        colorHex = inkHex,
        alignment = TextAlignment.CENTER,
        uppercase = false,
        letterSpacingEm = -0.03f,
        wordIndices = words.indices.toList(),
        inlineRowGroup = 0,
        shadowEnabled = false,
        shadowIntensity = 0.6f,
        depthLayer = DepthLayer.IN_FRONT,
        letterAnimation = LetterAnimationStyle.CHAR_REVEAL,
        surfaceStyle = TextSurfaceStyle.STANDARD,
        designerFrame = DesignerFrameStyle.POLARITY_CARD,
        polarityVariantIndex = phraseIndex,
        flourish = VectorFlourish.NONE,
      )

    return CaptionComposition(
      id = id,
      startMs = startMs,
      endMs = endMs,
      originalText = rawSentence,
      region = region,
      anchorXFraction = customRegionCoords?.first ?: region.defaultAnchorX,
      anchorYFraction = customRegionCoords?.second ?: region.defaultAnchorY,
      entryAnimation = preset.defaultEntryAnim,
      exitAnimation = preset.defaultExitAnim,
      easing = preset.defaultEasing,
      entryDurationMs = preset.entryDurationMs,
      exitDurationMs = preset.exitDurationMs,
      elements = listOf(element),
      wordMappings = words,
      depthLayer = DepthLayer.IN_FRONT,
      styleVariant = CompositionStyleVariant.HERO_STACK,
      spatialMode = SpatialLayoutMode.STANDARD,
      backgroundMaskEnabled = false,
    )
  }

  /**
   * Signature Preset 2: EDITORIAL HALO
   * Tight-tracked white Inter + HALATION_BLOOM glow, +8% optically matched italic Playfair Display
   * accents, and LEFT_RIGHT_SPLIT subject-flanking framing on 2–4 word emphasis blocks.
   */
  private fun composeEditorialHalo(
    id: String,
    startMs: Long,
    endMs: Long,
    rawSentence: String,
    words: List<WordTiming>,
    phraseIndex: Int,
    preset: PresetDefinition,
    sansFont: String,
    serifFont: String,
    defaultRegion: SafeRegion,
    customRegionCoords: Pair<Float, Float>?,
  ): CaptionComposition {
    val scores = words.mapIndexed { idx, wt -> idx to scoreWordSalience(wt.word, idx, words.size) }
    val emphasisIdx = scores.maxByOrNull { it.second }?.first ?: words.lastIndex
    val maxSalience = scores.maxOfOrNull { it.second } ?: 0f
    val useLeftRightSplit =
      words.size in 2..4 && (maxSalience >= 1.8f || phraseIndex % 2 == 0)

    val sansSizeSp = 24f * preset.dominantScaleMultiplier
    val serifSizeSp = sansSizeSp * 1.08f // +8% optical match for italic Playfair Display next to Inter

    val elements =
      if (useLeftRightSplit) {
        val splitAt =
          when (words.size) {
            2 -> 1
            3 -> if (emphasisIdx == 0) 1 else 2
            else -> 2
          }
        val leftRange = 0 until splitAt
        val rightRange = splitAt until words.size
        val emphasisOnRight = emphasisIdx >= splitAt

        val leftEl =
          CompositionElement(
            id = UUID.randomUUID().toString(),
            text = leftRange.joinToString(" ") { words[it].word },
            role = if (!emphasisOnRight) FontRole.EXPRESSIVE else FontRole.SUPPORTING,
            hierarchyLevel = if (!emphasisOnRight) HierarchyLevel.EXPRESSIVE else HierarchyLevel.SUPPORTING,
            fontFamilyId = if (!emphasisOnRight) serifFont else sansFont,
            fontWeight = if (!emphasisOnRight) 600 else 600,
            isItalic = !emphasisOnRight,
            fontSizeSp = if (!emphasisOnRight) serifSizeSp else sansSizeSp,
            colorHex = if (!emphasisOnRight) StudioPalette.CREAM else StudioPalette.WHITE,
            glowColorHex = StudioPalette.WHITE,
            alignment = TextAlignment.RIGHT,
            uppercase = false,
            letterSpacingEm = if (!emphasisOnRight) 0f else -0.025f,
            wordIndices = leftRange.toList(),
            inlineRowGroup = 0,
            shadowEnabled = true,
            shadowIntensity = 0.9f,
            depthLayer = DepthLayer.IN_FRONT,
            letterAnimation = LetterAnimationStyle.NONE,
            surfaceStyle = TextSurfaceStyle.HALATION_BLOOM,
            designerFrame = DesignerFrameStyle.NONE,
            flourish = VectorFlourish.NONE,
          )
        val rightEl =
          CompositionElement(
            id = UUID.randomUUID().toString(),
            text = rightRange.joinToString(" ") { words[it].word },
            role = if (emphasisOnRight) FontRole.EXPRESSIVE else FontRole.SUPPORTING,
            hierarchyLevel = if (emphasisOnRight) HierarchyLevel.EXPRESSIVE else HierarchyLevel.SUPPORTING,
            fontFamilyId = if (emphasisOnRight) serifFont else sansFont,
            fontWeight = 600,
            isItalic = emphasisOnRight,
            fontSizeSp = if (emphasisOnRight) serifSizeSp else sansSizeSp,
            colorHex = if (emphasisOnRight) StudioPalette.CREAM else StudioPalette.WHITE,
            glowColorHex = StudioPalette.WHITE,
            alignment = TextAlignment.LEFT,
            uppercase = false,
            letterSpacingEm = if (emphasisOnRight) 0f else -0.025f,
            staggerDelayMs = 60,
            wordIndices = rightRange.toList(),
            inlineRowGroup = 0,
            shadowEnabled = true,
            shadowIntensity = 0.9f,
            depthLayer = DepthLayer.IN_FRONT,
            letterAnimation = LetterAnimationStyle.NONE,
            surfaceStyle = TextSurfaceStyle.HALATION_BLOOM,
            designerFrame = DesignerFrameStyle.NONE,
            flourish = VectorFlourish.NONE,
          )
        listOf(leftEl, rightEl)
      } else {
        // Inline tight-tracked white Inter + +8% italic Playfair Display accent
        val built = mutableListOf<CompositionElement>()
        var idx = 0
        while (idx < words.size) {
          if (idx == emphasisIdx) {
            built.add(
              CompositionElement(
                id = UUID.randomUUID().toString(),
                text = words[idx].word,
                role = FontRole.EXPRESSIVE,
                hierarchyLevel = HierarchyLevel.EXPRESSIVE,
                fontFamilyId = serifFont,
                fontWeight = 600,
                isItalic = true,
                fontSizeSp = serifSizeSp,
                colorHex = StudioPalette.CREAM,
                glowColorHex = StudioPalette.WHITE,
                alignment = TextAlignment.CENTER,
                uppercase = false,
                letterSpacingEm = 0f,
                staggerDelayMs = if (idx > 0) 55 else 0,
                wordIndices = listOf(idx),
                inlineRowGroup = 0,
                shadowEnabled = true,
                shadowIntensity = 0.9f,
                depthLayer = DepthLayer.IN_FRONT,
                letterAnimation = LetterAnimationStyle.NONE,
                surfaceStyle = TextSurfaceStyle.HALATION_BLOOM,
                designerFrame = DesignerFrameStyle.NONE,
                flourish = VectorFlourish.NONE,
              )
            )
            idx++
          } else {
            val span = mutableListOf<Int>()
            while (idx < words.size && idx != emphasisIdx) {
              span.add(idx)
              idx++
            }
            built.add(
              CompositionElement(
                id = UUID.randomUUID().toString(),
                text = span.joinToString(" ") { words[it].word },
                role = FontRole.SUPPORTING,
                hierarchyLevel = HierarchyLevel.SUPPORTING,
                fontFamilyId = sansFont,
                fontWeight = 600,
                isItalic = false,
                fontSizeSp = sansSizeSp,
                colorHex = StudioPalette.WHITE,
                glowColorHex = StudioPalette.WHITE,
                alignment = TextAlignment.CENTER,
                uppercase = false,
                letterSpacingEm = -0.025f,
                wordIndices = span,
                inlineRowGroup = 0,
                shadowEnabled = true,
                shadowIntensity = 0.9f,
                depthLayer = DepthLayer.IN_FRONT,
                letterAnimation = LetterAnimationStyle.NONE,
                surfaceStyle = TextSurfaceStyle.HALATION_BLOOM,
                designerFrame = DesignerFrameStyle.NONE,
                flourish = VectorFlourish.NONE,
              )
            )
          }
        }
        built
      }

    val region =
      when {
        customRegionCoords != null -> SafeRegion.CUSTOM
        useLeftRightSplit -> SafeRegion.CENTER
        else -> defaultRegion
      }
    return CaptionComposition(
      id = id,
      startMs = startMs,
      endMs = endMs,
      originalText = rawSentence,
      region = region,
      anchorXFraction = customRegionCoords?.first ?: 0.50f,
      anchorYFraction = customRegionCoords?.second ?: if (useLeftRightSplit) 0.48f else region.defaultAnchorY,
      entryAnimation = preset.defaultEntryAnim,
      exitAnimation = preset.defaultExitAnim,
      easing = preset.defaultEasing,
      entryDurationMs = preset.entryDurationMs,
      exitDurationMs = preset.exitDurationMs,
      elements = elements,
      wordMappings = words,
      depthLayer = DepthLayer.IN_FRONT,
      styleVariant = CompositionStyleVariant.HERO_STACK,
      spatialMode = if (useLeftRightSplit) SpatialLayoutMode.LEFT_RIGHT_SPLIT else SpatialLayoutMode.STANDARD,
      backgroundMaskEnabled = false,
    )
  }

  /**
   * Signature Preset 3: STORY ARC
   * Warm-gold italic whispers (#FFD166) curving overhead via CURVED_ARC_PATH paired with glossy
   * INFLATED_3D hero bursts on peak words.
   */
  private fun composeStoryArc(
    id: String,
    startMs: Long,
    endMs: Long,
    rawSentence: String,
    words: List<WordTiming>,
    phraseIndex: Int,
    preset: PresetDefinition,
    heavyFont: String,
    whisperFont: String,
    customRegionCoords: Pair<Float, Float>?,
  ): CaptionComposition {
    val scores = words.mapIndexed { idx, wt -> idx to scoreWordSalience(wt.word, idx, words.size) }
    val peakIdx = scores.maxByOrNull { it.second }?.first ?: words.lastIndex
    val maxSalience = scores.maxOfOrNull { it.second } ?: 0f
    val hasDramatic = words.any { cleanToken(it.word) in dramaticWords || it.word.endsWith("!") }

    val includeInflatedBurst =
      words.size == 1 ||
        hasDramatic ||
        maxSalience >= 3.0f ||
        (words.size >= 2 && phraseIndex % 2 == 1)

    val elements = mutableListOf<CompositionElement>()
    if (words.size == 1) {
      elements.add(
        CompositionElement(
          id = UUID.randomUUID().toString(),
          text = words.first().word.trim('.', ',', ';', ':'),
          role = FontRole.HEAVY,
          hierarchyLevel = HierarchyLevel.DOMINANT,
          fontFamilyId = heavyFont,
          fontWeight = 900,
          isItalic = false,
          fontSizeSp = (54f * preset.dominantScaleMultiplier).coerceIn(38f, 66f),
          colorHex = StudioPalette.GOLD_AMBER,
          alignment = TextAlignment.CENTER,
          uppercase = true,
          letterSpacingEm = -0.02f,
          wordIndices = listOf(0),
          inlineRowGroup = 0,
          shadowEnabled = true,
          shadowIntensity = 1.0f,
          depthLayer = DepthLayer.IN_FRONT,
          letterAnimation = LetterAnimationStyle.STAGGER_POP,
          surfaceStyle = TextSurfaceStyle.INFLATED_3D,
          designerFrame = DesignerFrameStyle.NONE,
          flourish = VectorFlourish.SPARKLE_BURST,
        )
      )
    } else if (includeInflatedBurst) {
      // Pair overhead curved warm-gold italic whisper on row 0 with INFLATED_3D hero burst on row 1
      val whisperIndices = words.indices.filter { it != peakIdx }
      if (whisperIndices.isNotEmpty()) {
        elements.add(
          CompositionElement(
            id = UUID.randomUUID().toString(),
            text = whisperIndices.joinToString(" ") { words[it].word },
            role = FontRole.EXPRESSIVE,
            hierarchyLevel = HierarchyLevel.EXPRESSIVE,
            fontFamilyId = whisperFont,
            fontWeight = 600,
            isItalic = true,
            fontSizeSp = (25f * preset.expressiveScaleMultiplier).coerceIn(20f, 34f),
            colorHex = "#FFD166",
            glowColorHex = "#FFD166",
            alignment = TextAlignment.CENTER,
            uppercase = false,
            letterSpacingEm = 0.01f,
            wordIndices = whisperIndices,
            inlineRowGroup = 0,
            shadowEnabled = true,
            shadowIntensity = 0.9f,
            depthLayer = DepthLayer.IN_FRONT,
            letterAnimation = LetterAnimationStyle.STAGGER_POP,
            surfaceStyle = TextSurfaceStyle.STANDARD,
            designerFrame = DesignerFrameStyle.NONE,
            flourish = VectorFlourish.NONE,
          )
        )
      }
      val peakWord = words[peakIdx].word.trim('.', ',', ';', ':')
      elements.add(
        CompositionElement(
          id = UUID.randomUUID().toString(),
          text = peakWord,
          role = FontRole.HEAVY,
          hierarchyLevel = HierarchyLevel.DOMINANT,
          fontFamilyId = heavyFont,
          fontWeight = 900,
          isItalic = false,
          fontSizeSp = (48f * preset.dominantScaleMultiplier).coerceIn(36f, 64f),
          colorHex = if (hasDramatic) preset.dramaticColorHex else StudioPalette.GOLD_AMBER,
          alignment = TextAlignment.CENTER,
          uppercase = true,
          letterSpacingEm = -0.02f,
          staggerDelayMs = 80,
          wordIndices = listOf(peakIdx),
          inlineRowGroup = 1,
          shadowEnabled = true,
          shadowIntensity = 1.0f,
          depthLayer = DepthLayer.IN_FRONT,
          letterAnimation = LetterAnimationStyle.STAGGER_POP,
          surfaceStyle = TextSurfaceStyle.INFLATED_3D,
          designerFrame = DesignerFrameStyle.NONE,
          flourish = VectorFlourish.SPARKLE_BURST,
        )
      )
    } else {
      // Pure warm-gold italic whisper gliding along the overhead quadratic Bezier arc
      elements.add(
        CompositionElement(
          id = UUID.randomUUID().toString(),
          text = words.joinToString(" ") { it.word },
          role = FontRole.EXPRESSIVE,
          hierarchyLevel = HierarchyLevel.EXPRESSIVE,
          fontFamilyId = whisperFont,
          fontWeight = 600,
          isItalic = true,
          fontSizeSp = (26.5f * preset.expressiveScaleMultiplier).coerceIn(21f, 34f),
          colorHex = "#FFD166",
          glowColorHex = "#FFD166",
          alignment = TextAlignment.CENTER,
          uppercase = false,
          letterSpacingEm = 0.01f,
          wordIndices = words.indices.toList(),
          inlineRowGroup = 0,
          shadowEnabled = true,
          shadowIntensity = 0.9f,
          depthLayer = DepthLayer.IN_FRONT,
          letterAnimation = LetterAnimationStyle.STAGGER_POP,
          surfaceStyle = TextSurfaceStyle.STANDARD,
          designerFrame = DesignerFrameStyle.NONE,
          flourish = VectorFlourish.NONE,
        )
      )
    }

    val region = if (customRegionCoords != null) SafeRegion.CUSTOM else SafeRegion.UPPER_CENTER
    return CaptionComposition(
      id = id,
      startMs = startMs,
      endMs = endMs,
      originalText = rawSentence,
      region = region,
      anchorXFraction = customRegionCoords?.first ?: 0.50f,
      anchorYFraction = customRegionCoords?.second ?: 0.24f,
      entryAnimation = preset.defaultEntryAnim,
      exitAnimation = preset.defaultExitAnim,
      easing = preset.defaultEasing,
      entryDurationMs = preset.entryDurationMs,
      exitDurationMs = preset.exitDurationMs,
      elements = elements,
      wordMappings = words,
      depthLayer = DepthLayer.IN_FRONT,
      styleVariant = CompositionStyleVariant.HERO_STACK,
      spatialMode = SpatialLayoutMode.CURVED_ARC_PATH,
      backgroundMaskEnabled = false,
    )
  }

  /**
   * Signature Preset 4: HANDCRAFTED FRAME
   * CONDENSED_EDITORIAL white italic serif baseline interrupted on accent words by HAND_BOIL_15FPS
   * marker lettering, alternating DASHED_SELECTION_BOX frames & PAPER_CUTOUT_PILL badges, and
   * self-drawing SWOOSH_UNDERLINE / HAND_CIRCLE ink flourishes.
   */
  private fun composeHandcraftedFrame(
    id: String,
    startMs: Long,
    endMs: Long,
    rawSentence: String,
    words: List<WordTiming>,
    phraseIndex: Int,
    preset: PresetDefinition,
    serifFont: String,
    markerFont: String,
    defaultRegion: SafeRegion,
    customRegionCoords: Pair<Float, Float>?,
    shot: com.example.captionstudio.domain.model.ShotVisualAnalysis?,
  ): CaptionComposition {
    val scores = words.mapIndexed { idx, wt -> idx to scoreWordSalience(wt.word, idx, words.size) }
    val accentIdx = scores.maxByOrNull { it.second }?.first ?: words.lastIndex

    // Alternate DASHED_SELECTION_BOX + SWOOSH_UNDERLINE with PAPER_CUTOUT_PILL + HAND_CIRCLE
    val usePaperCutout = phraseIndex % 2 != 0
    val accentFrame =
      if (usePaperCutout) DesignerFrameStyle.PAPER_CUTOUT_PILL
      else DesignerFrameStyle.DASHED_SELECTION_BOX
    val accentFlourish =
      if (usePaperCutout) VectorFlourish.HAND_CIRCLE
      else VectorFlourish.SWOOSH_UNDERLINE
    val accentColor =
      if (usePaperCutout) "#17181C" else preset.expressiveColorHex
    val accentTilt = if (phraseIndex % 2 == 0) -2.2f else 2.0f

    val elements = mutableListOf<CompositionElement>()
    var rowGroup = 0
    var idx = 0
    while (idx < words.size) {
      if (idx == accentIdx) {
        elements.add(
          CompositionElement(
            id = UUID.randomUUID().toString(),
            text = words[idx].word.trim('.', ',', ';', ':'),
            role = FontRole.EXPRESSIVE,
            hierarchyLevel = HierarchyLevel.EXPRESSIVE,
            fontFamilyId = markerFont,
            fontWeight = 700,
            isItalic = false,
            fontSizeSp = (34f * preset.expressiveScaleMultiplier).coerceIn(26f, 46f),
            colorHex = accentColor,
            alignment = TextAlignment.CENTER,
            uppercase = false,
            staggerDelayMs = if (idx > 0) 65 else 0,
            wordIndices = listOf(idx),
            inlineRowGroup = rowGroup++,
            shadowEnabled = !usePaperCutout,
            shadowIntensity = 0.85f,
            depthLayer = DepthLayer.IN_FRONT,
            letterAnimation = LetterAnimationStyle.HAND_BOIL_15FPS,
            surfaceStyle = TextSurfaceStyle.STANDARD,
            tiltDegrees = accentTilt,
            designerFrame = accentFrame,
            flourish = accentFlourish,
          )
        )
        idx++
      } else {
        val span = mutableListOf<Int>()
        while (idx < words.size && idx != accentIdx) {
          span.add(idx)
          idx++
          if (span.size >= 4) break
        }
        elements.add(
          CompositionElement(
            id = UUID.randomUUID().toString(),
            text = span.joinToString(" ") { words[it].word },
            role = FontRole.SUPPORTING,
            hierarchyLevel = HierarchyLevel.SUPPORTING,
            fontFamilyId = serifFont,
            fontWeight = 600,
            isItalic = true,
            fontSizeSp = 22f,
            colorHex = StudioPalette.WHITE,
            alignment = TextAlignment.CENTER,
            uppercase = false,
            letterSpacingEm = -0.01f,
            wordIndices = span,
            inlineRowGroup = rowGroup++,
            shadowEnabled = true,
            shadowIntensity = 0.9f,
            depthLayer = DepthLayer.IN_FRONT,
            letterAnimation = LetterAnimationStyle.NONE,
            surfaceStyle = TextSurfaceStyle.CONDENSED_EDITORIAL,
            tiltDegrees = 0f,
            designerFrame = DesignerFrameStyle.NONE,
            flourish = VectorFlourish.NONE,
          )
        )
      }
    }

    val region =
      if (customRegionCoords != null) SafeRegion.CUSTOM
      else defaultRegion
    val zoneMetrics = shot?.zoneMetrics?.get(region)
    val guardedElements =
      if (zoneMetrics != null) {
        elements.map { el ->
          // Never cover a custom designer frame with an auto-readability pill
          if (el.designerFrame != DesignerFrameStyle.NONE) el
          else applyAutoReadabilityGuard(listOf(el), zoneMetrics).first()
        }
      } else {
        elements
      }

    return CaptionComposition(
      id = id,
      startMs = startMs,
      endMs = endMs,
      originalText = rawSentence,
      region = region,
      anchorXFraction = customRegionCoords?.first ?: region.defaultAnchorX,
      anchorYFraction = customRegionCoords?.second ?: region.defaultAnchorY,
      entryAnimation = preset.defaultEntryAnim,
      exitAnimation = preset.defaultExitAnim,
      easing = preset.defaultEasing,
      entryDurationMs = preset.entryDurationMs,
      exitDurationMs = preset.exitDurationMs,
      elements = guardedElements,
      wordMappings = words,
      depthLayer = DepthLayer.IN_FRONT,
      styleVariant = CompositionStyleVariant.HERO_STACK,
      spatialMode = SpatialLayoutMode.STANDARD,
      backgroundMaskEnabled = false,
    )
  }

  /**
   * Batch Hook & B-Roll Pacing Templates:
   * Automatically styles opening hook captions (startMs < 3000ms, or the opening caption if all
   * start later) with high-impact typography punch, while pacing subsequent body captions cleanly
   * for effortless B-roll and talking-head readability.
   */
  fun applyHookPacingTemplate(
    compositions: List<CaptionComposition>,
    template: HookPacingTemplate,
    activePreset: PresetDefinition,
    analysisReport: VideoAnalysisReport,
    fontOverrides: FontRoleOverrides = FontRoleOverrides(),
  ): HookPacingResult {
    if (compositions.isEmpty()) return HookPacingResult(emptyList(), 0, 0)
    if (template == HookPacingTemplate.UNIFORM_RESET) {
      val uniform =
        PresetCatalog.applyPresetToCompositions(
          preset = activePreset,
          compositions = compositions,
          analysisReport = analysisReport,
          fontOverrides = fontOverrides,
        )
      return HookPacingResult(uniform, hookCount = 0, bodyCount = uniform.size)
    }

    val hookPreset =
      when (template) {
        HookPacingTemplate.VIRAL_HOOK_KARAOKE_BODY -> PresetCatalog.findById("impact")
        HookPacingTemplate.BEHIND_HOOK_SUBTITLE_BODY -> PresetCatalog.findById("kinetic_street_poster")
        HookPacingTemplate.IMPACT_HOOK_PILL_BODY -> PresetCatalog.findById("kinetic_hype_drop")
        HookPacingTemplate.CINEMATIC_HOOK_MINIMAL -> PresetCatalog.findById("kinetic_vogue_luxury")
        HookPacingTemplate.UNIFORM_RESET -> activePreset
      }
    val bodyPreset =
      when (template) {
        HookPacingTemplate.VIRAL_HOOK_KARAOKE_BODY -> PresetCatalog.findById("normal_tiktok_native")
        HookPacingTemplate.BEHIND_HOOK_SUBTITLE_BODY -> PresetCatalog.findById("normal_classic_white")
        HookPacingTemplate.IMPACT_HOOK_PILL_BODY -> PresetCatalog.findById("normal_pill_sweep")
        HookPacingTemplate.CINEMATIC_HOOK_MINIMAL -> PresetCatalog.findById("normal_netflix_cinema")
        HookPacingTemplate.UNIFORM_RESET -> activePreset
      }

    val hasAnyUnderWindow = compositions.any { it.startMs < template.hookWindowMs }
    var hookCount = 0
    var bodyCount = 0

    val result =
      compositions.mapIndexed { idx, comp ->
        val isHook = if (hasAnyUnderWindow) comp.startMs < template.hookWindowMs else idx == 0
        if (isHook) {
          hookCount++
          val hookStyled =
            PresetCatalog.applyPresetToCompositions(
                preset = hookPreset,
                compositions = listOf(comp),
                analysisReport = analysisReport,
                fontOverrides = fontOverrides,
              )
              .first()
          val enableBehind =
            template == HookPacingTemplate.BEHIND_HOOK_SUBTITLE_BODY ||
              (template == HookPacingTemplate.VIRAL_HOOK_KARAOKE_BODY &&
                analysisReport.subjectCoverageFraction > 0.08f)
          val punchedEls =
            hookStyled.elements.map { el ->
              val isDom = el.hierarchyLevel == HierarchyLevel.DOMINANT
              el.copy(
                fontSizeSp = (el.fontSizeSp * if (isDom) 1.12f else 1.06f).coerceIn(16f, 70f),
                motionEffect =
                  if (isDom && template != HookPacingTemplate.CINEMATIC_HOOK_MINIMAL) {
                    LayerMotionEffect.POP_IN
                  } else {
                    el.motionEffect
                  },
                depthLayer = if (enableBehind && isDom) DepthLayer.BEHIND_SUBJECT else el.depthLayer,
              )
            }
          hookStyled.copy(
            entryAnimation =
              if (template == HookPacingTemplate.CINEMATIC_HOOK_MINIMAL) EntryAnimationType.SLIDE_UP
              else EntryAnimationType.FADE_POP,
            exitAnimation = ExitAnimationType.SCALE_OUT,
            easing = AnimationEasingType.SPRING_OVERSHOOT,
            entryDurationMs = if (template == HookPacingTemplate.CINEMATIC_HOOK_MINIMAL) 190 else 115,
            depthLayer = if (enableBehind) DepthLayer.BEHIND_SUBJECT else hookStyled.depthLayer,
            backgroundMaskEnabled = enableBehind || hookStyled.backgroundMaskEnabled,
            elements = punchedEls,
          )
        } else {
          bodyCount++
          PresetCatalog.applyPresetToCompositions(
              preset = bodyPreset,
              compositions = listOf(comp),
              analysisReport = analysisReport,
              fontOverrides = fontOverrides,
            )
            .first()
        }
      }

    return HookPacingResult(result, hookCount = hookCount, bodyCount = bodyCount)
  }

  private fun findShotIndexForTime(timeMs: Long, report: VideoAnalysisReport): Int {
    if (report.shotAnalyses.isNotEmpty()) {
      val exact = report.shotAnalyses.indexOfFirst { timeMs in it.startMs..it.endMs }
      if (exact >= 0) return exact
      // Fallback to nearest shot by midpoint
      return report.shotAnalyses.indices.minByOrNull { idx ->
        val s = report.shotAnalyses[idx]
        val mid = if (s.endMs == Long.MAX_VALUE) s.startMs else (s.startMs + s.endMs) / 2L
        kotlin.math.abs(mid - timeMs)
      } ?: 0
    }
    // Derive shot index from sceneChangeTimestampsMs when full shotAnalyses aren't present
    var idx = 0
    for (cutMs in report.sceneChangeTimestampsMs) {
      if (timeMs >= cutMs) idx++
    }
    return idx
  }

  /**
   * Frame-aware, shot-stable region selector:
   * - Never picks regions randomly via phraseIdx % N.
   * - Keeps captions anchored consistently inside the same shot.
   * - Vetoes any zone overlapping the speaker's face/eyes/mouth.
   * - Only uses LEFT_SIDE / RIGHT_SIDE when wide & empty.
   */
  private fun chooseFrameAwareRegion(
    styleVariant: CompositionStyleVariant,
    shot: com.example.captionstudio.domain.model.ShotVisualAnalysis?,
    currentShotAnchor: SafeRegion?,
    rankedRegions: List<SafeRegion>,
    analysisReport: VideoAnalysisReport,
    shotHasSubject: Boolean,
  ): Pair<SafeRegion, SafeRegion> {
    if (styleVariant == CompositionStyleVariant.SEAM_CALLOUT) {
      return SafeRegion.SEAM_CENTER to (currentShotAnchor ?: SafeRegion.SEAM_CENTER)
    }

    // Determine the shot's clean, stable anchor zone once per shot
    val baseShotZone =
      currentShotAnchor ?: computeCleanShotAnchor(shot, rankedRegions, analysisReport)

    if (shot == null) {
      // No frame metrics available yet (e.g., script-only project): stay cleanly anchored on baseShotZone!
      return when (styleVariant) {
        CompositionStyleVariant.LEFT_ANCHOR_STACK -> SafeRegion.LEFT_SIDE to baseShotZone
        else -> baseShotZone to baseShotZone
      }
    }

    val zones = shot.zoneMetrics
    val currentBlockedByFace = zones[baseShotZone]?.isBlockedByFace == true
    val safeAnchor =
      if (currentBlockedByFace) {
        computeCleanShotAnchor(shot, rankedRegions, analysisReport)
      } else {
        baseShotZone
      }

    // Deliberate behind-subject hero callouts may step cleanly into unblocked UPPER_CENTER headroom
    if (styleVariant == CompositionStyleVariant.HERO_STACK && shotHasSubject) {
      val upperMetrics = zones[SafeRegion.UPPER_CENTER]
      if (upperMetrics != null && !upperMetrics.isBlockedByFace && upperMetrics.edgeClutter < 28f) {
        return SafeRegion.UPPER_CENTER to safeAnchor
      }
    }

    // Left staircase only uses LEFT_SIDE if left margin is actually wide & empty and not face-blocked
    if (styleVariant == CompositionStyleVariant.LEFT_ANCHOR_STACK) {
      val leftMetrics = zones[SafeRegion.LEFT_SIDE]
      if (leftMetrics != null && !leftMetrics.isBlockedByFace && leftMetrics.isWideAndEmpty) {
        return SafeRegion.LEFT_SIDE to safeAnchor
      }
    }

    // Stay rock-steady on the shot's clean anchor!
    return safeAnchor to safeAnchor
  }

  private fun computeCleanShotAnchor(
    shot: com.example.captionstudio.domain.model.ShotVisualAnalysis?,
    rankedRegions: List<SafeRegion>,
    analysisReport: VideoAnalysisReport,
  ): SafeRegion {
    if (shot != null && shot.zoneMetrics.isNotEmpty()) {
      return com.example.captionstudio.media.VideoAnalysisService.selectBestZoneForShot(
        zoneMetrics = shot.zoneMetrics,
        isSplitScreen = shot.isSplitScreen,
        subjectCoverage = shot.subjectCoverage,
      )
    }
    // Fallback when no frame-level shot metrics exist: pick cleanest non-face-blocking vertical anchor
    val primaryFace = analysisReport.faceRegions.firstOrNull()
    if (primaryFace != null) {
      return if (primaryFace.bottom < 0.65f) SafeRegion.LOWER_CENTER
      else if (primaryFace.top > 0.34f) SafeRegion.UPPER_CENTER
      else SafeRegion.LOWER_CENTER
    }
    return rankedRegions.firstOrNull {
      it == SafeRegion.LOWER_CENTER || it == SafeRegion.UPPER_CENTER || it == SafeRegion.CENTER
    } ?: SafeRegion.LOWER_CENTER
  }

  /**
   * Auto-Readability Guard:
   * Inspects the measured luminance and edge clutter behind the chosen placement zone.
   * When bright (>165) or visually busy (>22), automatically boosts shadow depth, adds a crisp
   * dark outline stroke, and adds a subtle translucent dark pill when foreground legibility needs it.
   */
  internal fun applyAutoReadabilityGuard(
    elements: List<CompositionElement>,
    zoneMetrics: com.example.captionstudio.domain.model.ZoneVisualMetrics?,
  ): List<CompositionElement> {
    if (zoneMetrics == null) return elements
    val isBright = zoneMetrics.meanLuminance >= 165f
    val isVeryBright = zoneMetrics.meanLuminance >= 195f
    val isBusy = zoneMetrics.edgeClutter >= 22f
    if (!isBright && !isBusy) return elements

    return elements.map { el ->
      val boostedShadow = max(el.shadowIntensity, if (isVeryBright || isBusy) 1.65f else 1.40f)
      val needsStroke = el.strokeWidthFactor < 1.6f
      val strokeWidth =
        if (needsStroke) {
          if (el.hierarchyLevel == HierarchyLevel.DOMINANT) 2.0f else 1.6f
        } else {
          el.strokeWidthFactor
        }
      val needsPill =
        el.depthLayer == DepthLayer.IN_FRONT &&
          el.backgroundColorHex == null &&
          (isVeryBright || (isBright && isBusy))

      el.copy(
        shadowEnabled = true,
        shadowIntensity = boostedShadow,
        strokeWidthFactor = strokeWidth,
        strokeColorHex = if (needsStroke) "#101016" else el.strokeColorHex,
        backgroundColorHex = if (needsPill) "#121218" else el.backgroundColorHex,
        backgroundAlpha = if (needsPill) 0.62f else el.backgroundAlpha,
        backgroundCornerRadiusDp = if (needsPill) 10f else el.backgroundCornerRadiusDp,
      )
    }
  }

  private fun segmentIntoNaturalPhrases(words: List<WordTiming>): List<List<WordTiming>> {
    val result = mutableListOf<List<WordTiming>>()
    val current = mutableListOf<WordTiming>()

    for (i in words.indices) {
      val wt = words[i]
      current.add(wt)
      val w = wt.word
      val endsSentence = w.endsWith(".") || w.endsWith("!") || w.endsWith("?")
      val hasComma = w.endsWith(",") || w.endsWith(";") || w.endsWith(":")
      val nextGap =
        if (i + 1 < words.size) words[i + 1].startMs - wt.endMs else 0L

      val shouldSplit =
        endsSentence ||
          nextGap > 320L ||
          (hasComma && current.size >= 2) ||
          current.size >= 5 ||
          (current.size >= 3 && cleanToken(w) in dramaticWords)

      if (shouldSplit) {
        result.add(current.toList())
        current.clear()
      }
    }
    if (current.isNotEmpty()) {
      if (current.size == 1 && result.isNotEmpty() && result.last().size <= 4) {
        val prev = result.removeAt(result.lastIndex)
        result.add(prev + current)
      } else {
        result.add(current.toList())
      }
    }
    return result
  }

  /**
   * Frames 05, 06, 07, 08, 10, 14, 16: Minimal crisp sans floating in quiet negative space
   * with subtle final-word weight punch ("and" smaller -> "keeps" bolder).
   */
  private fun composeWhisperBeatElements(
    words: List<WordTiming>,
    supportingFont: String,
    heavyFont: String,
    preset: PresetDefinition,
    alignment: TextAlignment,
  ): List<CompositionElement> {
    if (words.size == 2) {
      return listOf(
        CompositionElement(
          id = UUID.randomUUID().toString(),
          text = words[0].word,
          role = FontRole.SUPPORTING,
          hierarchyLevel = HierarchyLevel.SECONDARY,
          fontFamilyId = supportingFont,
          fontWeight = 500,
          fontSizeSp = 15.5f,
          colorHex = StudioPalette.WHITE,
          alignment = alignment,
          wordIndices = listOf(0),
          inlineRowGroup = 0,
          offsetYEm = -0.10f,
          depthLayer = DepthLayer.IN_FRONT,
        ),
        CompositionElement(
          id = UUID.randomUUID().toString(),
          text = words[1].word,
          role = FontRole.SUPPORTING,
          hierarchyLevel = HierarchyLevel.SUPPORTING,
          fontFamilyId = supportingFont,
          fontWeight = 700,
          fontSizeSp = 21f,
          colorHex = preset.primaryColorHex,
          alignment = alignment,
          staggerDelayMs = 90,
          wordIndices = listOf(1),
          inlineRowGroup = 0,
          depthLayer = DepthLayer.IN_FRONT,
        ),
      )
    }

    val lastClean = cleanToken(words.last().word)
    val highlightLast = lastClean !in stopWords && words.size >= 2
    return if (highlightLast && words.size == 3) {
      listOf(
        CompositionElement(
          id = UUID.randomUUID().toString(),
          text = "${words[0].word} ${words[1].word}",
          role = FontRole.SUPPORTING,
          hierarchyLevel = HierarchyLevel.SECONDARY,
          fontFamilyId = supportingFont,
          fontWeight = 500,
          fontSizeSp = 16f,
          colorHex = StudioPalette.WHITE,
          alignment = alignment,
          wordIndices = listOf(0, 1),
          inlineRowGroup = 0,
          depthLayer = DepthLayer.IN_FRONT,
        ),
        CompositionElement(
          id = UUID.randomUUID().toString(),
          text = words[2].word,
          role = FontRole.HEAVY,
          hierarchyLevel = HierarchyLevel.SUPPORTING,
          fontFamilyId = heavyFont,
          fontWeight = 700,
          fontSizeSp = 24f,
          colorHex = preset.primaryColorHex,
          alignment = alignment,
          uppercase = false,
          staggerDelayMs = 110,
          wordIndices = listOf(2),
          inlineRowGroup = 0,
          depthLayer = DepthLayer.IN_FRONT,
        ),
      )
    } else {
      listOf(
        CompositionElement(
          id = UUID.randomUUID().toString(),
          text = words.joinToString(" ") { it.word },
          role = FontRole.SUPPORTING,
          hierarchyLevel = HierarchyLevel.SUPPORTING,
          fontFamilyId = supportingFont,
          fontWeight = 600,
          fontSizeSp = 18.5f,
          colorHex = StudioPalette.WHITE,
          alignment = alignment,
          wordIndices = words.indices.toList(),
          inlineRowGroup = 0,
          depthLayer = DepthLayer.IN_FRONT,
        )
      )
    }
  }

  /**
   * Frame 04 left stack: Tightly stacked vertical lines beside face ("no" / "it's not" / "just" / "because").
   */
  private fun composeLeftStaircaseElements(
    words: List<WordTiming>,
    heavyFont: String,
    supportingFont: String,
    preset: PresetDefinition,
  ): List<CompositionElement> {
    val elements = mutableListOf<CompositionElement>()
    words.forEachIndexed { idx, wt ->
      val isFirstAnchor = idx == 0
      elements.add(
        CompositionElement(
          id = UUID.randomUUID().toString(),
          text = wt.word.trim(','),
          role = if (isFirstAnchor) FontRole.HEAVY else FontRole.SUPPORTING,
          hierarchyLevel = if (isFirstAnchor) HierarchyLevel.DOMINANT else HierarchyLevel.SUPPORTING,
          fontFamilyId = if (isFirstAnchor) heavyFont else supportingFont,
          fontWeight = if (isFirstAnchor) 900 else 700,
          fontSizeSp = if (isFirstAnchor) 44f * preset.dominantScaleMultiplier else 19f,
          colorHex = StudioPalette.WHITE,
          alignment = TextAlignment.LEFT,
          uppercase = false,
          staggerDelayMs = idx * 80,
          wordIndices = listOf(idx),
          inlineRowGroup = idx,
          offsetYEm = if (idx > 0) -0.14f else 0f,
          depthLayer = DepthLayer.IN_FRONT,
        )
      )
    }
    return elements
  }

  /**
   * Frames 17–19: Compact horizontal UI callout at horizontal split seam with warm gold keyword highlight.
   */
  private fun composeSeamCalloutElements(
    words: List<WordTiming>,
    supportingFont: String,
    preset: PresetDefinition,
  ): List<CompositionElement> {
    if (words.size <= 1) {
      return listOf(
        CompositionElement(
          id = UUID.randomUUID().toString(),
          text = words.first().word,
          role = FontRole.SUPPORTING,
          hierarchyLevel = HierarchyLevel.SUPPORTING,
          fontFamilyId = supportingFont,
          fontWeight = 700,
          fontSizeSp = 21f,
          colorHex = StudioPalette.GOLD_AMBER,
          glowColorHex = StudioPalette.GOLD_AMBER,
          alignment = TextAlignment.CENTER,
          wordIndices = listOf(0),
          inlineRowGroup = 0,
        )
      )
    }
    val highlightIdx =
      words.indices.maxByOrNull { scoreWordSalience(words[it].word, it, words.size) }
        ?: words.lastIndex
    val elements = mutableListOf<CompositionElement>()
    if (highlightIdx > 0) {
      elements.add(
        CompositionElement(
          id = UUID.randomUUID().toString(),
          text = words.subList(0, highlightIdx).joinToString(" ") { it.word },
          role = FontRole.SUPPORTING,
          hierarchyLevel = HierarchyLevel.SUPPORTING,
          fontFamilyId = supportingFont,
          fontWeight = 600,
          fontSizeSp = 18.5f,
          colorHex = StudioPalette.WHITE,
          alignment = TextAlignment.CENTER,
          wordIndices = (0 until highlightIdx).toList(),
          inlineRowGroup = 0,
        )
      )
    }
    val tailRange = highlightIdx until words.size
    elements.add(
      CompositionElement(
        id = UUID.randomUUID().toString(),
        text = tailRange.joinToString(" ") { words[it].word },
        role = FontRole.SUPPORTING,
        hierarchyLevel = HierarchyLevel.SUPPORTING,
        fontFamilyId = supportingFont,
        fontWeight = 800,
        fontSizeSp = 20f,
        colorHex = preset.dominantColorHex.let { if (it == StudioPalette.WHITE) StudioPalette.GOLD_AMBER else it },
        glowColorHex = StudioPalette.GOLD_AMBER,
        alignment = TextAlignment.CENTER,
        staggerDelayMs = 70,
        wordIndices = tailRange.toList(),
        inlineRowGroup = 0,
      )
    )
    return elements
  }

  /**
   * Frames 04 bottom ("LOOKS GOOD") & 20 ("link in bio"): Glowing golden anchor with clean contrast.
   */
  private fun composeGlowBannerElements(
    words: List<WordTiming>,
    heavyFont: String,
    supportingFont: String,
    preset: PresetDefinition,
    behindSubject: Boolean,
  ): List<CompositionElement> {
    val phrase = words.joinToString(" ") { it.word }
    val isLinkInBio = phrase.contains("link in bio", ignoreCase = true)
    if (isLinkInBio && words.size > 3) {
      val splitAt = words.size - 3
      return listOf(
        CompositionElement(
          id = UUID.randomUUID().toString(),
          text = words.subList(0, splitAt).joinToString(" ") { it.word },
          role = FontRole.SUPPORTING,
          hierarchyLevel = HierarchyLevel.SUPPORTING,
          fontFamilyId = supportingFont,
          fontWeight = 600,
          fontSizeSp = 17.5f,
          colorHex = StudioPalette.WHITE,
          alignment = TextAlignment.CENTER,
          wordIndices = (0 until splitAt).toList(),
          inlineRowGroup = 0,
          depthLayer = DepthLayer.IN_FRONT,
        ),
        CompositionElement(
          id = UUID.randomUUID().toString(),
          text = words.subList(splitAt, words.size).joinToString(" ") { it.word },
          role = FontRole.SUPPORTING,
          hierarchyLevel = HierarchyLevel.SUPPORTING,
          fontFamilyId = supportingFont,
          fontWeight = 800,
          fontSizeSp = 18.5f,
          colorHex = StudioPalette.GOLD_AMBER,
          glowColorHex = StudioPalette.GOLD_AMBER,
          alignment = TextAlignment.CENTER,
          staggerDelayMs = 90,
          wordIndices = (splitAt until words.size).toList(),
          inlineRowGroup = 0,
          depthLayer = DepthLayer.IN_FRONT,
        ),
      )
    }

    return listOf(
      CompositionElement(
        id = UUID.randomUUID().toString(),
        text = phrase,
        role = FontRole.HEAVY,
        hierarchyLevel = HierarchyLevel.DOMINANT,
        fontFamilyId = heavyFont,
        fontWeight = 900,
        fontSizeSp = 54f * preset.dominantScaleMultiplier,
        colorHex = StudioPalette.GOLD_AMBER,
        glowColorHex = StudioPalette.GOLD_AMBER,
        alignment = TextAlignment.CENTER,
        uppercase = true,
        letterSpacingEm = -0.02f,
        wordIndices = words.indices.toList(),
        inlineRowGroup = 0,
        depthLayer = if (behindSubject) DepthLayer.BEHIND_SUBJECT else DepthLayer.IN_FRONT,
      )
    )
  }

  /**
   * Frames 02, 03, 09, 11, 15: Multi-tier editorial composition pairing crisp clean sans,
   * massive condensed display typography (behind subject when detected), and warm gold
   * handwritten script accents tucked neatly against the headline.
   */
  private fun composeHeroStackElements(
    words: List<WordTiming>,
    phraseIndex: Int,
    preset: PresetDefinition,
    heavyFont: String,
    supportingFont: String,
    expressiveFont: String,
    alignment: TextAlignment,
    behindSubject: Boolean,
  ): List<CompositionElement> {
    if (words.size == 1) {
      val clean = cleanToken(words.first().word)
      val isDramatic = clean in dramaticWords
      return listOf(
        CompositionElement(
          id = UUID.randomUUID().toString(),
          text = words.first().word,
          role = FontRole.HEAVY,
          hierarchyLevel = HierarchyLevel.DOMINANT,
          fontFamilyId = heavyFont,
          fontWeight = 900,
          fontSizeSp = 64f * preset.dominantScaleMultiplier,
          colorHex = if (isDramatic) preset.dramaticColorHex else StudioPalette.GOLD_AMBER,
          glowColorHex = if (!isDramatic) StudioPalette.GOLD_AMBER else null,
          alignment = alignment,
          uppercase = true,
          letterSpacingEm = -0.02f,
          wordIndices = listOf(0),
          inlineRowGroup = 0,
          depthLayer = if (behindSubject) DepthLayer.BEHIND_SUBJECT else DepthLayer.IN_FRONT,
        )
      )
    }

    val scores =
      words.mapIndexed { idx, wt -> idx to scoreWordSalience(wt.word, idx, words.size) }
    val dominantIndex = scores.maxByOrNull { it.second }?.first ?: (words.size - 1)

    // Pick at most one Expressive handwritten accent word/pair (distinct from dominantIndex)
    val expressiveIndex =
      words.indices
        .filter { it != dominantIndex }
        .firstOrNull { idx ->
          val clean = cleanToken(words[idx].word)
          clean in expressiveWords ||
            (clean.length >= 4 && clean !in stopWords && idx > dominantIndex)
        }
        ?: if (words.size >= 4 && dominantIndex > 1 && phraseIndex % 2 == 0) {
          (dominantIndex - 1).takeIf { cleanToken(words[it].word) !in stopWords }
        } else {
          null
        }

    val elements = mutableListOf<CompositionElement>()
    var rowGroup = 0
    var idx = 0
    while (idx < words.size) {
      when (idx) {
        dominantIndex -> {
          val cleanWord = words[idx].word.trim('.', ',', ';', ':')
          val isDramatic =
            cleanToken(cleanWord) in dramaticWords ||
              cleanWord.endsWith("!") ||
              cleanToken(cleanWord) == "big"
          val domColor =
            when {
              isDramatic -> preset.dramaticColorHex
              phraseIndex % 3 == 1 -> StudioPalette.GOLD_AMBER
              else -> preset.dominantColorHex
            }
          elements.add(
            CompositionElement(
              id = UUID.randomUUID().toString(),
              text = cleanWord,
              role = FontRole.HEAVY,
              hierarchyLevel = HierarchyLevel.DOMINANT,
              fontFamilyId = heavyFont,
              fontWeight = 900,
              isItalic = false,
              fontSizeSp = 60f * preset.dominantScaleMultiplier,
              colorHex = domColor,
              glowColorHex = if (domColor == StudioPalette.GOLD_AMBER) StudioPalette.GOLD_AMBER else null,
              alignment = alignment,
              uppercase = true,
              letterSpacingEm = if (preset.id == "cinematic") 0.08f else -0.02f,
              staggerDelayMs = 0,
              wordIndices = listOf(idx),
              inlineRowGroup = rowGroup++,
              depthLayer = if (behindSubject) DepthLayer.BEHIND_SUBJECT else DepthLayer.IN_FRONT,
            )
          )
          idx++
        }

        expressiveIndex -> {
          // Group two adjacent expressive words ("crazy text") together cleanly if next word is "text"/"captions"
          val pairNext =
            idx + 1 < words.size &&
              idx + 1 != dominantIndex &&
              cleanToken(words[idx + 1].word) in setOf("text", "captions", "style", "tools")
          val expIndices = if (pairNext) listOf(idx, idx + 1) else listOf(idx)
          val expText = expIndices.joinToString(" ") { words[it].word }
          elements.add(
            CompositionElement(
              id = UUID.randomUUID().toString(),
              text = expText,
              role = FontRole.EXPRESSIVE,
              hierarchyLevel = HierarchyLevel.EXPRESSIVE,
              fontFamilyId = expressiveFont,
              fontWeight = 600,
              isItalic = true,
              fontSizeSp = 36f * preset.expressiveScaleMultiplier,
              colorHex = preset.expressiveColorHex,
              glowColorHex = preset.expressiveColorHex,
              alignment = alignment,
              uppercase = false,
              staggerDelayMs = 55,
              wordIndices = expIndices,
              inlineRowGroup = rowGroup++,
              rotationDeg = -2.4f,
              offsetYEm = -0.22f,
              depthLayer = DepthLayer.IN_FRONT,
            )
          )
          idx += expIndices.size
        }

        else -> {
          val spanIndices = mutableListOf<Int>()
          while (idx < words.size && idx != dominantIndex && idx != expressiveIndex) {
            spanIndices.add(idx)
            idx++
            if (spanIndices.size >= 4) break
          }
          val spanText = spanIndices.joinToString(" ") { words[it].word }
          val isSecondaryTail =
            spanIndices.first() > dominantIndex && spanIndices.size >= 2 && elements.isNotEmpty()
          elements.add(
            CompositionElement(
              id = UUID.randomUUID().toString(),
              text = spanText,
              role = FontRole.SUPPORTING,
              hierarchyLevel =
                if (isSecondaryTail) HierarchyLevel.SECONDARY else HierarchyLevel.SUPPORTING,
              fontFamilyId = supportingFont,
              fontWeight = if (isSecondaryTail) 500 else 700,
              isItalic = false,
              fontSizeSp = if (isSecondaryTail) 16.5f else 19.5f,
              colorHex = preset.primaryColorHex,
              alignment = alignment,
              uppercase = false,
              staggerDelayMs = 45,
              wordIndices = spanIndices,
              inlineRowGroup = rowGroup++,
              depthLayer = DepthLayer.IN_FRONT,
            )
          )
        }
      }
    }
    return elements
  }

  internal fun scoreWordSalience(word: String, index: Int, total: Int): Float {
    val clean = cleanToken(word)
    if (clean.isEmpty()) return 0f
    var score = 1f
    if (clean in stopWords) score -= 1.2f else score += 1.4f
    if (clean in dramaticWords) score += 3.4f
    if (clean in expressiveWords) score += 0.5f
    if (clean.endsWith("ly") && clean.length >= 6) score -= 0.7f
    else if (clean.length >= 6 && clean !in expressiveWords) score += 1.9f
    if (word.any { it.isUpperCase() } && index > 0) score += 1.2f
    if (word.endsWith("!") || word.endsWith("?")) score += 1.0f
    if (index == total - 1 && clean == "this") {
      score += 3.5f
    } else if (index == total - 1 && clean !in stopWords && clean.length >= 4) {
      score += 1.5f
    }
    if (index == 0 && clean == "no") score += 3.0f
    return score
  }

  private fun cleanToken(token: String): String {
    return token.lowercase().trim('.', ',', '!', '?', ';', ':', '"', '\'', '(', ')')
  }
}
