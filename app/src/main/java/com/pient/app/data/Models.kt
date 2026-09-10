package com.pient.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// ─────────────────────────────────────────────────────────────
// 全局设置（跨页面即时生效；UI 原型阶段以内存状态承载）
// 注（2026-08-27 用户决策）：已全面弃用玻璃拟态，容器统一为
// 普通材质（PientPanel：纯色面板 + hairline 边框）。
// ─────────────────────────────────────────────────────────────
enum class DensityLevel { DEFAULT, COMPACT }

enum class BubbleStyle { FLAT, BUBBLE }

enum class ThemeMode { DARK, LIGHT, SYSTEM }

/**
 * 抽屉展出方式：
 * - SLIDE 水平滑出（默认，浮层 + 遮罩；平板端自动用压缩滑出）
 * - PERSPECTIVE 3D 透视（仅手机；平板端自动用压缩滑出）
 * - PUSH 推动展开（主内容整体被推向另一侧，与侧栏同时移动，不回排版面）
 */
enum class DrawerMode { SLIDE, PERSPECTIVE, PUSH }

/** 自定义背景媒体类型（背景设置标签：分段控制器两段） */
enum class BackgroundMediaType { IMAGE, VIDEO }

/** 视频背景画面裁剪模式（视频裁剪对话框：画面裁剪分段） */
enum class VideoCropMode(val label: String) {
    ORIGINAL("原始"),
    SQUARE("1:1"),
    RATIO_16_9("16:9"),
    RATIO_9_16("9:16"),
}

/** 字体来源（字体设置标签：分段控制器两段） */
enum class FontSource { BUILTIN, CUSTOM }

/** 字体大小滑轨范围（sp） */
const val FONT_SIZE_MIN = 12f
const val FONT_SIZE_MAX = 24f

/** 内置字体来源类型 */
enum class BuiltinFontKind { SYSTEM_FAMILY, SYSTEM_FILE, RES, ASSET }

/** 内置字体选项（名称 + 来源；解析失败回退 fallbackName 或默认字体） */
data class BuiltinFontOption(
    val name: String,
    val kind: BuiltinFontKind,
    val familyName: String = "",   // SYSTEM_FAMILY：系统字体族名；SYSTEM_FILE/RES/ASSET：解析失败回退族名
    val filePath: String = "",     // SYSTEM_FILE：系统字体文件路径
    val resId: Int = 0,            // RES：应用资源字体
    val assetPath: String = "",    // ASSET：assets 内字体路径
)

/** 内置字体列表（2026-08-31 用户定名：默认字体/思源黑体/思源宋体/霞鹜文楷/无衬线体/JetBrains Mono） */
val BuiltinFonts = listOf(
    BuiltinFontOption("默认字体", BuiltinFontKind.SYSTEM_FAMILY, familyName = "sans-serif"),
    BuiltinFontOption("思源黑体", BuiltinFontKind.SYSTEM_FILE, familyName = "sans-serif", filePath = "/system/fonts/NotoSansCJK-Regular.ttc"),
    BuiltinFontOption("思源宋体", BuiltinFontKind.SYSTEM_FILE, familyName = "serif", filePath = "/system/fonts/NotoSerifCJK-Regular.ttc"),
    BuiltinFontOption("霞鹜文楷", BuiltinFontKind.ASSET, familyName = "cursive", assetPath = "fonts/lxgw_wenkai.ttf"),
    BuiltinFontOption("无衬线体", BuiltinFontKind.SYSTEM_FILE, familyName = "sans-serif", filePath = "/system/fonts/SourceSansPro-Regular.ttf"),
    BuiltinFontOption("JetBrains Mono", BuiltinFontKind.RES, familyName = "monospace", resId = com.pient.app.R.font.jetbrains_mono),
)

object SettingsStore {
    var themeMode by mutableStateOf(ThemeMode.LIGHT)   // 2026-09-01：首启默认亮色（用户定）
    var accent by mutableStateOf(AccentPresets[0])
    var darkScheme by mutableStateOf(DarkSchemes[0])
    var lightScheme by mutableStateOf(LightSchemes[0])
    var density by mutableStateOf(DensityLevel.DEFAULT)
    var bubbleStyle by mutableStateOf(BubbleStyle.FLAT)
    var language by mutableStateOf("zh-CN")
    var drawerMode by mutableStateOf(DrawerMode.SLIDE)

    // ── 自定义主题色（2026-09-01）：开关 + 色相（0..360）；开启时覆盖 12 预设色作为 accent ──
    var customAccentEnabled by mutableStateOf(false)
    var customAccentHue by mutableStateOf(215f)   // 默认蓝 hue ≈ 215

