package com.example.captionstudio.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureApiKeyStore(context: Context) {

  private val prefs =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun saveApiKey(rawKey: String): Boolean {
    val trimmed = rawKey.trim()
    if (trimmed.isEmpty()) return false
    return try {
      val secretKey = getOrCreateKey()
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.ENCRYPT_MODE, secretKey)
      val iv = cipher.iv
      val ciphertext = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
      val payload = ByteArray(1 + iv.size + ciphertext.size)
      payload[0] = iv.size.toByte()
      System.arraycopy(iv, 0, payload, 1, iv.size)
      System.arraycopy(ciphertext, 0, payload, 1 + iv.size, ciphertext.size)
      val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
      prefs.edit().putString(KEY_ENCRYPTED_API_KEY, encoded).apply()
      true
    } catch (_: Exception) {
      false
    }
  }

  fun getApiKey(): String? {
    val encoded = prefs.getString(KEY_ENCRYPTED_API_KEY, null) ?: return null
    return try {
      val payload = Base64.decode(encoded, Base64.NO_WRAP)
      if (payload.size < 14) return null
      val ivLen = payload[0].toInt() and 0xFF
      if (ivLen <= 0 || payload.size <= 1 + ivLen) return null
      val iv = payload.copyOfRange(1, 1 + ivLen)
      val ciphertext = payload.copyOfRange(1 + ivLen, payload.size)
      val secretKey = getExistingKey() ?: return null
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
      String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    } catch (_: Exception) {
      null
    }
  }

  fun hasApiKey(): Boolean {
    return !prefs.getString(KEY_ENCRYPTED_API_KEY, null).isNullOrEmpty()
  }

  fun removeApiKey() {
    prefs.edit().remove(KEY_ENCRYPTED_API_KEY).apply()
    try {
      val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
      ks.load(null)
      if (ks.containsAlias(KEY_ALIAS)) {
        ks.deleteEntry(KEY_ALIAS)
      }
    } catch (_: Exception) {
      // Safe to ignore if keystore entry wasn't initialized
    }
  }

  private fun getExistingKey(): SecretKey? {
    val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
    ks.load(null)
    return ks.getKey(KEY_ALIAS, null) as? SecretKey
  }

  private fun getOrCreateKey(): SecretKey {
    getExistingKey()?.let { return it }
    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec =
      KeyGenParameterSpec.Builder(
          KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  companion object {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "caption_studio_gemini_aes_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val PREFS_NAME = "caption_studio_secure_vault"
    private const val KEY_ENCRYPTED_API_KEY = "enc_gemini_key_v1"
  }
}
