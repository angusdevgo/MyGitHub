package com.mygithub.lab.ui.screens.security

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mygithub.lab.security.totp.TotpEngine
import com.mygithub.lab.security.totp.TotpStore
import com.mygithub.lab.ui.components.KomiSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 安全中心：GitHub 2FA 验证器平替
 * - 扫码导入（GitHub 2FA 设置页二维码）
 * - 手动粘贴（otpauth URI / 纯 Base32 setup key）
 * - 实时 6 位动态码 + 点码即复制
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = remember { CoroutineScope(Dispatchers.Main) }

    var secret by remember { mutableStateOf<String?>(TotpStore.getSecret(context)) }
    var issuer by remember { mutableStateOf<String?>(TotpStore.getIssuer(context)) }
    var account by remember { mutableStateOf<String?>(TotpStore.getAccount(context)) }
    var verifyInput by remember { mutableStateOf("") }
    var verifyResult by remember { mutableStateOf<String?>(null) }
    var showUnbind by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var manualInput by remember { mutableStateOf("") }
    var manualError by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    fun copyCode(code: String) {
        clipboard.setText(AnnotatedString(code))
        scope.launch { snackbarHost.showSnackbar("✓ 已复制 $code") }
    }

    if (showScanner) {
        QrScannerScreen(
            onScanned = { payload ->
                TotpStore.saveSecret(context, payload.secret)
                TotpStore.saveIssuerAccount(context, payload.issuer, payload.account)
                secret = payload.secret
                issuer = payload.issuer
                account = payload.account
                showScanner = false
            },
            onBack = { showScanner = false }
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("安全中心", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (secret == null) {
                // ===== 未绑定：引导绑定 =====
                KomiSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Column {
                                Text("GitHub 2FA 验证器", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "替代 Google Authenticator：网页登录时点 More options → Authenticator app，在此复制 6 位码",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            "① 在 GitHub 网页：Settings → Password and authentication → Two-factor methods → Authenticator app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(onClick = { showScanner = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  扫码导入（推荐）")
                        }
                        Text(
                            "直接扫描 GitHub 显示的二维码，自动提取密钥",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedButton(onClick = {
                            // 展开手动输入区（默认已展开）
                        }, modifier = Modifier.fillMaxWidth()) { }
                        // 手动粘贴导入
                        OutlinedTextField(
                            value = manualInput,
                            onValueChange = { manualInput = it; manualError = null },
                            label = { Text("粘贴 otpauth:// URI 或 setup key") },
                            placeholder = { Text("JBSW Y3DP EHPK 3PXP 或 otpauth://totp/...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        manualError?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = {
                                val input = manualInput.trim()
                                val payload = when {
                                    input.startsWith("otpauth://", ignoreCase = true) -> parseOtpAuthUri(input)
                                    else -> {
                                        // 纯 Base32 setup key（GitHub 显示格式可能带空格）
                                        val cleaned = input.replace(" ", "").uppercase()
                                        if (cleaned.length >= 16 && cleaned.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567" }) {
                                            OtpAuthPayload(secret = cleaned, issuer = "GitHub", account = null)
                                        } else null
                                    }
                                }
                                if (payload == null) {
                                    manualError = "密钥格式无效：应为 otpauth:// URI 或 ≥16 位 Base32 字符（GitHub setup key）"
                                } else {
                                    TotpStore.saveSecret(context, payload.secret)
                                    TotpStore.saveIssuerAccount(context, payload.issuer, payload.account)
                                    secret = payload.secret
                                    issuer = payload.issuer
                                    account = payload.account
                                }
                            },
                            enabled = manualInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("手动导入密钥") }
                    }
                }
            } else {
                // ===== 已绑定：显示动态码 =====
                KomiSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                listOfNotNull(issuer, account).joinToString(" · ").ifBlank { "GitHub 2FA 动态码" },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        // 点码即复制（tick 局部化到 SecurityTotpCode）
                        SecurityTotpCode(secret = secret!!, onCopy = ::copyCode)
                        Text(
                            "网页登录：More options → Authenticator app → 输入上方 6 位码",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { showUnbind = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("  解除绑定", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // ===== 验证器自检 =====
                KomiSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("验证测试", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "输入 GitHub 网页当前要求/其他验证器显示的 6 位码，验证两端是否一致",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = verifyInput,
                            onValueChange = { verifyInput = it.filter { c -> c.isDigit() }.take(6) },
                            label = { Text("6 位动态码") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(onClick = {
                            verifyResult = if (TotpEngine.verify(secret!!, verifyInput))
                                "✓ 验证通过，两端一致" else "✗ 验证失败，请检查密钥或时钟"
                        }, enabled = verifyInput.length == 6) { Text("验证") }
                        verifyResult?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium,
                                color = if (it.startsWith("✓")) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showUnbind) {
        AlertDialog(
            onDismissRequest = { showUnbind = false },
            title = { Text("解除 TOTP 绑定？") },
            text = { Text("解绑后 MyGitHub 将不再生成动态码。请确保 GitHub 账号已换绑其他验证器，否则可能被锁在账号外。") },
            confirmButton = {
                TextButton(onClick = {
                    TotpStore.clear(context)
                    secret = null
                    issuer = null
                    account = null
                    showUnbind = false
                }) { Text("解绑", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showUnbind = false }) { Text("取消") } }
        )
    }
}

/**
 * SecurityTotpCode — 动态码显示区（tick 局部化，不触发 SecurityScreen 整页重组）
 */
@Composable
private fun SecurityTotpCode(secret: String, onCopy: (String) -> Unit) {
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            tick = System.currentTimeMillis()
            delay(1000)
        }
    }
    val currentCode = TotpEngine.generateCode(secret, tick)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCopy(currentCode) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = currentCode.chunked(3).joinToString(" "),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("点击复制", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val remain = TotpEngine.remainingSeconds(tick)
    LinearProgressIndicator(
        progress = { remain / 30f },
        modifier = Modifier.fillMaxWidth()
    )
    Text("${remain}s 后刷新", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
