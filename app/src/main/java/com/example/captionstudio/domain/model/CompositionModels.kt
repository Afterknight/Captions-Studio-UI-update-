package com.example.captionstudio.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
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

  /**
   * 24-color designer palette grid organized into 4 balanced rows (6 per row) for instant
   * brand matching: Neutrals, Warm Cinema, Vivid Creator, and Cool Electric.
   */
  val brandPaletteGrid: List<List<ColorSwatchOption>> =
    listOf(
      // Row 1: Studio Neutrals & Editorial Creams
      listOf(
        ColorSwatchOption("#FFFFFF", "Pure White"),
        ColorSwatchOption("#F5F5F7", "Soft Ivory"),
        ColorSwatchOption("#FFF3D6", "Warm Cream"),
        ColorSwatchOption("#A1A1AA", "Silver Slate"),
        ColorSwatchOption("#27272E", "Graphite"),
        ColorSwatchOption("#000000", "Pure Black"),
      ),
      // Row 2: Warm Coral, Golds & Cinema Reds
      listOf(
        ColorSwatchOption("#FFE066", "Sunbeam"),
        ColorSwatchOption("#FFB81C", "Studio Gold"),
        ColorSwatchOption("#FF8C42", "Tangerine"),
        ColorSwatchOption("#EE7E70", "Studio Coral"),
        ColorSwatchOption("#FF2E2E", "Cinema Red"),
        ColorSwatchOption("#B5174E", "Deep Berry"),
      ),
      // Row 3: Vivid Creator Pops & Pastels
      listOf(
        ColorSwatchOption("#FF5CA8", "Hot Pink"),
        ColorSwatchOption("#FF99C8", "Blush Rose"),
        ColorSwatchOption("#D946EF", "Electric Orchid"),
        ColorSwatchOption("#A87BFF", "Soft Violet"),
        ColorSwatchOption("#6366F1", "Indigo Glow"),
        ColorSwatchOption("#3B82F6", "Cobalt Blue"),
      ),
      // Row 4: Cool Cyans, Mint & Emerald
      listOf(
        ColorSwatchOption("#4CC9F0", "Sky Cyan"),
        ColorSwatchOption("#22D3EE", "Ice Aqua"),
        ColorSwatchOption("#14B8A6", "Deep Teal"),
        ColorSwatchOption("#3AE8B0", "Neon Mint"),
        ColorSwatchOption("#84CC16", "Electric Lime"),
        ColorSwatchOption("#22C55E", "Fresh Emerald"),
      ),
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
enum class SafeZonePlatform(val label: String, val shortLabel: String) {
  NONE("Off", "Off"),
  GRID("Grid", "Grid"),
  TIKTOK("TikTok", "TikTok"),
  REELS("Instagram Reels", "Reels"),
  SHORTS("YouTube Shorts", "Shorts"),
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

/**
 * Predefined per-layer kinetic motion effects applicable directly from the typography
 * customization menu and rendered identically in Live Preview and Media3 MP4 export.
 */
@Serializable
enum class LayerMotionEffect(
  val label: String,
  val category: String,
  val summary: String,
) {
  NONE("None", "Basic", "Follows caption timing cleanly"),
  FADE_IN("Fade In", "Entry", "Velvety opacity dissolve in"),
  SLIDE_UP("Slide Up", "Entry", "Crisp upward glide into place"),
  SLIDE_DOWN("Slide Down", "Entry", "Smooth drop-in from above"),
  SLIDE_LEFT("Slide In", "Entry", "Dynamic horizontal glide in"),
  POP_IN("Pop In", "Entry", "Punchy spring overshoot pop"),
  ZOOM_IN("Zoom In", "Entry", "Cinematic zoom-up reveal"),
  BOUNCE("Bounce", "Dynamic", "Elastic bouncy landing & hop"),
  WIGGLE("Wiggle", "Loop", "Playful rhythmic tilt wiggle"),
  PULSE("Pulse", "Loop", "Alive breathing scale beat"),
  FLOAT("Float", "Loop", "Weightless hovering drift"),
  WAVE("Wave", "Loop", "Smooth per-word ocean wave"),
  SWING("Swing", "Loop", "Gentle pendulum sway tilt"),
  SHAKE("Jitter", "Dynamic", "High-energy hype micro-shake"),
  TYPEWRITER("Typewriter", "Reveal", "Character-by-character typing"),
}

/**
 * Millisecond-synced active-word karaoke highlight styles rendered identically in Live Preview
 * and exported MP4 videos.
 */
@Serializable
enum class KaraokeHighlightMode(
  val label: String,
  val summary: String,
) {
  OFF("Off", "Uniform phrase styling"),
  COLOR_POP("Color Pop", "Active spoken word pops in accent color"),
  PILL_SWEEP("Pill Sweep", "Animated highlight pill glides across each word"),
  SCALE_POP("Scale Pop", "Elastic scale punch + color highlight per word"),
  UNDERLINE_SWEEP("Underline", "Glowing accent bar sweeps under each word"),
}

@Serializable
enum class PresetCategory(val label: String) {
  NORMAL("Normal"),
  KINETIC("Kinetic"),
}

/**
 * Batch pacing templates that automatically apply high-impact hook typography to the
 * opening 3 seconds (< 3000ms) and clean, retention-friendly body styling afterwards.
 */
@Serializable
enum class HookPacingTemplate(
  val id: String,
  val title: String,
  val subtitle: String,
  val hookWindowMs: Long = 3000L,
) {
  VIRAL_HOOK_KARAOKE_BODY(
    id = "viral_karaoke",
    title = "Viral Hook + Karaoke",
    subtitle = "0–3s bold impact opener, then color-pop karaoke body",
  ),
  BEHIND_HOOK_SUBTITLE_BODY(
    id = "behind_subtitle",
    title = "Behind-Subject + Clean",
    subtitle = "0–3s behind-subject hero hook, then crisp lower subtitles",
  ),
  IMPACT_HOOK_PILL_BODY(
    id = "impact_pill",
    title = "Impact Hook + Pill",
    subtitle = "0–3s oversized yellow hook, then pill-sweep body captions",
  ),
  CINEMATIC_HOOK_MINIMAL(
    id = "cinematic_minimal",
    title = "Cinema Opener + Minimal",
    subtitle = "0–3s wide gold headline hook, then quiet minimal body",
  ),
  UNIFORM_RESET(
    id = "uniform_reset",
    title = "Reset Uniform",
    subtitle = "Match all hook & body captions evenly to current preset",
  ),
}

@Immutable
@Serializable
data class WordTiming(
  val word: String,
  val startMs: Long,
  val endMs: Long,
  val isEstimated: Boolean = true,
  val emphasisScore: Float = 0f,
)

@Immutable
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
  val motionEffect: LayerMotionEffect = LayerMotionEffect.NONE,
  val motionSpeed: Float = 1.0f,
  val motionIntensity: Float = 1.0f,
  val karaokeMode: KaraokeHighlightMode = KaraokeHighlightMode.OFF,
  val karaokeHighlightColorHex: String = StudioPalette.YELLOW,
  val karaokePillColorHex: String? = null,
)

/**
 * Surgical patch model for updating any combination of kinetic typography layer attributes
 * cleanly without long parameter lists.
 */
@Immutable
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
  val motionEffect: LayerMotionEffect? = null,
  val motionSpeed: Float? = null,
  val motionIntensity: Float? = null,
  val karaokeMode: KaraokeHighlightMode? = null,
  val karaokeHighlightColorHex: String? = null,
  val karaokePillColorHex: String? = null,
  val clearKaraokePill: Boolean = false,
)

