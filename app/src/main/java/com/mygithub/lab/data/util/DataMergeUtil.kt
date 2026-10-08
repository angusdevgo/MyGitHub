package com.mygithub.lab.data.util

import com.mygithub.lab.data.api.GitHubEvent
import com.mygithub.lab.data.api.GitHubNotification

/**
 * 纯数据合并 / 筛选工具（便于单元测试）
 */
object DataMergeUtil {

    /**
     * 应用本地已读覆盖：远端说 unread=true，但本地已标记过已读 → 强制 unread=false
     */
    fun applyReadOverrides(
        notifications: List<GitHubNotification>,
        overrideIds: Set<String>
    ): List<GitHubNotification> {
        if (overrideIds.isEmpty()) return notifications
        return notifications.map { n ->
            if (n.unread && overrideIds.contains(n.id)) n.copy(unread = false) else n
        }
    }

    /**
     * 事件流筛选：仅保留白名单类型 → 按 id 去重 → 按 created_at 降序
     */
    fun curateEvents(
        events: List<GitHubEvent>,
        allowedTypes: Set<String>
    ): List<GitHubEvent> {
        return events
            .filter { allowedTypes.contains(it.type) }
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .sortedByDescending { it.created_at }
    }
}
