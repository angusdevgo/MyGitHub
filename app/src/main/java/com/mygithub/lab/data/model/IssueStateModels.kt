package com.mygithub.lab.data.model

data class IssueRef(
    val url: String,          // 通知原始 subject.url
    val owner: String,
    val repo: String,
    val number: Int,
    val isPr: Boolean
)

enum class IssueState(val value: String) {
    OPEN("open"),
    CLOSED("closed"),
    MERGED("merged");

    companion object {
        fun fromApi(str: String?, isMerged: Boolean = false): IssueState {
            if (isMerged) return MERGED
            return when (str?.uppercase()) {
                "OPEN" -> OPEN
                "CLOSED" -> CLOSED
                "MERGED" -> MERGED
                else -> OPEN
            }
        }
    }
}

data class OwnedRepoSummary(
    val totalStars: Int = 0,
    val totalForks: Int = 0,
    val totalOpenIssues: Int = 0,
    val repoCount: Int = 0,
    val starsDelta: Int = 0,
    val forksDelta: Int = 0
)
