package com.lifehub.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLogContractTest {

    @Test
    fun categoryLabelsAreUniqueAndAscii() {
        val labels = AppLog.Category.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
        labels.forEach { label ->
            assertEquals(label.lowercase(), label)
            assertEquals(true, label.all { it in 'a'..'z' })
        }
    }
}
