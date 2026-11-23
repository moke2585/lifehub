package com.lifehub.pluginapi

/**
 * 插件在宿主中的状态。
 *
 * [INSTALLED] 表示已发现但用户从未启用或禁用；[DISABLED] 表示用户显式禁用过。
 */
enum class PluginStatus {
    INSTALLED,
    ENABLED,
    DISABLED,
    UNINSTALLED,
    INCOMPATIBLE,
    ERROR,
    /** 已知的外部 APK 插件当前未安装。 */
    NOT_INSTALLED,
}

enum class PluginSource {
    BUILT_IN,
    EXTERNAL_ZIP,
    EXTERNAL_APK,
}

enum class PluginIssueCode {
    MANIFEST_UNREADABLE,
    PLUGIN_ID_MISMATCH,
    INVALID_PLUGIN_ID,
    INVALID_NAME,
    INVALID_ICON,
    INVALID_PUBLISHER,
    INVALID_VERSION,
    INVALID_HOST_RANGE,
    UNSUPPORTED_PLUGIN_SDK,
    HOST_VERSION_TOO_LOW,
    HOST_VERSION_TOO_HIGH,
    INVALID_DATA_SCHEMA,
    UNKNOWN_CAPABILITY,
    INVALID_CAPABILITY,
    INVALID_PAGE,
    DUPLICATE_PAGE_ID,
    DUPLICATE_PAGE_FILE,
    UNKNOWN_ENTRY_PAGE,
    MISSING_PAGE_FILE,
    UNSUPPORTED_SCHEMA_VERSION,
    PAGE_ID_MISMATCH,
    INVALID_SCHEMA,
    TOO_MANY_NODES,
    NODE_TOO_DEEP,
    STRING_TOO_LONG,
    TOO_MANY_LIST_ITEMS,
    INVALID_INPUT,
    DUPLICATE_INPUT_ID,
    INVALID_DATA_SOURCE,
    INVALID_ACTION,
    UNKNOWN_NAVIGATION_TARGET,
}

data class PluginIssue(
    val code: PluginIssueCode,
    val message: String,
)

/**
 * 注册表对插件清单、兼容性与页面文件的综合结果。
 * 无法解析清单时 [manifest] 为空，但仍保留一条可见的错误记录。
 */
data class PluginDescriptor(
    val pluginId: String,
    val displayName: String,
    val manifest: PluginManifest?,
    val status: PluginStatus,
    val source: PluginSource = PluginSource.BUILT_IN,
    val issues: List<PluginIssue> = emptyList(),
    /** 已通过 schema 校验的页面，按 manifest 中的 page id 索引。 */
    val pages: Map<String, PluginPageDocument> = emptyMap(),
) {
    val isOpenable: Boolean
        get() = status == PluginStatus.ENABLED && manifest != null

    val canToggle: Boolean
        get() = status == PluginStatus.INSTALLED ||
            status == PluginStatus.ENABLED ||
            status == PluginStatus.DISABLED
}
