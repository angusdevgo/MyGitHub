package com.mygithub.lab.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * 封装系统安装器拉起与 Android 8.0+ 未知应用来源安装权限检查。
 */
object PackageInstallerHelper {

    /** 检查是否已被授予安装未知来源应用权限 */
    fun canInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /** 引导用户打开系统未知应用安装权限开关 */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                // 部分定制 ROM 不支持直接带 package URI，回退到通用列表
                val fallback = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            }
        }
    }

    /**
     * 通过 FileProvider 拉起系统安装器安装指定 APK
     *
     * @return true 表示成功拉起，false 表示失败（如未授予权限）
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) return false

        val authority = "${context.packageName}.fileprovider"
        val contentUri = try {
            FileProvider.getUriForFile(context, authority, apkFile)
        } catch (e: Exception) {
            return false
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
