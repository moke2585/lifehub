package com.lifehub.app.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/**
 * 宿主统一顶栏：Material 3 surfaceContainer 表面，无分割线；
 * 插件页面与宿主页面共用，标题加重以强化层级。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeHubTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val container = scheme.surfaceContainer
    val scrolledContainer = scheme.surfaceContainerHigh

    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = container,
            titleContentColor = scheme.onSurface,
            navigationIconContentColor = scheme.onSurfaceVariant,
            actionIconContentColor = scheme.onSurfaceVariant,
            scrolledContainerColor = scrolledContainer,
        ),
    )
}

/** 页面标题的标准写法，保持顶栏字号一致。 */
@Composable
fun LifeHubTopBarTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        maxLines = 1,
    )
}
