package com.lifehub.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校验主题关键配对的 WCAG 对比度，避免后续调整色值时无意破坏可读性。
 */
class ThemeContrastTest {

    @Test
    fun lightThemeTextPairsMeetContrast() {
        assertPairs(
            listOf(
                ContrastCase("onSurface / background", Light.onSurface, Light.background, 4.5),
                ContrastCase("onSurface / surface", Light.onSurface, Light.surface, 4.5),
                ContrastCase("onSurfaceVariant / surface", Light.onSurfaceVariant, Light.surface, 4.5),
                ContrastCase("onSurfaceVariant / surfaceContainer", Light.onSurfaceVariant, Light.surfaceContainer, 4.5),
                ContrastCase("onPrimary / primary", Light.onPrimary, Light.primary, 4.5),
                ContrastCase("onPrimaryContainer / primaryContainer", Light.onPrimaryContainer, Light.primaryContainer, 4.5),
                ContrastCase("onError / error", Light.onError, Light.error, 4.5),
                ContrastCase("error / surface", Light.error, Light.surface, 4.5),
                ContrastCase("positive / surface", LightSemantic.positive, Light.surface, 4.5),
                ContrastCase("negative / surface", LightSemantic.negative, Light.surface, 4.5),
                ContrastCase("primary / surface", Light.primary, Light.surface, 4.5),
                ContrastCase("outline / surface (non-text)", Light.outline, Light.surface, 3.0),
            ),
        )
    }

    @Test
    fun darkThemeTextPairsMeetContrast() {
        assertPairs(
            listOf(
                ContrastCase("onSurface / background", Dark.onSurface, Dark.background, 4.5),
                ContrastCase("onSurface / surface", Dark.onSurface, Dark.surface, 4.5),
                ContrastCase("onSurfaceVariant / surface", Dark.onSurfaceVariant, Dark.surface, 4.5),
                ContrastCase("onSurfaceVariant / surfaceContainer", Dark.onSurfaceVariant, Dark.surfaceContainer, 4.5),
                ContrastCase("onPrimary / primary", Dark.onPrimary, Dark.primary, 4.5),
                ContrastCase("onPrimaryContainer / primaryContainer", Dark.onPrimaryContainer, Dark.primaryContainer, 4.5),
                ContrastCase("onError / error", Dark.onError, Dark.error, 4.5),
                ContrastCase("error / surface", Dark.error, Dark.surface, 4.5),
                ContrastCase("positive / surface", DarkSemantic.positive, Dark.surface, 4.5),
                ContrastCase("negative / surface", DarkSemantic.negative, Dark.surface, 4.5),
                ContrastCase("primary / surface", Dark.primary, Dark.surface, 4.5),
                ContrastCase("outline / surface (non-text)", Dark.outline, Dark.surface, 3.0),
            ),
        )
    }

    private fun assertPairs(cases: List<ContrastCase>) {
        cases.forEach { case ->
            val ratio = contrastRatio(case.foreground, case.background)
            assertTrue(
                "${case.label} 对比度 ${"%.2f".format(ratio)}:1 低于要求 ${case.minimum}:1",
                ratio >= case.minimum,
            )
        }
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val first = relativeLuminance(foreground)
        val second = relativeLuminance(background)
        val lighter = max(first, second)
        val darker = min(first, second)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val normalized = value.toDouble()
            return if (normalized <= 0.03928) {
                normalized / 12.92
            } else {
                ((normalized + 0.055) / 1.055).pow(2.4)
            }
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private data class ContrastCase(
        val label: String,
        val foreground: Color,
        val background: Color,
        val minimum: Double,
    )

    private companion object {
        val Light = LifeHubLightColors
        val Dark = LifeHubDarkColors
        val LightSemantic = LifeHubLightSemanticColors
        val DarkSemantic = LifeHubDarkSemanticColors
    }
}
