package com.mygithub.lab.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.mygithub.lab.ui.components.KomiChip
import com.mygithub.lab.ui.components.KomiRepoCard
import com.mygithub.lab.ui.components.KomiSurface
import kotlinx.coroutines.launch

private enum class SearchType(val label: String) { REPO("仓库"), USER("用户") }

/** 高级语法提示 */
private data class SyntaxHint(val pattern: String, val tip: String)
private val syntaxHints = listOf(
    SyntaxHint("language:", "限定编程语言，如 language:rust"),
    SyntaxHint("stars:>", "最低星数，如 stars:>1000"),
    SyntaxHint("topic:", "限定主题，如 topic:android"),
    SyntaxHint("in:name", "仅搜名称，如 android in:name")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onRepoClick: (GitHubRepo) -> Unit,
    onIssueClick: (GitHubIssue) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { GitHubRepository.get(context) }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(SearchType.REPO) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }

    var repos by remember { mutableStateOf<List<GitHubRepo>>(emptyList()) }
    var users by remember { mutableStateOf<List<com.mygithub.lab.data.api.GitHubUser>>(emptyList()) }
    var history by remember { mutableStateOf<List<String>>(emptyList()) }

    // 加载历史
    LaunchedEffect(Unit) {
        history = repo.getSearchHistory("repo")
    }

    fun doSearch(q: String = query) {
        if (q.isBlank()) return
        searching = true; error = null; hasSearched = true
        scope.launch {
            when (type) {
                SearchType.REPO -> when (val r = repo.searchRepos(q)) {
                    is GitHubRepository.Result.Success -> repos = r.data
                    is GitHubRepository.Result.Error -> error = r.message
                }
                SearchType.USER -> when (val r = repo.searchUsers(q)) {
                    is GitHubRepository.Result.Success -> users = r.data
                    is GitHubRepository.Result.Error -> error = r.message
                }
            }
            repo.addSearchHistory(q, "repo")
            history = repo.getSearchHistory("repo")
            searching = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "搜索",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        // 搜索框
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                hasSearched = false
            },
            placeholder = { Text("搜索") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; hasSearched = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "清空")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { doSearch() }
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            )
        )

        // 类型分段
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            SearchType.entries.forEachIndexed { index, t ->
                SegmentedButton(
                    selected = type == t,
                    onClick = { type = t; if (hasSearched) doSearch() },
                    shape = SegmentedButtonDefaults.itemShape(index, SearchType.entries.size)
                ) { Text(t.label) }
            }
        }

        // 语法提示（输入时）
        if (!hasSearched && query.isNotEmpty()) {
            val matched = syntaxHints.filter { query.contains(it.pattern.substringBefore(':')) }
            if (matched.isNotEmpty() || query.length > 3) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    matched.ifEmpty { syntaxHints.take(2) }.take(3).forEach { hint ->
                        KomiChip(text = hint.tip, onClick = { query = "${query.trimEnd()} ${hint.pattern.substringBefore(':')}" })
                    }
                }
            }
        }

        // 搜索历史（未搜索时）
        if (!hasSearched && history.isNotEmpty()) {
            Text(
                "搜索历史",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { h ->
                    KomiChip(
                        text = h,
                        onClick = { query = h; doSearch(h) }
                    )
                }
            }
        }

        // 结果
        if (searching) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                horizontalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
        } else if (error != null) {
            CenterHint(text = error!!)
        } else if (hasSearched) {
            when (type) {
                SearchType.REPO -> {
                    if (repos.isEmpty()) CenterHint("无结果") else LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                    ) {
                        items(repos, key = { it.id }) { r ->
                            KomiRepoCard(
                                owner = r.ownerLogin, name = r.name,
                                description = r.description.orEmpty(),
                                language = r.language, stars = r.stargazers_count,
                                onClick = { onRepoClick(r) }
                            )
                        }
                    }
                }
                SearchType.USER -> {
                    if (users.isEmpty()) CenterHint("无结果") else LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                    ) {
                        items(users, key = { it.id }) { user ->
                            KomiSurface(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // 头像占位
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                androidx.compose.foundation.shape.CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(user.login.take(1).uppercase(),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.primary)
                                    }
                                    Column {
                                        Text(user.login, style = MaterialTheme.typography.titleMedium)
                                        user.bio?.let {
                                            Text(it, style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            CenterHint("输入关键词开始搜索")
        }
    }
}


@Composable
private fun CenterHint(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
