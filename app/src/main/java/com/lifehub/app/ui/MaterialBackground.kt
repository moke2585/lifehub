package com.lifehub.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lifehub.app.R
import com.lifehub.app.ui.theme.LocalReduceMotion

/**
 * Material 3 Expressive 背景：低对比的有机形状壁纸 + 缓慢漂浮的色块。
 * 壁纸来自 Material You 风格生成素材，叠加表面色渐变保证正文可读；
 * “减少动效”开启时只保留静态壁纸。
 */
@Composable
fun MaterialWallpaperBackground(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val reduceMotion = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "wallpaper-drift")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wallpaper-drift-value",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(
                if (dark) R.drawable.material_bg_dark else R.drawable.material_bg_light,
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = if (dark) 0.78f else 0.9f,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = if (reduceMotion) 1.02f else 1.04f + drift * 0.05f
                    scaleX = scale
                    scaleY = scale
                    translationY = if (reduceMotion) 0f else (drift - 0.5f) * 24.dp.toPx()
                },
        )

        // 顶部轻、底部重的表面色渐变，让卡片与正文保持 Material 的清晰层级。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to scheme.surface.copy(alpha = if (dark) 0.2f else 0.12f),
                        0.45f to scheme.surface.copy(alpha = if (dark) 0.48f else 0.38f),
                        1f to scheme.surface.copy(alpha = if (dark) 0.9f else 0.88f),
                    ),
                ),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (reduceMotion) return@Canvas
            val driftX = (drift - 0.5f) * size.width * 0.05f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        scheme.primary.copy(alpha = 0.08f),
                        scheme.primary.copy(alpha = 0f),
                    ),
                    center = Offset(size.width * 0.18f + driftX, size.height * 0.22f),
                    radius = size.minDimension * 0.45f,
                ),
                radius = size.minDimension * 0.45f,
                center = Offset(size.width * 0.18f + driftX, size.height * 0.22f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        scheme.tertiary.copy(alpha = 0.06f),
                        scheme.tertiary.copy(alpha = 0f),
                    ),
                    center = Offset(size.width * 0.86f - driftX, size.height * 0.1f),
                    radius = size.minDimension * 0.38f,
                ),
                radius = size.minDimension * 0.38f,
                center = Offset(size.width * 0.86f - driftX, size.height * 0.1f),
            )
        }
    }
}
