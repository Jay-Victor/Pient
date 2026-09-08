package com.pient.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/** 顶栏右下方区域内容（消息区 / 文件内容预览区 / 终端页 / 分支画布 四态切换） */
enum class Panel { MESSAGES, FILES, TERMINAL, TREE }

/**
 * 聊天主页应用状态（跨导航保活：提升到 NavHost 外层）。
 * UI 原型阶段：状态以内存承载；接入 Pi 运行时后由 SessionManager /
 * get_state / get_available_models 等官方机制驱动。
 */
class ChatState {

    // ── 会话 ──────────────────────────────────────────────
    var currentProject by mutableStateOf(MockProjects.list.first().name)
    var currentSessionId by mutableStateOf<String?>("s-1024")

    // ── UI 状态（跨导航保活：提升到 NavHost 外层，避免页面往返丢失）──
    // 会话侧栏展开状态。手机端抽屉导航即关闭（无感）；平板压缩模式保持展开，
    // 进入二级页再返回聊天页时侧边栏仍是展开态（持久侧边栏语义）。
    var drawerOpen by mutableStateOf(false)

    val projects = mutableStateListOf<Project>().apply { addAll(MockProjects.list) }

    val sessions = mutableStateMapOf<String, SnapshotStateList<Session>>()
    val messagesBySession = mutableStateMapOf<String, SnapshotStateList<Msg>>()

    init {
        MockSessions.byProject.forEach { (proj, list) ->
            sessions[proj] = list.toMutableStateList()
        }
        messagesBySession["s-1024"] = MockMessages.fixLoginCrash.toMutableStateList()
        messagesBySession["s-1023"] = MockMessages.refactorPermissions.toMutableStateList()
    }

    /**
     * 添加项目（2026-09-02 实现真实功能）：
     * 「新建文件夹」= filesDir/Projects/<name> 真实目录；「选择本地文件夹」= SAF tree URI。
     * 重名返回 false（不重复添加）。成功后切换为当前项目。
     */
    fun addProject(name: String, path: String, uri: String? = null): Boolean {
        if (name.isBlank() || projects.any { it.name == name }) return false
        projects += Project(name, path, uri)
        sessions[name] = mutableStateListOf()
        setProject(name)
        return true
    }

    val currentMessages: SnapshotStateList<Msg>
        get() {
            val id = currentSessionId ?: return mutableStateListOf()
            return messagesBySession.getOrPut(id) { mutableStateListOf() }
        }

    val currentSession: Session?
        get() = sessions[currentProject].orEmpty().firstOrNull { it.id == currentSessionId }

    fun sessionsFor(project: String): List<Session> = sessions[project].orEmpty()

    fun setProject(name: String) {
        currentProject = name
        currentSessionId = sessionsFor(name).firstOrNull()?.id
        // 项目切换：清空文件预览标签与树展开状态（标签/展开路径属于原项目的文件树，
        // 2026-09-02 修复：切项目后标签栏仍显示上一项目文件）
        openTabs.clear()
        activeTabIndex = 0
        expandedDirs.clear()
    }

    fun selectSession(id: String) {
        currentSessionId = id
        activePanel = Panel.MESSAGES
    }

    fun newSession(): String {
        val list = sessions.getOrPut(currentProject) { mutableStateListOf() }
        val id = "s-${System.currentTimeMillis()}"
        list.add(0, Session(id, "新建会话", currentProject, "刚刚"))
        currentSessionId = id
        messagesBySession[id] = mutableStateListOf()
        activePanel = Panel.MESSAGES
        return id
    }

    fun renameSession(id: String, title: String) {
        // 跨项目按 id 查找（聊天页侧边栏与项目管理页共用）
        for (list in sessions.values) {
            val i = list.indexOfFirst { it.id == id }
            if (i >= 0) {
                val old = list[i]
                list[i] = old.copy(title = title.ifBlank { old.title })
                break
            }
        }
    }

    fun deleteSession(id: String) {
        sessions[currentProject]?.removeAll { it.id == id }
        messagesBySession.remove(id)
        if (currentSessionId == id) currentSessionId = sessionsFor(currentProject).firstOrNull()?.id
    }

