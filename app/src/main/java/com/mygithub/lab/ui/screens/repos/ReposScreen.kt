package com.mygithub.lab.ui.screens.repos
import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import com.mygithub.lab.data.api.GitHubIssue
import com.mygithub.lab.data.api.GitHubRepo
import com.mygithub.lab.data.repo.GitHubRepository
import com.mygithub.lab.ui.components.KomiRepoCard
import com.mygithub.lab.ui.components.KomiSurface
import com.mygithub.lab.ui.components.KomiPullRefreshIndicator
import kotlinx.coroutines.launch

private enum class Section(val label: String) { REPOS("仓库"), ISSUES("Issues") }
private enum class RepoFilter(val label: String) { ALL("全部"), PUBLIC("公开"), PRIVATE("私有"), FORK("Fork") }

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun ReposScreen(
    onRepoClick: (GitHubRepo) -> Unit,
    onIssueClick: (GitHubIssue) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { GitHubRepository.get(context) }
    val scope = rememberCoroutineScope()

    var section by remember { mutableStateOf(Section.REPOS) }
    var repoFilter by remember { mutableStateOf(RepoFilter.ALL) }
    var issueState by remember { mutableStateOf("all") }

    var repos by remember { mutableStateOf<List<GitHubRepo>>(emptyList()) }
    var issues by remember { mutableStateOf<List<GitHubIssue>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()

    LaunchedEffect(refreshKey, issueState, section) {
        if (refreshKey == 0) loading = true
        error = null
        try {
            if (section == Section.REPOS) {
                var firstEmission = true
                repo.myRepos().collect { result ->
                    when (result) {
                        is GitHubRepository.Result.Success -> {
                            repos = result.data; error = null
                            if (firstEmission || refreshKey > 0) {
                                loading = false; firstEmission = false; isRefreshing = false
                                if (refreshKey > 0) listState.scrollToItem(0)
                            }
                        }
                        is GitHubRepository.Result.Error -> { error = result.message; isRefreshing = false; loading = false }
                    }
                }
            } else {
                when (val r = repo.myRepoIssues(issueState)) {
                    is GitHubRepository.Result.Success -> { issues = r.data; error = null; isRefreshing = false; if (refreshKey > 0) listState.scrollToItem(0) }
                    is GitHubRepository.Result.Error -> { error = r.message; isRefreshing = false }
                }
                loading = false
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
                title = { androidx.compose.material3.Text("仓库", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
            Section.entries.forEachIndexed { index, s ->
                SegmentedButton(
                    selected = section == s,
                    onClick = {
                        section = s
                        // LaunchedEffect 会自动处理 section 切换后的加载
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, Section.entries.size)
                ) { Text(s.label) }
            }
        }

        when (section) {
            Section.REPOS -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator() }
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
                        RepoList(repos = filtered, listState = listState, onRepoClick = onRepoClick, error = error)
                    }
                }
            }

            Section.ISSUES -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = issueState == "all", onClick = { issueState = "all" }, label = { Text("全部") })
                    FilterChip(selected = issueState == "open", onClick = { issueState = "open" }, label = { Text("Open") })
                    FilterChip(selected = issueState == "closed", onClick = { issueState = "closed" }, label = { Text("Closed") })
                }
                if (loading && issues.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator() }
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
                        IssueList(issues = issues, listState = listState, onIssueClick = onIssueClick, error = error)
                    }
                }
            }
        }
    }
}
}

@Composable
private fun RepoList(repos: List<GitHubRepo>, listState: androidx.compose.foundation.lazy.LazyListState, onRepoClick: (GitHubRepo) -> Unit, error: String?) {
    if (repos.isEmpty() && error != null) { EmptyOrError(text = error) }
    else if (repos.isEmpty()) { EmptyOrError(text = "暂无仓库") }
    else {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
            items(repos, key = { it.id }) { r ->
                KomiRepoCard(owner = r.ownerLogin, name = r.name, description = r.description.orEmpty(), language = r.language, stars = r.stargazers_count, forks = r.forks_count, updatedAt = r.pushed_at, onClick = { onRepoClick(r) })
            }
        }
    }
}

@Composable
private fun IssueList(issues: List<GitHubIssue>, listState: androidx.compose.foundation.lazy.LazyListState, onIssueClick: (GitHubIssue) -> Unit, error: String?) {
    if (issues.isEmpty() && error != null) { EmptyOrError(text = error) }
    else if (issues.isEmpty()) { EmptyOrError(text = "暂无 Issue") }
    else {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
            items(issues, key = { it.id }) { issue ->
                KomiSurface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { onIssueClick(issue) }) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (issue.state == "open") "🟢" else "🟣",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(issue.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(issue.repoFullName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            Text("💬 ${issue.comments}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("#${issue.number}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyOrError(text: String) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
