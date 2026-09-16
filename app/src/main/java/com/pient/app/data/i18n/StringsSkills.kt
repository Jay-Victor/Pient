package com.pient.app.data.i18n

/** skills 组文案（由 i18n 流水线生成；zh = 源码原文字面量） */
interface SkillsStrings {
    fun importTarget(a0: Any?): String
    val importTargetGlobal: String
    val importTargetProject: String
    fun installedNote(a0: Any?, a1: Any?): String
    val importTitle: String
    val tabZip: String
    val tabManual: String
    val pickZip: String
    val globalPiSkillsPath: String
    val projectPiSkillsPath: String
    val nameLabel: String
    val nameHint: String
    val nameInvalid: String
    val descLabel: String
    val descHint: String
    val contentLabel: String
    val contentHint: String
    val importing: String
    val zipChosen: String
    val description: String
    val viewSkillMd: String
    val expandCollapse: String
    val dirStructure: String
    val noDirInfo: String
    val searchTitle: String
    val searchPlaceholder: String
    val searching: String
    val installGlobal: String
    val installProject: String
    val globalAgentsPath: String
    val projectAgentsPath: String
    val marketHint: String
    val searchingMarket: String
    val marketResults: String
    fun installStarted(a0: Any?, a1: Any?): String
    fun installFailed(a0: Any?, a1: Any?): String
    val installedLocal: String
    val disabledBadge: String
    fun noResults(a0: Any?): String
    val scanFailed: String
    val manageTitle: String
    val projectPaths: String
    val skillFileMissing: String
    fun enabledToast(a0: Any?): String
    fun disabledToast(a0: Any?): String
    val actionFailed: String
    fun scanFailedDetail(a0: Any?): String
    val scanning: String
    val noGlobalSkills: String
    val noProjectSkills: String
    fun importedName(a0: Any?): String
    fun importedSlug(a0: Any?): String
    fun deletedSkill(a0: Any?): String
}

object ZhSkills : SkillsStrings {
    override fun importTarget(a0: Any?): String = "导入目标：${a0}"
    override val importTargetGlobal: String = "全局 ~/.pi/agent/skills/"
    override val importTargetProject: String = "当前项目 .pi/skills/"
    override fun installedNote(a0: Any?, a1: Any?): String = "已安装「${a0}」（${a1}）"
    override val importTitle: String = "导入技能"
    override val tabZip: String = "ZIP 导入"
    override val tabManual: String = "手动输入"
    override val pickZip: String = "选择 .zip 文件（解压导入）"
    override val globalPiSkillsPath: String = "全局 ~/.pi/agent/skills/"
    override val projectPiSkillsPath: String = "当前项目 .pi/skills/"
    override val nameLabel: String = "技能名称"
    override val nameHint: String = "1–64 字符 · 小写/数字/连字符（frontmatter name）"
    override val nameInvalid: String = "名称仅限小写字母、数字、连字符，1–64 字符"
    override val descLabel: String = "技能简介"
    override val descHint: String = "必填 · ≤1024 字符（frontmatter description）"
    override val contentLabel: String = "技能内容"
    override val contentHint: String = "生成 SKILL.md 正文"
    override val importing: String = "导入中…（完成后自动刷新列表）"
    override val zipChosen: String = "已选择压缩包"
    override val description: String = "描述"
    override val viewSkillMd: String = "查看Skill.md"
    override val expandCollapse: String = "展开/收起"
    override val dirStructure: String = "目录结构"
    override val noDirInfo: String = "（无目录信息）"
    override val searchTitle: String = "搜索技能"
    override val searchPlaceholder: String = "输入技能名或关键词…"
    override val searching: String = "搜索中"
    override val installGlobal: String = "装到全局"
    override val installProject: String = "装到项目"
    override val globalAgentsPath: String = "~/.agents/skills（pi 全局技能目录）"
    override val projectAgentsPath: String = "当前项目 .agents/skills"
    override val marketHint: String = "输入关键词后搜索技能市场（skills.sh；搜索走应用直连，安装由 Ubuntu 里的 skills CLI 执行）"
    override val searchingMarket: String = "正在搜索技能市场…"
    override val marketResults: String = "市场结果（skills.sh）："
    override fun installStarted(a0: Any?, a1: Any?): String = "已开始安装 ${a0} —— 终端页「${a1}」可看进度"
    override fun installFailed(a0: Any?, a1: Any?): String = "安装失败（退出码 ${a0}）—— 终端页「${a1}」有完整报错"
    override val installedLocal: String = "已安装（本地匹配）："
    override val disabledBadge: String = "已停用"
    override fun noResults(a0: Any?): String = "没有找到与「${a0}」相关的技能"
    override val scanFailed: String = "扫描失败"
    override val manageTitle: String = "技能管理"
    override val projectPaths: String = "当前项目 .pi/skills/ · .agents/skills/"
    override val skillFileMissing: String = "找不到技能文件"
    override fun enabledToast(a0: Any?): String = "已启用 ${a0}"
    override fun disabledToast(a0: Any?): String = "已停用 ${a0}"
    override val actionFailed: String = "操作失败"
    override fun scanFailedDetail(a0: Any?): String = "扫描失败：${a0}"
    override val scanning: String = "正在扫描技能目录…"
    override val noGlobalSkills: String = "还没有全局技能。点右下「导入」新建，或用「搜索」查看技能市场。"
    override val noProjectSkills: String = "当前项目没有技能（.pi/skills 或 .agents/skills 里放 SKILL.md 即可）。"
    override fun importedName(a0: Any?): String = "已导入技能 ${a0}"
    override fun importedSlug(a0: Any?): String = "已导入技能 ${a0}"
    override fun deletedSkill(a0: Any?): String = "已删除技能 ${a0}"
}

