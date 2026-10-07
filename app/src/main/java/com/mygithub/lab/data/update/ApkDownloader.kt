package com.mygithub.lab.data.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * APK 下载与完整性校验。
 *
 * 下载过程中实时计算 SHA-256，落盘后与 Release 说明中的校验和比对。
 * 校验失败立即删除文件，绝不把不可信的安装包交给系统安装器。
 */
class ApkDownloader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {

    sealed interface Result {
        /** 下载并通过校验，[file] 可安全安装 */
        data class Success(val file: File) : Result

        /** 下载失败（网络异常、HTTP 错误等） */
        data class Failure(val message: String) : Result

        /** 下载完成但哈希不匹配，文件已删除 */
        data class ChecksumMismatch(
            val expected: String,
            val actual: String
        ) : Result
    }

    /**
     * 下载 APK 到应用缓存目录并校验哈希。
     *
     * @param onProgress 已下载字节数 / 总字节数（总长未知时为 -1）
     */
    suspend fun download(
        info: UpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): Result = withContext(Dispatchers.IO) {
        val targetDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(targetDir, "MyGitHub-${info.version}.apk")

        // 复用上次残留的完整文件，避免重复下载
        if (apkFile.exists() && apkFile.length() == info.apkSizeBytes && info.apkSizeBytes > 0) {
            val cached = sha256Of(apkFile)
            if (cached == info.sha256) return@withContext Result.Success(apkFile)
            apkFile.delete()
        }

        val request = Request.Builder().url(info.apkUrl).build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.Failure("HTTP ${response.code}")
                }

                val body = response.body ?: return@withContext Result.Failure("响应为空")
                val total = if (info.apkSizeBytes > 0) info.apkSizeBytes else body.contentLength()
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L

                // 先写临时文件，校验通过后再改名，避免半截文件被误用
                val tempFile = File(targetDir, "${apkFile.name}.part")

                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                        output.flush()
                    }
                }

                val actual = digest.digest().joinToString("") { "%02x".format(it) }

                if (!actual.equals(info.sha256, ignoreCase = true)) {
                    tempFile.delete()
                    return@withContext Result.ChecksumMismatch(info.sha256, actual)
                }

                if (apkFile.exists()) apkFile.delete()
                if (!tempFile.renameTo(apkFile)) {
                    tempFile.delete()
                    return@withContext Result.Failure("无法保存安装包")
                }

                Result.Success(apkFile)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e.message ?: "下载失败")
        }
    }

    /** 计算文件 SHA-256（用于复用缓存文件时的复核） */
    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** 清理已下载的安装包（安装完成或用户取消后调用） */
    fun clearCache() {
        File(context.cacheDir, "updates").deleteRecursively()
    }
}
