package com.lifehub.pluginapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManifestParserTest {

    @Test
    fun parsesValidManifest() {
        val manifest = parseManifest()

        assertEquals("com.lifehub.plugin.hello", manifest.pluginId)
        assertEquals("示例插件", manifest.name)
        assertEquals(1, manifest.pages.size)
        assertEquals("pages/home.json", manifest.entryPageFile())
    }

    @Test
    fun ignoresUnknownFields() {
        val json = manifestJson().replaceFirst("{", """{"futureField":true,""")
        val manifest = PluginManifestParser.parse(json).getOrThrow()

        assertEquals("com.lifehub.plugin.hello", manifest.pluginId)
    }

    @Test
    fun rejectsMalformedJson() {
        val result = PluginManifestParser.parse("""{"pluginId":"com.example","name":""")

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsMissingRequiredField() {
        val json = manifestJson().replace(""""name": "示例插件",""", "")

        assertTrue(PluginManifestParser.parse(json).isFailure)
    }

    @Test
    fun parsesHomeWidgets() {
        val manifest = parseManifest(
            manifestJson(
                homeWidgets = """
                    [
                      {
                        "id":"today",
                        "type":"summary",
                        "title":"今日任务",
                        "size":"medium",
                        "sourceId":"memo_summary"
                      }
                    ]
                """.trimIndent(),
            ),
        )

        val widget = manifest.homeWidgets.single()
        assertEquals(PluginHomeWidgetSize.MEDIUM, widget.size)
        assertEquals(3, widget.maxItems)
        assertEquals(false, widget.sensitive)
    }

    private fun PluginManifest.entryPageFile(): String =
        pages.first { it.id == entryPage }.file
}
