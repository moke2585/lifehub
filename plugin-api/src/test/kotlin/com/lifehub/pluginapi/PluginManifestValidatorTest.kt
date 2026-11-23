package com.lifehub.pluginapi

import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManifestValidatorTest {

    private val host = HostInfo(versionCode = 1, versionName = "0.1.0")

    @Test
    fun acceptsCompatibleManifest() {
        val issues = PluginManifestValidator.validate(parseManifest(), host)

        assertTrue(issues.toString(), issues.isEmpty())
    }

    @Test
    fun rejectsHostVersionBelowMinimum() {
        val manifest = parseManifest(manifestJson(minHostVersion = 2))

        val issues = PluginManifestValidator.validate(manifest, host)

        assertTrue(issues.any { it.code == PluginIssueCode.HOST_VERSION_TOO_LOW })
    }

    @Test
    fun rejectsHostVersionAboveMaximum() {
        val manifest = parseManifest(manifestJson(maxHostVersion = 0))

        val issues = PluginManifestValidator.validate(manifest, host)

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_HOST_RANGE })
    }

    @Test
    fun rejectsUnsupportedPluginSdk() {
        val manifest = parseManifest(manifestJson(pluginSdkVersion = 2))

        val issues = PluginManifestValidator.validate(manifest, host)

        assertTrue(issues.any { it.code == PluginIssueCode.UNSUPPORTED_PLUGIN_SDK })
    }

    @Test
    fun rejectsUnknownCapability() {
        val manifest = parseManifest(
            manifestJson(
                capabilities = """[{"id":"network.public","reason":"访问网络"}]""",
            ),
        )

        val issues = PluginManifestValidator.validate(manifest, host)

        assertTrue(issues.any { it.code == PluginIssueCode.UNKNOWN_CAPABILITY })
    }

    @Test
    fun rejectsCapabilityWithoutReason() {
        val manifest = parseManifest(
            manifestJson(capabilities = """[{"id":"storage.private"}]"""),
        )

        val issues = PluginManifestValidator.validate(manifest, host)

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_CAPABILITY })
    }

    @Test
    fun rejectsUnknownEntryPage() {
        val manifest = parseManifest(manifestJson(entryPage = "missing"))

        val issues = PluginManifestValidator.validate(manifest, host)

        assertTrue(issues.any { it.code == PluginIssueCode.UNKNOWN_ENTRY_PAGE })
    }

    @Test
    fun rejectsDuplicatePageIds() {
        val manifest = parseManifest(
            manifestJson(
                pages = """
                    [
                      {"id":"home","title":"首页","file":"pages/home.json"},
                      {"id":"home","title":"重复","file":"pages/other.json"}
                    ]
                """.trimIndent(),
            ),
        )

        val issues = PluginManifestValidator.validate(manifest, host)

        assertTrue(issues.any { it.code == PluginIssueCode.DUPLICATE_PAGE_ID })
    }

    @Test
    fun rejectsUnsafePagePath() {
        val manifest = parseManifest(
            manifestJson(
                pages = """[{"id":"home","title":"首页","file":"../secret.json"}]""",
            ),
        )

        val issues = PluginManifestValidator.validate(manifest, host)

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_PAGE })
    }

    @Test
    fun rejectsInvalidPluginId() {
        val manifest = parseManifest(manifestJson(pluginId = "Hello Plugin"))

        val issues = PluginManifestValidator.validate(manifest, host)

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_PLUGIN_ID })
    }

    @Test
    fun rejectsInvalidIcon() {
        val manifest = parseManifest().copy(icon = "Bad Icon")

        val issues = PluginManifestValidator.validate(manifest, host)

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_ICON })
    }

    @Test
    fun acceptsValidHomeWidgets() {
        val manifest = parseManifest(
            manifestJson(
                homeWidgets = """
                    [
                      {
                        "id":"today",
                        "type":"summary",
                        "title":"今日任务",
                        "size":"small",
                        "priority":100,
                        "sourceId":"memo_summary",
                        "action":{"type":"navigate","pageId":"home"}
                      }
                    ]
                """.trimIndent(),
            ),
        )

        val issues = PluginManifestValidator.validate(manifest, host)

        assertTrue(issues.toString(), issues.isEmpty())
    }

    @Test
    fun rejectsDuplicateHomeWidgetIds() {
        val manifest = parseManifest(
            manifestJson(
                homeWidgets = """
                    [
                      {"id":"today","type":"summary","title":"今日","sourceId":"memo_summary"},
                      {"id":"today","type":"summary","title":"重复","sourceId":"memo_summary"}
                    ]
                """.trimIndent(),
            ),
        )

        val issues = PluginManifestValidator.validate(manifest, host)

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_PAGE })
    }

    @Test
    fun rejectsDataWidgetWithoutSourceId() {
        val manifest = parseManifest(
            manifestJson(
                homeWidgets = """[{"id":"today","type":"summary","title":"今日任务"}]""",
            ),
        )

        val issues = PluginManifestValidator.validate(manifest, host)

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_DATA_SOURCE })
    }

    @Test
    fun rejectsHostActionInHomeWidget() {
        val manifest = parseManifest(
            manifestJson(
                homeWidgets = """
                    [
                      {
                        "id":"today",
                        "type":"summary",
                        "title":"今日任务",
                        "sourceId":"memo_summary",
                        "action":{"type":"host","actionId":"com.lifehub.plugin.hello.greet"}
                      }
                    ]
                """.trimIndent(),
            ),
        )

        val issues = PluginManifestValidator.validate(manifest, host)

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_ACTION })
    }

    @Test
    fun acceptsUnknownHomeWidgetTypeForForwardCompatibility() {
        val manifest = parseManifest(
            manifestJson(
                homeWidgets = """[{"id":"future","type":"heatmap","title":"未来组件"}]""",
            ),
        )

        val issues = PluginManifestValidator.validate(manifest, host)

        assertTrue(issues.toString(), issues.isEmpty())
    }

    @Test
    fun acceptsSearchSource() {
        val manifest = parseManifest(
            manifestJson(search = """{"sourceId":"memo_search"}"""),
        )

        val issues = PluginManifestValidator.validate(manifest, host)

        assertTrue(issues.toString(), issues.isEmpty())
    }

    @Test
    fun rejectsBlankSearchSource() {
        val manifest = parseManifest(
            manifestJson(search = """{"sourceId":""}"""),
        )

        val issues = PluginManifestValidator.validate(manifest, host)

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_DATA_SOURCE })
    }

    @Test
    fun rejectsTooManyHomeWidgets() {
        val widgets = (1..7).joinToString(prefix = "[", postfix = "]") { index ->
            """{"id":"w$index","type":"summary","title":"组件$index","sourceId":"memo_summary"}"""
        }
        val manifest = parseManifest(manifestJson(homeWidgets = widgets))

        val issues = PluginManifestValidator.validate(manifest, host)

        assertTrue(issues.any { it.code == PluginIssueCode.INVALID_PAGE })
    }
}
