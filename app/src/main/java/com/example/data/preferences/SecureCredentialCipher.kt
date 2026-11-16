package com.example.data.preferences

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts sensitive user credentials (OpenRouter / Gemini API keys, GitHub PAT, HF token)
 * before persisting them in Jetpack DataStore Preferences.
 *
 * Uses Android Keystore AES-256-GCM when available on device, with a deterministic
 * app-scoped AES-256-GCM fallback in JVM / Robolectric test environments where
 * AndroidKeyStore is unavailable.
 */
class SecureCredentialCipher(private val context: Context) {

    private val secureRandom = SecureRandom()

    fun isHardwareKeystoreAvailable(): Boolean = runCatching {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        true
    }.getOrDefault(false)

    fun encrypt(plainText: String): String {
        val trimmed = plainText.trim()
        if (trimmed.isEmpty()) return ""
        if (isEncryptedPayload(trimmed)) return trimmed

        // Primary: Android Keystore AES/GCM/NoPadding
        runCatching {
            val secretKey = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(trimmed.toByteArray(StandardCharsets.UTF_8))
            return "$PREFIX_KEYSTORE${encodeBase64(iv)}:${encodeBase64(encryptedBytes)}"
        }

        // Fallback (Robolectric / JVM unit test environments without Keystore provider)
        return runCatching {
            val secretKey = getAppScopedFallbackKey()
            val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            val encryptedBytes = cipher.doFinal(trimmed.toByteArray(StandardCharsets.UTF_8))
            "$PREFIX_APP_FALLBACK${encodeBase64(iv)}:${encodeBase64(encryptedBytes)}"
        }.getOrElse { trimmed }
    }

    fun decrypt(storedValue: String): String {
        val trimmed = storedValue.trim()
        if (trimmed.isEmpty()) return ""

        return when {
            trimmed.startsWith(PREFIX_KEYSTORE) -> {
                val payload = trimmed.removePrefix(PREFIX_KEYSTORE)
                decryptWithPayload(payload) { getOrCreateKeystoreKey() }
            }
            trimmed.startsWith(PREFIX_APP_FALLBACK) -> {
                val payload = trimmed.removePrefix(PREFIX_APP_FALLBACK)
                decryptWithPayload(payload) { getAppScopedFallbackKey() }
            }
            else -> trimmed // Transparently read legacy plaintext values
        }
    }

    private inline fun decryptWithPayload(
        payload: String,
        keyProvider: () -> SecretKey
    ): String {
        val separatorIndex = payload.indexOf(':')
        if (separatorIndex <= 0 || separatorIndex >= payload.lastIndex) return ""
        return runCatching {
            val iv = decodeBase64(payload.substring(0, separatorIndex))
            val cipherBytes = decodeBase64(payload.substring(separatorIndex + 1))
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun getAppScopedFallbackKey(): SecretKey {
        val seedMaterial = "${context.packageName}:$KEYSTORE_ALIAS:aes256gcm_v1"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(seedMaterial.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    private fun encodeBase64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decodeBase64(text: String): ByteArray =
        Base64.decode(text, Base64.NO_WRAP)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "ai_app_studio_credentials_aes_v1"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128

        const val PREFIX_KEYSTORE = "enc:v1:"
        const val PREFIX_APP_FALLBACK = "enc:v1-app:"

        fun isEncryptedPayload(value: String): Boolean =
            value.startsWith(PREFIX_KEYSTORE) || value.startsWith(PREFIX_APP_FALLBACK)

        fun maskSecret(secret: String): String {
            val clean = secret.trim()
            if (clean.isEmpty()) return ""
            if (clean.length <= 8) return "•".repeat(clean.length)
            val prefixLen = if (clean.startsWith("sk-or-v1-")) 9 else 6
            val safePrefix = clean.take(prefixLen.coerceAtMost(clean.length - 4))
            val suffix = clean.takeLast(4)
            return "$safePrefix••••••$suffix"
        }
    }
}
