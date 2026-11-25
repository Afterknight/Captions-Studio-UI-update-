package com.example.captionstudio.presets

import com.example.captionstudio.domain.model.AnimationEasingType
import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.DesignerFrameStyle
import com.example.captionstudio.domain.model.EntryAnimationType
import com.example.captionstudio.domain.model.ExitAnimationType
import com.example.captionstudio.domain.model.FontRole
import com.example.captionstudio.domain.model.FontRoleOverrides
import com.example.captionstudio.domain.model.HierarchyLevel
import com.example.captionstudio.domain.model.LetterAnimationStyle
import com.example.captionstudio.domain.model.PresetDefinition
import com.example.captionstudio.domain.model.SafeRegion
import com.example.captionstudio.domain.model.SpatialLayoutMode
import com.example.captionstudio.domain.model.StudioPalette
import com.example.captionstudio.domain.model.TextSurfaceStyle
import com.example.captionstudio.domain.model.VectorFlourish
import com.example.captionstudio.domain.model.VideoAnalysisReport

object PresetCatalog {

  const val DEFAULT_PRESET_ID = "editorial"

  const val SIGNATURE_BEAT_POLARITY_ID = "sig_beat_polarity"
  const val SIGNATURE_EDITORIAL_HALO_ID = "sig_editorial_halo"
  const val SIGNATURE_STORY_ARC_ID = "sig_story_arc"
  const val SIGNATURE_HANDCRAFTED_FRAME_ID = "sig_handcrafted_frame"

  val signaturePresetIds: Set<String> =
    setOf(
      SIGNATURE_BEAT_POLARITY_ID,
      SIGNATURE_EDITORIAL_HALO_ID,
      SIGNATURE_STORY_ARC_ID,
      SIGNATURE_HANDCRAFTED_FRAME_ID,
    )

