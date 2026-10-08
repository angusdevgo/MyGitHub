package com.mygithub.lab.ui.screens.repos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mygithub.lab.data.api.GitHubRepo
import com.mygithub.lab.data.repo.GitHubRepository
import com.mygithub.lab.ui.components.KomiPullRefreshIndicator
import com.mygithub.lab.ui.components.KomiRepoCard

private enum class RepoFilter(val label: String) { ALL("全部"), PUBLIC("公开"), PRIVATE("私有"), FORK("Fork") }

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun ReposScreen(
    onRepoClick: (GitHubRepo) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { GitHubRepository.get(context) }

    var repoFilter by remember { mutableStateOf(RepoFilter.ALL) }
    var repos by remember { mutableStateOf<List<GitHubRepo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()

    LaunchedEffect(refreshKey) {
        if (refreshKey == 0) loading = true
        error = null
        try {
            var firstEmission = true
            repo.myRepos().collect { result ->
                when (result) {
                    is GitHubRepository.Result.Success -> {
                        repos = result.data
                        error = null
                        if (firstEmission || refreshKey > 0) {
                            loading = false
                            firstEmission = false
                            isRefreshing = false
                            if (refreshKey > 0) listState.scrollToItem(0)
                        }
                    }
                    is GitHubRepository.Result.Error -> {
                        error = result.message
                        isRefreshing = false
                        loading = false
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
                title = { Text("仓库", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RepoFilter.entries.forEach { f ->
                    FilterChip(
                        selected = repoFilter == f,
                        onClick = { repoFilter = f },
                        label = { Text(f.label) }
                    )
                }
            }

            val filtered = when (repoFilter) {
                RepoFilter.ALL -> repos
                RepoFilter.PUBLIC -> repos.filter { !it.private }
                RepoFilter.PRIVATE -> repos.filter { it.private }
                RepoFilter.FORK -> repos.filter { it.fork }
            }

            if (loading && filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
            } else {
                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        refreshKey++
                    },
                    state = pullState,
                    indicator = {
                        KomiPullRefreshIndicator(state = pullState, isRefreshing = isRefreshing)
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    RepoList(repos = filtered, listState = listState, onRepoClick = onRepoClick, error = error)
                }
            }
        }
    }
}

@Composable
private fun RepoList(
    repos: List<GitHubRepo>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onRepoClick: (GitHubRepo) -> Unit,
    error: String?
) {
    if (repos.isEmpty() && error != null) {
        EmptyOrError(text = error)
    } else if (repos.isEmpty()) {
        EmptyOrError(text = "暂无仓库")
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
        ) {
            items(repos, key = { it.id }) { r ->
                KomiRepoCard(
                    owner = r.ownerLogin,
                    name = r.name,
                    description = r.description.orEmpty(),
                    language = r.language,
                    stars = r.stargazers_count,
                    forks = r.forks_count,
                    updatedAt = r.pushed_at,
                    onClick = { onRepoClick(r) }
                )
            }
        }
    }
}

@Composable
private fun EmptyOrError(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
