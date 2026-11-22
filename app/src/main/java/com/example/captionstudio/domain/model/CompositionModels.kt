package com.example.captionstudio.domain.model

import kotlinx.serialization.Serializable

data class ColorSwatchOption(
  val hex: String,
  val label: String,
)

object StudioPalette {
  const val WHITE = "#FFFFFF"
  const val OFF_WHITE = "#F5F7FA"
  const val CREAM = "#FFF3D6"
  const val YELLOW = "#FFB81C"
  const val GOLD_AMBER = "#FFB81C"
  const val ORANGE = "#FFAD28"
  const val CORAL = "#EE7E70"
  const val RED = "#FF2E2E"
  const val PINK = "#FF5CA8"
  const val VIOLET = "#A87BFF"
  const val CYAN = "#4CC9F0"
  const val MINT = "#3AE8B0"
  const val CHARCOAL = "#1A1A20"
  const val SHADOW = "#000000"

  val textSwatches: List<ColorSwatchOption> =
    listOf(
      ColorSwatchOption(WHITE, "White"),
      ColorSwatchOption(CREAM, "Warm Cream"),
      ColorSwatchOption(YELLOW, "Yellow"),
      ColorSwatchOption(ORANGE, "Amber"),
      ColorSwatchOption(CORAL, "Coral"),
      ColorSwatchOption(RED, "Crimson Red"),
      ColorSwatchOption(PINK, "Neon Pink"),
      ColorSwatchOption(VIOLET, "Violet"),
      ColorSwatchOption(CYAN, "Sky Cyan"),
      ColorSwatchOption(MINT, "Mint Green"),
      ColorSwatchOption(CHARCOAL, "Charcoal"),
      ColorSwatchOption(SHADOW, "Pure Black"),
    )

  val effectSwatches: List<ColorSwatchOption> =
    listOf(
      ColorSwatchOption(SHADOW, "Black"),
      ColorSwatchOption(CHARCOAL, "Obsidian"),
      ColorSwatchOption(WHITE, "White"),
      ColorSwatchOption(YELLOW, "Yellow"),
      ColorSwatchOption(ORANGE, "Amber"),
      ColorSwatchOption(CORAL, "Coral"),
      ColorSwatchOption(RED, "Red"),
      ColorSwatchOption(PINK, "Pink"),
      ColorSwatchOption(VIOLET, "Violet"),
      ColorSwatchOption(CYAN, "Cyan"),
      ColorSwatchOption(MINT, "Mint"),
    )
}

@Serializable
enum class DepthLayer(val label: String) {
  IN_FRONT("In Front"),
  BEHIND_SUBJECT("Behind Subject"),
}

@Serializable
enum class CompositionStyleVariant(val label: String) {
  STANDARD("Standard"),
  HERO_STACK("Hero Behind Stack"),
  WHISPER_NEGATIVE_SPACE("Quiet Negative Space"),
  LEFT_ANCHOR_STACK("Side Staircase Stack"),
  SEAM_CALLOUT("Split Seam Callout"),
  GLOW_BANNER("Warm Glow Anchor"),
}

@Serializable
enum class AspectRatioOption(val label: String, val ratioWidth: Float, val ratioHeight: Float) {
  PORTRAIT_9_16("9:16", 9f, 16f),
  LANDSCAPE_16_9("16:9", 16f, 9f),
  SQUARE_1_1("1:1", 1f, 1f),
}

@Serializable
enum class ExportResolution(val label: String, val shortEdgePx: Int) {
  RES_720P("720p", 720),
  RES_1080P("1080p", 1080),
  RES_SOURCE("Source", 0),
}

@Serializable
enum class ExportFrameRate(val fps: Int, val label: String) {
  FPS_24(24, "24 fps"),
  FPS_30(30, "30 fps"),
  FPS_60(60, "60 fps"),
}

@Serializable
enum class ExportQualityMode(val label: String, val bitrateMbps: Int) {
  BALANCED("Balanced", 8),
  HIGH_QUALITY("High Quality", 16),
}

@Serializable
enum class PreviewQualityMode(val label: String, val description: String) {
  PERFORMANCE("Performance", "Fast cached overlay rendering for older/mid devices"),
  HIGH_QUALITY("High Quality", "Full anti-aliased shadows and 60Hz animation sampling"),
}

@Serializable
enum class HierarchyLevel {
  DOMINANT,
  SUPPORTING,
  EXPRESSIVE,
  SECONDARY,
}

@Serializable
enum class FontRole {
  HEAVY,
  SUPPORTING,
  EXPRESSIVE,
}

@Serializable
enum class TextAlignment {
  LEFT,
  CENTER,
  RIGHT,
}

