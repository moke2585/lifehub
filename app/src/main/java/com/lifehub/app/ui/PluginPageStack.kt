package com.lifehub.app.ui

internal const val MAX_PAGE_STACK = 8

internal data class PageRoute(
    val pageId: String,
    val params: Map<String, String>,
)

/**
 * 插件页面导航栈。目标页面已在栈中时回退到该页面，避免“编辑页 -> 列表页”产生重复列表页。
 */
internal fun navigatePageStack(
    stack: MutableList<PageRoute>,
    targetPageId: String,
    params: Map<String, String>,
    availablePages: Set<String>,
) {
    if (stack.isEmpty() || targetPageId !in availablePages) return

    val currentIndex = stack.lastIndex
    if (stack[currentIndex].pageId == targetPageId) {
        stack[currentIndex] = PageRoute(targetPageId, params)
        return
    }

    val existingIndex = stack.indexOfLast { it.pageId == targetPageId }
    if (existingIndex >= 0) {
        while (stack.lastIndex > existingIndex) {
            stack.removeAt(stack.lastIndex)
        }
        stack[existingIndex] = PageRoute(targetPageId, params)
        return
    }

    if (stack.size >= MAX_PAGE_STACK) {
        stack.removeAt(1)
    }
    stack += PageRoute(targetPageId, params)
}
