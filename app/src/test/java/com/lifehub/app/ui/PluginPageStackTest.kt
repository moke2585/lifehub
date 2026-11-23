package com.lifehub.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginPageStackTest {

    private val pages = setOf("list", "edit", "stats")

    @Test
    fun pushesForwardNavigation() {
        val stack = mutableListOf(PageRoute("list", emptyMap()))

        navigatePageStack(stack, "edit", mapOf("id" to "1"), pages)

        assertEquals(
            listOf("list", "edit"),
            stack.map { it.pageId },
        )
        assertEquals(mapOf("id" to "1"), stack.last().params)
    }

    @Test
    fun popsBackToExistingPageAndUpdatesParams() {
        val stack = mutableListOf(
            PageRoute("list", emptyMap()),
            PageRoute("edit", mapOf("id" to "1")),
        )

        navigatePageStack(stack, "list", mapOf("period" to "month"), pages)

        assertEquals(listOf("list"), stack.map { it.pageId })
        assertEquals(mapOf("period" to "month"), stack.single().params)
    }

    @Test
    fun replacesCurrentPageParams() {
        val stack = mutableListOf(PageRoute("list", emptyMap()))

        navigatePageStack(stack, "list", mapOf("period" to "year"), pages)

        assertEquals(listOf("list"), stack.map { it.pageId })
        assertEquals(mapOf("period" to "year"), stack.single().params)
    }

    @Test
    fun ignoresUnknownTarget() {
        val stack = mutableListOf(PageRoute("list", emptyMap()))

        navigatePageStack(stack, "missing", emptyMap(), pages)

        assertEquals(listOf("list"), stack.map { it.pageId })
    }

    @Test
    fun keepsRootWhenStackIsFull() {
        val stack = mutableListOf(PageRoute("list", emptyMap()))
        repeat(MAX_PAGE_STACK - 1) { index ->
            stack += PageRoute("page$index", emptyMap())
        }

        navigatePageStack(stack, "stats", emptyMap(), pages + "stats")

        assertEquals(MAX_PAGE_STACK, stack.size)
        assertEquals("list", stack.first().pageId)
        assertEquals("stats", stack.last().pageId)
    }
}