@Serializable
enum class SafeRegion(
  val label: String,
  val defaultAnchorX: Float,
  val defaultAnchorY: Float,
) {
  UPPER_CENTER("Upper Center", 0.50f, 0.24f),
  UPPER_LEFT("Upper Left", 0.30f, 0.24f),
  UPPER_RIGHT("Upper Right", 0.70f, 0.24f),
  CENTER("Center", 0.50f, 0.50f),
  SEAM_CENTER("Split Seam", 0.50f, 0.52f),
  LOWER_CENTER("Lower Center", 0.50f, 0.78f),
  LEFT_SIDE("Left Side", 0.27f, 0.44f),
  RIGHT_SIDE("Right Side", 0.73f, 0.44f),
  CUSTOM("Custom", 0.50f, 0.50f),
}

@Serializable
enum class EntryAnimationType(val label: String) {
  SCALE_RISE("Scale & Rise"),
  SLIDE_UP("Slide Up"),
  FADE_POP("Fade Pop"),
  STAGGER_REVEAL("Staggered Reveal"),
  CUT("Clean Cut"),
}

@Serializable
enum class ExitAnimationType(val label: String) {
  FADE_DROP("Fade & Drop"),
  SCALE_OUT("Scale Out"),
  SLIDE_DOWN("Slide Down"),
  CLEAN_CUT("Clean Cut"),
}

@Serializable
enum class AnimationEasingType(val label: String) {
  DECELERATE("Ease Out Cubic"),
  SPRING_OVERSHOOT("Crisp Overshoot"),
  SMOOTH_IN_OUT("Smooth In-Out"),
  LINEAR("Linear"),
}

@Serializable
data class WordTiming(
  val word: String,
  val startMs: Long,
  val endMs: Long,
  val isEstimated: Boolean = true,
  val emphasisScore: Float = 0f,
)

@Serializable
data class CompositionElement(
  val id: String,
  val text: String,
  val role: FontRole,
  val hierarchyLevel: HierarchyLevel,
  val fontFamilyId: String,
  val fontWeight: Int = 700,
  val isItalic: Boolean = false,
  val fontSizeSp: Float = 24f,
  val colorHex: String = StudioPalette.WHITE,
  val alignment: TextAlignment = TextAlignment.CENTER,
  val uppercase: Boolean = false,
  val letterSpacingEm: Float = 0f,
  val staggerDelayMs: Int = 0,
  val wordIndices: List<Int> = emptyList(),
  val inlineRowGroup: Int = 0,
  val rotationDeg: Float = 0f,
  val offsetXEm: Float = 0f,
  val offsetYEm: Float = 0f,
  val glowColorHex: String? = null,
  val strokeWidthFactor: Float = 0.0f,
  val depthLayer: DepthLayer = DepthLayer.IN_FRONT,
  val strokeColorHex: String = StudioPalette.SHADOW,
  val backgroundColorHex: String? = null,
  val backgroundAlpha: Float = 0.72f,
  val backgroundCornerRadiusDp: Float = 10f,
  val shadowEnabled: Boolean = true,
  val shadowIntensity: Float = 1.0f,
  val shadowColorHex: String = StudioPalette.SHADOW,
  val opacity: Float = 1.0f,
  val lineHeightMultiplier: Float = 1.05f,
  val isUnderlined: Boolean = false,
  val glowIntensity: Float = 1.0f,
)

/**
 * Surgical patch model for updating any combination of kinetic typography layer attributes
 * cleanly without long parameter lists.
 */
data class ElementStylePatch(
  val fontFamilyId: String? = null,
  val fontWeight: Int? = null,
  val isItalic: Boolean? = null,
  val isUnderlined: Boolean? = null,
  val uppercase: Boolean? = null,
  val fontSizeSp: Float? = null,
  val colorHex: String? = null,
  val alignment: TextAlignment? = null,
  val letterSpacingEm: Float? = null,
  val lineHeightMultiplier: Float? = null,
  val opacity: Float? = null,
  val rotationDeg: Float? = null,
  val offsetXEm: Float? = null,
  val offsetYEm: Float? = null,
  val strokeWidthFactor: Float? = null,
  val strokeColorHex: String? = null,
  val glowColorHex: String? = null,
  val clearGlow: Boolean = false,
  val glowIntensity: Float? = null,
  val shadowEnabled: Boolean? = null,
  val shadowIntensity: Float? = null,
  val shadowColorHex: String? = null,
  val backgroundColorHex: String? = null,
  val clearBackground: Boolean = false,
  val backgroundAlpha: Float? = null,
  val backgroundCornerRadiusDp: Float? = null,
  val depthLayer: DepthLayer? = null,
  val safeRegion: SafeRegion? = null,
)

