package com.mygithub.lab.ui.screens.rankings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mygithub.lab.data.api.GitHubRepo
import com.mygithub.lab.data.repo.GitHubRepository
import com.mygithub.lab.ui.components.KomiChip
import com.mygithub.lab.ui.components.KomiRepoCard
import com.mygithub.lab.ui.components.KomiPullRefreshIndicator
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter


private enum class Period(val label: String, val days: Int) {
    DAY("日榜", 1), WEEK("周榜", 7), MONTH("月榜", 30)
}

private val languages = listOf("全部", "Kotlin", "Java", "Python", "JavaScript", "TypeScript", "Go", "Rust", "C++", "C#", "Swift", "Dart")

/**
 * 排行榜：Trending（日/周/月榜）+ 语言过滤 + 名次徽章
 * 数据源：GitHub Search API（created/sort:stars + 近期 star 数）
 * 缓存：Room（断网可用）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingsScreen(onRepoClick: (GitHubRepo) -> Unit) {
    val context = LocalContext.current
    val repo = remember { GitHubRepository.get(context) }
    val scope = rememberCoroutineScope()

    var period by remember { mutableStateOf(Period.WEEK) }
    var language by remember { mutableStateOf("全部") }
    var rankings by remember { mutableStateOf<List<GitHubRepo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var fromCache by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()

    LaunchedEffect(period, language, refreshKey) {
        if (refreshKey == 0) loading = true
        error = null
        try {
            repo.getTrending(period.days, if (language == "全部") null else language)
                .collect { r ->
                    when (r) {
                        is GitHubRepository.Result.Success -> {
                            rankings = r.data; fromCache = r.fromCache
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
                title = {
                    Text(
                        "排行榜",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 周期芯片
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Period.entries.forEach { p ->
                    KomiChip(
                        text = p.label,
                        selected = period == p,
                        onClick = { period = p }
                    )
                }
            }
            // 语言芯片（横向滚动）
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(languages) { lang ->
                    KomiChip(
                        text = lang,
                        selected = language == lang,
                        onClick = { language = lang }
                    )
                }
            }

            // 缓存提示
            if (fromCache) {
                Text(
                    "（离线缓存）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
            } else if (error != null) {
                CenterHint(text = error!!)
            } else {
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
                    modifier = Modifier.fillMaxSize()
                ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 96.dp)
                ) {
                    items(rankings, key = { it.id }) { r ->
                        val rank = rankings.indexOf(r) + 1
                        RankingRow(rank = rank, repo = r, onClick = { onRepoClick(r) })
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun RankingRow(rank: Int, repo: GitHubRepo, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // 名次徽章
        Text(
            text = when (rank) {
                1 -> "🥇"
                2 -> "🥈"
                3 -> "🥉"
                else -> "$rank"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = when {
                rank <= 3 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(start = 16.dp).align(Alignment.CenterVertically)
        )
        KomiRepoCard(
            owner = repo.ownerLogin,
            name = repo.name,
            description = repo.description.orEmpty(),
            language = repo.language,
            stars = repo.stargazers_count,
            forks = repo.forks_count,
            updatedAt = repo.pushed_at,
            onClick = onClick,
            modifier = Modifier.weight(1f).padding(start = 0.dp)
        )
    }
}

@Composable
private fun CenterHint(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
