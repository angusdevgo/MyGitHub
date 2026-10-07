package com.mygithub.lab.security.device

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PairedDevice(
    val did: String,            // 设备 DID
    val name: String,           // 设备名称 (如 Chrome on PC)
    val secret: String,         // 配对共享密钥
    val relayUrl: String,       // 中继地址
    val pairedAt: Long = System.currentTimeMillis()
)

/**
 * 已配对信任设备管理
 */
object DeviceTrustStore {
    private const val PREFS = "device_trust_prefs"
    private const val KEY_DEVICES = "paired_devices_json"
    private const val KEY_RELAY_OVERRIDE = "relay_url_override"
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getPairedDevices(context: Context): List<PairedDevice> {
        val str = prefs(context).getString(KEY_DEVICES, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<PairedDevice>>(str)
        }.getOrDefault(emptyList())
    }

    fun savePairedDevice(context: Context, device: PairedDevice) {
        val current = getPairedDevices(context).filter { it.did != device.did }.toMutableList()
        current.add(device)
        prefs(context).edit()
            .putString(KEY_DEVICES, json.encodeToString(current))
            .apply()
    }

    fun removePairedDevice(context: Context, did: String) {
        val current = getPairedDevices(context).filter { it.did != did }
        prefs(context).edit()
            .putString(KEY_DEVICES, json.encodeToString(current))
            .apply()
    }

    fun getPrimaryRelayUrl(context: Context): String {
        val override = prefs(context).getString(KEY_RELAY_OVERRIDE, null)
        if (!override.isNullOrBlank()) return override
        val dev = getPairedDevices(context).firstOrNull()
        return dev?.relayUrl?.ifBlank { null } ?: "https://mygithub-relay.workers.dev"
    }

    fun setPrimaryRelayUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_RELAY_OVERRIDE, url.trim()).apply()
    }
}
