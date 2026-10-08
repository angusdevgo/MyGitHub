package com.mygithub.lab.data.util

import com.mygithub.lab.data.api.EventActor
import com.mygithub.lab.data.api.EventPayload
import com.mygithub.lab.data.api.EventRepo
import com.mygithub.lab.data.api.GitHubEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class DataMergeUtilTest {

    @Test
    fun curateEvents_filtersPushAndKeepsAllowedTypesSortedDesc() {
        val allowed = setOf("WatchEvent", "ForkEvent", "IssuesEvent")
        val events = listOf(
            makeEvent("e1", "PushEvent", "2026-10-01T12:00:00Z"), // disallowed
            makeEvent("e2", "WatchEvent", "2026-10-01T10:00:00Z"),
            makeEvent("e3", "ForkEvent", "2026-10-01T14:00:00Z"), // newer
            makeEvent("e3", "ForkEvent", "2026-10-01T14:00:00Z"), // duplicate id
            makeEvent("e4", "IssuesEvent", "2026-10-01T11:00:00Z")
        )

        val result = DataMergeUtil.curateEvents(events, allowed)

        assertEquals(3, result.size)
        // 应该按时间倒序: e3 (14:00) -> e4 (11:00) -> e2 (10:00)
        assertEquals("e3", result[0].id)
        assertEquals("e4", result[1].id)
        assertEquals("e2", result[2].id)
    }

    private fun makeEvent(id: String, type: String, createdAt: String) =
        GitHubEvent(
            id = id,
            type = type,
            actor = EventActor("user", ""),
            repo = EventRepo("owner/repo", ""),
            payload = EventPayload(),
            created_at = createdAt
        )
}
