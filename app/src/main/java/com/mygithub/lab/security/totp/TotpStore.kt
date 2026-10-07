package com.mygithub.lab.security.totp

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * TOTP 密钥安全存储（全局单例 + 同步 commit 强制刷盘 + 旧文件无感自动迁移）
 * 彻底杜绝退出账号/杀死后台/应用升级导致的 2FA 数据丢失
 */
object TotpStore {
    private const val PREFS_PLAIN = "totp_plain_prefs"
    private const val PREFS_LEGACY_SECURE = "totp_secure_prefs"

    private const val KEY = "totp_secret"
    private const val KEY_ISSUER = "totp_issuer"
    private const val KEY_ACCOUNT = "totp_account"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_PLAIN, Context.MODE_PRIVATE)
    }

    /**
     * 获取已保存的密钥
     * 策略：
     * 1. 优先读取新版稳定文件 totp_plain_prefs
     * 2. 若为空，尝试尝试从旧版 EncryptedSharedPreferences (totp_secure_prefs) 自动迁移
     */
    fun getSecret(context: Context): String? {
        val prefs = getPrefs(context)
        val current = prefs.getString(KEY, null)
        if (!current.isNullOrBlank()) {
            return current
        }

        // 尝试自动迁移老版本加密文件
        val migrated = tryMigrateFromLegacy(context)
        if (!migrated.isNullOrBlank()) {
            return migrated
        }

        return null
    }

    /**
     * 同步写入密钥，使用 commit() 确保立刻物理落盘
     */
    fun saveSecret(context: Context, secret: String): Boolean {
        return getPrefs(context).edit()
            .putString(KEY, secret)
            .commit()
    }

    fun getIssuer(context: Context): String? {
        return getPrefs(context).getString(KEY_ISSUER, null)
    }

    fun getAccount(context: Context): String? {
        return getPrefs(context).getString(KEY_ACCOUNT, null)
    }

    /**
     * 同步写入账号与颁发者信息
     */
    fun saveIssuerAccount(context: Context, issuer: String?, account: String?): Boolean {
        return getPrefs(context).edit()
            .putString(KEY_ISSUER, issuer)
            .putString(KEY_ACCOUNT, account)
            .commit()
    }

    /**
     * 解绑时主动清空
     */
    fun clear(context: Context): Boolean {
        return getPrefs(context).edit()
            .clear()
            .commit()
    }

    /**
     * 自动从老版本加密存储迁移到新存储
     */
    private fun tryMigrateFromLegacy(context: Context): String? {
        return try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val legacyPrefs = EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_LEGACY_SECURE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            val legacySecret = legacyPrefs.getString(KEY, null)
            val legacyIssuer = legacyPrefs.getString(KEY_ISSUER, null)
            val legacyAccount = legacyPrefs.getString(KEY_ACCOUNT, null)

            if (!legacySecret.isNullOrBlank()) {
                // 成功从旧版读出，立刻同步刷入新版稳定存储
                getPrefs(context).edit()
                    .putString(KEY, legacySecret)
                    .putString(KEY_ISSUER, legacyIssuer)
                    .putString(KEY_ACCOUNT, legacyAccount)
                    .commit()
                legacySecret
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
