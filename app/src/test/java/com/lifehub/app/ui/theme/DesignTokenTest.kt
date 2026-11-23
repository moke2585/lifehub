package com.lifehub.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定间距、形状、字号与图表色板，防止后续迭代重新引入零散数值。
 */
class DesignTokenTest {

    @Test
    fun spacingFollowsFourDpRhythm() {
        val values = listOf(
            LifeHubSpacing.xxs,
            LifeHubSpacing.xs,
            LifeHubSpacing.sm,
            LifeHubSpacing.md,
            LifeHubSpacing.lg,
            LifeHubSpacing.xl,
            LifeHubSpacing.xxl,
            LifeHubSpacing.xxxl,
        ).map { it.value }

        assertEquals(listOf(2f, 4f, 8f, 12f, 16f, 24f, 32f, 48f), values)
    }

    @Test
    fun pageGuttersMatchBreakpoints() {
        assertEquals(16f, LifeHubContentWidth.compact.value)
        assertEquals(24f, LifeHubContentWidth.medium.value)
        assertEquals(32f, LifeHubContentWidth.expanded.value)
        assertEquals(48f, LifeHubSizes.touchTarget.value)
        assertEquals(24f, LifeHubSizes.icon.value)
    }

    @Test
    fun shapesStayInRoundedScale() {
        assertEquals(RoundedCornerShape(8.dp), LifeHubShapes.extraSmall)
        assertEquals(RoundedCornerShape(12.dp), LifeHubShapes.small)
        assertEquals(RoundedCornerShape(18.dp), LifeHubShapes.medium)
        assertEquals(RoundedCornerShape(24.dp), LifeHubShapes.large)
        assertEquals(RoundedCornerShape(32.dp), LifeHubShapes.extraLarge)
    }

    @Test
    fun expressiveShapesKeepAsymmetricCorners() {
        assertTrue(LifeHubExpressiveShapes.hero != LifeHubExpressiveShapes.tool)
        assertTrue(LifeHubExpressiveShapes.tool != LifeHubExpressiveShapes.badge)
    }

    @Test
    fun chineseBodyLineHeightStaysComfortable() {
        val bodyMedium = LifeHubTypography.bodyMedium
        val bodyLarge = LifeHubTypography.bodyLarge

        assertEquals(14f, bodyMedium.fontSize.value)
        assertEquals(22f, bodyMedium.lineHeight.value)
        assertEquals(16f, bodyLarge.fontSize.value)
        assertEquals(26f, bodyLarge.lineHeight.value)

        assertTrue(
            "bodyMedium 行高应不低于 1.5 倍字号",
            bodyMedium.lineHeight.value >= bodyMedium.fontSize.value * 1.5f,
        )
        assertTrue(
            "bodyLarge 行高应不低于 1.5 倍字号",
            bodyLarge.lineHeight.value >= bodyLarge.fontSize.value * 1.5f,
        )
    }

    @Test
    fun weightsStayWithinThreeSteps() {
        val weights = listOf(
            LifeHubTypography.bodyMedium.fontWeight,
            LifeHubTypography.titleMedium.fontWeight,
            LifeHubTypography.headlineMedium.fontWeight,
            LifeHubTypography.labelMedium.fontWeight,
        ).toSet()

        assertEquals(
            setOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold),
            weights,
        )
    }

    @Test
    fun chartPalettesExposeEightDistinctColors() {
        assertEquals(8, LifeHubLightChartColors.series.size)
        assertEquals(8, LifeHubDarkChartColors.series.size)
        assertEquals(8, LifeHubLightChartColors.series.toSet().size)
        assertEquals(8, LifeHubDarkChartColors.series.toSet().size)
    }
}
