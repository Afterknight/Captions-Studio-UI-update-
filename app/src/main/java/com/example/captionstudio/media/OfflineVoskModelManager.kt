package com.example.captionstudio.media

import android.content.Context
import com.example.captionstudio.domain.model.WordTiming
import com.example.captionstudio.engine.AudioSpeechAligner
import com.example.captionstudio.engine.LocalCompositionEngine
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

data class OfflineVoicePackInfo(
  val id: String,
  val languagePrefix: String,
  val displayName: String,
  val approxSizeMb: Int,
  val downloadUrl: String,
)

data class OfflineVoicePackStatus(
  val pack: OfflineVoicePackInfo,
  val isInstalled: Boolean,
  val isDownloading: Boolean = false,
  val downloadProgress: Float = 0f,
  val errorMessage: String? = null,
)

/**
 * Manages embedded Kaldi/Vosk offline speech models (`minSdk 24+` — works on Android 7, 8, 9,
 * 10, 11, 12, and 13+ completely offline once cached) and executes fast multi-x word-timestamped
 * speech recognition directly on decoded 16kHz mono PCM audio.
 */
object OfflineVoskModelManager {

  val supportedPacks: List<OfflineVoicePackInfo> =
    listOf(
      OfflineVoicePackInfo(
        id = "vosk-model-small-en-us-0.15",
        languagePrefix = "en-us",
        displayName = "English (US)",
        approxSizeMb = 40,
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
      ),
      OfflineVoicePackInfo(
        id = "vosk-model-small-en-in-0.4",
        languagePrefix = "en-in",
        displayName = "English (India)",
        approxSizeMb = 36,
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip",
      ),
      OfflineVoicePackInfo(
        id = "vosk-model-small-hi-0.22",
        languagePrefix = "hi",
        displayName = "Hindi",
        approxSizeMb = 42,
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
      ),
      OfflineVoicePackInfo(
        id = "vosk-model-small-es-0.42",
        languagePrefix = "es",
        displayName = "Spanish",
        approxSizeMb = 39,
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip",
      ),
      OfflineVoicePackInfo(
        id = "vosk-model-small-fr-0.22",
        languagePrefix = "fr",
        displayName = "French",
        approxSizeMb = 41,
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
      ),
      OfflineVoicePackInfo(
        id = "vosk-model-small-de-0.15",
        languagePrefix = "de",
        displayName = "German",
        approxSizeMb = 45,
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip",
      ),
    )

  private val ipv4FirstDns =
    object : Dns {
      override fun lookup(hostname: String): List<InetAddress> {
        val resolved = Dns.SYSTEM.lookup(hostname)
        val v4 = resolved.filterIsInstance<Inet4Address>()
        val v6 = resolved.filterIsInstance<Inet6Address>()
        return (v4 + v6).ifEmpty { resolved }
      }
    }

  private val downloadClient =
    OkHttpClient.Builder()
      .dns(ipv4FirstDns)
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .retryOnConnectionFailure(true)
      .build()

  private val _downloadProgress = MutableStateFlow<Pair<String, Float>?>(null)
  val downloadProgress: StateFlow<Pair<String, Float>?> = _downloadProgress.asStateFlow()

  private var cachedModelId: String? = null
  private var cachedModel: Model? = null

  fun packForLanguage(languageTag: String): OfflineVoicePackInfo {
    val norm = languageTag.trim().lowercase()
    return supportedPacks.firstOrNull { norm == it.languagePrefix }
      ?: supportedPacks.firstOrNull { norm.startsWith(it.languagePrefix.substringBefore("-")) }
      ?: supportedPacks.first()
  }

  private fun modelsRoot(context: Context): File = File(context.filesDir, "vosk_models").apply { mkdirs() }

  fun getInstalledModelDir(context: Context, languageTag: String): File? {
    val preferred = packForLanguage(languageTag)
    val preferredDir = File(modelsRoot(context), preferred.id)
    if (isValidVoskModelDir(preferredDir)) return preferredDir

    // Fallback to any installed English pack when en-GB / en-IN / en-US is selected
    if (preferred.languagePrefix.startsWith("en")) {
      supportedPacks
        .filter { it.languagePrefix.startsWith("en") }
        .forEach { enPack ->
          val d = File(modelsRoot(context), enPack.id)
          if (isValidVoskModelDir(d)) return d
        }
    }
    return null
  }

