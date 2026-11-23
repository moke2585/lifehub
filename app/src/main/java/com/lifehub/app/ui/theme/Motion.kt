package com.lifehub.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

/**
 * 按钮按压反馈：按下时缩到 98%，松开回弹。
 * 默认跟随系统动画设置；开启“减少动效”后保持 1.0。
 */
@Composable
fun rememberPressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduceMotion) 0.96f else 1f,
        animationSpec = LifeHubMotion.springExpressive,
        label = "press-scale",
    )
    return scale
}
