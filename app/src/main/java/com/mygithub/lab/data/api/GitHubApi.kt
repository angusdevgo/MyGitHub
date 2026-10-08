package com.mygithub.lab.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * GitHub REST API v3 核心接口
 */
interface GitHubApi {

    @GET("user")
    suspend fun getCurrentUser(@Header("Authorization") token: String): GitHubUser

    @GET("user/repos")
    suspend fun getUserRepos(
        @Header("Authorization") token: String,
        @Query("visibility") visibility: String? = null,
        @Query("affiliation") affiliation: String? = "owner,collaborator,organization_member",
        @Query("sort") sort: String = "pushed",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): List<GitHubRepo>

    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRepo

    @GET("repos/{owner}/{repo}/readme")
    suspend fun getReadme(
        @Header("Authorization") token: String,
        @Header("Accept") accept: String = "application/vnd.github.raw+json",
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): retrofit2.Response<String>

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 20
    ): List<GitHubRelease>

    /**
     * 最新正式版 Release（不含 pre-release / draft）。
     * 仓库尚无 Release 时返回 404。
     */
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Header("Authorization") token: String? = null,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease

    @GET("repos/{owner}/{repo}/issues")
    suspend fun getRepoIssues(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("sort") sort: String = "updated",
        @Query("direction") direction: String = "desc",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): List<GitHubIssue>

    @GET("repos/{owner}/{repo}/issues/{number}")
    suspend fun getIssue(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int
    ): GitHubIssue

    @POST("repos/{owner}/{repo}/issues/{number}/comments")
    suspend fun createComment(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Body body: CreateCommentRequest
    ): GitHubComment

    @PATCH("repos/{owner}/{repo}/issues/{number}")
    suspend fun updateIssue(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Body body: UpdateIssueRequest
    ): GitHubIssue

    @GET("repos/{owner}/{repo}/issues/{number}/comments")
    suspend fun getIssueComments(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Query("per_page") perPage: Int = 50
    ): List<GitHubComment>

    @GET("user/starred")
    suspend fun getStarred(
        @Header("Authorization") token: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): List<GitHubRepo>

    @GET("user/followers")
    suspend fun getFollowers(
        @Header("Authorization") token: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): List<GitHubUser>

    @GET("user/following")
    suspend fun getFollowing(
        @Header("Authorization") token: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): List<GitHubUser>

    @GET("users/{username}/followers")
    suspend fun getUserFollowers(
        @Header("Authorization") token: String,
        @Path("username") username: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): List<GitHubUser>

    @GET("users/{username}/following")
    suspend fun getUserFollowing(
        @Header("Authorization") token: String,
        @Path("username") username: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): List<GitHubUser>

    @GET("user/starred/{owner}/{repo}")
    suspend fun checkStarred(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): retrofit2.Response<Unit>

    @GET("repos/{owner}/{repo}/events")
    suspend fun getRepoEvents(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): List<GitHubEvent>

    @GET("users/{username}/events")
    suspend fun getUserEvents(
        @Header("Authorization") token: String,
        @Path("username") username: String,
        @Query("per_page") perPage: Int = 30
    ): List<GitHubEvent>

    @PUT("user/starred/{owner}/{repo}")
    suspend fun starRepo(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): retrofit2.Response<Unit>

    @DELETE("user/starred/{owner}/{repo}")
    suspend fun unstarRepo(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): retrofit2.Response<Unit>

    @GET("issues")
    suspend fun getMyIssues(
        @Header("Authorization") token: String,
        @Query("filter") filter: String = "assigned",
        @Query("state") state: String = "open",
        @Query("per_page") perPage: Int = 100
    ): List<GitHubIssue>

    @GET("search/repositories")
    suspend fun searchRepos(
        @Header("Authorization") token: String,
        @Query("q") query: String,
        @Query("sort") sort: String = "stars",
        @Query("order") order: String = "desc",
        @Query("per_page") perPage: Int = 30
    ): SearchRepoResult

    @GET("search/issues")
    suspend fun searchIssues(
        @Header("Authorization") token: String,
        @Query("q") query: String,
        @Query("per_page") perPage: Int = 30
    ): SearchIssueResult

    @GET("search/users")
    suspend fun searchUsers(
        @Header("Authorization") token: String,
        @Query("q") query: String,
        @Query("per_page") perPage: Int = 30
    ): SearchUserResult
}

