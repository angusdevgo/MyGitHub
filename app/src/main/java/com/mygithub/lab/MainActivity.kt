package com.mygithub.lab

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.mygithub.lab.ui.MyGitHubApp
import okio.Path.Companion.toOkioPath

class MainActivity : ComponentActivity(), SingletonImageLoader.Factory {
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyGitHubApp()
        }
    }

    // 全局 Coil 图片加载器：crossfade + 内存缓存(50MB) + 磁盘缓存(100MB)，列表头像二次显示秒显零解码
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .crossfade(true)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizeBytes(50L * 1024 * 1024)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                .maxSizeBytes(100L * 1024 * 1024)
                .build()
        }
        .build()
}
