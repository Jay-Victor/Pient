package com.pient.app.data.i18n

import com.pient.app.data.SettingsStore
import java.util.Locale

/**
 * 界面语言注册表（机制对齐 pi-web `lib/i18n/registry.ts`）：注册制语言包 + 「跟随系统」解析。
 *
 * 新增一门语言 = ① 建一套 `Strings<X>` 实现 ② 在 [packs] 注册一行 —— 语言设置页的列表、
 * 落盘、切换逻辑都不用改。缺条目由 Kotlin 接口在编译期拦住（pi-web 的 map 方案只能回退英文）。
 */
object Languages {

    /** 「跟随系统」的保存值（SettingsStore.language 的取值之一） */
    const val SYSTEM = "system"

    /** 语言包（id 用于落盘 / [Strings] 是文案本体；label 用该语言自己的写法，切换前也能认出来） */
    class Pack(val id: String, val label: String, val strings: Strings)

    private val packs: List<Pack> = listOf(
        Pack("zh-CN", "简体中文", ZhStrings),
        Pack("zh-TW", "繁體中文", ZhTwStrings),
        Pack("en", "English", EnStrings),
        Pack("ja", "日本語", JaStrings),
    )

    /** 语言设置页的选项：「跟随系统」置顶 + 各语言包（设计计划 6.3） */
    data class Option(val id: String, val label: String)

    fun options(): List<Option> =
        listOf(Option(SYSTEM, L.settings.followSystem)) + packs.map { Option(it.id, it.label) }

    /** 保存值（可能是「跟随系统」）→ 实际生效的语言包 id */
    fun resolveId(saved: String): String {
        if (saved != SYSTEM && packs.any { it.id == saved }) return saved
        return systemId()
    }

    /** 系统语言 → 我们的语言包：zh-Hant/zh-TW/zh-HK/zh-MO → 繁中，zh* → 简中，ja* → 日语，其余 → 英文 */
    private fun systemId(): String {
        val tag = Locale.getDefault().toLanguageTag().lowercase(Locale.ROOT)
        return when {
            tag.startsWith("zh") && (tag.contains("hant") || tag.contains("tw") ||
                tag.contains("hk") || tag.contains("mo")) -> "zh-TW"
            tag.startsWith("zh") -> "zh-CN"
            tag.startsWith("ja") -> "ja"
            else -> "en"
        }
    }

    /** 当前生效的文案包 */
    fun current(): Strings = packs.firstOrNull { it.id == resolveId(SettingsStore.language) }?.strings
        ?: ZhStrings
}
