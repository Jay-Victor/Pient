package com.pient.app.data.i18n

/**
 * 界面文案总表（自研消息表，机制对齐 pi-web `lib/i18n`）。
 *
 * 用法：在任意位置写 `L.<组>.<条目>`，例如 `Text(L.settings.languageTitle)`。
 * - 组 = 文案归属的功能域（common/settings/chat/…），每组一张 `Strings<组>.kt`（接口 + Zh/En 实现）。
 * - 「漏翻」由接口在编译期拦住：新增条目必须三处齐全（接口 + Zh + En），否则编译不过。
 * - 取词是**快照感知**的：[L] 在 getter 里读 `SettingsStore.language`（mutableStateOf），
 *   因此组合期读它会自动登记依赖 —— 切语言只需改这个状态，界面即重组，无需重建 Activity。
 */
interface Strings {
    val common: CommonStrings
    val settings: SettingsStrings
    val chat: ChatStrings
    val canvas: CanvasStrings
    val session: SessionStrings
    val files: FilesStrings
    val terminal: TerminalStrings
    val skills: SkillsStrings
    val plugins: PluginsStrings
    val models: ModelsStrings
    val perm: PermStrings
    val project: ProjectStrings
    val env: EnvStrings
    val onboarding: OnboardingStrings
    val theme: ThemeStrings
    val runtime: RuntimeStrings
}

object ZhStrings : Strings {
    override val common = ZhCommon
    override val settings = ZhSettings
    override val chat = ZhChat
    override val canvas = ZhCanvas
    override val session = ZhSession
    override val files = ZhFiles
    override val terminal = ZhTerminal
    override val skills = ZhSkills
    override val plugins = ZhPlugins
    override val models = ZhModels
    override val perm = ZhPerm
    override val project = ZhProject
    override val env = ZhEnv
    override val onboarding = ZhOnboarding
    override val theme = ZhTheme
    override val runtime = ZhRuntime
}

object EnStrings : Strings {
    override val common = EnCommon
    override val settings = EnSettings
    override val chat = EnChat
    override val canvas = EnCanvas
    override val session = EnSession
    override val files = EnFiles
    override val terminal = EnTerminal
    override val skills = EnSkills
    override val plugins = EnPlugins
    override val models = EnModels
    override val perm = EnPerm
    override val project = EnProject
    override val env = EnEnv
    override val onboarding = EnOnboarding
    override val theme = EnTheme
    override val runtime = EnRuntime
}

/** 当前语言包的文案（Compose 与非 Compose 通用；切换语言 = SettingsStore.language 变化 → 重组） */
val L: Strings get() = Languages.current()