@Serializable
data class GitHubUser(
    val login: String = "",
    val id: Long = 0,
    val avatar_url: String = "",
    val name: String? = null,
    val bio: String? = null,
    val public_repos: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
    val html_url: String = ""
)

@Serializable
data class CreateCommentRequest(val body: String)

@Serializable
data class UpdateIssueRequest(val state: String? = null, val title: String? = null, val body: String? = null)

@Serializable
data class GitHubComment(
    val id: Long = 0,
    val body: String = "",
    val user: GitHubOwner? = null,
    val created_at: String = "",
    val updated_at: String = "",
    val html_url: String = ""
)

@Serializable
data class GitHubEvent(
    val id: String = "",
    val type: String = "", // PushEvent, WatchEvent, IssuesEvent, ForkEvent, CreateEvent, etc
    val actor: EventActor = EventActor(),
    val repo: EventRepo = EventRepo(),
    val payload: EventPayload = EventPayload(),
    val created_at: String = ""
)

@Serializable
data class EventActor(val login: String = "", val avatar_url: String = "")

@Serializable
data class EventRepo(val name: String = "", val url: String = "")

@Serializable
data class EventPayload(
    val action: String = "", // opened, closed, created, etc
    val ref: String? = null,
    val ref_type: String? = null,
    val size: Int = 0,
    val description: String? = null
)

@Serializable
data class GitHubRepo(
    val id: Long = 0,
    val name: String = "",
    val full_name: String = "",
    val owner: GitHubOwner = GitHubOwner(),
    val private: Boolean = false,
    val fork: Boolean = false,
    val description: String? = null,
    val language: String? = null,
    val stargazers_count: Int = 0,
    val forks_count: Int = 0,
    val watchers_count: Int = 0,
    val open_issues_count: Int = 0,
    val pushed_at: String? = null,
    val html_url: String = "",
    val topics: List<String> = emptyList()
) {
    val ownerLogin: String get() = owner.login
}

@Serializable
data class GitHubRelease(
    val id: Long = 0,
    val tag_name: String = "",
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val html_url: String = "",
    val published_at: String? = null,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val id: Long = 0,
    val name: String = "",
    val size: Long = 0,
    val download_count: Long = 0,
    val browser_download_url: String = ""
)

@Serializable
data class GitHubOwner(
    val login: String = "",
    val id: Long = 0,
    val avatar_url: String = ""
)

@Serializable
data class GitHubIssue(
    val id: Long = 0,
    val number: Int = 0,
    val title: String = "",
    val state: String = "open",
    val body: String? = null,
    val user: GitHubOwner? = null,
    val labels: List<GitHubLabel> = emptyList(),
    val comments: Int = 0,
    val created_at: String = "",
    val updated_at: String = "",
    val html_url: String = "",
    val repository_url: String = "",
    /** 仅当该条目实际是 Pull Request 时存在（GitHub REST 约定） */
    val pull_request: PullRequestMarker? = null
) {
    /** 从 repository_url (api.github.com/repos/o/r) 提取 o/r */
    val repoFullName: String get() =
        repository_url.substringAfter("repos/", "").ifBlank { "" }

    /** 是否为 Pull Request */
    val isPr: Boolean get() = pull_request != null

    /** 是否为已合并的 Pull Request */
    val isMerged: Boolean get() = pull_request?.merged_at != null
}

@Serializable
data class PullRequestMarker(
    val url: String? = null,
    val html_url: String? = null,
    val diff_url: String? = null,
    val patch_url: String? = null,
    val merged_at: String? = null
)

@Serializable
data class GitHubLabel(
    val id: Long = 0,
    val name: String = "",
    val color: String = ""
)

@Serializable
data class SearchRepoResult(
    val total_count: Int = 0,
    val incomplete_results: Boolean = false,
    val items: List<GitHubRepo> = emptyList()
)

@Serializable
data class SearchIssueResult(
    val total_count: Int = 0,
    val incomplete_results: Boolean = false,
    val items: List<GitHubIssue> = emptyList()
)

@Serializable
data class SearchUserResult(
    val total_count: Int = 0,
    val incomplete_results: Boolean = false,
    val items: List<GitHubUser> = emptyList()
)