  val signaturePresets: List<PresetDefinition> =
    listOf(
      PresetDefinition(
        id = SIGNATURE_BEAT_POLARITY_ID,
        name = "BEAT POLARITY",
        tagline = "Space Grotesk • Beat Typing + Cream/Navy Cards",
        description =
          "Tight lowercase geometric grotesk with beat-synced character reveal typing and alternating high-contrast warm cream and midnight navy gold-accent polarity cards.",
        heavyFontId = "space_grotesk",
        supportingFontId = "space_grotesk",
        expressiveFontId = "plus_jakarta_sans",
        primaryColorHex = "#111318",
        dominantColorHex = "#C8A252",
        expressiveColorHex = "#EFECE4",
        dramaticColorHex = "#C8A252",
        defaultEntryAnim = EntryAnimationType.FADE_POP,
        defaultExitAnim = ExitAnimationType.CLEAN_CUT,
        defaultEasing = AnimationEasingType.DECELERATE,
        entryDurationMs = 110,
        exitDurationMs = 95,
        dominantScaleMultiplier = 0.98f,
        expressiveScaleMultiplier = 1.0f,
        shadowRadiusFactor = 0.7f,
        preferredRegions =
          listOf(SafeRegion.LOWER_CENTER, SafeRegion.CENTER, SafeRegion.UPPER_CENTER),
        sampleSentence = "design every beat with contrast",
        preferUppercase = false,
        defaultLetterAnimation = LetterAnimationStyle.CHAR_REVEAL,
        defaultSurfaceStyle = TextSurfaceStyle.STANDARD,
        defaultDesignerFrame = DesignerFrameStyle.POLARITY_CARD,
        defaultSpatialMode = SpatialLayoutMode.STANDARD,
        defaultFlourish = VectorFlourish.NONE,
      ),
      PresetDefinition(
        id = SIGNATURE_EDITORIAL_HALO_ID,
        name = "EDITORIAL HALO",
        tagline = "Inter + Playfair Italic • Halation + L/R Split",
        description =
          "Tight-tracked luminous Inter white typography with 140ms film halation bloom, +8% optically matched italic Playfair Display accents, and subject-framing left/right split emphasis blocks.",
        heavyFontId = "inter",
        supportingFontId = "inter",
        expressiveFontId = "playfair_display",
        primaryColorHex = StudioPalette.WHITE,
        dominantColorHex = StudioPalette.WHITE,
        expressiveColorHex = StudioPalette.CREAM,
        dramaticColorHex = StudioPalette.GOLD_AMBER,
        defaultEntryAnim = EntryAnimationType.SLIDE_UP,
        defaultExitAnim = ExitAnimationType.FADE_DROP,
        defaultEasing = AnimationEasingType.DECELERATE,
        entryDurationMs = 150,
        exitDurationMs = 130,
        dominantScaleMultiplier = 1.04f,
        expressiveScaleMultiplier = 1.08f,
        shadowRadiusFactor = 0.85f,
        preferredRegions =
          listOf(SafeRegion.CENTER, SafeRegion.LOWER_CENTER, SafeRegion.UPPER_CENTER),
        sampleSentence = "Quiet craft beats loud noise",
        preferUppercase = false,
        defaultLetterAnimation = LetterAnimationStyle.NONE,
        defaultSurfaceStyle = TextSurfaceStyle.HALATION_BLOOM,
        defaultDesignerFrame = DesignerFrameStyle.NONE,
        defaultSpatialMode = SpatialLayoutMode.LEFT_RIGHT_SPLIT,
        defaultFlourish = VectorFlourish.NONE,
      ),
      PresetDefinition(
        id = SIGNATURE_STORY_ARC_ID,
        name = "STORY ARC",
        tagline = "Gold Italic Arcs • 3D Inflated Hero Bursts",
        description =
          "Warm-gold italic whispers curving gently above the subject along a quadratic Bezier arc, punctuated by glossy 3D inflated hero bursts on peak words.",
        heavyFontId = "archivo_black",
        supportingFontId = "playfair_display",
        expressiveFontId = "cormorant_garamond",
        primaryColorHex = "#FFD166",
        dominantColorHex = StudioPalette.GOLD_AMBER,
        expressiveColorHex = "#FFD166",
        dramaticColorHex = StudioPalette.ORANGE,
        defaultEntryAnim = EntryAnimationType.SCALE_RISE,
        defaultExitAnim = ExitAnimationType.FADE_DROP,
        defaultEasing = AnimationEasingType.SPRING_OVERSHOOT,
        entryDurationMs = 165,
        exitDurationMs = 135,
        dominantScaleMultiplier = 1.12f,
        expressiveScaleMultiplier = 1.08f,
        shadowRadiusFactor = 1.0f,
        preferredRegions =
          listOf(SafeRegion.UPPER_CENTER, SafeRegion.CENTER, SafeRegion.LOWER_CENTER),
        sampleSentence = "Watch this story unfold",
        preferUppercase = false,
        defaultLetterAnimation = LetterAnimationStyle.STAGGER_POP,
        defaultSurfaceStyle = TextSurfaceStyle.INFLATED_3D,
        defaultDesignerFrame = DesignerFrameStyle.NONE,
        defaultSpatialMode = SpatialLayoutMode.CURVED_ARC_PATH,
        defaultFlourish = VectorFlourish.SPARKLE_BURST,
      ),
      PresetDefinition(
        id = SIGNATURE_HANDCRAFTED_FRAME_ID,
        name = "HANDCRAFTED FRAME",
        tagline = "Condensed Serif • 15fps Hand-Boil + Ink Frames",
        description =
          "Condensed editorial white italic serif baseline interrupted by 15fps boiling hand-lettered marker words, Figma dashed transform boxes, paper cutout badges, and self-drawing ink flourishes.",
        heavyFontId = "caveat",
        supportingFontId = "libre_baskerville",
        expressiveFontId = "caveat",
        primaryColorHex = StudioPalette.WHITE,
        dominantColorHex = "#17181C",
        expressiveColorHex = StudioPalette.GOLD_AMBER,
        dramaticColorHex = StudioPalette.CORAL,
        defaultEntryAnim = EntryAnimationType.FADE_POP,
        defaultExitAnim = ExitAnimationType.FADE_DROP,
        defaultEasing = AnimationEasingType.SPRING_OVERSHOOT,
        entryDurationMs = 145,
        exitDurationMs = 120,
        dominantScaleMultiplier = 1.06f,
        expressiveScaleMultiplier = 1.14f,
        shadowRadiusFactor = 0.9f,
        preferredRegions =
          listOf(SafeRegion.LOWER_CENTER, SafeRegion.UPPER_CENTER, SafeRegion.CENTER),
        sampleSentence = "Make every detail feel handcrafted",
        preferUppercase = false,
        defaultLetterAnimation = LetterAnimationStyle.HAND_BOIL_15FPS,
        defaultSurfaceStyle = TextSurfaceStyle.CONDENSED_EDITORIAL,
        defaultDesignerFrame = DesignerFrameStyle.DASHED_SELECTION_BOX,
        defaultSpatialMode = SpatialLayoutMode.STANDARD,
        defaultFlourish = VectorFlourish.SWOOSH_UNDERLINE,
      ),
    )

