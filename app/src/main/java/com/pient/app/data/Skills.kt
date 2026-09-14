package com.pient.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * **技能装配（内核侧）** —— 把已启用的技能交给模型，口径逐字对齐 pi：
 *
 * 1. pi 在启动时扫技能目录，只取 **name / description / location**；
 * 2. 系统提示词里放一段 `<available_skills>` XML（Agent Skills 规范，
 *    `Refences/pi-0.85.1/packages/coding-agent/src/core/skills.ts:352` 的 `formatSkillsForPrompt`）；
 * 3. 任务命中时由模型用 **`read`** 打开技能目录里的 `SKILL.md` 全文（那里才是真正的指令）；
 * 4. 技能内引用的相对路径，相对**技能目录**解析（提示词里明说了）。
 *
 * 为什么不在内核里"自动注入技能正文"：pi 的口径是**按需加载**（技能可能很长，全塞进系统提示词
 * 会白烧上下文）；Pient 保持同一条路，模型看到 description 判断要不要读。
 *
 * 技能的扫描/启停/市场仍是 [SkillStore] 的职责（一份实现）——这里只做"装配给模型"这一件事。
 */
object Skills {

    /**
     * 已启用、可供模型调用的技能。
     *
     * 首次调用时若 [SkillStore] 还没扫过（比如刚启动就发消息，没进过技能页），就地扫一次；
     * 之后复用它的扫描结果，不再重复读盘。
     */
    suspend fun enabled(context: Context): List<SkillItem> = withContext(Dispatchers.IO) {
        if (SkillStore.global.isEmpty() && SkillStore.project.isEmpty() && !SkillStore.loading) {
            runCatching { SkillStore.refresh(context) }
        }
        (SkillStore.global + SkillStore.project).filter { it.enabled }
    }

    /**
     * 系统提示词里的技能段；没有可用技能（或都缺 location）时返回 null
     * —— 调用方据此保持提示词原样（不写空壳 XML）。
     */
    fun promptBlock(skills: List<SkillItem>): String? {
        val usable = skills.filter { !it.path.isNullOrBlank() }
        if (usable.isEmpty()) return null
        val sb = StringBuilder()
        sb.append("\n\nThe following skills provide specialized instructions for specific tasks.\n")
        sb.append("Use the read tool to load a skill's file when the task matches its description.\n")
        sb.append("When a skill file references a relative path, resolve it against the skill directory (parent of SKILL.md) and use that absolute path in tool commands.\n\n")
        sb.append("<available_skills>\n")
        for (s in usable) {
            sb.append("  <skill>\n")
            sb.append("    <name>").append(escapeXml(s.name)).append("</name>\n")
            sb.append("    <description>").append(escapeXml(s.desc)).append("</description>\n")
            sb.append("    <location>").append(escapeXml(s.path.orEmpty())).append("</location>\n")
            sb.append("  </skill>\n")
        }
        sb.append("</available_skills>")
        return sb.toString()
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
