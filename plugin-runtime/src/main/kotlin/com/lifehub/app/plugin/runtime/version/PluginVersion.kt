package com.lifehub.app.plugin.runtime.version

/**
 * 插件版本比较：只比较数字段，忽略 `-`/`+` 之后的后缀。
 *
 * 与首版 `PluginZipImporter` 的行为保持一致，供 ZIP 导入与插件市场共用。
 */
object PluginVersion {

    fun compare(left: String, right: String): Int {
        val a = parts(left)
        val b = parts(right)
        for (index in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(index) { 0 }.compareTo(b.getOrElse(index) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    fun isNewer(candidate: String, installed: String): Boolean = compare(candidate, installed) > 0

    private fun parts(value: String): List<Int> =
        value.substringBefore('-').substringBefore('+')
            .split('.')
            .map { it.toIntOrNull() ?: 0 }
}
