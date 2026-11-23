package com.lifehub.pluginapi

/**
 * 插件包内相对路径的统一校验规则。
 *
 * 拒绝绝对路径、反斜杠、空段、`.` 与 `..`，避免插件清单或包源被用于路径穿越。
 */
object PluginPathRules {
    fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank() || path.startsWith("/") || path.contains('\\')) return false
        return path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }
    }
}
