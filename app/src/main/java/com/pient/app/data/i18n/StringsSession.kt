package com.pient.app.data.i18n

/** session 组文案（由 i18n 流水线生成；zh = 源码原文字面量） */
interface SessionStrings {
    fun projectLabel(a0: Any?): String
    val noProject: String
    val deleteSession: String
    val searchSessions: String
    val noMatchingSessions: String
    val renameSession: String
    fun deleteConfirm(a0: Any?): String
    val createProjectFirst: String
    val resetWorkspace: String
    val newProject: String
    val pin: String
    fun selectedCount(a0: Any?): String
    val batchMode: String
    val newSession: String
    val emptySessions: String
    val showMoreSessions: String
    val plugins: String
    val resetConfirm: String
    fun workspaceResetToast(a0: Any?): String
    val resetFailed: String
    fun clearConfirm(a0: Any?): String
    val clearConfirmSuffix: String
    fun templateCreatedToast(a0: Any?, a1: Any?): String
    val folderCreateFailed: String
    val projectTypeLabel: String
    fun projectTypeHint(a0: Any?): String
    val batchDeleteTitle: String
    fun batchDeleteConfirm(a0: Any?): String
    val moreActions: String
    val unpin: String
}

object ZhSession : SessionStrings {
    override fun projectLabel(a0: Any?): String = "项目：${a0}"
    override val noProject: String = "未创建项目"
    override val deleteSession: String = "删除会话"
    override val searchSessions: String = "搜索会话标题"
    override val noMatchingSessions: String = "无匹配会话"
    override val renameSession: String = "重命名会话"
    override fun deleteConfirm(a0: Any?): String = "确定要删除会话「${a0}」吗？此操作不可撤销。"
    override val createProjectFirst: String = "请先创建项目"
    override val resetWorkspace: String = "重置工作区"
    override val newProject: String = "新建项目"
    override val pin: String = "置顶"
    override fun selectedCount(a0: Any?): String = "已选 ${a0} 个会话"
    override val batchMode: String = "批量管理"
    override val newSession: String = "新建会话"
    override val emptySessions: String = "暂无会话 · 点右上角 + 新建会话"
    override val showMoreSessions: String = "显示更多会话"
    override val plugins: String = "插件"
    override val resetConfirm: String = "重置"
    override fun workspaceResetToast(a0: Any?): String = "工作区已重置：${a0}"
    override val resetFailed: String = "重置失败（目录不可写？）"
    override fun clearConfirm(a0: Any?): String = "确定要清空项目「${a0}」的全部文件吗？目录本身保留（项目仍绑定在原位置），"
    override val clearConfirmSuffix: String = "其中所有文件与子目录将被删除；项目标记（.pient-project.json）会重新写回。此操作不可撤销。"
    override fun templateCreatedToast(a0: Any?, a1: Any?): String = "已按「${a0}」模板创建（${a1} 个文件）"
    override val folderCreateFailed: String = "文件夹创建失败"
    override val projectTypeLabel: String = "项目类型（模板）"
    override fun projectTypeHint(a0: Any?): String = "${a0} · 将在应用私有目录 Projects/ 下创建"
    override val batchDeleteTitle: String = "批量删除会话"
    override fun batchDeleteConfirm(a0: Any?): String = "确定要删除选中的 ${a0} 个会话吗？此操作不可撤销。"
    override val moreActions: String = "更多操作"
    override val unpin: String = "取消置顶"
}

object EnSession : SessionStrings {
    override fun projectLabel(a0: Any?): String = "Project: ${a0}"
    override val noProject: String = "No project"
    override val deleteSession: String = "Delete Session"
    override val searchSessions: String = "Search session title"
    override val noMatchingSessions: String = "No matching sessions"
    override val renameSession: String = "Rename Session"
    override fun deleteConfirm(a0: Any?): String = "Delete session “${a0}”? This cannot be undone."
    override val createProjectFirst: String = "Create a project first"
    override val resetWorkspace: String = "Reset Workspace"
    override val newProject: String = "New Project"
    override val pin: String = "Pinned"
    override fun selectedCount(a0: Any?): String = "${a0} sessions selected"
    override val batchMode: String = "Batch mode"
    override val newSession: String = "New Session"
    override val emptySessions: String = "No sessions · Tap + in the top right to create one"
    override val showMoreSessions: String = "Show more sessions"
    override val plugins: String = "Plugins"
    override val resetConfirm: String = "Reset"
    override fun workspaceResetToast(a0: Any?): String = "Workspace reset: ${a0}"
    override val resetFailed: String = "Reset failed (directory not writable?)"
    override fun clearConfirm(a0: Any?): String = "Clear all files in project “${a0}”? The directory itself is kept (the project stays bound to its location), "
    override val clearConfirmSuffix: String = "all files and subdirectories in it will be deleted; the project marker (.pient-project.json) will be written back. This cannot be undone."
    override fun templateCreatedToast(a0: Any?, a1: Any?): String = "Created from the “${a0}” template (${a1} files)"
    override val folderCreateFailed: String = "Failed to create the folder"
    override val projectTypeLabel: String = "Project type (template)"
    override fun projectTypeHint(a0: Any?): String = "${a0} · Will be created under the app-private directory Projects/"
    override val batchDeleteTitle: String = "Batch delete sessions"
    override fun batchDeleteConfirm(a0: Any?): String = "Delete the ${a0} selected sessions? This cannot be undone."
    override val moreActions: String = "More actions"
    override val unpin: String = "Unpin"
}
