package com.mygithub.lab.data.local

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * 本地已读覆盖管理器（SharedPreferences 持久化，无需 Room 迁移）
 * 解决网络慢/失败时本地点掉的蓝点在下次全量同步中被远端覆盖而复活的问题。
 */
class NotificationReadOverrideStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("notif_read_overrides", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_OVERRIDES = "overrides_json"
        private const val RETENTION_MS = 7L * 24 * 3600 * 1000 // 7 天过期清理
    }

    @Synchronized
    fun markRead(notificationId: String) {
        val map = loadMap()
        map.put(notificationId, System.currentTimeMillis())
        saveMap(map)
    }

    @Synchronized
    fun isMarkedRead(notificationId: String): Boolean {
        val map = loadMap()
        return map.has(notificationId)
    }

    @Synchronized
    fun getMarkedReadIds(): Set<String> {
        val map = loadMap()
        val keys = mutableSetOf<String>()
        val it = map.keys()
        while (it.hasNext()) {
            keys.add(it.next())
        }
        return keys
    }

    @Synchronized
    private fun loadMap(): JSONObject {
        val jsonStr = prefs.getString(KEY_OVERRIDES, null) ?: return JSONObject()
        return try {
            val raw = JSONObject(jsonStr)
            val now = System.currentTimeMillis()
            val cleaned = JSONObject()
            val keys = raw.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val ts = raw.optLong(k, 0L)
                if (now - ts < RETENTION_MS) {
                    cleaned.put(k, ts)
                }
            }
            cleaned
        } catch (_: Exception) {
            JSONObject()
        }
    }

    @Synchronized
    private fun saveMap(map: JSONObject) {
        prefs.edit().putString(KEY_OVERRIDES, map.toString()).apply()
    }
}
