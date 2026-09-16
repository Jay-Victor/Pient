package com.pient.app.data.i18n

/** project 组文案（由 i18n 流水线生成；zh = 源码原文字面量） */
interface ProjectStrings {
    val renameProject: String
    val deleteProject: String
    val rebind: String
    val projectActions: String
    val typeBlank: String
    val typeBlankDesc: String
    val typeJavaDesc: String
    val exportTime: String
    val exportSessionCount: String
    val untitledSession: String
    val exportProjectLabel: String
    val exportUpdatedAt: String
    val exportMessageCount: String
    val exportNoMessages: String
    fun exportLocation(a0: Any?, a1: Any?): String
    val exportUserHeading: String
    val exportQuote: String
    val exportQuoteAi: String
    val exportQuoteUser: String
    val exportAttachments: String
    val exportEmptyAnswer: String
    val exportThinkingSummary: String
    val exportToolCallHeading: String
    val exportToolResultHeading: String
    val exportCompaction: String
    val exportCompactionSaved: String
    val sectionProjects: String
    val searchProjects: String
    val noMatchingProjects: String
    val sectionSessions: String
    fun selectedSessionsCount(a0: Any?, a1: Any?): String
    fun selectedSessionsAction(a0: Any?): String
    val sectionUnbound: String
    val searchUnbound: String
    fun selectedUnboundCount(a0: Any?, a1: Any?): String
    val noUnbound: String
    fun reboundToast(a0: Any?): String
    val projectExists: String
    fun selectedProjectsAction(a0: Any?): String
    val selectedSessionsTitle: String
    val exportDocTitle: String
    fun exportDone(a0: Any?, a1: Any?): String
    val exportFailed: String
    fun deletedSessionsToast(a0: Any?): String
    val exportSessions: String
    val unbindProject: String
    fun unbindConfirm(a0: Any?, a1: Any?): String
    val folderDeleteFailed: String
    fun deleteConfirm(a0: Any?, a1: Any?): String
    val selectedProjectsTitle: String
    fun reboundCountToast(a0: Any?, a1: Any?): String
    fun deletedCountToast(a0: Any?, a1: Any?): String
    fun deleteDirConfirm(a0: Any?): String
    val clearSearchField: String
    val sessionActions: String
}

object ZhProject : ProjectStrings {
    override val renameProject: String = "重命名项目"
    override val deleteProject: String = "删除项目"
    override val rebind: String = "重新绑定"
    override val projectActions: String = "项目操作"
    override val typeBlank: String = "空白"
    override val typeBlankDesc: String = "只建目录与项目配置"
    override val typeJavaDesc: String = "src/Main.java（javac 可直接编）"
    override val exportTime: String = "导出时间："
    override val exportSessionCount: String = "　·　会话数："
    override val untitledSession: String = "未命名会话"
    override val exportProjectLabel: String = "- 项目："
    override val exportUpdatedAt: String = "　·　最后更新："
    override val exportMessageCount: String = "　·　消息数："
    override val exportNoMessages: String = "（该会话没有可导出的消息）\n\n"
    override fun exportLocation(a0: Any?, a1: Any?): String = "下载/${a0}/${a1}"
    override val exportUserHeading: String = "### 用户\n\n"
    override val exportQuote: String = "> 引用"
    override val exportQuoteAi: String = " AI 回答"
    override val exportQuoteUser: String = "用户消息"
    override val exportAttachments: String = "附件："
    override val exportEmptyAnswer: String = "（空回答）"
    override val exportThinkingSummary: String = "<details><summary>思考过程</summary>\n\n"
    override val exportToolCallHeading: String = "### 工具调用 `"
    override val exportToolResultHeading: String = "### 工具结果 `"
    override val exportCompaction: String = "> 上下文压缩："
    override val exportCompactionSaved: String = " → 省 "
    override val sectionProjects: String = "项目记录"
    override val searchProjects: String = "搜索项目名称"
    override val noMatchingProjects: String = "无匹配项目"
    override val sectionSessions: String = "会话记录"
    override fun selectedSessionsCount(a0: Any?, a1: Any?): String = "已选择 ${a0}/${a1} 条"
    override fun selectedSessionsAction(a0: Any?): String = "操作已选会话（${a0}）"
    override val sectionUnbound: String = "已解绑项目"
    override val searchUnbound: String = "搜索已解绑项目"
    override fun selectedUnboundCount(a0: Any?, a1: Any?): String = "已选择 ${a0}/${a1} 条"
    override val noUnbound: String = "无已解绑项目"
    override fun reboundToast(a0: Any?): String = "已重新绑定项目「${a0}」"
    override val projectExists: String = "已存在同名项目"
    override fun selectedProjectsAction(a0: Any?): String = "操作已选项目（${a0}）"
    override val selectedSessionsTitle: String = "操作已选会话"
    override val exportDocTitle: String = "Pient 会话导出"
    override fun exportDone(a0: Any?, a1: Any?): String = "已导出 ${a0} 个会话 → ${a1}"
    override val exportFailed: String = "导出失败：下拉目录不可写"
    override fun deletedSessionsToast(a0: Any?): String = "已删除 ${a0} 个会话"
    override val exportSessions: String = "导出会话"
    override val unbindProject: String = "解绑项目"
    override fun unbindConfirm(a0: Any?, a1: Any?): String = "确定要解绑项目「${a0}」吗？其下 ${a1} 个会话记录将一并删除，项目文件夹及其中文件不受影响。"
    override val folderDeleteFailed: String = "项目文件夹删除失败"
    override fun deleteConfirm(a0: Any?, a1: Any?): String = "确定要删除项目「${a0}」吗？项目文件夹及其中所有文件、其下 ${a1} 个会话记录将一并删除。此操作不可撤销。"
    override val selectedProjectsTitle: String = "操作已选项目"
    override fun reboundCountToast(a0: Any?, a1: Any?): String = "已重新绑定 ${a0}/${a1} 个项目"
    override fun deletedCountToast(a0: Any?, a1: Any?): String = "已删除 ${a0}/${a1} 个项目"
    override fun deleteDirConfirm(a0: Any?): String = "确定要删除项目「${a0}」吗？其文件夹及其中所有文件将被彻底删除。此操作不可撤销。"
    override val clearSearchField: String = "清空"
    override val sessionActions: String = "会话操作"
}

