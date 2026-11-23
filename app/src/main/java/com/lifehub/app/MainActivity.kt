package com.lifehub.app

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.lifehub.app.core.AppLog
import com.lifehub.app.ui.LifeHubApp
import com.lifehub.app.ui.theme.LifeHubTheme
import com.lifehub.app.ui.theme.LocalReduceMotion
import com.lifehub.app.ui.theme.LocalThemePreferenceStore
import com.lifehub.app.ui.theme.ThemeMode
import com.lifehub.app.ui.theme.ThemePreferenceStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLog.event(AppLog.Category.STARTUP, "main_activity_created")
        setContent {
            val context = LocalContext.current
            val themeStore = remember { ThemePreferenceStore(context) }
            val darkTheme = when (themeStore.mode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val systemReduceMotion = remember(context) {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                ) == 0f
            }
            val reduceMotion = systemReduceMotion || themeStore.reduceMotion
            val dynamicScheme = if (
                themeStore.dynamicColor &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
                if (darkTheme) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            } else {
                null
            }
            // 顶栏已改为表面色，系统栏图标明暗直接跟随主题，不再取反。
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            LifeHubTheme(darkTheme = darkTheme, colorScheme = dynamicScheme) {
                CompositionLocalProvider(
                    LocalThemePreferenceStore provides themeStore,
                    LocalReduceMotion provides reduceMotion,
                ) {
                    LifeHubApp()
                }
            }
        }
    }
}