    // ── 背景设置（2026-08-31）：自定义背景（图片/视频各持一份 URI）+ 背景效果 ──
    var backgroundMediaType by mutableStateOf(BackgroundMediaType.IMAGE)
    var backgroundImageUri by mutableStateOf<String?>(null)
    var backgroundVideoUri by mutableStateOf<String?>(null)
    var backgroundBlurEnabled by mutableStateOf(false)
    var backgroundBlurRadius by mutableStateOf(10f)   // 1..25
    var backgroundBrightness by mutableStateOf(1f)    // 0.1..1.5

    // ── 视频背景播放设置（2026-08-31）：静音/循环/裁剪区间（秒；null = 未裁剪）/画面裁剪模式/播放倍速 ──
    var videoBackgroundMuted by mutableStateOf(true)
    var videoBackgroundLoop by mutableStateOf(true)
    var videoTrimStartSec by mutableStateOf<Float?>(null)
    var videoTrimEndSec by mutableStateOf<Float?>(null)
    var videoCropMode by mutableStateOf(VideoCropMode.ORIGINAL)
    var videoPlaybackSpeed by mutableStateOf(1f)   // 0.5..2.0

    // ── 字体设置（2026-08-31）：字体样式（内置/自定义）+ 字体大小 ──
    var fontSource by mutableStateOf(FontSource.BUILTIN)
    var builtinFontName by mutableStateOf(BuiltinFonts[0].name)   // 存储键 = 选项名
    var customFontPath by mutableStateOf<String?>(null)   // filesDir/fonts/ 下文件名
    var customFontLabel by mutableStateOf<String?>(null)  // 导入文件原始名（弹窗显示名）
    var fontSize by mutableStateOf(14f)                   // 12..24 sp

    /** 当前主题下生效的主色：自定义开启时按色相生成暗/亮双变体，否则用预设 */
    fun accentFor(dark: Boolean): Color =
        if (customAccentEnabled) {
            if (dark) Color.hsv(customAccentHue, 0.65f, 1f)
            else Color.hsv(customAccentHue, 0.9f, 0.7f)
        } else if (dark) accent.dark else accent.light

    /** 当前明暗下生效的界面主题方案 */
    fun schemeFor(dark: Boolean): ThemeScheme = if (dark) darkScheme else lightScheme

    /** 启动时恢复持久化主题（MainActivity 在 setContent 前调用，避免首帧闪错主题） */
    fun load(androidCtx: android.content.Context) {
        val p = androidCtx.getSharedPreferences("pient_prefs", android.content.Context.MODE_PRIVATE)
        themeMode = runCatching {
            ThemeMode.valueOf(p.getString("theme_mode", "LIGHT") ?: "LIGHT")
        }.getOrDefault(ThemeMode.LIGHT)
        val saved = p.getInt("accent", -1)
        accent = AccentPresets.firstOrNull { it.dark.toArgb() == saved } ?: AccentPresets[0]
        val savedDarkScheme = p.getString("dark_scheme", null)
        darkScheme = DarkSchemes.firstOrNull { it.name == savedDarkScheme } ?: DarkSchemes[0]
        val savedLightScheme = p.getString("light_scheme", null)
        lightScheme = LightSchemes.firstOrNull { it.name == savedLightScheme } ?: LightSchemes[0]
        drawerMode = runCatching {
            DrawerMode.valueOf(p.getString("drawer_mode", "SLIDE") ?: "SLIDE")
        }.getOrDefault(DrawerMode.SLIDE)
        customAccentEnabled = p.getBoolean("custom_accent_enabled", false)
        customAccentHue = p.getFloat("custom_accent_hue", 215f).coerceIn(0f, 360f)
        backgroundMediaType = runCatching {
            BackgroundMediaType.valueOf(p.getString("background_media_type", "IMAGE") ?: "IMAGE")
        }.getOrDefault(BackgroundMediaType.IMAGE)
        backgroundImageUri = p.getString("background_image_uri", null)
        backgroundVideoUri = p.getString("background_video_uri", null)
        backgroundBlurEnabled = p.getBoolean("background_blur", false)
        backgroundBlurRadius = p.getFloat("background_blur_radius", 10f)
        backgroundBrightness = p.getFloat("background_brightness", 1f)
        videoBackgroundMuted = p.getBoolean("video_background_muted", true)
        videoBackgroundLoop = p.getBoolean("video_background_loop", true)
        videoTrimStartSec = if (p.contains("video_trim_start")) p.getFloat("video_trim_start", 0f) else null
        videoTrimEndSec = if (p.contains("video_trim_end")) p.getFloat("video_trim_end", 0f) else null
        videoCropMode = runCatching {
            VideoCropMode.valueOf(p.getString("video_crop_mode", "ORIGINAL") ?: "ORIGINAL")
        }.getOrDefault(VideoCropMode.ORIGINAL)
        videoPlaybackSpeed = p.getFloat("video_playback_speed", 1f).coerceIn(0.5f, 2f)
        fontSource = runCatching {
            FontSource.valueOf(p.getString("font_source", "BUILTIN") ?: "BUILTIN")
        }.getOrDefault(FontSource.BUILTIN)
        val savedBuiltin = p.getString("builtin_font", null)
        builtinFontName = BuiltinFonts.firstOrNull { it.name == savedBuiltin }?.name
            ?: BuiltinFonts[0].name
        customFontPath = p.getString("custom_font_path", null)
        customFontLabel = p.getString("custom_font_label", null)
        fontSize = p.getFloat("font_size", 14f).coerceIn(FONT_SIZE_MIN, FONT_SIZE_MAX)
    }

