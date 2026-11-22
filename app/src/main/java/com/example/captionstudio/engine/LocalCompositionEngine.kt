package com.example.captionstudio.engine

import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.CompositionElement
import com.example.captionstudio.domain.model.CompositionStyleVariant
import com.example.captionstudio.domain.model.DepthLayer
import com.example.captionstudio.domain.model.FontRole
import com.example.captionstudio.domain.model.FontRoleOverrides
import com.example.captionstudio.domain.model.HierarchyLevel
import com.example.captionstudio.domain.model.PresetDefinition
import com.example.captionstudio.domain.model.SafeRegion
import com.example.captionstudio.domain.model.StudioPalette
import com.example.captionstudio.domain.model.TextAlignment
import com.example.captionstudio.domain.model.VideoAnalysisReport
import com.example.captionstudio.domain.model.WordTiming
import java.util.UUID
import kotlin.math.max

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
        .ifEmpty { listOf(SafeRegion.UPPER_CENTER, SafeRegion.LOWER_CENTER) }

    val heavyFont = fontOverrides.heavyFontId ?: preset.heavyFontId
    val supportingFont = fontOverrides.supportingFontId ?: preset.supportingFontId
    val expressiveFont = fontOverrides.expressiveFontId ?: preset.expressiveFontId

    val hasSubject = analysisReport.subjectCoverageFraction > 0.08f || analysisReport.faceRegions.isNotEmpty()

    return nonEmptyPhrases.mapIndexed { phraseIdx, phraseWords ->
      val startMs = phraseWords.first().startMs
      val endMs = max(phraseWords.last().endMs, startMs + 480L)
      val rawSentence = phraseWords.joinToString(" ") { it.word }
      val lowerPhrase = phraseWords.joinToString(" ") { cleanToken(it.word) }.trim()
      val maxSalience =
        phraseWords.indices.maxOfOrNull { idx ->
          scoreWordSalience(phraseWords[idx].word, idx, phraseWords.size)
        } ?: 0f

      val stopCount = phraseWords.count { cleanToken(it.word) in stopWords }
      val isWhisperBeat =
        phraseWords.size in 2..3 &&
          lowerPhrase !in actionCallouts &&
          phraseWords.none { cleanToken(it.word) in dramaticWords } &&
          (phraseWords.size == 2 || stopCount >= 2 || maxSalience < 3.6f || phraseIdx % 4 == 2)

      val isActionGlow = lowerPhrase in actionCallouts || lowerPhrase.contains("link in bio")
      val isNegationStack =
        cleanToken(phraseWords.first().word) == "no" && phraseWords.size >= 3
      val isSeamCallout =
        (analysisReport.isSplitScreenDetected && phraseIdx % 3 != 0) ||
          (phraseWords.size in 2..4 && cleanToken(phraseWords.first().word) in seamTriggerWords)

      val styleVariant =
        when {
          isSeamCallout -> CompositionStyleVariant.SEAM_CALLOUT
          isActionGlow -> CompositionStyleVariant.GLOW_BANNER
          isNegationStack -> CompositionStyleVariant.LEFT_ANCHOR_STACK
          isWhisperBeat -> CompositionStyleVariant.WHISPER_NEGATIVE_SPACE
          else -> CompositionStyleVariant.HERO_STACK
        }

      val region =
        chooseRegionForVariant(
          styleVariant = styleVariant,
          phraseIdx = phraseIdx,
          rankedRegions = regions,
          analysisReport = analysisReport,
        )

      val useBehindSubject =
        hasSubject &&
          (styleVariant == CompositionStyleVariant.HERO_STACK ||
            (styleVariant == CompositionStyleVariant.GLOW_BANNER && region == SafeRegion.UPPER_CENTER))

      val alignment =
        when (region) {
          SafeRegion.UPPER_LEFT,
          SafeRegion.LEFT_SIDE -> TextAlignment.LEFT
          SafeRegion.UPPER_RIGHT,
          SafeRegion.RIGHT_SIDE -> TextAlignment.RIGHT
          else -> TextAlignment.CENTER
        }

      val elements =
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
        }

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

  private fun chooseRegionForVariant(
    styleVariant: CompositionStyleVariant,
    phraseIdx: Int,
    rankedRegions: List<SafeRegion>,
    analysisReport: VideoAnalysisReport,
  ): SafeRegion {
    return when (styleVariant) {
      CompositionStyleVariant.SEAM_CALLOUT -> SafeRegion.SEAM_CENTER
      CompositionStyleVariant.LEFT_ANCHOR_STACK -> SafeRegion.LEFT_SIDE
      CompositionStyleVariant.GLOW_BANNER ->
        if (phraseIdx % 2 == 0) SafeRegion.UPPER_CENTER else SafeRegion.LOWER_CENTER
      CompositionStyleVariant.WHISPER_NEGATIVE_SPACE -> {
        val quiet =
          rankedRegions.filter {
            it == SafeRegion.RIGHT_SIDE ||
              it == SafeRegion.UPPER_RIGHT ||
              it == SafeRegion.LOWER_CENTER ||
              it == SafeRegion.CENTER
          }
        if (quiet.isNotEmpty()) quiet[phraseIdx % quiet.size] else SafeRegion.LOWER_CENTER
      }
      CompositionStyleVariant.HERO_STACK,
      CompositionStyleVariant.STANDARD -> {
        // Behind-subject editorial hero stacks look strongest in upper headroom or upper-left
        val primaryFace = analysisReport.faceRegions.firstOrNull()
        if (primaryFace != null && primaryFace.top < 0.22f) {
          listOf(SafeRegion.UPPER_LEFT, SafeRegion.RIGHT_SIDE, SafeRegion.LOWER_CENTER)[phraseIdx % 3]
        } else {
          val preferred =
            listOf(
              SafeRegion.UPPER_CENTER,
              SafeRegion.UPPER_LEFT,
              SafeRegion.UPPER_CENTER,
              SafeRegion.LOWER_CENTER,
            )
          preferred[phraseIdx % preferred.size]
        }
      }
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