object EnProject : ProjectStrings {
    override val renameProject: String = "Rename Project"
    override val deleteProject: String = "Delete Project"
    override val rebind: String = "Rebind"
    override val projectActions: String = "Project actions"
    override val typeBlank: String = "Blank"
    override val typeBlankDesc: String = "Creates only the directory and project config"
    override val typeJavaDesc: String = "src/Main.java (compiles directly with javac)"
    override val exportTime: String = "Exported at: "
    override val exportSessionCount: String = " · Sessions: "
    override val untitledSession: String = "Untitled Session"
    override val exportProjectLabel: String = "- Project: "
    override val exportUpdatedAt: String = " · Last updated: "
    override val exportMessageCount: String = " · Messages: "
    override val exportNoMessages: String = "(No messages to export for this session)\n\n"
    override fun exportLocation(a0: Any?, a1: Any?): String = "Downloads/${a0}/${a1}"
    override val exportUserHeading: String = "### User\n\n"
    override val exportQuote: String = "> Quote"
    override val exportQuoteAi: String = " AI answer"
    override val exportQuoteUser: String = "User message"
    override val exportAttachments: String = "Attachments: "
    override val exportEmptyAnswer: String = "(Empty answer)"
    override val exportThinkingSummary: String = "<details><summary>Thinking</summary>\n\n"
    override val exportToolCallHeading: String = "### Tool call `"
    override val exportToolResultHeading: String = "### Tool result `"
    override val exportCompaction: String = "> Context compaction: "
    override val exportCompactionSaved: String = " → saved "
    override val sectionProjects: String = "Projects"
    override val searchProjects: String = "Search project name"
    override val noMatchingProjects: String = "No matching projects"
    override val sectionSessions: String = "Sessions"
    override fun selectedSessionsCount(a0: Any?, a1: Any?): String = "${a0}/${a1} selected"
    override fun selectedSessionsAction(a0: Any?): String = "Actions for selected sessions (${a0})"
    override val sectionUnbound: String = "Unbound projects"
    override val searchUnbound: String = "Search unbound projects"
    override fun selectedUnboundCount(a0: Any?, a1: Any?): String = "${a0}/${a1} selected"
    override val noUnbound: String = "No unbound projects"
    override fun reboundToast(a0: Any?): String = "Rebound project “${a0}”"
    override val projectExists: String = "A project with this name already exists"
    override fun selectedProjectsAction(a0: Any?): String = "Actions for selected projects (${a0})"
    override val selectedSessionsTitle: String = "Selected sessions"
    override val exportDocTitle: String = "Pient Session Export"
    override fun exportDone(a0: Any?, a1: Any?): String = "Exported ${a0} sessions → ${a1}"
    override val exportFailed: String = "Export failed: download directory is not writable"
    override fun deletedSessionsToast(a0: Any?): String = "Deleted ${a0} sessions"
    override val exportSessions: String = "Export sessions"
    override val unbindProject: String = "Unbind Project"
    override fun unbindConfirm(a0: Any?, a1: Any?): String = "Unbind project “${a0}”? Its ${a1} session records will also be deleted. The project folder and its files are not affected."
    override val folderDeleteFailed: String = "Failed to delete the project folder"
    override fun deleteConfirm(a0: Any?, a1: Any?): String = "Delete project “${a0}”? Its folder, all files in it, and its ${a1} session records will be deleted. This cannot be undone."
    override val selectedProjectsTitle: String = "Selected projects"
    override fun reboundCountToast(a0: Any?, a1: Any?): String = "Rebound ${a0}/${a1} projects"
    override fun deletedCountToast(a0: Any?, a1: Any?): String = "Deleted ${a0}/${a1} projects"
    override fun deleteDirConfirm(a0: Any?): String = "Delete project “${a0}”? Its folder and all files inside will be permanently deleted. This cannot be undone."
    override val clearSearchField: String = "Clear"
    override val sessionActions: String = "Session actions"
}
