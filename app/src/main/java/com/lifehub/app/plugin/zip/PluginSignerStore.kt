package com.lifehub.app.plugin.zip

/**
 * 记录每个外部插件的签名者指纹，保证同一 pluginId 的升级使用同一签名者。
 */
interface PluginSignerStore {
    fun fingerprint(pluginId: String): String?

    fun pin(pluginId: String, fingerprint: String, keyId: String)
}

class SharedPreferencesPluginSignerStore(
    private val preferences: android.content.SharedPreferences,
) : PluginSignerStore {

    override fun fingerprint(pluginId: String): String? =
        preferences.getString(key(pluginId), null)
            ?.substringBefore('|')
            ?.takeIf { it.isNotBlank() }

    override fun pin(pluginId: String, fingerprint: String, keyId: String) {
        preferences.edit()
            .putString(key(pluginId), "$fingerprint|$keyId")
            .apply()
    }

    private fun key(pluginId: String): String = "plugin_signer_$pluginId"
}
