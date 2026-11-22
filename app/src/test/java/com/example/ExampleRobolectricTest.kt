package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.captionstudio.engine.CompositionValidator
import com.example.captionstudio.engine.KineticCompositionRenderer
import com.example.captionstudio.typography.FontRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name matches Caption Studio`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Caption Studio", appName)
  }

  @Test
  fun `all 19 bundled offline google fonts load cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals(19, FontRegistry.bundledFonts.size)
    FontRegistry.bundledFonts.forEach { font ->
      val tf = FontRegistry.getTypeface(context, font.id, 700, font.supportsItalic)
      assertNotNull("Typeface failed for ${font.id}", tf)
    }
  }

  @Test
  fun `renderer draws customized typography with outline, pill background, glow, shadow, and underline`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val renderer = KineticCompositionRenderer(context)
    val customComp =
      com.example.captionstudio.domain.model.CaptionComposition(
        id = "custom_typo_1",
        startMs = 0L,
        endMs = 2500L,
        originalText = "Kinetic Style",
        elements =
          listOf(
            com.example.captionstudio.domain.model.CompositionElement(
              id = "el_custom",
              text = "Kinetic Style",
              role = com.example.captionstudio.domain.model.FontRole.HEAVY,
              hierarchyLevel = com.example.captionstudio.domain.model.HierarchyLevel.DOMINANT,
              fontFamilyId = "syne",
              fontWeight = 800,
              isItalic = true,
              isUnderlined = true,
              uppercase = true,
              fontSizeSp = 38f,
              colorHex = com.example.captionstudio.domain.model.StudioPalette.CORAL,
              letterSpacingEm = 0.08f,
              lineHeightMultiplier = 1.25f,
              opacity = 0.92f,
              rotationDeg = -6f,
              strokeWidthFactor = 3.0f,
              strokeColorHex = com.example.captionstudio.domain.model.StudioPalette.WHITE,
              glowColorHex = com.example.captionstudio.domain.model.StudioPalette.YELLOW,
              glowIntensity = 1.4f,
              shadowEnabled = true,
              shadowIntensity = 1.2f,
              shadowColorHex = com.example.captionstudio.domain.model.StudioPalette.SHADOW,
              backgroundColorHex = com.example.captionstudio.domain.model.StudioPalette.CHARCOAL,
              backgroundAlpha = 0.80f,
              backgroundCornerRadiusDp = 14f,
            )
          ),
      )
    val validated = CompositionValidator.validateAndRepair(context, listOf(customComp))
    assertEquals("syne", validated.first().elements.first().fontFamilyId)
    assertEquals(com.example.captionstudio.domain.model.StudioPalette.CHARCOAL, validated.first().elements.first().backgroundColorHex)
    val bmp = android.graphics.Bitmap.createBitmap(540, 960, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    renderer.drawAtTime(canvas, 540, 960, validated, 450L)
    bmp.recycle()
  }

  @Test
  fun `renderer draws validated composition without error`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val renderer = KineticCompositionRenderer(context)
    val validated =
      CompositionValidator.validateAndRepair(
        context = context,
        compositions =
          com.example.captionstudio.engine.LocalCompositionEngine.generateCompositions(
            transcript = "But here's the big problem.",
            wordTimings = emptyList(),
            totalDurationMs = 3000L,
            preset = com.example.captionstudio.presets.PresetCatalog.findById("editorial"),
            analysisReport = com.example.captionstudio.domain.model.VideoAnalysisReport(),
          ),
      )
    assertTrue(validated.isNotEmpty())
    val bmp = android.graphics.Bitmap.createBitmap(540, 960, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    renderer.drawAtTime(canvas, 540, 960, validated, validated.first().startMs + 100L)
    bmp.recycle()
  }

  @Test
  fun `renderer draws behind-subject composition cleanly with feathered depth mask`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val renderer = KineticCompositionRenderer(context)
    val validated =
      CompositionValidator.validateAndRepair(
        context = context,
        compositions =
          com.example.captionstudio.engine.LocalCompositionEngine.generateCompositions(
            transcript = "Why do many creators add crazy text.",
            wordTimings = emptyList(),
            totalDurationMs = 3000L,
            preset = com.example.captionstudio.presets.PresetCatalog.findById("creator"),
            analysisReport = com.example.captionstudio.domain.model.VideoAnalysisReport(
              subjectCoverageFraction = 0.32f,
            ),
          ),
      )
    assertTrue(validated.first().hasBehindSubjectElements())
    val maskBmp = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888).apply {
      eraseColor(android.graphics.Color.argb(200, 255, 255, 255))
    }
    val outBmp = android.graphics.Bitmap.createBitmap(540, 960, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(outBmp)
    renderer.drawWithSubjectMask(
      canvas = canvas,
      canvasWidth = 540,
      canvasHeight = 960,
      compositions = validated,
      positionMs = validated.first().startMs + 180L,
      subjectMaskBitmap = maskBmp,
      wrapInOffscreenLayer = true,
    )
    maskBmp.recycle()
    outBmp.recycle()
  }

  @Test
  fun `MainActivity launches cleanly`() {
    val activity = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup().get()
    assertNotNull(activity)
  }

  @Test
  fun `vosk transcription service parses Vosk JSON utterances with word timings and confidence`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val service = com.example.captionstudio.media.VoskVideoTranscriptionService(context)
    assertEquals(false, service.state.value.isTranscribing)

    val sampleVoskJson =
      """
      {
        "result": [
          {"conf": 0.96, "end": 0.48, "start": 0.12, "word": "especially"},
          {"conf": 0.89, "end": 0.84, "start": 0.50, "word": "text"},
          {"conf": 0.94, "end": 1.15, "start": 0.86, "word": "this"}
        ],
        "text": "especially text this"
      }
      """.trimIndent()

    val parsed =
      com.example.captionstudio.media.VoskVideoTranscriptionService.parseVoskJsonUtterance(
        jsonStr = sampleVoskJson,
        segmentOffsetMs = 1000L,
      )
    assertEquals(3, parsed.size)
    assertEquals("especially", parsed[0].timing.word)
    assertEquals(1120L, parsed[0].timing.startMs)
    assertEquals(1480L, parsed[0].timing.endMs)
    assertEquals(0.96f, parsed[0].confidence, 0.01f)

    val segments =
      com.example.captionstudio.media.VoskVideoTranscriptionService.segmentRecognizedWords(
        words = parsed.map { it.timing },
        confidences = parsed.map { it.confidence },
      )
    assertEquals(1, segments.size)
    assertEquals("especially text this", segments[0].text)
    assertEquals(1120L, segments[0].startMs)
    assertEquals(2150L, segments[0].endMs)
  }
}
