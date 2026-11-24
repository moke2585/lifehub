package com.lifehub.app.market.index

import android.content.res.AssetManager

/** 随 APK 内置的索引快照与索引公钥；内置内容由 APK 签名保护。 */
class BundledPluginIndex(private val assets: AssetManager) {

    fun registryBytes(): ByteArray = assets.open(REGISTRY_ASSET).use { it.readBytes() }

    fun publicKey(keyId: String): String? {
        if (!KEY_ID.matches(keyId)) return null
        return runCatching {
            assets.open("$KEY_DIR/$keyId.pem").use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
    }

    private companion object {
        const val REGISTRY_ASSET = "market/registry.json"
        const val KEY_DIR = "market/keys"
        val KEY_ID = Regex("^[A-Za-z0-9._-]+$")
    }
}
