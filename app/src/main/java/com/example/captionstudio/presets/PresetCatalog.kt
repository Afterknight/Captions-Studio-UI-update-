package com.example.captionstudio.presets

import com.example.captionstudio.domain.model.AnimationEasingType
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.EntryAnimationType
import com.example.captionstudio.domain.model.ExitAnimationType
import com.example.captionstudio.domain.model.FontRole
import com.example.captionstudio.domain.model.FontRoleOverrides
import com.example.captionstudio.domain.model.HierarchyLevel
import com.example.captionstudio.domain.model.PresetDefinition
import com.example.captionstudio.domain.model.SafeRegion
import com.example.captionstudio.domain.model.StudioPalette
import com.example.captionstudio.domain.model.VideoAnalysisReport

object PresetCatalog {

  const val DEFAULT_PRESET_ID = "editorial"

  val presets: List<PresetDefinition> =
    listOf(
      PresetDefinition(
        id = "editorial",
        name = "EDITORIAL",
        tagline = "Bebas Neue + Plus Jakarta + Caveat Script",
        description =
          "Signature contact-sheet kinetic balance. Crisp white geometric context, warm gold handwritten script accents, and oversized behind-subject condensed headlines.",
        heavyFontId = "bebas_neue",
        supportingFontId = "plus_jakarta_sans",
        expressiveFontId = "caveat",
        primaryColorHex = StudioPalette.WHITE,
        dominantColorHex = StudioPalette.WHITE,
        expressiveColorHex = StudioPalette.ORANGE,
        dramaticColorHex = StudioPalette.RED,
        defaultEntryAnim = EntryAnimationType.SCALE_RISE,
        defaultExitAnim = ExitAnimationType.FADE_DROP,
        defaultEasing = AnimationEasingType.SPRING_OVERSHOOT,
        entryDurationMs = 155,
        exitDurationMs = 125,
        dominantScaleMultiplier = 1.08f,
        expressiveScaleMultiplier = 1.12f,
        shadowRadiusFactor = 0.95f,
        preferredRegions =
          listOf(
            SafeRegion.UPPER_CENTER,
            SafeRegion.LOWER_CENTER,
            SafeRegion.LEFT_SIDE,
            SafeRegion.UPPER_RIGHT,
            SafeRegion.CENTER,
          ),
        sampleSentence = "Why do many creators add crazy text",
      ),
      PresetDefinition(
        id = "impact",
        name = "IMPACT",
        tagline = "Archivo Black + DM Sans + Bodoni",
        description =
          "High-energy hook typography with massive bold contrast, punchy red and yellow dominant words, and tight 120ms snap-in transitions.",
        heavyFontId = "archivo_black",
        supportingFontId = "dm_sans",
        expressiveFontId = "bodoni_moda",
        primaryColorHex = StudioPalette.WHITE,
        dominantColorHex = StudioPalette.YELLOW,
        expressiveColorHex = StudioPalette.ORANGE,
        dramaticColorHex = StudioPalette.RED,
        defaultEntryAnim = EntryAnimationType.FADE_POP,
        defaultExitAnim = ExitAnimationType.SCALE_OUT,
        defaultEasing = AnimationEasingType.SPRING_OVERSHOOT,
        entryDurationMs = 125,
        exitDurationMs = 105,
        dominantScaleMultiplier = 1.15f,
        expressiveScaleMultiplier = 0.95f,
        shadowRadiusFactor = 1.25f,
        preferredRegions =
          listOf(
            SafeRegion.CENTER,
            SafeRegion.UPPER_CENTER,
            SafeRegion.LOWER_CENTER,
          ),
        sampleSentence = "But here's the big problem",
      ),
      PresetDefinition(
        id = "kinetic",
        name = "KINETIC",
        tagline = "Bebas Neue + Manrope + Pacifico",
        description =
          "Tall condensed motion graphics built for rapid spoken cadence with staggered word-group reveals and asymmetric side/upper anchoring.",
        heavyFontId = "bebas_neue",
        supportingFontId = "manrope",
        expressiveFontId = "pacifico",
        primaryColorHex = StudioPalette.WHITE,
        dominantColorHex = StudioPalette.YELLOW,
        expressiveColorHex = StudioPalette.ORANGE,
        dramaticColorHex = StudioPalette.RED,
        defaultEntryAnim = EntryAnimationType.STAGGER_REVEAL,
        defaultExitAnim = ExitAnimationType.SLIDE_DOWN,
        defaultEasing = AnimationEasingType.DECELERATE,
        entryDurationMs = 180,
        exitDurationMs = 130,
        dominantScaleMultiplier = 1.10f,
        expressiveScaleMultiplier = 1.05f,
        shadowRadiusFactor = 1.0f,
        preferredRegions =
          listOf(
            SafeRegion.UPPER_LEFT,
            SafeRegion.RIGHT_SIDE,
            SafeRegion.LOWER_CENTER,
            SafeRegion.LEFT_SIDE,
            SafeRegion.UPPER_RIGHT,
          ),
        sampleSentence = "Why do many creators add",
      ),
      PresetDefinition(
        id = "minimal",
        name = "MINIMAL",
        tagline = "Oswald + Plus Jakarta + Libre Baskerville",
        description =
          "Restrained whitespace-first editorial captions with calm typography contrast, subtle yellow highlights, and zero visual clutter.",
        heavyFontId = "oswald",
        supportingFontId = "plus_jakarta_sans",
        expressiveFontId = "libre_baskerville",
        primaryColorHex = StudioPalette.WHITE,
        dominantColorHex = StudioPalette.WHITE,
        expressiveColorHex = StudioPalette.YELLOW,
        dramaticColorHex = StudioPalette.ORANGE,
        defaultEntryAnim = EntryAnimationType.SLIDE_UP,
        defaultExitAnim = ExitAnimationType.FADE_DROP,
        defaultEasing = AnimationEasingType.SMOOTH_IN_OUT,
        entryDurationMs = 200,
        exitDurationMs = 150,
        dominantScaleMultiplier = 0.88f,
        expressiveScaleMultiplier = 0.94f,
        shadowRadiusFactor = 0.7f,
        preferredRegions =
          listOf(SafeRegion.LOWER_CENTER, SafeRegion.UPPER_CENTER),
        sampleSentence = "No, it's not just because of it",
      ),
      PresetDefinition(
        id = "magazine",
        name = "MAGAZINE",
        tagline = "Anton + DM Sans + Playfair Serif",
        description =
          "Luxury print-inspired layouts amplifying expressive italic serifs in warm amber alongside crisp uppercase sans anchors.",
        heavyFontId = "anton",
        supportingFontId = "dm_sans",
        expressiveFontId = "playfair_display",
        primaryColorHex = StudioPalette.WHITE,
        dominantColorHex = StudioPalette.WHITE,
        expressiveColorHex = StudioPalette.ORANGE,
        dramaticColorHex = StudioPalette.YELLOW,
        defaultEntryAnim = EntryAnimationType.SCALE_RISE,
        defaultExitAnim = ExitAnimationType.FADE_DROP,
        defaultEasing = AnimationEasingType.DECELERATE,
        entryDurationMs = 180,
        exitDurationMs = 140,
        dominantScaleMultiplier = 0.96f,
        expressiveScaleMultiplier = 1.18f,
        shadowRadiusFactor = 0.9f,
        preferredRegions =
          listOf(
            SafeRegion.UPPER_RIGHT,
            SafeRegion.LOWER_CENTER,
            SafeRegion.UPPER_LEFT,
            SafeRegion.CENTER,
          ),
        sampleSentence = "Especially text like this",
      ),
      PresetDefinition(
        id = "neon",
        name = "NEON",
        tagline = "Archivo Black + Manrope + Bodoni",
        description =
          "High-contrast electric yellow, cyan, and coral accents backed by deep drop shadows for dark or moody studio footage.",
        heavyFontId = "archivo_black",
        supportingFontId = "manrope",
        expressiveFontId = "bodoni_moda",
        primaryColorHex = StudioPalette.WHITE,
        dominantColorHex = StudioPalette.YELLOW,
        expressiveColorHex = StudioPalette.CYAN,
        dramaticColorHex = StudioPalette.RED,
        defaultEntryAnim = EntryAnimationType.FADE_POP,
        defaultExitAnim = ExitAnimationType.SCALE_OUT,
        defaultEasing = AnimationEasingType.SPRING_OVERSHOOT,
        entryDurationMs = 140,
        exitDurationMs = 115,
        dominantScaleMultiplier = 1.08f,
        expressiveScaleMultiplier = 1.04f,
        shadowRadiusFactor = 1.55f,
        preferredRegions =
          listOf(SafeRegion.CENTER, SafeRegion.LOWER_CENTER, SafeRegion.UPPER_CENTER),
        sampleSentence = "Link in bio",
      ),
      PresetDefinition(
        id = "cinematic",
        name = "CINEMATIC",
        tagline = "Oswald + Roboto + Cormorant",
        description =
          "Wide-tracked filmic letterforms and refined serif flourishes with unhurried 230ms atmospheric dissolves.",
        heavyFontId = "oswald",
        supportingFontId = "roboto",
        expressiveFontId = "cormorant_garamond",
        primaryColorHex = StudioPalette.WHITE,
        dominantColorHex = StudioPalette.WHITE,
        expressiveColorHex = StudioPalette.ORANGE,
        dramaticColorHex = StudioPalette.YELLOW,
        defaultEntryAnim = EntryAnimationType.SLIDE_UP,
        defaultExitAnim = ExitAnimationType.FADE_DROP,
        defaultEasing = AnimationEasingType.SMOOTH_IN_OUT,
        entryDurationMs = 230,
        exitDurationMs = 180,
        dominantScaleMultiplier = 0.92f,
        expressiveScaleMultiplier = 1.12f,
        shadowRadiusFactor = 1.1f,
        preferredRegions =
          listOf(SafeRegion.LOWER_CENTER, SafeRegion.UPPER_CENTER),
        sampleSentence = "Why do many creators add",
      ),
      PresetDefinition(
        id = "creator",
        name = "CREATOR",
        tagline = "Bebas Neue + Plus Jakarta + Caveat",
        description =
          "Engineered for 9:16 talking-head reels—alternates quiet whispers, glowing amber stacks, and behind-subject condensed anchors.",
        heavyFontId = "bebas_neue",
        supportingFontId = "plus_jakarta_sans",
        expressiveFontId = "caveat",
        primaryColorHex = StudioPalette.WHITE,
        dominantColorHex = StudioPalette.YELLOW,
        expressiveColorHex = StudioPalette.ORANGE,
        dramaticColorHex = StudioPalette.RED,
        defaultEntryAnim = EntryAnimationType.STAGGER_REVEAL,
        defaultExitAnim = ExitAnimationType.SCALE_OUT,
        defaultEasing = AnimationEasingType.SPRING_OVERSHOOT,
        entryDurationMs = 150,
        exitDurationMs = 120,
        dominantScaleMultiplier = 1.14f,
        expressiveScaleMultiplier = 1.14f,
        shadowRadiusFactor = 1.0f,
        preferredRegions =
          listOf(
            SafeRegion.UPPER_CENTER,
            SafeRegion.LOWER_CENTER,
            SafeRegion.LEFT_SIDE,
            SafeRegion.UPPER_RIGHT,
          ),
        sampleSentence = "But here's the big problem",
      ),
    )

