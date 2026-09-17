package com.pient.app.data

/**
 * `/skill:<名字>` 命令的**显示还原**（读端）—— 与 [ContextPolicy] 里的附件清单同族：
 * pi 侧存的是**展开全文**，界面要显示用户实际敲的那个紧凑命令。
 *
 * 谁写的（写端 = pi 自己，`Refences/pi-0.85.1/packages/coding-agent/src/core/agent-session.ts`
 * 的 `_expandSkillCommand`）：prompt 以 `/skill:<名字> [args]` 开头时，pi 把它换成
 *
 * ```
 * <skill name="<名字>" location="<SKILL.md 路径>">
 * References are relative to <技能目录>.
 *
 * <技能正文>
 * </skill>
 * ```
 *
 * 有 args 时再 `\n\n<args>` 追加在后（args = 命令名之后剩下的全部文本）。
 *
 * 所以：会话 JSONL / `get_messages` 里那条用户消息的文本是这一大坨，聊天气泡、画布节点预览、
 * 复制 XML 都会照着显示；而用户记得自己发的是 `/skill:<名字>`。本对象把它还原回去
 * （**只在显示层**用，回写 pi 的路径一律用原文本 → 不会把展开态写坏）。
 *
 * 口径与 pi-web 的 `lib/slash-display.ts`（`skillExpansionToCommand`）逐条对齐 —— 同一份正则形状：
 * 首尾信封必须完整、必须带 `References are relative to …` 那一行、正文贪婪匹配以最后一个 `</skill>`
 * 收口（技能正文里出现示例 `</skill>` 标签也不会被误截）。
 */
object SkillExpansion {

    /** pi 展开信封的完整形状；用 [Regex.matchEntire] 全串匹配（等价 pi-web 的 `^…$`） */
    private val ENVELOPE = Regex(
        "<skill name=\"([^\"\\n]+)\" location=\"([^\"\\n]+)\">\\n" +
            "References are relative to [^\\n]+\\.\\n\\n([\\s\\S]*)\\n</skill>" +
            "(?:\\n\\n([\\s\\S]+))?",
    )

    /**
     * 是展开形态就回 `/skill:<名字>`（有 args 时 ` /skill:<名字> <args>`），否则回 null。
     * **不是展开形态一律原样不动** —— 用户自己打的形似文本不会被改。
     */
    fun restore(text: String): String? {
        val m = ENVELOPE.matchEntire(text) ?: return null
        val name = m.groupValues[1]
        val args = m.groupValues.getOrNull(4).orEmpty()
        return if (args.isBlank()) "/skill:$name" else "/skill:$name ${args.trimStart()}"
    }

    /** 显示用文本：[restore] 失败 = 原样返回（调用点的唯一入口，别在别处再写一份正则） */
    fun display(text: String): String = restore(text) ?: text
}
