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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 顶栏右下方区域内容（消息区 / 文件内容预览区 / 终端页 / 分支画布 四态切换） */
enum class Panel { MESSAGES, FILES, TERMINAL, TREE }

/**
 * 长会话上屏窗口（2026-09-12）：**按内容高度计价**，不按条数。
 *
 * 条数计价的问题（用户真机实测发现）：同一个「40 条」在模拟器的短消息里约两三屏，
 * 在真机的长回答里十几屏 —— 两端的「手感」完全不同（真机上要滚十几屏才够到
 * 「显示更早的消息」，而且首帧错位时的跳动幅度也大一倍）。参照 Hermes 桌面端
 * 按渲染成本计价（RENDER_BUDGET=600 单位 ≈ 10-20 个 turn）的做法，这里按**估算高度**
 * 折算屏数：首屏 ≈ [WINDOW_SCREENS] 屏内容，每次翻页再放 ≈ [WINDOW_PAGE_SCREENS] 屏。
 */
private const val WINDOW_SCREENS = 2.0f          // 进入会话时上屏的内容 ≈ 2 屏

private const val WINDOW_PAGE_SCREENS = 1.0f     // 每次「显示更早的消息」再上屏 ≈ 1 屏

/** 条数下限：再长的消息也至少上屏这么多条（避免一屏只有一条时按钮贴脸） */
private const val WINDOW_MIN_MESSAGES = 6

/** 剩余更早内容估算不足这么多屏时直接全显（按钮不出现，避免「点一下没变化」） */
private const val WINDOW_TAIL_SCREENS = 0.5f

/**
 * 单条消息的**渲染高度估算**（dp）——只在选择窗口大小时用，不影响渲染。
 *
 * 系数为**实测最小二乘拟合**（2026-09-12，AVD 420dpi / 1080px 宽 / 正文 14sp，
 * 样本 = 五种形态的助手回答 + 短用户消息，逐条比对 LazyColumn 实测高度）：
 *
 * | 形态 | 字符数 | 估算 | 实测 |
 * |---|---|---|---|
 * | 纯中文散文 | 194 | 137 | 173 |
 * | 散文 + 代码块 | 263 | 166 | 270 |
 * | 散文 + 行内公式 | 154 | 120 | 163 |
 * | 散文 + 块级公式 | 134 | 112 | 137 |
 * | 散文 + 表格 | 162 | 124 | 217 |
 * | 用户短消息 | 15 | 53 | 34 |
 *
 * 拟合：助手 ≈ 31 + 0.89×字符（取 30 + 0.9×）；用户 ≈ 28 + 0.5×字符。
 * 精度约 ±20%（表格/长代码块偏低估），对「折算屏数」而言足够。
 */
