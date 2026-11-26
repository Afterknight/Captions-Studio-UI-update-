package com.example.captionstudio.media

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

data class WhisperModelPack(
  val id: String,
  val fileName: String,
  val displayName: String,
  val shortLabel: String,
  val approxSizeMb: Int,
  val downloadUrl: String,
  val isMultilingual: Boolean,
)

data class WhisperDownloadProgress(
  val packId: String,
  val fraction: Float,
  val downloadedMb: Int,
  val totalMb: Int,
)

/**
 * Manages on-demand quantized Whisper Small (`q5_1`, ~190 MB) GGML model packs stored in
 * `context.filesDir/models/whisper/` with resumable HTTP Range downloads and immediate Cancel
 * support wired into `StudioTaskProgressPill`.
 */
object OfflineWhisperModelManager {

  private const val MIN_VALID_MODEL_BYTES = 50_000_000L // Quantized small q5_1 is ~190 MB

  val ENGLISH_PACK =
    WhisperModelPack(
      id = "ggml-small.en-q5_1",
      fileName = "ggml-small.en-q5_1.bin",
      displayName = "Whisper Small (English)",
      shortLabel = "EN",
      approxSizeMb = 190,
      downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en-q5_1.bin",
      isMultilingual = false,
    )

  val MULTILINGUAL_PACK =
    WhisperModelPack(
      id = "ggml-small-q5_1",
      fileName = "ggml-small-q5_1.bin",
      displayName = "Whisper Small (Multilingual)",
      shortLabel = "Multi",
      approxSizeMb = 190,
      downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
      isMultilingual = true,
    )

  val supportedPacks: List<WhisperModelPack> = listOf(ENGLISH_PACK, MULTILINGUAL_PACK)

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
      .followRedirects(true)
      .followSslRedirects(true)
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(90, TimeUnit.SECONDS)
      .retryOnConnectionFailure(true)
      .build()

  private val activeCallRef = AtomicReference<Call?>(null)

  private val _downloadProgress = MutableStateFlow<WhisperDownloadProgress?>(null)
  val downloadProgress: StateFlow<WhisperDownloadProgress?> = _downloadProgress.asStateFlow()

  /**
   * Selects the English-specialized `ggml-small.en-q5_1.bin` pack for English tags (`en`, `en-US`,
   * `en-GB`, `en-IN`, etc.) and the Multilingual `ggml-small-q5_1.bin` pack for Hindi, Hinglish,
   * Spanish, French, German, Japanese, auto-detect, and all other languages.
   */
  fun packForLanguage(languageTag: String): WhisperModelPack {
    val norm = languageTag.trim().lowercase()
    return if (norm == "en" || norm.startsWith("en-") || norm.startsWith("en_")) {
      ENGLISH_PACK
    } else {
      MULTILINGUAL_PACK
    }
  }

  /** Converts a BCP-47 tag (e.g. `en-US`, `hi-IN`) into a Whisper ISO language code (`en`, `hi`, `auto`). */
  fun whisperLanguageCode(languageTag: String, pack: WhisperModelPack): String {
    if (!pack.isMultilingual) return "en"
    val norm = languageTag.trim().lowercase()
    if (norm.isEmpty() || norm == "auto" || norm == "hinglish" || norm == "hi-en") {
      return "auto"
    }
    return norm.substringBefore("-").substringBefore("_").ifBlank { "auto" }
  }

  fun modelsRoot(context: Context): File =
    File(File(context.filesDir, "models"), "whisper").apply { mkdirs() }

  fun getInstalledModelFile(context: Context, languageTag: String): File? {
    val preferred = packForLanguage(languageTag)
    val preferredFile = File(modelsRoot(context), preferred.fileName)
    if (isValidWhisperModelFile(preferredFile)) return preferredFile

    // If user requested English and Multilingual is already cached locally, reuse it offline
    if (!preferred.isMultilingual) {
      val multiFile = File(modelsRoot(context), MULTILINGUAL_PACK.fileName)
      if (isValidWhisperModelFile(multiFile)) return multiFile
    }
    return null
  }

  fun isPackInstalled(context: Context, pack: WhisperModelPack): Boolean =
    isValidWhisperModelFile(File(modelsRoot(context), pack.fileName))

  fun deletePack(context: Context, pack: WhisperModelPack) {
    File(modelsRoot(context), pack.fileName).delete()
    File(modelsRoot(context), "${pack.fileName}.part").delete()
  }

  fun cancelActiveDownload() {
    activeCallRef.getAndSet(null)?.cancel()
    _downloadProgress.value = null
  }

