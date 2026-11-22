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
  fun `switching between all 8 presets preserves transcript and timings`() {
    val base =
      LocalCompositionEngine.generateCompositions(
        transcript = "Why do many creators add. Especially text like this.",
        wordTimings = emptyList(),
        totalDurationMs = 5000L,
        preset = PresetCatalog.findById("editorial"),
        analysisReport = VideoAnalysisReport(),
      )
    assertEquals(8, PresetCatalog.presets.size)
    PresetCatalog.presets.forEach { preset ->
      val restyled =
        PresetCatalog.applyPresetToCompositions(preset, base, VideoAnalysisReport())
      assertEquals(base.size, restyled.size)
      restyled.forEachIndexed { idx, comp ->
        assertEquals(base[idx].originalText, comp.originalText)
        assertEquals(base[idx].startMs, comp.startMs)
        assertEquals(base[idx].endMs, comp.endMs)
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
}