    /** 跨项目按 id 删除会话（项目管理页使用；含消息记录与当前会话指针处理） */
    fun deleteSessionById(id: String) {
        for (list in sessions.values) {
            if (list.removeAll { it.id == id }) break
        }
        messagesBySession.remove(id)
        if (currentSessionId == id) currentSessionId = sessionsFor(currentProject).firstOrNull()?.id
    }

    /** 置顶/取消置顶会话 */
    fun togglePin(id: String) {
        sessions[currentProject].orEmpty().indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { i ->
            val old = sessions[currentProject]!![i]
            sessions[currentProject]!![i] = old.copy(pinned = !old.pinned)
        }
    }

    /** 重命名项目：迁移 sessions 键、同步会话 project 字段与 currentProject；空名/重名忽略 */
    fun renameProject(oldName: String, newNameRaw: String) {
        val newName = newNameRaw.trim()
        if (newName.isBlank() || newName == oldName) return
        if (projects.any { it.name == newName }) return
        val i = projects.indexOfFirst { it.name == oldName }
        if (i < 0) return
        val old = projects[i]
        projects[i] = old.copy(
            name = newName,
            // SAF 项目（content:// tree URI）重命名不改 path：URI 无法按文件名拼接重建
            path = if (old.path.startsWith("content://")) old.path
            else old.path.substringBeforeLast('/', old.path) + "/" + newName,
        )
        val moved = sessions.remove(oldName) ?: mutableStateListOf()
        for (j in moved.indices) moved[j] = moved[j].copy(project = newName)
        sessions[newName] = moved
        if (currentProject == oldName) currentProject = newName
    }

    /**
     * 将项目移出列表（至少保留一个）：连同其会话与消息记录一并移除；
     * 删除当前项目时切换到第一个剩余项目。
     * 「删除项目」= 调用方先删文件夹（ProjectFiles.deleteProjectRoot）再调本方法；
     * 「解绑项目」= 直接调本方法（保留文件夹）。
     */
    fun removeProject(name: String) {
        if (projects.size <= 1) return
        if (!projects.removeAll { it.name == name }) return
        sessions.remove(name)?.forEach { s -> messagesBySession.remove(s.id) }
        if (currentProject == name) {
            currentProject = projects.first().name
            currentSessionId = sessionsFor(currentProject).firstOrNull()?.id
            // 被删项目的文件树不再有效；FileTreePanel 会按新 currentProject 重载
            fileTreeRoot = null
        }
    }

    // ── 流式 mock 发送 ────────────────────────────────────
    var isStreaming by mutableStateOf(false)
    var streamDraft by mutableStateOf("")
    var streamJob: Job? = null

    suspend fun streamReply(userText: String) {
        isStreaming = true
        streamDraft = ""
        currentMessages += Msg.User(userText, attachments.toList())
        attachments.clear()
        // 状态徽标：当前会话标记运行中
        markRunning(true)
        delay(600) // “思考中”停顿
        val reply = MockReplies.next(userText)
        if (streamingOutputEnabled) {
            // 流式输出：逐字追加
            reply.forEach { ch ->
                streamDraft += ch
                delay(14)
            }
        } else {
            // 关闭流式：整段输出（原型：仍走同一 draft 通道）
            streamDraft = reply
        }
        val usage = Usage(
            inTokens = 1200 + userText.length * 2,
            outTokens = reply.length / 2,
            cacheTokens = 3400,
            costUsd = 0.006,
        )
        currentMessages += Msg.Assistant(streamDraft, usage, selectedModel.name)
        streamDraft = ""
        isStreaming = false
        markRunning(false)
    }

