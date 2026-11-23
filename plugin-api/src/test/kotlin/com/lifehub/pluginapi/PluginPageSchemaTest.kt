package com.lifehub.pluginapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginPageSchemaTest {

    @Test
    fun parsesAllV1NodeTypes() {
        val json = pageJson(
            nodes = """
                [
                  {"type":"title","text":"标题","level":1},
                  {"type":"text","text":"正文"},
                  {"type":"section","title":"分组","children":[
                    {"type":"input","id":"name","label":"名字","kind":"text"},
                    {"type":"input","id":"amount","label":"金额","kind":"money"},
                    {"type":"input","id":"date","label":"日期","kind":"date"}
                  ]},
                  {"type":"button","label":"提交","action":{"type":"host","actionId":"com.example.submit","confirm":{"title":"确认","message":"确定提交？"}}},
                  {"type":"button","label":"跳转","emphasis":"secondary","action":{"type":"navigate","pageId":"about","params":{"from":"home"}}},
                  {"type":"list","items":[{"title":"条目","subtitle":"说明","trailing":"1"}]},
                  {"type":"recordList","sourceId":"records","emptyTitle":"暂无记录"},
                  {"type":"metrics","sourceId":"records"},
                  {"type":"dateStrip","id":"filterDate","days":14,"action":{"type":"host","actionId":"com.example.filter"}},
                  {"type":"dateWheel","id":"deadline","label":"截止日期"},
                  {"type":"emojiPicker","id":"icon","label":"图标","options":["📝","🎯"],"defaultValue":"📝"},
                  {"type":"chips","id":"direction","label":"方向","options":[{"label":"支出","value":"支出"},{"label":"收入","value":"收入"}],"defaultValue":"支出"},
                  {"type":"rating","id":"rating","label":"地区评分","max":5,"defaultValue":"0"},
                  {"type":"periodNav","sourceId":"records","shiftAction":{"type":"host","actionId":"com.example.shift"}},
                  {"type":"barChart","sourceId":"records","title":"趋势"},
                  {"type":"donutChart","sourceId":"records","title":"分类"},
                  {"type":"geoMap","sourceId":"records","height":480,"action":{"type":"navigate","pageId":"about"}},
                  {"type":"empty","title":"空","message":"没有数据"},
                  {"type":"loading","text":"加载中"}
                ]
            """.trimIndent(),
        )

        val document = PluginPageParser.parse(json).getOrThrow()
        val issues = PluginPageValidator.validate(
            document = document,
            expectedPageId = "home",
            availablePageIds = setOf("home", "about"),
        )

        assertEquals(19, document.nodes.size)
        assertTrue(issues.toString(), issues.isEmpty())
    }

    @Test
    fun acceptsValidTopBarActions() {
        val issues = validate(
            pageJson(
                topBarActions = """
                    [{"icon":"add","contentDescription":"新建","action":{"type":"navigate","pageId":"about"}}]
                """.trimIndent(),
            ),
        )

        assertTrue(issues.toString(), issues.isEmpty())
    }

    @Test
    fun rejectsUnknownTopBarIcon() {
        val issues = validate(
            pageJson(
                topBarActions = """
                    [{"icon":"share","contentDescription":"分享","action":{"type":"navigate","pageId":"about"}}]
                """.trimIndent(),
            ),
        )

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_SCHEMA })
    }

    @Test
    fun rejectsInvalidDataSourceId() {
        val issues = validate(
            pageJson(nodes = """[{"type":"recordList","sourceId":"Bad Source"}]"""),
        )

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_DATA_SOURCE })
    }

    @Test
    fun rejectsInvalidDateStripRange() {
        val issues = validate(
            pageJson(nodes = """[{"type":"dateStrip","id":"filterDate","days":0}]"""),
        )

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_SCHEMA })
    }

    @Test
    fun rejectsRatingStarCountOutsideRange() {
        val issues = validate(
            pageJson(nodes = """[{"type":"rating","id":"rating","max":2}]"""),
        )

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_SCHEMA })
    }

    @Test
    fun rejectsRatingDefaultOutsideRange() {
        val issues = validate(
            pageJson(nodes = """[{"type":"rating","id":"rating","max":5,"defaultValue":"6"}]"""),
        )

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_INPUT })
    }

    @Test
    fun rejectsGeoMapHeightOutsideRange() {
        val issues = validate(
            pageJson(nodes = """[{"type":"geoMap","sourceId":"map","height":100}]"""),
        )

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_SCHEMA })
    }

    @Test
    fun rejectsInvalidGeoMapDataSource() {
        val issues = validate(
            pageJson(nodes = """[{"type":"geoMap","sourceId":"Bad Map","height":480}]"""),
        )

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_DATA_SOURCE })
    }

    @Test
    fun rejectsChipsDefaultOutsideOptions() {
        val issues = validate(
            pageJson(
                nodes = """
                    [{"type":"chips","id":"direction","label":"方向",
                      "options":[{"label":"支出","value":"支出"}],
                      "defaultValue":"收入"}]
                """.trimIndent(),
            ),
        )

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_SCHEMA })
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        val issues = validate(pageJson(schemaVersion = 2))

        assertTrue(issues.any { it.code == PluginIssueCode.UNSUPPORTED_SCHEMA_VERSION })
    }

    @Test
    fun rejectsPageIdMismatch() {
        val issues = validate(pageJson(pageId = "other"), expectedPageId = "home")

        assertTrue(issues.any { it.code == PluginIssueCode.PAGE_ID_MISMATCH })
    }

    @Test
    fun rejectsUnknownNodeType() {
        val result = PluginPageParser.parse(
            pageJson(nodes = """[{"type":"webview","url":"https://example.com"}]"""),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsTooManyNodes() {
        val nodes = List(PluginSchemaLimits.MAX_NODES_PER_PAGE + 1) {
            """{"type":"text","text":"x"}"""
        }.joinToString(",")

        val issues = validate(pageJson(nodes = "[$nodes]"))

        assertTrue(issues.any { it.code == PluginIssueCode.TOO_MANY_NODES })
    }

    @Test
    fun rejectsTooDeepNesting() {
        var node = """{"type":"text","text":"x"}"""
        repeat(PluginSchemaLimits.MAX_NODE_DEPTH + 1) {
            node = """{"type":"section","children":[$node]}"""
        }

        val issues = validate(pageJson(nodes = "[$node]"))

        assertTrue(issues.any { it.code == PluginIssueCode.NODE_TOO_DEEP })
    }

    @Test
    fun rejectsOverlongText() {
        val text = "a".repeat(PluginSchemaLimits.MAX_TEXT_LENGTH + 1)

        val issues = validate(pageJson(nodes = """[{"type":"text","text":"$text"}]"""))

        assertTrue(issues.any { it.code == PluginIssueCode.STRING_TOO_LONG })
    }

    @Test
    fun rejectsDuplicateInputIds() {
        val issues = validate(
            pageJson(
                nodes = """
                    [
                      {"type":"input","id":"name","label":"名字"},
                      {"type":"input","id":"name","label":"重复"}
                    ]
                """.trimIndent(),
            ),
        )

        assertTrue(issues.any { it.code == PluginIssueCode.DUPLICATE_INPUT_ID })
    }

    @Test
    fun rejectsInvalidInputId() {
        val issues = validate(
            pageJson(nodes = """[{"type":"input","id":"Bad Id","label":"名字"}]"""),
        )

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_INPUT })
    }

    @Test
    fun rejectsUnknownNavigationTarget() {
        val issues = validate(
            pageJson(
                nodes = """
                    [{"type":"button","label":"跳转","action":{"type":"navigate","pageId":"missing"}}]
                """.trimIndent(),
            ),
        )

        assertTrue(issues.any { it.code == PluginIssueCode.UNKNOWN_NAVIGATION_TARGET })
    }

    @Test
    fun rejectsInvalidActionId() {
        val issues = validate(
            pageJson(
                nodes = """
                    [{"type":"button","label":"提交","action":{"type":"host","actionId":"Bad Action"}}]
                """.trimIndent(),
            ),
        )

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_ACTION })
    }

    @Test
    fun rejectsTooManyListItems() {
        val items = List(PluginSchemaLimits.MAX_LIST_ITEMS + 1) {
            """{"title":"条目"}"""
        }.joinToString(",")

        val issues = validate(pageJson(nodes = """[{"type":"list","items":[$items]}]"""))

        assertTrue(issues.any { it.code == PluginIssueCode.TOO_MANY_LIST_ITEMS })
    }

    private fun validate(
        json: String,
        expectedPageId: String = "home",
    ): List<PluginIssue> {
        val document = PluginPageParser.parse(json).getOrThrow()
        return PluginPageValidator.validate(
            document = document,
            expectedPageId = expectedPageId,
            availablePageIds = setOf("home", "about"),
        )
    }

    private fun pageJson(
        schemaVersion: Int = 1,
        pageId: String = "home",
        title: String = "首页",
        nodes: String = """[{"type":"text","text":"内容"}]""",
        topBarActions: String = "[]",
    ): String = """
        {
          "schemaVersion": $schemaVersion,
          "pageId": "$pageId",
          "title": "$title",
          "topBarActions": $topBarActions,
          "nodes": $nodes
        }
    """.trimIndent()
}
