package com.lifehub.pluginapi

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object PluginManifestParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun parse(raw: String): Result<PluginManifest> = runCatching {
        json.decodeFromString<PluginManifest>(raw)
    }
}