@Immutable
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

@Immutable
@Serializable
data class NormalizedRect(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
)

@Immutable
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

@Immutable
@Serializable
data class ZoneVisualMetrics(
  val edgeClutter: Float = 0f,
  val meanLuminance: Float = 96f,
  val isBlockedByFace: Boolean = false,
  val isBlockedBySubject: Boolean = false,
  val isWideAndEmpty: Boolean = false,
)

@Immutable
@Serializable
data class ShotVisualAnalysis(
  val startMs: Long = 0L,
  val endMs: Long = Long.MAX_VALUE,
  val headVetoRect: NormalizedRect? = null,
  val subjectCentroidX: Float = 0.5f,
  val subjectCentroidY: Float = 0.55f,
  val subjectCoverage: Float = 0f,
  val isSplitScreen: Boolean = false,
  val zoneMetrics: Map<SafeRegion, ZoneVisualMetrics> = emptyMap(),
  val preferredZone: SafeRegion = SafeRegion.LOWER_CENTER,
)

@Immutable
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
  val shotAnalyses: List<ShotVisualAnalysis> = emptyList(),
)

@Immutable
@Serializable
data class ExportPreferences(
  val aspectRatio: AspectRatioOption = AspectRatioOption.PORTRAIT_9_16,
  val resolution: ExportResolution = ExportResolution.RES_1080P,
  val frameRate: ExportFrameRate = ExportFrameRate.FPS_30,
  val qualityMode: ExportQualityMode = ExportQualityMode.BALANCED,
  val showWatermark: Boolean = true,
)

@Immutable
@Serializable
data class AnimationDefaults(
  val entryAnimation: EntryAnimationType = EntryAnimationType.SCALE_RISE,
  val exitAnimation: ExitAnimationType = ExitAnimationType.FADE_DROP,
  val easing: AnimationEasingType = AnimationEasingType.DECELERATE,
  val entryDurationMs: Int = 160,
  val exitDurationMs: Int = 130,
)

@Immutable
@Serializable
data class FontRoleOverrides(
  val heavyFontId: String? = null,
  val supportingFontId: String? = null,
  val expressiveFontId: String? = null,
)

@Immutable
@Serializable
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
  val strokeWidthFactor: Float = 0f,
  val strokeColorHex: String = StudioPalette.SHADOW,
  val backgroundPillColorHex: String? = null,
  val backgroundPillAlpha: Float = 0.74f,
  val preferBehindSubject: Boolean = false,
  val preferUppercase: Boolean = false,
  val isCustomCloned: Boolean = false,
  val category: PresetCategory = PresetCategory.KINETIC,
  val defaultKaraokeMode: KaraokeHighlightMode = KaraokeHighlightMode.OFF,
  val karaokeHighlightColorHex: String = StudioPalette.YELLOW,
  val karaokePillColorHex: String? = null,
)

@Immutable
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
