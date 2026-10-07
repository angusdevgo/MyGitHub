package com.mygithub.lab.ui.screens.profile

import coil3.compose.AsyncImage
import coil3.request.crossfade
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mygithub.lab.data.api.GitHubUser
import com.mygithub.lab.data.auth.TokenStore
import com.mygithub.lab.data.repo.GitHubRepository
import com.mygithub.lab.security.totp.TotpEngine
import com.mygithub.lab.security.totp.TotpStore
import com.mygithub.lab.ui.screens.stars.StarsScreen
import com.mygithub.lab.data.api.GitHubRepo
import com.mygithub.lab.ui.components.KomiPullRefreshIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ContentCopy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onRepoClick: (GitHubRepo) -> Unit,
    onLogout: () -> Unit,
    onShowUpdate: (com.mygithub.lab.data.update.UpdateInfo) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { GitHubRepository.get(context) }
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<GitHubUser?>(null) }
    var showStars by remember { mutableStateOf(false) }
    var showSecurity by remember { mutableStateOf(false) }
    var showRecentViews by remember { mutableStateOf(false) }
    var showStatPage by remember { mutableStateOf<String?>(null) } // "repos" | "followers" | "following"
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    var themeMode by remember { mutableStateOf(context.getSharedPreferences("mygithub_prefs", android.content.Context.MODE_PRIVATE).getString("theme_mode", "system") ?: "system") }
    var showThemeDialog by remember { mutableStateOf(false) }
    val themeLabel = when (themeMode) { "dark" -> "深色"; "light" -> "浅色"; else -> "跟随系统" }

    // ===== 2FA 速览卡片状态（tick 局部化，不触发整页重组） =====
    val clipboard = LocalClipboardManager.current
    var totpSecret by remember { mutableStateOf<String?>(TotpStore.getSecret(context)) }
    var totpIssuer by remember { mutableStateOf<String?>(TotpStore.getIssuer(context)) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val loginTypeLabel = remember { TokenStore.getLoginType(context)?.let { if (it == "pat") "PAT 令牌" else "Device Flow" } ?: "未知" }

    // 当从“安全中心”返回或组件初次显示时，强制重新查询最新 2FA 绑定状态，杜绝界面假死
    LaunchedEffect(showSecurity) {
        if (!showSecurity) {
            totpSecret = TotpStore.getSecret(context)
            totpIssuer = TotpStore.getIssuer(context)
        }
    }

    fun onStatClick(type: String) {
        showStatPage = type
    }

    LaunchedEffect(refreshKey) {
        if (refreshKey == 0) loading = true
        try {
            when (val r = repository.currentUser()) {
                is GitHubRepository.Result.Success -> {
                    user = r.data
                    if (refreshKey > 0) listState.scrollToItem(0)
                }
                is GitHubRepository.Result.Error -> {}
            }
        } finally {
            loading = false
            isRefreshing = false
        }
    }

    when {
        showStars -> { StarsScreen(onRepoClick = onRepoClick, onBack = { showStars = false }); return }
        showSecurity -> { com.mygithub.lab.ui.screens.security.SecurityScreen(onBack = { showSecurity = false }); return }
        showRecentViews -> { RecentViewsScreen(onRepoClick = onRepoClick, onBack = { showRecentViews = false }); return }
        showStatPage != null -> {
            StatListScreen(
                type = showStatPage!!,
                user = user,
                onRepoClick = onRepoClick,
                onBack = { showStatPage = null }
            )
            return
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("外观主题") },
            text = {
                Column {
                    listOf("system" to "跟随系统", "dark" to "深色", "light" to "浅色").forEach { (mode, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                            themeMode = mode
                            context.getSharedPreferences("mygithub_prefs", android.content.Context.MODE_PRIVATE).edit().putString("theme_mode", mode).apply()
                            showThemeDialog = false
                        }.padding(vertical = 8.dp)) {
                            RadioButton(selected = themeMode == mode, onClick = { themeMode = mode; context.getSharedPreferences("mygithub_prefs", android.content.Context.MODE_PRIVATE).edit().putString("theme_mode", mode).apply(); showThemeDialog = false })
                            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("关闭") } }
        )
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.CenterAlignedTopAppBar(
                title = { Text("个人", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showLogoutDialog = true }) { Icon(Icons.Filled.Logout, contentDescription = "退出") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                refreshKey++
            },
            state = pullState,
            indicator = {
                KomiPullRefreshIndicator(
                    state = pullState,
                    isRefreshing = isRefreshing
                )
            },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ===== 个人资料卡片 =====
            item(key = "profile_card") {
                user?.let { u ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 头像 + 名字
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // 真实头像（Coil 加载，失败 fallback 首字母）
                            if (!u.avatar_url.isNullOrBlank()) {
                                AsyncImage(
                                    model = coil3.request.ImageRequest.Builder(LocalContext.current)
                                        .data(u.avatar_url)
                                        .crossfade(false)
                                        .memoryCacheKey(u.avatar_url)
                                        .build(),
                                    contentDescription = "头像",
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    clipToBounds = true
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        u.login.take(1).uppercase(),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Column {
                                Text(
                                    u.name ?: u.login,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "@${u.login}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 统计行（可点击）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(value = "${u.public_repos}", label = "仓库", onClick = { onStatClick("repos") })
                            Divider()
                            StatItem(value = "${u.followers}", label = "关注者", onClick = { onStatClick("followers") })
                            Divider()
                            StatItem(value = "${u.following}", label = "正在关注", onClick = { onStatClick("following") })
                        }
                    }
                }
            }

            // ===== 2FA 速览卡片（key 局部化避免整列重组）（tick 局部化到 TotpQuickCard，不触发整页重组） =====
            item(key = "totp_card") {
                val s = totpSecret
                if (s != null) {
                    TotpQuickCard(
                        secret = s,
                        issuer = totpIssuer,
                        onClick = { clipboard.setText(AnnotatedString(TotpEngine.generateCode(s, System.currentTimeMillis()))) }
                    )
                } else {
                    // 未绑定：引导入口
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                            .clickable { showSecurity = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Column {
                            Text("绑定 GitHub 2FA", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "导入密钥后此处快速复制动态码",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ===== 媒体库 =====
            item(key = "media_title") {
                Text("媒体库", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            item(key = "menu_stars") {
                MenuRow(
                    icon = Icons.Filled.Star,
                    iconColor = MaterialTheme.colorScheme.tertiary,
                    title = "星标",
                    subtitle = "您在 GitHub 上加了星标的仓库",
                    onClick = { showStars = true }
                )
            }
            item(key = "menu_recent") {
                MenuRow(
                    icon = Icons.Filled.History,
                    iconColor = MaterialTheme.colorScheme.primary,
                    title = "最近查看",
                    subtitle = "你访问过的仓库",
                    onClick = { showRecentViews = true }
                )
            }

            // ===== 设置 =====
            item(key = "settings_title") {
                Text("设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            item(key = "menu_theme") {
                MenuRow(
                    icon = Icons.Filled.Palette,
                    iconColor = MaterialTheme.colorScheme.tertiary,
                    title = "外观主题",
                    subtitle = themeLabel,
                    onClick = { showThemeDialog = true }
                )
            }
            item(key = "menu_security") {
                MenuRow(
                    icon = Icons.Filled.Security,
                    iconColor = Color(0xFF4CAF50),
                    title = "安全中心",
                    subtitle = "本地 TOTP 动态码 · GitHub 二次验证",
                    onClick = { showSecurity = true }
                )
            }
            item(key = "menu_account") {
                MenuRow(
                    icon = Icons.Filled.Label,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    title = "账号",
                    subtitle = "登录方式：${loginTypeLabel}",
                    onClick = { }
                )
            }
            item(key = "menu_cache") {
                MenuRow(
                    icon = Icons.Filled.Delete,
                    iconColor = MaterialTheme.colorScheme.error,
                    title = "清理缓存",
                    subtitle = "清除仓库与榜单离线缓存",
                    onClick = { }
                )
            }
            item(key = "menu_update") {
                var checkingUpdate by remember { mutableStateOf(false) }
                MenuRow(
                    icon = Icons.Filled.SystemUpdate,
                    iconColor = MaterialTheme.colorScheme.primary,
                    title = "检查新版本",
                    subtitle = if (checkingUpdate) "正在检查更新..." else "当前版本 v0.0.1",
                    onClick = {
                        if (checkingUpdate) return@MenuRow
                        checkingUpdate = true
                        scope.launch {
                            try {
                                val update = repository.checkAppUpdate()
                                if (update != null) {
                                    onShowUpdate(update)
                                } else {
                                    android.widget.Toast.makeText(context, "已是最新版本 v0.0.1", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(context, "检查更新失败，请稍后重试", android.widget.Toast.LENGTH_SHORT).show()
                            } finally {
                                checkingUpdate = false
                            }
                        }
                    }
                )
            }
            item {
                Text(
                    "MyGitHub v0.0.1 · 开源",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出当前账号？") },
            text = { Text("退出账号仅清除云端登录凭证与本地缓存。本地绑定的 2FA 动态码与密钥将永久保留在设备中，绝不会失效。") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) { Text("退出登录", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun StatItem(value: String, label: String, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Divider() {
    Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))
}

@Composable
private fun MenuRow(
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
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconColor.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = iconColor)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant)
    }
}

// ==================== 统计列表页（仓库/关注者/正在关注） ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatListScreen(
    type: String,
    user: GitHubUser?,
    onRepoClick: (GitHubRepo) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { GitHubRepository.get(context) }
    var repos by remember { mutableStateOf<List<GitHubRepo>>(emptyList()) }
    var users by remember { mutableStateOf<List<GitHubUser>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var themeMode by remember { mutableStateOf(context.getSharedPreferences("mygithub_prefs", android.content.Context.MODE_PRIVATE).getString("theme_mode", "system") ?: "system") }
    var showThemeDialog by remember { mutableStateOf(false) }
    val themeLabel = when (themeMode) { "dark" -> "深色"; "light" -> "浅色"; else -> "跟随系统" }
    var error by remember { mutableStateOf<String?>(null) }

    val title = when (type) {
        "repos" -> "仓库"
        "followers" -> "关注者"
        "following" -> "正在关注"
        else -> ""
    }

    LaunchedEffect(type) {
        loading = true
        when (type) {
            "repos" -> {
                repository.myRepos().collect { result ->
                    when (result) {
                        is GitHubRepository.Result.Success -> { repos = result.data; error = null }
                        is GitHubRepository.Result.Error -> error = result.message
                    }
                    loading = false
                }
            }
            else -> {
                // followers / following 真实数据加载
                val username = user?.login
                if (username != null) {
                    val result = if (type == "followers") repository.getFollowers(username)
                    else repository.getFollowing(username)
                    when (result) {
                        is GitHubRepository.Result.Success -> { users = result.data; error = null }
                        is GitHubRepository.Result.Error -> error = result.message
                    }
                } else {
                    error = "用户信息未加载"
                }
                loading = false
            }
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("外观主题") },
            text = {
                Column {
                    listOf("system" to "跟随系统", "dark" to "深色", "light" to "浅色").forEach { (mode, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                            themeMode = mode
                            context.getSharedPreferences("mygithub_prefs", android.content.Context.MODE_PRIVATE).edit().putString("theme_mode", mode).apply()
                            showThemeDialog = false
                        }.padding(vertical = 8.dp)) {
                            RadioButton(selected = themeMode == mode, onClick = { themeMode = mode; context.getSharedPreferences("mygithub_prefs", android.content.Context.MODE_PRIVATE).edit().putString("theme_mode", mode).apply(); showThemeDialog = false })
                            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("关闭") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "返回", modifier = Modifier.size(24.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (error != null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (type == "repos") {
                    items(count = repos.size, key = { repos[it].id }) { index ->
                        val repo = repos[index]
                        com.mygithub.lab.ui.components.KomiRepoCard(
                            owner = repo.ownerLogin,
                            name = repo.name,
                            description = repo.description.orEmpty(),
                            language = repo.language,
                            stars = repo.stargazers_count,
                            onClick = { onRepoClick(repo) }
                        )
                    }
                } else {
                    // followers / following 用户列表
                    items(count = users.size, key = { users[it].id }) { index ->
                        val u = users[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    com.mygithub.lab.ui.components.openUrl(context, u.html_url)
                                }
                                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            coil3.compose.AsyncImage(
                                model = u.avatar_url,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                clipToBounds = true
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    u.name ?: u.login,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "@${u.login}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 最近查看历史 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentViewsScreen(
    onRepoClick: (GitHubRepo) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { GitHubRepository.get(context) }
    var recentViews by remember { mutableStateOf<List<com.mygithub.lab.data.local.RecentViewEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        recentViews = repository.getRecentViews()
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("最近查看", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "返回", modifier = Modifier.size(24.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (recentViews.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无浏览记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(count = recentViews.size, key = { recentViews[it].repo_full_name }) { index ->
                    val v = recentViews[index]
                    com.mygithub.lab.ui.components.KomiRepoCard(
                        owner = v.owner,
                        name = v.name,
                        description = v.description,
                        language = v.language.ifBlank { null },
                        stars = v.stars,
                        onClick = { onRepoClick(GitHubRepo(name = v.name, full_name = v.repo_full_name, owner = com.mygithub.lab.data.api.GitHubOwner(login = v.owner), description = v.description, language = v.language.ifBlank { null }, stargazers_count = v.stars)) }
                    )
                }
            }
        }
    }
}

/**
 * TotpQuickCard — 2FA 速览卡片（tick 状态局部化，不触发父级 ProfileScreen 重组）
 */
@Composable
private fun TotpQuickCard(
    secret: String,
    issuer: String?,
    onClick: () -> Unit
) {
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            tick = System.currentTimeMillis()
            delay(1000)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text(
                listOfNotNull(issuer, "2FA").joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.ContentCopy, contentDescription = "复制", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = TotpEngine.generateCode(secret, tick).chunked(3).joinToString(" "),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${TotpEngine.remainingSeconds(tick)}s",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { TotpEngine.remainingSeconds(tick) / 30f },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
