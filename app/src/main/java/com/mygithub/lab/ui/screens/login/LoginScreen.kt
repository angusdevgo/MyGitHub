package com.mygithub.lab.ui.screens.login

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mygithub.lab.data.auth.DeviceFlowAuth
import com.mygithub.lab.data.auth.TokenStore
import com.mygithub.lab.security.totp.TotpEngine
import com.mygithub.lab.security.totp.TotpStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 登录页
 *
 * 采用 GitHub OAuth Device Flow (RFC 8628)：
 *   1. App 向 GitHub 申请设备码，拿到 8 位验证码（如 WDJB-MJHT）
 *   2. 验证码自动复制到剪贴板，并尝试打开浏览器授权页
 *   3. 用户在网页输入验证码并点 Authorize，App 自动完成登录
 *
 * 备选：PAT 令牌登录（零配置，国内网络更稳）
 */
private const val GITHUB_CLIENT_ID = "Ov23liqsr2H35Mscqfii"
private const val DEVICE_AUTH_URL = "https://github.com/login/device"

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var userCode by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPatInput by remember { mutableStateOf(false) }
    var patText by remember { mutableStateOf("") }

    // 轮询任务句柄：支持用户主动取消
    var pollJob by remember { mutableStateOf<Job?>(null) }
    // 验证码剩余有效秒数（用于展示与本地超时）
    var remainSeconds by remember { mutableStateOf(0) }
    val expiresInSeconds = 900

    /** 取消登录并重置界面（供取消按钮与超时使用） */
    fun cancelLogin(message: String? = null) {
        pollJob?.cancel()
        pollJob = null
        loading = false
        userCode = null
        status = null
        remainSeconds = 0
        if (message != null) error = message
    }

    val auth = remember { DeviceFlowAuth(clientId = GITHUB_CLIENT_ID) }

    var totpSecret by remember { mutableStateOf(TotpStore.getSecret(context)) }
    var totpTick by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(totpSecret) {
        if (totpSecret != null) {
            while (true) {
                totpTick = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    // 验证码有效期倒计时（同时作为本地超时兜底）
    LaunchedEffect(userCode) {
        if (userCode == null) return@LaunchedEffect
        remainSeconds = expiresInSeconds
        while (remainSeconds > 0 && userCode != null) {
            kotlinx.coroutines.delay(1000)
            remainSeconds -= 1
        }
    }

    // 页面离开时清理轮询，避免泄漏
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { pollJob?.cancel() }
    }

    fun copyToClipboard(text: String, toastMsg: String = "已复制") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ===== 顶部 Logo =====
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )
                    )
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "MyGitHub",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "纯客户端 · 零服务器 · 无需注册",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))

            // ===== 错误提示 =====
            error?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                        .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            // ===== 加载动画 =====
            if (loading && userCode == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "正在连接 GitHub 认证中心...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
            }

            // ===== 设备授权验证码卡片 =====
            userCode?.let { code ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(22.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "请在浏览器中完成设备授权",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // 验证码大卡片（点击直接复制）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { copyToClipboard(code, "验证码已复制: $code") }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = code,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "已自动复制，点此可再次复制",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        // 操作区
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { copyToClipboard(code, "验证码已复制: $code") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("复制验证码", fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    copyToClipboard(code, "验证码已复制，请在浏览器中粘贴")
                                    com.mygithub.lab.ui.components.openUrl(context, DEVICE_AUTH_URL)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("打开授权页", fontSize = 13.sp)
                            }
                        }

                        // 手动兜底指引（针对部分 ROM 无法自动唤起浏览器的情况）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "浏览器没自动打开？手动操作：",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "1. 自行打开任意浏览器\n2. 访问 github.com/login/device\n3. 粘贴验证码 $code 并点击 Authorize",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                                OutlinedButton(
                                    onClick = { copyToClipboard(DEVICE_AUTH_URL, "授权网址已复制") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("复制授权网址", fontSize = 12.sp)
                                }
                            }
                        }

                        // 轮询状态 + 倒计时 + 取消入口
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = status ?: "正在等待网页端授权...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (remainSeconds > 0) {
                                Text(
                                    text = "剩余 ${remainSeconds / 60}:${(remainSeconds % 60).toString().padStart(2, '0')}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        // 取消登录：随时可退回选择其他登录方式
                        TextButton(
                            onClick = { cancelLogin("已取消本次登录") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("取消登录", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // ===== 主交互面板 =====
            AnimatedVisibility(visible = !showPatInput, enter = fadeIn(), exit = fadeOut()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (userCode == null && !loading) {
                        Button(
                            onClick = {
                                loading = true
                                error = null
                                status = null
                                pollJob?.cancel()
                                pollJob = scope.launch {
                                    try {
                                        val resp = auth.requestCode()
                                        userCode = resp.user_code
                                        status = "等待网页授权确认..."
                                        // 自动复制验证码并尝试打开授权页
                                        copyToClipboard(resp.user_code, "验证码 ${resp.user_code} 已复制")
                                        com.mygithub.lab.ui.components.openUrl(context, resp.verification_uri)

                                        when (val r = auth.pollForToken(
                                            deviceCode = resp.device_code,
                                            intervalSeconds = resp.interval,
                                            expiresInSeconds = resp.expires_in
                                        )) {
                                            is DeviceFlowAuth.AuthResult.Success -> {
                                                TokenStore.saveToken(context, r.token, "device_flow")
                                                loading = false
                                                onLoginSuccess()
                                            }
                                            is DeviceFlowAuth.AuthResult.Error -> {
                                                cancelLogin(r.message)
                                            }
                                            is DeviceFlowAuth.AuthResult.Cancelled -> {
                                                cancelLogin()
                                            }
                                        }
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        // 用户取消：静默清理，不当作错误
                                        loading = false
                                        userCode = null
                                        status = null
                                    } catch (e: Exception) {
                                        val msg = e.message.orEmpty()
                                        val hint = if (msg.contains("github.com") && (msg.contains("connect") || msg.contains("timeout"))) {
                                            "无法连接 GitHub，请检查网络或代理设置后重试"
                                        } else {
                                            "登录发起失败: $msg"
                                        }
                                        cancelLogin(hint)
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("使用 GitHub 账号登录", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(Modifier.height(22.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "或",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    OutlinedButton(
                        onClick = { showPatInput = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("使用令牌登录（国内推荐）", fontSize = 14.sp)
                    }
                }
            }

            // ===== PAT 令牌登录面板 =====
            AnimatedVisibility(visible = showPatInput, enter = fadeIn(), exit = fadeOut()) {
                PatLoginPanel(
                    patText = patText,
                    onPatChange = { patText = it },
                    onCopy = ::copyToClipboard,
                    onLogin = {
                        if (patText.isNotBlank()) {
                            TokenStore.saveToken(context, patText.trim(), "pat")
                            onLoginSuccess()
                        }
                    },
                    onBack = { showPatInput = false }
                )
            }
        }
    }
}

// ==================== PAT 令牌登录面板 ====================

@Composable
private fun PatLoginPanel(
    patText: String,
    onPatChange: (String) -> Unit,
    onCopy: (String, String) -> Unit,
    onLogin: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "如何获取 Personal Access Token？",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "1. 在浏览器打开下方链接；\n2. 登录账号后滑到底部生成 Token；\n3. 复制 ghp_ 开头的密钥并粘贴到下方。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 19.sp
                )
                OutlinedButton(
                    onClick = {
                        val url = "https://github.com/settings/tokens/new?scopes=repo,user,read:org&description=MyGitHub"
                        onCopy(url, "已复制新建令牌链接")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("复制新建令牌专属链接", fontSize = 13.sp)
                }
            }
        }

        OutlinedTextField(
            value = patText,
            onValueChange = onPatChange,
            placeholder = { Text("粘贴 ghp_ 开头的 Personal Access Token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            enabled = patText.isNotBlank()
        ) {
            Text("完成并登录", fontWeight = FontWeight.SemiBold)
        }

        TextButton(onClick = onBack) {
            Text("返回其他登录方式", fontSize = 13.sp)
        }
    }
}
