package com.lifehub.pluginapi

/**
 * 页面 schema 校验器。除格式与资源上限外，还检查导航目标是否在 manifest 的页面清单中。
 */
object PluginPageValidator {
    private val inputIdPattern = Regex("^[a-z][a-zA-Z0-9_]{0,63}$")
    private val dataSourcePattern = Regex("^[a-z][a-zA-Z0-9_]{0,63}$")
    private val actionIdPattern = Regex("^[a-z][a-zA-Z0-9_.-]*$")
    private val paramKeyPattern = Regex("^[a-zA-Z][a-zA-Z0-9_]{0,63}$")

    fun validate(
        document: PluginPageDocument,
        expectedPageId: String,
        availablePageIds: Set<String>,
    ): List<PluginIssue> {
        val context = ValidationContext(availablePageIds)
        val issues = context.issues

        if (document.schemaVersion != PluginSchemaLimits.SUPPORTED_SCHEMA_VERSION) {
            issues += PluginIssue(
                PluginIssueCode.UNSUPPORTED_SCHEMA_VERSION,
                "页面 ${document.pageId} 的 schemaVersion ${document.schemaVersion} 不受支持，" +
                    "当前仅支持 ${PluginSchemaLimits.SUPPORTED_SCHEMA_VERSION}",
            )
        }
        if (document.pageId != expectedPageId) {
            issues += PluginIssue(
                PluginIssueCode.PAGE_ID_MISMATCH,
                "页面文件声明的 pageId「${document.pageId}」与 manifest 中的「$expectedPageId」不一致",
            )
        }
        if (document.title.isBlank()) {
            issues += PluginIssue(PluginIssueCode.INVALID_SCHEMA, "页面标题不能为空")
        }
        context.checkText(
            value = document.title,
            field = "页面标题",
            maxLength = PluginSchemaLimits.MAX_PAGE_TITLE_LENGTH,
        )
        if (document.nodes.isEmpty()) {
            issues += PluginIssue(PluginIssueCode.INVALID_SCHEMA, "页面至少需要一个节点")
        }

        context.walk(document.nodes, depth = 1)
        context.validateTopBarActions(document.topBarActions)

        if (context.nodeCount > PluginSchemaLimits.MAX_NODES_PER_PAGE) {
            issues += PluginIssue(
                PluginIssueCode.TOO_MANY_NODES,
                "页面节点数 ${context.nodeCount} 超过上限 ${PluginSchemaLimits.MAX_NODES_PER_PAGE}",
            )
        }
        if (context.maxDepth > PluginSchemaLimits.MAX_NODE_DEPTH) {
            issues += PluginIssue(
                PluginIssueCode.NODE_TOO_DEEP,
                "页面节点嵌套深度 ${context.maxDepth} 超过上限 ${PluginSchemaLimits.MAX_NODE_DEPTH}",
            )
        }
        return issues
    }

    private class ValidationContext(private val availablePageIds: Set<String>) {
        val issues = mutableListOf<PluginIssue>()
        var nodeCount = 0
            private set
        var maxDepth = 0
            private set

        private val inputIds = mutableSetOf<String>()

        fun validateTopBarActions(actions: List<PluginTopBarAction>) {
            if (actions.size > 4) {
                report(PluginIssueCode.INVALID_SCHEMA, "顶栏动作最多 4 个")
            }
            actions.forEach { action ->
                if (action.icon !in PluginTopBarIcons.ALL) {
                    report(
                        PluginIssueCode.INVALID_SCHEMA,
                        "不支持的顶栏图标：${action.icon}",
                    )
                }
                if (action.contentDescription.isBlank()) {
                    report(PluginIssueCode.INVALID_SCHEMA, "顶栏动作描述不能为空")
                }
                checkText(
                    action.contentDescription,
                    "顶栏动作描述",
                    PluginSchemaLimits.MAX_BUTTON_LABEL_LENGTH,
                )
                validateAction(action.action)
            }
        }

