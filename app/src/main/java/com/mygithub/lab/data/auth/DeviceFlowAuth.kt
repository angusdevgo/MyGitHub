package com.mygithub.lab.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * GitHub OAuth Device Flow (RFC 8628)
 * 1. POST github.com/login/device/code → user_code + verification_uri
 * 2. 用户浏览器打开 verification_uri 输入 user_code
 * 3. 轮询 POST github.com/login/oauth/access_token → access_token
 *
 * 注意：client_id 属于公开标识，不属于密钥（Device Flow 设计如此）
 */
class DeviceFlowAuth(
    private val clientId: String,
    private val clientSecret: String? = null, // OAuth App 无 secret 时可为 null
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    @Serializable
    data class DeviceCodeResponse(
        val device_code: String,
        val user_code: String,
        val verification_uri: String,
        val expires_in: Int,
        val interval: Int = 5
    )

    @Serializable
    data class TokenResponse(
        val access_token: String? = null,
        val token_type: String? = null,
        val error: String? = null,
        val error_description: String? = null,
        val interval: Int? = null
    )

    sealed class AuthResult {
        data class Success(val token: String) : AuthResult()
        data class Pending(val message: String) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    suspend fun requestCode(scopes: String = "repo,user,read:org"): DeviceCodeResponse =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("scope", scopes)
                .build()
            val request = Request.Builder()
                .url("https://github.com/login/device/code")
                .header("Accept", "application/json")
                .post(body)
                .build()
            okHttpClient.newCall(request).await().use { resp ->
                val text = resp.body?.string() ?: throw IllegalStateException("empty body")
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: $text")
                json.decodeFromString(DeviceCodeResponse.serializer(), text)
            }
        }

    /**
     * 轮询换 token。onUserCode 回调用于 UI 展示用户码与验证链接。
     */
    suspend fun pollForToken(
        deviceCode: String,
        intervalSeconds: Int = 5,
        onUserCode: suspend (DeviceCodeResponse) -> Unit = {},
        deviceCodeResponse: DeviceCodeResponse? = null
    ): AuthResult = withContext(Dispatchers.IO) {
        deviceCodeResponse?.let { onUserCode(it) }
        var interval = intervalSeconds
        while (true) {
            val builder = FormBody.Builder()
                .add("client_id", clientId)
                .add("device_code", deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            clientSecret?.let { builder.add("client_secret", it) }

            val request = Request.Builder()
                .url("https://github.com/login/oauth/access_token")
                .header("Accept", "application/json")
                .post(builder.build())
                .build()
            val respBody = okHttpClient.newCall(request).await().use { resp ->
                resp.body?.string() ?: ""
            }
            val parsed = json.decodeFromString(TokenResponse.serializer(), respBody)
            when (parsed.error) {
                null -> {
                    val token = parsed.access_token
                    return@withContext if (token.isNullOrBlank())
                        AuthResult.Error("empty token") else AuthResult.Success(token)
                }
                "authorization_pending" -> {
                    kotlinx.coroutines.delay(interval * 1000L)
                }
                "slow_down" -> {
                    interval = (parsed.interval ?: (interval + 5))
                    kotlinx.coroutines.delay(interval * 1000L)
                }
                "expired_token" -> return@withContext AuthResult.Error("验证码已过期，请重新发起登录")
                else -> return@withContext AuthResult.Error(parsed.error_description ?: parsed.error)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        AuthResult.Error("unreachable")
    }
}

/** OkHttp Call 挂起扩展 */
private suspend fun okhttp3.Call.await(): okhttp3.Response =
    suspendCoroutine { continuation ->
        enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                continuation.resumeWithException(e)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                continuation.resume(response)
            }
        })
    }
