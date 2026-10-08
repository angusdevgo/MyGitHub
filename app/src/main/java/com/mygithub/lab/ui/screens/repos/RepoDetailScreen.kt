package com.mygithub.lab.ui.screens.repos
import androidx.compose.foundation.isSystemInDarkTheme

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mygithub.lab.data.api.GitHubIssue
import com.mygithub.lab.data.api.GitHubRelease
import com.mygithub.lab.data.api.GitHubRepo
import com.mygithub.lab.data.repo.GitHubRepository
import com.mygithub.lab.data.repo.buildRepoDetailHtml
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    repo: GitHubRepo,
    onIssueClick: (GitHubIssue) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { GitHubRepository.get(context) }
    val prefs = context.getSharedPreferences("mygithub_prefs", android.content.Context.MODE_PRIVATE)
    val themeMode = prefs.getString("theme_mode", "system") ?: "system"
    val isDark = when (themeMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val scope = rememberCoroutineScope()

    var pageHtml by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var isStarred by remember { mutableStateOf(false) }
    var releases by remember { mutableStateOf<List<GitHubRelease>>(emptyList()) }
    var issues by remember { mutableStateOf<List<GitHubIssue>>(emptyList()) }
    var showReleasesSheet by remember { mutableStateOf(false) }
    var showIssuesSheet by remember { mutableStateOf(false) }

    // Fork 对话框状态
    var showForkDialog by remember { mutableStateOf(false) }
    var forkRepoName by remember { mutableStateOf(repo.name) }
    var forkDefaultBranchOnly by remember { mutableStateOf(true) }
    var forkLoading by remember { mutableStateOf(false) }

    // 局部即时维护的 star 计数与星标状态（乐观更新，零卡顿）
    var liveStarCount by remember(repo.stargazers_count) { mutableStateOf(repo.stargazers_count) }

    LaunchedEffect(repo.full_name, isDark) {
        loading = true
        // 写入最近查看历史
        scope.launch { repository.addRecentView(repo) }
        // 4 个请求并行加载（原串行~4×单请求 → 现~1×单请求）
        val bundle = repository.loadRepoDetail(repo.ownerLogin, repo.name, dark = isDark)
        isStarred = bundle.isStarred
        releases = bundle.releases
        issues = bundle.issues
        pageHtml = buildRepoDetailHtml(repo, bundle.readme, isStarred, releases.size, releases.firstOrNull()?.tag_name, dark = isDark)
        loading = false
    }

    BackHandler(enabled = true) { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, repo.html_url)
                        }
                        context.startActivity(android.content.Intent.createChooser(send, "分享"))
                    }) { Icon(Icons.Filled.Share, contentDescription = "分享") }
                    IconButton(onClick = {
                        com.mygithub.lab.ui.components.openUrl(context, repo.html_url)
                    }) { Icon(Icons.Filled.ExitToApp, contentDescription = "浏览器") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                AndroidView(
                    factory = { ctx ->
                        val owner = repo.ownerLogin
                        val repoName = repo.name
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.loadsImagesAutomatically = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"
                            webChromeClient = android.webkit.WebChromeClient()
                            setBackgroundColor(android.graphics.Color.parseColor("#111316"))
                            isVerticalScrollBarEnabled = true
                            isHorizontalScrollBarEnabled = false
                                    settings.layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.NARROW_COLUMNS
                                    settings.layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.NARROW_COLUMNS
                                    settings.layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.NARROW_COLUMNS
                                    settings.layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.NARROW_COLUMNS
                                    settings.layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.NARROW_COLUMNS
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                    val rawUrl = request?.url?.toString() ?: return false
                                    return when {
                                        // 内部操作
                                        rawUrl == "mygithub://star" -> {
                                            // 1. 立即执行乐观更新：无需等网络返回，DOM 秒级切换高亮与计数值
                                            val targetStarred = !isStarred
                                            isStarred = targetStarred
                                            if (targetStarred) liveStarCount += 1 else liveStarCount = (liveStarCount - 1).coerceAtLeast(0)
                                            val jsAction = if (targetStarred) {
                                                """
                                                (function() {
                                                    var b = document.getElementById('star-btn');
                                                    if (b) { b.classList.add('starred'); }
                                                    var l = document.getElementById('star-label');
                                                    if (l) { l.innerText = '已标星'; }
                                                    var c = document.getElementById('star-count');
                                                    if (c) { c.innerText = '$liveStarCount'; }
                                                })();
                                                """.trimIndent()
                                            } else {
                                                """
                                                (function() {
                                                    var b = document.getElementById('star-btn');
                                                    if (b) { b.classList.remove('starred'); }
                                                    var l = document.getElementById('star-label');
                                                    if (l) { l.innerText = '标星'; }
                                                    var c = document.getElementById('star-count');
                                                    if (c) { c.innerText = '$liveStarCount'; }
                                                })();
                                                """.trimIndent()
                                            }
                                            view?.evaluateJavascript(jsAction, null)

                                            // 2. 后台异步提交网络请求，失败则静默回滚
                                            scope.launch {
                                                val success = if (targetStarred) repository.starRepo(owner, repoName)
                                                else repository.unstarRepo(owner, repoName)
                                                if (!success) {
                                                    // 回滚
                                                    isStarred = !targetStarred
                                                    if (isStarred) liveStarCount += 1 else liveStarCount = (liveStarCount - 1).coerceAtLeast(0)
                                                    val rollbackJs = if (isStarred) {
                                                        "document.getElementById('star-btn')?.classList.add('starred');document.getElementById('star-label')&&(document.getElementById('star-label').innerText='已标星');"
                                                    } else {
                                                        "document.getElementById('star-btn')?.classList.remove('starred');document.getElementById('star-label')&&(document.getElementById('star-label').innerText='标星');"
                                                    }
                                                    view?.evaluateJavascript(rollbackJs, null)
                                                    android.widget.Toast.makeText(context, "操作失败，已还原", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            true
                                        }
                                        rawUrl == "mygithub://fork" -> {
                                            forkRepoName = repo.name
                                            forkDefaultBranchOnly = true
                                            showForkDialog = true
                                            true
                                        }
                                        rawUrl == "mygithub://issues" -> { showIssuesSheet = true; true }
                                        rawUrl == "mygithub://releases" -> { showReleasesSheet = true; true }
                                        // README 其他语言文件
                                        rawUrl.lowercase().let { it.endsWith(".md") || it.endsWith(".rst") || it.endsWith(".txt") } -> {
                                            val filePath = rawUrl.substringAfterLast("/")
                                            scope.launch {
                                                val md = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repository.getReadmeFile(owner, repoName, filePath) }
                                                if (md != null) view?.post { view?.loadDataWithBaseURL("https://github.com/", md, "text/html", "utf-8", null) }
                                            }
                                            true
                                        }
                                        // 页内锚点不拦截
                                        rawUrl.startsWith("#") || (rawUrl.contains("#") && !rawUrl.startsWith("http")) -> false
                                        // 其余全部外部浏览器
                                        else -> {
                                            com.mygithub.lab.ui.components.openUrl(view?.context ?: context, rawUrl)
                                            true
                                        }
                                    }
                                }
                            }
                            pageHtml?.let { loadDataWithBaseURL("https://github.com/", it, "text/html", "utf-8", null) }
                        }
                    },
                    update = { view ->
                        pageHtml?.let { view.loadDataWithBaseURL("https://github.com/", it, "text/html", "utf-8", null) }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Releases 抽屉
    if (showReleasesSheet) {
        ModalBottomSheet(onDismissRequest = { showReleasesSheet = false }, sheetState = rememberModalBottomSheetState()) {
            ReleasesSheetContent(releases = releases, context = context)
        }
    }

    // Issues 抽屉
    if (showIssuesSheet) {
        ModalBottomSheet(onDismissRequest = { showIssuesSheet = false }, sheetState = rememberModalBottomSheetState()) {
            IssuesSheetContent(issues = issues, onIssueClick = { showIssuesSheet = false; onIssueClick(it) })
        }
    }

    // Fork 对话框（原生客户端支持，如同 Web 一样设定仓库名与主分支限制）
    if (showForkDialog) {
        AlertDialog(
            onDismissRequest = { if (!forkLoading) showForkDialog = false },
            title = {
                Text(
                    text = "复刻仓库 (Fork)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "将 ${repo.full_name} 复制到您的个人 GitHub 账号下作为新仓库。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = forkRepoName,
                        onValueChange = { forkRepoName = it.trim() },
                        label = { Text("仓库名称") },
                        placeholder = { Text(repo.name) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { forkDefaultBranchOnly = !forkDefaultBranchOnly }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = forkDefaultBranchOnly,
                            onCheckedChange = { forkDefaultBranchOnly = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "仅复制默认分支 (${repo.default_branch.ifBlank { "main" }})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (forkRepoName.isBlank()) return@Button
                        forkLoading = true
                        scope.launch {
                            val res = repository.forkRepo(
                                owner = repo.ownerLogin,
                                repo = repo.name,
                                customName = forkRepoName,
                                defaultBranchOnly = forkDefaultBranchOnly
                            )
                            forkLoading = false
                            showForkDialog = false
                            when (res) {
                                is GitHubRepository.Result.Success -> {
                                    android.widget.Toast.makeText(
                                        context,
                                        "已成功发起 Fork: ${res.data.full_name}",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                                is GitHubRepository.Result.Error -> {
                                    android.widget.Toast.makeText(
                                        context,
                                        res.message,
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    },
                    enabled = !forkLoading && forkRepoName.isNotBlank()
                ) {
                    if (forkLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("创建 Fork")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showForkDialog = false },
                    enabled = !forkLoading
                ) {
                    Text("取消")
                }
            }
        )
    }
}

// ==================== Sheet 组件 ====================

@Composable
private fun ReleasesSheetContent(releases: List<GitHubRelease>, context: android.content.Context) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("版本发布 (Releases)", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 14.dp))
        if (releases.isEmpty()) {
            Text("暂无 Release", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(20.dp)) {
                items(releases, key = { it.id }) { rel ->
                    Column(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(rel.tag_name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            if (rel.prerelease) Text("Pre", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                        rel.name?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
                        rel.assets.take(3).forEach { asset ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { com.mygithub.lab.ui.components.openUrl(context, asset.browser_download_url) }.padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                                Text("${asset.name} (${asset.size/1024/1024}MB)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IssuesSheetContent(issues: List<GitHubIssue>, onIssueClick: (GitHubIssue) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("议题 (Issues)", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 14.dp))
        if (issues.isEmpty()) {
            Text("暂无议题", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(20.dp)) {
                items(issues, key = { it.id }) { issue ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onIssueClick(issue) }.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(issue.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("#${issue.number} · 💬 ${issue.comments}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
