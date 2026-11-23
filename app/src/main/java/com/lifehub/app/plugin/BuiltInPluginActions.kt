package com.lifehub.app.plugin

import com.lifehub.pluginapi.PluginActionHandler
import com.lifehub.pluginapi.PluginActionResult

/**
 * 第一方插件动作处理器。P6 会把记账、任务清单的处理器移到各自的插件包中。
 */
object BuiltInPluginActions {
    const val HELLO_GREET: String = "com.lifehub.plugin.hello.greet"

    private const val COLLECTION_META = "meta"
    private const val ID_GREETING_COUNT = "greeting_count"

    val handlers: Map<String, PluginActionHandler> = mapOf(
        HELLO_GREET to PluginActionHandler { context ->
            val request = context.request
            val storage = context.storage
                ?: return@PluginActionHandler PluginActionResult.Failure(
                    "插件未获得私有存储授权",
                )

            val name = request.params["name"]?.trim().orEmpty().ifBlank { "世界" }
            val existing = storage.get(COLLECTION_META, ID_GREETING_COUNT)
            val nextCount = (existing?.payload?.toIntOrNull() ?: 0) + 1
            if (existing == null) {
                storage.insert(COLLECTION_META, nextCount.toString(), ID_GREETING_COUNT)
            } else {
                storage.update(COLLECTION_META, ID_GREETING_COUNT, nextCount.toString())
            }

            val details = buildList {
                add("第 $nextCount 次问候")
                request.params["amount"]?.takeIf { it.isNotBlank() }?.let { add("金额 $it") }
                request.params["date"]?.takeIf { it.isNotBlank() }?.let { add("日期 $it") }
                request.params["from"]?.takeIf { it.isNotBlank() }?.let { add("来源 $it") }
            }
            PluginActionResult.ShowMessage(
                "你好，$name（${details.joinToString("；")}）",
            )
        },
    )
}
