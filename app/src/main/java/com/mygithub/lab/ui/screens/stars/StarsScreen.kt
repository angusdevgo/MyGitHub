package com.mygithub.lab.ui.screens.stars

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.launch

/**
 * Star 收藏管理：本地分类标签 + 私有备注 + 过滤
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarsScreen(
    onRepoClick: (GitHubRepo) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { GitHubRepository.get(context) }
    val scope = rememberCoroutineScope()

    var stars by remember { mutableStateOf<List<GitHubRepo>>(emptyList()) }
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var tagDialogFor by remember { mutableStateOf<String?>(null) } // repo full name
    var newTagName by remember { mutableStateOf("") }

    // 加载
    LaunchedEffect(Unit) {
        var firstEmission = true
        repo.starredRepos().collect { result ->
            when (result) {
                is GitHubRepository.Result.Success -> {
                    stars = result.data
                    if (firstEmission) { loading = false; firstEmission = false }
                }
                is GitHubRepository.Result.Error -> {}
            }
            if (!firstEmission) loading = false
        }
        tags = repo.getAllTags()
    }

    // 打标签对话框
    tagDialogFor?.let { fullName ->
        AlertDialog(
            onDismissRequest = { tagDialogFor = null },
            title = { Text("管理标签") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(fullName, style = MaterialTheme.typography.bodyMedium)
                    // 已有标签
                    val repoTags = repo.getTagsSync(fullName)
                    if (repoTags.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(repoTags) { t ->
                                KomiChip(
                                    text = t,
                                    selected = true,
                                    onClick = {
                                        scope.launch {
                                            repo.removeTag(fullName, t)
                                            tags = repo.getAllTags()
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newTagName,
                            onValueChange = { newTagName = it },
                            placeholder = { Text("新标签名") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(onClick = {
                            if (newTagName.isNotBlank()) {
                                scope.launch {
                                    repo.addTag(fullName, newTagName.trim())
                                    newTagName = ""
                                    tags = repo.getAllTags()
                                }
                            }
                        }) { Text("添加") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { tagDialogFor = null }) { Text("完成") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Star 收藏", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
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
            // 统计行 + 标签过滤
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${stars.size} 个收藏 · ${tags.size} 个分类",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    KomiChip(text = "全部", selected = selectedTag == null, onClick = { selectedTag = null })
                }
                items(tags) { tag ->
                    KomiChip(
                        text = tag,
                        selected = selectedTag == tag,
                        onClick = { selectedTag = if (selectedTag == tag) null else tag }
                    )
                }
            }

            if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
            } else {
                val filtered = if (selectedTag != null) {
                    val taggedNames = repo.getReposByTagSync(selectedTag!!).toSet()
                    stars.filter { it.full_name in taggedNames }
                } else stars

                if (filtered.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            if (selectedTag != null) "此分类暂无收藏" else "暂无 Star",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                    ) {
                        items(filtered, key = { it.id }) { r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                KomiRepoCard(
                                    owner = r.ownerLogin,
                                    name = r.name,
                                    description = r.description.orEmpty(),
                                    language = r.language,
                                    stars = r.stargazers_count,
                                    onClick = { onRepoClick(r) },
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { tagDialogFor = r.full_name }) {
                                    Icon(
                                        Icons.Filled.Label,
                                        contentDescription = "标签",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
