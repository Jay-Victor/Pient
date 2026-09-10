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
import kotlinx.coroutines.flow.collect

/** 顶栏右下方区域内容（消息区 / 文件内容预览区 / 终端页 / 分支画布 四态切换） */
enum class Panel { MESSAGES, FILES, TERMINAL, TREE }

/**
 * 聊天主页应用状态（跨导航保活：提升到 NavHost 外层）。
 * UI 原型阶段：状态以内存承载；接入 Pi 运行时后由 SessionManager /
 * get_state / get_available_models 等官方机制驱动。
 */
class ChatState {

    // ── 会话 ──────────────────────────────────────────────
    // 2026-09-08：移除全部 mock 项目/会话/消息——初次进入无项目（聊天页显示引导），
    // 项目由用户经「创建项目（绑定文件夹）」真实创建。
    var currentProject by mutableStateOf<String?>(null)
    var currentSessionId by mutableStateOf<String?>(null)

    // ── UI 状态（跨导航保活：提升到 NavHost 外层，避免页面往返丢失）──
    // 会话侧栏展开状态。手机端抽屉导航即关闭（无感）；平板压缩模式保持展开，
    // 进入二级页再返回聊天页时侧边栏仍是展开态（持久侧边栏语义）。
    var drawerOpen by mutableStateOf(false)

    val projects = mutableStateListOf<Project>()

    val sessions = mutableStateMapOf<String, SnapshotStateList<Session>>()
    val messagesBySession = mutableStateMapOf<String, SnapshotStateList<Msg>>()

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

    /** 会话记录恢复用（ChatStore.load）：仅加入列表，不切换当前项目 */ 
    fun addProjectSilently(name: String, path: String, uri: String?) {
        if (name.isBlank() || projects.any { it.name == name }) return
        projects += Project(name, path, uri)
        if (name !in sessions) sessions[name] = mutableStateListOf()
    }

    /** 恢复后归一化：指针字段与已选模型校验（配置被删/记录损坏时回退） */
    fun normalizeAfterLoad() {
        if (currentProject != null && projects.none { it.name == currentProject }) {
            currentProject = projects.firstOrNull()?.name
        }
        val proj = currentProject
        if (currentSessionId != null && sessionsFor(proj ?: "").none { it.id == currentSessionId }) {
            currentSessionId = sessionsFor(proj ?: "").firstOrNull()?.id
        }
        // 已选模型不在当前可用列表：回退第一个可用模型（无可用模型则留空）
        if (availableModels.none { it.id == selectedModelId }) {
            selectedModelId = availableModels.firstOrNull()?.id.orEmpty()
        }
    }

    val currentMessages: SnapshotStateList<Msg>
        get() {
            val id = currentSessionId ?: return mutableStateListOf()
            return messagesBySession.getOrPut(id) { mutableStateListOf() }
        }

