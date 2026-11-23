package com.lifehub.app.data

import com.lifehub.pluginapi.PluginStorageLimits

internal object PluginRecordRules {
    private val collectionPattern = Regex(
        "^[a-z][a-zA-Z0-9_]{0,${PluginStorageLimits.MAX_COLLECTION_NAME_LENGTH - 1}}$",
    )
    private val idPattern = Regex(
        "^[a-zA-Z0-9_-]{1,${PluginStorageLimits.MAX_RECORD_ID_LENGTH}}$",
    )

    fun requireCollection(collection: String) {
        require(collectionPattern.matches(collection)) {
            "集合名不合法：$collection（小写字母开头，仅允许字母、数字、下划线）"
        }
    }

    fun requireId(id: String) {
        require(idPattern.matches(id)) {
            "记录 ID 不合法：$id（仅允许字母、数字、下划线和连字符）"
        }
    }

    fun requirePayload(payload: String) {
        val bytes = payload.toByteArray(Charsets.UTF_8).size
        require(bytes <= PluginStorageLimits.MAX_RECORD_BYTES) {
            "记录内容 $bytes 字节超过上限 ${PluginStorageLimits.MAX_RECORD_BYTES} 字节"
        }
    }

    fun requirePage(limit: Int, offset: Int) {
        require(offset >= 0) { "offset 不能为负数" }
        require(limit in 1..PluginStorageLimits.MAX_PAGE_SIZE) {
            "limit 必须在 1..${PluginStorageLimits.MAX_PAGE_SIZE} 之间"
        }
    }
}