  private fun isValidWhisperModelFile(file: File): Boolean =
    file.exists() && file.isFile && file.length() >= MIN_VALID_MODEL_BYTES

  /**
   * Ensures [pack] is available in `context.filesDir/models/whisper/`, resuming any partial
   * `.part` download via HTTP `Range` requests and reporting progress in real time.
   * On cancellation, preserves the `.part` file so the next attempt resumes where it left off.
   */
  suspend fun ensureOrDownloadPack(
    context: Context,
    pack: WhisperModelPack,
    onProgress: (fraction: Float, downloadedMb: Int, totalMb: Int) -> Unit = { _, _, _ -> },
  ): Result<File> =
    withContext(Dispatchers.IO) {
      val rootDir = modelsRoot(context)
      val destFile = File(rootDir, pack.fileName)
      if (isValidWhisperModelFile(destFile)) {
        onProgress(1f, pack.approxSizeMb, pack.approxSizeMb)
        return@withContext Result.success(destFile)
      }

      val partFile = File(rootDir, "${pack.fileName}.part")
      var resumedBytes = if (partFile.exists() && partFile.isFile) partFile.length() else 0L

      try {
        coroutineContext.ensureActive()

        val reqBuilder = Request.Builder().url(pack.downloadUrl).get()
        if (resumedBytes > 0L) {
          reqBuilder.header("Range", "bytes=$resumedBytes-")
        }

        val call = downloadClient.newCall(reqBuilder.build())
        activeCallRef.set(call)

        call.execute().use { resp ->
          // Handle 416 Range Not Satisfiable (either already complete or stale partial file)
          if (resp.code == 416) {
            if (partFile.length() >= MIN_VALID_MODEL_BYTES) {
              destFile.delete()
              if (partFile.renameTo(destFile) && isValidWhisperModelFile(destFile)) {
                _downloadProgress.value = null
                onProgress(1f, pack.approxSizeMb, pack.approxSizeMb)
                return@withContext Result.success(destFile)
              }
            }
            partFile.delete()
            resumedBytes = 0L
            throw IOException("Resumable range expired; retry download from start.")
          }

          if (!resp.isSuccessful) {
            throw IOException("Whisper model download failed (HTTP ${resp.code})")
          }

          val isPartialAppend = resp.code == 206 && resumedBytes > 0L
          if (!isPartialAppend) {
            resumedBytes = 0L
          }

          val body = resp.body ?: throw IOException("Empty Whisper model response")
          val contentLen = body.contentLength()
          val totalBytes =
            when {
              isPartialAppend && contentLen > 0L -> resumedBytes + contentLen
              contentLen > 0L -> contentLen
              else -> pack.approxSizeMb.toLong() * 1024L * 1024L
            }.coerceAtLeast(1L)

          var downloadedBytes = resumedBytes
          val totalMb = (totalBytes / (1024L * 1024L)).toInt().coerceAtLeast(1)
          var lastReportMs = 0L

          FileOutputStream(partFile, isPartialAppend).use { out ->
            body.byteStream().use { input ->
              val buffer = ByteArray(32 * 1024)
              while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read <= 0) break
                out.write(buffer, 0, read)
                downloadedBytes += read

                val now = System.currentTimeMillis()
                if (now - lastReportMs >= 120L || downloadedBytes >= totalBytes) {
                  lastReportMs = now
                  val frac = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                  val dlMb = (downloadedBytes / (1024L * 1024L)).toInt().coerceAtLeast(0)
                  _downloadProgress.value =
                    WhisperDownloadProgress(
                      packId = pack.id,
                      fraction = frac,
                      downloadedMb = dlMb,
                      totalMb = totalMb,
                    )
                  onProgress(frac, dlMb, totalMb)
                }
              }
              out.flush()
            }
          }
        }

        if (!isValidWhisperModelFile(partFile)) {
          partFile.delete()
          throw IOException("Downloaded Whisper model is incomplete or corrupted.")
        }

        destFile.delete()
        if (!partFile.renameTo(destFile)) {
          partFile.copyTo(destFile, overwrite = true)
          partFile.delete()
        }

        _downloadProgress.value = null
        val finalMb = (destFile.length() / (1024L * 1024L)).toInt().coerceAtLeast(1)
        onProgress(1f, finalMb, finalMb)
        Result.success(destFile)
      } catch (ce: CancellationException) {
        activeCallRef.getAndSet(null)?.cancel()
        _downloadProgress.value = null
        // Preserve .part file intentionally so next attempt resumes!
        throw ce
      } catch (t: Throwable) {
        activeCallRef.set(null)
        _downloadProgress.value = null
        Result.failure(t)
      } finally {
        activeCallRef.set(null)
      }
    }
}
