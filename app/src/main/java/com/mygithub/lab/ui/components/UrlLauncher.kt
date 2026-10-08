package com.mygithub.lab.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast

/**
 * 全局统一链接打开器（Android 11+ 包可见性规范落地）
 *
 * 核心设计规则：
 * 1. 移除任何 createChooser，使用纯粹直接的 startActivity(intent)，尊重系统设定的默认浏览器；
 * 2. 在 AndroidManifest.xml 配合 <queries> 声明 https/http 的 BROWSABLE 意图，确保 Android 11+ 包可见性机制能感知所有第三方浏览器；
 * 3. 校验并补全完整的 https:// 协议头，打印调试日志。
 */
fun openUrl(context: Context, rawUrl: String) {
    if (rawUrl.isBlank()) return

    // 1. 确认 URL 的协议前缀完整（避免因缺少 scheme 被其他泛解析 App 错误捕获）
    val normalized = when {
        rawUrl.startsWith("https://", ignoreCase = true) || rawUrl.startsWith("http://", ignoreCase = true) -> rawUrl
        rawUrl.startsWith("//") -> "https:$rawUrl"
        else -> "https://$rawUrl"
    }

    Log.d("URL", "准备唤起浏览器打开: $normalized")

    val uri = try {
        Uri.parse(normalized)
    } catch (e: Exception) {
        Log.e("URL", "URI 解析失败: $normalized", e)
        copyUrl(context, normalized, "链接格式有误，已复制")
        return
    }

    // 2. 构造标准浏览器意图：显式声明 CATEGORY_BROWSABLE，绝不调用 Intent.createChooser
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }

    val launcherContext = context.findActivity() ?: context
    if (launcherContext !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        launcherContext.startActivity(intent)
        Log.d("URL", "成功向系统发起直接启动浏览器意图")
    } catch (_: ActivityNotFoundException) {
        Log.w("URL", "未找到可用浏览器，准备回退复制剪贴板")
        copyUrl(context, normalized, "未找到可用浏览器，链接已复制")
    } catch (e: Exception) {
        Log.e("URL", "打开浏览器异常: ${e.message}", e)
        copyUrl(context, normalized, "无法打开浏览器，链接已复制")
    }
}

private fun copyUrl(context: Context, url: String, toast: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
    Toast.makeText(context, toast, Toast.LENGTH_LONG).show()
}

/** 从 Context 包装链中解出 Activity（优先使用 Activity context 发起跳转） */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    var guard = 0
    while (ctx != null && guard < 8) {
        if (ctx is Activity) return ctx
        ctx = (ctx as? ContextWrapper)?.baseContext
        guard++
    }
    return null
}

fun openUrlWithDefaultBrowser(context: Context, url: String) = openUrl(context, url)