  @Volatile
  private var customPresetsList: List<PresetDefinition> = emptyList()

  fun customPresets(): List<PresetDefinition> = customPresetsList

  fun allPresets(): List<PresetDefinition> = customPresetsList + presets

  fun registerCustomPresets(customs: List<PresetDefinition>) {
    customPresetsList = customs.distinctBy { it.id }
  }

  fun findById(id: String): PresetDefinition {
    return customPresetsList.firstOrNull { it.id == id }
      ?: presets.firstOrNull { it.id == id }
      ?: presets.first()
  }

  /**
   * Re-styles existing compositions when switching presets without losing transcript edits, timing, or depth layers.
   */
  fun applyPresetToCompositions(
    preset: PresetDefinition,
    compositions: List<CaptionComposition>,
    analysisReport: VideoAnalysisReport,
    fontOverrides: FontRoleOverrides = FontRoleOverrides(),
  ): List<CaptionComposition> {
    val heavyFont = fontOverrides.heavyFontId ?: preset.heavyFontId
    val supportingFont = fontOverrides.supportingFontId ?: preset.supportingFontId
    val expressiveFont = fontOverrides.expressiveFontId ?: preset.expressiveFontId

    val candidateRegions =
      (analysisReport.safeRegionsRanked.filter { it in preset.preferredRegions } +
          preset.preferredRegions)
        .distinct()
        .ifEmpty { listOf(SafeRegion.UPPER_CENTER, SafeRegion.LOWER_CENTER) }

    return compositions.mapIndexed { idx, comp ->
      val preserveCustomRegion = comp.region == SafeRegion.SEAM_CENTER || comp.region == SafeRegion.CUSTOM
      val region = if (preserveCustomRegion) comp.region else candidateRegions[idx % candidateRegions.size]
      val baseElements =
        comp.elements.map { el ->
          val roleFont =
            when (el.role) {
              FontRole.HEAVY -> heavyFont
              FontRole.SUPPORTING -> supportingFont
              FontRole.EXPRESSIVE -> expressiveFont
            }
          val roleColor =
            when (el.hierarchyLevel) {
              HierarchyLevel.DOMINANT -> {
                if (el.colorHex == StudioPalette.RED ||
                    el.text.contains("BIG", ignoreCase = true) ||
                    el.text.contains("STOP", ignoreCase = true) ||
                    el.text.contains("PROBLEM", ignoreCase = true)
                ) {
                  preset.dramaticColorHex
                } else {
                  preset.dominantColorHex
                }
              }
              HierarchyLevel.EXPRESSIVE -> preset.expressiveColorHex
              HierarchyLevel.SUPPORTING,
              HierarchyLevel.SECONDARY -> preset.primaryColorHex
            }
          val baseSize =
            when (el.hierarchyLevel) {
              HierarchyLevel.DOMINANT -> 54f * preset.dominantScaleMultiplier
              HierarchyLevel.EXPRESSIVE -> 34f * preset.expressiveScaleMultiplier
              HierarchyLevel.SUPPORTING -> 19f
              HierarchyLevel.SECONDARY -> 16f
            }
          val useBehindSubject =
            if (preset.preferBehindSubject && el.hierarchyLevel == HierarchyLevel.DOMINANT) {
              com.example.captionstudio.domain.model.DepthLayer.BEHIND_SUBJECT
            } else {
              el.depthLayer
            }
          el.copy(
            fontFamilyId = roleFont,
            colorHex = roleColor,
            fontSizeSp = baseSize.coerceIn(15f, 74f),
            uppercase = if (preset.preferUppercase) true else el.uppercase,
            strokeWidthFactor =
              if (preset.strokeWidthFactor > 0f) preset.strokeWidthFactor else el.strokeWidthFactor,
            strokeColorHex =
              if (preset.strokeWidthFactor > 0f) preset.strokeColorHex else el.strokeColorHex,
            backgroundColorHex = preset.backgroundPillColorHex ?: el.backgroundColorHex,
            backgroundAlpha =
              if (preset.backgroundPillColorHex != null) preset.backgroundPillAlpha else el.backgroundAlpha,
            depthLayer = useBehindSubject,
            glowColorHex =
              if (roleColor == StudioPalette.YELLOW || roleColor == StudioPalette.ORANGE) roleColor
              else null,
            letterSpacingEm =
              if (preset.id == "cinematic" && el.role == FontRole.HEAVY) 0.08f
              else if (el.role == FontRole.HEAVY) -0.02f
              else 0f,
          )
        }
      comp.copy(
        region = region,
        anchorXFraction = if (preserveCustomRegion) comp.anchorXFraction else region.defaultAnchorX,
        anchorYFraction = if (preserveCustomRegion) comp.anchorYFraction else region.defaultAnchorY,
        entryAnimation = preset.defaultEntryAnim,
        exitAnimation = preset.defaultExitAnim,
        easing = preset.defaultEasing,
        entryDurationMs = preset.entryDurationMs,
        exitDurationMs = preset.exitDurationMs,
        depthLayer =
          if (preset.preferBehindSubject) com.example.captionstudio.domain.model.DepthLayer.BEHIND_SUBJECT
          else comp.depthLayer,
        backgroundMaskEnabled = if (preset.preferBehindSubject) true else comp.backgroundMaskEnabled,
        elements = baseElements,
      )
    }
  }
}
