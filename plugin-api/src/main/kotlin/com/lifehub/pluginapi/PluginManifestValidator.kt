package com.lifehub.pluginapi

/**
 * 宿主版本信息。versionCode 用于 manifest 的 min/maxHostVersion 判断。
 */
data class HostInfo(
    val versionCode: Int,
    val versionName: String,
    val pluginApiMajor: Int = PluginApiVersion.MAJOR,
)

/**
 * 校验 manifest 的格式、兼容性与 capability 声明。
 * 页面文件是否真实存在由注册表结合插件包源检查。
 */
object PluginManifestValidator {
    private val pluginIdPattern = Regex("^[a-z][a-z0-9]*(\\.[a-z0-9][a-z0-9_-]*)+$")
    private val versionPattern = Regex("^\\d+(\\.\\d+){0,3}([-+][0-9A-Za-z.-]+)?$")
    private val iconPattern = Regex("^[a-z][a-z0-9_]{0,31}$")

    fun validate(manifest: PluginManifest, host: HostInfo): List<PluginIssue> {
        val issues = mutableListOf<PluginIssue>()

        fun report(code: PluginIssueCode, message: String) {
            issues += PluginIssue(code, message)
        }

        if (!pluginIdPattern.matches(manifest.pluginId)) {
            report(PluginIssueCode.INVALID_PLUGIN_ID, "pluginId 必须为小写反向域名格式，例如 com.example.plugin")
        }
        if (manifest.name.isBlank()) {
            report(PluginIssueCode.INVALID_NAME, "插件名称不能为空")
        }
        if (!iconPattern.matches(manifest.icon)) {
            report(PluginIssueCode.INVALID_ICON, "icon 必须是小写字母开头的标识，例如 ledger")
        }
        if (manifest.publisher.isBlank()) {
            report(PluginIssueCode.INVALID_PUBLISHER, "发布者不能为空")
        }
        if (!versionPattern.matches(manifest.version)) {
            report(PluginIssueCode.INVALID_VERSION, "version 必须为数字版本号，例如 1.0.0")
        }
        if (manifest.minHostVersion <= 0) {
            report(PluginIssueCode.INVALID_HOST_RANGE, "minHostVersion 必须大于 0")
        }
        if (manifest.maxHostVersion != null && manifest.maxHostVersion < manifest.minHostVersion) {
            report(PluginIssueCode.INVALID_HOST_RANGE, "maxHostVersion 不能小于 minHostVersion")
        }
        if (manifest.minHostVersion > 0 && host.versionCode < manifest.minHostVersion) {
            report(
                PluginIssueCode.HOST_VERSION_TOO_LOW,
                "宿主版本 ${host.versionCode} 低于插件要求的 ${manifest.minHostVersion}",
            )
        }
        if (manifest.maxHostVersion != null &&
            manifest.maxHostVersion >= manifest.minHostVersion &&
            host.versionCode > manifest.maxHostVersion
        ) {
            report(
                PluginIssueCode.HOST_VERSION_TOO_HIGH,
                "宿主版本 ${host.versionCode} 高于插件支持的上限 ${manifest.maxHostVersion}",
            )
        }
        if (!PluginApiVersion.isCompatible(manifest.pluginSdkVersion)) {
            report(
                PluginIssueCode.UNSUPPORTED_PLUGIN_SDK,
                "插件 SDK ${manifest.pluginSdkVersion} 与宿主 SDK ${PluginApiVersion.MAJOR} 不兼容",
            )
        }
        if (manifest.dataSchemaVersion < 1) {
            report(PluginIssueCode.INVALID_DATA_SCHEMA, "dataSchemaVersion 必须大于 0")
        }

        if (manifest.pages.isEmpty()) {
            report(PluginIssueCode.UNKNOWN_ENTRY_PAGE, "插件未声明任何页面")
        }
        if (manifest.pages.size > PluginSchemaLimits.MAX_PAGES_PER_PLUGIN) {
            report(
                PluginIssueCode.INVALID_PAGE,
                "页面数量 ${manifest.pages.size} 超过上限 ${PluginSchemaLimits.MAX_PAGES_PER_PLUGIN}",
            )
        }

        val pageIds = mutableSetOf<String>()
        val pageFiles = mutableSetOf<String>()
        manifest.pages.forEach { page ->
            if (page.id.isBlank() || page.title.isBlank() || page.file.isBlank()) {
                report(PluginIssueCode.INVALID_PAGE, "页面 id、标题与文件路径不能为空")
                return@forEach
            }
            if (!pageIds.add(page.id)) {
                report(PluginIssueCode.DUPLICATE_PAGE_ID, "页面 id 重复：${page.id}")
            }
            if (!PluginPathRules.isSafeRelativePath(page.file)) {
                report(PluginIssueCode.INVALID_PAGE, "页面文件路径不合法：${page.file}")
            } else if (!pageFiles.add(page.file)) {
                report(PluginIssueCode.DUPLICATE_PAGE_FILE, "页面文件重复：${page.file}")
            }
        }

        if (manifest.entryPage.isBlank()) {
            report(PluginIssueCode.UNKNOWN_ENTRY_PAGE, "entryPage 不能为空")
        } else if (manifest.pages.none { it.id == manifest.entryPage }) {
            report(
                PluginIssueCode.UNKNOWN_ENTRY_PAGE,
                "entryPage「${manifest.entryPage}」未在 pages 中声明",
            )
        }

        manifest.quickCreate?.let { quickCreate ->
            if (quickCreate.label.isBlank()) {
                report(PluginIssueCode.INVALID_PAGE, "quickCreate.label 不能为空")
            } else if (quickCreate.label.length > 16) {
                report(PluginIssueCode.INVALID_PAGE, "quickCreate.label 最多 16 个字符")
            }
            if (quickCreate.pageId.isBlank() || manifest.pages.none { it.id == quickCreate.pageId }) {
                report(
                    PluginIssueCode.INVALID_PAGE,
                    "quickCreate.pageId「${quickCreate.pageId}」未在 pages 中声明",
                )
            }
        }

        manifest.search?.let { search ->
            if (search.sourceId.isBlank() || search.sourceId.length > 64) {
                report(
                    PluginIssueCode.INVALID_DATA_SOURCE,
                    "search.sourceId 不能为空且最多 64 个字符",
                )
            }
        }

        if (manifest.homeWidgets.size > PluginSchemaLimits.MAX_HOME_WIDGETS_PER_PLUGIN) {
            report(
                PluginIssueCode.INVALID_PAGE,
                "主页小组件数量 ${manifest.homeWidgets.size} 超过上限 " +
                    PluginSchemaLimits.MAX_HOME_WIDGETS_PER_PLUGIN,
            )
        }
        val homeWidgetIds = mutableSetOf<String>()
        manifest.homeWidgets.forEach { widget ->
            if (widget.id.isBlank() || widget.id.length > PluginSchemaLimits.MAX_HOME_WIDGET_ID_LENGTH) {
                report(
                    PluginIssueCode.INVALID_PAGE,
                    "主页小组件 id 不能为空且最多 ${PluginSchemaLimits.MAX_HOME_WIDGET_ID_LENGTH} 个字符",
                )
            } else if (!homeWidgetIds.add(widget.id)) {
                report(PluginIssueCode.INVALID_PAGE, "主页小组件 id 重复：${widget.id}")
            }
            if (widget.title.isBlank() ||
                widget.title.length > PluginSchemaLimits.MAX_HOME_WIDGET_TITLE_LENGTH
            ) {
                report(
                    PluginIssueCode.INVALID_PAGE,
                    "主页小组件标题不能为空且最多 ${PluginSchemaLimits.MAX_HOME_WIDGET_TITLE_LENGTH} 个字符",
                )
            }
            if (widget.priority !in 0..100) {
                report(PluginIssueCode.INVALID_PAGE, "主页小组件 priority 必须在 0..100")
            }
            if (widget.type.isBlank()) {
                report(PluginIssueCode.INVALID_PAGE, "主页小组件 type 不能为空")
            }
            if (widget.type in PluginHomeWidgetTypes.dataTypes && widget.sourceId.isNullOrBlank()) {
                report(
                    PluginIssueCode.INVALID_DATA_SOURCE,
                    "主页小组件「${widget.title}」缺少 sourceId",
                )
            }
            if (widget.type == PluginHomeWidgetTypes.LIST &&
                widget.maxItems !in 1..PluginSchemaLimits.MAX_HOME_WIDGET_ITEMS
            ) {
                report(
                    PluginIssueCode.INVALID_PAGE,
                    "主页小组件 maxItems 必须在 1..${PluginSchemaLimits.MAX_HOME_WIDGET_ITEMS}",
                )
            }
            widget.action?.let { action ->
                when (action) {
                    is PluginAction.Navigate -> {
                        if (action.pageId !in pageIds) {
                            report(
                                PluginIssueCode.UNKNOWN_NAVIGATION_TARGET,
                                "主页小组件导航目标页面不存在：${action.pageId}",
                            )
                        }
                        if (action.params.size > PluginSchemaLimits.MAX_ACTION_PARAMS) {
                            report(PluginIssueCode.INVALID_ACTION, "主页小组件动作参数过多")
                        }
                    }

                    is PluginAction.Host -> report(
                        PluginIssueCode.INVALID_ACTION,
                        "主页小组件只允许 navigate 动作",
                    )
                }
            }
        }

        val capabilityIds = mutableSetOf<String>()
        manifest.capabilities.forEach { capability ->
            if (capability.id.isBlank()) {
                report(PluginIssueCode.INVALID_CAPABILITY, "能力 id 不能为空")
                return@forEach
            }
            if (!capabilityIds.add(capability.id)) {
                report(PluginIssueCode.INVALID_CAPABILITY, "能力重复：${capability.id}")
            }
            if (capability.id !in PluginCapabilities.supported) {
                report(PluginIssueCode.UNKNOWN_CAPABILITY, "未知能力：${capability.id}")
            }
            if (capability.reason.isBlank()) {
                report(PluginIssueCode.INVALID_CAPABILITY, "能力 ${capability.id} 缺少用途说明")
            }
        }

        return issues
    }
}