object EnSkills : SkillsStrings {
    override fun importTarget(a0: Any?): String = "Import target: ${a0}"
    override val importTargetGlobal: String = "Global ~/.pi/agent/skills/"
    override val importTargetProject: String = "Current project .pi/skills/"
    override fun installedNote(a0: Any?, a1: Any?): String = "Installed “${a0}” (${a1})"
    override val importTitle: String = "Import Skill"
    override val tabZip: String = "ZIP import"
    override val tabManual: String = "Manual entry"
    override val pickZip: String = "Choose a .zip file (extract on import)"
    override val globalPiSkillsPath: String = "Global ~/.pi/agent/skills/"
    override val projectPiSkillsPath: String = "Current project .pi/skills/"
    override val nameLabel: String = "Skill name"
    override val nameHint: String = "1–64 characters · lowercase/digits/hyphens (frontmatter name)"
    override val nameInvalid: String = "Name may only contain lowercase letters, digits and hyphens, 1–64 characters"
    override val descLabel: String = "Skill description"
    override val descHint: String = "Required · ≤1024 characters (frontmatter description)"
    override val contentLabel: String = "Skill content"
    override val contentHint: String = "Generates the SKILL.md body"
    override val importing: String = "Importing… (the list refreshes automatically when done)"
    override val zipChosen: String = "Archive selected"
    override val description: String = "Description"
    override val viewSkillMd: String = "View SKILL.md"
    override val expandCollapse: String = "Expand/Collapse"
    override val dirStructure: String = "Directory structure"
    override val noDirInfo: String = "(No directory info)"
    override val searchTitle: String = "Search Skills"
    override val searchPlaceholder: String = "Enter a skill name or keyword…"
    override val searching: String = "Searching"
    override val installGlobal: String = "Install globally"
    override val installProject: String = "Install to project"
    override val globalAgentsPath: String = "~/.agents/skills (pi global skills directory)"
    override val projectAgentsPath: String = "Current project .agents/skills"
    override val marketHint: String = "Enter a keyword to search the skill market (skills.sh; search runs directly in the app, installs are run by the skills CLI in Ubuntu)"
    override val searchingMarket: String = "Searching the skill market…"
    override val marketResults: String = "Market results (skills.sh):"
    override fun installStarted(a0: Any?, a1: Any?): String = "Install started: ${a0} — check progress in “${a1}” on the Terminal page"
    override fun installFailed(a0: Any?, a1: Any?): String = "Install failed (exit code ${a0}) — the full error is in “${a1}” on the Terminal page"
    override val installedLocal: String = "Installed (local match):"
    override val disabledBadge: String = "Disabled"
    override fun noResults(a0: Any?): String = "No skills found for “${a0}”"
    override val scanFailed: String = "Scan failed"
    override val manageTitle: String = "Skill management"
    override val projectPaths: String = "Current project .pi/skills/ · .agents/skills/"
    override val skillFileMissing: String = "Skill file not found"
    override fun enabledToast(a0: Any?): String = "Enabled ${a0}"
    override fun disabledToast(a0: Any?): String = "Disabled ${a0}"
    override val actionFailed: String = "Operation failed"
    override fun scanFailedDetail(a0: Any?): String = "Scan failed: ${a0}"
    override val scanning: String = "Scanning skill directories…"
    override val noGlobalSkills: String = "No global skills yet. Tap \"Import\" at the bottom right to create one, or use \"Search\" to browse the skill market."
    override val noProjectSkills: String = "This project has no skills (drop a SKILL.md into .pi/skills or .agents/skills)."
    override fun importedName(a0: Any?): String = "Imported skill ${a0}"
    override fun importedSlug(a0: Any?): String = "Imported skill ${a0}"
    override fun deletedSkill(a0: Any?): String = "Deleted skill ${a0}"
}
