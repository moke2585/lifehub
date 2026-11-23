package com.lifehub.app.core

import android.util.Log

/**
 * 宿主统一日志入口。
 *
 * 只记录事件名、插件 ID、错误码等非敏感元数据；禁止记录插件数据正文、用户输入、
 * 金额、任务内容或备份口令。生产构建关闭调试日志，错误日志只保留错误码与堆栈。
 */
object AppLog {
    private const val TAG = "LifeHub"

    enum class Category(val label: String) {
        STARTUP("startup"),
        PLUGIN("plugin"),
        DATA("data"),
        UI("ui"),
        ERROR("error"),
    }

    fun event(category: Category, event: String, pluginId: String? = null) {
        val suffix = pluginId?.let { " pluginId=$it" }.orEmpty()
        Log.i(TAG, "[${category.label}] $event$suffix")
    }

    fun error(
        category: Category,
        event: String,
        errorCode: String? = null,
        throwable: Throwable? = null,
    ) {
        val suffix = errorCode?.let { " code=$it" }.orEmpty()
        Log.e(TAG, "[${category.label}] $event$suffix", throwable)
    }
}