  val classicKineticPresets: List<PresetDefinition> =
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

  val kineticPresets: List<PresetDefinition> =
    signaturePresets + classicKineticPresets + ExpandedPresets.expandedKineticPresets

  val normalPresets: List<PresetDefinition> = ExpandedPresets.normalPresets

  val presets: List<PresetDefinition> = kineticPresets + normalPresets

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
   * Re-styles existing compositions when switching presets without losing transcript edits,
   * word timings, or custom placements. Cleanly adapts between Normal uniform subtitles and
   * multi-tier Kinetic typography!
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

    val fallbackRegion =
      analysisReport.shotAnalyses.firstOrNull()?.preferredZone
        ?: analysisReport.safeRegionsRanked.firstOrNull {
          it == SafeRegion.LOWER_CENTER || it == SafeRegion.UPPER_CENTER || it == SafeRegion.CENTER
        }
        ?: preset.preferredRegions.firstOrNull()
        ?: SafeRegion.LOWER_CENTER

    return compositions.mapIndexed { idx, comp ->
      val preserveCoords = true
      val effectiveRegion = if (comp.region == SafeRegion.CUSTOM) SafeRegion.CUSTOM else comp.region

      // Branch S: Switching to one of the 4 flagship Signature Presets -> dedicated signature choreography
      if (preset.id in signaturePresetIds) {
        return@mapIndexed com.example.captionstudio.engine.LocalCompositionEngine.buildSignatureComposition(
          comp = comp,
          phraseIndex = idx,
          preset = preset,
          analysisReport = analysisReport,
          fontOverrides = fontOverrides,
        )
      }

      // Branch A: Switching to a NORMAL everyday caption preset -> clean single-block uniform caption + karaoke
      if (preset.category == com.example.captionstudio.domain.model.PresetCategory.NORMAL) {
        val normalEl =
          com.example.captionstudio.engine.LocalCompositionEngine.buildNormalElementForComposition(
            comp = comp,
            preset = preset,
            fontOverrides = fontOverrides,
          )
        val anchorRegion =
          if (comp.region == SafeRegion.CUSTOM) SafeRegion.CUSTOM
          else if (comp.region == SafeRegion.UPPER_CENTER) SafeRegion.UPPER_CENTER
          else SafeRegion.LOWER_CENTER
        return@mapIndexed comp.copy(
          region = anchorRegion,
          anchorXFraction = if (comp.region == SafeRegion.CUSTOM) comp.anchorXFraction else anchorRegion.defaultAnchorX,
          anchorYFraction = if (comp.region == SafeRegion.CUSTOM) comp.anchorYFraction else anchorRegion.defaultAnchorY,
          entryAnimation = preset.defaultEntryAnim,
          exitAnimation = preset.defaultExitAnim,
          easing = preset.defaultEasing,
          entryDurationMs = preset.entryDurationMs,
          exitDurationMs = preset.exitDurationMs,
          depthLayer = com.example.captionstudio.domain.model.DepthLayer.IN_FRONT,
          styleVariant = com.example.captionstudio.domain.model.CompositionStyleVariant.STANDARD,
          spatialMode = preset.defaultSpatialMode,
          backgroundMaskEnabled = preset.preferBehindSubject,
          elements = listOf(normalEl),
        )
      }

      // Branch B: Switching to a KINETIC preset from a single-element Normal composition -> expand back to kinetic tiers
      val wordCount = comp.originalText.trim().split(Regex("\\s+")).count { it.isNotBlank() }
      if (comp.styleVariant == com.example.captionstudio.domain.model.CompositionStyleVariant.STANDARD &&
          comp.elements.size == 1 &&
          wordCount >= 2
      ) {
        val rebuiltElements =
          com.example.captionstudio.engine.LocalCompositionEngine.buildKineticElementsForComposition(
            comp = comp,
            phraseIndex = idx,
            preset = preset,
            analysisReport = analysisReport,
            fontOverrides = fontOverrides,
          )
        val hasBehind = rebuiltElements.any { it.depthLayer == com.example.captionstudio.domain.model.DepthLayer.BEHIND_SUBJECT }
        return@mapIndexed comp.copy(
          region = effectiveRegion,
          anchorXFraction = if (preserveCoords) comp.anchorXFraction else fallbackRegion.defaultAnchorX,
          anchorYFraction = if (preserveCoords) comp.anchorYFraction else fallbackRegion.defaultAnchorY,
          entryAnimation = preset.defaultEntryAnim,
          exitAnimation = preset.defaultExitAnim,
          easing = preset.defaultEasing,
          entryDurationMs = preset.entryDurationMs,
          exitDurationMs = preset.exitDurationMs,
          depthLayer =
            if (hasBehind || preset.preferBehindSubject) com.example.captionstudio.domain.model.DepthLayer.BEHIND_SUBJECT
            else com.example.captionstudio.domain.model.DepthLayer.IN_FRONT,
          styleVariant = com.example.captionstudio.domain.model.CompositionStyleVariant.HERO_STACK,
          spatialMode = preset.defaultSpatialMode,
          backgroundMaskEnabled = hasBehind || preset.preferBehindSubject,
          elements = rebuiltElements,
        )
      }

      // Branch C: Kinetic -> Kinetic restyle preserving manual edits & layer hierarchy
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
            strokeWidthFactor = preset.strokeWidthFactor,
            strokeColorHex = preset.strokeColorHex,
            backgroundColorHex = preset.backgroundPillColorHex,
            backgroundAlpha = preset.backgroundPillAlpha,
            depthLayer = useBehindSubject,
            glowColorHex =
              if (roleColor == StudioPalette.YELLOW ||
                  roleColor == StudioPalette.ORANGE ||
                  roleColor == StudioPalette.CYAN
              ) {
                roleColor
              } else {
                null
              },
            letterSpacingEm =
              if (preset.id == "cinematic" && el.role == FontRole.HEAVY) 0.08f
              else if (el.role == FontRole.HEAVY) -0.02f
              else 0f,
            karaokeMode = preset.defaultKaraokeMode,
            karaokeHighlightColorHex = preset.karaokeHighlightColorHex,
            karaokePillColorHex = preset.karaokePillColorHex,
            letterAnimation = preset.defaultLetterAnimation,
            surfaceStyle = preset.defaultSurfaceStyle,
            tiltDegrees = 0f,
            designerFrame = preset.defaultDesignerFrame,
            flourish = preset.defaultFlourish,
            polarityVariantIndex = 0,
          )
        }
      comp.copy(
        region = effectiveRegion,
        anchorXFraction = if (preserveCoords) comp.anchorXFraction else fallbackRegion.defaultAnchorX,
        anchorYFraction = if (preserveCoords) comp.anchorYFraction else fallbackRegion.defaultAnchorY,
        entryAnimation = preset.defaultEntryAnim,
        exitAnimation = preset.defaultExitAnim,
        easing = preset.defaultEasing,
        entryDurationMs = preset.entryDurationMs,
        exitDurationMs = preset.exitDurationMs,
        depthLayer =
          if (preset.preferBehindSubject) com.example.captionstudio.domain.model.DepthLayer.BEHIND_SUBJECT
          else comp.depthLayer,
        spatialMode = preset.defaultSpatialMode,
        backgroundMaskEnabled = if (preset.preferBehindSubject) true else comp.backgroundMaskEnabled,
        elements = baseElements,
      )
    }
  }
}
