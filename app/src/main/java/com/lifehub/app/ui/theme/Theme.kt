package com.lifehub.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 雾林 Calm Forest：中性表面承担绝大部分面积，绿色只用于主行动、选中态与品牌标识。
// 颜色角色完整覆盖 Material 3 的 primary / secondary / tertiary / error / surface / outline 六组，
// 插件与宿主共用同一套角色，插件只能通过 MaterialTheme 取色。
internal val LifeHubLightColors = lightColorScheme(
    primary = Color(0xFF1F5C43),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6EFDD),
    onPrimaryContainer = Color(0xFF0B2A1B),
    inversePrimary = Color(0xFF9ED8B5),
    secondary = Color(0xFF4C6B57),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE9DF),
    onSecondaryContainer = Color(0xFF12251A),
    tertiary = Color(0xFF7A5A32),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3E5CF),
    onTertiaryContainer = Color(0xFF2E1F0C),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF7F8F6),
    onBackground = Color(0xFF17201A),
    surface = Color(0xFFFCFDFB),
    onSurface = Color(0xFF17201A),
    surfaceVariant = Color(0xFFE1E8E2),
    onSurfaceVariant = Color(0xFF5A665E),
    surfaceTint = Color(0xFF1F5C43),
    inverseSurface = Color(0xFF2B322D),
    inverseOnSurface = Color(0xFFEFF3EF),
    outline = Color(0xFF7C8A80),
    outlineVariant = Color(0xFFDDE4DE),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFFDDE3DD),
    surfaceBright = Color(0xFFFCFDFB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAF8),
    surfaceContainer = Color(0xFFF2F4F1),
    surfaceContainerHigh = Color(0xFFECEFEB),
    surfaceContainerHighest = Color(0xFFE5E9E4),
)

internal val LifeHubDarkColors = darkColorScheme(
    primary = Color(0xFF9ED8B5),
    onPrimary = Color(0xFF06301E),
    primaryContainer = Color(0xFF214836),
    onPrimaryContainer = Color(0xFFD6EFDD),
    inversePrimary = Color(0xFF1F5C43),
    secondary = Color(0xFFB3CBB9),
    onSecondary = Color(0xFF1E3326),
    secondaryContainer = Color(0xFF31493A),
    onSecondaryContainer = Color(0xFFDDE9DF),
    tertiary = Color(0xFFE5C08A),
    onTertiary = Color(0xFF3D2B10),
    tertiaryContainer = Color(0xFF553F1E),
    onTertiaryContainer = Color(0xFFF3E5CF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121612),
    onBackground = Color(0xFFE3E9E4),
    surface = Color(0xFF171C18),
    onSurface = Color(0xFFE3E9E4),
    surfaceVariant = Color(0xFF3A453D),
    onSurfaceVariant = Color(0xFFA9B5AC),
    surfaceTint = Color(0xFF9ED8B5),
    inverseSurface = Color(0xFFE3E9E4),
    inverseOnSurface = Color(0xFF2B322D),
    outline = Color(0xFF78857B),
    outlineVariant = Color(0xFF3A453D),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFF121612),
    surfaceBright = Color(0xFF333C35),
    surfaceContainerLowest = Color(0xFF0D110E),
    surfaceContainerLow = Color(0xFF1B211C),
    surfaceContainer = Color(0xFF1F2620),
    surfaceContainerHigh = Color(0xFF242C26),
    surfaceContainerHighest = Color(0xFF2A332C),
)

/**
 * 字阶为中文重设：正文行高 1.55–1.65，标题行高 1.35–1.45；
 * 字重只保留 400/500/600 三档，避免中文粗体过重。
 */
internal val LifeHubTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 48.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
)

/**
 * 收支等语义色与品牌色分离：浅色主题使用更深的绿色保证白底可读性，
 * 深色主题改用高明度色，避免深绿在深底上对比不足。
 */
@Immutable
data class LifeHubSemanticColors(
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val info: Color,
)

internal val LifeHubLightSemanticColors = LifeHubSemanticColors(
    positive = Color(0xFF2E6B4F),
    negative = Color(0xFFB3261E),
    warning = Color(0xFF8A5A00),
    info = Color(0xFF2C5C8A),
)

internal val LifeHubDarkSemanticColors = LifeHubSemanticColors(
    positive = Color(0xFF8FD3AE),
    negative = Color(0xFFFFB4AB),
    warning = Color(0xFFF0C36A),
    info = Color(0xFF9CC7EF),
)

val LocalLifeHubSemanticColors = staticCompositionLocalOf { LifeHubLightSemanticColors }

/** 图表色板由主题统一提供，插件渲染器不再硬编码色值。 */
@Immutable
data class LifeHubChartColors(
    val series: List<Color>,
)

internal val LifeHubLightChartColors = LifeHubChartColors(
    series = listOf(
        Color(0xFF2F6B4F),
        Color(0xFF6E8B3D),
        Color(0xFFB08948),
        Color(0xFFA65E4E),
        Color(0xFF5B7C99),
        Color(0xFF7A6B9A),
        Color(0xFF4E8C8A),
        Color(0xFF8A8F98),
    ),
)

internal val LifeHubDarkChartColors = LifeHubChartColors(
    series = listOf(
        Color(0xFF8FC7A8),
        Color(0xFFA9C46A),
        Color(0xFFE0BE7A),
        Color(0xFFD89A88),
        Color(0xFF8FB0CC),
        Color(0xFFB0A3CC),
        Color(0xFF7FC3C0),
        Color(0xFFAEB4BC),
    ),
)

val LocalLifeHubChartColors = staticCompositionLocalOf { LifeHubLightChartColors }

/** 系统或用户开启“减少动效”时为 true；动画应直接跳到终态。 */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun LifeHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorScheme: ColorScheme? = null,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colorScheme ?: if (darkTheme) LifeHubDarkColors else LifeHubLightColors,
        typography = LifeHubTypography,
        shapes = LifeHubShapes,
    ) {
        CompositionLocalProvider(
            LocalLifeHubSemanticColors provides if (darkTheme) {
                LifeHubDarkSemanticColors
            } else {
                LifeHubLightSemanticColors
            },
            LocalLifeHubChartColors provides if (darkTheme) {
                LifeHubDarkChartColors
            } else {
                LifeHubLightChartColors
            },
            content = content,
        )
    }
}
