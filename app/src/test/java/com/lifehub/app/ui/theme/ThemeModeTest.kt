package com.lifehub.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun parsesKnownStorageValues() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("system"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorage("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage(" DARK "))
    }

    @Test
    fun fallsBackToSystemForUnknownValues() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("auto"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(""))
    }
}