        fun walk(nodes: List<PluginNode>, depth: Int) {
            if (depth > PluginSchemaLimits.MAX_NODE_DEPTH) {
                maxDepth = maxOf(maxDepth, depth)
                return
            }
            maxDepth = maxOf(maxDepth, depth)
            nodes.forEach { node ->
                nodeCount += 1
                when (node) {
                    is PluginNode.Title -> {
                        checkText(node.text, "标题文本", PluginSchemaLimits.MAX_TEXT_LENGTH)
                        if (node.level !in 1..3) {
                            report(PluginIssueCode.INVALID_SCHEMA, "标题 level 只能是 1、2 或 3")
                        }
                    }

                    is PluginNode.Text -> checkText(
                        node.text,
                        "文本",
                        PluginSchemaLimits.MAX_TEXT_LENGTH,
                    )

                    is PluginNode.Button -> {
                        if (node.label.isBlank()) {
                            report(PluginIssueCode.INVALID_SCHEMA, "按钮文案不能为空")
                        }
                        checkText(
                            node.label,
                            "按钮文案",
                            PluginSchemaLimits.MAX_BUTTON_LABEL_LENGTH,
                        )
                        validateAction(node.action)
                    }

                    is PluginNode.Section -> {
                        node.title?.let {
                            checkText(it, "分组标题", PluginSchemaLimits.MAX_PAGE_TITLE_LENGTH)
                        }
                        walk(node.children, depth + 1)
                    }

                    is PluginNode.Row -> {
                        if (node.children.isEmpty() || node.children.size > 4) {
                            report(
                                PluginIssueCode.INVALID_SCHEMA,
                                "行内节点数量必须在 1..4 之间",
                            )
                        }
                        walk(node.children, depth + 1)
                    }

                    is PluginNode.ListBlock -> {
                        if (node.items.size > PluginSchemaLimits.MAX_LIST_ITEMS) {
                            report(
                                PluginIssueCode.TOO_MANY_LIST_ITEMS,
                                "列表项 ${node.items.size} 超过上限 ${PluginSchemaLimits.MAX_LIST_ITEMS}",
                            )
                        }
                        node.items.forEach { item ->
                            if (item.title.isBlank()) {
                                report(PluginIssueCode.INVALID_SCHEMA, "列表项标题不能为空")
                            }
                            checkText(
                                item.title,
                                "列表项标题",
                                PluginSchemaLimits.MAX_LIST_ITEM_TEXT_LENGTH,
                            )
                            checkText(
                                item.subtitle,
                                "列表项副标题",
                                PluginSchemaLimits.MAX_LIST_ITEM_TEXT_LENGTH,
                            )
                            checkText(
                                item.trailing,
                                "列表项尾部文本",
                                PluginSchemaLimits.MAX_LIST_ITEM_TEXT_LENGTH,
                            )
                        }
                    }

                    is PluginNode.RecordList -> {
                        if (!dataSourcePattern.matches(node.sourceId)) {
                            report(
                                PluginIssueCode.INVALID_DATA_SOURCE,
                                "数据源 id 不合法：${node.sourceId}",
                            )
                        }
                        if (node.emptyTitle.isBlank()) {
                            report(PluginIssueCode.INVALID_SCHEMA, "记录列表空态标题不能为空")
                        }
                        checkText(node.emptyTitle, "记录列表空态标题", PluginSchemaLimits.MAX_TEXT_LENGTH)
                        checkText(node.emptyMessage, "记录列表空态说明", PluginSchemaLimits.MAX_TEXT_LENGTH)
                        if ((node.emptyActionLabel == null) != (node.emptyAction == null)) {
                            report(
                                PluginIssueCode.INVALID_SCHEMA,
                                "记录列表空态按钮文案与动作必须同时提供",
                            )
                        }
                        node.emptyActionLabel?.let {
                            if (it.isBlank()) {
                                report(PluginIssueCode.INVALID_SCHEMA, "空态按钮文案不能为空")
                            }
                            checkText(it, "空态按钮文案", PluginSchemaLimits.MAX_BUTTON_LABEL_LENGTH)
                        }
                        node.emptyAction?.let { validateAction(it) }
                        if (node.collapsedCount < 0) {
                            report(PluginIssueCode.INVALID_SCHEMA, "记录列表折叠条数不能为负数")
                        }
                    }

                    is PluginNode.Metrics -> {
                        if (!dataSourcePattern.matches(node.sourceId)) {
                            report(
                                PluginIssueCode.INVALID_DATA_SOURCE,
                                "指标数据源 id 不合法：${node.sourceId}",
                            )
                        }
                    }

                    is PluginNode.DateStrip -> {
                        if (!inputIdPattern.matches(node.id)) {
                            report(
                                PluginIssueCode.INVALID_INPUT,
                                "日期选择 id 不合法：${node.id}",
                            )
                        }
                        if (node.days !in 1..60) {
                            report(PluginIssueCode.INVALID_SCHEMA, "日期选择天数必须在 1..60 之间")
                        }
                        if (node.label.isBlank()) {
                            report(PluginIssueCode.INVALID_SCHEMA, "日期选择标签不能为空")
                        }
                        checkText(node.label, "日期选择标签", PluginSchemaLimits.MAX_PAGE_TITLE_LENGTH)
                        node.action?.let { validateAction(it) }
                    }

                    is PluginNode.DateWheel -> {
                        if (!inputIdPattern.matches(node.id)) {
                            report(
                                PluginIssueCode.INVALID_INPUT,
                                "日期滚轮 id 不合法：${node.id}",
                            )
                        }
                        if (!inputIds.add(node.id)) {
                            report(PluginIssueCode.DUPLICATE_INPUT_ID, "输入 id 重复：${node.id}")
                        }
                        if (inputIds.size > PluginSchemaLimits.MAX_INPUTS_PER_PAGE) {
                            report(
                                PluginIssueCode.INVALID_INPUT,
                                "输入数量超过上限 ${PluginSchemaLimits.MAX_INPUTS_PER_PAGE}",
                            )
                        }
                        if (node.label.isBlank()) {
                            report(PluginIssueCode.INVALID_SCHEMA, "日期滚轮标签不能为空")
                        }
                        checkText(node.label, "日期滚轮标签", PluginSchemaLimits.MAX_PAGE_TITLE_LENGTH)
                        checkText(
                            node.emptyLabel,
                            "日期滚轮空值文案",
                            PluginSchemaLimits.MAX_PAGE_TITLE_LENGTH,
                        )
                    }

                    is PluginNode.EmojiPicker -> {
                        if (!inputIdPattern.matches(node.id)) {
                            report(PluginIssueCode.INVALID_INPUT, "图标选择 id 不合法：${node.id}")
                        }
                        if (!inputIds.add(node.id)) {
                            report(PluginIssueCode.DUPLICATE_INPUT_ID, "输入 id 重复：${node.id}")
                        }
                        if (inputIds.size > PluginSchemaLimits.MAX_INPUTS_PER_PAGE) {
                            report(
                                PluginIssueCode.INVALID_INPUT,
                                "输入数量超过上限 ${PluginSchemaLimits.MAX_INPUTS_PER_PAGE}",
                            )
                        }
                        if (node.label.isBlank()) {
                            report(PluginIssueCode.INVALID_SCHEMA, "图标选择标签不能为空")
                        }
                        checkText(node.label, "图标选择标签", PluginSchemaLimits.MAX_PAGE_TITLE_LENGTH)
                        if (node.options.isEmpty() || node.options.size > 40) {
                            report(
                                PluginIssueCode.INVALID_SCHEMA,
                                "图标选项数量必须在 1..40 之间",
                            )
                        }
                        val optionValues = mutableSetOf<String>()
                        node.options.forEach { option ->
                            if (option.isBlank()) {
                                report(PluginIssueCode.INVALID_SCHEMA, "图标选项不能为空")
                            }
                            checkText(option, "图标选项", 16)
                            if (option.isNotBlank() && !optionValues.add(option)) {
                                report(PluginIssueCode.INVALID_SCHEMA, "图标选项重复：$option")
                            }
                        }
                        node.defaultValue?.let { defaultValue ->
                            if (defaultValue !in optionValues) {
                                report(
                                    PluginIssueCode.INVALID_SCHEMA,
                                    "图标默认值不在选项中：$defaultValue",
                                )
                            }
                        }
                        if (node.collapsedCount < 0) {
                            report(PluginIssueCode.INVALID_SCHEMA, "图标折叠个数不能为负数")
                        }
                    }

                    is PluginNode.Chips -> {
                        if (!inputIdPattern.matches(node.id)) {
                            report(PluginIssueCode.INVALID_INPUT, "快捷选择 id 不合法：${node.id}")
                        }
                        if (node.label.isBlank()) {
                            report(PluginIssueCode.INVALID_SCHEMA, "快捷选择标签不能为空")
                        }
                        checkText(node.label, "快捷选择标签", PluginSchemaLimits.MAX_PAGE_TITLE_LENGTH)
                        if (node.options.isEmpty() || node.options.size > 20) {
                            report(
                                PluginIssueCode.INVALID_SCHEMA,
                                "快捷选择选项数量必须在 1..20 之间",
                            )
                        }
                        val optionValues = mutableSetOf<String>()
                        node.options.forEach { option ->
                            if (option.label.isBlank() || option.value.isBlank()) {
                                report(PluginIssueCode.INVALID_SCHEMA, "快捷选择选项不能为空")
                            }
                            checkText(option.label, "快捷选择选项文案", 40)
                            checkText(option.value, "快捷选择选项值", 64)
                            if (option.value.isNotBlank() && !optionValues.add(option.value)) {
                                report(
                                    PluginIssueCode.INVALID_SCHEMA,
                                    "快捷选择选项值重复：${option.value}",
                                )
                            }
                        }
                        node.defaultValue?.let { defaultValue ->
                            if (defaultValue !in optionValues) {
                                report(
                                    PluginIssueCode.INVALID_SCHEMA,
                                    "快捷选择默认值不在选项中：$defaultValue",
                                )
                            }
                        }
                        node.action?.let { validateAction(it) }
                    }

                    is PluginNode.Selector -> {
                        if (!inputIdPattern.matches(node.id)) {
                            report(PluginIssueCode.INVALID_INPUT, "选择器 id 不合法：${node.id}")
                        }
                        if (!inputIds.add(node.id)) {
                            report(PluginIssueCode.DUPLICATE_INPUT_ID, "输入 id 重复：${node.id}")
                        }
                        if (inputIds.size > PluginSchemaLimits.MAX_INPUTS_PER_PAGE) {
                            report(
                                PluginIssueCode.INVALID_INPUT,
                                "输入数量超过上限 ${PluginSchemaLimits.MAX_INPUTS_PER_PAGE}",
                            )
                        }
                        if (node.icon !in PluginSelectorIcons.ALL) {
                            report(
                                PluginIssueCode.INVALID_SCHEMA,
                                "不支持的选择器图标：${node.icon}",
                            )
                        }
                        node.label?.let {
                            checkText(it, "选择器标签", PluginSchemaLimits.MAX_PAGE_TITLE_LENGTH)
                        }
                        if (node.options.isEmpty() || node.options.size > 20) {
                            report(
                                PluginIssueCode.INVALID_SCHEMA,
                                "选择器选项数量必须在 1..20 之间",
                            )
                        }
                        val optionValues = mutableSetOf<String>()
                        node.options.forEach { option ->
                            if (option.label.isBlank() || option.value.isBlank()) {
                                report(PluginIssueCode.INVALID_SCHEMA, "选择器选项不能为空")
                            }
                            checkText(option.label, "选择器选项文案", 40)
                            checkText(option.value, "选择器选项值", 64)
                            if (option.value.isNotBlank() && !optionValues.add(option.value)) {
                                report(
                                    PluginIssueCode.INVALID_SCHEMA,
                                    "选择器选项值重复：${option.value}",
                                )
                            }
                        }
                        node.defaultValue?.let { defaultValue ->
                            if (defaultValue !in optionValues) {
                                report(
                                    PluginIssueCode.INVALID_SCHEMA,
                                    "选择器默认值不在选项中：$defaultValue",
                                )
                            }
                        }
                        node.action?.let { validateAction(it) }
                    }

                    is PluginNode.PeriodNav -> {
                        if (!dataSourcePattern.matches(node.sourceId)) {
                            report(
                                PluginIssueCode.INVALID_DATA_SOURCE,
                                "周期导航数据源 id 不合法：${node.sourceId}",
                            )
                        }
                        if (!inputIdPattern.matches(node.anchorId)) {
                            report(
                                PluginIssueCode.INVALID_INPUT,
                                "周期导航锚点 id 不合法：${node.anchorId}",
                            )
                        }
                        if (node.resetLabel.isBlank()) {
                            report(PluginIssueCode.INVALID_SCHEMA, "周期导航重置文案不能为空")
                        }
                        checkText(node.resetLabel, "周期导航重置文案", 40)
                        validateAction(node.shiftAction)
                    }

                    is PluginNode.BarChart -> {
                        if (!dataSourcePattern.matches(node.sourceId)) {
                            report(
                                PluginIssueCode.INVALID_DATA_SOURCE,
                                "柱状图数据源 id 不合法：${node.sourceId}",
                            )
                        }
                        if (node.title.isBlank()) {
                            report(PluginIssueCode.INVALID_SCHEMA, "柱状图标题不能为空")
                        }
                        checkText(node.title, "柱状图标题", PluginSchemaLimits.MAX_PAGE_TITLE_LENGTH)
                        node.action?.let { validateAction(it) }
                    }

                    is PluginNode.DonutChart -> {
                        if (!dataSourcePattern.matches(node.sourceId)) {
                            report(
                                PluginIssueCode.INVALID_DATA_SOURCE,
                                "环图数据源 id 不合法：${node.sourceId}",
                            )
                        }
                        if (node.title.isBlank()) {
                            report(PluginIssueCode.INVALID_SCHEMA, "环图标题不能为空")
                        }
                        checkText(node.title, "环图标题", PluginSchemaLimits.MAX_PAGE_TITLE_LENGTH)
                    }

                    is PluginNode.GeoMap -> {
                        if (!dataSourcePattern.matches(node.sourceId)) {
                            report(
                                PluginIssueCode.INVALID_DATA_SOURCE,
                                "地图数据源 id 不合法：${node.sourceId}",
                            )
                        }
                        if (node.height !in 240..720) {
                            report(
                                PluginIssueCode.INVALID_SCHEMA,
                                "地图高度必须在 240..720 之间",
                            )
                        }
                        node.action?.let { validateAction(it) }
                    }

                    is PluginNode.Rating -> {
                        if (!inputIdPattern.matches(node.id)) {
                            report(PluginIssueCode.INVALID_INPUT, "评分控件 id 不合法：${node.id}")
                        }
                        if (!inputIds.add(node.id)) {
                            report(PluginIssueCode.DUPLICATE_INPUT_ID, "输入 id 重复：${node.id}")
                        }
                        if (inputIds.size > PluginSchemaLimits.MAX_INPUTS_PER_PAGE) {
                            report(
                                PluginIssueCode.INVALID_INPUT,
                                "输入数量超过上限 ${PluginSchemaLimits.MAX_INPUTS_PER_PAGE}",
                            )
                        }
                        if (node.max !in 3..5) {
                            report(PluginIssueCode.INVALID_SCHEMA, "评分星数必须在 3..5 之间")
                        }
                        checkText(node.label, "评分标签", PluginSchemaLimits.MAX_PAGE_TITLE_LENGTH)
                        node.defaultValue?.let { defaultValue ->
                            val value = defaultValue.toIntOrNull()
                            if (value == null || value !in 0..node.max) {
                                report(
                                    PluginIssueCode.INVALID_INPUT,
                                    "评分默认值必须位于 0..${node.max}",
                                )
                            }
                        }
                    }

                    is PluginNode.Input -> {
                        if (!inputIdPattern.matches(node.id)) {
                            report(PluginIssueCode.INVALID_INPUT, "输入框 id 不合法：${node.id}")
                        }
                        if (!inputIds.add(node.id)) {
                            report(PluginIssueCode.DUPLICATE_INPUT_ID, "输入框 id 重复：${node.id}")
                        }
                        if (inputIds.size > PluginSchemaLimits.MAX_INPUTS_PER_PAGE) {
                            report(
                                PluginIssueCode.INVALID_INPUT,
                                "输入框数量超过上限 ${PluginSchemaLimits.MAX_INPUTS_PER_PAGE}",
                            )
                        }
                        if (node.label.isBlank()) {
                            report(PluginIssueCode.INVALID_INPUT, "输入框 ${node.id} 缺少标签")
                        }
                        checkText(node.label, "输入框标签", PluginSchemaLimits.MAX_PAGE_TITLE_LENGTH)
                        checkText(node.placeholder, "输入框占位符", PluginSchemaLimits.MAX_TEXT_LENGTH)
                        node.action?.let { validateAction(it) }
                    }

                    is PluginNode.Empty -> {
                        if (node.title.isBlank()) {
                            report(PluginIssueCode.INVALID_SCHEMA, "空态标题不能为空")
                        }
                        checkText(node.title, "空态标题", PluginSchemaLimits.MAX_TEXT_LENGTH)
                        checkText(node.message, "空态说明", PluginSchemaLimits.MAX_TEXT_LENGTH)
                    }

                    is PluginNode.Loading -> checkText(
                        node.text,
                        "加载态文本",
                        PluginSchemaLimits.MAX_TEXT_LENGTH,
                    )
                }
            }
        }

