package com.lifehub.app.plugin.runtime.index

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * 插件索引（`registry.json`）的根对象。
 *
 * 索引只用于发现与元数据；宿主安装前仍会校验包内签名、SHA-256 与签名者连续性。
 */
@Serializable
data class PluginIndex(
    val schema: Int,
    val generatedAt: String? = null,
    val plugins: List<IndexedPlugin> = emptyList(),
)

@Serializable
data class IndexedPlugin(
    val pluginId: String,
    val name: String,
    val version: String,
    /** `apk` 或 `lhplugin`。 */
    val artifactType: String,
    /** APK 插件的 applicationId；`lhplugin` 若填写必须等于 pluginId。 */
    val applicationId: String? = null,
    /** APK 的 versionCode；用于市场判断更新与降级。 */
    val versionCode: Long? = null,
    val url: String,
    val sha256: String,
    val signerSha256: String,
    val minHostVersion: Int,
    val pluginSdkVersion: Int,
    val capabilities: List<String>,
    val repo: String,
    val sizeBytes: Long? = null,
) {
    val isApk: Boolean get() = artifactType == PluginIndexParser.ARTIFACT_APK

    val isZip: Boolean get() = artifactType == PluginIndexParser.ARTIFACT_ZIP
}

object PluginIndexParser {
    const val SCHEMA: Int = 1
    const val ARTIFACT_APK: String = "apk"
    const val ARTIFACT_ZIP: String = "lhplugin"
    const val MAX_PLUGINS: Int = 200

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun parse(raw: String): Result<PluginIndex> = runCatching {
        val index = json.decodeFromString<PluginIndex>(raw)
        val issues = PluginIndexValidator.validate(index)
        require(issues.isEmpty()) { issues.joinToString("；") }
        index
    }
}

object PluginIndexValidator {
    private val PLUGIN_ID = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    private val VERSION = Regex("^\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?$")
    private val SHA256 = Regex("^[0-9a-f]{64}$")
    private val HTTPS = Regex("^https://\\S+$")
    private val CAPABILITY = Regex("^[a-z][a-z0-9._-]*$")

    fun validate(index: PluginIndex): List<String> {
        val issues = mutableListOf<String>()
        if (index.schema != PluginIndexParser.SCHEMA) {
            issues += "不支持的索引 schema：${index.schema}"
        }
        if (index.generatedAt != null) {
            runCatching { java.time.Instant.parse(index.generatedAt) }
                .onFailure { issues += "generatedAt 不是 ISO-8601 时间" }
        }
        if (index.plugins.size > PluginIndexParser.MAX_PLUGINS) {
            issues += "插件数量超过 ${PluginIndexParser.MAX_PLUGINS} 上限"
        }

        val seen = mutableSetOf<String>()
        index.plugins.forEachIndexed { position, plugin ->
            val prefix = "plugins[$position]"
            if (!PLUGIN_ID.matches(plugin.pluginId)) {
                issues += "$prefix.pluginId 格式无效：${plugin.pluginId}"
            } else if (!seen.add(plugin.pluginId)) {
                issues += "$prefix.pluginId 重复：${plugin.pluginId}"
            }
            if (plugin.name.isBlank()) issues += "$prefix.name 不能为空"
            if (!VERSION.matches(plugin.version)) {
                issues += "$prefix.version 不是合法语义化版本：${plugin.version}"
            }
            if (plugin.artifactType != PluginIndexParser.ARTIFACT_APK &&
                plugin.artifactType != PluginIndexParser.ARTIFACT_ZIP
            ) {
                issues += "$prefix.artifactType 必须是 apk 或 lhplugin：${plugin.artifactType}"
            }
            if (!HTTPS.matches(plugin.url)) issues += "$prefix.url 必须是 https 地址"
            if (!HTTPS.matches(plugin.repo)) issues += "$prefix.repo 必须是 https 地址"
            if (!SHA256.matches(plugin.sha256)) {
                issues += "$prefix.sha256 必须是 64 位小写十六进制"
            }
            if (!SHA256.matches(plugin.signerSha256)) {
                issues += "$prefix.signerSha256 必须是 64 位小写十六进制"
            }
            if (plugin.minHostVersion <= 0) issues += "$prefix.minHostVersion 必须是正整数"
            if (plugin.pluginSdkVersion <= 0) issues += "$prefix.pluginSdkVersion 必须是正整数"
            if (plugin.capabilities.isEmpty()) issues += "$prefix.capabilities 不能为空"
            if (plugin.capabilities.size != plugin.capabilities.distinct().size) {
                issues += "$prefix.capabilities 不能重复"
            }
            plugin.capabilities.forEach { capability ->
                if (!CAPABILITY.matches(capability)) {
                    issues += "$prefix.capabilities 含非法能力 ID：$capability"
                }
            }
            if (plugin.sizeBytes != null && plugin.sizeBytes <= 0) {
                issues += "$prefix.sizeBytes 必须是正整数"
            }

            when (plugin.artifactType) {
                PluginIndexParser.ARTIFACT_APK -> {
                    val applicationId = plugin.applicationId
                    if (applicationId.isNullOrBlank()) {
                        issues += "$prefix.applicationId 在 APK 条目中必填"
                    } else if (!PLUGIN_ID.matches(applicationId)) {
                        issues += "$prefix.applicationId 格式无效：$applicationId"
                    }
                    val versionCode = plugin.versionCode
                    if (versionCode == null || versionCode <= 0) {
                        issues += "$prefix.versionCode 在 APK 条目中必填且必须为正整数"
                    }
                }

                PluginIndexParser.ARTIFACT_ZIP -> {
                    val applicationId = plugin.applicationId
                    if (applicationId != null && applicationId != plugin.pluginId) {
                        issues += "$prefix.applicationId 必须等于 pluginId"
                    }
                    if (plugin.versionCode != null && plugin.versionCode <= 0) {
                        issues += "$prefix.versionCode 必须是正整数"
                    }
                }
            }
        }
        return issues
    }
}