    private fun markRunning(running: Boolean) {
        val list = sessions[currentProject] ?: return
        val id = currentSessionId ?: return
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) list[i] = list[i].copy(running = running)
    }

    fun abort() {
        streamJob?.cancel()
        if (streamDraft.isNotEmpty()) {
            currentMessages += Msg.Assistant(streamDraft, null)
            streamDraft = ""
        }
        isStreaming = false
        markRunning(false)
    }

    // ── 分支（2026-09-02 分支功能设计：/tree 画布页 + fork）──

    /** /tree 画布页会话树（原型：mock 树按会话 id 提供；接入运行时后由会话树数据驱动） */
    val branchTree: SessionTreeNode?
        get() = when (currentSessionId) {
            "s-1024" -> MockTrees.fixLoginCrash
            else -> null
        }

    /**
     * /tree 画布页切到目标节点：把根→目标的路径节点 exchange 展平为当前消息列表
     * （2026-09-08 起不再向聊天流注入分支切换条——会话内分支在 /tree 画布页展示，
     * 会话外分支在会话列表展示，聊天流内无分支卡片）。
     */
    fun navigateToNode(nodeId: String): Boolean {
        val tree = branchTree ?: return false
        val path = mutableListOf<SessionTreeNode>()
        if (!collectNodePath(tree, nodeId, path)) return false
        val newMsgs = mutableListOf<Msg>()
        path.forEach { node -> newMsgs += node.exchange }
        currentSessionId?.let { messagesBySession[it] = newMsgs.toMutableStateList() }
        return true
    }

    private fun collectNodePath(
        node: SessionTreeNode,
        id: String,
        out: MutableList<SessionTreeNode>,
    ): Boolean {
        out += node
        if (node.id == id) return true
        for (child in node.children) {
            if (collectNodePath(child, id, out)) return true
            out.removeAt(out.lastIndex)
        }
        return false
    }

    /**
     * fork 会话外分支（原型；v1 = 官方 RPC fork，上下文拷贝由 pi 底座完成）：
     * 新建独立会话入当前项目侧栏头部，消息流 = fork 点（含）之前全部消息的拷贝；
     * 与原会话再无关联（pi 表头 parentSession 保留但不展示）。
     * 自动跳转新会话（用户拍板：沿用 pi-web）。
     */
    fun forkSession(entryIndex: Int): String {
        val id = currentSessionId ?: return ""
        val src = messagesBySession[id]?.toList() ?: return ""
        if (entryIndex !in src.indices) return ""
        val newId = "s-${System.currentTimeMillis()}"
        val prefix = src.subList(0, entryIndex + 1).toMutableStateList()
        val list = sessions.getOrPut(currentProject) { mutableStateListOf() }
        list.add(0, Session(newId, forkTitle(src, entryIndex), currentProject, "刚刚"))
        messagesBySession[newId] = prefix
        currentSessionId = newId
        activePanel = Panel.MESSAGES
        return newId
    }

    /** fork 新会话默认标题：fork 点用户消息前 20 字（AI 消息则取其前一条用户消息） */
    private fun forkTitle(msgs: List<Msg>, index: Int): String {
        val userIdx = if (msgs[index] is Msg.User) index
        else (index downTo 0).firstOrNull { msgs[it] is Msg.User }
        if (userIdx == null) return "新建会话"
        val text = (msgs[userIdx] as Msg.User).text.trim()
        val t = text.take(20)
        return if (text.length > 20) "$t…" else t
    }

    // ── 输入栏：模型选择器 ────────────────────────────────
    var selectedModelId by mutableStateOf("anthropic/claude-sonnet-4-5")
    var thinkingEnabled by mutableStateOf(false)
    var thinkingLevel by mutableStateOf(ThinkingLevel.MEDIUM)
    var streamingOutputEnabled by mutableStateOf(true) // 流式输出开关（模型选择器"输出"栏）

    val selectedModel: AiModel
        get() = MockModels.providers.firstOrNull { it.id == selectedModelId }
            ?: MockModels.providers.first()

    // ── 输入栏：上下文指示器（数据源 = get_state 同源口径） ──
    var contextPercent by mutableStateOf(34f)
    var windowTokens by mutableStateOf(61200)
    var maxWindowTokens by mutableStateOf(180000)
    var connectionLabel by mutableStateOf("已连接")
    // 系统提示词只读展示（2026-09-01，对齐 pi-web system 面板）：
    // 真实值 = agent.state.systemPrompt（pi 程序化构建的 base prompt），此处为原型 mock。
    var systemPrompt by mutableStateOf(
        "You are Pi, an autonomous agent operating on this device.\n" +
            "\n" +
            "You can read, write, and edit files, run shell commands, and search the codebase to complete the user's tasks.\n" +
            "\n" +
            "Work autonomously: break complex tasks into steps, execute them with your tools, and verify results before reporting.\n" +
            "\n" +
            "Be precise and factual. Never fabricate file contents, command outputs, or results you did not actually produce.\n" +
            "\n" +
            "When unsure, say so plainly instead of guessing.\n" +
            "\n" +
            "Follow project rules injected as applicable skills and rules. Respect user preferences and local conventions.",
    )
    // 上下文用量分类明细（Hermes 上下文卡片口径；UI 原型 mock，合计 = windowTokens）
    // 分类经 pi-0.84.2 源码核实（2026-08-28）：pi 无语义记忆（memory=会话存储）、
    // fork 子代理定义不进父上下文——「记忆」「子代理」已移除。
    var contextCategories by mutableStateOf(
        listOf(
            ContextCategory("conversation", "对话", 40000),
            ContextCategory("system_prompt", "系统提示词", 9200),
            ContextCategory("tool_definitions", "工具定义", 7200),
            ContextCategory("skills", "技能", 3700),
            ContextCategory("rules", "规则", 1100),
        )
    )

    // ── 输入栏：附件 chip ─────────────────────────────────
    val attachments = mutableStateListOf<Attachment>()

    // ── 顶栏区域切换 ──────────────────────────────────────
    var activePanel by mutableStateOf(Panel.MESSAGES)

    fun togglePanel(p: Panel) {
        activePanel = if (activePanel == p) Panel.MESSAGES else p
    }

    // ── 文件页状态 ────────────────────────────────────────
    // 文件树（2026-09-02 真实化：当前项目真实目录；null = 尚未加载或目录不存在时由 UI 回退演示树）
    var fileTreeRoot by mutableStateOf<FileNode?>(null)

    /** 重载当前项目文件树（真实文件系统；目录不存在时 fileTreeRoot 置 null） */
    fun refreshFileTree(context: Context) {
        val project = projects.firstOrNull { it.name == currentProject }
        fileTreeRoot = if (project != null) ProjectFiles.loadTree(context, project) else null
    }

    val openTabs = mutableStateListOf<FileNode>()
    var activeTabIndex by mutableIntStateOf(0)
    var mdEditMode by mutableStateOf(false)      // markdown 渲染/编辑切换
    var lineNumbers by mutableStateOf(true)      // 行号可选
    val expandedDirs = mutableStateSetOf<String>() // 文件树展开路径
    // 文件树长按菜单「@ 提及插入输入框」请求（ChatScreen 消费后置 null）
    var mentionInsertRequest by mutableStateOf<String?>(null)

    fun openFile(node: FileNode) {
        // 同一文件 = 名称 + 真实位置相同（FileNode 无 equals：文件树每次打开都重建
        // 节点实例，引用比较会重复加标签；也不能靠 equals——见 FileNode 注释）
        val existing = openTabs.indexOfFirst { it.name == node.name && it.source == node.source }
        if (existing >= 0) activeTabIndex = existing
        else {
            openTabs.add(node)
            activeTabIndex = openTabs.lastIndex
        }
        if (node.ext == "md") mdEditMode = false
    }

    fun closeTab(index: Int) {
        if (index in openTabs.indices) {
            openTabs.removeAt(index)
            if (openTabs.isEmpty()) activeTabIndex = 0
            else if (activeTabIndex > index) activeTabIndex--
            else if (activeTabIndex >= openTabs.size) activeTabIndex = openTabs.lastIndex
        }
    }

    fun toggleDir(path: String) {
        if (path in expandedDirs) expandedDirs.remove(path) else expandedDirs.add(path)
    }

    // ── 终端页状态 ────────────────────────────────────────
    var terminalIndex by mutableIntStateOf(0)
    var envReady by mutableStateOf(false)        // rootfs 就绪检测（一键环境配置）
}

/**
 * 上下文用量分类明细（Hermes 上下文卡片同源结构）：
 * 接入 Pi 运行时后由 get_state / 后端 context breakdown 驱动，替换 mock。
 */
data class ContextCategory(
    val id: String,
    val label: String,
    val tokens: Int,
)