        private fun validateAction(action: PluginAction) {
            when (action) {
                is PluginAction.Host -> {
                    if (action.actionId.isBlank() ||
                        action.actionId.length > PluginSchemaLimits.MAX_ACTION_ID_LENGTH ||
                        !actionIdPattern.matches(action.actionId)
                    ) {
                        report(PluginIssueCode.INVALID_ACTION, "actionId 不合法：${action.actionId}")
                    }
                    validateParams(action.params)
                    validateConfirm(action.confirm)
                }

                is PluginAction.Navigate -> {
                    if (action.pageId !in availablePageIds) {
                        report(
                            PluginIssueCode.UNKNOWN_NAVIGATION_TARGET,
                            "导航目标页面不存在：${action.pageId}",
                        )
                    }
                    validateParams(action.params)
                    validateConfirm(action.confirm)
                }
            }
        }

        private fun validateParams(params: Map<String, String>) {
            if (params.size > PluginSchemaLimits.MAX_ACTION_PARAMS) {
                report(
                    PluginIssueCode.INVALID_ACTION,
                    "动作参数 ${params.size} 个，超过上限 ${PluginSchemaLimits.MAX_ACTION_PARAMS}",
                )
            }
            params.forEach { (key, value) ->
                if (!paramKeyPattern.matches(key)) {
                    report(PluginIssueCode.INVALID_ACTION, "动作参数名不合法：$key")
                }
                checkText(value, "动作参数 $key", PluginSchemaLimits.MAX_TEXT_LENGTH)
            }
        }

        private fun validateConfirm(confirm: PluginConfirmDialog?) {
            if (confirm == null) return
            if (confirm.title.isBlank()) {
                report(PluginIssueCode.INVALID_ACTION, "确认对话框标题不能为空")
            }
            checkText(confirm.title, "确认对话框标题", PluginSchemaLimits.MAX_PAGE_TITLE_LENGTH)
            checkText(
                confirm.message,
                "确认对话框内容",
                PluginSchemaLimits.MAX_CONFIRM_MESSAGE_LENGTH,
            )
            checkText(
                confirm.confirmLabel,
                "确认按钮文案",
                PluginSchemaLimits.MAX_BUTTON_LABEL_LENGTH,
            )
            checkText(
                confirm.cancelLabel,
                "取消按钮文案",
                PluginSchemaLimits.MAX_BUTTON_LABEL_LENGTH,
            )
        }

        fun checkText(value: String, field: String, maxLength: Int) {
            if (value.length > maxLength) {
                report(
                    PluginIssueCode.STRING_TOO_LONG,
                    "$field 长度 ${value.length} 超过上限 $maxLength",
                )
            }
        }

        private fun report(code: PluginIssueCode, message: String) {
            issues += PluginIssue(code, message)
        }
    }
}
