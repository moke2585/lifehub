package com.lifehub.pluginapi

internal fun manifestJson(
    pluginId: String = "com.lifehub.plugin.hello",
    name: String = "示例插件",
    publisher: String = "LifeHub",
    version: String = "1.0.0",
    minHostVersion: Int = 1,
    maxHostVersion: Int? = null,
    pluginSdkVersion: Int = 1,
    entryPage: String = "home",
    pages: String = """[{"id":"home","title":"首页","file":"pages/home.json"}]""",
    capabilities: String = """[{"id":"storage.private","reason":"保存插件自己的数据"}]""",
    homeWidgets: String = "[]",
    search: String = "null",
    dataSchemaVersion: Int = 1,
): String {
    val maxHostField = maxHostVersion?.let { ""","maxHostVersion":$it""" }.orEmpty()
    return """
        {
          "pluginId": "$pluginId",
          "name": "$name",
          "version": "$version",
          "description": "测试插件",
          "publisher": "$publisher",
          "minHostVersion": $minHostVersion$maxHostField,
          "pluginSdkVersion": $pluginSdkVersion,
          "capabilities": $capabilities,
          "entryPage": "$entryPage",
          "pages": $pages,
          "homeWidgets": $homeWidgets,
          "search": $search,
          "dataSchemaVersion": $dataSchemaVersion
        }
    """.trimIndent()
}

internal fun parseManifest(json: String = manifestJson()): PluginManifest =
    PluginManifestParser.parse(json).getOrThrow()
