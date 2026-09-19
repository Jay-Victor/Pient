package com.pient.app.data.i18n

/**
 * 界面文案总表（自研消息表）。
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

/** 繁體中文（台灣用語）文案包 */
object ZhTwStrings : Strings {
    override val common = ZhTwCommon
    override val settings = ZhTwSettings
    override val chat = ZhTwChat
    override val canvas = ZhTwCanvas
    override val session = ZhTwSession
    override val files = ZhTwFiles
    override val terminal = ZhTwTerminal
    override val skills = ZhTwSkills
    override val plugins = ZhTwPlugins
    override val models = ZhTwModels
    override val perm = ZhTwPerm
    override val project = ZhTwProject
    override val env = ZhTwEnv
    override val onboarding = ZhTwOnboarding
    override val theme = ZhTwTheme
    override val runtime = ZhTwRuntime
}

/** 日本語 文案包 */
object JaStrings : Strings {
    override val common = JaCommon
    override val settings = JaSettings
    override val chat = JaChat
    override val canvas = JaCanvas
    override val session = JaSession
    override val files = JaFiles
    override val terminal = JaTerminal
    override val skills = JaSkills
    override val plugins = JaPlugins
    override val models = JaModels
    override val perm = JaPerm
    override val project = JaProject
    override val env = JaEnv
    override val onboarding = JaOnboarding
    override val theme = JaTheme
    override val runtime = JaRuntime
}

/** Español（西班牙语）文案包 */
object EsStrings : Strings {
    override val common = EsCommon
    override val settings = EsSettings
    override val chat = EsChat
    override val canvas = EsCanvas
    override val session = EsSession
    override val files = EsFiles
    override val terminal = EsTerminal
    override val skills = EsSkills
    override val plugins = EsPlugins
    override val models = EsModels
    override val perm = EsPerm
    override val project = EsProject
    override val env = EsEnv
    override val onboarding = EsOnboarding
    override val theme = EsTheme
    override val runtime = EsRuntime
}

/** हिन्दी（印地语）文案包 */
object HiStrings : Strings {
    override val common = HiCommon
    override val settings = HiSettings
    override val chat = HiChat
    override val canvas = HiCanvas
    override val session = HiSession
    override val files = HiFiles
    override val terminal = HiTerminal
    override val skills = HiSkills
    override val plugins = HiPlugins
    override val models = HiModels
    override val perm = HiPerm
    override val project = HiProject
    override val env = HiEnv
    override val onboarding = HiOnboarding
    override val theme = HiTheme
    override val runtime = HiRuntime
}

/** 语言包（Fr）文案包 */
object FrStrings : Strings {
    override val common = FrCommon
    override val settings = FrSettings
    override val chat = FrChat
    override val canvas = FrCanvas
    override val session = FrSession
    override val files = FrFiles
    override val terminal = FrTerminal
    override val skills = FrSkills
    override val plugins = FrPlugins
    override val models = FrModels
    override val perm = FrPerm
    override val project = FrProject
    override val env = FrEnv
    override val onboarding = FrOnboarding
    override val theme = FrTheme
    override val runtime = FrRuntime
}

/** 语言包（Pt）文案包 */
object PtStrings : Strings {
    override val common = PtCommon
    override val settings = PtSettings
    override val chat = PtChat
    override val canvas = PtCanvas
    override val session = PtSession
    override val files = PtFiles
    override val terminal = PtTerminal
    override val skills = PtSkills
    override val plugins = PtPlugins
    override val models = PtModels
    override val perm = PtPerm
    override val project = PtProject
    override val env = PtEnv
    override val onboarding = PtOnboarding
    override val theme = PtTheme
    override val runtime = PtRuntime
}

/** 当前语言包的文案（Compose 与非 Compose 通用；切换语言 = SettingsStore.language 变化 → 重组） */
val L: Strings get() = Languages.current()
