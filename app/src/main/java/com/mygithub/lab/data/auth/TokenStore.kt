package com.mygithub.lab.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Token 安全存储：EncryptedSharedPreferences（AES-256-GCM 硬件级加密）
 */
object TokenStore {
    private const val PREFS = "mygithub_secure_prefs"
    private const val KEY_TOKEN = "github_token"
    private const val KEY_LOGIN_TYPE = "login_type" // device_flow | pat

    @Volatile private var cached: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val p = EncryptedSharedPreferences.create(
                context,
                PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            cached = p
            return p
        }
    }

    fun saveToken(context: Context, token: String, type: String) {
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_LOGIN_TYPE, type)
            .commit()
    }

    fun getToken(context: Context): String? =
        prefs(context).getString(KEY_TOKEN, null)

    fun getLoginType(context: Context): String? =
        prefs(context).getString(KEY_LOGIN_TYPE, null)

    fun isLoggedIn(context: Context): Boolean =
        !getToken(context).isNullOrBlank()

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }
}
