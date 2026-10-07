package com.mygithub.lab.ui.screens.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.mygithub.lab.data.api.GitHubComment
import com.mygithub.lab.data.api.GitHubIssue
import com.mygithub.lab.data.repo.GitHubRepository
import com.mygithub.lab.ui.components.KomiSurface
import com.mygithub.lab.ui.components.relativeTimeFromIso
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueDetailScreen(
    issue: GitHubIssue,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { GitHubRepository.get(context) }
    val scope = rememberCoroutineScope()

    var currentIssue by remember(issue) { mutableStateOf(issue) }
    var commentText by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf<List<GitHubComment>>(emptyList()) }
    var loadingComments by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var currentState by remember { mutableStateOf(issue.state) }
    var stateChanging by remember { mutableStateOf(false) }

    // 从 issue 提取 owner/repo
    val parts = currentIssue.repository_url.removePrefix("https://api.github.com/repos/").split("/")
    val owner = parts.getOrElse(0) { "" }
    val repoName = parts.getOrElse(1) { "" }

    LaunchedEffect(Unit) {
        // 后台异步补全最新完整 Issue 字段与状态（0ms 秒开无感知）
        launch {
            when (val r = repo.getIssue(owner, repoName, currentIssue.number)) {
                is GitHubRepository.Result.Success -> {
                    currentIssue = r.data
                    currentState = r.data.state
                }
                else -> {}
            }
        }
        // 后台异步拉取评论
        launch {
            when (val r = repo.getIssueComments(owner, repoName, currentIssue.number)) {
                is GitHubRepository.Result.Success -> comments = r.data
                else -> {}
            }
            loadingComments = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("#${currentIssue.number}", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // Close / Reopen 按钮
                    if (stateChanging) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        OutlinedButton(
                            onClick = {
                                val newState = if (currentState == "open") "closed" else "open"
                                stateChanging = true
                                scope.launch {
                                    when (repo.updateIssueState(owner, repoName, currentIssue.number, newState)) {
                                        is GitHubRepository.Result.Success -> currentState = newState
                                        else -> {}
                                    }
                                    stateChanging = false
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (currentState == "open") MaterialTheme.colorScheme.error
                                              else MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text(if (currentState == "open") "Close" else "Reopen")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text("添加评论...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 3
                )
                IconButton(
                    onClick = {
                        if (commentText.isBlank() || sending) return@IconButton
                        sending = true
                        scope.launch {
                            when (val r = repo.createComment(owner, repoName, currentIssue.number, commentText)) {
                                is GitHubRepository.Result.Success -> {
                                    comments = comments + r.data
                                    commentText = ""
                                }
                                else -> {}
                            }
                            sending = false
                        }
                    }
                ) {
                    if (sending) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送",
                            tint = if (commentText.isBlank()) MaterialTheme.colorScheme.outlineVariant
                            else MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Issue 标题+状态
            item {
                KomiSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = currentIssue.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = if (currentState == "open") "🟢 Open" else "🟣 Closed",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (currentState == "open") MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = currentIssue.repoFullName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = relativeTimeFromIso(currentIssue.created_at),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (currentIssue.labels.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                currentIssue.labels.take(5).forEach { label ->
                                    Text(
                                        "•${label.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 正文
            item {
                KomiSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 作者
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AsyncImage(
                                model = coil3.request.ImageRequest.Builder(LocalContext.current)
                                    .data(currentIssue.user?.avatar_url ?: "")
                                    .crossfade(false)
                                    .memoryCacheKey(currentIssue.user?.avatar_url ?: "")
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                currentIssue.user?.login ?: "unknown",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = currentIssue.body?.take(5000) ?: "（无描述）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // 评论列表
            if (loadingComments) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (comments.isNotEmpty()) {
                item {
                    Text(
                        "${comments.size} 条评论",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(comments, key = { it.id }) { comment ->
                    KomiSurface(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AsyncImage(
                                    model = coil3.request.ImageRequest.Builder(LocalContext.current)
                                        .data(comment.user?.avatar_url ?: "")
                                        .crossfade(false)
                                        .memoryCacheKey(comment.user?.avatar_url ?: "")
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Text(
                                    comment.user?.login ?: "unknown",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    relativeTimeFromIso(comment.created_at),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                comment.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
