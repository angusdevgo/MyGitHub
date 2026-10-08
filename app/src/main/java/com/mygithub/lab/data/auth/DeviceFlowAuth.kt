package com.mygithub.lab.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * GitHub OAuth Device Flow (RFC 8628)
 *
 * 1. POST github.com/login/device/code → user_code + verification_uri
 * 2. 用户在浏览器打开 verification_uri 并输入 user_code
 * 3. 轮询 POST github.com/login/oauth/access_token → access_token
 *
 * 说明：
 * - client_id 属于公开标识，不是密钥（Device Flow 设计如此）
 * - 轮询支持**协程取消**（取消时立即中断底层网络请求）
 * - 支持**本地超时**，避免验证码过期后仍无意义轮询
 */
class DeviceFlowAuth(
    private val clientId: String,
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
        data class Error(val message: String) : AuthResult()
        /** 用户主动取消 */
        data object Cancelled : AuthResult()
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
                val text = resp.body?.string() ?: throw IllegalStateException("响应为空")
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: $text")
                json.decodeFromString(DeviceCodeResponse.serializer(), text)
            }
        }

    /**
     * 轮询换取 access token。
     *
     * @param expiresInSeconds 本地超时上限（通常取设备码响应的 expires_in，默认 900 秒）
     * 该函数可被协程取消：取消时立即中断正在进行的网络请求并返回 [AuthResult.Cancelled]。
     */
    suspend fun pollForToken(
        deviceCode: String,
        intervalSeconds: Int = 5,
        expiresInSeconds: Int = 900
    ): AuthResult = withContext(Dispatchers.IO) {
        var interval = intervalSeconds.coerceAtLeast(1)
        val deadline = System.currentTimeMillis() + expiresInSeconds * 1000L

        while (true) {
            if (System.currentTimeMillis() >= deadline) {
                return@withContext AuthResult.Error("验证码已过期，请重新发起登录")
            }

            val parsed = try {
                val body = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("device_code", deviceCode)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .build()

                val request = Request.Builder()
                    .url("https://github.com/login/oauth/access_token")
                    .header("Accept", "application/json")
                    .post(body)
                    .build()

                val text = okHttpClient.newCall(request).await().use { resp ->
                    resp.body?.string() ?: ""
                }
                json.decodeFromString(TokenResponse.serializer(), text)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户主动取消：向上抛出，由调用方统一处理
                throw e
            } catch (e: Exception) {
                // 网络抖动：短暂退避后重试，不直接中断整个登录
                delay(2000)
                continue
            }

            when (parsed.error) {
                null -> {
                    val token = parsed.access_token
                    return@withContext if (token.isNullOrBlank())
                        AuthResult.Error("未能获取到访问令牌") else AuthResult.Success(token)
                }
                "authorization_pending" -> delay(interval * 1000L)
                "slow_down" -> {
                    interval = (parsed.interval ?: (interval + 5)).coerceAtLeast(1)
                    delay(interval * 1000L)
                }
                "expired_token" -> return@withContext AuthResult.Error("验证码已过期，请重新发起登录")
                "access_denied" -> return@withContext AuthResult.Error("你已拒绝本次授权，可重新发起登录")
                "device_flow_disabled" -> return@withContext AuthResult.Error("该 OAuth App 未启用 Device Flow，请在 GitHub 应用设置中开启")
                "incorrect_client_credentials" -> return@withContext AuthResult.Error("Client ID 无效，请检查应用配置")
                else -> return@withContext AuthResult.Error(
                    parsed.error_description ?: parsed.error ?: "未知错误"
                )
            }
        }
        @Suppress("UNREACHABLE_CODE")
        AuthResult.Error("unreachable")
    }
}

/**
 * OkHttp Call 的可取消挂起扩展。
 * 协程被取消时会调用 [Call.cancel]，立即中断底层请求，避免泄漏与无谓等待。
 */
private suspend fun Call.await(): Response =
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else runCatching { response.close() }
            }
        })
    }
