package com.pient.app.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
// Pient 视觉令牌 —— Hermes 桌面端实测同源（GitHub-dark 系）
// 设计计划《Pient UI 设计计划.md》附录 A
// 主色为蓝色（品牌决策 2026-08-26：暗 #58a6ff / 亮 #0969da）
// ─────────────────────────────────────────────────────────────

// ▸ 暗色
val DarkBackground = Color(0xFF0D1117)          // --background
val DarkOnBackground = Color(0xFFE6EDF3)        // --foreground
val DarkSurfaceContainer = Color(0xFF161B22)    // --card / --popover
val DarkSurfaceContainerLow = Color(0xFF010409) // --card(消息面板) / sidebarBackground
val DarkPrimary = Color(0xFF58A6FF)             // --primary
val DarkOnPrimary = Color(0xFFFFFFFF)           // --primary-foreground
val DarkSecondary = Color(0xFF12233D)           // --secondary（蓝系 tint，随主色联动）
val DarkOutlineVariant = Color(0xFF30363D)      // --border
val DarkError = Color(0xFFF85149)               // --destructive
val DarkOnSurfaceVariant = Color(0xFF8B949E)    // --mutedForeground（GitHub-dark muted；#7d8590 在 OLED 上偏暗）
val DarkUserBubble = Color(0xFF0D1F33)          // --userBubble（蓝系 tint）
val DarkWarn = Color(0xFFD29922)                // --warn
val DarkBrandPurple = Color(0xFFA371F7)         // 品牌紫（Codex 滑块同系）
// ▸ 上下文用量分类色（Hermes 上下文卡片语义 → GitHub 色系映射，2026-08-28）
val DarkCategoryRules = Color(0xFF3FB950)       // 规则（Hermes --context-usage-rules 绿）
val DarkCategoryConversation = Color(0xFF39C5CF) // 对话（--context-usage-conversation 青）
// 其余分类复用既有令牌：系统提示词=OnSurfaceVariant(灰)、工具定义=BrandPurple(紫)、技能=Warn(黄)
// （pi 无记忆/子代理上下文分类，橙/蓝无需令牌）
val DarkSurface = Color(0xFF0D1117)
val DarkSurfaceHigh = Color(0xFF21262D)         // 悬浮态
val DarkScrim = Color(0x99000000)

// ▸ 亮色
val LightBackground = Color(0xFFFFFFFF)
val LightOnBackground = Color(0xFF1F2328)
val LightSurfaceContainer = Color(0xFFF6F8FA)
val LightSurfaceContainerLow = Color(0xFFF6F8FA)
val LightPrimary = Color(0xFF0969DA)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightSecondary = Color(0xFFDDF4FF)
val LightOutlineVariant = Color(0xFFD0D7DE)
val LightError = Color(0xFFCF222E)
val LightOnSurfaceVariant = Color(0xFF656D76)
val LightUserBubble = Color(0xFFDBE7F2)
val LightWarn = Color(0xFF9A6700)
val LightBrandPurple = Color(0xFF8250DF)
// ▸ 上下文用量分类色（Hermes 上下文卡片语义 → GitHub 色系映射，2026-08-28）
val LightCategoryRules = Color(0xFF1A7F37)
val LightCategoryConversation = Color(0xFF1B7C83)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceHigh = Color(0xFFEFF2F5)
val LightScrim = Color(0x66000000)

// ▸ 终端 16 色（GitHub 终端配色，暗/亮两套，随主题联动）
val TerminalDark = mapOf(
    "black" to Color(0xFF6E7681),
    "red" to Color(0xFFFF7B72),
    "green" to Color(0xFF3FB950),
    "yellow" to Color(0xFFD29922),
    "blue" to Color(0xFF58A6FF),
    "magenta" to Color(0xFFBC8CFF),
    "cyan" to Color(0xFF39C5CF),
    "white" to Color(0xFFE6EDF3),
    "bg" to Color(0xFF010409),
)
val TerminalLight = mapOf(
    "black" to Color(0xFF57606A),
    "red" to Color(0xFFCF222E),
    "green" to Color(0xFF1A7F37),
    "yellow" to Color(0xFF9A6700),
    "blue" to Color(0xFF0969DA),
    "magenta" to Color(0xFF8250DF),
    "cyan" to Color(0xFF1B7C83),
    "white" to Color(0xFF1F2328),
    "bg" to Color(0xFFF6F8FA),
)