    val currentSession: Session?
        get() = currentProject?.let { sessions[it] }.orEmpty()
            .firstOrNull { it.id == currentSessionId }

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
        val proj = currentProject ?: return "" // 未绑定项目：调用方 Toast 提示
        val list = sessions.getOrPut(proj) { mutableStateListOf() }
        val id = "s-${System.currentTimeMillis()}"
        list.add(0, Session(id, "新建会话", proj, updatedAt = System.currentTimeMillis()))
        currentSessionId = id
        messagesBySession[id] = mutableStateListOf()
        activePanel = Panel.MESSAGES
        return id
    }

    /**
     * 会话活动打点（发消息时调用）：更新最后活动时间与侧栏时间标签；
     * 首条用户消息自动命名会话（前 20 字，pi-web 同语义——会话记录友好）。
     */
    private fun touchSession(firstUserText: String?) {
        val proj = currentProject ?: return
        val id = currentSessionId ?: return
        val list = sessions[proj] ?: return
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return
        val old = list[i]
        val now = System.currentTimeMillis()
        val autoTitle = if (old.title == "新建会话" && !firstUserText.isNullOrBlank()) {
            val t = firstUserText.trim().take(20)
            if (firstUserText.trim().length > 20) "$t…" else t
        } else old.title
        list[i] = old.copy(title = autoTitle, updatedAt = now)
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
        val proj = currentProject ?: return
        sessions[proj]?.removeAll { it.id == id }
        messagesBySession.remove(id)
        if (currentSessionId == id) currentSessionId = sessionsFor(proj).firstOrNull()?.id
        // 删除最后一个会话后自动新建（2026-09-09 用户定：侧边栏会话列表恒有会话）
        if (sessionsFor(proj).isEmpty()) newSession()
    }

    /** 跨项目按 id 删除会话（项目管理页使用；含消息记录与当前会话指针处理） */
    fun deleteSessionById(id: String) {
        for (list in sessions.values) {
            if (list.removeAll { it.id == id }) break
        }
        messagesBySession.remove(id)
        if (currentSessionId == id) {
            currentSessionId = sessionsFor(currentProject ?: "").firstOrNull()?.id
        }
    }

    /** 置顶/取消置顶会话 */
    fun togglePin(id: String) {
        val list = currentProject?.let { sessions[it] } ?: return
        list.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { i ->
            val old = list[i]
            list[i] = old.copy(pinned = !old.pinned)
        }
    }

    // 时间分组折叠状态（2026-09-09：Hermes 侧栏日历桶折叠同款——组头保留、
    // 组内会话隐藏；键 = 日历桶 key，缺省展开；跨抽屉开关/导航保活，重启重置）
    val collapsedTimeGroups = mutableStateSetOf<String>()

    fun toggleTimeGroup(key: String) {
        if (key in collapsedTimeGroups) collapsedTimeGroups.remove(key) else collapsedTimeGroups.add(key)
    }

    // 时间分组渐进揭示（2026-09-09 用户要求）：三点按键每点揭示 5 个会话；
    // 组完全揭示后下一更老分组解锁为新的折叠渐进组。揭示即自动展开该组。
    val timeGroupRevealed = mutableStateMapOf<String, Int>()

    fun revealMoreTimeGroup(key: String, groupSize: Int) {
        collapsedTimeGroups.remove(key)
        val cur = timeGroupRevealed[key] ?: 0
        timeGroupRevealed[key] = minOf(cur + 5, groupSize)
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
     * 将项目移出列表：连同其会话与消息记录一并移除。
     * 删除当前项目时切换到第一个剩余项目；删空后回到未绑定状态（聊天页显示引导）。
     * 「删除项目」= 调用方先删文件夹（ProjectFiles.deleteProjectRoot）再调本方法；
     * 「解绑项目」= 直接调本方法（保留文件夹）。
     */
    fun removeProject(name: String) {
        if (!projects.removeAll { it.name == name }) return
        sessions.remove(name)?.forEach { s -> messagesBySession.remove(s.id) }
        if (currentProject == name) {
            currentProject = projects.firstOrNull()?.name
            currentSessionId = sessionsFor(currentProject ?: "").firstOrNull()?.id
            // 被删项目的文件树不再有效；FileTreePanel 会按新 currentProject 重载
            fileTreeRoot = null
        }
    }

    // ── 真实对话发送（2026-09-09 实现 AI 接入，替换 mock 流式回复）──
    var isStreaming by mutableStateOf(false)
    var streamDraft by mutableStateOf("")
    var streamJob: Job? = null

    /**
     * 发送消息并请求 AI 回复：历史重建 = 当前会话的 User/Assistant（跳过错误消息与
     * 思考/工具条目），system prompt 走 ChatState.systemPrompt；思考级别/流式开关/
     * 模型参数均来自输入栏与配置页状态。请求失败以 error 助手消息呈现（不进 API 上下文）。
     */
    suspend fun streamReply(userText: String) {
        isStreaming = true
        streamDraft = ""
        // ★ 历史快照必须先于消息上屏：buildApiHistory 读 currentMessages，
        //   上屏后再取会把本条用户消息算进历史、又被末尾显式追加一次 = 重复（2026-09-09 修复）
        val historyBefore = buildApiHistory()
        currentMessages += Msg.User(userText, attachments.toList())
        attachments.clear()
        touchSession(userText)
        markRunning(true)

        val model = selectedModel
        val cfg = model?.provider?.let { AiConfigStore.configs[it] }
        if (cfg == null || model == null) {
            currentMessages += Msg.Assistant(
                "⚠️ 尚未配置可用模型：请在「服务商与模型配置」中添加服务商，填入 API 密钥后填写模型列表或点「刷新」拉取。",
                error = true,
            )
            isStreaming = false
            streamDraft = ""
            markRunning(false)
            return
        }
        // 所选模型可能不在配置的模型列表内（列表被改）→ 取配置列表首个
        val effectiveModel = model.name.takeIf { cfg.models.contains(it) }
            ?: cfg.models.firstOrNull().orEmpty()

        val history = historyBefore + ("user" to userText)
        val trimmedHistory = trimToContextBudget(history, cfg.ctxLenK)
        val thinking = if (thinkingEnabled) thinkingLevel else null
        try {
            var usage: Usage? = null
            if (streamingOutputEnabled) {
                val sb = StringBuilder()
                AiBackend.chatStream(
                    cfg = cfg.copy(modelList = effectiveModel),
                    systemPrompt = systemPrompt,
                    history = trimmedHistory,
                    thinkingLevel = thinking,
                ).collect { ev ->
                    when (ev) {
                        is ChatEvent.TextDelta -> {
                            sb.append(ev.text)
                            streamDraft = sb.toString()
                        }
                        is ChatEvent.UsageEvent -> usage = ev.usage
                        is ChatEvent.Failed -> throw AiException(ev.message)
                        ChatEvent.Done -> Unit
                    }
                }
                currentMessages += Msg.Assistant(sb.toString(), usage, effectiveModel)
            } else {
                val result = AiBackend.chat(
                    cfg = cfg.copy(modelList = effectiveModel),
                    systemPrompt = systemPrompt,
                    history = trimmedHistory,
                    thinkingLevel = thinking,
                )
                currentMessages += Msg.Assistant(result.text, result.usage, effectiveModel)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // abort：保留 abort() 对 draft 的处理
        } catch (e: Exception) {
            currentMessages += Msg.Assistant(
                "⚠️ 请求失败：${e.message ?: "未知错误"}",
                error = true,
            )
        } finally {
            streamDraft = ""
            isStreaming = false
            markRunning(false)
        }
    }

    /** API 上下文重建：仅 User/Assistant 且跳过 error 消息（上限 40 条防过长） */
    private fun buildApiHistory(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        currentMessages.forEach { m ->
            when (m) {
                is Msg.User -> out += "user" to m.text
                is Msg.Assistant -> if (!m.error) out += "assistant" to m.markdown
                else -> Unit // 思考/工具/结果/压缩不参与基本对话上下文
            }
        }
        return out.takeLast(40)
    }

    /**
     * 上下文长度预算裁剪（配置页「上下文长度」K Tokens 生效）：
     * 粗略估算 token ≈ 字符数/2（中英混排折中），超预算丢最旧条目；
     * 但永远保留最新一条用户消息（本轮提问不可丢）。
     */
    private fun trimToContextBudget(
        history: List<Pair<String, String>>,
        ctxLenK: String,
    ): List<Pair<String, String>> {
        val k = ctxLenK.toIntOrNull() ?: return history
        if (k <= 0) return history
        val charBudget = (k * 1000L) * 2 // 1 token ≈ 2 字符（中英折中）
        var used = history.sumOf { (_, c) -> c.length.toLong() }
        if (used <= charBudget) return history
        var start = 0
        while (start < history.size - 1 && used > charBudget) {
            used -= history[start].second.length
            start++
        }
        // 若仍超预算（单条超长），至少保留最后一条
        return if (start <= history.lastIndex) history.subList(start, history.size) else history.takeLast(1)
    }

    private fun markRunning(running: Boolean) {
        val proj = currentProject ?: return
        val list = sessions[proj] ?: return
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

    /** /tree 画布页会话树（2026-09-08 起无 mock 树——原型期无演示会话，接入运行时后由会话树数据驱动） */
    val branchTree: SessionTreeNode? get() = null

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
        val proj = currentProject ?: return ""
        val id = currentSessionId ?: return ""
        val src = messagesBySession[id]?.toList() ?: return ""
        if (entryIndex !in src.indices) return ""
        val newId = "s-${System.currentTimeMillis()}"
        val prefix = src.subList(0, entryIndex + 1).toMutableStateList()
        val list = sessions.getOrPut(proj) { mutableStateListOf() }
        list.add(0, Session(newId, forkTitle(src, entryIndex), proj, updatedAt = System.currentTimeMillis()))
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
    // 模型数据源（2026-09-09 起）：已配置服务商的模型列表（AiConfigStore）；
    // id = "providerId/modelName"。
    var selectedModelId by mutableStateOf("")
    var thinkingEnabled by mutableStateOf(false)
    var thinkingLevel by mutableStateOf(ThinkingLevel.MEDIUM)
    var streamingOutputEnabled by mutableStateOf(true) // 流式输出开关（模型选择器"输出"栏）

    /** 聊天页可用模型 = 已配置服务商模型列表（模型切换数据源） */
    val availableModels: List<AiModel>
        get() = AiConfigStore.configs.values.flatMap { cfg ->
            cfg.models.map { AiModel("${cfg.providerId}/$it", it, cfg.providerId) }
        }

    val selectedModel: AiModel?
        get() = availableModels.firstOrNull { it.id == selectedModelId }
            ?: availableModels.firstOrNull()

    // AI 已配置标记（2026-09-08 用户定：聊天页首次引导第二步）：模型配置页「测试连接」
    // 成功即置真（持久化于 AiConfigStore，2026-09-09 起）；与项目一起作为聊天页引导的
    // 两个完成条件，两者齐备才显示输入栏。
    var aiConfigured: Boolean
        get() = AiConfigStore.aiConfigured
        set(value) {
            AiConfigStore.aiConfigured = value
        }

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
