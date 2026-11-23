package com.example

import com.example.captionstudio.domain.model.CaptionComposition
import com.example.captionstudio.domain.model.CompositionElement
import com.example.captionstudio.domain.model.CompositionStyleVariant
import com.example.captionstudio.domain.model.DepthLayer
import com.example.captionstudio.domain.model.FontRole
import com.example.captionstudio.domain.model.HierarchyLevel
import com.example.captionstudio.domain.model.SafeRegion
import com.example.captionstudio.domain.model.StudioPalette
import com.example.captionstudio.domain.model.VideoAnalysisReport
import com.example.captionstudio.domain.model.WordTiming
import com.example.captionstudio.engine.CompositionValidator
import com.example.captionstudio.engine.LocalCompositionEngine
import com.example.captionstudio.media.OfflineVoskModelManager
import com.example.captionstudio.media.SpeechTranscriptionManager
import com.example.captionstudio.media.VoskVideoTranscriptionService
import com.example.captionstudio.presets.PresetCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionStudioEngineTest {

  @Test
  fun `reference example 1 - creators is dominant uppercase`() {
    val preset = PresetCatalog.findById("editorial")
    val comps =
      LocalCompositionEngine.generateCompositions(
        transcript = "Why do many creators add.",
        wordTimings = emptyList(),
        totalDurationMs = 3000L,
        preset = preset,
        analysisReport = VideoAnalysisReport(),
      )
    assertEquals(1, comps.size)
    val dominant = comps.first().elements.first { it.hierarchyLevel == HierarchyLevel.DOMINANT }
    assertTrue(dominant.text.equals("creators", ignoreCase = true))
    assertTrue(dominant.uppercase)
    assertTrue(dominant.fontSizeSp >= 40f)
  }

  @Test
  fun `reference example 2 - especially text like this mixes supporting, italic serif, and dominant`() {
    val preset = PresetCatalog.findById("editorial")
    val comps =
      LocalCompositionEngine.generateCompositions(
        transcript = "Especially text like this.",
        wordTimings = emptyList(),
        totalDurationMs = 3000L,
        preset = preset,
        analysisReport = VideoAnalysisReport(),
      )
    val elements = comps.first().elements
    val expressive = elements.first { it.role == FontRole.EXPRESSIVE && it.isItalic }
    val dominant = elements.first { it.role == FontRole.HEAVY && it.hierarchyLevel == HierarchyLevel.DOMINANT }
    assertTrue(expressive.text.equals("text", ignoreCase = true))
    assertTrue(dominant.text.equals("this", ignoreCase = true))
    assertTrue(elements.any { it.role == FontRole.SUPPORTING })
  }

  @Test
  fun `reference example 4 - big problem highlights BIG in red`() {
    val preset = PresetCatalog.findById("editorial")
    val comps =
      LocalCompositionEngine.generateCompositions(
        transcript = "But here's the big problem.",
        wordTimings = emptyList(),
        totalDurationMs = 3000L,
        preset = preset,
        analysisReport = VideoAnalysisReport(),
      )
    val dominant = comps.first().elements.first { it.hierarchyLevel == HierarchyLevel.DOMINANT }
    assertTrue(dominant.text.equals("big", ignoreCase = true))
    assertEquals(StudioPalette.RED, dominant.colorHex)
  }

  @Test
  fun `reference example 5 - link in bio uses yellow dominant callout`() {
    val preset = PresetCatalog.findById("editorial")
    val comps =
      LocalCompositionEngine.generateCompositions(
        transcript = "Link in bio.",
        wordTimings = emptyList(),
        totalDurationMs = 2200L,
        preset = preset,
        analysisReport = VideoAnalysisReport(),
      )
    val dominant = comps.first().elements.first()
    assertEquals(StudioPalette.YELLOW, dominant.colorHex)
    assertEquals(HierarchyLevel.DOMINANT, dominant.hierarchyLevel)
  }

  @Test
  fun `validator repairs invalid timestamps, fonts, bounds, and missing text`() {
    val broken =
      listOf(
        CaptionComposition(
          id = "c1",
          startMs = 1500L,
          endMs = 1000L,
          originalText = "Hello world",
          anchorXFraction = 0.99f,
          anchorYFraction = 0.01f,
          elements =
            listOf(
              CompositionElement(
                id = "e1",
                text = "world",
                role = FontRole.HEAVY,
                hierarchyLevel = HierarchyLevel.DOMINANT,
                fontFamilyId = "non_existent_font",
                fontSizeSp = 120f,
                colorHex = "INVALID",
              )
            ),
        )
      )
    val fixed = CompositionValidator.validateAndRepair(null, broken)
    assertEquals(1, fixed.size)
    assertTrue(fixed[0].endMs > fixed[0].startMs)
    assertTrue(fixed[0].anchorXFraction <= 0.85f)
    assertTrue(fixed[0].anchorYFraction >= 0.12f)
    assertEquals("anton", fixed[0].elements[0].fontFamilyId)
    assertTrue(fixed[0].elements[0].fontSizeSp <= 68f)
    assertEquals(StudioPalette.WHITE, fixed[0].elements[0].colorHex)
    assertTrue(fixed[0].elements.size >= 2)
  }

  @Test
  fun `switching between all 36 normal and kinetic presets preserves transcript and timings`() {
    val base =
      LocalCompositionEngine.generateCompositions(
        transcript = "Why do many creators add. Especially text like this.",
        wordTimings = emptyList(),
        totalDurationMs = 5000L,
        preset = PresetCatalog.findById("editorial"),
        analysisReport = VideoAnalysisReport(),
      )
    assertEquals(16, PresetCatalog.normalPresets.size)
    assertEquals(20, PresetCatalog.kineticPresets.size)
    assertEquals(36, PresetCatalog.presets.size)
    val legacyIds = listOf("editorial", "impact", "kinetic", "minimal", "magazine", "neon", "cinematic", "creator")
    legacyIds.forEach { legacyId ->
      assertTrue("Legacy preset $legacyId must exist", PresetCatalog.presets.any { it.id == legacyId })
    }
    PresetCatalog.presets.forEach { preset ->
      val restyled =
        PresetCatalog.applyPresetToCompositions(preset, base, VideoAnalysisReport())
      assertEquals(base.size, restyled.size)
      restyled.forEachIndexed { idx, comp ->
        assertEquals(base[idx].originalText, comp.originalText)
        assertEquals(base[idx].startMs, comp.startMs)
        assertEquals(base[idx].endMs, comp.endMs)
        assertTrue("Preset ${preset.id} must produce non-empty elements", comp.elements.isNotEmpty())
      }
    }
  }

  @Test
  fun `srt subtitle parser extracts exact cues`() {
    val srt =
      """
      1
      00:00:01,000 --> 00:00:02,500
      Why do many creators add

      2
      00:00:03,000 --> 00:00:04,500
      Especially text like this
      """.trimIndent()
    val (text, timings) = SpeechTranscriptionManager.parseSubtitles(srt)
    assertTrue(text.contains("creators"))
    assertEquals(1000L, timings.first().startMs)
    assertEquals(4500L, timings.last().endMs)
  }

  @Test
  fun `acoustic aligner snaps words to voiced intervals and skips silence`() {
    // 200 frames = 2000ms @ 10ms per frame
    // Voiced island 1: frames 20..60 (200ms..600ms)
    // Silence: frames 61..119 (610ms..1190ms)
    // Voiced island 2: frames 120..170 (1200ms..1700ms)
    val envelope = FloatArray(200) { idx ->
      when (idx) {
        in 20..60 -> 0.25f
        in 120..170 -> 0.30f
        else -> 0.001f
      }
    }
    val words = listOf("Stop", "guessing", "captions")
    val aligned =
      com.example.captionstudio.engine.AudioSpeechAligner.alignWordsToAcousticEnvelope(
        words = words,
        envelope10ms = envelope,
        windowStartMs = 0L,
        windowEndMs = 2000L,
      )
    assertEquals(3, aligned.size)
    assertTrue(aligned.all { !it.isEstimated })
    assertTrue("First word should start near 200ms voiced onset", aligned[0].startMs >= 160L)
    assertTrue("Last word should end near 1700ms voiced offset", aligned[2].endMs <= 1760L)
    assertTrue("Chronological monotonicity", aligned[0].endMs <= aligned[1].startMs + 20L)
  }

  @Test
  fun `wav encoder produces valid RIFF WAVE PCM header`() {
    val pcm = ShortArray(1600) { (it % 100).toShort() }
    val wav = com.example.captionstudio.media.AudioExtractionService.encodeWavBytes(pcm)
    assertEquals(44 + pcm.size * 2, wav.size)
    assertEquals("RIFF", String(wav.copyOfRange(0, 4), Charsets.US_ASCII))
    assertEquals("WAVE", String(wav.copyOfRange(8, 12), Charsets.US_ASCII))
  }

  @Test
  fun `contact sheet visual archetypes select whisper, seam callout, and behind-subject depth`() {
    val preset = PresetCatalog.findById("editorial")
    val heroReport = VideoAnalysisReport(subjectCoverageFraction = 0.32f)
    val heroComps =
      LocalCompositionEngine.generateCompositions(
        transcript = "Why do many creators add.",
        wordTimings = emptyList(),
        totalDurationMs = 3000L,
        preset = preset,
        analysisReport = heroReport,
      )
    assertEquals(CompositionStyleVariant.HERO_STACK, heroComps.first().styleVariant)
    assertEquals(DepthLayer.BEHIND_SUBJECT, heroComps.first().depthLayer)
    assertTrue(heroComps.first().hasBehindSubjectElements())

    val whisperComps =
      LocalCompositionEngine.generateCompositions(
        transcript = "On mute.",
        wordTimings = emptyList(),
        totalDurationMs = 1800L,
        preset = preset,
        analysisReport = heroReport,
      )
    assertEquals(CompositionStyleVariant.WHISPER_NEGATIVE_SPACE, whisperComps.first().styleVariant)
    assertEquals(DepthLayer.IN_FRONT, whisperComps.first().depthLayer)

    val splitReport = VideoAnalysisReport(isSplitScreenDetected = true)
    val seamComps =
      LocalCompositionEngine.generateCompositions(
        transcript = "Click Dynamic Captions.",
        wordTimings = emptyList(),
        totalDurationMs = 2000L,
        preset = preset,
        analysisReport = splitReport,
      )
    assertEquals(CompositionStyleVariant.SEAM_CALLOUT, seamComps.first().styleVariant)
    assertEquals(SafeRegion.SEAM_CENTER, seamComps.first().region)
  }

  @Test
  fun `offline vosk model manager maps language tags cleanly`() {
    assertEquals("en-us", OfflineVoskModelManager.packForLanguage("en-US").languagePrefix)
    assertEquals("en-in", OfflineVoskModelManager.packForLanguage("en-IN").languagePrefix)
    assertEquals("hi", OfflineVoskModelManager.packForLanguage("hi-IN").languagePrefix)
    assertEquals("es", OfflineVoskModelManager.packForLanguage("es-ES").languagePrefix)
    assertEquals("fr", OfflineVoskModelManager.packForLanguage("fr-FR").languagePrefix)
    assertEquals("de", OfflineVoskModelManager.packForLanguage("de-DE").languagePrefix)
  }

  @Test
  fun `vosk service groups words into timed editor segments across speech pauses`() {
    val words =
      listOf(
        WordTiming("Why", 100L, 260L, isEstimated = false),
        WordTiming("creators", 280L, 580L, isEstimated = false),
        // 520ms natural pause triggers segment break
        WordTiming("especially", 1100L, 1450L, isEstimated = false),
        WordTiming("text", 1470L, 1750L, isEstimated = false),
        WordTiming("like", 1770L, 1980L, isEstimated = false),
        WordTiming("this", 2000L, 2340L, isEstimated = false),
      )
    val confs = listOf(0.95f, 0.91f, 0.88f, 0.94f, 0.90f, 0.92f)
    val segments = VoskVideoTranscriptionService.segmentRecognizedWords(words, confs)

    assertEquals(2, segments.size)
    assertEquals("Why creators", segments[0].text)
    assertEquals(100L, segments[0].startMs)
    assertEquals(580L, segments[0].endMs)
    assertEquals(0.93f, segments[0].averageConfidence, 0.01f)

    assertEquals("especially text like this", segments[1].text)
    assertEquals(1100L, segments[1].startMs)
    assertEquals(2340L, segments[1].endMs)

    val comps =
      LocalCompositionEngine.generateCompositionsFromPhrases(
        phrases = segments.map { it.words },
        preset = PresetCatalog.findById("editorial"),
        analysisReport = VideoAnalysisReport(),
      )
    assertEquals(2, comps.size)
    assertEquals(100L, comps[0].startMs)
    assertEquals(1100L, comps[1].startMs)
  }

  @Test
  fun `vosk service boosts quiet 16kHz PCM without clipping`() {
    val quietPcm = ShortArray(400) { idx -> if (idx % 2 == 0) 900 else -900 }
    val normalized = VoskVideoTranscriptionService.normalizeQuietPcmIfNeeded(quietPcm, 0, quietPcm.size)
    val maxSample = normalized.maxOf { kotlin.math.abs(it.toInt()) }
    assertTrue("Quiet PCM peak should be boosted above 4000, was $maxSample", maxSample in 4500..26000)

    val loudPcm = ShortArray(400) { idx -> if (idx % 2 == 0) 18000 else -18000 }
    val unchanged = VoskVideoTranscriptionService.normalizeQuietPcmIfNeeded(loudPcm, 0, loudPcm.size)
    assertEquals(18000.toShort(), unchanged[0])
  }

  @Test
  fun `validator clamps layer motion speed and intensity while preserving motion effect`() {
    val comp =
      CaptionComposition(
        id = "c_motion",
        startMs = 0L,
        endMs = 2000L,
        originalText = "Bounce",
        elements =
          listOf(
            CompositionElement(
              id = "e_motion",
              text = "Bounce",
              role = FontRole.HEAVY,
              hierarchyLevel = HierarchyLevel.DOMINANT,
              fontFamilyId = "anton",
              fontSizeSp = 40f,
              motionEffect = com.example.captionstudio.domain.model.LayerMotionEffect.BOUNCE,
              motionSpeed = 9.5f,
              motionIntensity = 0.01f,
            )
          ),
      )
    val fixed = CompositionValidator.validateAndRepair(null, listOf(comp))
    val el = fixed.first().elements.first()
    assertEquals(com.example.captionstudio.domain.model.LayerMotionEffect.BOUNCE, el.motionEffect)
    assertEquals(2.2f, el.motionSpeed, 0.001f)
    assertEquals(0.25f, el.motionIntensity, 0.001f)
  }

  @Test
  fun `style deep link codec round-trips custom cloned preset cleanly`() {
    val original =
      com.example.captionstudio.domain.model.PresetDefinition(
        id = "cloned_viral01",
        name = "VIRAL PUNCH",
        tagline = "Cloned • anton + Pop",
        description = "High contrast kinetic style",
        heavyFontId = "anton",
        supportingFontId = "plus_jakarta_sans",
        expressiveFontId = "caveat",
        primaryColorHex = "#FFFFFF",
        dominantColorHex = "#FFE14D",
        expressiveColorHex = "#FF7A2F",
        dramaticColorHex = "#FF3B30",
        defaultEntryAnim = com.example.captionstudio.domain.model.EntryAnimationType.FADE_POP,
        defaultExitAnim = com.example.captionstudio.domain.model.ExitAnimationType.FADE_DROP,
        defaultEasing = com.example.captionstudio.domain.model.AnimationEasingType.SPRING_OVERSHOOT,
        entryDurationMs = 135,
        exitDurationMs = 110,
        dominantScaleMultiplier = 1.14f,
        expressiveScaleMultiplier = 1.08f,
        shadowRadiusFactor = 1.1f,
        preferredRegions = listOf(SafeRegion.LOWER_CENTER, SafeRegion.CENTER),
        sampleSentence = "Why do creators win",
        strokeWidthFactor = 0.085f,
        strokeColorHex = "#111116",
        backgroundPillColorHex = "#0E0E14",
        backgroundPillAlpha = 0.80f,
        preferBehindSubject = true,
        preferUppercase = true,
        isCustomCloned = true,
      )
    val link = com.example.captionstudio.presets.StyleDeepLinkCodec.encodeToDeepLink(original)
    assertTrue("Deep link must use captionstudio://style?d= scheme", link.startsWith("captionstudio://style?d="))

    val decoded = com.example.captionstudio.presets.StyleDeepLinkCodec.decodeFromDeepLink(link)
    assertTrue("Decoded preset should not be null", decoded != null)
    assertEquals(original.name, decoded!!.name)
    assertEquals(original.heavyFontId, decoded.heavyFontId)
    assertEquals(original.dominantColorHex, decoded.dominantColorHex)
    assertEquals(original.strokeWidthFactor, decoded.strokeWidthFactor, 0.001f)
    assertEquals(original.backgroundPillColorHex, decoded.backgroundPillColorHex)
    assertEquals(true, decoded.preferBehindSubject)
    assertEquals(true, decoded.isCustomCloned)

    val invalid = com.example.captionstudio.presets.StyleDeepLinkCodec.decodeFromDeepLink("https://invalid.example.com")
    assertEquals(null, invalid)
  }

  @Test
  fun `subject mask processor distinguishes static frames, normal speech, and hard scene cuts`() {
    val darkFrame = IntArray(256) { 0xFF141820.toInt() }
    val slightNoiseFrame = IntArray(256) { 0xFF161A22.toInt() }
    val fastMotionFrame = IntArray(256) { idx -> if (idx % 2 == 0) 0xFF42464E.toInt() else 0xFF141820.toInt() }
    val sceneCutFrame = IntArray(256) { 0xFFE8EEF5.toInt() }

    val staticSad = com.example.captionstudio.media.SubjectMaskProcessor.frameDifferenceSad(darkFrame, slightNoiseFrame)
    val motionSad = com.example.captionstudio.media.SubjectMaskProcessor.frameDifferenceSad(darkFrame, fastMotionFrame)
    val cutSad = com.example.captionstudio.media.SubjectMaskProcessor.frameDifferenceSad(darkFrame, sceneCutFrame)

    assertTrue("Static frame SAD should be < 4.2f, was $staticSad", staticSad < 4.2f)
    assertTrue("Motion frame SAD should be in 15.5..42.0, was $motionSad", motionSad in 15.5f..42.0f)
    assertTrue("Scene cut SAD should be >= 42.0f, was $cutSad", cutSad >= 42.0f)
  }

  @Test
  fun `reference style cloner synthesizes spring pop, behind-subject depth, and font archetype`() {
    val obs =
      listOf(
        com.example.captionstudio.media.ReferenceStyleCloner.TrackedCaptionObservation(
          timeMs = 200L,
          text = "STOP SCROLLING",
          normalizedCenterX = 0.50f,
          normalizedCenterY = 0.72f,
          normalizedHeight = 0.070f,
          charAspectRatio = 0.40f,
          wordCount = 2,
          isAllCaps = true,
          primaryColorInt = 0xFFFFFFFF.toInt(),
          accentColorInt = 0xFFFFE14D.toInt(),
          hasDarkStroke = true,
          strokeColorInt = 0xFF0A0A0D.toInt(),
          pillBackgroundColorInt = 0xFF121218.toInt(),
          fillDensity = 0.38f,
          isSceneCutFrame = false,
        ),
        com.example.captionstudio.media.ReferenceStyleCloner.TrackedCaptionObservation(
          timeMs = 270L,
          text = "STOP SCROLLING",
          normalizedCenterX = 0.50f,
          normalizedCenterY = 0.72f,
          normalizedHeight = 0.088f, // Spring overshoot peak
          charAspectRatio = 0.40f,
          wordCount = 2,
          isAllCaps = true,
          primaryColorInt = 0xFFFFFFFF.toInt(),
          accentColorInt = 0xFFFFE14D.toInt(),
          hasDarkStroke = true,
          strokeColorInt = 0xFF0A0A0D.toInt(),
          pillBackgroundColorInt = 0xFF121218.toInt(),
          fillDensity = 0.38f,
          isSceneCutFrame = false,
        ),
        com.example.captionstudio.media.ReferenceStyleCloner.TrackedCaptionObservation(
          timeMs = 340L,
          text = "STOP SCROLLING",
          normalizedCenterX = 0.50f,
          normalizedCenterY = 0.72f,
          normalizedHeight = 0.080f, // Settles back down -> overshoot detected
          charAspectRatio = 0.40f,
          wordCount = 2,
          isAllCaps = true,
          primaryColorInt = 0xFFFFFFFF.toInt(),
          accentColorInt = 0xFFFFE14D.toInt(),
          hasDarkStroke = true,
          strokeColorInt = 0xFF0A0A0D.toInt(),
          pillBackgroundColorInt = 0xFF121218.toInt(),
          fillDensity = 0.38f,
          isSceneCutFrame = false,
        ),
      )

    val report =
      com.example.captionstudio.media.ReferenceStyleCloner.synthesizePresetFromObservations(
        observations = obs,
        framesScanned = 36,
        sceneCuts = 2,
        behindSubjectDetected = true,
        customStyleName = "MY VIRAL HOOK",
      )

    assertEquals("MY VIRAL HOOK", report.preset.name)
    assertEquals(com.example.captionstudio.domain.model.EntryAnimationType.FADE_POP, report.preset.defaultEntryAnim)
    assertEquals(com.example.captionstudio.domain.model.AnimationEasingType.SPRING_OVERSHOOT, report.preset.defaultEasing)
    assertEquals("anton", report.preset.heavyFontId)
    assertTrue(report.alternativeHeavyFontIds.size >= 3)
    assertEquals(true, report.preset.preferBehindSubject)
    assertTrue(report.preset.strokeWidthFactor > 0f)
    assertTrue(report.preset.backgroundPillColorHex != null)
  }

  @Test
  fun `frame-aware offline engine enforces shot-level stability and vetoes face zones`() {
    val preset = PresetCatalog.findById("editorial")
    val shot1 =
      com.example.captionstudio.domain.model.ShotVisualAnalysis(
        startMs = 0L,
        endMs = 4000L,
        headVetoRect = com.example.captionstudio.domain.model.NormalizedRect(0.30f, 0.14f, 0.70f, 0.44f),
        subjectCentroidX = 0.50f,
        subjectCentroidY = 0.45f,
        subjectCoverage = 0.24f,
        zoneMetrics =
          mapOf(
            SafeRegion.UPPER_CENTER to
              com.example.captionstudio.domain.model.ZoneVisualMetrics(
                edgeClutter = 28f,
                meanLuminance = 130f,
                isBlockedByFace = true,
                isBlockedBySubject = true,
              ),
            SafeRegion.LOWER_CENTER to
              com.example.captionstudio.domain.model.ZoneVisualMetrics(
                edgeClutter = 8f,
                meanLuminance = 52f,
                isBlockedByFace = false,
                isBlockedBySubject = false,
              ),
            SafeRegion.CENTER to
              com.example.captionstudio.domain.model.ZoneVisualMetrics(
                edgeClutter = 20f,
                meanLuminance = 95f,
                isBlockedByFace = false,
                isBlockedBySubject = true,
              ),
          ),
        preferredZone = SafeRegion.LOWER_CENTER,
      )
    val shot2 =
      com.example.captionstudio.domain.model.ShotVisualAnalysis(
        startMs = 4000L,
        endMs = 8000L,
        headVetoRect = com.example.captionstudio.domain.model.NormalizedRect(0.30f, 0.56f, 0.70f, 0.86f),
        subjectCentroidX = 0.50f,
        subjectCentroidY = 0.70f,
        subjectCoverage = 0.22f,
        zoneMetrics =
          mapOf(
            SafeRegion.UPPER_CENTER to
              com.example.captionstudio.domain.model.ZoneVisualMetrics(
                edgeClutter = 6f,
                meanLuminance = 44f,
                isBlockedByFace = false,
                isBlockedBySubject = false,
              ),
            SafeRegion.LOWER_CENTER to
              com.example.captionstudio.domain.model.ZoneVisualMetrics(
                edgeClutter = 32f,
                meanLuminance = 140f,
                isBlockedByFace = true,
                isBlockedBySubject = true,
              ),
          ),
        preferredZone = SafeRegion.UPPER_CENTER,
      )
    val report =
      VideoAnalysisReport(
        frameCountSampled = 8,
        sceneChangeTimestampsMs = listOf(4000L),
        subjectCoverageFraction = 0.23f,
        shotAnalyses = listOf(shot1, shot2),
      )

    val phrases =
      listOf(
        listOf(WordTiming("First", 200L, 600L), WordTiming("phrase", 650L, 1200L)),
        listOf(WordTiming("Second", 1500L, 2100L), WordTiming("line", 2150L, 2800L)),
        listOf(WordTiming("After", 4400L, 5100L), WordTiming("cut", 5150L, 6000L)),
      )
    val comps = LocalCompositionEngine.generateCompositionsFromPhrases(phrases, preset, report)
    assertEquals(3, comps.size)
    // Both phrases in shot 1 stay anchored in the clean Lower Center (never Upper Center face veto)
    assertEquals(SafeRegion.LOWER_CENTER, comps[0].region)
    assertEquals(SafeRegion.LOWER_CENTER, comps[1].region)
    // After the scene cut at 4000ms, shot 2 shifts intentionally to clean Upper Center
    assertEquals(SafeRegion.UPPER_CENTER, comps[2].region)
  }

  @Test
  fun `auto readability guard boosts contrast on bright busy background and leaves clean dark zones untouched`() {
    val baseEl =
      CompositionElement(
        id = "e1",
        text = "Clarity",
        role = FontRole.HEAVY,
        hierarchyLevel = HierarchyLevel.DOMINANT,
        fontFamilyId = "anton",
        fontSizeSp = 40f,
        strokeWidthFactor = 0f,
        shadowIntensity = 0.9f,
        backgroundColorHex = null,
      )

    val cleanDark =
      com.example.captionstudio.domain.model.ZoneVisualMetrics(
        edgeClutter = 6f,
        meanLuminance = 45f,
      )
    val untouched = LocalCompositionEngine.applyAutoReadabilityGuard(listOf(baseEl), cleanDark)
    assertEquals(0f, untouched[0].strokeWidthFactor, 0.001f)
    assertEquals(null, untouched[0].backgroundColorHex)

    val brightBusy =
      com.example.captionstudio.domain.model.ZoneVisualMetrics(
        edgeClutter = 34f,
        meanLuminance = 205f,
      )
    val guarded = LocalCompositionEngine.applyAutoReadabilityGuard(listOf(baseEl), brightBusy)
    assertTrue("Expected boosted outline stroke", guarded[0].strokeWidthFactor >= 1.6f)
    assertEquals("#101016", guarded[0].strokeColorHex)
    assertTrue("Expected boosted shadow intensity", guarded[0].shadowIntensity >= 1.6f)
    assertEquals("#121218", guarded[0].backgroundColorHex)
  }

  @Test
  fun `subject mask processor computes centroid and upper head veto rect from probability map`() {
    val w = 40
    val h = 60
    val probs = FloatArray(w * h)
    // Place a centered standing speaker silhouette in x=12..27, y=10..55
    for (y in 10..55) {
      for (x in 12..27) {
        probs[y * w + x] = 0.92f
      }
    }
    val metrics =
      com.example.captionstudio.media.SubjectMaskProcessor.computeMetricsFromProbabilityMap(
        probs = probs,
        width = w,
        height = h,
      )
    assertTrue("Coverage should be meaningful", metrics.coverageFraction > 0.25f)
    assertEquals(0.49f, metrics.centroidX, 0.06f)
    assertTrue("Head veto rect must be computed", metrics.headVetoRect != null)
    val head = metrics.headVetoRect!!
    assertTrue("Head veto top should start near top of silhouette", head.top <= 0.22f)
    assertTrue("Head veto bottom should cover eyes/mouth zone", head.bottom >= 0.35f)
  }

  @Test
  fun `gemini client builds resilient fallback model cascade and flags 503 as transient busy`() {
    val cascade =
      com.example.captionstudio.ai.GeminiApiClient.buildFallbackModelCascade("models/gemini-2.5-flash")
    assertEquals("models/gemini-2.5-flash", cascade.first())
    assertTrue("Cascade should contain backup models", cascade.size >= 3)
    assertTrue(
      "Cascade should include fast lite backup model",
      cascade.any { it.contains("gemini-2.5-flash-lite") || it.contains("gemini-2.0-flash") },
    )
    assertEquals(cascade.distinct().size, cascade.size)

    val err503 = com.example.captionstudio.ai.GeminiApiClient.mapHttpError(503, "Service Unavailable")
    assertEquals(503, err503.httpCode)
    assertTrue("HTTP 503 must be marked transient busy", err503.isTransientBusy)

    val err429 = com.example.captionstudio.ai.GeminiApiClient.mapHttpError(429, "Quota exceeded")
    assertEquals(429, err429.httpCode)
    assertTrue("HTTP 429 must be marked transient busy", err429.isTransientBusy)

    val err401 = com.example.captionstudio.ai.GeminiApiClient.mapHttpError(401, "Invalid key")
    assertEquals(401, err401.httpCode)
    assertTrue("HTTP 401 should not be transient busy", !err401.isTransientBusy)
  }

  @Test
  fun `validator scales down oversized multi-word phrase containing a long token`() {
    val comp =
      CaptionComposition(
        id = "c_long_word",
        startMs = 0L,
        endMs = 2000L,
        originalText = "Extraordinary cinematography",
        elements =
          listOf(
            CompositionElement(
              id = "e_long",
              text = "Extraordinary cinematography",
              role = FontRole.HEAVY,
              hierarchyLevel = HierarchyLevel.DOMINANT,
              fontFamilyId = "anton",
              fontSizeSp = 64f,
              uppercase = true,
            )
          ),
      )
    val fixed = CompositionValidator.validateAndRepair(null, listOf(comp))
    val scaledSize = fixed.first().elements.first().fontSizeSp
    assertTrue("Expected validator to shrink oversized 14-char token below 50sp, was $scaledSize", scaledSize < 50f)
  }

  @Test
  fun `active-word karaoke timing resolves exact word mappings and interpolates manual scripts`() {
    val mappedComp =
      CaptionComposition(
        id = "c_karaoke",
        startMs = 1000L,
        endMs = 2500L,
        originalText = "Watch every word",
        wordMappings =
          listOf(
            WordTiming("Watch", 1000L, 1400L, isEstimated = false),
            WordTiming("every", 1450L, 1950L, isEstimated = false),
            WordTiming("word", 2000L, 2500L, isEstimated = false),
          ),
        elements = emptyList(),
      )
    val w0At1700 =
      com.example.captionstudio.engine.KineticCompositionRenderer.resolveActiveWordTiming(
        comp = mappedComp,
        globalWordOrder = 0,
        mappedWordIndex = 0,
        totalWordsInElement = 3,
        positionMs = 1700L,
      )
    val w1At1700 =
      com.example.captionstudio.engine.KineticCompositionRenderer.resolveActiveWordTiming(
        comp = mappedComp,
        globalWordOrder = 1,
        mappedWordIndex = 1,
        totalWordsInElement = 3,
        positionMs = 1700L,
      )
    val w2At1700 =
      com.example.captionstudio.engine.KineticCompositionRenderer.resolveActiveWordTiming(
        comp = mappedComp,
        globalWordOrder = 2,
        mappedWordIndex = 2,
        totalWordsInElement = 3,
        positionMs = 1700L,
      )
    assertTrue("First word should already be spoken at 1700ms", w0At1700.isSpokenAlready && !w0At1700.isActive)
    assertTrue("Middle word should be active at 1700ms", w1At1700.isActive && w1At1700.progress01 in 0.45f..0.55f)
    assertTrue("Third word should not be active yet", !w2At1700.isActive && !w2At1700.isSpokenAlready)

    // Fallback proportional interpolation when wordMappings is empty
    val unmappedComp = mappedComp.copy(wordMappings = emptyList())
    val fallbackMid =
      com.example.captionstudio.engine.KineticCompositionRenderer.resolveActiveWordTiming(
        comp = unmappedComp,
        globalWordOrder = 1,
        mappedWordIndex = null,
        totalWordsInElement = 3,
        positionMs = 1750L,
      )
    assertTrue("Interpolated middle word should be active at midpoint 1750ms", fallbackMid.isActive)
  }

  @Test
  fun `magnetic snap locks cleanly to center axes and platform safe zones`() {
    val centerSnap =
      com.example.captionstudio.ui.components.computeMagneticSnap(
        rawX = 0.512f,
        rawY = 0.488f,
        safeZonePlatform = com.example.captionstudio.domain.model.SafeZonePlatform.NONE,
      )
    assertEquals(0.50f, centerSnap.x, 0.0001f)
    assertEquals(0.50f, centerSnap.y, 0.0001f)
    assertEquals(0.50f, centerSnap.snappedVerticalX)
    assertEquals(0.50f, centerSnap.snappedHorizontalY)

    val tiktokSafeSnap =
      com.example.captionstudio.ui.components.computeMagneticSnap(
        rawX = 0.41f,
        rawY = 0.745f,
        safeZonePlatform = com.example.captionstudio.domain.model.SafeZonePlatform.TIKTOK,
      )
    assertEquals(0.42f, tiktokSafeSnap.x, 0.0001f)
    assertEquals(0.74f, tiktokSafeSnap.y, 0.0001f)

    val freePos =
      com.example.captionstudio.ui.components.computeMagneticSnap(
        rawX = 0.62f,
        rawY = 0.36f,
        safeZonePlatform = com.example.captionstudio.domain.model.SafeZonePlatform.NONE,
      )
    assertEquals(0.62f, freePos.x, 0.0001f)
    assertEquals(0.36f, freePos.y, 0.0001f)
    assertEquals(null, freePos.snappedVerticalX)
    assertEquals(null, freePos.snappedHorizontalY)
  }

  @Test
  fun `visual timeline trim clamp enforces min duration and prevents neighbor overlap`() {
    val (clampedStart, clampedEnd) =
      com.example.captionstudio.ui.components.clampTrimRange(
        proposedStartMs = 900L,
        proposedEndMs = 4200L,
        totalDurationMs = 8000L,
        prevCompEndMs = 1500L,
        nextCompStartMs = 3500L,
        minDurationMs = 300L,
      )
    assertEquals(1500L, clampedStart)
    assertEquals(3500L, clampedEnd)

    val (minStart, minEnd) =
      com.example.captionstudio.ui.components.clampTrimRange(
        proposedStartMs = 2000L,
        proposedEndMs = 2050L,
        totalDurationMs = 8000L,
        minDurationMs = 300L,
      )
    assertTrue("Clamped block must preserve at least 300ms duration", minEnd - minStart >= 300L)
  }

  @Test
  fun `batch hook pacing template distinguishes opening 0-3s hook from body captions`() {
    val phrases =
      listOf(
        listOf(WordTiming("Stop", 100L, 500L), WordTiming("scrolling", 520L, 1200L), WordTiming("now", 1220L, 1800L)),
        listOf(WordTiming("Here", 3200L, 3600L), WordTiming("is", 3620L, 3900L), WordTiming("the", 3920L, 4200L), WordTiming("secret", 4220L, 4900L)),
      )
    val base =
      LocalCompositionEngine.generateCompositionsFromPhrases(
        phrases = phrases,
        preset = PresetCatalog.findById("normal_classic_white"),
        analysisReport = VideoAnalysisReport(),
      )
    val pacing =
      LocalCompositionEngine.applyHookPacingTemplate(
        compositions = base,
        template = com.example.captionstudio.domain.model.HookPacingTemplate.VIRAL_HOOK_KARAOKE_BODY,
        activePreset = PresetCatalog.findById("normal_classic_white"),
        analysisReport = VideoAnalysisReport(),
      )
    assertEquals(1, pacing.hookCount)
    assertEquals(1, pacing.bodyCount)
    assertEquals(2, pacing.compositions.size)
    // Hook (<3000ms) gets high-impact larger typography than clean body (>=3000ms)
    val hookMaxSp = pacing.compositions[0].elements.maxOf { it.fontSizeSp }
    val bodyMaxSp = pacing.compositions[1].elements.maxOf { it.fontSizeSp }
    assertTrue("Hook font ($hookMaxSp) should punch larger than body ($bodyMaxSp)", hookMaxSp > bodyMaxSp)
    assertTrue(
      "Body caption in Viral Hook + Karaoke should enable active-word karaoke",
      pacing.compositions[1].elements.any {
        it.karaokeMode != com.example.captionstudio.domain.model.KaraokeHighlightMode.OFF
      },
    )

    val reset =
      LocalCompositionEngine.applyHookPacingTemplate(
        compositions = pacing.compositions,
        template = com.example.captionstudio.domain.model.HookPacingTemplate.UNIFORM_RESET,
        activePreset = PresetCatalog.findById("normal_classic_white"),
        analysisReport = VideoAnalysisReport(),
      )
    assertEquals(0, reset.hookCount)
    assertEquals(2, reset.bodyCount)
  }

  @Test
  fun `normal preset generates clean single-block subtitle with karaoke mode intact`() {
    val preset = PresetCatalog.findById("normal_hormozi_pop")
    assertEquals(com.example.captionstudio.domain.model.PresetCategory.NORMAL, preset.category)
    val comps =
      LocalCompositionEngine.generateCompositions(
        transcript = "Every single word pops right on beat.",
        wordTimings = emptyList(),
        totalDurationMs = 3000L,
        preset = preset,
        analysisReport = VideoAnalysisReport(),
      )
    assertTrue(comps.isNotEmpty())
    assertEquals(1, comps.first().elements.size)
    assertEquals(
      com.example.captionstudio.domain.model.KaraokeHighlightMode.SCALE_POP,
      comps.first().elements.first().karaokeMode,
    )
  }
}
