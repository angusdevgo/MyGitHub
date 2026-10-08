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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.mygithub.lab.data.api.GitHubEvent
import com.mygithub.lab.data.api.GitHubNotification
import com.mygithub.lab.data.model.IssueRef
import com.mygithub.lab.data.model.OwnedRepoSummary
import com.mygithub.lab.data.repo.GitHubRepository
import com.mygithub.lab.ui.components.KomiPullRefreshIndicator
import com.mygithub.lab.ui.components.KomiSurface
import com.mygithub.lab.ui.components.relativeTimeFromIso
import kotlinx.coroutines.launch

private enum class NotifFilter(val label: String) {
    ALL("全部"), UNREAD("未读"), MENTION("提及"), ASSIGN("指派")
}

/** 从通知的 subject.url 解析出 IssueRef（同时支持 /issues/N 与 /pulls/N） */
internal fun parseIssueRef(url: String): IssueRef? {
    if (url.isBlank()) return null
    val prefix = "https://api.github.com/repos/"
    if (!url.startsWith(prefix)) return null
    val parts = url.removePrefix(prefix).split("/")
    if (parts.size < 4) return null
    val number = parts[3].toIntOrNull() ?: return null
    val kind = parts[2]
    if (kind != "issues" && kind != "pulls") return null
    return IssueRef(
        url = url,
        owner = parts[0],
        repo = parts[1],
        number = number,
        isPr = kind == "pulls"
    )
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
    var events by remember { mutableStateOf<List<GitHubEvent>>(emptyList()) }
    var summary by remember { mutableStateOf<OwnedRepoSummary?>(null) }
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

    // ===== 批量解析 Issue/PR 实时状态（GraphQL，替代逐条 N+1 请求） =====
    val notificationUrls = remember(notifications) { notifications.map { it.subject.url } }
    LaunchedEffect(notificationUrls, refreshKey) {
        val refs = notifications.mapNotNull { parseIssueRef(it.subject.url) }
        if (refs.isEmpty()) return@LaunchedEffect
        // 先填缓存中仍然有效的状态，保证界面不闪烁
        val cached = refs.mapNotNull { r ->
            repo.getCachedIssueState(r.url)?.let { r.url to it }
        }.toMap()
        if (cached.isNotEmpty()) issueStateMap = issueStateMap + cached

        val resolved = repo.resolveIssueStates(refs)
        if (resolved.isNotEmpty()) issueStateMap = issueStateMap + resolved
    }

    // ===== 拉取通知 / 动态 =====
    LaunchedEffect(refreshKey, selectedTab) {
        if (refreshKey == 0) loading = true
        error = null
        try {
            if (selectedTab == 0) {
                repo.getNotifications(all = true, maxPages = 2).collect { r ->
                    when (r) {
                        is GitHubRepository.Result.Success -> {
                            notifications = r.data
                            loading = false
                            isRefreshing = false
                            if (refreshKey > 0) listState.scrollToItem(0)
                        }
                        is GitHubRepository.Result.Error -> {
                            error = r.message
                            loading = false
                            isRefreshing = false
                        }
                    }
                }
            } else {
                repo.getReceivedActivity().collect { r ->
                    when (r) {
                        is GitHubRepository.Result.Success -> {
                            events = r.data
                            eventsLoaded = true
                            loading = false
                            isRefreshing = false
                            if (refreshKey > 0) listState.scrollToItem(0)
                        }
                        is GitHubRepository.Result.Error -> {
                            error = r.message
                            eventsLoaded = true
                            loading = false
                            isRefreshing = false
                        }
                    }
                }
                // 总量摘要（数值准确，不依赖事件流）
                when (val s = repo.getOwnedRepoSummary()) {
                    is GitHubRepository.Result.Success -> summary = s.data
                    is GitHubRepository.Result.Error -> {}
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
        derivedStateOf {
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

                error != null && notifications.isEmpty() && events.isEmpty() -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                    }
                }

                selectedTab == 0 && filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无通知", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                selectedTab == 1 && eventsLoaded && events.isEmpty() -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Text("暂无动态", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                        Text(
                            "GitHub 事件流仅保留 30 天内、最多 300 条事件，且存在 30 秒 ~ 6 小时的同步延迟。\n" +
                                "你的自有仓库近期暂无 star / fork / issue / PR 活动。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                else -> androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        repo.invalidateIssueStates()
                        issueStateMap = emptyMap()
                        refreshKey++
                    },
                    state = pullState,
                    indicator = {
                        KomiPullRefreshIndicator(state = pullState, isRefreshing = isRefreshing)
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
                                    // 1. 乐观消除未读蓝点 + 持久化覆盖表并通知云端
                                    if (n.unread) {
                                        notifications = notifications.map {
                                            if (it.id == n.id) it.copy(unread = false) else it
                                        }
                                        scope.launch { repo.markNotificationAsRead(n) }
                                    }
                                    // 2. 软件内 0ms 秒级推入详情页
                                    when (n.subject.type) {
                                        "Issue", "PullRequest" -> {
                                            val ref = parseIssueRef(n.subject.url)
                                            if (ref != null) {
                                                val fastIssue = com.mygithub.lab.data.api.GitHubIssue(
                                                    number = ref.number,
                                                    title = n.subject.title,
                                                    // 仅使用真实解析出的状态；未知则默认 open 但界面不显示状态胶囊
                                                    state = issueStateMap[n.subject.url] ?: "open",
                                                    repository_url = "https://api.github.com/repos/${ref.owner}/${ref.repo}",
                                                    created_at = n.updated_at
                                                )
                                                val fastRepo = com.mygithub.lab.data.api.GitHubRepo(
                                                    name = ref.repo,
                                                    full_name = "${ref.owner}/${ref.repo}"
                                                )
                                                onNavigateToIssue(fastRepo, fastIssue)
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        } else {
                            item(key = "activity_summary") {
                                ActivitySummaryCard(summary, events.size)
                            }
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

/** 动态 Tab 顶部总量摘要（数值来自仓库对象，永远准确） */
@Composable
private fun ActivitySummaryCard(summary: OwnedRepoSummary?, eventCount: Int) {
    KomiSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "自有仓库概览",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            if (summary == null) {
                Text(
                    "统计加载中...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryMetric("总 Stars", summary.totalStars.toString())
                    SummaryMetric("总 Forks", summary.totalForks.toString())
                    SummaryMetric("Open Issues", summary.totalOpenIssues.toString())
                }
                if (summary.starsDelta > 0 || summary.forksDelta > 0) {
                    Text(
                        "自上次查看：+${summary.starsDelta} Stars · +${summary.forksDelta} Forks",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    "共 ${summary.repoCount} 个自有仓库 · 近期事件 $eventCount 条（事件流有延迟）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatePill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun NotificationCard(n: GitHubNotification, realState: String? = null, onClick: () -> Unit) {
    val isIssue = n.subject.type == "Issue"
    val isPR = n.subject.type == "PullRequest"

    // 仅使用真实解析出的状态，绝不用标题启发式猜测
    val statePill: Pair<String, Color>? = when (realState) {
        "open" -> "Open" to Color(0xFF4CAF50)
        "closed" -> "Closed" to MaterialTheme.colorScheme.tertiary
        "merged" -> "Merged" to Color(0xFF8250DF)
        else -> null
    }

    val typeIcon = when (n.subject.type) {
        "Issue" -> if (realState == "closed") "🟣" else "🟢"
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
        "state_change" -> "状态变更"
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
                    if (isIssue || isPR) {
                        if (statePill != null) {
                            StatePill(statePill.first, statePill.second)
                        } else {
                            // 状态尚未解析出来：显示中性提示，不猜测
                            StatePill("同步中", MaterialTheme.colorScheme.outline)
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
private fun EventCard(e: GitHubEvent) {
    val isIssueEvent = e.type == "IssuesEvent"
    val isClosed = e.payload.action == "closed"
    val isOpened = e.payload.action == "opened"

    val (icon, actionText) = when (e.type) {
        "WatchEvent" -> "⭐" to "Star 了仓库"
        "ForkEvent" -> "⑂" to "Fork 了仓库"
        "IssuesEvent" -> (if (isClosed) "🟣" else "🟢") to when (e.payload.action) {
            "opened" -> "开启了 Issue"
            "closed" -> "关闭了 Issue"
            else -> "更新了 Issue"
        }
        "IssueCommentEvent" -> "💬" to "评论了 Issue"
        "PullRequestEvent" -> "🔀" to when (e.payload.action) {
            "opened" -> "创建了 PR"
            "closed" -> "关闭了 PR"
            else -> "更新了 PR"
        }
        "ReleaseEvent" -> "🏷️" to "发布了新版本"
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
                        StatePill(
                            text = if (isClosed) "Closed" else if (isOpened) "Open" else "Updated",
                            color = if (isClosed) MaterialTheme.colorScheme.tertiary else Color(0xFF4CAF50)
                        )
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