@Serializable
data class CaptionComposition(
  val id: String,
  val startMs: Long,
  val endMs: Long,
  val originalText: String,
  val region: SafeRegion = SafeRegion.LOWER_CENTER,
  val anchorXFraction: Float = 0.50f,
  val anchorYFraction: Float = 0.74f,
  val entryAnimation: EntryAnimationType = EntryAnimationType.SCALE_RISE,
  val exitAnimation: ExitAnimationType = ExitAnimationType.FADE_DROP,
  val easing: AnimationEasingType = AnimationEasingType.DECELERATE,
  val entryDurationMs: Int = 160,
  val exitDurationMs: Int = 130,
  val elements: List<CompositionElement> = emptyList(),
  val wordMappings: List<WordTiming> = emptyList(),
  val depthLayer: DepthLayer = DepthLayer.IN_FRONT,
  val styleVariant: CompositionStyleVariant = CompositionStyleVariant.HERO_STACK,
  val backgroundMaskEnabled: Boolean = true,
) {
  fun hasBehindSubjectElements(): Boolean {
    return backgroundMaskEnabled &&
      (depthLayer == DepthLayer.BEHIND_SUBJECT ||
        elements.any { it.depthLayer == DepthLayer.BEHIND_SUBJECT })
  }
}

@Serializable
data class NormalizedRect(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
)

@Serializable
data class VideoMetadata(
  val durationMs: Long = 0L,
  val width: Int = 1080,
  val height: Int = 1920,
  val rotationDegrees: Int = 0,
  val frameRate: Float = 30f,
  val hasAudio: Boolean = true,
) {
  val effectiveWidth: Int
    get() = if (rotationDegrees == 90 || rotationDegrees == 270) height else width

  val effectiveHeight: Int
    get() = if (rotationDegrees == 90 || rotationDegrees == 270) width else height
}

@Serializable
data class VideoAnalysisReport(
  val frameCountSampled: Int = 0,
  val sceneChangeTimestampsMs: List<Long> = emptyList(),
  val faceRegions: List<NormalizedRect> = emptyList(),
  val safeRegionsRanked: List<SafeRegion> =
    listOf(
      SafeRegion.UPPER_CENTER,
      SafeRegion.LOWER_CENTER,
      SafeRegion.UPPER_LEFT,
      SafeRegion.UPPER_RIGHT,
    ),
  val suggestedAnchorX: Float = 0.5f,
  val suggestedAnchorY: Float = 0.26f,
  val summary: String = "Local heuristic layout ready",
  val analyzedWithGemini: Boolean = false,
  val subjectCoverageFraction: Float = 0f,
  val isSplitScreenDetected: Boolean = false,
)

@Serializable
data class ExportPreferences(
  val aspectRatio: AspectRatioOption = AspectRatioOption.PORTRAIT_9_16,
  val resolution: ExportResolution = ExportResolution.RES_1080P,
  val frameRate: ExportFrameRate = ExportFrameRate.FPS_30,
  val qualityMode: ExportQualityMode = ExportQualityMode.BALANCED,
)

@Serializable
data class AnimationDefaults(
  val entryAnimation: EntryAnimationType = EntryAnimationType.SCALE_RISE,
  val exitAnimation: ExitAnimationType = ExitAnimationType.FADE_DROP,
  val easing: AnimationEasingType = AnimationEasingType.DECELERATE,
  val entryDurationMs: Int = 160,
  val exitDurationMs: Int = 130,
)

@Serializable
data class FontRoleOverrides(
  val heavyFontId: String? = null,
  val supportingFontId: String? = null,
  val expressiveFontId: String? = null,
)

data class PresetDefinition(
  val id: String,
  val name: String,
  val tagline: String,
  val description: String,
  val heavyFontId: String,
  val supportingFontId: String,
  val expressiveFontId: String,
  val primaryColorHex: String,
  val dominantColorHex: String,
  val expressiveColorHex: String,
  val dramaticColorHex: String,
  val defaultEntryAnim: EntryAnimationType,
  val defaultExitAnim: ExitAnimationType,
  val defaultEasing: AnimationEasingType,
  val entryDurationMs: Int,
  val exitDurationMs: Int,
  val dominantScaleMultiplier: Float,
  val expressiveScaleMultiplier: Float,
  val shadowRadiusFactor: Float,
  val preferredRegions: List<SafeRegion>,
  val sampleSentence: String,
)

data class CaptionProject(
  val id: String,
  val title: String,
  val videoUriString: String,
  val metadata: VideoMetadata,
  val presetId: String,
  val transcriptText: String,
  val wordTimings: List<WordTiming>,
  val compositions: List<CaptionComposition>,
  val analysisReport: VideoAnalysisReport,
  val fontOverrides: FontRoleOverrides,
  val animationDefaults: AnimationDefaults,
  val exportPreferences: ExportPreferences,
  val createdAtMs: Long,
  val updatedAtMs: Long,
)

enum class ApplyScope(val label: String) {
  SINGLE_ELEMENT("This Element"),
  SINGLE_COMPOSITION("This Composition"),
  ALL_COMPOSITIONS("All Compositions"),
}

enum class ProjectSortOption(val label: String) {
  DATE_CREATED("Date Created"),
  NAME("Name"),
  DURATION("Duration"),
}