  fun isPackInstalled(context: Context, packId: String): Boolean {
    return isValidVoskModelDir(File(modelsRoot(context), packId))
  }

  fun isAnyPackInstalled(context: Context, languageTag: String): Boolean {
    return getInstalledModelDir(context, languageTag) != null
  }

  fun deletePack(context: Context, packId: String) {
    if (cachedModelId == packId) {
      runCatching { cachedModel?.close() }
      cachedModel = null
      cachedModelId = null
    }
    File(modelsRoot(context), packId).deleteRecursively()
  }

  private fun isValidVoskModelDir(dir: File): Boolean {
    if (!dir.exists() || !dir.isDirectory) return false
    val marker = File(dir, "am/final.mdl")
    val confMarker = File(dir, "conf/model.conf")
    return marker.exists() || confMarker.exists()
  }

  suspend fun ensureOrDownloadPack(
    context: Context,
    pack: OfflineVoicePackInfo,
    onProgress: (Float) -> Unit = {},
  ): Result<File> =
    withContext(Dispatchers.IO) {
      val destDir = File(modelsRoot(context), pack.id)
      if (isValidVoskModelDir(destDir)) {
        return@withContext Result.success(destDir)
      }

      val tempZip = File(context.cacheDir, "${pack.id}.zip.part")
      val stagingDir = File(modelsRoot(context), "${pack.id}_staging")
      runCatching {
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()

        val req = Request.Builder().url(pack.downloadUrl).get().build()
        downloadClient.newCall(req).execute().use { resp ->
          check(resp.isSuccessful) { "Download HTTP ${resp.code}" }
          val body = resp.body ?: error("Empty response")
          val totalBytes = body.contentLength().coerceAtLeast(1L)
          var downloaded = 0L

          FileOutputStream(tempZip).use { out ->
            body.byteStream().use { input ->
              val buf = ByteArray(16 * 1024)
              while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                out.write(buf, 0, read)
                downloaded += read
                val p = (downloaded.toFloat() / totalBytes).coerceIn(0f, 0.85f)
                _downloadProgress.value = pack.id to p
                onProgress(p)
              }
            }
          }
        }

        _downloadProgress.value = pack.id to 0.88f
        onProgress(0.88f)

        // Extract safely preventing Zip Slip
        val stagingCanonical = stagingDir.canonicalPath + File.separator
        ZipInputStream(BufferedInputStream(tempZip.inputStream())).use { zis ->
          var entry = zis.nextEntry
          val buffer = ByteArray(16 * 1024)
          while (entry != null) {
            val outFile = File(stagingDir, entry.name)
            if (!outFile.canonicalPath.startsWith(stagingCanonical)) {
              throw SecurityException("Blocked unsafe ZIP entry")
            }
            if (entry.isDirectory) {
              outFile.mkdirs()
            } else {
              outFile.parentFile?.mkdirs()
              FileOutputStream(outFile).use { fos ->
                while (true) {
                  val count = zis.read(buffer)
                  if (count <= 0) break
                  fos.write(buffer, 0, count)
                }
              }
            }
            zis.closeEntry()
            entry = zis.nextEntry
          }
        }

        // Unwrap nested root directory inside the Vosk zip (e.g. vosk-model-small-en-us-0.15/*)
        val innerRoot =
          stagingDir.listFiles()?.firstOrNull { it.isDirectory && isValidVoskModelDir(it) }
            ?: stagingDir.takeIf { isValidVoskModelDir(it) }
            ?: error("Downloaded archive did not contain valid Vosk model files.")

        destDir.deleteRecursively()
        check(innerRoot.renameTo(destDir)) { "Could not finalize model directory." }
        stagingDir.deleteRecursively()
        tempZip.delete()
        _downloadProgress.value = null
        onProgress(1f)
        destDir
      }.onFailure {
        stagingDir.deleteRecursively()
        tempZip.delete()
        _downloadProgress.value = null
      }
    }

  fun getOrLoadCachedModel(modelDir: File): Model {
    return synchronized(this) {
      val current = cachedModel
      if (current != null && cachedModelId == modelDir.name) {
        current
      } else {
        runCatching { cachedModel?.close() }
        Model(modelDir.absolutePath).also {
          cachedModel = it
          cachedModelId = modelDir.name
        }
      }
    }
  }

  /**
   * Runs fast offline Kaldi ASR directly on 16kHz PCM across all [SpeechSegment] intervals
   * with native word start/end timestamps + 10ms acoustic onset snapping.
   */
  suspend fun transcribePcm16k(
    context: Context,
    modelDir: File,
    pcmMono16k: ShortArray,
    segments: List<SpeechSegment>,
    envelope10ms: FloatArray,
    onProgress: (Float, String) -> Unit = { _, _ -> },
  ): Result<Pair<String, List<WordTiming>>> =
    withContext(Dispatchers.Default) {
      runCatching {
        val model = getOrLoadCachedModel(modelDir)

        val allWords = mutableListOf<WordTiming>()
        val transcriptChunks = mutableListOf<String>()

        for ((idx, seg) in segments.withIndex()) {
          val p = idx.toFloat() / max(1, segments.size)
          onProgress(p, "Offline ASR decoding segment ${idx + 1}/${segments.size}...")

          val recognizer = Recognizer(model, 16000f)
          try {
            recognizer.setWords(true)

            val startSample = ((seg.startMs * 16000L) / 1000L).toInt().coerceIn(0, pcmMono16k.size)
            val endSample = ((seg.endMs * 16000L) / 1000L).toInt().coerceIn(startSample, pcmMono16k.size)
            val chunkSamples = 4096
            val byteBuf = ByteBuffer.allocate(chunkSamples * 2).order(ByteOrder.LITTLE_ENDIAN)

            var cursor = startSample
            while (cursor < endSample) {
              val count = min(chunkSamples, endSample - cursor)
              byteBuf.clear()
              for (i in 0 until count) {
                byteBuf.putShort(pcmMono16k[cursor + i])
              }
              if (recognizer.acceptWaveForm(byteBuf.array(), count * 2)) {
                parseVoskResultJson(recognizer.result, seg.startMs, allWords, transcriptChunks)
              }
              cursor += count
            }
            // Append 300ms silence tail so final word probabilities flush cleanly
            val silence = ByteArray(4800 * 2)
            recognizer.acceptWaveForm(silence, silence.size)
            parseVoskResultJson(recognizer.finalResult, seg.startMs, allWords, transcriptChunks)
          } finally {
            runCatching { recognizer.close() }
          }
        }

        onProgress(0.95f, "Snapping word timings to 10ms audio onsets...")
        val refined =
          if (allWords.isNotEmpty()) {
            AudioSpeechAligner.refineWordTimings(allWords, envelope10ms)
          } else {
            emptyList()
          }
        val fullTranscript =
          transcriptChunks.joinToString(" ").trim().ifBlank {
            refined.joinToString(" ") { it.word }
          }
        fullTranscript to refined
      }
    }

  private fun parseVoskResultJson(
    jsonStr: String?,
    segmentOffsetMs: Long,
    outWords: MutableList<WordTiming>,
    outTexts: MutableList<String>,
  ) {
    if (jsonStr.isNullOrBlank()) return
    val obj = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return
    val text = obj.optString("text", "").trim()
    if (text.isNotEmpty()) outTexts.add(text)

    val resultArr = obj.optJSONArray("result") ?: JSONArray()
    if (resultArr.length() > 0) {
      for (i in 0 until resultArr.length()) {
        val wObj = resultArr.optJSONObject(i) ?: continue
        val w = wObj.optString("word", "").trim()
        if (w.isEmpty()) continue
        val sMs = segmentOffsetMs + (wObj.optDouble("start", 0.0) * 1000.0).toLong()
        val eMs = segmentOffsetMs + (wObj.optDouble("end", 0.15) * 1000.0).toLong()
        outWords.add(
          WordTiming(
            word = w,
            startMs = sMs.coerceAtLeast(segmentOffsetMs),
            endMs = max(sMs + 75L, eMs),
            isEstimated = false,
            emphasisScore = LocalCompositionEngine.scoreWordSalience(w, outWords.size, max(1, resultArr.length())),
          )
        )
      }
    }
  }
}
