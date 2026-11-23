package com.lifehub.pluginapi

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object PluginPageParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        classDiscriminator = "type"
    }

    fun parse(raw: String): Result<PluginPageDocument> = runCatching {
        json.decodeFromString<PluginPageDocument>(raw)
    }
}
