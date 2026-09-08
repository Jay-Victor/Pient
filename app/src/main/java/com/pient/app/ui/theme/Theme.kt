package com.pient.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pient.app.data.DarkSchemes
import com.pient.app.data.LightSchemes
import com.pient.app.data.SettingsStore
import com.pient.app.data.ThemeScheme

/**
 * 当前是否深色主题（CompositionLocal：状态感知、切换即时生效）。
 * 供代码内联判断明暗（终端 16 色、品牌紫等主题联动色）。
 * ★ 不要用 SideEffect + 全局变量同步——会滞后一帧且不触发重组，
 * 导致切换主题后出现"亮色下黑块 / 暗色下深色文字"（用户实测）。
 * 页面底色统一由 PientApp 根部绘制 colorScheme.background，
 * 任何页面/组件不得依赖窗口底色或硬编码明暗背景。
 */
val LocalPientIsDark = compositionLocalOf { true }

/**
 * 用户消息气泡底色（2026-09-08 改不透明实底，对齐 Hermes --ui-chat-bubble-background 公式）：
 * 暗 = accent 46% over surfaceContainer（Hermes .dark --theme-mix-bubble:46% + neutral-card）；
 * 亮 = accent 22% over background（pi-web #eff6ff / Operit primaryContainer 蓝系 tint 语义）。
 * 两端均 compositeOver 实底，不透明。
 */
val LocalPientUserBubble = compositionLocalOf { Color(0xFF345B88) }

/**
 * Pient 主题：Hermes 令牌同源 ColorScheme 映射（设计计划附录 A）。
 * 用户可在"主题与外观"中修改主色（accent），secondary/userBubble 随主色联动。
 * darkTheme 为必填参数：明暗状态只能来自调用方（状态感知），
 * 不得回落到任何全局变量（历史教训见上）。
 */
@Composable
fun PientTheme(
    darkTheme: Boolean,
    accent: Color = if (darkTheme) DarkPrimary else LightPrimary,
    scheme: ThemeScheme = if (darkTheme) DarkSchemes.first() else LightSchemes.first(),
    content: @Composable () -> Unit,
) {
    // 主色联动派生（secondary / userBubble 蓝色系 tint 随主色走）
    val darkSecondary = accent.copy(alpha = 0.14f).compositeOver(scheme.surfaceContainer)
    val lightSecondary = accent.copy(alpha = 0.12f).compositeOver(scheme.surfaceContainer)
    val darkUserBubble = accent.copy(alpha = 0.46f).compositeOver(scheme.surfaceContainer)
    val lightUserBubble = accent.copy(alpha = 0.22f).compositeOver(scheme.background)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accent,
            onPrimary = DarkOnPrimary,
            secondary = darkSecondary,
            onSecondary = accent,
            background = scheme.background,
            onBackground = DarkOnBackground,
            surface = scheme.background,
            onSurface = DarkOnBackground,
            surfaceVariant = scheme.surfaceContainer,
            onSurfaceVariant = scheme.onSurfaceVariant,
            surfaceContainerLowest = scheme.surfaceContainerLow,
            surfaceContainerLow = scheme.surfaceContainerLow,
            surfaceContainer = scheme.surfaceContainer,
            surfaceContainerHigh = scheme.surfaceContainerHigh,
            surfaceContainerHighest = scheme.surfaceContainerHigh,
            outline = scheme.outlineVariant,
            outlineVariant = scheme.outlineVariant,
            error = DarkError,
            onError = Color.White,
            scrim = DarkScrim,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = LightOnPrimary,
            secondary = lightSecondary,
            onSecondary = accent,
            background = scheme.background,
            onBackground = LightOnBackground,
            surface = scheme.background,
            onSurface = LightOnBackground,
            surfaceVariant = scheme.surfaceContainer,
            onSurfaceVariant = scheme.onSurfaceVariant,
            surfaceContainerLowest = scheme.background,
            surfaceContainerLow = scheme.surfaceContainerLow,
            surfaceContainer = scheme.surfaceContainer,
            surfaceContainerHigh = scheme.surfaceContainerHigh,
            surfaceContainerHighest = scheme.surfaceContainerHigh,
            outline = scheme.outlineVariant,
            outlineVariant = scheme.outlineVariant,
            error = LightError,
            onError = Color.White,
            scrim = LightScrim,
        )
    }

    CompositionLocalProvider(
        LocalPientIsDark provides darkTheme,
        LocalPientUserBubble provides if (darkTheme) darkUserBubble else lightUserBubble,
    ) {
        // 字体设置全局生效（2026-08-31）：字体样式 → 全局 FontFamily；字体大小 → 全局缩放
        // （factor = 设置值 / 14sp 基准）。字体解析涉及 assets/filesDir 文件 IO，按设置键 remember 缓存。
        val context = LocalContext.current
        val fontFamily = remember(
            SettingsStore.fontSource,
            SettingsStore.builtinFontName,
            SettingsStore.customFontPath,
        ) { resolveFontFamily(context) }
        val scale = SettingsStore.fontSize / 14f
        val typography = remember(fontFamily, scale) {
            PientTypography.withFontFamily(fontFamily).scaled(scale)
        }
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),   // 圆角两级：12dp
                medium = RoundedCornerShape(16.dp),  //          16dp
                large = RoundedCornerShape(20.dp),
                extraLarge = RoundedCornerShape(28.dp),
            ),
        ) {
            // ★ 关键修复（2026-08-27）：M3 1.4.0 的 MaterialTheme 不再提供
            // LocalContentColor（实测反编译 MaterialThemeKt 确认），所有未显式
            // 指定 color 的 Text 会回落到默认值 Color.Black —— 暗色模式下黑字
            // 黑底不可见（用户实测：抽屉"项目："标题、设置列表、技能/插件顶栏、
            // 弹窗标题等）。在此统一提供：默认文字色 = onBackground，随明暗联动。
            CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
                content()
            }
        }
    }
}
