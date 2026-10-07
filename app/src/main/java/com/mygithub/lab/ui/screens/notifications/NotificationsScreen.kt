package com.mygithub.lab.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.mygithub.lab.data.api.GitHubNotification
import com.mygithub.lab.data.repo.GitHubRepository
import com.mygithub.lab.ui.components.KomiSurface
import com.mygithub.lab.ui.components.KomiChip
import com.mygithub.lab.ui.components.KomiPullRefreshIndicator
import com.mygithub.lab.ui.components.relativeTimeFromIso
import kotlinx.coroutines.launch

private enum class NotifFilter(val label: String) {
    ALL("全部"), UNREAD("未读"), MENTION("提及"), ASSIGN("指派")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onNavigateToRepo: (com.mygithub.lab.data.api.GitHubRepo) -> Unit,
    onNavigateToIssue: (com.mygithub.lab.data.api.GitHubRepo, com.mygithub.lab.data.api.GitHubIssue) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { GitHubRepository.get(context) }
    val scope = rememberCoroutineScope()

    var notifications by remember { mutableStateOf<List<GitHubNotification>>(emptyList()) }
    var events by remember { mutableStateOf<List<com.mygithub.lab.data.api.GitHubEvent>>(emptyList()) }
    var eventsLoaded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(NotifFilter.ALL) }
    var selectedTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) } // 0=通知 1=动态
    var refreshKey by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var issueStateMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()

    // 针对当前页面上的 Issue/PR 通知，在后台批量精准探测其实时状态
    LaunchedEffect(notifications) {
        val issueNotifs = notifications.filter { it.subject.type == "Issue" || it.subject.type == "PullRequest" }
        issueNotifs.forEach { n ->
            val cached = repo.getCachedIssueState(n.subject.url)
            if (cached != null) {
                issueStateMap = issueStateMap + (n.subject.url to cached)
            } else {
                val parts = n.subject.url.removePrefix("https://api.github.com/repos/").split("/")
                if (parts.size >= 4) {
                    val owner = parts[0]; val repoName = parts[1]; val num = parts[3].toIntOrNull()
                    if (num != null) {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val r = repo.getIssue(owner, repoName, num)
                            if (r is GitHubRepository.Result.Success) {
                                issueStateMap = issueStateMap + (n.subject.url to r.data.state)
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(refreshKey, selectedTab) {
        if (refreshKey == 0) loading = true
        error = null
        try {
            if (selectedTab == 0) {
                repo.getNotifications(all = true).collect { r ->
                    when (r) {
                        is GitHubRepository.Result.Success -> { notifications = r.data; loading = false; isRefreshing = false; if (refreshKey > 0) listState.scrollToItem(0) }
                        is GitHubRepository.Result.Error -> { error = r.message; loading = false; isRefreshing = false }
                    }
                }
            } else {
                repo.getReceivedActivity().collect { r ->
                    when (r) {
                        is GitHubRepository.Result.Success -> { events = r.data; eventsLoaded = true; loading = false; isRefreshing = false; if (refreshKey > 0) listState.scrollToItem(0) }
                        is GitHubRepository.Result.Error -> { error = r.message; loading = false; isRefreshing = false }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            isRefreshing = false
            throw e
        } catch (e: Exception) {
            error = e.message
            loading = false
            isRefreshing = false
        }
    }

    val filtered = remember(notifications, filter) {
        androidx.compose.runtime.derivedStateOf {
            when (filter) {
                NotifFilter.ALL -> notifications
                NotifFilter.UNREAD -> notifications.filter { it.unread }
                NotifFilter.MENTION -> notifications.filter { it.reason == "mention" }
                NotifFilter.ASSIGN -> notifications.filter { it.reason == "assign" }
            }
        }
    }.value

    Scaffold(
        topBar = {
            androidx.compose.material3.CenterAlignedTopAppBar(
                title = { Text("信息", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 通知 / 动态 居中分段控制器（与仓库/议题设计对齐，移出左上角）
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                val tabLabels = listOf("通知", "动态")
                tabLabels.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        shape = SegmentedButtonDefaults.itemShape(index, tabLabels.size)
                    ) {
                        Text(label, fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }

            // 通知 filter chips（居中排列，仅通知 Tab 显示）
            if (selectedTab == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NotifFilter.entries.forEach { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(f.label) }
                        )
                    }
                }
            }
            when {
                loading && !eventsLoaded && notifications.isEmpty() -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                selectedTab == 0 && filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无通知", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                selectedTab == 1 && eventsLoaded && events.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无动态", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> androidx.compose.material3.pulltorefresh.PullToRefreshBox(
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
                    modifier = Modifier.fillMaxSize()
                ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
                ) {
                    if (selectedTab == 0) {
                        items(filtered, key = { it.id }) { n ->
                            NotificationCard(n, realState = issueStateMap[n.subject.url]) {
                                // 1. 乐观消除未读蓝点 + 持久化并通知云端
                                if (n.unread) {
                                    notifications = notifications.map {
                                        if (it.id == n.id) it.copy(unread = false) else it
                                    }
                                    scope.launch {
                                        repo.markNotificationAsRead(n)
                                    }
                                }
                                // 2. 软件内跳转（0ms 秒级推入详情页，完全零等待）
                                when (n.subject.type) {
                                    "Issue", "PullRequest" -> {
                                        val parts = n.subject.url.removePrefix("https://api.github.com/repos/").split("/")
                                        if (parts.size >= 4) {
                                            val owner = parts[0]
                                            val repoName = parts[1]
                                            val num = parts[3].toIntOrNull()
                                            if (num != null) {
                                                val fastIssue = com.mygithub.lab.data.api.GitHubIssue(
                                                    number = num,
                                                    title = n.subject.title,
                                                    state = issueStateMap[n.subject.url] ?: if (n.reason == "state_change") "closed" else "open",
                                                    repository_url = "https://api.github.com/repos/$owner/$repoName",
                                                    created_at = n.updated_at
                                                )
                                                val fastRepo = com.mygithub.lab.data.api.GitHubRepo(
                                                    name = repoName,
                                                    full_name = "$owner/$repoName"
                                                )
                                                onNavigateToIssue(fastRepo, fastIssue)
                                            }
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    } else {
                        items(events, key = { it.id }) { e ->
                            EventCard(e)
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(n: GitHubNotification, realState: String? = null, onClick: () -> Unit) {
    val isIssue = n.subject.type == "Issue"
    val isPR = n.subject.type == "PullRequest"
    // 优先采用实测查询的真实最新状态，其次使用启发式规则
    val isClosed = if (realState != null) {
        realState.equals("closed", ignoreCase = true)
    } else {
        n.reason == "state_change" ||
            n.subject.title.contains("closed", ignoreCase = true) ||
            n.subject.title.contains("close", ignoreCase = true)
    }

    val typeIcon = when (n.subject.type) {
        "Issue" -> if (isClosed) "🟣" else "🟢"
        "PullRequest" -> "🔀"
        "Release" -> "🏷️"
        "Commit" -> "📦"
        else -> "🔔"
    }
    val reasonText = when (n.reason) {
        "mention" -> "提及了你"
        "assign" -> "指派给你"
        "review_requested" -> "请求你评审"
        "author" -> "你创建的"
        "comment" -> "有新评论"
        "ci_activity" -> "CI 状态"
        "approval_requested" -> "请求批准"
        "state_change" -> if (isClosed) "状态变更为 Closed" else "状态变更"
        "subscribed" -> "订阅更新"
        "team_mention" -> "团队提及"
        "security_alert" -> "安全警报"
        "invitation" -> "邀请"
        "member_feature_requested" -> "功能请求"
        else -> n.reason
    }
    val reasonIcon = when (n.reason) {
        "mention" -> "@"
        "assign" -> "👤"
        "review_requested" -> "👀"
        "author" -> "✏️"
        "comment" -> "💬"
        "ci_activity" -> "⚙️"
        "approval_requested" -> "✅"
        "state_change" -> "🔄"
        "subscribed" -> "🔔"
        "team_mention" -> "👥"
        "security_alert" -> "🔒"
        "invitation" -> "✉️"
        else -> "📌"
    }
    KomiSurface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(
                model = coil3.request.ImageRequest.Builder(LocalContext.current)
                    .data(n.repository.owner.avatar_url)
                    .crossfade(false)
                    .memoryCacheKey(n.repository.owner.avatar_url)
                    .build(),
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(typeIcon, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = n.repository.full_name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    // Issue/PR 状态微型指示胶囊
                    if (isIssue) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isClosed) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                    else androidx.compose.ui.graphics.Color(0xFF4CAF50).copy(alpha = 0.15f)
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = if (isClosed) "Closed" else "Open",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isClosed) MaterialTheme.colorScheme.tertiary else androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (isPR) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "PR",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (n.unread) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                Text(
                    text = n.subject.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("$reasonIcon $reasonText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val timeText = remember(n.updated_at) { relativeTimeFromIso(n.updated_at) }
                    Text(timeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        }
    }
}
@Composable
private fun EventCard(e: com.mygithub.lab.data.api.GitHubEvent) {
    val isIssueEvent = e.type == "IssuesEvent"
    val isClosed = e.payload.action == "closed"
    val isOpened = e.payload.action == "opened"

    val (icon, actionText) = when (e.type) {
        "PushEvent" -> "📤" to "推送了提交"
        "WatchEvent" -> "⭐" to "Star 了仓库"
        "ForkEvent" -> "⑂" to "Fork 了仓库"
        "IssuesEvent" -> (if (isClosed) "🟣" else "🟢") to when (e.payload.action) {
            "opened" -> "开启了 Issue"
            "closed" -> "关闭了 Issue"
            else -> "更新了 Issue"
        }
        "IssueCommentEvent" -> "💬" to "评论了 Issue"
        "PullRequestEvent" -> "🔀" to when (e.payload.action) { "opened" -> "创建了 PR"; "closed" -> "关闭了 PR"; else -> "更新了 PR" }
        "CreateEvent" -> "✨" to "创建了 ${e.payload.ref_type ?: "仓库"}"
        "ReleaseEvent" -> "🏷️" to "发布了新版本"
        "DeleteEvent" -> "🗑️" to "删除了 ${e.payload.ref_type ?: "分支"}"
        else -> "📌" to e.type
    }
    KomiSurface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(
                model = coil3.request.ImageRequest.Builder(LocalContext.current)
                    .data(e.actor.avatar_url)
                    .crossfade(false)
                    .memoryCacheKey(e.actor.avatar_url)
                    .build(),
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(icon, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = e.actor.login,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isIssueEvent) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isClosed) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                    else androidx.compose.ui.graphics.Color(0xFF4CAF50).copy(alpha = 0.15f)
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = if (isClosed) "Closed" else if (isOpened) "Open" else "Updated",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isClosed) MaterialTheme.colorScheme.tertiary else androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = e.repo.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = remember(e.created_at) { relativeTimeFromIso(e.created_at) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
