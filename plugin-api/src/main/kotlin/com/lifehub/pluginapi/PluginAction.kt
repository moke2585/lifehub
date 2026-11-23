package com.lifehub.pluginapi

import kotlinx.serialization.Serializable

/**
 * 插件 action 的调用契约。宿主只把经过 schema 校验的动作转成请求，
 * 能力与权限复核在后续数据/运行时阶段接入。
 */
@Serializable
data class PluginActionRequest(
    val pluginId: String,
    val pageId: String,
    val actionId: String,
    val params: Map<String, String> = emptyMap(),
)

@Serializable
sealed interface PluginActionResult {
    @Serializable
    data object Success : PluginActionResult

    @Serializable
    data class ShowMessage(val message: String) : PluginActionResult

    @Serializable
    data class Navigate(
        val pageId: String,
        val params: Map<String, String> = emptyMap(),
    ) : PluginActionResult

    @Serializable
    data class Refresh(
        val message: String? = null,
        /** 可选的撤销动作文案，例如“撤销”。 */
        val undoLabel: String? = null,
        /** 用户点击撤销时执行的动作。 */
        val undoAction: PluginAction? = null,
        /** 刷新后需要清空的输入框 id，例如快速添加成功后清空输入。 */
        val clearInputIds: List<String> = emptyList(),
    ) : PluginActionResult

    @Serializable
    data class Failure(val message: String) : PluginActionResult

    /**
     * 插件生成一张图片，由宿主保存到系统相册。
     * 插件只负责像素与文件名，不直接接触 Android 存储 API。
     */
    @Serializable
    data class ExportImage(
        val fileName: String,
        val pngBytes: ByteArray,
    ) : PluginActionResult
}

/**
 * action 执行上下文。只有用户已授权 `storage.private` 时 [storage] 才非空；
 * [assetReader] 与数据源相同，只读插件包内资产。
 */
data class PluginActionContext(
    val request: PluginActionRequest,
    val storage: PluginStorage? = null,
    val assetReader: ((String) -> String?)? = null,
)

fun interface PluginActionHandler {
    fun handle(context: PluginActionContext): PluginActionResult
}
