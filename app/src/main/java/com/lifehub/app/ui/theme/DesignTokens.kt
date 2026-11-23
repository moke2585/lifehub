package com.lifehub.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * 插件渲染器与宿主共用的间距 token。4dp 基准、8dp 节奏；
 * 页面边距与卡片内边距由 [LifeHubContentWidth] 和 [LifeHubSpacing] 组合表达。
 */
object LifeHubSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

/** 页面边距按窗口宽度切换：手机 16dp、平板 24dp、宽屏 32dp。 */
object LifeHubContentWidth {
    val compact = 16.dp
    val medium = 24.dp
    val expanded = 32.dp
    val maxContent = 720.dp
    val maxHome = 960.dp
}

/**
 * 尺寸 token。图标 24dp、触控热区 48dp，列表行高按行数分档。
 */
object LifeHubSizes {
    val iconSmall = 20.dp
    val icon = 24.dp
    val iconLarge = 32.dp
    val iconContainer = 44.dp
    val touchTarget = 48.dp
    val button = 48.dp
    val buttonCompact = 40.dp
    val divider = 1.dp
    val statusDot = 6.dp
    val emptyStateIcon = 96.dp
    val listRowSingle = 56.dp
    val listRowDouble = 72.dp
    val listRowTriple = 88.dp
}

/**
 * 形状 token：Material 3 Expressive 使用更大的圆角与对比形状。
 * 卡片 24–32dp、输入框与按钮 16dp、对话框 32dp；chip 与 FAB 保持全圆角。
 */
internal val LifeHubShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * M3 Expressive 的不对称形状：主页主卡与工具卡使用不同角半径，
 * 在不改变功能的前提下制造“有表情”的轮廓对比。
 */
object LifeHubExpressiveShapes {
    val hero = RoundedCornerShape(
        topStart = 36.dp,
        topEnd = 36.dp,
        bottomEnd = 12.dp,
        bottomStart = 36.dp,
    )
    val tool = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomEnd = 28.dp,
        bottomStart = 12.dp,
    )
    val badge = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomEnd = 18.dp,
        bottomStart = 6.dp,
    )
    val pill = CircleShape
}

/** 阴影只用于浮层；卡片用表面色阶与描边建立层次。 */
object LifeHubElevation {
    val flat = 0.dp
    val card = 0.dp
    val floating = 3.dp
    val dialog = 6.dp
}

/**
 * 动效 token：短、稳、可中断。所有时长在系统关闭动画时由调用方直接跳过。
 */
object LifeHubMotion {
    const val short = 140
    const val medium = 260
    const val long = 380

    val standard = FastOutSlowInEasing
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val springDefault: SpringSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 380f)

    /** M3 Expressive 的默认弹性：略有回弹，用于卡片入场与按压。 */
    val springExpressive: SpringSpec<Float> = spring(dampingRatio = 0.68f, stiffness = 320f)
    val springGentle: SpringSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 200f)
}

/** 数字与金额使用等宽数字，保证纵向对齐。 */
object LifeHubType {
    val tabularNumbers = TextStyle(fontFeatureSettings = "tnum")
}
