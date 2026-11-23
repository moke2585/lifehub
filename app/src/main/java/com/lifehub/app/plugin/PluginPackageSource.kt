package com.lifehub.app.plugin

import android.content.res.AssetManager
import com.lifehub.pluginapi.PluginPathRules
import java.io.InputStream

enum class PluginPackageTrust {
    /** 包内容随已签名的 APK 一起分发，由 APK v2+ 签名提供完整性与来源保证。 */
    APK_SIGNED,

    /** 外部导入的 `.lhplugin`：导入时已校验包签名并固定签名者。 */
    ZIP_SIGNED,

    /** 未经过宿主信任校验的来源；注册表拒绝加载。 */
    UNTRUSTED,
}

/**
 * 插件包内容来源。一个来源可以同时承载多个插件；信任等级按插件返回，
 * 便于内置包与外部 ZIP 组合。
 */
interface PluginPackageSource {
    fun pluginIds(): List<String>

    fun open(pluginId: String, relativePath: String): InputStream?

    fun trustFor(pluginId: String): PluginPackageTrust
}

class AssetPluginPackageSource(
    private val assets: AssetManager,
) : PluginPackageSource {

    override fun pluginIds(): List<String> =
        assets.list(PLUGIN_ROOT).orEmpty().toList()

    override fun open(pluginId: String, relativePath: String): InputStream? {
        if (!PluginPathRules.isSafeRelativePath(pluginId) ||
            !PluginPathRules.isSafeRelativePath(relativePath)
        ) {
            return null
        }
        return runCatching { assets.open("$PLUGIN_ROOT/$pluginId/$relativePath") }.getOrNull()
    }

    override fun trustFor(pluginId: String): PluginPackageTrust =
        PluginPackageTrust.APK_SIGNED

    private companion object {
        const val PLUGIN_ROOT = "plugins"
    }
}

sealed interface PluginTrustDecision {
    data object Trusted : PluginTrustDecision

    data class Denied(val reason: String) : PluginTrustDecision
}

/**
 * 插件包来源信任策略：内置 APK 与已验签的 `.lhplugin` 可信，未知来源拒绝。
 */
class PluginTrustPolicy(
    private val allowedTrust: Set<PluginPackageTrust> = setOf(
        PluginPackageTrust.APK_SIGNED,
        PluginPackageTrust.ZIP_SIGNED,
    ),
) {
    fun verify(trust: PluginPackageTrust): PluginTrustDecision =
        if (trust in allowedTrust) {
            PluginTrustDecision.Trusted
        } else {
            PluginTrustDecision.Denied("插件包来源未通过签名信任校验：$trust")
        }
}
