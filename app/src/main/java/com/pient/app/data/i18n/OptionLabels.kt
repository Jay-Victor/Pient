package com.pient.app.data.i18n

/**
 * 「持久化选项名 → 显示文案」映射层。
 *
 * 为什么单独一层：主题方案与内置字体的**名字就是存储键**（`SettingsStore` 按 name 存/读、
 * 也参与 `firstOrNull { it.name == saved }` 的等值判定），所以这些字面量不能直接被
 * `L.xxx` 替换掉；而它们又要随界面语言显示 —— 于是在**渲染点**过这一层：
 * 存储侧继续用稳定的中文标识（老配置不受影响），显示侧取语言表。
 *
 * 未收录的名字（如用户导入的自定义字体）原样返回。
 */
object OptionLabels {

    /** 主题方案名（`DarkSchemes` / `LightSchemes` 的 name） */
    fun scheme(name: String): String = when (name) {
        "墨黑" -> L.theme.schemeInkBlack
        "纯黑" -> L.theme.schemePureBlack
        "深蓝" -> L.theme.schemeDeepBlue
        "石墨" -> L.theme.schemeGraphite
        "暗紫" -> L.theme.schemeDarkPurple
        "亮白" -> L.theme.schemeBrightWhite
        "暖白" -> L.theme.schemeWarmWhite
        "冷灰" -> L.theme.schemeCoolGray
        "薄荷" -> L.theme.schemeMint
        "亚麻" -> L.theme.schemeLinen
        else -> name
    }

    /** 主题方案说明（`ThemeScheme.description`） */
    fun schemeDesc(name: String): String = when (name) {
        "墨黑" -> L.theme.schemeDescInkBlack
        "纯黑" -> L.theme.schemeDescPureBlack
        "深蓝" -> L.theme.schemeDescDeepBlue
        "石墨" -> L.theme.schemeDescGraphite
        "暗紫" -> L.theme.schemeDescDarkPurple
        "亮白" -> L.theme.schemeDescBrightWhite
        "暖白" -> L.theme.schemeDescWarmWhite
        "冷灰" -> L.theme.schemeDescCoolGray
        "薄荷" -> L.theme.schemeDescMint
        "亚麻" -> L.theme.schemeDescLinen
        else -> ""
    }

    /** 内置字体名（`BuiltinFonts` 的 name；自定义字体传文件名，原样返回） */
    fun font(name: String): String = when (name) {
        "默认字体" -> L.theme.fontDefault
        "思源黑体" -> L.theme.fontSourceHanSans
        "思源宋体" -> L.theme.fontSourceHanSerif
        "霞鹜文楷" -> L.theme.fontLxgwWenKai
        "无衬线体" -> L.theme.fontSourceSansPro
        "JetBrains Mono" -> L.theme.fontJetBrainsMono
        else -> name
    }

    /** 自定义主题色名的显示（`AccentPreset.name`：色块的 contentDescription） */
    fun accent(name: String): String = when (name) {
        "默认蓝" -> L.theme.accentDefaultBlue
        "绿" -> L.theme.accentGreen
        "紫" -> L.theme.accentPurple
        "橙" -> L.theme.accentOrange
        "青" -> L.theme.accentCyan
        "红" -> L.theme.accentRed
        "粉" -> L.theme.accentPink
        "黄" -> L.theme.accentYellow
        "珊瑚" -> L.theme.accentCoral
        "靛" -> L.theme.accentIndigo
        "青绿" -> L.theme.accentTeal
        "灰" -> L.theme.accentGray
        else -> name
    }

    /** apt 镜像名（`AptMirror.name` 是存储值） */
    fun aptMirror(name: String): String = when (name) {
        "Ubuntu 官方" -> L.env.mirrorUbuntuOfficial
        "清华 TUNA" -> L.env.mirrorTsinghuaTuna
        "阿里云" -> L.env.mirrorAliyun
        "中科大 USTC" -> L.env.mirrorUstc
        "网易 163" -> L.env.mirrorNetEase
        else -> name
    }

    /** 环境组件分类标题（`ComponentGroups.*` 是分类标识，同时用作等值判定） */
    fun envGroup(id: String): String = when (id) {
        "Node.js" -> "Node.js"
        "pi 搜索依赖" -> L.env.groupPiSearch
        "Python" -> "Python"
        "Go" -> "Go"
        "Java" -> "Java"
        "Rust" -> "Rust"
        "SSH 与远程" -> L.env.groupSsh
        "基础与开发" -> L.env.groupBase
        else -> id
    }
}
