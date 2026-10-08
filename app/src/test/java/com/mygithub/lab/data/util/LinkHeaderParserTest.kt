package com.mygithub.lab.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkHeaderParserTest {

    @Test
    fun parseLastPage_normalHeader_returnsLastPageNumber() {
        val header = """<https://api.github.com/notifications?all=true&page=2&per_page=100>; rel="next", <https://api.github.com/notifications?all=true&page=7&per_page=100>; rel="last""""
        assertEquals(7, LinkHeaderParser.parseLastPage(header))
    }

    @Test
    fun parseLastPage_noLastRel_returnsNull() {
        val header = """<https://api.github.com/notifications?page=2>; rel="next""""
        assertNull(LinkHeaderParser.parseLastPage(header))
    }

    @Test
    fun parseLastPage_nullOrBlank_returnsNull() {
        assertNull(LinkHeaderParser.parseLastPage(null))
        assertNull(LinkHeaderParser.parseLastPage(""))
        assertNull(LinkHeaderParser.parseLastPage("   "))
    }

    @Test
    fun parseLastPage_malformed_returnsNullWithoutException() {
        assertNull(LinkHeaderParser.parseLastPage("not a real header"))
        assertNull(LinkHeaderParser.parseLastPage("<bad-url>; rel=\"last\""))
    }

    @Test
    fun parseNextPage_extractsCorrectly() {
        val header = """<https://api.github.com/notifications?page=3>; rel="next", <https://api.github.com/notifications?page=5>; rel="last""""
        assertEquals(3, LinkHeaderParser.parseNextPage(header))
    }
}
