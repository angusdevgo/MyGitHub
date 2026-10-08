package com.mygithub.lab.data.repo

import android.content.Context
import com.mygithub.lab.data.api.GitHubApi
import com.mygithub.lab.data.api.GitHubRepo
import com.mygithub.lab.data.auth.TokenStore
import com.mygithub.lab.data.local.AppDatabase
import com.mygithub.lab.data.local.RepoCacheEntity
import com.mygithub.lab.data.model.OwnedIssueItem
import com.mygithub.lab.data.model.OwnedRepoSummary
import com.mygithub.lab.data.util.DataMergeUtil
import com.mygithub.lab.data.util.OwnedIssueMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 数据仓库层：远程 API + Room 缓存（离线可用）
 */
class GitHubRepository(context: Context) {

    private val appContext = context.applicationContext
    private val token: String get() = TokenStore.getToken(appContext).orEmpty()
    private val db: AppDatabase get() = AppDatabase.get(appContext)
    private val json = Json { ignoreUnknownKeys = true }

    private val rateLimitInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("Accept", "application/vnd.github+json")
            .build()
        val response = chain.proceed(request)
        response
    }

    private val okClient = OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(15))
        .readTimeout(java.time.Duration.ofSeconds(30))
        .addInterceptor(rateLimitInterceptor)
        .proxySelector(com.mygithub.lab.data.network.ProxyManager.createDynamicProxySelector(appContext))
        .build()

    private val api: GitHubApi = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(okClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GitHubApi::class.java)

    sealed class Result<out T> {
        data class Success<out T>(val data: T, val fromCache: Boolean = false) : Result<T>()
        data class Error(val message: String, val cached: Any? = null) : Result<Nothing>()
    }

    // ===== 个人仓库 =====

    fun myRepos(): Flow<Result<List<GitHubRepo>>> = flow {
        val cached = db.repoCacheDao().getByCategory("personal")
        if (cached.isNotEmpty()) {
            // 有缓存：立即展示缓存，后台静默拉远程并只在有新增时追加（不替换，避免跳动）
            emit(Result.Success(cached.map { json.decodeFromString(GitHubRepo.serializer(), it.json) }.sortedByDescending { it.stargazers_count }, fromCache = true))
            try {
                val remote = api.getUserRepos("Bearer $token").sortedByDescending { it.stargazers_count }
                db.repoCacheDao().clearCategory("personal")
                db.repoCacheDao().upsertAll(remote.map {
                    RepoCacheEntity(full_name = it.full_name, json = json.encodeToString(GitHubRepo.serializer(), it), cached_at = System.currentTimeMillis(), category = "personal")
                })
                // 有新增项目时才更新 UI（保持位置不跳动）
                val cachedNames = cached.map { it.full_name }.toSet()
                val newItems = remote.filter { it.full_name !in cachedNames }
                if (newItems.isNotEmpty()) {
                    emit(Result.Success(remote))
                }
            } catch (_: Exception) { }
        } else {
            try {
                val remote = api.getUserRepos("Bearer $token").sortedByDescending { it.stargazers_count }
                db.repoCacheDao().clearCategory("personal")
                db.repoCacheDao().upsertAll(remote.map {
                    RepoCacheEntity(full_name = it.full_name, json = json.encodeToString(GitHubRepo.serializer(), it), cached_at = System.currentTimeMillis(), category = "personal")
                })
                emit(Result.Success(remote))
            } catch (e: Exception) { emit(Result.Error("加载失败: ${e.message}")) }
        }
    }.flowOn(Dispatchers.IO)

    // ===== Star 列表 =====

    fun starredRepos(): Flow<Result<List<GitHubRepo>>> = flow {
        val cached = db.repoCacheDao().getByCategory("starred")
        if (cached.isNotEmpty()) {
            // 有缓存：立即展示缓存，后台拉远程并合并更新（远程数据到达后刷新 UI 但用 distinct 保证不跳动）
            val cachedList = cached.map { json.decodeFromString(GitHubRepo.serializer(), it.json) }
            emit(Result.Success(cachedList, fromCache = true))
            try {
                val remote = api.getStarred("Bearer $token")
                db.repoCacheDao().clearCategory("starred")
                db.repoCacheDao().upsertAll(remote.map {
                    RepoCacheEntity(full_name = it.full_name, json = json.encodeToString(GitHubRepo.serializer(), it), cached_at = System.currentTimeMillis(), category = "starred")
                })
                // 远程数据与缓存不同时刷新 UI（保证完整数据同步）
                if (remote.size != cachedList.size || remote.map { it.full_name }.toSet() != cachedList.map { it.full_name }.toSet()) {
                    emit(Result.Success(remote))
                }
            } catch (_: Exception) { }
        } else {
            try {
                val remote = api.getStarred("Bearer $token")
                db.repoCacheDao().clearCategory("starred")
                db.repoCacheDao().upsertAll(remote.map {
                    RepoCacheEntity(full_name = it.full_name, json = json.encodeToString(GitHubRepo.serializer(), it), cached_at = System.currentTimeMillis(), category = "starred")
                })
                emit(Result.Success(remote))
            } catch (e: Exception) {
                emit(Result.Error("加载失败: ${e.message}"))
            }
        }
    }.flowOn(Dispatchers.IO)

    // ===== 仓库详情 =====

    suspend fun getReadmeFile(owner: String, repo: String, filename: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cleanPath = filename.removePrefix("./").removePrefix("/")
                // 先尝试 HTML 渲染版
                val request = okhttp3.Request.Builder()
                    .url("https://api.github.com/repos/$owner/$repo/contents/$cleanPath")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github.html+json")
                    .build()
                val resp = okClient.newCall(request).execute()
                if (resp.isSuccessful) {
                    val html = resp.body?.string()
                    resp.close()
                    if (html != null) return@withContext wrapHtmlWithTheme(html, owner, repo)
                }
                resp.close()
                // 回退：raw 文本
                val rawReq = okhttp3.Request.Builder()
                    .url("https://raw.githubusercontent.com/$owner/$repo/main/$cleanPath")
                    .header("Authorization", "Bearer $token")
                    .build()
                val rawResp = okClient.newCall(rawReq).execute()
                if (rawResp.isSuccessful) {
                    val rawText = rawResp.body?.string()
                    rawResp.close()
                    if (rawText != null) {
                        val simpleHtml = rawText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
                        return@withContext wrapHtmlWithTheme(simpleHtml, owner, repo)
                    }
                }
                rawResp.close()
                null
            } catch (e: Exception) { null }
        }

    suspend fun getReadme(owner: String, repo: String, dark: Boolean = true): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 优先用 HTML 渲染版 API（与官方 App 一样）
                val request = okhttp3.Request.Builder()
                    .url("https://api.github.com/repos/$owner/$repo/readme")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github.html+json")
                    .build()
                val resp = okClient.newCall(request).execute()
                if (resp.isSuccessful) {
                    val html = resp.body?.string()
                    resp.close()
                    if (html != null) return@withContext wrapHtmlWithTheme(html, owner, repo, dark)
                }
                resp.close()
                // 如果 /readme 返回 404，尝试常见 README 文件名
                val readmeCandidates = listOf("README.md", "README.rst", "README", "README.txt", "readme.md", "Readme.md")
                for (candidate in readmeCandidates) {
                    try {
                        val rawReq = okhttp3.Request.Builder()
                            .url("https://raw.githubusercontent.com/$owner/$repo/main/$candidate")
                            .header("Authorization", "Bearer $token")
                            .build()
                        val rawResp = okClient.newCall(rawReq).execute()
                        if (rawResp.isSuccessful) {
                            val rawText = rawResp.body?.string()
                            rawResp.close()
                            if (rawText != null) {
                                // 简单转 HTML
                                val simpleHtml = rawText
                                    .replace("&", "&amp;")
                                    .replace("<", "&lt;")
                                    .replace(">", "&gt;")
                                    .replace("\n", "<br>")
                                return@withContext wrapHtmlWithTheme(simpleHtml, owner, repo, dark)
                            }
                        }
                        rawResp.close()
                    } catch (_: Exception) { continue }
                }
                null
            } catch (e: Exception) { null }
        }

    suspend fun repoDetail(owner: String, repo: String): Result<GitHubRepo> = try {
        Result.Success(api.getRepo("Bearer $token", owner, repo))
    } catch (e: Exception) {
        val cached = db.repoCacheDao().get("$owner/$repo")
        if (cached != null) Result.Success(json.decodeFromString(GitHubRepo.serializer(), cached.json), fromCache = true)
        else Result.Error("加载失败: ${e.message}")
    }

    // ===== 搜索 =====

    suspend fun searchRepos(query: String): Result<List<GitHubRepo>> = try {
        Result.Success(api.searchRepos("Bearer $token", query).items)
    } catch (e: Exception) {
        Result.Error("搜索失败: ${e.message}")
    }

    suspend fun searchReposByQuery(q: String): Result<List<GitHubRepo>> = try {
        Result.Success(api.searchRepos("Bearer $token", q, perPage = 30).items.sortedByDescending { it.stargazers_count })
    } catch (e: Exception) {
        Result.Error("加载失败: ${e.message}")
    }

    suspend fun getRepo(owner: String, repo: String): Result<GitHubRepo> = try {
        Result.Success(api.getRepo("Bearer $token", owner, repo))
    } catch (e: Exception) { Result.Error("仓库加载失败: ${e.message}") }

    suspend fun getIssue(owner: String, repo: String, num: Int): Result<com.mygithub.lab.data.api.GitHubIssue> {
        return try {
            val issue = api.getIssue("Bearer $token", owner, repo, num)
            Result.Success(issue)
        } catch (e: Exception) { Result.Error("Issue 加载失败: ${e.message}") }
    }

    suspend fun getIssueComments(owner: String, repo: String, num: Int): Result<List<com.mygithub.lab.data.api.GitHubComment>> = try {
        Result.Success(api.getIssueComments("Bearer $token", owner, repo, num))
    } catch (e: Exception) { Result.Error("评论加载失败: ${e.message}") }

    suspend fun createComment(owner: String, repo: String, num: Int, body: String): Result<com.mygithub.lab.data.api.GitHubComment> = try {
        Result.Success(api.createComment("Bearer $token", owner, repo, num, com.mygithub.lab.data.api.CreateCommentRequest(body)))
    } catch (e: Exception) { Result.Error("评论失败: ${e.message}") }

    suspend fun updateIssueState(owner: String, repo: String, num: Int, state: String): Result<com.mygithub.lab.data.api.GitHubIssue> = try {
        val res = api.updateIssue("Bearer $token", owner, repo, num, com.mygithub.lab.data.api.UpdateIssueRequest(state = state))
        Result.Success(res)
    } catch (e: Exception) { Result.Error("操作失败: ${e.message}") }

    /** 将网络/限流异常转为用户可读文案 */
    private fun friendlyError(e: Exception, prefix: String): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("403") || msg.contains("429") || msg.contains("rate limit", ignoreCase = true) ->
                "GitHub API 速率限制已用尽，请稍后重试"
            msg.contains("401") || msg.contains("Bad credentials", ignoreCase = true) ->
                "登录凭证已失效，请重新登录"
            msg.contains("timeout", ignoreCase = true) || msg.contains("Unable to resolve host", ignoreCase = true) ->
                "网络连接超时，请检查网络或代理设置"
            else -> "$prefix: $msg"
        }
    }

    suspend fun getUserEvents(username: String): Result<List<com.mygithub.lab.data.api.GitHubEvent>> = try {
        Result.Success(api.getUserEvents("Bearer $token", username))
    } catch (e: Exception) { Result.Error("动态加载失败: ${e.message}") }

    /**
     * 收到的动态：别人对自己仓库的 star/fork/issue 操作
     */
    fun getReceivedActivity(): Flow<Result<List<com.mygithub.lab.data.api.GitHubEvent>>> = flow {
        val cached = db.repoCacheDao().getByCategory("received_activity")
        val cachedEvents = if (cached.isNotEmpty()) {
            cached.mapNotNull { runCatching { json.decodeFromString(com.mygithub.lab.data.api.GitHubEvent.serializer(), it.json) }.getOrNull() }
                .sortedByDescending { it.created_at }
        } else emptyList()
        if (cachedEvents.isNotEmpty()) {
            emit(Result.Success(cachedEvents, fromCache = true))
        }
        try {
            // 1. 仅取自己拥有的仓库（affiliation=owner），最多 2 页 × 100 = 200 个
            val repos = loadOwnedRepos()

            // 2. 并发限流（同时最多 6 个请求）拉取每个仓库的 events 事件流
            val allEvents = java.util.Collections.synchronizedList(mutableListOf<com.mygithub.lab.data.api.GitHubEvent>())
            val semaphore = Semaphore(6)

            kotlinx.coroutines.supervisorScope {
                repos.forEach { r ->
                    launch {
                        semaphore.withPermit {
                            try {
                                api.getRepoEvents("Bearer $token", r.owner.login, r.name, perPage = 30)
                                    .forEach { e -> if (ACTIVITY_EVENT_TYPES.contains(e.type)) allEvents.add(e) }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            val sorted = DataMergeUtil.curateEvents(allEvents, ACTIVITY_EVENT_TYPES)

            // 3. 写入 Room 缓存（复用 repo_cache 表，category=received_activity，免 DB 迁移）
            db.repoCacheDao().clearCategory("received_activity")
            db.repoCacheDao().upsertAll(sorted.take(150).map {
                RepoCacheEntity(full_name = it.id, json = json.encodeToString(com.mygithub.lab.data.api.GitHubEvent.serializer(), it), cached_at = System.currentTimeMillis(), category = "received_activity")
            })

            // 4. 与缓存不同才二次 emit（避免列表跳动）
            if (sorted.map { it.id }.toSet() != cachedEvents.map { it.id }.toSet()) {
                emit(Result.Success(sorted))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (cachedEvents.isEmpty()) emit(Result.Error(friendlyError(e, "动态加载失败")))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 加载当前用户「自己拥有」的非 fork 仓库（最多 2 页 × 100 = 200 个）
     * 供「动态」事件流、「议题」列表与总量摘要共用，保证仓库口径统一。
     */
    private suspend fun loadOwnedRepos(): List<GitHubRepo> {
        val owned = mutableListOf<GitHubRepo>()
        for (page in 1..2) {
            val chunk = api.getUserRepos(
                "Bearer $token",
                affiliation = "owner",
                sort = "pushed",
                perPage = 100,
                page = page
            )
            owned.addAll(chunk)
            if (chunk.size < 100) break
        }
        return owned.filter { !it.fork }
    }

    /**
     * 「议题」分段：按仓库扇出拉取自有仓库的全部 Issue / PR
     * @param state open | closed | all
     */
    fun getAllOwnedIssues(state: String = "open"): Flow<Result<List<OwnedIssueItem>>> = flow {
        val category = "owned_issues_$state"
        val cachedRows = db.repoCacheDao().getByCategory(category)
        val cachedItems = cachedRows.mapNotNull { row ->
            runCatching { json.decodeFromString(OwnedIssueItem.serializer(), row.json) }.getOrNull()
        }
        if (cachedItems.isNotEmpty()) {
            emit(Result.Success(cachedItems, fromCache = true))
        }

        try {
            val repos = loadOwnedRepos()
            val collected = java.util.Collections.synchronizedList(mutableListOf<OwnedIssueItem>())
            val semaphore = Semaphore(6)

            kotlinx.coroutines.supervisorScope {
                repos.forEach { r ->
                    launch {
                        semaphore.withPermit {
                            try {
                                // 单仓库失败（含 issues 被禁用的 410 Gone）静默跳过
                                val issues = api.getRepoIssues(
                                    "Bearer $token",
                                    r.owner.login,
                                    r.name,
                                    state = state,
                                    sort = "updated",
                                    direction = "desc",
                                    perPage = 100,
                                    page = 1
                                )
                                collected.addAll(OwnedIssueMapper.map(r.full_name, issues))
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            val curated = OwnedIssueMapper.curate(collected)

            // 写入 Room 缓存（复用 repo_cache 表，免 DB 迁移）
            db.repoCacheDao().clearCategory(category)
            if (curated.isNotEmpty()) {
                db.repoCacheDao().upsertAll(curated.take(300).map { item ->
                    RepoCacheEntity(
                        full_name = item.cacheKey,
                        json = json.encodeToString(OwnedIssueItem.serializer(), item),
                        cached_at = System.currentTimeMillis(),
                        category = category
                    )
                })
            }

            // 与缓存不同才二次 emit（避免列表跳动）
            if (curated.map { it.cacheKey }.toSet() != cachedItems.map { it.cacheKey }.toSet()) {
                emit(Result.Success(curated))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (cachedItems.isEmpty()) emit(Result.Error(friendlyError(e, "议题加载失败")))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 自有仓库总量摘要（1 次请求即可得，不依赖事件流，数值永远准确）
     * 同时计算“自上次查看以来 +N Stars / +M Forks”增量。
     */
    suspend fun getOwnedRepoSummary(): Result<OwnedRepoSummary> = try {
        val repos = loadOwnedRepos()

        val totalStars = repos.sumOf { it.stargazers_count }
        val totalForks = repos.sumOf { it.forks_count }
        val totalOpenIssues = repos.sumOf { it.open_issues_count }

        // 读取上次快照计算增量
        val previous = db.repoCacheDao().getByCategory("received_counters")
        val prevMap = previous.mapNotNull { row ->
            runCatching { row.full_name to json.decodeFromString<RepoCounters>(row.json) }.getOrNull()
        }.toMap()

        var starsDelta = 0
        var forksDelta = 0
        if (prevMap.isNotEmpty()) {
            repos.forEach { r ->
                val prev = prevMap[r.full_name] ?: return@forEach
                starsDelta += (r.stargazers_count - prev.stars).coerceAtLeast(0)
                forksDelta += (r.forks_count - prev.forks).coerceAtLeast(0)
            }
        }

        // 写入本次快照
        db.repoCacheDao().clearCategory("received_counters")
        db.repoCacheDao().upsertAll(repos.map { r ->
            RepoCacheEntity(
                full_name = r.full_name,
                json = json.encodeToString(RepoCounters.serializer(), RepoCounters(r.stargazers_count, r.forks_count)),
                cached_at = System.currentTimeMillis(),
                category = "received_counters"
            )
        })

        Result.Success(
            OwnedRepoSummary(
                totalStars = totalStars,
                totalForks = totalForks,
                totalOpenIssues = totalOpenIssues,
                repoCount = repos.size,
                starsDelta = starsDelta,
                forksDelta = forksDelta
            )
        )
    } catch (e: Exception) {
        Result.Error(friendlyError(e, "统计加载失败"))
    }

    @kotlinx.serialization.Serializable
    private data class RepoCounters(val stars: Int = 0, val forks: Int = 0)

    suspend fun getCachedUser(): com.mygithub.lab.data.api.GitHubUser? =
        try { api.getCurrentUser("Bearer $token") } catch (_: Exception) { null }

    suspend fun searchIssues(query: String): Result<List<com.mygithub.lab.data.api.GitHubIssue>> = try {
        Result.Success(api.searchIssues("Bearer $token", query).items)
    } catch (e: Exception) {
        Result.Error("搜索失败: ${e.message}")
    }

    suspend fun searchUsers(query: String): Result<List<com.mygithub.lab.data.api.GitHubUser>> = try {
        Result.Success(api.searchUsers("Bearer $token", query).items)
    } catch (e: Exception) {
        Result.Error("搜索失败: ${e.message}")
    }

    // ===== 搜索历史（Room） =====

    suspend fun getSearchHistory(type: String): List<String> =
        db.searchHistoryDao().getRecent(type)

    suspend fun addSearchHistory(query: String, type: String) {
        db.searchHistoryDao().insert(
            com.mygithub.lab.data.local.SearchHistoryEntity(query = query, type = type, searched_at = System.currentTimeMillis())
        )
    }

    // ===== Releases / Repo Issues =====

    suspend fun getReleases(owner: String, repo: String): List<com.mygithub.lab.data.api.GitHubRelease> = try {
        api.getReleases("Bearer $token", owner, repo)
    } catch (e: Exception) { emptyList() }

    suspend fun getRepoIssues(owner: String, repo: String): List<com.mygithub.lab.data.api.GitHubIssue> = try {
        api.getRepoIssues("Bearer $token", owner, repo)
    } catch (e: Exception) { emptyList() }

    /**
     * 仓库详情并行加载（4 个请求并行，时间从串行4×降到1×）
     */
    data class RepoDetailBundle(
        val readme: String?,
        val isStarred: Boolean,
        val releases: List<com.mygithub.lab.data.api.GitHubRelease>,
        val issues: List<com.mygithub.lab.data.api.GitHubIssue>
    )

    suspend fun loadRepoDetail(owner: String, repo: String, dark: Boolean = true): RepoDetailBundle =
        kotlinx.coroutines.coroutineScope {
            val readmeDeferred = async { getReadme(owner, repo, dark) }
            val starredDeferred = async { checkStarred(owner, repo) }
            val releasesDeferred = async { getReleases(owner, repo) }
            val issuesDeferred = async { getRepoIssues(owner, repo) }
            RepoDetailBundle(
                readme = readmeDeferred.await(),
                isStarred = starredDeferred.await(),
                releases = releasesDeferred.await(),
                issues = issuesDeferred.await()
            )
        }

    // ===== 推荐算法（端侧 + 并行请求 + Room 缓存） =====

    private var starredCache: List<GitHubRepo> = emptyList()

    fun getRecommendations(): Flow<Result<List<GitHubRepo>>> = flow {
        val cached = db.repoCacheDao().getByCategory("recommendations")
        if (cached.isNotEmpty()) {
            emit(Result.Success(cached.map { json.decodeFromString(GitHubRepo.serializer(), it.json) }, fromCache = true))
        }
        try {
            val remote = refreshRecommendations()
            if (cached.isEmpty() || remote.map { it.full_name }.toSet() != cached.map { it.full_name }.toSet()) {
                emit(Result.Success(remote))
            }
        } catch (e: Exception) {
            if (cached.isEmpty()) emit(Result.Error("推荐加载失败: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun refreshRecommendations(): List<GitHubRepo> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        starredCache = try { api.getStarred("Bearer $token") } catch (_: Exception) { emptyList() }
        val starredNames = starredCache.map { it.full_name }.toSet()
        val langCount = starredCache.mapNotNull { it.language }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
        val topLang = langCount.firstOrNull()?.key
        val topTopics = starredCache.flatMap { it.topics }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key }
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
        val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<GitHubRepo>>>()
        if (topTopics.isEmpty() && topLang == null) {
            // 无 Star：给热门推荐
            jobs.add(scope.async {
                try { api.searchRepos("Bearer $token", "stars:>10000 pushed:>2025-06-01").items.take(15) }
                catch (_: Exception) { emptyList() }
            })
            jobs.add(scope.async {
                try { api.searchRepos("Bearer $token", "stars:>5000 language:kotlin pushed:>2025-01-01").items.take(10) }
                catch (_: Exception) { emptyList() }
            })
        } else {
            topTopics.forEach { topic ->
                jobs.add(scope.async {
                    try { api.searchRepos("Bearer $token", "topic:$topic stars:>200").items.filter { it.full_name !in starredNames }.take(5) }
                    catch (_: Exception) { emptyList() }
                })
            }
            if (topLang != null) {
                jobs.add(scope.async {
                    try { api.searchRepos("Bearer $token", "language:$topLang stars:>1000 pushed:>2025-06-01").items.filter { it.full_name !in starredNames }.take(8) }
                    catch (_: Exception) { emptyList() }
                })
            }
            jobs.add(scope.async {
                try { api.searchRepos("Bearer $token", "stars:>5000 pushed:>2025-09-01").items.filter { it.full_name !in starredNames }.take(6) }
                catch (_: Exception) { emptyList() }
            })
        }
        val results = mutableListOf<GitHubRepo>()
        jobs.forEach { job -> job.await().forEach { repo -> if (results.none { it.full_name == repo.full_name }) results.add(repo) } }
        val final = results.distinctBy { it.full_name }.sortedByDescending { it.stargazers_count }.take(25)
        db.repoCacheDao().clearCategory("recommendations")
        db.repoCacheDao().upsertAll(final.map {
            RepoCacheEntity(full_name = it.full_name, json = json.encodeToString(GitHubRepo.serializer(), it), cached_at = System.currentTimeMillis(), category = "recommendations")
        })
        final
    }


    /** 用户画像：最高频语言与 topic */
    suspend fun getTopProfile(): Pair<String?, String?> {
        if (starredCache.isEmpty()) {
            starredCache = try { api.getStarred("Bearer $token") } catch (e: Exception) { emptyList() }
        }
        val topLang = starredCache.mapNotNull { it.language }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key
        val topTopic = starredCache.flatMap { it.topics }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key
        return topLang to topTopic
    }

    // ===== Trending 排行榜 =====

    /**
     * Trending：近 N 天创建/活跃 + 高星，Room 缓存（断网可用）
     */
    fun getTrending(days: Int, language: String?): Flow<Result<List<GitHubRepo>>> = flow {
        val cacheKey = "trending_${days}_${language ?: "all"}"
        val cached = db.repoCacheDao().getByCategory(cacheKey)
        if (cached.isNotEmpty()) {
            emit(Result.Success(cached.map { json.decodeFromString(GitHubRepo.serializer(), it.json) }, fromCache = true))
        }
        // 远程刷新
        try {
            val remote = fetchTrending(days, language)
            db.repoCacheDao().clearCategory(cacheKey)
            db.repoCacheDao().upsertAll(remote.map {
                RepoCacheEntity(full_name = it.full_name, json = json.encodeToString(GitHubRepo.serializer(), it), cached_at = System.currentTimeMillis(), category = cacheKey)
            })
            emit(Result.Success(remote))
        } catch (e: Exception) {
            if (cached.isNotEmpty()) {
                // 已有缓存，静默失败不报错
            } else {
                val fallback = db.repoCacheDao().getByCategory("trending_7_all")
                if (fallback.isNotEmpty()) {
                    emit(Result.Success(fallback.map { json.decodeFromString(GitHubRepo.serializer(), it.json) }, fromCache = true))
                } else {
                    emit(Result.Error("加载失败: ${e.message}"))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchTrending(days: Int, language: String?): List<GitHubRepo> {
        val langQuery = language?.let { " language:$it" } ?: ""
        // 日榜范围放宽：近2天 >500 星；周榜近7天 >3000；月榜近30天 >5000
        val (sinceDays, minStars) = when (days) { 1 -> 2 to 500; 7 -> 7 to 3000; else -> 30 to 5000 }
        val since = LocalDate.now().minusDays(sinceDays.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val q = "stars:>${minStars} pushed:>${since}$langQuery"
        return api.searchRepos("Bearer $token", q, perPage = 30).items
    }



    suspend fun checkStarred(owner: String, repo: String): Boolean = try {
        val resp = api.checkStarred("Bearer $token", owner, repo)
        resp.isSuccessful && resp.code() == 204
    } catch (e: Exception) { false }

    suspend fun starRepo(owner: String, repo: String): Boolean = try {
        api.starRepo("Bearer $token", owner, repo).isSuccessful
    } catch (e: Exception) { false }

    suspend fun unstarRepo(owner: String, repo: String): Boolean = try {
        api.unstarRepo("Bearer $token", owner, repo).isSuccessful
    } catch (e: Exception) { false }

    // ===== Star 标签（Room 本地） =====

    suspend fun addTag(repoFullName: String, tag: String) =
        db.starTagDao().insert(
            com.mygithub.lab.data.local.StarTagEntity(repo_full_name = repoFullName, tag = tag)
        )

    suspend fun removeTag(repoFullName: String, tag: String) =
        db.starTagDao().deleteTag(repoFullName, tag)

    suspend fun getAllTags(): List<String> = db.starTagDao().getAllTags()

    suspend fun getReposByTag(tag: String): List<String> = db.starTagDao().getByTag(tag).map { it.repo_full_name }

    /** UI 同步快照（Compose 重组时快速读） */
    fun getTagsSync(repoFullName: String): List<String> =
        kotlinx.coroutines.runBlocking { db.starTagDao().getTags(repoFullName).map { it.tag } }

    fun getReposByTagSync(tag: String): List<String> =
        kotlinx.coroutines.runBlocking { getReposByTag(tag) }

    // ===== 缓存 =====

    suspend fun clearCache() {
        db.repoCacheDao().clearCategory("personal")
        db.repoCacheDao().clearCategory("starred")
        db.repoCacheDao().clearCategory("trending_1_all")
        db.repoCacheDao().clearCategory("trending_7_all")
        db.repoCacheDao().clearCategory("trending_30_all")
    }

    // ===== 最近查看 =====

    suspend fun addRecentView(repo: GitHubRepo) {
        db.recentViewDao().insert(
            com.mygithub.lab.data.local.RecentViewEntity(
                repo_full_name = repo.full_name,
                owner = repo.ownerLogin,
                name = repo.name,
                description = repo.description.orEmpty(),
                language = repo.language ?: "",
                stars = repo.stargazers_count,
                viewed_at = System.currentTimeMillis()
            )
        )
    }

    suspend fun getRecentViews(): List<com.mygithub.lab.data.local.RecentViewEntity> =
        db.recentViewDao().getRecent()

    suspend fun clearRecentViews() = db.recentViewDao().clear()

    // ===== 当前用户 =====

    // ===== 关注者 / 正在关注 =====

    suspend fun getFollowers(username: String? = null): Result<List<com.mygithub.lab.data.api.GitHubUser>> = try {
        val data = if (username != null) api.getUserFollowers("Bearer $token", username)
        else api.getFollowers("Bearer $token")
        Result.Success(data)
    } catch (e: Exception) {
        Result.Error("加载失败: ${e.message}")
    }

    suspend fun getFollowing(username: String? = null): Result<List<com.mygithub.lab.data.api.GitHubUser>> = try {
        val data = if (username != null) api.getUserFollowing("Bearer $token", username)
        else api.getFollowing("Bearer $token")
        Result.Success(data)
    } catch (e: Exception) {
        Result.Error("加载失败: ${e.message}")
    }

    suspend fun currentUser(): Result<com.mygithub.lab.data.api.GitHubUser> = try {
        Result.Success(api.getCurrentUser("Bearer $token"))
    } catch (e: Exception) {
        Result.Error("加载失败: ${e.message}")
    }

    /**
     * 检查应用是否有新版本发布。
     *
     * 自动从 angusdevgo/MyGitHub 仓库拉取最新正式版 Release，
     * 仅当云端版本高于当前客户端、且附带 APK 资产、且说明包含 SHA-256 时才构造 UpdateInfo。
     */
    suspend fun checkAppUpdate(
        currentVersionName: String = com.mygithub.lab.BuildConfig.VERSION_NAME,
        owner: String = "angusdevgo",
        repo: String = "MyGitHub"
    ): com.mygithub.lab.data.update.UpdateInfo? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val currentVersion = com.mygithub.lab.data.update.Version.parse(currentVersionName) ?: return@withContext null
        val authHeader = if (token.isNotBlank()) "Bearer $token" else null
        val release = try {
            api.getLatestRelease(token = authHeader, owner = owner, repo = repo)
        } catch (_: Exception) {
            return@withContext null
        }

        val releaseVersion = com.mygithub.lab.data.update.Version.parse(release.tag_name)
            ?: com.mygithub.lab.data.update.Version.parse(release.name)
            ?: return@withContext null

        if (releaseVersion <= currentVersion) return@withContext null

        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: return@withContext null

        val sha256 = com.mygithub.lab.data.update.ChecksumParser.parse(release.body)
            ?: return@withContext null

        com.mygithub.lab.data.update.UpdateInfo(
            version = releaseVersion,
            releaseName = release.name?.ifBlank { release.tag_name } ?: release.tag_name,
            releaseNotes = release.body.orEmpty(),
            releasePageUrl = release.html_url,
            apkUrl = apkAsset.browser_download_url,
            apkSizeBytes = apkAsset.size,
            sha256 = sha256
        )
    }

    companion object {
        @Volatile private var instance: GitHubRepository? = null
        fun get(context: Context): GitHubRepository =
            instance ?: synchronized(this) {
                instance ?: GitHubRepository(context).also { instance = it }
            }

        /** 动态 Tab 保留的事件类型（滤掉 PushEvent 等刷屏噪声） */
        val ACTIVITY_EVENT_TYPES = setOf(
            "WatchEvent",
            "ForkEvent",
            "IssuesEvent",
            "IssueCommentEvent",
            "PullRequestEvent",
            "ReleaseEvent"
        )
    }
}


/**
 * README 包装：注入 GitHub 风格 CSS（支持明暗双主题）
 */
private fun wrapHtmlWithTheme(html: String, owner: String = "", repo: String = "", dark: Boolean = true): String {
    val bg = if (dark) "#111316" else "#FBFBFD"
    val text = if (dark) "#E3E2E6" else "#1A1C1E"
    val textVariant = if (dark) "#C4C6CF" else "#44474E"
    val border = if (dark) "#44474E" else "#E2E2E6"
    val codeBg = if (dark) "#1E2023" else "#F3F4F8"
    val tableThBg = if (dark) "#1E2023" else "#EEF0F4"
    val link = if (dark) "#B6C4FF" else "#3B5BDB"
    val css = """
    <style>
    html, body { overflow-x: hidden; max-width: 100vw; }
    body { font-family: -apple-system, sans-serif; font-size: 14px; line-height: 1.6; color: $text; background: $bg; padding: 0; margin: 0; word-wrap: break-word; overflow-x: hidden; max-width: 100vw; }
    h1, h2, h3, h4, h5, h6 { margin-top: 24px; margin-bottom: 16px; font-weight: 600; line-height: 1.25; }
    h1 { font-size: 2em; padding-bottom: .3em; border-bottom: 1px solid $border; }
    h2 { font-size: 1.5em; padding-bottom: .3em; border-bottom: 1px solid $border; }
    h3 { font-size: 1.25em; }
    p { margin-top: 0; margin-bottom: 16px; }
    a { color: $link; text-decoration: none; }
    code { font-family: "SF Mono", Consolas, monospace; font-size: 85%; padding: .2em .4em; background: $codeBg; border-radius: 6px; }
    pre { font-family: "SF Mono", Consolas, monospace; font-size: 13px; padding: 16px; background: $codeBg; border-radius: 8px; overflow-x: auto; max-width: 100%; }
    pre code { padding: 0; background: none; }
    blockquote { padding: 0 1em; color: $textVariant; border-left: .25em solid $border; margin: 0 0 16px; }
    table { border-spacing: 0; border-collapse: collapse; margin-bottom: 16px; max-width: 100%; }
    table th, table td { padding: 6px 13px; border: 1px solid $border; }
    table th { font-weight: 600; background: $tableThBg; }
    img { max-width: 100%; height: auto; }
    ul, ol { margin-top: 0; margin-bottom: 16px; padding-left: 2em; }
    li { margin-top: .25em; margin-bottom: .25em; }
    hr { height: .25em; border: 0; background: $border; margin: 24px 0; }
    :root { color-scheme: ${if (dark) "dark" else "light"}; }
    </style>
    """.trimIndent()
    var processedHtml = html
    if (owner.isNotEmpty() && repo.isNotEmpty()) {
        val rawBase = "https://raw.githubusercontent.com/$owner/$repo/main/"
        val srcRegex = Regex("src=\"(?!https?://)([^\"]+)\"")
        processedHtml = srcRegex.replace(html) { m ->
            val path = m.groupValues[1].removePrefix("./").removePrefix("/")
            "src=\"$rawBase$path\""
        }
    }
    // star-history 图表主题跟随：浅色主题下把 theme=dark 替换为 theme=light
    if (!dark) {
        processedHtml = processedHtml.replace("theme=dark", "theme=light")
    }
    return "<html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'>$css</head><body>$processedHtml</body></html>"
}

/**
 * 生成仓库详情完整 HTML：头部信息卡 + README 内容
 * 整个页面由一个 WebView 渲染，自己管理滚动，头部随内容一起滚动
 */
fun buildRepoDetailHtml(repo: GitHubRepo, readmeHtml: String?, isStarred: Boolean = false, releasesCount: Int = 0, latestTag: String? = null, dark: Boolean = true): String {
    val bg = if (dark) "#111316" else "#FBFBFD"
    val text = if (dark) "#E3E2E6" else "#1A1C1E"
    val textVariant = if (dark) "#C4C6CF" else "#44474E"
    val border = if (dark) "#44474E" else "#E2E2E6"
    val surfaceBg = if (dark) "#1E2023" else "#F3F4F8"
    val codeBg = if (dark) "#1E2023" else "#F3F4F8"
    val tableThBg = if (dark) "#1E2023" else "#EEF0F4"
    val link = if (dark) "#B6C4FF" else "#3B5BDB"
    val accent = if (dark) "#B6C4FF" else "#3B5BDB"
    val surfaceBorder = if (dark) "#44474E" else "#E2E2E6"
    val css = """
    <style>
    * { box-sizing: border-box; }
    html, body { overflow-x: hidden; max-width: 100vw; }
    body { font-family: -apple-system, sans-serif; font-size: 14px; line-height: 1.6; color: ${if (dark) "#E3E2E6" else "#1A1C1E"}; background: ${if (dark) "#111316" else "#FBFBFD"}; margin: 0; padding: 0; overflow-x: hidden; max-width: 100vw; }
    .header { padding: 16px 20px; }
    .owner { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
    .avatar { width: 24px; height: 24px; border-radius: 50%; background: $accent; color: $bg; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: bold; }
    .owner-name { color: $textVariant; font-size: 14px; }
    .repo-name { font-size: 24px; font-weight: bold; color: $text; margin-bottom: 8px; }
    .desc { color: $textVariant; font-size: 15px; line-height: 22px; margin-bottom: 12px; }
    .stats { display: flex; gap: 20px; align-items: center; margin-bottom: 12px; flex-wrap: wrap; }
    .stat { display: flex; align-items: center; gap: 5px; font-size: 14px; }
    .stat-icon { font-size: 14px; }
    .star { color: $accent; font-weight: 500; }
    .fork { color: $textVariant; }
    .lang-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
    .lang-text { color: $textVariant; font-size: 14px; }
    .actions { display: flex; gap: 10px; margin-bottom: 12px; }
    .btn { flex: 1; padding: 8px 12px; border: 1px solid $border; border-radius: 8px; text-align: center; color: $text; font-size: 14px; text-decoration: none; background: transparent; }
    .btn:active { background: $surfaceBg; }
    .topics { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }
    .topic { background: $surfaceBg; color: $accent; font-size: 11px; padding: 3px 8px; border-radius: 12px; }
    .divider { height: 1px; background: $border; margin: 0 20px; opacity: 0.4; }
    .action-row { display: flex; justify-content: space-between; align-items: center; padding: 12px 20px; }
    .action-left { display: flex; align-items: center; gap: 14px; }
    .action-icon-box { width: 28px; height: 28px; border-radius: 6px; display: flex; align-items: center; justify-content: center; font-size: 14px; }
    .action-title { font-size: 15px; font-weight: 500; color: $text; }
    .action-right { display: flex; align-items: center; gap: 6px; color: $textVariant; font-size: 14px; }
    .readme-title { padding: 10px 20px; font-size: 16px; font-weight: 600; color: $text; }
    h1, h2, h3, h4, h5, h6 { margin-top: 24px; margin-bottom: 16px; font-weight: 600; }
    h1 { font-size: 2em; padding-bottom: .3em; border-bottom: 1px solid $border; }
    h2 { font-size: 1.5em; padding-bottom: .3em; border-bottom: 1px solid $border; }
    h3 { font-size: 1.25em; }
    p { margin-top: 0; margin-bottom: 16px; }
    a { color: $accent; text-decoration: none; }
    code { font-family: 'SF Mono', Consolas, monospace; font-size: 85%; padding: .2em .4em; background: $surfaceBg; border-radius: 6px; }
    pre { font-family: 'SF Mono', Consolas, monospace; font-size: 13px; padding: 16px; background: $surfaceBg; border-radius: 8px; overflow-x: auto; max-width: 100%; }
    pre code { padding: 0; background: none; }
    blockquote { padding: 0 1em; color: $textVariant; border-left: .25em solid $border; }
    table { border-spacing: 0; max-width: 100%; border-collapse: collapse; margin-bottom: 16px; width: 100%; }
    table th, table td { padding: 6px 13px; border: 1px solid $border; }
    table th { font-weight: 600; background: $surfaceBg; }
    img { max-width: 100%; height: auto; }
    ul, ol { margin-top: 0; margin-bottom: 16px; padding-left: 2em; }
    li { margin-top: .25em; margin-bottom: .25em; }
    hr { height: .25em; border: 0; background: $border; margin: 24px 0; }
    :root { color-scheme: ${if (dark) "dark" else "light"}; }
    </style>
    """.trimIndent()

    val LANG_COLORS = mapOf("Kotlin" to "#A97BFF","Java" to "#b07219","Python" to "#3572A5","JavaScript" to "#f1e05a","TypeScript" to "#3178c6","Go" to "#00ADD8","Rust" to "#dea584","C" to "#555555","C++" to "#f34b7d","C#" to "#178600","Swift" to "#F05138","Dart" to "#00B4AB","Vue" to "#41b883","Shell" to "#89e051","Ruby" to "#701516","PHP" to "#4F5D95")
    val langColorHex = repo.language?.let { lang ->
        LANG_COLORS[lang] ?: "$textVariant"
        
    } ?: "$textVariant"

    val header = """
    <div class="header">
        <div class="owner">
            <div class="avatar">${repo.ownerLogin.take(1).uppercase()}</div>
            <span class="owner-name">${repo.ownerLogin}</span>
        </div>
        <div class="repo-name">${repo.name}</div>
        ${if (!repo.description.isNullOrBlank()) "<div class=\"desc\">${repo.description}</div>" else ""}
        <div class="stats">
            <div class="stat star"><span class="stat-icon">★</span> ${formatStarsCompat(repo.stargazers_count)} 星标</div>
            <div class="stat fork"><span class="stat-icon">⑂</span> ${repo.forks_count} 复刻</div>
            ${if (repo.language != null) "<div class=\"stat\"><span class=\"lang-dot\" style=\"background:$langColorHex\"></span><span class=\"lang-text\">${repo.language}</span></div>" else ""}
        </div>
        <div class="actions">
            <a class="btn" href="mygithub://star">★ ${if (isStarred) "已标星" else "标星"}</a>
            <a class="btn" href="${repo.html_url}/fork">⑂ Fork</a>
        </div>
        ${if (repo.topics.isNotEmpty()) "<div class=\"topics\">" + repo.topics.joinToString("") { "<span class=\"topic\">#$it</span>" } + "</div>" else ""}
    </div>
    <div class="divider"></div>
    <div class="action-row" onclick="window.location.href='mygithub://issues'">
        <div class="action-left">
            <div class="action-icon-box" style="background:rgba(76,175,80,0.15);color:#4CAF50">⊙</div>
            <span class="action-title">议题 (Issues)</span>
        </div>
        <div class="action-right">${repo.open_issues_count} ›</div>
    </div>
    <div class="action-row" onclick="window.location.href='mygithub://releases'">
        <div class="action-left">
            <div class="action-icon-box" style="background:rgba(33,150,243,0.15);color:#2196F3">📦</div>
            <span class="action-title">版本发布 (Releases)</span>
        </div>
        <div class="action-right">›</div>
    </div>
    <div class="divider"></div>
    <div class="readme-title">README.md</div>
    """.trimIndent()

    val readmeContent = readmeHtml ?: "<div style='padding:40px;text-align:center;color:$textVariant'>该仓库没有提供 README</div>"

    return "<html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'>$css</head><body>$header<div style='padding:0 20px 40px'>$readmeContent</div></body></html>"
}

private fun formatStarsCompat(count: Int): String = when {
    count >= 10000 -> String.format("%.1fw", count / 10000f)
    count >= 1000 -> String.format("%.1fk", count / 1000f)
    else -> count.toString()
}
