package com.mygithub.lab.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mygithub.lab.data.update.ApkDownloader
import com.mygithub.lab.data.update.PackageInstallerHelper
import com.mygithub.lab.data.update.UpdateInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Downloading(val downloaded: Long, val total: Long, val progress: Float) : UpdateState
    data object Verifying : UpdateState
    data class ReadyToInstall(val apkFile: File) : UpdateState
    data class Error(val message: String, val canRetry: Boolean = true) : UpdateState
}

/**
 * 应用内更新提示弹窗（支持多阶段状态展示与哈希防篡改校验）
 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloader = remember { ApkDownloader(context) }

    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    fun copyText(text: String, label: String = "SHA-256") {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "已复制 $label", Toast.LENGTH_SHORT).show()
    }

    fun startDownload() {
        downloadJob?.cancel()
        state = UpdateState.Downloading(downloaded = 0, total = info.apkSizeBytes, progress = 0f)
        downloadJob = scope.launch {
            val result = downloader.download(
                info = info,
                onProgress = { downloaded, total ->
                    val fraction = if (total > 0) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                    state = UpdateState.Downloading(downloaded, total, fraction)
                }
            )
            state = when (result) {
                is ApkDownloader.Result.Success -> UpdateState.ReadyToInstall(result.file)
                is ApkDownloader.Result.Failure -> UpdateState.Error("下载失败：${result.message}")
                is ApkDownloader.Result.ChecksumMismatch -> {
                    UpdateState.Error(
                        "安全拦截：安装包 SHA-256 与发布信息不匹配！文件已被安全删除以防篡改。"
                    )
                }
            }
        }
    }

    fun triggerInstall(file: File) {
        if (!PackageInstallerHelper.canInstall(context)) {
            Toast.makeText(context, "请授予应用安装权限后重试", Toast.LENGTH_LONG).show()
            PackageInstallerHelper.openInstallPermissionSettings(context)
        } else {
            val launched = PackageInstallerHelper.installApk(context, file)
            if (!launched) {
                Toast.makeText(context, "拉起安装器失败，请检查系统设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (state !is UpdateState.Downloading) {
                onDismiss()
            }
        },
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (state) {
                        is UpdateState.ReadyToInstall -> Icons.Filled.CheckCircle
                        is UpdateState.Error -> Icons.Filled.Error
                        else -> Icons.Filled.SystemUpdate
                    },
                    contentDescription = null,
                    tint = when (state) {
                        is UpdateState.ReadyToInstall -> MaterialTheme.colorScheme.tertiary
                        is UpdateState.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = "发现新版本 ${info.version.display}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 版本摘要
                if (info.releaseName.isNotBlank() && info.releaseName != info.version.display) {
                    Text(
                        text = info.releaseName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 体积与安全摘要
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (info.apkSizeBytes > 0) {
                        Text(
                            text = "大小: ${formatBytes(info.apkSizeBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "已启用 SHA-256 完整性校验",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                // 预期的安全哈希值
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SHA256: ${info.sha256.take(12)}...${info.sha256.takeLast(8)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { copyText(info.sha256) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "复制哈希",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // 更新日志内容
                if (info.releaseNotes.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = info.releaseNotes.take(1000),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                // 动态进度/状态展示
                when (val s = state) {
                    is UpdateState.Downloading -> {
                        Spacer(Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { s.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${(s.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${formatBytes(s.downloaded)} / ${formatBytes(s.total)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    is UpdateState.Verifying -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("正在校验安装包 SHA-256 完整性...", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    is UpdateState.ReadyToInstall -> {
                        Text(
                            text = "✓ 已成功验证 SHA-256 校验和，安装包安全完整。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    is UpdateState.Error -> {
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is UpdateState.Idle -> {}
                }
            }
        },
        confirmButton = {
            when (val s = state) {
                is UpdateState.Idle -> {
                    Button(onClick = { startDownload() }) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("立即更新")
                    }
                }
                is UpdateState.Downloading -> {
                    TextButton(onClick = {
                        downloadJob?.cancel()
                        state = UpdateState.Idle
                    }) {
                        Text("取消下载")
                    }
                }
                is UpdateState.ReadyToInstall -> {
                    Button(onClick = { triggerInstall(s.apkFile) }) {
                        Text("立即安装")
                    }
                }
                is UpdateState.Error -> {
                    if (s.canRetry) {
                        Button(onClick = { startDownload() }) {
                            Text("重试下载")
                        }
                    }
                }
                is UpdateState.Verifying -> {}
            }
        },
        dismissButton = {
            if (state !is UpdateState.Downloading) {
                TextButton(onClick = onDismiss) {
                    Text("稍后提醒")
                }
            }
        }
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "未知"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
