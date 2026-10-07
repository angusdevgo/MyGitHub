package com.mygithub.lab.data.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.time.Duration

enum class ProxyMode {
    DIRECT,     // 直连 GitHub
    MIRROR,     // 公共反代镜像加速
    CUSTOM      // 自定义 HTTP / SOCKS5 代理
}

enum class CustomProxyType {
    HTTP,
    SOCKS
}

data class ProxySettings(
    val mode: ProxyMode = ProxyMode.DIRECT,
    val customType: CustomProxyType = CustomProxyType.HTTP,
    val host: String = "127.0.0.1",
    val port: Int = 7890,
    val mirrorPrefix: String = "https://ghproxy.net/"
)

object ProxyManager {
    private const val PREFS_NAME = "mygithub_proxy_prefs"
    private const val KEY_MODE = "proxy_mode"
    private const val KEY_TYPE = "proxy_type"
    private const val KEY_HOST = "proxy_host"
    private const val KEY_PORT = "proxy_port"
    private const val KEY_MIRROR = "proxy_mirror"

    val DEFAULT_MIRRORS = listOf(
        "https://ghproxy.net/" to "GHProxy (稳定通用)",
        "https://mirror.ghproxy.com/" to "Mirror GHProxy (高速备用)",
        "https://gh.ddlc.top/" to "DDLC 加速源"
    )

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSettings(context: Context): ProxySettings {
        val prefs = getPrefs(context)
        val modeStr = prefs.getString(KEY_MODE, ProxyMode.DIRECT.name) ?: ProxyMode.DIRECT.name
        val typeStr = prefs.getString(KEY_TYPE, CustomProxyType.HTTP.name) ?: CustomProxyType.HTTP.name
        val host = prefs.getString(KEY_HOST, "127.0.0.1") ?: "127.0.0.1"
        val port = prefs.getInt(KEY_PORT, 7890)
        val mirror = prefs.getString(KEY_MIRROR, "https://ghproxy.net/") ?: "https://ghproxy.net/"

        return ProxySettings(
            mode = runCatching { ProxyMode.valueOf(modeStr) }.getOrDefault(ProxyMode.DIRECT),
            customType = runCatching { CustomProxyType.valueOf(typeStr) }.getOrDefault(CustomProxyType.HTTP),
            host = host,
            port = port,
            mirrorPrefix = mirror
        )
    }

    fun saveSettings(context: Context, settings: ProxySettings) {
        getPrefs(context).edit()
            .putString(KEY_MODE, settings.mode.name)
            .putString(KEY_TYPE, settings.customType.name)
            .putString(KEY_HOST, settings.host.trim())
            .putInt(KEY_PORT, settings.port)
            .putString(KEY_MIRROR, settings.mirrorPrefix.trim())
            .apply()
    }

    /**
     * 动态 ProxySelector：每次网络请求时从 SharedPreferences 动态读取最新代理配置，
     * 保证配置修改后全局即刻生效，无需重建 OkHttpClient。
     */
    fun createDynamicProxySelector(context: Context): ProxySelector {
        val appContext = context.applicationContext
        return object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> {
                val settings = getSettings(appContext)
                if (settings.mode == ProxyMode.CUSTOM && settings.host.isNotBlank() && settings.port > 0) {
                    val pType = if (settings.customType == CustomProxyType.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
                    val proxy = Proxy(pType, InetSocketAddress(settings.host, settings.port))
                    return listOf(proxy)
                }
                return listOf(Proxy.NO_PROXY)
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                // 代理连接失败时不中断全局异常，由 OkHttp 抛给上层统一重试
            }
        }
    }

    /**
     * 对给定的目标 URL 进行公共镜像前缀加速包装（针对 Release、Raw、Repo 资源）
     */
    fun wrapUrlIfMirror(context: Context, targetUrl: String): String {
        val settings = getSettings(context)
        if (settings.mode != ProxyMode.MIRROR) return targetUrl
        val prefix = settings.mirrorPrefix.trim().trimEnd('/') + "/"
        return if (targetUrl.startsWith(prefix)) targetUrl else "$prefix$targetUrl"
    }

    /**
     * 连通性与延迟测试（测量连接 GitHub API 或对应节点的耗时）
     * 返回毫秒延迟（> 0 表示成功，< 0 表示异常）
     */
    suspend fun testConnection(context: Context, settings: ProxySettings): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val builder = OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(6))
                .readTimeout(Duration.ofSeconds(6))

            when (settings.mode) {
                ProxyMode.DIRECT -> {
                    // 直连默认 Proxy
                }
                ProxyMode.CUSTOM -> {
                    val pType = if (settings.customType == CustomProxyType.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
                    builder.proxy(Proxy(pType, InetSocketAddress(settings.host.trim(), settings.port)))
                }
                ProxyMode.MIRROR -> {
                    // 测试镜像节点的可用性
                }
            }

            val testClient = builder.build()
            val start = System.currentTimeMillis()

            val testUrl = if (settings.mode == ProxyMode.MIRROR) {
                val prefix = settings.mirrorPrefix.trim().trimEnd('/') + "/"
                "${prefix}https://api.github.com/zen"
            } else {
                "https://api.github.com/zen"
            }

            val req = Request.Builder()
                .url(testUrl)
                .header("User-Agent", "MyGitHub-SpeedTest")
                .build()

            testClient.newCall(req).execute().use { response ->
                val duration = System.currentTimeMillis() - start
                if (response.isSuccessful || response.code in 200..404) {
                    Result.success(duration)
                } else {
                    Result.failure(IOException("HTTP 状态异常: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
