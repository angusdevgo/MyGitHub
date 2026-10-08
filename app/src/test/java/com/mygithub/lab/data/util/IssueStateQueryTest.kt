package com.mygithub.lab.data.util

import com.mygithub.lab.data.model.IssueRef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueStateQueryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun buildQuery_mixedIssuesAndPrs_generatesValidGraphQLAliases() {
        val refs = listOf(
            IssueRef("url1", "ownerA", "repoA", 10, isPr = false),
            IssueRef("url2", "ownerA", "repoA", 11, isPr = true),
            IssueRef("url3", "ownerB", "repoB", 5, isPr = false)
        )
        val chunks = IssueStateQuery.chunkRefs(refs)
        assertEquals(1, chunks.size)
        val query = IssueStateQuery.buildQuery(chunks[0])

        assertTrue(query.contains("r_ownerA_repoA: repository(owner: \"ownerA\", name: \"repoA\")"))
        assertTrue(query.contains("i0: issue(number: 10) { state }"))
        assertTrue(query.contains("i1: pullRequest(number: 11) { state merged }"))
        assertTrue(query.contains("r_ownerB_repoB: repository(owner: \"ownerB\", name: \"repoB\")"))
        assertTrue(query.contains("i0: issue(number: 5) { state }"))
    }

    @Test
    fun parseResponse_normalPayload_mapsStatesCorrectly() {
        val refs = listOf(
            IssueRef("url1", "ownerA", "repoA", 10, isPr = false),
            IssueRef("url2", "ownerA", "repoA", 11, isPr = true),
            IssueRef("url3", "ownerA", "repoA", 12, isPr = true)
        )
        val chunks = IssueStateQuery.chunkRefs(refs)

        val rawJson = """
        {
            "r_ownerA_repoA": {
                "i0": { "state": "OPEN" },
                "i1": { "state": "CLOSED", "merged": false },
                "i2": { "state": "CLOSED", "merged": true }
            }
        }
        """.trimIndent()
        val data = json.parseToJsonElement(rawJson).jsonObject

        val states = IssueStateQuery.parseResponse(data, chunks[0])

        assertEquals("open", states["url1"])
        assertEquals("closed", states["url2"])
        assertEquals("merged", states["url3"])
    }

    @Test
    fun parseResponse_missingNodeOrField_skipsGracefullyWithoutThrowing() {
        val refs = listOf(
            IssueRef("url1", "ownerA", "repoA", 10, isPr = false),
            IssueRef("url2", "ownerA", "repoA", 99, isPr = false) // absent in payload
        )
        val chunks = IssueStateQuery.chunkRefs(refs)

        val rawJson = """
        {
            "r_ownerA_repoA": {
                "i0": { "state": "OPEN" }
            }
        }
        """.trimIndent()
        val data = json.parseToJsonElement(rawJson).jsonObject

        val states = IssueStateQuery.parseResponse(data, chunks[0])
        assertEquals("open", states["url1"])
        assertEquals(null, states["url2"])
    }

    @Test
    fun chunkRefs_capsAliasesPerChunk() {
        val refs = (1..95).map { n ->
            IssueRef("url$n", "owner", "repo$n", 1, isPr = false)
        }
        val chunks = IssueStateQuery.chunkRefs(refs)
        // 95 别名，MAX_ALIASES=40 → 至少分 3 个 alias-chunk
        assertTrue(chunks.size >= 3)
    }
}
