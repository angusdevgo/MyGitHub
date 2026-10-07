package com.mygithub.lab.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mygithub.lab.data.auth.TokenStore
import com.mygithub.lab.data.api.GitHubIssue
import com.mygithub.lab.data.api.GitHubRepo
import com.mygithub.lab.data.repo.GitHubRepository
import com.mygithub.lab.data.update.UpdateInfo
import com.mygithub.lab.ui.components.KomiFloatingBottomBar
import com.mygithub.lab.ui.components.KomiNavItem
import com.mygithub.lab.ui.components.UpdateDialog
import com.mygithub.lab.ui.screens.login.LoginScreen
import com.mygithub.lab.ui.screens.home.HomeScreen
import com.mygithub.lab.ui.screens.profile.ProfileScreen
import com.mygithub.lab.ui.screens.rankings.RankingsScreen
import com.mygithub.lab.ui.screens.search.SearchScreen
import com.mygithub.lab.ui.screens.notifications.NotificationsScreen
import com.mygithub.lab.ui.screens.repos.RepoDetailScreen
import com.mygithub.lab.ui.screens.repos.ReposScreen
import com.mygithub.lab.ui.screens.issues.IssueDetailScreen
import com.mygithub.lab.ui.screens.about.AboutScreen
import com.mygithub.lab.ui.theme.MyGitHubTheme

data class TabItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

// 路由
object Routes {
    const val MAIN = "main"
    const val REPO_DETAIL = "repo_detail"
    const val ISSUE_DETAIL = "issue_detail"
    const val SEARCH = "search"
    const val ABOUT = "about"
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun MyGitHubApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("mygithub_prefs", android.content.Context.MODE_PRIVATE) }
    val themeMode by produceState(initialValue = prefs.getString("theme_mode", "system") ?: "system", key1 = true) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme_mode") value = prefs.getString("theme_mode", "system") ?: "system"
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    MyGitHubTheme(darkTheme = darkTheme) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val isLoggedIn = remember { TokenStore.isLoggedIn(context) }
        var loggedIn by remember { mutableStateOf(isLoggedIn) }

        // 应用内更新信息状态
        var appUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

        // 启动时静默检查更新一次（后台异步，零阻塞）
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1200)
            try {
                val update = GitHubRepository.get(context).checkAppUpdate()
                if (update != null) {
                    appUpdateInfo = update
                }
            } catch (_: Exception) {}
        }

        if (!loggedIn) {
            LoginScreen(onLoginSuccess = { loggedIn = true })
            // 登录界面也支持弹窗展示更新
            appUpdateInfo?.let { info ->
                UpdateDialog(
                    info = info,
                    onDismiss = { appUpdateInfo = null }
                )
            }
            return@MyGitHubTheme
        }

        val navController = rememberNavController()
        // 详情页导航状态（简化传参：直接对象持有）
        var selectedRepo by remember { mutableStateOf<GitHubRepo?>(null) }
        var selectedIssue by remember { mutableStateOf<GitHubIssue?>(null) }

        NavHost(navController = navController, startDestination = Routes.MAIN) {
            composable(Routes.MAIN) {
                MainScaffold(
                    navController = navController,
                    onRepoClick = { repo ->
                        selectedRepo = repo
                        navController.navigate(Routes.REPO_DETAIL)
                    },
                    onIssueClick = { issue ->
                        selectedIssue = issue
                        navController.navigate(Routes.ISSUE_DETAIL)
                    },
                    onLogout = {
                        TokenStore.clear(context)
                        loggedIn = false
                    },
                    onShowUpdate = { appUpdateInfo = it }
                )
            }
            composable(Routes.REPO_DETAIL) {
                BackHandler(enabled = true) { navController.popBackStack() }
                selectedRepo?.let { repo ->
                    RepoDetailScreen(
                        repo = repo,
                        onIssueClick = { issue ->
                            selectedIssue = issue
                            navController.navigate(Routes.ISSUE_DETAIL)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(Routes.ISSUE_DETAIL) {
                BackHandler(enabled = true) { navController.popBackStack() }
                selectedIssue?.let { issue ->
                    IssueDetailScreen(issue = issue, onBack = { navController.popBackStack() })
                }
            }
            composable(Routes.SEARCH) {
                BackHandler(enabled = true) { navController.popBackStack() }
                SearchScreen(
                    onRepoClick = { repo ->
                        selectedRepo = repo
                        navController.navigate(Routes.REPO_DETAIL)
                    },
                    onIssueClick = { issue ->
                        selectedIssue = issue
                        navController.navigate(Routes.ISSUE_DETAIL)
                    }
                )
            }
            composable(Routes.ABOUT) {
                BackHandler(enabled = true) { navController.popBackStack() }
                AboutScreen(
                    onBack = { navController.popBackStack() },
                    onShowUpdate = { appUpdateInfo = it }
                )
            }
        }

        // 全局更新弹窗（任何页面上均可展示）
        appUpdateInfo?.let { info ->
            UpdateDialog(
                info = info,
                onDismiss = { appUpdateInfo = null }
            )
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun MainScaffold(
    navController: androidx.navigation.NavController,
    onRepoClick: (GitHubRepo) -> Unit,
    onIssueClick: (GitHubIssue) -> Unit,
    onLogout: () -> Unit,
    onShowUpdate: (UpdateInfo) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val tabs = listOf(
        KomiNavItem("首页", Icons.Filled.Home, Icons.Outlined.Home),
        KomiNavItem("排行榜", Icons.Filled.Leaderboard, Icons.Outlined.Leaderboard),
        KomiNavItem("信息", Icons.Filled.Notifications, Icons.Outlined.Notifications),
        KomiNavItem("仓库", Icons.Filled.Folder, Icons.Outlined.Folder),
        KomiNavItem("我的", Icons.Filled.Person, Icons.Outlined.Person)
    )
    var selected by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 内容层：全屏通底铺满，绝不截断底部视口，使列表能够自然穿透滑动到底栏下方
        when (selected) {
            0 -> HomeScreen(onRepoClick = onRepoClick, onSearchClick = { navController.navigate(Routes.SEARCH) })
            1 -> RankingsScreen(onRepoClick = onRepoClick)
            2 -> NotificationsScreen(
                onNavigateToRepo = { ghRepo ->
                    onRepoClick(ghRepo)
                },
                onNavigateToIssue = { _, issue ->
                    onIssueClick(issue)
                }
            )
            3 -> ReposScreen(onRepoClick = onRepoClick, onIssueClick = onIssueClick)
            4 -> ProfileScreen(
                onRepoClick = onRepoClick,
                onLogout = onLogout,
                onShowUpdate = onShowUpdate,
                onNavigateToAbout = { navController.navigate(Routes.ABOUT) }
            )
        }

        // 2. 悬浮底栏层：独立叠加在最上层，靠底部居中对齐
        KomiFloatingBottomBar(
            items = tabs,
            selectedIndex = selected,
            onItemSelected = { selected = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

