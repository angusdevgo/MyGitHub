package com.mygithub.lab.data.relay

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.mygithub.lab.security.device.DeviceTrustStore
import com.mygithub.lab.security.device.EnvelopeCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * 手机端局域网直连监听器 (端口 18337)
 * 允许电脑扩展在同一 Wi-Fi 下以 5ms 极低延迟直接呼叫手机弹窗，免除对公网中继的强依赖
 */
class LanRelayServer private constructor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val json = Json { ignoreUnknownKeys = true }

    fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                serverSocket = ServerSocket(18337)
                Log.d("LanRelayServer", "局域网直连服务已启动，端口 18337, 本机IP: ${getLocalIpAddress()}")
                while (isActive && isRunning) {
                    val client = serverSocket?.accept() ?: break
                    scope.launch { handleClient(client) }
                }
            } catch (e: Exception) {
                Log.w("LanRelayServer", "局域网服务异常或端口被占: ${e.message}")
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val firstLine = reader.readLine() ?: return

            // 简单读取 HTTP 头部
            var contentLength = 0
            var line: String? = reader.readLine()
            while (!line.isNullOrBlank()) {
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
                line = reader.readLine()
            }

            // 读取 Body
            val bodyChars = CharArray(contentLength)
            var readTotal = 0
            while (readTotal < contentLength) {
                val r = reader.read(bodyChars, readTotal, contentLength - readTotal)
                if (r == -1) break
                readTotal += r
            }
            val body = String(bodyChars, 0, readTotal)

            if (firstLine.contains("POST /challenge")) {
                val envelope = json.decodeFromString<RelayEnvelope>(body)
                val devices = DeviceTrustStore.getPairedDevices(context)
                val matchedDevice = devices.find { it.did == envelope.from } ?: devices.firstOrNull()

                if (matchedDevice != null) {
                    val decryptedStr = EnvelopeCrypto.decrypt(
                        envelope.ciphertext,
                        envelope.nonce,
                        matchedDevice.secret
                    )
                    val challenge = json.decodeFromString<DecryptedChallenge>(decryptedStr)

                    // 唤醒全局弹窗
                    RelayClient.get(context).emitLocalChallenge(
                        IncomingAuthRequest(
                            sessionId = envelope.sessionId,
                            deviceName = matchedDevice.name,
                            expectedDigits = challenge.digits,
                            secret = matchedDevice.secret,
                            relayUrl = "lan://${socket.inetAddress.hostAddress}:18337",
                            rawEnvelope = envelope
                        )
                    )
                }

                val response = "HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\nContent-Type: application/json\r\nContent-Length: 11\r\n\r\n{\"ok\":true}"
                socket.getOutputStream().write(response.toByteArray())
            } else if (firstLine.startsWith("OPTIONS")) {
                val response = "HTTP/1.1 204 No Content\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: POST, OPTIONS\r\nAccess-Control-Allow-Headers: Content-Type\r\n\r\n"
                socket.getOutputStream().write(response.toByteArray())
            }
        } catch (e: Exception) {
            Log.e("LanRelayServer", "处理客户端请求出错", e)
        } finally {
            runCatching { socket.close() }
        }
    }

    fun getLocalIpAddress(): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifiManager.connectionInfo.ipAddress
            if (ip != 0) {
                String.format(
                    "%d.%d.%d.%d",
                    ip and 0xff,
                    ip shr 8 and 0xff,
                    ip shr 16 and 0xff,
                    ip shr 24 and 0xff
                )
            } else "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }

    companion object {
        @Volatile
        private var instance: LanRelayServer? = null

        fun get(context: Context): LanRelayServer =
            instance ?: synchronized(this) {
                instance ?: LanRelayServer(context.applicationContext).also { instance = it }
            }
    }
}
