package com.lifehub.pluginapi

import kotlinx.serialization.json.Json

/**
 * APK 插件 provider 契约。
 *
 * 插件 APK 暴露一个 `ContentProvider`，在 manifest 中声明
 * [ACTION_DISCOVER] 的 intent-filter；宿主通过
 * `PackageManager.queryIntentContentProviders` 发现后，用
 * `ContentResolver.call` 调用下列方法。所有请求/响应都是 JSON 字符串。
 */
object PluginApkProtocol {
    const val VERSION: Int = 1

    /** 插件 provider 用于被宿主发现的 intent action。 */
    const val ACTION_DISCOVER: String = "com.lifehub.plugin.apk.PROVIDER"

    const val METHOD_VERSION: String = "version"
    const val METHOD_MANIFEST: String = "manifest"
    const val METHOD_PAGE: String = "page"
    const val METHOD_ACTION: String = "action"
    const val METHOD_DATA: String = "data"
    const val METHOD_EXPORT_BACKUP: String = "exportBackup"
    const val METHOD_IMPORT_BACKUP: String = "importBackup"

    /** Bundle 中的 JSON 字段名。 */
    const val KEY_JSON: String = "json"
    const val KEY_OK: String = "ok"
    const val KEY_ERROR: String = "error"
    const val KEY_VERSION: String = "version"

    /**
     * 单次 Binder 调用的 payload 建议上限。Binder 事务通常约 1 MB，
     * 留出 Bundle 与字符串编码开销；超过时插件应返回明确错误。
     */
    const val MAX_PAYLOAD_BYTES: Int = 512 * 1024

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun isCompatible(version: Int): Boolean = version == VERSION
}
