package com.mygithub.lab.data.util

import com.mygithub.lab.data.model.IssueRef
import com.mygithub.lab.data.model.IssueState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

typealias RepoKey = Pair<String, String>

/**
 * GraphQL 批量 Issue/PR 状态查询构建与解析（纯函数，便于单元测试）
 */
object IssueStateQuery {

    const val MAX_ALIASES_PER_REQUEST = 40
    const val MAX_REPOS_PER_REQUEST = 8

    /**
     * 把 refs 按「每请求最多 MAX_ALIASES_PER_REQUEST 个别名、MAX_REPOS_PER_REQUEST 个仓库」分片
     */
    fun chunkRefs(refs: List<IssueRef>): List<List<Map.Entry<RepoKey, List<IssueRef>>>> {
        if (refs.isEmpty()) return emptyList()
        return refs
            .distinctBy { it.url }
            .chunked(MAX_ALIASES_PER_REQUEST)
            .map { aliasChunk ->
                aliasChunk.groupBy { it.owner to it.repo }
                    .entries
                    .chunked(MAX_REPOS_PER_REQUEST)
                    .flatten()
            }
    }

    /** 为单个分片生成 GraphQL query */
    fun buildQuery(repoGroup: List<Map.Entry<RepoKey, List<IssueRef>>>): String {
        val sb = StringBuilder("query IssueStates {\n")
        repoGroup.forEach { (key, repoRefs) ->
            val (owner, repoName) = key
            sb.append("  ${aliasFor(owner, repoName)}: repository(owner: \"$owner\", name: \"$repoName\") {\n")
            repoRefs.forEachIndexed { idx, ref ->
                if (ref.isPr) {
                    sb.append("    i$idx: pullRequest(number: ${ref.number}) { state merged }\n")
                } else {
                    sb.append("    i$idx: issue(number: ${ref.number}) { state }\n")
                }
            }
            sb.append("  }\n")
        }
        sb.append("}")
        return sb.toString()
    }

    fun aliasFor(owner: String, repo: String): String = "r_${owner}_$repo"

    /**
     * 解析 GraphQL data 节点，返回 url -> state 映射
     * 缺字段的条目直接跳过，绝不抛异常
     */
    fun parseResponse(
        data: JsonObject?,
        repoGroup: List<Map.Entry<RepoKey, List<IssueRef>>>
    ): Map<String, String> {
        if (data == null) return emptyMap()
        val out = mutableMapOf<String, String>()
        repoGroup.forEach { (key, repoRefs) ->
            val repoNode = runCatching {
                data[aliasFor(key.first, key.second)]?.jsonObject
            }.getOrNull() ?: return@forEach
            repoRefs.forEachIndexed { idx, ref ->
                val node = runCatching { repoNode["i$idx"]?.jsonObject }.getOrNull() ?: return@forEachIndexed
                val rawState = runCatching { node["state"]?.jsonPrimitive?.content }.getOrNull()
                val merged = runCatching { node["merged"]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: false
                out[ref.url] = IssueState.fromApi(rawState, merged).value
            }
        }
        return out
    }
}