fun estimatedMessageHeightDp(msg: Msg): Float = when (msg) {
    is Msg.User ->
        28f + 0.5f * msg.text.length +
            if (msg.attachments.isEmpty()) 0f else 30f + 26f * msg.attachments.size +
            if (msg.quote != null) 30f else 0f
    is Msg.Assistant -> 30f + 0.9f * msg.markdown.length
    is Msg.Thinking -> 38f
    is Msg.ToolCall -> 56f
    is Msg.ToolResult -> 40f
    is Msg.Compaction -> 48f
}

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

    // ── 会话条目树（pi session-format v3 同构，2026-09-11：会话内分支的真实承载）──
    // entriesBySession = 会话**全部**条目（含被放弃的分支），id/parentId 链接成树；
    // leafBySession = 当前所在位置（活跃分支末尾条目 id）；messagesBySession
    // 只保存 root→leaf 派生出的上屏消息流（重建见 rebuildMessagesFromLeaf）。
    val entriesBySession = mutableStateMapOf<String, SnapshotStateList<SessionEntry>>()
    val leafBySession = mutableStateMapOf<String, String?>()

    private fun entriesOf(sid: String): SnapshotStateList<SessionEntry> =
        entriesBySession.getOrPut(sid) { mutableStateListOf() }

    /** 新条目 id：pi 同款 8 位 hex，同会话内不重复 */
    private fun newEntryId(existing: List<SessionEntry>): String {
        while (true) {
            val id = java.util.UUID.randomUUID().toString().replace("-", "").take(8)
            if (existing.none { it.id == id }) return id
        }
    }

    /**
     * 追加一条会话条目（父 = 当前 leaf，pi appendMessage 同语义）：写入条目树 +
     * 上屏 + 推进 leaf。navigateToNode 之后再追加即从旧条目长出**新的兄弟分支**
     * （原分支条目保留在树里，不丢）。
     */
    fun appendEntry(msg: Msg) {
        val sid = currentSessionId ?: return
        val list = entriesOf(sid)
        val id = newEntryId(list)
        list += SessionEntry(id, leafBySession[sid], msg)
        leafBySession[sid] = id
        messagesBySession.getOrPut(sid) { mutableStateListOf() } += msg
    }

    /** 由 root→leaf 重建上屏消息流（切分支后调用；条目树本身不动） */
    fun rebuildMessagesFromLeaf(sid: String) {
        val entries = entriesBySession[sid] ?: return
        val byId = entries.associateBy { it.id }
        val path = mutableListOf<Msg>()
        var cur = leafBySession[sid]?.let { byId[it] }
        while (cur != null) {
            path += cur.msg
            cur = cur.parentId?.let { byId[it] }
        }
        path.reverse()
        messagesBySession[sid] = path.toMutableStateList()
    }

    /**
     * 老记录迁移（无条目树的历史会话）：按消息流建线性链，leaf = 末条目。
     * 2026-09-11 之前 state.json 只有扁平消息流，迁移后才能参与 /tree 分支切换。
     */
    fun migrateEntriesIfNeeded(sid: String) {
        if (entriesBySession[sid]?.isNotEmpty() == true) return
        val msgs = messagesBySession[sid]?.toList().orEmpty()
        if (msgs.isEmpty()) return
        val chain = mutableStateListOf<SessionEntry>()
        var parent: String? = null
        msgs.forEach { m ->
            val id = newEntryId(chain)
            chain += SessionEntry(id, parent, m)
            parent = id
        }
        entriesBySession[sid] = chain
        leafBySession[sid] = parent
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
        entriesBySession[id] = mutableStateListOf()
        leafBySession[id] = null
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
        entriesBySession.remove(id)
        leafBySession.remove(id)
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
        entriesBySession.remove(id)
        leafBySession.remove(id)
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

    /**
     * 一次性展开全部时间分组（批量模式进入时调用，2026-09-10 用户要求）：
     * 清掉手动折叠 + 把每组的渐进揭示数拉满（揭示数缺省 0 = 渐进折叠，所以必须逐组写满，
     * 不能 clear 掉 map）。批量勾选时不应有会话被折叠或渐进隐藏挡在列表外。
     */
    fun expandAllTimeGroups(groups: List<SessionGroup>) {
        collapsedTimeGroups.clear()
        groups.forEach { timeGroupRevealed[it.key] = it.sessions.size }
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
        sessions.remove(name)?.forEach { s ->
            messagesBySession.remove(s.id)
            entriesBySession.remove(s.id)
            leafBySession.remove(s.id)
        }
        if (currentProject == name) {
            currentProject = projects.firstOrNull()?.name
            currentSessionId = sessionsFor(currentProject ?: "").firstOrNull()?.id
            // 被删项目的文件树不再有效；FileTreePanel 会按新 currentProject 重载
            fileTreeRoot = null
            fileTreeTruncated = false
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
    suspend fun streamReply(userText: String, quote: Quote? = null) {
        isStreaming = true
        streamDraft = ""
        // ★ 历史快照必须先于消息上屏：buildApiHistory 读 currentMessages，
        //   上屏后再取会把本条用户消息算进历史、又被末尾显式追加一次 = 重复（2026-09-09 修复）
        val historyBefore = buildApiHistory()
        appendEntry(Msg.User(userText, attachments.toList(), quote))
        attachments.clear()
        touchSession(userText)
        markRunning(true)

        val model = selectedModel
        val cfg = model?.provider?.let { AiConfigStore.configs[it] }
        if (cfg == null || model == null) {
            appendEntry(
                Msg.Assistant(
                    "⚠️ 尚未配置可用模型：请在「服务商与模型配置」中添加服务商，填入 API 密钥后填写模型列表或点「刷新」拉取。",
                    error = true,
                )
            )
            isStreaming = false
            streamDraft = ""
            markRunning(false)
            return
        }
        // 所选模型可能不在配置的模型列表内（列表被改）→ 取配置列表首个
        val effectiveModel = model.name.takeIf { cfg.models.contains(it) }
            ?: cfg.models.firstOrNull().orEmpty()

        // 引用消息：正文以 markdown 块引用注入（Quote.toPrompt；UI 仍只显示用户正文）
        val history = historyBefore + ("user" to (quote?.toPrompt(userText) ?: userText))
        val trimmedHistory = trimToContextBudget(history, cfg.ctxLenK)
        try {
            val (text, usage) = runChat(
                cfg = cfg.copy(modelList = effectiveModel),
                history = trimmedHistory,
            ) { draft -> streamDraft = draft }
            appendEntry(Msg.Assistant(text, usage, effectiveModel))
            // 用量台账（用量页数据源）：完成即记一笔（usage 为空 = 服务商未返回用量，不记）
            UsageStore.record(cfg.providerId, effectiveModel, usage)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // abort：保留 abort() 对 draft 的处理
        } catch (e: Exception) {
            appendEntry(
                Msg.Assistant(
                    "⚠️ 请求失败：${e.message ?: "未知错误"}",
                    error = true,
                )
            )
        } finally {
            streamDraft = ""
            isStreaming = false
            markRunning(false)
        }
    }

    /**
     * 重新生成指定助手消息（消息流下标；长按菜单「重新生成」入口，参照 Operit
     * `regenerateSingleAiMessage` 语义）：
     * 用「该消息之前的历史」（含其前一条用户消息）重新请求，结果**原位替换**该条消息
     * （条目树位置不变，分支结构不受影响）；流式开启时逐片刷新，用户可见打字过程。
     * 失败**回退原内容**并返回错误文案（调用方 Toast）——不把用户已看到的内容清空。
     *
     * @return null = 成功；非空 = 失败原因（含运行中被拒的口径）
     */
    suspend fun regenerateMessage(index: Int): String? {
        val list = currentMessages
        val original = list.getOrNull(index) as? Msg.Assistant
            ?: return "该条消息无法重新生成"
        // 只有最下方一条消息支持重新生成（2026-09-11 用户定）：中间消息重生成会与其后的
        // 对话上下文脱节（后续消息引用的正是旧回答），仅在会话末尾语义成立。
        if (index != list.lastIndex) return "仅最后一条消息支持重新生成"
        if (isStreaming) return "当前已有消息在处理中，请稍后再试"
        val model = selectedModel ?: return "尚未配置可用模型"
        val cfg = AiConfigStore.configs[model.provider] ?: return "尚未配置可用模型"
        // 历史 = 该消息之前的部分（其前一条用户消息已包含在内），不含本条与之后内容
        val history = trimToContextBudget(apiHistoryOf(list.subList(0, index)), cfg.ctxLenK)
        if (history.isEmpty()) return "缺少可用的上下文"
        val effectiveModel = model.name.takeIf { cfg.models.contains(it) }
            ?: cfg.models.firstOrNull().orEmpty()

        isStreaming = true
        streamDraft = ""
        markRunning(true)
        return try {
            val (text, usage) = runChat(
                cfg = cfg.copy(modelList = effectiveModel),
                history = history,
            ) { draft -> replaceMessageAt(index, Msg.Assistant(draft, null, effectiveModel)) }
            replaceMessageAt(index, Msg.Assistant(text, usage, effectiveModel))
            // 重新生成同样计入用量台账（一次真实请求 = 一笔用量）
            UsageStore.record(cfg.providerId, effectiveModel, usage)
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            replaceMessageAt(index, original)   // 中止：恢复原内容
            throw e
        } catch (e: Exception) {
            replaceMessageAt(index, original)
            e.message ?: "未知错误"
        } finally {
            streamDraft = ""
            isStreaming = false
            markRunning(false)
        }
    }

    /**
     * 单次对话请求（流式 / 非流式共用一条路径，2026-09-11 抽出供发送与重新生成复用）：
     * 流式逐片回调 onDelta，返回 (完整文本, usage)。
     */
    private suspend fun runChat(
        cfg: ProviderConfig,
        history: List<Pair<String, String>>,
        onDelta: (String) -> Unit,
    ): Pair<String, Usage?> {
        val thinking = if (thinkingEnabled) thinkingLevel else null
        if (!streamingOutputEnabled) {
            val result = AiBackend.chat(cfg, systemPrompt, history, thinking)
            return result.text to result.usage
        }
        val sb = StringBuilder()
        var usage: Usage? = null
        AiBackend.chatStream(cfg, systemPrompt, history, thinking).collect { ev ->
            when (ev) {
                is ChatEvent.TextDelta -> {
                    sb.append(ev.text)
                    onDelta(sb.toString())
                }
                is ChatEvent.UsageEvent -> usage = ev.usage
                is ChatEvent.Failed -> throw AiException(ev.message)
                ChatEvent.Done -> Unit
            }
        }
        return sb.toString() to usage
    }

    /**
     * 原位替换消息流第 index 条消息（条目树同步）：leaf 路径第 index 个条目 = 该消息，
     * 只改其 msg、不动树结构——分支画布的节点/连线与 leaf 保持不变。
     */
    fun replaceMessageAt(index: Int, msg: Msg) {
        val sid = currentSessionId ?: return
        val list = messagesBySession[sid] ?: return
        if (index !in list.indices) return
        list[index] = msg
        val entries = entriesBySession[sid] ?: return
        val byId = entries.associateBy { it.id }
        val path = mutableListOf<SessionEntry>()
        var cur = leafBySession[sid]?.let { byId[it] }
        while (cur != null) {
            path += cur
            cur = cur.parentId?.let { byId[it] }
        }
        path.reverse()
        val target = path.getOrNull(index) ?: return
        val i = entries.indexOfFirst { it.id == target.id }
        if (i >= 0) entries[i] = entries[i].copy(msg = msg)
    }

    /** API 上下文重建：仅 User/Assistant 且跳过 error 消息（上限 40 条防过长） */
    private fun buildApiHistory(): List<Pair<String, String>> = apiHistoryOf(currentMessages)

    private fun apiHistoryOf(msgs: List<Msg>): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        msgs.forEach { m ->
            when (m) {
                is Msg.User -> out += "user" to (m.quote?.toPrompt(m.text) ?: m.text)
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
            appendEntry(Msg.Assistant(streamDraft, null))
            streamDraft = ""
        }
        isStreaming = false
        markRunning(false)
    }

    // ── 分支（2026-09-02 分支功能设计：/tree 画布页 + fork；2026-09-11 真实化）──

    /**
     * /tree 画布页会话树（2026-09-11 起由真实条目树派生，不再是 mock/null）：
     * 节点 = 一条用户消息；exchange = 该节点回合条目（用户消息 + 其后单链非用户条目
     * = AI 回答/思考/工具过程）；children = 从该节点长出的用户消息分支；
     * active = 当前 leaf 上溯路径上的节点（活跃分支）。
     * 接入 pi 运行时后改由 SDK `getTree()` 同源数据驱动。
     */
    val branchTree: SessionTreeNode? get() = buildBranchTree(currentSessionId)

    fun buildBranchTree(sid: String?): SessionTreeNode? {
        if (sid == null) return null
        val entries = entriesBySession[sid]?.toList().orEmpty()
        if (entries.isEmpty()) return null
        val byId = entries.associateBy { it.id }
        val childrenOf = entries.groupBy { it.parentId }

        // 回合链：用户条目 → 其后「唯一非用户子条目」链（遇用户子条目 = 下一条对话，停）
        fun turnChain(u: SessionEntry): List<SessionEntry> {
            val out = mutableListOf(u)
            var cur = u
            while (true) {
                val next = childrenOf[cur.id].orEmpty().filter { it.msg !is Msg.User }
                if (next.size != 1) break
                out += next[0]
                cur = next[0]
            }
            return out
        }

        // 归属节点：条目自身向上找最近的用户条目祖先（跳过 AI 回答/工具条目）
        fun ownerIdOf(e: SessionEntry): String? {
            var p = e.parentId?.let { byId[it] }
            while (p != null) {
                if (p.msg is Msg.User) return p.id
                p = p.parentId?.let { byId[it] }
            }
            return null
        }

        val userEntries = entries.filter { it.msg is Msg.User }
        val userChildrenOf = userEntries.groupBy { ownerIdOf(it) }

        // 活跃路径：当前 leaf 上溯遇到的用户条目（含 leaf 所在回合的节点）
        val activeIds = mutableSetOf<String>()
        var cur = leafBySession[sid]?.let { byId[it] }
        while (cur != null) {
            if (cur.msg is Msg.User) activeIds += cur.id
            cur = cur.parentId?.let { byId[it] }
        }

        fun node(u: SessionEntry): SessionTreeNode = SessionTreeNode(
            id = u.id,
            userText = (u.msg as Msg.User).text,
            exchange = turnChain(u).map { it.msg },
            children = userChildrenOf[u.id].orEmpty().map { node(it) },
            branchLabel = null,
            active = u.id in activeIds,
        )

        return userChildrenOf[null].orEmpty().firstOrNull()?.let { node(it) }
    }

    /**
     * /tree 画布页切到目标节点（会话内分支，pi `branch(entryId)` / pi-web 封装命令
     * `navigate_tree`（底层 SDK `navigateTree()`）同语义）：leaf 移到该节点**回合末尾**
     * （用户消息 + 其 AI 回答），上屏消息流由 root→leaf 重建——满足验收口径「返回消息区
     * 最后一条对话消息 = 所选节点那次对话的末尾消息」。
     * 非破坏性：不删任何条目，被切走的分支仍留在条目树里；此后继续发消息 = 从该节点
     * 长出新的兄弟分支。
     */
    fun navigateToNode(nodeId: String): Boolean {
        val sid = currentSessionId ?: return false
        val entries = entriesBySession[sid]?.toList().orEmpty()
        val u = entries.firstOrNull { it.id == nodeId && it.msg is Msg.User } ?: return false
        var end = u
        while (true) {
            val next = entries.filter { it.parentId == end.id && it.msg !is Msg.User }
            if (next.size != 1) break
            end = next[0]
        }
        leafBySession[sid] = end.id
        rebuildMessagesFromLeaf(sid)
        return true
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
        // 新会话条目树 = 前缀线性链（全新 id，leaf = 末条目）：新会话自带完整上下文、
        // 并能独立继续分叉（/tree 画布页与继续发消息都可用）
        val chain = mutableStateListOf<SessionEntry>()
        var parent: String? = null
        prefix.forEach { m ->
            val eid = newEntryId(chain)
            chain += SessionEntry(eid, parent, m)
            parent = eid
        }
        entriesBySession[newId] = chain
        leafBySession[newId] = parent
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

    // ── 消息窗口（长会话防护，2026-09-12）──────────────────
    // 参考 Hermes 桌面端长会话（components/assistant-ui/thread/list.tsx 的 showEarlier）：
    // 只把最近一段内容交给列表渲染，更早的靠「显示更早的消息」一页一页往前翻。
    // 计价用**估算高度**（屏数）而不是条数——条数在长短消息差异大的两端表现完全不同
    // （用户真机实测发现，2026-09-12）。每会话独立记「已展开屏数」，仅内存态不落盘。
    private val messageWindowPagesBySession = mutableStateMapOf<String, Float>()

    /**
     * 上屏窗口起点（0 = 全部消息都在窗口内）：从最新一条往前累计**估算高度**，
     * 直到达到「该会话已展开的屏数」× [screenDp]，再按条数下限兜底。
     * 剩余更早内容估算不足 [WINDOW_TAIL_SCREENS] 屏时直接全显（按钮不出现）。
     */
    fun messageWindowStart(sessionId: String?, messages: List<Msg>, screenDp: Float): Int {
        if (sessionId == null || messages.isEmpty() || screenDp <= 0f) return 0
        val targetDp = (messageWindowPagesBySession[sessionId] ?: WINDOW_SCREENS) * screenDp
        var acc = 0f
        var count = 0
        var i = messages.lastIndex
        while (i >= 0) {
            acc += estimatedMessageHeightDp(messages[i])
            count++
            i--
            if (acc >= targetDp && count >= WINDOW_MIN_MESSAGES) break
        }
        val start = (i + 1).coerceAtLeast(0)
        if (start == 0) return 0
        // 更早的剩余内容太少 → 直接全显（避免按钮点一下几乎没变化）
        var older = 0f
        for (k in 0 until start) older += estimatedMessageHeightDp(messages[k])
        return if (older < screenDp * WINDOW_TAIL_SCREENS) 0 else start
    }

    /** 「显示更早的消息」：再展开一屏；返回**本次新增条数**（列表据此保持视口锚点） */
    fun showEarlierMessages(sessionId: String?, messages: List<Msg>, screenDp: Float): Int {
        if (sessionId == null) return 0
        val before = messageWindowStart(sessionId, messages, screenDp)
        if (before == 0) return 0
        val cur = messageWindowPagesBySession[sessionId] ?: WINDOW_SCREENS
        messageWindowPagesBySession[sessionId] = cur + WINDOW_PAGE_SCREENS
        val after = messageWindowStart(sessionId, messages, screenDp)
        return before - after
    }

    /** 定位跳转前把目标消息纳入窗口（按目标到末尾的估算高度把屏数放大到够用） */
    fun ensureMessageVisible(sessionId: String?, messages: List<Msg>, screenDp: Float, index: Int) {
        if (sessionId == null || messages.isEmpty() || index < 0 || screenDp <= 0f) return
        if (index >= messageWindowStart(sessionId, messages, screenDp)) return
        var acc = 0f
        for (k in index..messages.lastIndex) acc += estimatedMessageHeightDp(messages[k])
        val needed = acc / screenDp + 0.2f
        val cur = messageWindowPagesBySession[sessionId] ?: WINDOW_SCREENS
        if (needed > cur) messageWindowPagesBySession[sessionId] = needed
    }

    // ── 文件页状态 ────────────────────────────────────────
    // 文件树（2026-09-02 真实化：当前项目真实目录；null = 尚未加载或目录不存在时由 UI 回退演示树）
    var fileTreeRoot by mutableStateOf<FileNode?>(null)

    /** 文件树正在后台扫描（面板显示加载态；扫描全程不占主线程） */
    var fileTreeLoading by mutableStateOf(false)

    /** 树因「节点数/时间预算」或单目录上限被截断（面板页脚提示「仅显示部分文件」） */
    var fileTreeTruncated by mutableStateOf(false)

    /** 请求序号：项目快速切换/连点刷新时丢弃过期结果 */
    private var treeRequestSeq = 0

    /**
     * 应用级协程作用域：只承载「后台刷新」类调用方不等待的任务（文件树重载）。
     * ChatState 与进程同生命周期（PientApp 根部 remember），无需取消。
     */
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 非挂起入口：后台重载文件树（点击刷新 / 保存后 / 增删改后等场景，调用方不等待） */
    fun refreshFileTreeAsync(context: Context) {
        bgScope.launch { refreshFileTree(context) }
    }

    /**
     * 重载当前项目文件树（真实文件系统；目录不存在时 fileTreeRoot 置 null）。
     *
     * **扫描一定在 IO 线程**（2026-09-12 修复「项目文件夹里文件一多，打开应用就卡死」）：
     * 旧实现在主线程同步递归整棵树，SAF 项目每个文件还要 4 次 ContentProvider 查询 ——
     * 4000 个文件 ≈ 1.6 万次 IPC，主线程被占住数分钟 → ANR（实测复现）。
     * 现改为：IO 线程扫描 + 请求序号防竞态；调用方可直接 await（LaunchedEffect），
     * 不必关心线程（suspend 返回时状态已就绪）。
     */
    suspend fun refreshFileTree(context: Context) {
        val project = projects.firstOrNull { it.name == currentProject }
        val seq = ++treeRequestSeq
        if (project == null) {
            fileTreeRoot = null
            fileTreeTruncated = false
            fileTreeLoading = false
            return
        }
        fileTreeLoading = true
        val appContext = context.applicationContext
        val tree = withContext(Dispatchers.IO) { ProjectFiles.loadTree(appContext, project) }
        if (seq != treeRequestSeq) return      // 已有更新的请求在途，丢弃本次结果
        fileTreeRoot = tree
        fileTreeTruncated = tree?.truncated == true
        fileTreeLoading = false
    }

    val openTabs = mutableStateListOf<FileNode>()
    var activeTabIndex by mutableIntStateOf(0)
    // 源码/预览切换（markdown 与 html 共用；默认进预览）：预览 = md 渲染 / html 渲染，源码 = 可编辑文本
    var sourceEditMode by mutableStateOf(false)
    // 代码文件底部符号工具栏是否可见（由 FileContentView 的 CodeSourceEditor 汇报：进入置真、离开复位）。
    // FilesPanel 据此把右下 FAB 抬到工具栏之上——二进制 / 超限文件走提示分支不显示工具栏，
    // 只有渲染方知道，故不做扩展名的静态推测（否则 zip 之类会凭空抬起 FAB）
    var symbolToolbarVisible by mutableStateOf(false)
    // 行号不设开关（2026-09-10 用户定）：是否显示由预览的文件类型决定，见 FileContentView.CodeView
    val expandedDirs = mutableStateSetOf<String>() // 文件树展开路径
    // 文件树长按菜单「@ 提及插入输入框」请求（ChatScreen 消费后置 null）
    var mentionInsertRequest by mutableStateOf<String?>(null)

    // ── 编辑态（2026-09-10：文本/代码可编辑，参照 Operit 工作区编辑器）──
    /** 文件键：同一文件 = 名称 + 真实位置（与 openFile 判定口径一致） */
    fun fileKey(node: FileNode): String = (node.source ?: "") + "|" + node.name

    /** 编辑缓冲（键 → 当前文本；文件读取完成时播种，切标签/重组不丢） */
    val fileDrafts = mutableStateMapOf<String, String>()

    /** 未保存文件键集合（编辑器有改动未写回磁盘） */
    val unsavedFiles = mutableStateSetOf<String>()

    /** 关闭未保存文件的确认弹窗目标（页根浮层；null = 未打开） */
    var closingTabIndex by mutableStateOf<Int?>(null)

    fun isUnsaved(node: FileNode): Boolean = fileKey(node) in unsavedFiles

    /** 读取完成播种（不标记未保存；已有缓冲不覆盖） */
    fun seedDraft(node: FileNode, text: String) {
        val k = fileKey(node)
        if (fileDrafts[k] == null) fileDrafts[k] = text
    }

    /** 编辑改动 → 写入缓冲并标记未保存 */
    fun editDraft(node: FileNode, text: String) {
        val k = fileKey(node)
        fileDrafts[k] = text
        unsavedFiles.add(k)
    }

    /** 丢弃缓冲（关闭标签 / 放弃改动） */
    fun discardDraft(node: FileNode) {
        val k = fileKey(node)
        fileDrafts.remove(k)
        unsavedFiles.remove(k)
    }

    /** 保存：缓冲写回磁盘（成功清未保存标记 + 刷新文件树，供大小/时间显示更新） */
    fun saveFile(context: Context, node: FileNode): Boolean {
        val k = fileKey(node)
        val text = fileDrafts[k] ?: return false
        val ok = ProjectFiles.writeText(context, node, text)
        if (ok) {
            unsavedFiles.remove(k)
            refreshFileTreeAsync(context)
        }
        return ok
    }

    fun openFile(node: FileNode) {
        // 同一文件 = 名称 + 真实位置相同（FileNode 无 equals：文件树每次打开都重建
        // 节点实例，引用比较会重复加标签；也不能靠 equals——见 FileNode 注释）
        val existing = openTabs.indexOfFirst { it.name == node.name && it.source == node.source }
        if (existing >= 0) activeTabIndex = existing
        else {
            openTabs.add(node)
            activeTabIndex = openTabs.lastIndex
        }
        if (node.ext in SOURCE_TOGGLE_EXTS) sourceEditMode = false   // markdown / html 一律从渲染模式进入
    }

    fun closeTab(index: Int) {
        if (index in openTabs.indices) {
            discardDraft(openTabs[index])   // 关闭 = 丢弃缓冲（未保存内容的取舍由确认弹窗先行处理）
            openTabs.removeAt(index)
            if (openTabs.isEmpty()) activeTabIndex = 0
            else if (activeTabIndex > index) activeTabIndex--
            else if (activeTabIndex >= openTabs.size) activeTabIndex = openTabs.lastIndex
        }
    }

    /** 关闭标签入口：有未保存改动 → 弹确认（页根浮层），否则直接关 */
    fun requestCloseTab(index: Int) {
        val node = openTabs.getOrNull(index) ?: return
        if (isUnsaved(node)) closingTabIndex = index else closeTab(index)
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