    /** 保存当前主题选择（外观模式 + 主题色 + 自定义主题色 + 深浅界面方案），重启后保持 */
    fun saveTheme(androidCtx: android.content.Context) {
        androidCtx.getSharedPreferences("pient_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("theme_mode", themeMode.name)
            .putInt("accent", accent.dark.toArgb())
            .putString("dark_scheme", darkScheme.name)
            .putString("light_scheme", lightScheme.name)
            .putBoolean("custom_accent_enabled", customAccentEnabled)
            .putFloat("custom_accent_hue", customAccentHue)
            .apply()
    }

    /** 保存抽屉展出方式（行为设置），重启后保持 */
    fun saveDrawerMode(androidCtx: android.content.Context) {
        androidCtx.getSharedPreferences("pient_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("drawer_mode", drawerMode.name)
            .apply()
    }

    /** 保存背景设置（媒体类型/图片/视频/模糊/亮度/视频播放），重启后保持 */
    fun saveBackground(androidCtx: android.content.Context) {
        val e = androidCtx.getSharedPreferences("pient_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("background_media_type", backgroundMediaType.name)
            .putBoolean("background_blur", backgroundBlurEnabled)
            .putFloat("background_blur_radius", backgroundBlurRadius)
            .putFloat("background_brightness", backgroundBrightness)
            .putBoolean("video_background_muted", videoBackgroundMuted)
            .putBoolean("video_background_loop", videoBackgroundLoop)
            .putString("video_crop_mode", videoCropMode.name)
            .putFloat("video_playback_speed", videoPlaybackSpeed)
        backgroundImageUri?.let { e.putString("background_image_uri", it) } ?: e.remove("background_image_uri")
        backgroundVideoUri?.let { e.putString("background_video_uri", it) } ?: e.remove("background_video_uri")
        videoTrimStartSec?.let { e.putFloat("video_trim_start", it) } ?: e.remove("video_trim_start")
        videoTrimEndSec?.let { e.putFloat("video_trim_end", it) } ?: e.remove("video_trim_end")
        e.apply()
    }

    /** 保存字体设置（来源/内置字体/自定义字体文件/字号），重启后保持 */
    fun saveFont(androidCtx: android.content.Context) {
        val e = androidCtx.getSharedPreferences("pient_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("font_source", fontSource.name)
            .putString("builtin_font", builtinFontName)
            .putFloat("font_size", fontSize)
        customFontPath?.let { e.putString("custom_font_path", it) } ?: e.remove("custom_font_path")
        customFontLabel?.let { e.putString("custom_font_label", it) } ?: e.remove("custom_font_label")
        e.apply()
    }
}

/**
 * 主题色预设色板（默认蓝 + 备选）。
 * 每个预设含暗/亮双变体：暗色面板用高亮变体，亮色面板用深色变体，
 * 保证 onPrimary 白字在两种模式下均满足对比度（设计计划附录 A 联动原则）。
 */
data class AccentPreset(
    val dark: Color,
    val light: Color,
    val name: String,
)

val AccentPresets = listOf(
    AccentPreset(Color(0xFF58A6FF), Color(0xFF0969DA), "默认蓝"),
    AccentPreset(Color(0xFF3FB950), Color(0xFF1A7F37), "绿"),
    AccentPreset(Color(0xFFA371F7), Color(0xFF8250DF), "紫"),
    AccentPreset(Color(0xFFF0883E), Color(0xFF953800), "橙"),
    AccentPreset(Color(0xFF39C5CF), Color(0xFF1B7C83), "青"),
    AccentPreset(Color(0xFFF85149), Color(0xFFCF222E), "红"),
    AccentPreset(Color(0xFFF778BA), Color(0xFFBF3989), "粉"),
    AccentPreset(Color(0xFFE3B341), Color(0xFF9A6700), "黄"),
    AccentPreset(Color(0xFFFFA657), Color(0xFFC4432B), "珊瑚"),
    AccentPreset(Color(0xFFA5B4FC), Color(0xFF6366F1), "靛"),
    AccentPreset(Color(0xFF2DD4BF), Color(0xFF0F766E), "青绿"),
    AccentPreset(Color(0xFF8B949E), Color(0xFF57606A), "灰"),
)

/**
 * 界面主题方案（2026-08-31）：底色/表面色套装，浅色与深色各独立一套选择。
 * 仅替换背景类令牌；文字色（onBackground/onSurface）、error/scrim 不随方案变化。
 * surfaceContainerLowest 沿用映射：暗色 = surfaceContainerLow，亮色 = background。
 */
data class ThemeScheme(
    val name: String,
    val description: String,
    val background: Color,            // 页面底
    val surfaceContainerLow: Color,   // 卡片底
    val surfaceContainer: Color,      // 面板/弹层
    val surfaceContainerHigh: Color,  // 悬浮态
    val outlineVariant: Color,        // 描边
    val onSurfaceVariant: Color,      // 次要文字
)

val DarkSchemes = listOf(
    ThemeScheme("墨黑", "GitHub 暗色系，蓝调墨黑底色", Color(0xFF0D1117), Color(0xFF010409), Color(0xFF161B22), Color(0xFF21262D), Color(0xFF30363D), Color(0xFF8B949E)),
    ThemeScheme("纯黑", "OLED 纯黑，极致对比与省电", Color(0xFF000000), Color(0xFF050505), Color(0xFF0F0F0F), Color(0xFF1A1A1A), Color(0xFF262626), Color(0xFF8B949E)),
    ThemeScheme("深蓝", "深海军蓝调，柔和护眼", Color(0xFF0A0E1C), Color(0xFF050811), Color(0xFF0F1526), Color(0xFF1B2239), Color(0xFF2A3350), Color(0xFF8B949E)),
    ThemeScheme("石墨", "中性石墨灰，均衡无偏色", Color(0xFF1A1D21), Color(0xFF0E1114), Color(0xFF1F2328), Color(0xFF292E34), Color(0xFF383D43), Color(0xFF8B949E)),
    ThemeScheme("暗紫", "低饱和暗紫，夜景氛围", Color(0xFF12101C), Color(0xFF0B0912), Color(0xFF171226), Color(0xFF221B33), Color(0xFF2F2543), Color(0xFF8B949E)),
)

val LightSchemes = listOf(
    ThemeScheme("亮白", "纯净白底，标准亮色系", Color(0xFFFFFFFF), Color(0xFFF6F8FA), Color(0xFFF6F8FA), Color(0xFFEFF2F5), Color(0xFFD0D7DE), Color(0xFF656D76)),
    ThemeScheme("暖白", "米色暖调，久读舒适", Color(0xFFFDFBF6), Color(0xFFF8F3E9), Color(0xFFF8F3E9), Color(0xFFF1EADB), Color(0xFFD8CFBC), Color(0xFF6E675A)),
    ThemeScheme("冷灰", "冷灰底，清爽冷静", Color(0xFFF4F6F9), Color(0xFFEDF1F5), Color(0xFFEDF1F5), Color(0xFFE3E9EF), Color(0xFFC9D2DC), Color(0xFF5D6773)),
    ThemeScheme("薄荷", "微绿清新，护眼柔和", Color(0xFFF3FAF7), Color(0xFFEAF4EF), Color(0xFFEAF4EF), Color(0xFFE0EEE6), Color(0xFFC7DCD2), Color(0xFF5D6E66)),
    ThemeScheme("亚麻", "亚麻米黄，纸张质感", Color(0xFFFBF7EC), Color(0xFFF5EEDC), Color(0xFFF5EEDC), Color(0xFFEDE3CB), Color(0xFFD6C9A8), Color(0xFF6E6550)),
)

// ─────────────────────────────────────────────────────────────
// 会话与消息数据模型（会话格式与 pi 官方 session v3 对齐的 UI 投影）
// ─────────────────────────────────────────────────────────────
data class Project(
    val name: String,
    val path: String, // 展示的项目根路径（本地项目 = 绝对路径；SAF 项目 = 解析后的真实路径，解析失败回退 content URI）
    val uri: String? = null, // SAF tree URI（访问文件用；本地项目为 null）
)

data class Session(
    val id: String,
    val title: String,
    val project: String,
    val running: Boolean = false,
    val pinned: Boolean = false,
    /** 最后活动时间（epoch ms；会话记录持久化与侧栏相对时间/时间分组依据，0 = 创建时刻） */
    val updatedAt: Long = 0,
)

// ─────────────────────────────────────────────────────────────
// 侧栏双重时间编码（2026-09-09 对齐 Hermes 桌面端 lib/time.ts + session-date-groups.ts）：
// 行级 = 相对时长标签（coarseElapsed 最粗单位 floor：刚刚 / N分 / N时 / N天）；
// 分组 = 日历桶（4AM 日界；头部最新活动 run 簇无标签，其下依次 今天早些时候 / 昨天 /
// 本周 / 上周 / 本月 / N月 / YYYY年N月，每桶一个分组头，第一个渲染的分组永不贴标签）。
// ─────────────────────────────────────────────────────────────

const val MINUTE_MS = 60_000L
const val HOUR_MS = 3_600_000L
const val DAY_MS = 86_400_000L

/** 人一天不以午夜为界——凌晨活动归前一天晚上（睡眠追踪器同款 4AM 日界） */
private const val DAY_ROLLOVER_HOUR = 4

/**
 * 会话最后活动时间 → 侧栏行相对时间标签（Hermes session-row formatAge 同口径：
 * coarseElapsed 最粗单位、floor 取整；不足 1 分钟一律「刚刚」——秒级粒度不进侧栏）。
 */
fun relativeTimeLabel(updatedAt: Long, now: Long = System.currentTimeMillis()): String {
    val t = if (updatedAt <= 0) now else updatedAt
    val diff = (now - t).coerceAtLeast(0)
    return when {
        diff < MINUTE_MS -> "刚刚"
        diff < HOUR_MS -> "${diff / MINUTE_MS}分"
        diff < DAY_MS -> "${diff / HOUR_MS}时"
        else -> "${diff / DAY_MS}天"
    }
}

/** 时间分组桶类型（Hermes SessionBucketKind 同款） */
enum class SessionBucketKind { TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, MONTH, MONTH_YEAR }

/** 日历桶：key = 唯一分桶键；at = 会话名义日起点（epoch ms，月份标签格式化用） */
data class SessionBucket(val key: String, val kind: SessionBucketKind, val at: Long)

/** 侧栏时间分组输出：label 恒非空（2026-09-09 用户要求头部 run 簇也贴标签） */
data class SessionGroup(val key: String, val label: String, val sessions: List<Session>)

private fun startOfLocalDay(ms: Long): Long {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    c.set(java.util.Calendar.HOUR_OF_DAY, 0)
    c.set(java.util.Calendar.MINUTE, 0)
    c.set(java.util.Calendar.SECOND, 0)
    c.set(java.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** 名义日起点：4AM 日界（周六凌晨 1 点 → 归周五） */
private fun nominalDayStart(ms: Long): Long = startOfLocalDay(ms - DAY_ROLLOVER_HOUR * HOUR_MS)

/** 本地日历周起点（weekStartsOn 用 JS getDay 惯例 0=周日…6=周六；中文环境周一=1） */
private fun startOfLocalWeek(ms: Long, weekStartsOn: Int): Long {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = startOfLocalDay(ms) }
    val jsDow = c.get(java.util.Calendar.DAY_OF_WEEK) - 1 // Calendar 1=周日…7=周六 → JS 0=周日…6=周六
    val back = (jsDow - weekStartsOn + 7) % 7
    c.add(java.util.Calendar.DAY_OF_YEAR, -back)
    return c.timeInMillis
}

/**
 * 粗日历桶（Hermes calendarBucket 同款，周起点默认周一）：今天 → 昨天 → 本周 →
 * 上周 → 本月 → 月 → 月+年。粒度随年龄变粗；空区间不产生桶。
 */
fun sessionBucket(ms: Long, nowMs: Long, weekStartsOn: Int = 1): SessionBucket {
    val nominal = nominalDayStart(ms)
    val todayNominal = nominalDayStart(nowMs)
    val dayDiff = Math.round((todayNominal - nominal).toDouble() / DAY_MS)

    if (dayDiff <= 0) return SessionBucket("today", SessionBucketKind.TODAY, nominal)
    if (dayDiff == 1L) return SessionBucket("yesterday", SessionBucketKind.YESTERDAY, nominal)

    val weekStart = startOfLocalWeek(todayNominal, weekStartsOn)
    if (nominal >= weekStart) return SessionBucket("this-week", SessionBucketKind.THIS_WEEK, nominal)

    val prevWeekStart = java.util.Calendar.getInstance().apply { timeInMillis = weekStart }
        .apply { add(java.util.Calendar.DAY_OF_YEAR, -7) }.timeInMillis
    if (nominal >= prevWeekStart) return SessionBucket("last-week", SessionBucketKind.LAST_WEEK, nominal)

    val d = java.util.Calendar.getInstance().apply { timeInMillis = nominal }
    val now = java.util.Calendar.getInstance().apply { timeInMillis = todayNominal }
    val sameYear = d.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR)
    if (sameYear && d.get(java.util.Calendar.MONTH) == now.get(java.util.Calendar.MONTH)) {
        return SessionBucket("this-month", SessionBucketKind.THIS_MONTH, nominal)
    }

    val ym = "${d.get(java.util.Calendar.YEAR)}-${d.get(java.util.Calendar.MONTH)}"
    return if (sameYear) SessionBucket("m-$ym", SessionBucketKind.MONTH, nominal)
    else SessionBucket("my-$ym", SessionBucketKind.MONTH_YEAR, nominal)
}

private val MONTH_NAMES = arrayOf(
    "一月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月"
)

/** 桶的本地化分组标签（Hermes sessionBucketLabel 同款；固定相对文案 + 中文月份名） */
fun sessionBucketLabel(bucket: SessionBucket): String = when (bucket.kind) {
    SessionBucketKind.TODAY -> "今天早些时候"
    SessionBucketKind.YESTERDAY -> "昨天"
    SessionBucketKind.THIS_WEEK -> "本周"
    SessionBucketKind.LAST_WEEK -> "上周"
    SessionBucketKind.THIS_MONTH -> "本月"
    SessionBucketKind.MONTH -> {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = bucket.at }
        MONTH_NAMES[c.get(java.util.Calendar.MONTH)]
    }
    SessionBucketKind.MONTH_YEAR -> {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = bucket.at }
        "${c.get(java.util.Calendar.YEAR)}年${MONTH_NAMES[c.get(java.util.Calendar.MONTH)]}"
    }
}

private const val TARGET_HEAD_SESSIONS = 5
private const val MIN_RUN_BREAK_MS = 30 * MINUTE_MS
private const val MAX_RUN_GAP_MS = 8 * HOUR_MS

/**
 * 头部无标签 run 簇切割点（Hermes headRunCutoffMs 同款；times 降序）：
 * 候选切点 = ≥30min 的活动间隙（>8h 的间隙强制断 run 并停止向后搜索），
 * 选「头部会话数（log 尺度）最接近 5」的切点；run 结束处与下方会话同日历桶时
 * 头部溶解。返回头内最老时间戳；Long.MIN_VALUE = 全列表一个 run（无组头）；
 * Long.MAX_VALUE = 无头簇（日历桶全接管）。
 */
private fun headRunCutoffMs(times: List<Long>, nowMs: Long): Long {
    var bestIdx = -1
    var bestScore = Double.POSITIVE_INFINITY
    var runEnded = false
    for (i in 1 until times.size) {
        val gap = times[i - 1] - times[i]
        val endsRun = gap > MAX_RUN_GAP_MS
        if (gap >= MIN_RUN_BREAK_MS || endsRun) {
            val score = Math.abs(Math.log(i.toDouble() / TARGET_HEAD_SESSIONS))
            if (score < bestScore) {
                bestScore = score
                bestIdx = i
                runEnded = endsRun
            }
        }
        if (endsRun) break
    }
    if (bestIdx == -1) return Long.MIN_VALUE
    if (runEnded) {
        val headBucket = sessionBucket(times[0], nowMs)
        val belowBucket = sessionBucket(times[bestIdx], nowMs)
        if (headBucket.key == belowBucket.key) return Long.MAX_VALUE
    }
    return times[bestIdx - 1]
}

/**
 * 头簇标签：头簇是「现在」而非「早些时候」——today 桶显示「今天」，其余桶按常规
 * 日历标签（跨午夜 run 的最新会话归昨天时显示「昨天」，保证语义准确）。
 */
fun sessionBucketHeadLabel(bucket: SessionBucket): String =
    if (bucket.kind == SessionBucketKind.TODAY) "今天" else sessionBucketLabel(bucket)

/**
 * 侧栏时间分组：列表按最后活动降序；头部 run 簇贴其最新会话所属日历桶的标签
 * （2026-09-09 用户要求：最上方会话上方也显示分组标签，如「今天」）；其下按
 * 日历桶分组、每桶贴标签（不再保留 Hermes「第一个渲染的分组永不贴标签」——
 * 该规则与头部贴标签矛盾）。置顶会话不参与（置顶段独立）。
 */
fun groupSessionsByRecency(
    unpinned: List<Session>,
    nowMs: Long = System.currentTimeMillis(),
): List<SessionGroup> {
    val sorted = unpinned.sortedByDescending { if (it.updatedAt <= 0) nowMs else it.updatedAt }
    if (sorted.isEmpty()) return emptyList()
    val times = sorted.map { if (it.updatedAt <= 0) nowMs else it.updatedAt }
    val cutoff = headRunCutoffMs(times, nowMs)

    val groups = mutableListOf<SessionGroup>()
    var lastKey: String? = null
    val emitted = mutableSetOf<String>()

    for ((idx, s) in sorted.withIndex()) {
        val ms = times[idx]
        if (ms >= cutoff) {
            lastKey = "__recent__"
            val tail = groups.lastOrNull()
            if (tail != null && tail.key == "__recent__") {
                groups[groups.lastIndex] = tail.copy(sessions = tail.sessions + s)
            } else {
                val headLabel = sessionBucketHeadLabel(sessionBucket(times[0], nowMs))
                groups += SessionGroup("__recent__", headLabel, listOf(s))
            }
            continue
        }
        val bucket = sessionBucket(ms, nowMs)
        if (bucket.key != lastKey) {
            lastKey = bucket.key
            val alreadyEmitted = emitted.contains(bucket.key)
            emitted.add(bucket.key)
            if (!alreadyEmitted) {
                groups += SessionGroup(bucket.key, sessionBucketLabel(bucket), emptyList())
            }
        }
        val tail = groups.last()
        groups[groups.lastIndex] = tail.copy(sessions = tail.sessions + s)
    }
    return groups
}

/** 附件类型（chip 图标展示与提交语义区分；2026-09-09 移除「文件夹」项，菜单四项） */
enum class AttachmentKind(val emoji: String) {
    IMAGE("🖼 "), FILE("📎 "), URL("🔗 ")
}

data class Attachment(
    val name: String,
    val kind: AttachmentKind = AttachmentKind.FILE,
    /** 真实位置：本地文件绝对路径（照片/拍照/文件落盘后）；null = 无实体 */
    val path: String? = null,
)

sealed class Msg {
    data class User(val text: String, val attachments: List<Attachment> = emptyList()) : Msg()

    data class Assistant(
        val markdown: String,
        val usage: Usage? = null,
        val model: String? = null,   // 消息所用模型（pi-web 助手消息头部模型标签）
        val error: Boolean = false,  // 请求失败提示（不进 API 上下文，历史重建时跳过）
    ) : Msg()

    data class Thinking(
        val level: String, // off/minimal/low/medium/high/xhigh
        val text: String,
    ) : Msg()

    data class ToolCall(
        val name: String,
        val params: String,
        val status: ToolStatus = ToolStatus.DONE,
        val detail: String? = null,
    ) : Msg()

    data class ToolResult(
        val toolName: String,
        val preview: String,
        val full: String? = null,
    ) : Msg()

    data class Compaction(
        val tokensBefore: Int,
        val saved: Int,
        val summary: String,
    ) : Msg()
}

/**
 * 会话树节点（P12 /tree 画布页数据源；原型由 mock 提供，
 * 接入 pi 运行时后由 get_state 会话树 / SDK navigateTree() 驱动）。
 * 节点 = 一条用户消息；exchange = 该节点代表的那次对话（用户消息 + 后续至
 * 下一条用户消息前的全部条目，含 AI 回答）。
 */
data class SessionTreeNode(
    val id: String,
    val userText: String,
    val exchange: List<Msg>,
    val children: List<SessionTreeNode> = emptyList(),
    val branchLabel: String? = null,
    val active: Boolean = false,
) {
    /** 是否存在分支（顶栏分支键指示逻辑，pi-web hasBranch 同款：顶层 >1 或任一节点 children >1） */
    fun hasBranches(): Boolean = children.size > 1 || children.any { it.hasBranches() }
}

enum class ToolStatus { RUNNING, DONE, FAILED }

data class Usage(
    val inTokens: Int,
    val outTokens: Int,
    val cacheTokens: Int,
    val costUsd: Double,
)

// ─────────────────────────────────────────────────────────────
// 模型（分组折叠列表数据源 = get_available_models 的 UI 投影）
// ─────────────────────────────────────────────────────────────
data class AiModel(
    val id: String,      // provider/modelId
    val name: String,
    val provider: String,
)

/** thinking 五档 ↔ pi thinking 级别（minimal…xhigh；max 不暴露，设计计划 3.4.1） */
enum class ThinkingLevel(val piValue: String, val label: String) {
    MINIMAL("minimal", "最低"),
    LOW("low", "低"),
    MEDIUM("medium", "中"),
    HIGH("high", "高"),
    XHIGH("xhigh", "最高");

    companion object {
        fun fromPi(v: String): ThinkingLevel =
            entries.firstOrNull { it.piValue == v } ?: MEDIUM
    }
}

// ─────────────────────────────────────────────────────────────
// 技能 / 插件
// ─────────────────────────────────────────────────────────────
data class SkillItem(
    val name: String,
    val desc: String,
    val enabled: Boolean,
    val global: Boolean = true,
    val skillMd: String? = null,   // SKILL.md 文件内容（mock；null = 无此文件）
    val fileTree: String? = null,  // 技能目录 ASCII 树（mock；null = 无目录信息）
)

/** 插件包状态（pi-web PluginPackageInfo.status） */
enum class PluginStatus { LOADED, INSTALLED, MISSING, DISABLED }

/** 包内资源类型（pi package.json pi 清单四类） */
enum class PluginResourceKind { EXTENSION, SKILL, PROMPT, THEME }

/** 包内单个资源（pi-web PluginResourceInfo 投影，多一个启用位） */
data class PluginResource(
    val kind: PluginResourceKind,
    val name: String,
    val relativePath: String,
    val enabled: Boolean = true,
)

data class PluginItem(
    val name: String,
    val source: String,
    val enabled: Boolean,
    val global: Boolean = true,
    val desc: String = "",                    // package.json description（mock）
    val readmeMd: String? = null,             // README.md 文件内容（mock；null = 无此文件）
    val version: String? = null,              // 已安装版本（mock；null = 未知）
    val configuredVersion: String? = null,    // 已配置版本/pinned ref（mock；null = 未配置）
    val status: PluginStatus = PluginStatus.LOADED,  // 包级状态（enabled=false 时显示「已禁用」）
    val resources: List<PluginResource> = emptyList(),  // 已解析资源（mock）
)

// ─────────────────────────────────────────────────────────────
// 文件树（pi-web FileExplorer 的 TreeNode 投影，懒加载语义）
// ─────────────────────────────────────────────────────────────
class FileNode(
    val name: String,
    val isDir: Boolean,
    val content: String? = null,          // 文本文件内容（mock）
    val imageHint: String? = null,        // 图片类文件占位提示
    val children: List<FileNode> = emptyList(),
    val size: Long = 0L,                  // 文件大小（字节；排序用 mock）
    val modifiedAt: Long = 0L,            // 最后修改时间（epoch ms；排序用 mock）
    val source: String? = null,           // 真实位置（本地绝对路径 / SAF 文档 URI）；null = mock 节点
) {
    val ext: String
        get() = if (isDir) "" else name.substringAfterLast('.', "")

    // 注意：不要给 FileNode 实现 equals/hashCode——fileTreeRoot 是 mutableStateOf，
    // 结构相等会让 refreshFileTree 的新树根与旧树根「相等」而静默跳过状态更新，
    // 导致创建/删除文件后树不刷新（2026-09-03 实测，曾加 equals 后踩坑）。
    // 同一文件判定一律显式按 name + source 比较（见 ChatState.openFile）。
}

// ─────────────────────────────────────────────────────────────
// 终端（多会话，Ubuntu 24.04 ARM64 rootfs —— 唯一终端环境）
// ─────────────────────────────────────────────────────────────
data class TerminalLine(
    val text: String,
    val kind: TerminalLineKind = TerminalLineKind.OUTPUT,
)

enum class TerminalLineKind { COMMAND, OUTPUT, PROMPT, BANNER, SLOGAN }

data class TerminalSession(
    val id: Int,
    val name: String,
    val lines: androidx.compose.runtime.snapshots.SnapshotStateList<TerminalLine> =
        androidx.compose.runtime.mutableStateListOf(),
)
