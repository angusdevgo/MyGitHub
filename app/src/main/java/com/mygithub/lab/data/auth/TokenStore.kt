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

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveToken(context: Context, token: String, type: String) {
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_LOGIN_TYPE, type)
            .apply()
    }

    fun getToken(context: Context): String? =
        prefs(context).getString(KEY_TOKEN, null)

    fun getLoginType(context: Context): String? =
        prefs(context).getString(KEY_LOGIN_TYPE, null)

    fun isLoggedIn(context: Context): Boolean =
        !getToken(context).isNullOrBlank()

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
