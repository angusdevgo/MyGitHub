package com.mygithub.lab.data.util

import com.mygithub.lab.data.api.GitHubIssue
import com.mygithub.lab.data.api.PullRequestMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnedIssueMappingTest {

    // ===== PR / 合并状态判定 =====

    @Test
    fun plainIssue_isNotPrAndNotMerged() {
        val issue = GitHubIssue(number = 1, title = "Bug", state = "open", pull_request = null)
        assertFalse(issue.isPr)
        assertFalse(issue.isMerged)
    }

    @Test
    fun openPullRequest_isPrButNotMerged() {
        val pr = GitHubIssue(
            number = 2, title = "Feature", state = "open",
            pull_request = PullRequestMarker(url = "https://api.github.com/.../pulls/2", merged_at = null)
        )
        assertTrue(pr.isPr)
        assertFalse(pr.isMerged)
    }

    @Test
    fun mergedPullRequest_isPrAndMerged() {
        val pr = GitHubIssue(
            number = 3, title = "Fix", state = "closed",
            pull_request = PullRequestMarker(url = "https://api.github.com/.../pulls/3", merged_at = "2026-10-01T00:00:00Z")
        )
        assertTrue(pr.isPr)
        assertTrue(pr.isMerged)
    }

    // ===== 映射 =====

    @Test
    fun map_carriesAllFieldsAndRepoFullName() {
        val issues = listOf(
            GitHubIssue(
                number = 42, title = "Issue A", state = "open", comments = 3,
                updated_at = "2026-10-05T10:00:00Z", html_url = "https://github.com/o/r/issues/42"
            )
        )
        val mapped = OwnedIssueMapper.map("o/r", issues)
        assertEquals(1, mapped.size)
        val item = mapped.first()
        assertEquals("o/r", item.repoFullName)
        assertEquals(42, item.number)
        assertEquals("Issue A", item.title)
        assertEquals("open", item.state)
        assertEquals(3, item.comments)
        assertEquals("2026-10-05T10:00:00Z", item.updatedAt)
        assertEquals("https://github.com/o/r/issues/42", item.htmlUrl)
        assertFalse(item.isPr)
    }

    // ===== 缓存主键 =====

    @Test
    fun cacheKey_usesOwnerRepoHashNumber() {
        val item = OwnedIssueMapper.map("owner/repo", listOf(GitHubIssue(number = 42))).first()
        assertEquals("owner/repo#42", item.cacheKey)
    }

    @Test
    fun cacheKey_doesNotCollideWithOtherCacheCategories() {
        // received_activity 用事件 id，received_counters 用仓库全名；议题键带 "#" 分隔，天然不冲突
        val issueKey = OwnedIssueMapper.map("owner/repo", listOf(GitHubIssue(number = 7))).first().cacheKey
        assertTrue(issueKey.contains("#"))
        assertFalse(issueKey == "owner/repo")
    }

    // ===== 展示状态 =====

    @Test
    fun displayState_mergedTakesPrecedence() {
        val merged = OwnedIssueMapper.map(
            "o/r",
            listOf(GitHubIssue(number = 1, state = "closed", pull_request = PullRequestMarker(merged_at = "2026-01-01T00:00:00Z")))
        ).first()
        assertEquals("merged", merged.displayState)
    }

    @Test
    fun displayState_closedIssue() {
        val closed = OwnedIssueMapper.map("o/r", listOf(GitHubIssue(number = 1, state = "closed"))).first()
        assertEquals("closed", closed.displayState)
    }

    @Test
    fun displayState_openIssue() {
        val open = OwnedIssueMapper.map("o/r", listOf(GitHubIssue(number = 1, state = "open"))).first()
        assertEquals("open", open.displayState)
    }

    // ===== 去重与排序 =====

    @Test
    fun curate_deduplicatesByCacheKeyAndSortsByUpdatedAtDesc() {
        val items = OwnedIssueMapper.map(
            "o/r",
            listOf(
                GitHubIssue(number = 1, title = "old", updated_at = "2026-10-01T00:00:00Z"),
                GitHubIssue(number = 2, title = "new", updated_at = "2026-10-09T00:00:00Z"),
                GitHubIssue(number = 3, title = "mid", updated_at = "2026-10-05T00:00:00Z")
            )
        )
        // 追加一条与 #2 相同 cacheKey 的重复项
        val duplicated = items + items.first { it.number == 2 }.copy(title = "new-dup")

        val curated = OwnedIssueMapper.curate(duplicated)

        assertEquals(3, curated.size)
        assertEquals(2, curated[0].number)
        assertEquals(3, curated[1].number)
        assertEquals(1, curated[2].number)
    }

    @Test
    fun curate_mergesAcrossReposSortedByUpdatedAt() {
        val a = OwnedIssueMapper.map("o/a", listOf(GitHubIssue(number = 1, updated_at = "2026-10-02T00:00:00Z")))
        val b = OwnedIssueMapper.map("o/b", listOf(GitHubIssue(number = 1, updated_at = "2026-10-08T00:00:00Z")))
        val curated = OwnedIssueMapper.curate(a + b)
        assertEquals(2, curated.size)
        assertEquals("o/b", curated[0].repoFullName)
        assertEquals("o/a", curated[1].repoFullName)
    }

    @Test
    fun curate_dropsInvalidEntries() {
        val invalid = OwnedIssueMapper.map("", listOf(GitHubIssue(number = 1)))
        val zeroNumber = OwnedIssueMapper.map("o/r", listOf(GitHubIssue(number = 0)))
        val valid = OwnedIssueMapper.map("o/r", listOf(GitHubIssue(number = 5)))
        val curated = OwnedIssueMapper.curate(invalid + zeroNumber + valid)
        assertEquals(1, curated.size)
        assertEquals(5, curated.first().number)
    }
}
