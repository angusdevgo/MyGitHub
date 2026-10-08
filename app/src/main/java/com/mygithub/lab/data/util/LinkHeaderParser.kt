package com.mygithub.lab.data.util

import java.util.regex.Pattern

/**
 * GitHub API Link 响应头解析器（RFC 5988）
 * 示例: <https://api.github.com/notifications?page=2>; rel="next", <https://api.github.com/notifications?page=5>; rel="last"
 */
object LinkHeaderParser {
    private val LINK_PATTERN: Pattern = Pattern.compile("<([^>]+)>;\\s*rel=\"([^\"]+)\"")
    private val PAGE_PARAM_PATTERN: Pattern = Pattern.compile("[?&]page=(\\d+)")

    fun parseLastPage(linkHeader: String?): Int? {
        if (linkHeader.isNullOrBlank()) return null
        val matcher = LINK_PATTERN.matcher(linkHeader)
        while (matcher.find()) {
            val url = matcher.group(1) ?: continue
            val rel = matcher.group(2) ?: continue
            if (rel.equals("last", ignoreCase = true)) {
                val pageMatcher = PAGE_PARAM_PATTERN.matcher(url)
                if (pageMatcher.find()) {
                    return pageMatcher.group(1)?.toIntOrNull()
                }
            }
        }
        return null
    }

    fun parseNextPage(linkHeader: String?): Int? {
        if (linkHeader.isNullOrBlank()) return null
        val matcher = LINK_PATTERN.matcher(linkHeader)
        while (matcher.find()) {
            val url = matcher.group(1) ?: continue
            val rel = matcher.group(2) ?: continue
            if (rel.equals("next", ignoreCase = true)) {
                val pageMatcher = PAGE_PARAM_PATTERN.matcher(url)
                if (pageMatcher.find()) {
                    return pageMatcher.group(1)?.toIntOrNull()
                }
            }
        }
        return null
    }
}
