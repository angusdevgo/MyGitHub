package com.mygithub.lab.ui.screens.info

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.mygithub.lab.data.model.OwnedIssueItem
import com.mygithub.lab.data.model.OwnedRepoSummary
import com.mygithub.lab.data.repo.GitHubRepository
import com.mygithub.lab.ui.components.KomiPullRefreshIndicator
import com.mygithub.lab.ui.components.KomiSurface
import com.mygithub.lab.ui.components.relativeTimeFromIso

/** 「议题」分段的状态筛选：All 置前 */
private enum class IssueStateFilter(val label: String, val apiValue: String) {
    ALL("All", "all"),
    OPEN("Open", "open"),
    CLOSED("Closed", "closed")
}

/** 信息 Tab 的分段：议题优先 */
private const val TAB_ISSUES = 0
private const val TAB_ACTIVITY = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(
    onNavigateToIssue: (com.mygithub.lab.data.api.GitHubRepo, com.mygithub.lab.data.api.GitHubIssue) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { GitHubRepository.get(context) }

    // ===== 议题 =====
    var ownedIssues by remember { mutableStateOf<List<OwnedIssueItem>>(emptyList()) }
    var issuesLoaded by remember { mutableStateOf(false) }
    var issueStateFilter by remember { mutableStateOf(IssueStateFilter.ALL) }

    // ===== 动态 =====
    var events by remember { mutableStateOf<List<GitHubEvent>>(emptyList()) }
    var summary by remember { mutableStateOf<OwnedRepoSummary?>(null) }
    var eventsLoaded by remember { mutableStateOf(false) }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(TAB_ISSUES) }
    var refreshKey by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()

    // ===== 数据拉取 =====
    LaunchedEffect(refreshKey, selectedTab, issueStateFilter) {
        if (refreshKey == 0) loading = true
        error = null
        try {
            if (selectedTab == TAB_ISSUES) {
                repo.getAllOwnedIssues(state = issueStateFilter.apiValue).collect { r ->
                    when (r) {
                        is GitHubRepository.Result.Success -> {
                            ownedIssues = r.data
                            issuesLoaded = true
                            loading = false
                            isRefreshing = false
                            if (refreshKey > 0) listState.scrollToItem(0)
                        }
                        is GitHubRepository.Result.Error -> {
                            error = r.message
                            issuesLoaded = true
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
                // 总量摘要（数值来自仓库对象，永远准确）
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
                val tabLabels = listOf("议题", "动态")
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

            if (selectedTab == TAB_ISSUES) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IssueStateFilter.entries.forEach { f ->
                        FilterChip(
                            selected = issueStateFilter == f,
                            onClick = { issueStateFilter = f },
                            label = { Text(f.label) }
                        )
                    }
                }
            }

            val currentTabEmpty = when (selectedTab) {
                TAB_ISSUES -> ownedIssues.isEmpty()
                else -> events.isEmpty()
            }
            val currentTabLoaded = when (selectedTab) {
                TAB_ISSUES -> issuesLoaded
                else -> eventsLoaded
            }

            when {
                loading && !currentTabLoaded && currentTabEmpty -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }

                error != null && currentTabEmpty -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }

                selectedTab == TAB_ISSUES && issuesLoaded && ownedIssues.isEmpty() -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Text(
                            "自有仓库暂无该状态的议题",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "当前筛选：${issueStateFilter.label}。切换上方芯片可查看其他状态。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                selectedTab == TAB_ACTIVITY && eventsLoaded && events.isEmpty() -> Box(
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
                        repo.invalidateOwnedReposCache()
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
                        if (selectedTab == TAB_ISSUES) {
                            items(ownedIssues, key = { it.cacheKey }) { item ->
                                OwnedIssueCard(item) {
                                    val parts = item.repoFullName.split("/")
                                    if (parts.size == 2) {
                                        val fastRepo = com.mygithub.lab.data.api.GitHubRepo(
                                            name = parts[1],
                                            full_name = item.repoFullName
                                        )
                                        val fastIssue = com.mygithub.lab.data.api.GitHubIssue(
                                            number = item.number,
                                            title = item.title,
                                            state = item.state,
                                            comments = item.comments,
                                            updated_at = item.updatedAt,
                                            html_url = item.htmlUrl,
                                            repository_url = "https://api.github.com/repos/${item.repoFullName}"
                                        )
                                        onNavigateToIssue(fastRepo, fastIssue)
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

/** 状态胶囊（Open 绿 / Closed 灰 / Merged 紫） */
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

/** 「议题」卡片：自有仓库的 Issue / PR */
@Composable
private fun OwnedIssueCard(item: OwnedIssueItem, onClick: () -> Unit) {
    val statePill: Pair<String, Color> = when (item.displayState) {
        "merged" -> "Merged" to Color(0xFF8250DF)
        "closed" -> "Closed" to MaterialTheme.colorScheme.tertiary
        else -> "Open" to Color(0xFF4CAF50)
    }

    KomiSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (item.isPr) "🔀" else "📄",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.repoFullName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "#${item.number}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.isPr) {
                        StatePill("PR", MaterialTheme.colorScheme.primary)
                    }
                    StatePill(statePill.first, statePill.second)
                }
                Text(
                    text = item.title.ifBlank { "(无标题)" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (item.comments > 0) {
                        Text(
                            "💬 ${item.comments}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = remember(item.updatedAt) { relativeTimeFromIso(item.updatedAt) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
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

/** 「动态」卡片：别人对你仓库的 star / fork / issue / PR / release 操作 */
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
