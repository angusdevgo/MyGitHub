package com.mygithub.lab.ui.screens.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueRefParserTest {

    @Test
    fun parseIssueRef_standardIssueUrl_parsesCorrectly() {
        val url = "https://api.github.com/repos/torvalds/linux/issues/1234"
        val ref = parseIssueRef(url)
        assertNotNull(ref)
        assertEquals("torvalds", ref!!.owner)
        assertEquals("linux", ref.repo)
        assertEquals(1234, ref.number)
        assertFalse(ref.isPr)
    }

    @Test
    fun parseIssueRef_standardPrUrl_parsesCorrectly() {
        val url = "https://api.github.com/repos/torvalds/linux/pulls/5678"
        val ref = parseIssueRef(url)
        assertNotNull(ref)
        assertEquals("torvalds", ref!!.owner)
        assertEquals("linux", ref.repo)
        assertEquals(5678, ref.number)
        assertTrue(ref.isPr)
    }

    @Test
    fun parseIssueRef_releaseOrCommitUrl_returnsNull() {
        assertNull(parseIssueRef("https://api.github.com/repos/torvalds/linux/releases/1"))
        assertNull(parseIssueRef("https://api.github.com/repos/torvalds/linux/commits/abcdef"))
    }

    @Test
    fun parseIssueRef_malformed_returnsNull() {
        assertNull(parseIssueRef(""))
        assertNull(parseIssueRef("not a url"))
        assertNull(parseIssueRef("https://api.github.com/repos/invalid"))
    }
}
