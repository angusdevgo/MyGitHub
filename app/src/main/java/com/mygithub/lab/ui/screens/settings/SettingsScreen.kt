package com.mygithub.lab.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mygithub.lab.data.network.CustomProxyType
import com.mygithub.lab.data.network.ProxyManager
import com.mygithub.lab.data.network.ProxyMode
import com.mygithub.lab.data.network.ProxySettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenThemeDialog: () -> Unit,
    onOpenAccount: () -> Unit,
    themeLabel: String,
    loginTypeLabel: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler(enabled = true) { onBack() }

    var proxySettings by remember { mutableStateOf(ProxyManager.getSettings(context)) }
    var hostInput by remember { mutableStateOf(proxySettings.host) }
    var portInput by remember { mutableStateOf(proxySettings.port.toString()) }
    var selectedMirror by remember { mutableStateOf(proxySettings.mirrorPrefix) }

    var isTestingSpeed by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var testIsSuccess by remember { mutableStateOf(false) }

    fun updateSettings(newSettings: ProxySettings) {
        proxySettings = newSettings
        ProxyManager.saveSettings(context, newSettings)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "应用设置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ===== 分组 1：通用偏好 =====
            SettingsSectionHeader(title = "通用偏好")
            Spacer(modifier = Modifier.height(8.dp))

            SettingsCardGroup {
                SettingsItem(
                    icon = Icons.Filled.Palette,
                    iconColor = MaterialTheme.colorScheme.tertiary,
                    title = "外观主题",
                    subtitle = themeLabel,
                    onClick = onOpenThemeDialog
                )
                SettingsDivider()
                SettingsItem(
                    icon = Icons.Filled.Person,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    title = "当前账号",
                    subtitle = "登录方式：$loginTypeLabel",
                    onClick = onOpenAccount
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ===== 分组 2：网络加速与代理 (方案 A) =====
            SettingsSectionHeader(title = "网络加速与代理")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "针对国内网络环境优化 GitHub API 与更新资源访问",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f))
                    .padding(10.dp)
            ) {
                Text(
                    text = "提示：如果手机已经开了 VPN / 全局代理，请选择【直连 GitHub】。" +
                        "App 层再叠一层代理会导致握手失败（如 HTTP 402/407）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            // 模式选择三卡片
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 1. 直连模式
                ProxyModeOptionCard(
                    title = "直连 GitHub (Direct)",
                    subtitle = "不使用任何代理或镜像，直接建立 TLS 官方连接",
                    icon = Icons.Filled.Language,
                    isSelected = proxySettings.mode == ProxyMode.DIRECT,
                    onClick = {
                        val updated = proxySettings.copy(mode = ProxyMode.DIRECT)
                        updateSettings(updated)
                        testResultText = null
                    }
                )

                // 2. 公共反代镜像加速
                ProxyModeOptionCard(
                    title = "公共反代镜像加速 (Mirror)",
                    subtitle = "免自建节点，通过国内合规公网镜像加速 Release 与资源下载",
                    icon = Icons.Filled.Public,
                    isSelected = proxySettings.mode == ProxyMode.MIRROR,
                    onClick = {
                        val updated = proxySettings.copy(mode = ProxyMode.MIRROR)
                        updateSettings(updated)
                        testResultText = null
                    }
                )

                // 镜像源配置展开（支持折叠/展开，紧凑省空间）
                AnimatedVisibility(visible = proxySettings.mode == ProxyMode.MIRROR) {
                    var isMirrorExpanded by remember { mutableStateOf(false) }
                    val currentMirrorLabel = ProxyManager.DEFAULT_MIRRORS.find { it.first == proxySettings.mirrorPrefix }?.second ?: "默认镜像源"

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 紧凑折叠摘要栏（点击展开/收起）
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { isMirrorExpanded = !isMirrorExpanded }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "镜像节点: $currentMirrorLabel",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = proxySettings.mirrorPrefix,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (isMirrorExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = if (isMirrorExpanded) "收起" else "展开切换",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // 展开的可选节点列表
                            AnimatedVisibility(visible = isMirrorExpanded) {
                                Column(
                                    modifier = Modifier.padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    ProxyManager.DEFAULT_MIRRORS.forEach { (url, label) ->
                                        val isMirrorSelected = proxySettings.mirrorPrefix == url
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isMirrorSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                    else Color.Transparent
                                                )
                                                .clickable {
                                                    selectedMirror = url
                                                    val updated = proxySettings.copy(mirrorPrefix = url)
                                                    updateSettings(updated)
                                                    isMirrorExpanded = false
                                                }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isMirrorSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                                Text(
                                                    text = url,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (isMirrorSelected) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. 自定义代理
                ProxyModeOptionCard(
                    title = "自定义代理节点 (HTTP / SOCKS5)",
                    subtitle = "支持 Clash、v2ray、局域网或本机本地代理节点",
                    icon = Icons.Filled.Router,
                    isSelected = proxySettings.mode == ProxyMode.CUSTOM,
                    onClick = {
                        val updated = proxySettings.copy(mode = ProxyMode.CUSTOM)
                        updateSettings(updated)
                        testResultText = null
                    }
                )

                // 自定义节点输入表单
                AnimatedVisibility(visible = proxySettings.mode == ProxyMode.CUSTOM) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "代理协议类型",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilterChip(
                                    selected = proxySettings.customType == CustomProxyType.HTTP,
                                    onClick = {
                                        val updated = proxySettings.copy(customType = CustomProxyType.HTTP)
                                        updateSettings(updated)
                                    },
                                    label = { Text("HTTP 代理") },
                                    leadingIcon = if (proxySettings.customType == CustomProxyType.HTTP) {
                                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null
                                )
                                FilterChip(
                                    selected = proxySettings.customType == CustomProxyType.SOCKS,
                                    onClick = {
                                        val updated = proxySettings.copy(customType = CustomProxyType.SOCKS)
                                        updateSettings(updated)
                                    },
                                    label = { Text("SOCKS5 代理") },
                                    leadingIcon = if (proxySettings.customType == CustomProxyType.SOCKS) {
                                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = hostInput,
                                    onValueChange = {
                                        hostInput = it
                                        val updated = proxySettings.copy(host = it)
                                        updateSettings(updated)
                                    },
                                    label = { Text("主机地址") },
                                    placeholder = { Text("127.0.0.1") },
                                    singleLine = true,
                                    modifier = Modifier.weight(2f)
                                )
                                OutlinedTextField(
                                    value = portInput,
                                    onValueChange = {
                                        portInput = it
                                        val portInt = it.toIntOrNull() ?: 7890
                                        val updated = proxySettings.copy(port = portInt)
                                        updateSettings(updated)
                                    },
                                    label = { Text("端口") },
                                    placeholder = { Text("7890") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 连通性测试与延迟反馈条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "网络连通性",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        if (testResultText != null) {
                            Text(
                                text = testResultText!!,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (testIsSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "点击测试当前加速方案的响应延迟",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (isTestingSpeed) return@Button
                            isTestingSpeed = true
                            testResultText = "正在握手探测..."
                            scope.launch {
                                val current = proxySettings.copy(
                                    host = hostInput,
                                    port = portInput.toIntOrNull() ?: 7890,
                                    mirrorPrefix = selectedMirror
                                )
                                val res = ProxyManager.testConnection(context, current)
                                isTestingSpeed = false
                                res.fold(
                                    onSuccess = { ms ->
                                        testIsSuccess = true
                                        testResultText = "✓ 成功连接 (${ms}ms)"
                                        Toast.makeText(context, "连接通畅，延迟 ${ms}ms", Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = { err ->
                                        testIsSuccess = false
                                        testResultText = "✕ 连接失败 (${err.message?.take(24)})"
                                        Toast.makeText(context, "连接失败，请检查配置", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        enabled = !isTestingSpeed
                    ) {
                        if (isTestingSpeed) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("测试连接")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun SettingsCardGroup(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ProxyModeOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surface
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "当前选择",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
