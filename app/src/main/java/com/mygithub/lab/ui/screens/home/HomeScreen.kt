package com.mygithub.lab.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mygithub.lab.data.api.GitHubRepo
import com.mygithub.lab.data.repo.GitHubRepository
import com.mygithub.lab.ui.components.KomiChip
import com.mygithub.lab.ui.components.KomiRepoCard
import com.mygithub.lab.ui.components.KomiPullRefreshIndicator
import kotlinx.coroutines.launch

/**
 * 首页：个性化推荐流
 * 端侧算法：基于 Star 历史的语言偏好 + Topic 偏好，调用 GitHub Search 拉推荐候选
 * 规则：已 Star 的项目不再重复推荐
 */
private data class HomeCategory(val label: String, val query: String)

private val homeCategories = listOf(
    HomeCategory("推荐", ""),
    HomeCategory("热门", "stars:>5000 pushed:>2026-09-01"),
    HomeCategory("Kotlin", "language:kotlin stars:>1000 pushed:>2026-06-01"),
    HomeCategory("Python", "language:python stars:>1000 pushed:>2026-06-01"),
    HomeCategory("Rust", "language:rust stars:>500 pushed:>2026-06-01"),
    HomeCategory("Go", "language:go stars:>500 pushed:>2026-06-01"),
    HomeCategory("TypeScript", "language:typescript stars:>1000 pushed:>2026-06-01"),
    HomeCategory("文档", "stars:>2000 pushed:>2026-01-01 topic:documentation")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onRepoClick: (GitHubRepo) -> Unit, onSearchClick: () -> Unit = {}) {
    val context = LocalContext.current
    val repo = remember { GitHubRepository.get(context) }
    val scope = rememberCoroutineScope()

    var recommendations by remember { mutableStateOf<List<GitHubRepo>>(emptyList()) }
    var topProfile by remember { mutableStateOf<Pair<String?, String?>>(null to null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(0) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()

    LaunchedEffect(refreshKey, selectedCategory) {
        if (refreshKey == 0) loading = true
        error = null
        try {
            if (homeCategories[selectedCategory].query.isEmpty()) {
                repo.getRecommendations().collect { r ->
                    when (r) {
                        is GitHubRepository.Result.Success -> {
                            recommendations = r.data
                            if (selectedCategory == 0) topProfile = repo.getTopProfile()
                            loading = false
                            isRefreshing = false
                            if (refreshKey > 0) listState.scrollToItem(0)
                        }
                        is GitHubRepository.Result.Error -> { error = r.message; loading = false; isRefreshing = false }
                    }
                }
            } else {
                when (val r = repo.searchReposByQuery(homeCategories[selectedCategory].query)) {
                    is GitHubRepository.Result.Success -> {
                        recommendations = r.data; error = null
                        if (refreshKey > 0) listState.scrollToItem(0)
                    }
                    is GitHubRepository.Result.Error -> error = r.message
                }
                loading = false
                isRefreshing = false
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
                        "推荐",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
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
            // 顶部分类芯片（微调间距更紧凑）
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(homeCategories.size) { index ->
                    KomiChip(
                        text = homeCategories[index].label,
                        selected = selectedCategory == index
                    ) { selectedCategory = index; recommendations = emptyList() }
                }
            }
            if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
            } else if (error != null) {
                CenterHint(text = error!!)
            } else if (recommendations.isEmpty()) {
                CenterHint(text = "暂无推荐，先去 Star 一些项目吧")
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 96.dp)
                ) {
                    items(recommendations, key = { it.id }) { r ->
                        val badge = recommendReasonFor(r, emptyMap(), topProfile)
                        KomiRepoCard(
                            owner = r.ownerLogin,
                            name = r.name,
                            description = r.description.orEmpty(),
                            language = r.language,
                            stars = r.stargazers_count,
                            forks = r.forks_count,
                            updatedAt = r.pushed_at,
                            badge = badge,
                            onClick = { onRepoClick(r) }
                        )
                    }
                }
                }
            }
        }
    }
}

/** 推荐标签推导（作为卡片右上角精致角标） */
private fun recommendReasonFor(
    repo: GitHubRepo,
    reasons: Map<String, String>,
    topProfile: Pair<String?, String?>
): String? {
    val (topLang, topTopic) = topProfile
    return when {
        topTopic != null && repo.topics.contains(topTopic) -> "#$topTopic"
        topLang != null && repo.language == topLang -> "$topLang 精选"
        else -> null
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
