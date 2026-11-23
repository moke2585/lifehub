package com.lifehub.pluginapi

/**
 * 插件 SDK 版本，是宿主与插件判断兼容性的唯一来源。
 *
 * 版本号本身不授予任何能力；安装与运行仍需校验 manifest 声明、宿主版本与用户授权。
 */
object PluginApiVersion {
    const val MAJOR: Int = 1
    const val MINOR: Int = 0
    const val PATCH: Int = 0

    val current: String
        get() = "$MAJOR.$MINOR.$PATCH"

    /**
     * 首版只接受同一主版本的插件，避免出现未定义的行为降级。
     */
    fun isCompatible(pluginMajor: Int): Boolean = pluginMajor == MAJOR
}
