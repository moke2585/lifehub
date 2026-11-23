package com.lifehub.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutSplitTest {

    @Test
    fun clampsSplitRatioToSafeRange() {
        assertEquals(0.28f, clampSplitRatio(0.1f), 0.001f)
        assertEquals(0.65f, clampSplitRatio(0.9f), 0.001f)
        assertEquals(DEFAULT_SPLIT_RATIO, clampSplitRatio(DEFAULT_SPLIT_RATIO), 0.001f)
    }
}
