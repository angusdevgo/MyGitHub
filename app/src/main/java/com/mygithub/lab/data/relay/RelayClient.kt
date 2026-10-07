package com.mygithub.lab.data.relay

import android.content.Context
import android.util.Log
import com.mygithub.lab.security.device.DeviceTrustStore
import com.mygithub.lab.security.device.EnvelopeCrypto
import com.mygithub.lab.security.totp.TotpEngine
import com.mygithub.lab.security.totp.TotpStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.time.Duration

@Serializable
data class RelayEnvelope(
    val sessionId: String,
    val type: String,
    val from: String,
    val ciphertext: String,
    val nonce: String,
    val sig: String = "",
    val ts: Long = 0L
)

@Serializable
data class DecryptedChallenge(
    val digits: String,
    val client: String,
    val action: String,
    val time: Long
)

data class IncomingAuthRequest(
    val sessionId: String,
    val deviceName: String,
    val expectedDigits: String,
    val secret: String,
    val relayUrl: String,
    val rawEnvelope: RelayEnvelope
)

/**
 * 跨设备挑战中继客户端 (支持 WebSocket 实时长连接 + 自动断线重连)
 */
class RelayClient private constructor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val json = Json { ignoreUnknownKeys = true }
    private var webSocket: WebSocket? = null
    private var isRunning = false

    private val okClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(0)) // WS 保持
        .build()

    private val _incomingRequests = MutableSharedFlow<IncomingAuthRequest>(extraBufferCapacity = 8)
    val incomingRequests = _incomingRequests.asSharedFlow()

    fun startListening() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            while (isActive && isRunning) {
                val devices = DeviceTrustStore.getPairedDevices(context)
                val account = TotpStore.getAccount(context)?.ifBlank { null } ?: "angusdevgo"
                if (devices.isNotEmpty()) {
                    val relayUrl = DeviceTrustStore.getPrimaryRelayUrl(context)
                    connectWs(relayUrl, account)
                }
                delay(8000) // 8秒重连探针
            }
        }
    }

    private fun connectWs(relayUrl: String, account: String) {
        val wsUrl = relayUrl.replace("https://", "wss://").replace("http://", "ws://").trimEnd('/') +
                "/ws?account=${account}"

        val request = Request.Builder().url(wsUrl).build()
        webSocket?.cancel()
        webSocket = okClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("RelayClient", "WebSocket 已连接至: $wsUrl")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleWsMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("RelayClient", "WS 断开: ${t.message}")
            }
        })
    }

    private fun handleWsMessage(text: String) {
        try {
            val root = json.parseToJsonElement(text)
            val kind = root.toString()
            if (!text.contains("\"challenge\"")) return

            // 提取 Envelope
            val envStart = text.indexOf("\"envelope\":")
            if (envStart == -1) return
            val envelopeJson = text.substring(envStart + 11).trimEnd('}')
            val envelope = json.decodeFromString<RelayEnvelope>(envelopeJson)

            if (envelope.type != "challenge") return

            // 寻找对应的配对设备
            val devices = DeviceTrustStore.getPairedDevices(context)
            val matchedDevice = devices.find { it.did == envelope.from } ?: devices.firstOrNull() ?: return

            // AES-GCM 解密挑战内容
            val decryptedStr = EnvelopeCrypto.decrypt(
                envelope.ciphertext,
                envelope.nonce,
                matchedDevice.secret
            )

            val challenge = json.decodeFromString<DecryptedChallenge>(decryptedStr)

            // 发射到 UI 前台弹窗
            scope.launch {
                _incomingRequests.emit(
                    IncomingAuthRequest(
                        sessionId = envelope.sessionId,
                        deviceName = matchedDevice.name,
                        expectedDigits = challenge.digits,
                        secret = matchedDevice.secret,
                        relayUrl = matchedDevice.relayUrl,
                        rawEnvelope = envelope
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("RelayClient", "解析挑战信封失败", e)
        }
    }

    /**
     * 手机端批准并回传当前 6 位 TOTP 动态码
     */
    suspend fun approveChallenge(req: IncomingAuthRequest): Boolean = withContext(Dispatchers.IO) {
        try {
            val totpSecret = TotpStore.getSecret(context) ?: return@withContext false
            val currentCode = TotpEngine.generateCode(totpSecret)

            val payload = json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                kotlinx.serialization.json.buildJsonObject {
                    put("status", kotlinx.serialization.json.JsonPrimitive("approved"))
                    put("totpCode", kotlinx.serialization.json.JsonPrimitive(currentCode))
                    put("approvedAt", kotlinx.serialization.json.JsonPrimitive(System.currentTimeMillis()))
                }
            )

            val (ciphertext, nonce) = EnvelopeCrypto.encrypt(payload, req.secret)

            val approveEnvelope = RelayEnvelope(
                sessionId = req.sessionId,
                type = "approve",
                from = "did:mygithub:mobile",
                ciphertext = ciphertext,
                nonce = nonce,
                ts = System.currentTimeMillis()
            )

            val url = req.relayUrl.trimEnd('/') + "/auth/approve?account=angusdevgo"
            val reqBody = json.encodeToString(RelayEnvelope.serializer(), approveEnvelope).toRequestBody("application/json".toMediaType())
            val post = Request.Builder().url(url).post(reqBody).build()

            okClient.newCall(post).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e("RelayClient", "批准回传失败", e)
            false
        }
    }

    /**
     * 手机端拒绝挑战
     */
    suspend fun denyChallenge(req: IncomingAuthRequest): Boolean = withContext(Dispatchers.IO) {
        try {
            val (ciphertext, nonce) = EnvelopeCrypto.encrypt("{\"status\":\"denied\"}", req.secret)
            val denyEnvelope = RelayEnvelope(
                sessionId = req.sessionId,
                type = "deny",
                from = "did:mygithub:mobile",
                ciphertext = ciphertext,
                nonce = nonce,
                ts = System.currentTimeMillis()
            )

            val url = req.relayUrl.trimEnd('/') + "/auth/deny?account=angusdevgo"
            val reqBody = json.encodeToString(RelayEnvelope.serializer(), denyEnvelope).toRequestBody("application/json".toMediaType())
            val post = Request.Builder().url(url).post(reqBody).build()
            okClient.newCall(post).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        @Volatile
        private var instance: RelayClient? = null

        fun get(context: Context): RelayClient =
            instance ?: synchronized(this) {
                instance ?: RelayClient(context.applicationContext).also { instance = it }
            }
    }
}
