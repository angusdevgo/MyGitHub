package com.mygithub.lab.security.totp

import android.net.Uri
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 TOTP 引擎（与 Google Authenticator / 1Password 兼容）
 * 纯端侧实现，无网络依赖
 */
object TotpEngine {

    private const val ALGORITHM = "HmacSHA1"   // RFC 6238 标准算法（Google Authenticator 兼容）
    private const val DIGITS = 6
    private const val PERIOD_SECONDS = 30

    /** 生成随机 Base32 密钥（160 bit，标准强度） */
    fun generateSecret(): String {
        val bytes = ByteArray(20).also { SecureRandom().nextBytes(it) }
        return Base32.encode(bytes)
    }

    /** otpauth:// URI（供 Authenticator 扫码绑定） */
    fun buildOtpAuthUri(secret: String, account: String, issuer: String = "MyGitHub"): String =
        Uri.encode("otpauth://totp/$issuer:$account?secret=$secret&issuer=$issuer&algorithm=SHA1&digits=$DIGITS&period=$PERIOD_SECONDS")
            .let { it }
            // Uri.encode 会把 : // 等转义，手动构建更直接
            .run { "otpauth://totp/${Uri.encode("$issuer:$account")}?secret=$secret&issuer=${Uri.encode(issuer)}&algorithm=SHA1&digits=$DIGITS&period=$PERIOD_SECONDS" }

    /** 计算当前时间窗口的 TOTP 码 */
    fun generateCode(secret: String, timestampMillis: Long = System.currentTimeMillis()): String {
        val counter = timestampMillis / 1000 / PERIOD_SECONDS
        return generateCodeAtCounter(secret, counter)
    }

    /** 校验用户输入的 6 位码（允许 ±1 时间窗口漂移） */
    fun verify(secret: String, code: String, timestampMillis: Long = System.currentTimeMillis()): Boolean {
        if (code.length != DIGITS || !code.all { it.isDigit() }) return false
        val counter = timestampMillis / 1000 / PERIOD_SECONDS
        // 当前窗口与前后各一个窗口均视为有效（时钟漂移容错）
        return (counter - 1..counter + 1).any { c ->
            generateCodeAtCounter(secret, c) == code
        }
    }

    /** 当前码剩余有效秒数（UI 倒计时用） */
    fun remainingSeconds(timestampMillis: Long = System.currentTimeMillis()): Int =
        (PERIOD_SECONDS - ((timestampMillis / 1000) % PERIOD_SECONDS)).toInt()

    private fun generateCodeAtCounter(secret: String, counter: Long): String {
        val keyBytes = Base32.decode(secret)
        val counterBytes = ByteArray(8).also { buf ->
            var c = counter
            for (i in 7 downTo 0) {
                buf[i] = (c and 0xFF).toByte()
                c = c shr 8
            }
        }
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(keyBytes, ALGORITHM))
        val hash = mac.doFinal(counterBytes)
        val offset = hash.last().toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)
        val code = binary % 1000000
        return code.toString().padStart(DIGITS, '0')
    }
}

/** RFC 4648 Base32 编解码（无 padding） */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) {
            sb.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        }
        return sb.toString()
    }

    fun decode(text: String): ByteArray {
        val clean = text.uppercase().filter { it in ALPHABET }
        val out = mutableListOf<Byte>()
        var buffer = 0
        var bits = 0
        for (c in clean) {
            buffer = (buffer shl 5) or ALPHABET.indexOf(c)
            bits += 5
            if (bits >= 8) {
                out.add(((buffer shr (bits - 8)) and 0xFF).toByte())
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}
