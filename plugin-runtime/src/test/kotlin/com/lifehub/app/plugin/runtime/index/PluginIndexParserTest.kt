package com.lifehub.app.plugin.runtime.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginIndexParserTest {

    @Test
    fun parsesValidApkEntry() {
        val result = PluginIndexParser.parse(json(pluginJson()))

        assertTrue(result.toString(), result.isSuccess)
        val plugin = result.getOrThrow().plugins.single()
        assertEquals("com.lifehub.plugin.memo", plugin.pluginId)
        assertEquals("apk", plugin.artifactType)
        assertEquals("com.lifehub.plugin.memo", plugin.applicationId)
        assertEquals(7L, plugin.versionCode)
    }

    @Test
    fun acceptsEmptyIndex() {
        val result = PluginIndexParser.parse("""{"schema":1,"plugins":[]}""")

        assertTrue(result.toString(), result.isSuccess)
        assertTrue(result.getOrThrow().plugins.isEmpty())
    }

    @Test
    fun rejectsApkWithoutApplicationId() {
        val result = PluginIndexParser.parse(
            json(pluginJson().replace("""  "applicationId": "com.lifehub.plugin.memo",""", "")),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("applicationId"))
    }

    @Test
    fun rejectsApkWithoutVersionCode() {
        val result = PluginIndexParser.parse(
            json(pluginJson().replace("""  "versionCode": 7,""", "")),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("versionCode"))
    }

    @Test
    fun rejectsUnknownFields() {
        val result = PluginIndexParser.parse(
            json(pluginJson().replace("\"repo\"", "\"unknown\": true,\n  \"repo\"")),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsDuplicatePluginIds() {
        val plugin = pluginJson()
        val result = PluginIndexParser.parse(
            """{"schema":1,"plugins":[$plugin,$plugin]}""",
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("重复"))
    }

    @Test
    fun rejectsInvalidHashAndUrl() {
        val badHash = PluginIndexParser.parse(
            json(pluginJson().replace(SHA256, "ABC")),
        )
        assertTrue(badHash.isFailure)

        val badUrl = PluginIndexParser.parse(
            json(pluginJson().replace("https://example.com/memo.apk", "http://example.com/memo.apk")),
        )
        assertTrue(badUrl.isFailure)
        assertTrue(badUrl.exceptionOrNull()?.message.orEmpty().contains("https"))
    }

    @Test
    fun rejectsInvalidVersion() {
        val result = PluginIndexParser.parse(
            json(pluginJson().replace("\"1.1.0\"", "\"1.1\"")),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("版本"))
    }

    @Test
    fun rejectsZipWithDifferentApplicationId() {
        val zip = pluginJson()
            .replace("\"artifactType\": \"apk\"", "\"artifactType\": \"lhplugin\"")
            .replace("\"applicationId\": \"com.lifehub.plugin.memo\"", "\"applicationId\": \"com.example.other\"")
        val result = PluginIndexParser.parse(json(zip))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("applicationId"))
    }

    private fun json(plugin: String): String =
        """{"schema":1,"generatedAt":"2026-11-23T00:00:00Z","plugins":[$plugin]}"""

    private fun pluginJson(): String = """
        {
          "pluginId": "com.lifehub.plugin.memo",
          "name": "任务清单",
          "version": "1.1.0",
          "artifactType": "apk",
          "applicationId": "com.lifehub.plugin.memo",
          "versionCode": 7,
          "url": "https://example.com/memo.apk",
          "sha256": "$SHA256",
          "signerSha256": "$SIGNER",
          "minHostVersion": 1,
          "pluginSdkVersion": 1,
          "capabilities": ["ui.action", "storage.private"],
          "repo": "https://example.com/lifehub-plugin-memo"
        }
    """.trimIndent()

    private companion object {
        const val SHA256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val SIGNER = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
    }
}
