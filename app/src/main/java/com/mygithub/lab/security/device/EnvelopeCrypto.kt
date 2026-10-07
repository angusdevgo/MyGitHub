package com.mygithub.lab.security.device

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 跨端信封加解密 (与 Web Crypto API 的 AES-256-GCM 完全对齐)
 */
object EnvelopeCrypto {
    private const val SALT = "MyGitHub-Relay-Salt"
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256

    private fun deriveKey(secretStr: String): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(secretStr.toCharArray(), SALT.toByteArray(Charsets.UTF_8), ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    /**
     * AES-GCM 解密
     */
    fun decrypt(ciphertextBase64: String, nonceBase64: String, secret: String): String {
        val key = deriveKey(secret)
        val cipherBytes = Base64.decode(ciphertextBase64, Base64.DEFAULT)
        val ivBytes = Base64.decode(nonceBase64, Base64.DEFAULT)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, ivBytes)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

        val decryptedBytes = cipher.doFinal(cipherBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * AES-GCM 加密
     */
    fun encrypt(plainText: String, secret: String): Pair<String, String> {
        val key = deriveKey(secret)
        val ivBytes = ByteArray(12)
        java.security.SecureRandom().nextBytes(ivBytes)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, ivBytes)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val ciphertextBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        val nonceBase64 = Base64.encodeToString(ivBytes, Base64.NO_WRAP)

        return Pair(ciphertextBase64, nonceBase64)
    }
}
