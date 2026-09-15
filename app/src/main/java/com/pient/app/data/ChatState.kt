package com.pient.app.data

import com.pient.app.AppCtx

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import com.pient.app.runtime.PiRpc
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.withContext

private const val TAG_CHAT = "PientChat"

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

/** 旧会话里的单个工具结果进入对话历史时的字符上限（按「够模型接着推理」收窄） */
private const val TOOL_HISTORY_CHARS = 2000

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
    // 思考块：折叠头行 38dp + 展开正文（0.9×字符）。估算按展开态取——Hermes 口径下
    // 流式思考默认展开、结束也保持展开，展开是它最常见的形态；收起态略高估不影响窗口判定。
    is Msg.Thinking -> 38f + 0.9f * msg.text.length
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

    private companion object {
        /** 日志 tag（上下文压缩等取证日志，便于 logcat 一条命令过滤） */
        const val TAG = "Pient"
    }

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
    fun appendEntry(msg: Msg): Int {
        val sid = currentSessionId ?: return -1
        val at = runInsertAt
        if (at != null) {
            insertEntryAt(at, msg)
            runInsertAt = at + 1
            return at
        }
        val list = entriesOf(sid)
        val id = newEntryId(list)
        list += SessionEntry(id, leafBySession[sid], msg)
        leafBySession[sid] = id
        val screen = messagesBySession.getOrPut(sid) { mutableStateListOf() }
        screen += msg
        return screen.lastIndex
    }

    /**
     * 在主屏消息流的 [index] 处插入一条条目（条目树同步：新条目父 = 原 index-1 位置的条目，
     * 原 index 位置的条目改挂到新条目下，叶子与分支关系不变）。
     * 重新生成时本轮的思考/工具卡/结果都走这里——插在目标回答**之前**，不追加到末尾。
     */
    private fun insertEntryAt(index: Int, msg: Msg) {
        val sid = currentSessionId ?: return
        val screen = messagesBySession.getOrPut(sid) { mutableStateListOf() }
        if (index !in 0..screen.size) return
        val entries = entriesBySession[sid] ?: return
        val path = leafPath(sid)
        val parent = path.getOrNull(index - 1)
        val child = path.getOrNull(index)
        val id = newEntryId(entries)
        entries += SessionEntry(id, parent?.id, msg)
        if (child != null) {
            val ci = entries.indexOfFirst { it.id == child.id }
            if (ci >= 0) entries[ci] = entries[ci].copy(parentId = id)
        }
        screen.add(index, msg)
    }

    /** 由 root→leaf 重建上屏消息流（切分支后调用；条目树本身不动） */
    fun rebuildMessagesFromLeaf(sid: String) {
        val entries = entriesBySession[sid] ?: return
        // 上屏窗口起点按「消息下标」记：切分支/换叶后同一会话的消息流换了内容，
        // 旧下标不再对应同一段消息 → 清掉记录，让窗口回到「最近 N 屏」自动口径。
        messageWindowStartBySession.remove(sid)
        liveThinkingIndex = -1   // 换叶/切分支后下标全部重排，旧标记作废
        messagesBySession[sid] = leafPath(sid).map { it.msg }.toMutableStateList()
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
     * 输入名称 → `filesDir/Projects/<name>` 真实目录（2026-09-14 起唯一形态；SAF「选择本地文件夹」已按用户要求移除）。
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
        liveThinkingIndex = -1   // 换会话：上一次的流式思考块不再享受展开（Hermes 历史态收起）
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
        liveThinkingIndex = -1
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
    /**
     * 流式思考文本（思考模式开启、且服务商回推理增量时非空）。
     * Hermes 口径：流式期间思考块**默认展开**并实时预览（见 ChatMessages 的思考折叠块）。
     * 与 streamDraft 一样只存内存、不进持久化快照（避免每个增量触发一次写盘）。
     */
    var streamThinking by mutableStateOf("")

    /** 本轮思考起点（毫秒；0 = 本轮尚无思考）——实时计时与落库 durationMs 都取它 */
    var streamThinkingStartedAt by mutableStateOf(0L)

    /**
     * 本次运行内**流式思考块**所在的消息下标（-1 = 无）。
     * Hermes 口径的「live preview 保持展开」：流式期间默认展开的思考块在回答落地、换成
     * 真实条目后不得突然折叠（那正是 Hermes 注释里要避免的 settle 跳动）；判据只存内存，
     * 换会话/换分支/重启后归 -1 → 历史思考块一律收起，用户可手动展开。
     */
    var liveThinkingIndex by mutableStateOf(-1)
    var streamJob: Job? = null

    /**
     * **pi 通道开关**（2026-09-14）：开 = 对话由 guest(Ubuntu) 里的 pi 跑（它自己维护会话上下文、
     * 自己拿工具），关 = 退回 Pient 内核直连（[AiBackend]）。通道起不来（Ubuntu/pi 未就绪）时
     * 自动落回直连，聊天不断。
     */
    var piChannelEnabled: Boolean = true

    /** pi 通道最近一次报错（回合内收敛，供本回合失败时如实抛给 UI） */
    private var lastPiError: String? = null

    /**
     * 发送消息并请求 AI 回复：历史重建 = 当前会话的 User/Assistant（跳过错误消息与
     * 思考/工具条目），system prompt 走 ChatState.systemPrompt；思考级别/流式开关/
     * 模型参数均来自输入栏与配置页状态。请求失败以 error 助手消息呈现（不进 API 上下文）。
     */
    suspend fun streamReply(userText: String, quote: Quote? = null) {
        isStreaming = true
        streamDraft = ""
        streamThinking = ""
        streamThinkingStartedAt = 0L
        // ★ 历史快照必须先于消息上屏：buildApiHistory 读 currentMessages，
        //   上屏后再取会把本条用户消息算进历史、又被末尾显式追加一次 = 重复（2026-09-09 修复）
        // 内核自实现的自动压缩（2026-09-14 取代随宿主冻结的 pi 原生 compaction）：逼近上限时先把老消息压成摘要卡，
        // 这样紧接着构建的 history 就是压缩后的形态。失败只记日志，绝不阻断发送。
        selectedModel?.provider?.let { AiConfigStore.configs[it] }?.let { c ->
            runCatching { maybeAutoCompact(c) }
                .onFailure { Log.w(TAG, "自动压缩失败：${it.message}") }
        }
        val historyBefore = buildApiHistory()
        // 当前这条用户消息（附件随文本进请求用；attachments 列表马上会被清空，先取快照）
        val currentMsg = Msg.User(userText, attachments.toList(), quote)
        appendEntry(currentMsg)
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
        // 本轮用户消息的请求文本：附件以「名称 · 路径」附在正文后（本条是最新回合，媒体恒保留）；
        // 该条文本随后与历史一起拼成整段请求。
        // 附件直发（2026-09-14 照 Operit 的三个媒体开关）：开启的类别把文件本体转成内容部件随请求发出，
        // 已直发的附件不再重复列路径（Operit 的「移除链接」）；关闭的类别**不拦消息**，
        // 只追加一行 Operit 原文占位（「图片内容已省略，当前模型不支持图片处理」）。
        val appCtx = AppCtx.get()
        val inline = if (appCtx != null) {
            MediaInline.parts(appCtx, currentMsg.attachments, cfg)
        } else {
            MediaInline.InlineResult(emptyList(), emptySet(), emptyList())
        }
        val textMsg = if (inline.inlinedIndexes.isEmpty()) {
            currentMsg
        } else {
            currentMsg.copy(
                attachments = currentMsg.attachments.filterIndexed { i, _ -> i !in inline.inlinedIndexes },
            )
        }
        val userTurnText = ContextPolicy.promptTextFor(textMsg, true to true) +
            (if (inline.notes.isNotEmpty()) "\n\n" + inline.notes.joinToString("\n") else "")
        val history = historyBefore + ("user" to (quote?.toPrompt(userTurnText) ?: userTurnText))
        val trimmedHistory = trimToContextBudget(history, cfg.ctxLenK)
        try {
            val outcome = runChat(
                cfg = cfg.copy(modelList = effectiveModel),
                history = trimmedHistory,
                onDelta = { draft -> streamDraft = draft },
                onThinking = { noteThinkingDelta(it) },
                media = inline.parts,
            )
            // 思考先于回答落库：条目顺序 = [思考, 回答]，列表层把思考并入紧随其后的回答卡
            appendThinkingEntry(outcome)
            appendEntry(Msg.Assistant(outcome.text, outcome.usage, effectiveModel))
            // 用量台账（用量页数据源）：完成即记一笔（usage 为空 = 服务商未返回用量，不记）
            UsageStore.record(cfg.providerId, effectiveModel, outcome.usage)
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
            streamThinking = ""
            streamThinkingStartedAt = 0L
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
        streamThinking = ""
        streamThinkingStartedAt = 0L
        // 本轮思考条目一律插到目标回答**之前**：目标回答就在末位，按追加语义写会渲染到回答之后
        runInsertAt = index
        markRunning(true)
        return try {
            val outcome = runChat(
                cfg = cfg.copy(modelList = effectiveModel),
                history = history,
                onDelta = { draft ->
                    val at = runInsertAt ?: index
                    replaceMessageAt(at, Msg.Assistant(draft, null, effectiveModel))
                },
                onThinking = { noteThinkingDelta(it) },
            )
            // 思考条目与回答同位替换（游标 = 目标回答当前位置）：前面已有思考 → 原位替换；没有则插入
            val target = upsertThinkingBefore(runInsertAt ?: index, outcome)
            replaceMessageAt(target, Msg.Assistant(outcome.text, outcome.usage, effectiveModel))
            // 重新生成同样计入用量台账（一次真实请求 = 一笔用量）
            UsageStore.record(cfg.providerId, effectiveModel, outcome.usage)
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            replaceMessageAt(runInsertAt ?: index, original)   // 中止：恢复原内容
            throw e
        } catch (e: Exception) {
            replaceMessageAt(runInsertAt ?: index, original)
            e.message ?: "未知错误"
        } finally {
            runInsertAt = null
            streamDraft = ""
            streamThinking = ""
            streamThinkingStartedAt = 0L
            isStreaming = false
            markRunning(false)
        }
    }

    /**
     * 单次对话请求（流式 / 非流式共用一条路径，2026-09-11 抽出供发送与重新生成复用）：
     * 流式逐片回调 onDelta/onThinking，返回完整文本、usage 与思考文本。
     *
     * 2026-09-14 用户拍板：工具能力整体移除 —— 不再下发 `tools`、不再解析/执行工具调用、
     * 不再有文本标记（DSML）兜底，这里就是一次普通的文本请求。
     */
    private suspend fun runChat(
        cfg: ProviderConfig,
        history: List<Pair<String, String>>,
        onDelta: (String) -> Unit,
        onThinking: (String) -> Unit = {},
        /** 本回合要直发的附件部件（媒体能力开关；只作用于最新一条用户消息） */
        media: List<WirePart> = emptyList(),
    ): ChatOutcome {
        // ── pi 通道（2026-09-14）：这一轮交给 Ubuntu 里的 pi ──
        // 会话上下文由 pi 自己维护（同一进程内连续 prompt），这里只把新消息送进去、
        // 把流式增量与 usage 接回 UI；起不来就落回下面的直连内核。
        if (piChannelEnabled) {
            val modelId = cfg.models.firstOrNull()
            if (!modelId.isNullOrBlank() && PiRpc.usable() && PiRpc.start(cfg.providerId, modelId)) {
                bindPiSession()          // 会话映射：懒建 / 切到本会话对应的 pi 会话文件（失败不阻断本轮）
                return runChatViaPi(cfg, history, onDelta, onThinking)
            }
        }
        // 思考模式的总开关：null = 不给服务商发思考参数、且**服务商自带的推理内容一律不展示不落库**
        // （DeepSeek-R1 / GLM / Kimi 思考系列不靠 reasoning_effort 也会回 reasoning_content，
        //   开关关着却把推理显示出来＝越权；2026-09-12 用户报「关了思考模式、流式期间仍显示思考内容、
        //   回答完又消失」即此处漏门控——流式分支当时漏了判 `thinking != null`，只落了「不落库」）。
        val thinking = if (thinkingEnabled) thinkingLevel else null
        val prompt = Prompts.systemPrompt()
        systemPrompt = prompt   // 面板显示真实下发内容
        val turns = ArrayList<ChatTurn>(history.size)
        history.forEachIndexed { i, (role, content) ->
            if (i == history.lastIndex && role == "user" && media.isNotEmpty()) {
                turns.add(ChatTurn.Rich(role, content, media))
            } else {
                turns.add(ChatTurn.Text(role, content))
            }
        }

        val roundText = StringBuilder()
        val roundThinking = StringBuilder()
        var usage: Usage? = null
        if (!streamingOutputEnabled) {
            val res = AiBackend.chat(cfg, prompt, turns, thinking)
            roundText.append(res.text)
            usage = res.usage
            if (thinking != null) {
                res.thinking?.takeIf { it.isNotBlank() }?.let { onThinking(it) }
            }
        } else {
            AiBackend.chatStream(cfg, prompt, turns, thinking).collect { ev ->
                when (ev) {
                    is ChatEvent.TextDelta -> {
                        roundText.append(ev.text)
                        onDelta(roundText.toString())
                    }
                    is ChatEvent.ThinkingDelta -> if (thinking != null) {
                        roundThinking.append(ev.text)
                        onThinking(roundThinking.toString())
                    }
                    is ChatEvent.UsageEvent -> usage = ev.usage
                    is ChatEvent.Failed -> throw AiException(ev.message)
                    ChatEvent.Done -> Unit
                }
            }
        }
        usage?.let { updateContextPercent(it, cfg) }

        val text = roundText.toString().trim()
        if (text.isNotEmpty()) onDelta(text)
        return ChatOutcome(text, usage?.takeIf { it.inTokens + it.outTokens > 0 }, streamThinking)
    }


    // ─────────────── 会话映射与分支（2026-09-14）───────────────
    //
    // 口径（对照 pi 的会话模型）：
    //   Pient 会话            ↔  pi 的一个 session 文件（`~/.pi/agent/sessions/*.jsonl`）
    //   Pient 会话内分支      ↔  **同一个文件里的树导航**（移动活跃叶；pi 的 /tree，走扩展命令 /pient-nav）
    //   Pient 会话外分支      ↔  **新文件**（pi 的 /fork = 从某条用户消息分叉；/clone = 复制活跃分支）
    // 内容真相源仍是 pi 的文件；Pient 侧的条目树只做展示镜像（逐步退役）。

    /** 当前会话（或指定会话）的记录 */
    fun sessionRecord(id: String = currentSessionId.orEmpty()): Session? =
        sessions.values.firstOrNull { list -> list.any { it.id == id } }?.firstOrNull { it.id == id }

    /** pi 会话树（画布数据源；null = 未绑 pi 或还没拉） */
    var piTree by mutableStateOf<SessionTreeNode?>(null)
        private set

    /** pi 侧的活跃叶（画布上标"当前位置"） */
    var piLeafId by mutableStateOf<String?>(null)
        private set

    /**
     * 绑（或切到）当前 Pient 会话对应的 pi 会话文件。
     * **懒建**：没有就让 pi `new_session`（顺带把 Pient 的标题 `set_session_name` 同步过去），
     * 拿到 sessionFile 记回会话记录并立刻落盘；已有则 `switch_session`。
     */
    suspend fun bindPiSession() {
        val id = currentSessionId ?: return
        val rec = sessionRecord(id) ?: return
        runCatching {
            val file = rec.piSessionFile
            if (file.isNullOrBlank()) {
                PiRpc.newSession()
                PiRpc.setSessionName(rec.title)
                val newFile = PiRpc.getSessionStats()?.optString("sessionFile").orEmpty()
                if (newFile.isNotBlank()) {
                    updateSessionPiFile(id, newFile)
                    Log.i(TAG_CHAT, "会话已映射到 pi 文件：$newFile")
                }
            } else {
                PiRpc.switchSession(file)
            }
            refreshPiTree()
        }.onFailure { Log.w(TAG_CHAT, "绑 pi 会话失败：${it.message}") }
    }

    private fun updateSessionPiFile(id: String, file: String) {
        sessions.values.forEach { list ->
            val i = list.indexOfFirst { it.id == id }
            if (i >= 0) list[i] = list[i].copy(piSessionFile = file)
        }
        AppCtx.get()?.let { ChatStore.save(it, this) }
    }

    /** 拉 pi 的会话树，转成画布用的 [SessionTreeNode]（节点 id **就是 pi 的 entry id**） */
    suspend fun refreshPiTree() {
        val rec = sessionRecord() ?: return
        if (rec.piSessionFile.isNullOrBlank() || !PiRpc.usable()) return
        val data = PiRpc.getTree() ?: return
        piLeafId = data.optString("leafId").takeIf { it.isNotBlank() && it != "null" }
        piTree = piTreeFromJson(data)
    }

    /** pi `get_tree` → [SessionTreeNode]（只把**用户消息**当节点，与其后的助手文本做 exchange —— 与本地画布同口径） */
    private fun piTreeFromJson(data: JSONObject): SessionTreeNode? {
        val roots = data.optJSONArray("tree") ?: return null
        // 拍平成 (id → {entry, children})，并记下"到叶的路径"用于标 active
        val parentOf = HashMap<String, String?>()
        val entryById = LinkedHashMap<String, JSONObject>()
        val childrenOf = HashMap<String, MutableList<String>>()
        val order = ArrayList<String>()
        fun walk(node: JSONObject) {
            val e = node.optJSONObject("entry") ?: return
            val id = e.optString("id")
            if (id.isBlank()) return
            entryById[id] = e
            order += id
            parentOf[id] = e.optString("parentId").takeIf { it.isNotBlank() && it != "null" }
            val kids = node.optJSONArray("children") ?: JSONArray()
            for (i in 0 until kids.length()) kids.optJSONObject(i)?.let { walk(it) }
        }
        for (i in 0 until roots.length()) roots.optJSONObject(i)?.let { walk(it) }
        if (entryById.isEmpty()) return null

        val activePath = HashSet<String>()
        var cur = piLeafId
        while (cur != null) {
            activePath += cur
            cur = parentOf[cur]
        }

        val userIds = order.filter { piRole(entryById[it]) == "user" }
        if (userIds.isEmpty()) return null
        fun textOf(id: String) = piText(entryById[id]?.optJSONObject("message"))
        // 每个用户消息的 exchange = 它之后、下一个用户消息之前的所有助手/工具文本
        val idxOf = HashMap<String, Int>().apply { userIds.forEachIndexed { i, u -> put(u, i) } }
        val exchangeOf = HashMap<String, MutableList<Msg>>()
        run {
            var pending: String? = null
            val buf = ArrayList<Msg>()
            for (id in order) {
                val role = piRole(entryById[id])
                if (role == "user") {
                    pending?.let { exchangeOf[it] = ArrayList(buf) }
                    buf.clear()
                    pending = id
                } else if (pending != null) {
                    val t = textOf(id).trim()
                    if (t.isNotEmpty() && role == "assistant") buf += Msg.Assistant(t, null)
                }
            }
            pending?.let { exchangeOf[it] = ArrayList(buf) }
        }
        // 父节点 = 该用户消息上游最近的那个用户消息
        fun nearestUserAncestor(id: String): String? {
            var p = parentOf[id]
            while (p != null) {
                if (idxOf.containsKey(p)) return p
                p = parentOf[p]
            }
            return null
        }
        fun build(id: String): SessionTreeNode {
            val kids = order.filter { nearestUserAncestor(it) == id }
            return SessionTreeNode(
                id = id,
                userText = textOf(id).trim(),
                exchange = exchangeOf[id].orEmpty(),
                children = kids.map { build(it) },
                active = activePath.contains(id),
            )
        }
        val topNodes = userIds.filter { nearestUserAncestor(it) == null }
        val built = topNodes.map { build(it) }
        // 画布只认单根：多个根（分叉起点不同）时包一个合成根
        return if (built.size == 1) built[0] else SessionTreeNode(
            id = "pi-root",
            userText = "",
            exchange = emptyList(),
            children = built,
            active = built.any { it.active },
        )
    }

    private fun piRole(entry: JSONObject?): String =
        entry?.optJSONObject("message")?.optString("role").orEmpty()

    /** pi 条目里的文本（content 可能是字符串，也可能是 [{type:"text",text:…}]） */
    private fun piText(msg: JSONObject?): String {
        msg ?: return ""
        return when (val c = msg.opt("content")) {
            is String -> c
            is JSONArray -> buildString {
                for (i in 0 until c.length()) {
                    val b = c.optJSONObject(i) ?: continue
                    if (b.optString("type") == "text") append(b.optString("text"))
                }
            }
            else -> ""
        }
    }

    /**
     * **会话外分支**：从 [entryId]（一条用户消息）在 pi 侧 fork 出一个新会话文件，
     * 并在 Pient 里建一个绑定它的新会话（标题带「分支」前缀，便于认）。
     * 返回新会话 id（异步建，失败返回空串）。
     */
    fun forkPiSession(entryId: String): String {
        val proj = currentProject ?: return ""
        val newId = "s-${System.currentTimeMillis()}"
        bgScope.launch {
            runCatching {
                PiRpc.fork(entryId)
                val file = PiRpc.getSessionStats()?.optString("sessionFile").orEmpty()
                if (file.isBlank()) return@runCatching
                val title = "分支 · " + (sessionRecord()?.title ?: "会话")
                sessions.getOrPut(proj) { mutableStateListOf() }
                    .add(0, Session(newId, title, proj, updatedAt = System.currentTimeMillis(), piSessionFile = file))
                messagesBySession[newId] = mutableStateListOf()
                entriesBySession[newId] = mutableStateListOf()
                leafBySession[newId] = null
                currentSessionId = newId
                refreshPiTree()
                AppCtx.get()?.let { ChatStore.save(it, this@ChatState) }
                Log.i(TAG_CHAT, "会话外分支已建：$file")
            }.onFailure { Log.w(TAG_CHAT, "fork 失败：${it.message}") }
        }
        return newId
    }

    /**
     * **走 pi 通道跑一轮**（2026-09-14）：只送最后一条用户消息 —— pi 在同一条 RPC 会话里
     * 自己累积上下文（含工具结果），这也是官方客户端（pi-web / SDK）的口径。
     *
     * 流式：`message_update.assistantMessageEvent` 的 `text_delta` / `thinking_delta`；
     * usage：事件顶层的累积值（provider 不上报时为 0，回合结束以 `message_end` 为准不动）；
     * 回合结束判据：**`agent_settled`**（pi 口径：重试、压缩重试、排队续写都settled了才算完）。
     *
     * 工具调用（`tool_execution_start/update/end`）本切片只记日志，工具行 UI 是下一步。
     */
    private suspend fun runChatViaPi(
        cfg: ProviderConfig,
        history: List<Pair<String, String>>,
        onDelta: (String) -> Unit,
        onThinking: (String) -> Unit,
    ): ChatOutcome = coroutineScope {
        val userText = history.lastOrNull { it.first == "user" }?.second.orEmpty()
        val text = StringBuilder()
        val think = StringBuilder()
        var usage: Usage? = null
        val settled = CompletableDeferred<Unit>()
        lastPiError = null
        val collector = launch {
            PiRpc.events.collect { ev ->
                when (ev.optString("type")) {
                    "message_update" -> {
                        ev.optJSONObject("usage")?.let { u -> piUsage(u)?.let { usage = it } }
                        val d = ev.optJSONObject("assistantMessageEvent") ?: return@collect
                        when (d.optString("type")) {
                            "text_delta" -> {
                                text.append(d.optString("delta"))
                                onDelta(text.toString())
                            }
                            "thinking_delta" -> if (thinkingEnabled) {
                                think.append(d.optString("delta"))
                                onThinking(think.toString())
                            }
                        }
                    }
                    "tool_execution_start" -> Log.i("PientChat", "pi 工具开始：${ev.optString("toolName")}")
                    "tool_execution_end" -> Log.i(
                        "PientChat",
                        "pi 工具结束：${ev.optString("toolName")} 失败=${ev.optBoolean("isError")}",
                    )
                    "agent_settled", "channel_closed" -> settled.complete(Unit)
                    "error" -> {
                        val msg = ev.optJSONObject("error")?.optString("message").orEmpty()
                            .ifBlank { ev.optString("message") }
                        if (msg.isNotBlank()) lastPiError = msg
                    }
                }
            }
        }
        val res = PiRpc.prompt(userText)
        if (res != null && !res.optBoolean("success", true)) {
            collector.cancel()
            throw AiException(res.optString("error").ifBlank { "pi 拒绝了这次请求" })
        }
        val ok = withTimeoutOrNull(600_000) { settled.await() } != null
        collector.cancel()
        if (!ok) {
            bgScope.launch { PiRpc.abort() }
            throw AiException("pi 通道超时（10 分钟未见 agent_settled）")
        }
        lastPiError?.let { err ->
            lastPiError = null
            if (text.isEmpty()) throw AiException(err)
        }
        usage?.let { updateContextPercent(it, cfg) }
        val out = text.toString().trim()
        if (out.isNotEmpty()) onDelta(out)
        ChatOutcome(out, usage?.takeIf { it.inTokens + it.outTokens > 0 }, think.toString())
    }

    /** pi 事件的 usage → Pient 的 [Usage]（pi 口径：input 不含 cacheRead/cacheWrite） */
    private fun piUsage(u: JSONObject): Usage? {
        val inTok = u.optInt("input", 0)
        val outTok = u.optInt("output", 0)
        val cache = u.optInt("cacheRead", 0)
        val cacheWrite = u.optInt("cacheWrite", 0)
        if (inTok + outTok + cache + cacheWrite <= 0) return null
        val cost = u.optJSONObject("cost")?.optDouble("total", 0.0) ?: 0.0
        return Usage(inTok, outTok, cache, cost, cacheWrite)
    }

    /** 单次请求结果：正文 + usage + 思考文本（思考模式关闭时为空串） */
    private data class ChatOutcome(val text: String, val usage: Usage?, val thinking: String)

    /**
     * 上下文占用百分比：本轮用量（输入 + 缓存读/写 + 输出）÷ 配置的上下文长度。
     * 数据源 = 服务商返回的 usage 真值；配置里长度缺失/为 0 时不改。
     */
    private fun updateContextPercent(usage: Usage, cfg: ProviderConfig) {
        val limitK = cfg.ctxLenK.trim().toIntOrNull() ?: return
        if (limitK <= 0) return
        val used = usage.inTokens + usage.cacheTokens + usage.cacheWriteTokens + usage.outTokens
        if (used <= 0) return
        contextUsedTokens = used
        contextPercent = (used * 100f / (limitK * 1000f)).coerceIn(0f, 100f)
    }

    // ─────────── 上下文压缩（**内核自实现，pi 口径**：阈值触发 / keepRecentTokens 切点） ───────────

    /**
     * 手动压缩上下文（参照 pi 桌面端的 `/compact`，移动端等价入口）。
     *
     * 为什么 Pient 需要它：**Android 上没有命令行入口**，而 pi 的手动压缩是 `/compact` 命令 ——
     * 移动端的等价入口 = 上下文用量卡里的「压缩上下文」动作（用户看得见"什么时候能压、压了什么"）。
     *
     * 结果只有一条回执（内核自实现）：摘要 + 前后 token 估值直接落一张压缩卡。
     * 返回 null = 成功；非空 = 如实回报的原因（没有可用模型 / 没什么可压 / 摘要为空 / 上一次还在跑）。
     */
    suspend fun compactNow(): String? {
        val cfg = selectedModel?.provider?.let { AiConfigStore.configs[it] }
            ?: return "没有可用的服务商 / 模型"
        return runCompaction(cfg, "manual")
    }

    /** 自动压缩：超阈值才动手（[Compaction.shouldCompact]）；摘要失败静默，不阻断本轮发送 */
    private suspend fun maybeAutoCompact(cfg: ProviderConfig) {
        if (compacting) return
        val history = buildApiHistory()
        val limit = Compaction.compactLimitTokens(cfg.ctxLenK, cfg.reserveTokensValue)
        if (!Compaction.shouldCompact(history, cfg.ctxLenK, cfg.reserveTokensValue, cfg.compactionEnabled)) return
        Log.i(TAG, "自动压缩触发：估 ${Compaction.estimateTokens(history)} tokens > 阈值 $limit")
        runCompaction(cfg, "threshold")?.let { Log.w(TAG, "自动压缩未执行：$it") }
    }

    /**
     * 真做一次压缩：切片 → 调模型生成 checkpoint 摘要 → 落一张压缩卡。
     * 摘要在卡里、也在请求里（[apiHistoryOf] 把 `Msg.Compaction` 当一条 user 文本发出），
     * 之后的请求由 [ContextPolicy.sliceFromLastCompaction] 从该卡起算。
     */
    private suspend fun runCompaction(cfg: ProviderConfig, reason: String): String? {
        if (compacting) return "上一次压缩还在进行中"
        val history = buildApiHistory()
        val prefix = Compaction.prefixToSummarize(history, cfg.keepRecentTokensValue) ?: run {
            Log.i(TAG, "压缩跳过：可压缩的前缀太短（历史太短或 keepRecentTokens 太大）")
            return "没什么可压缩的：当前历史太短（或「保留最近 tokens」设得过大）"
        }
        compacting = true
        return try {
            val before = Compaction.estimateTokens(history)
            val summary = Compaction.summarize(cfg, prefix, cfg.compactInstructions.ifBlank { null })
            if (summary.isNullOrBlank()) {
                Log.w(TAG, "压缩失败：摘要为空（保持原文，不落假卡）")
                return "压缩失败：模型没有返回摘要（原文保持不变）"
            }
            // 压缩后的上下文 = 摘要 + **保留的尾部**（tail 是要继续带着走的，不能漏算 —— 漏了会把
            // 「节省」夸大成 before − 摘要，实测在 keepRecentTokens≈历史长度时最离谱）
            val tailTokens = Compaction.estimateTokens(history.subList(prefix.size, history.size))
            val after = Compaction.estimateTokens(listOf("user" to summary)) + tailTokens
            appendCompactionEntry(summary, before, after, reason)
            Log.i(TAG, "压缩完成（$reason）：估 $before → $after tokens（摘要 ${summary.length} 字 + 保留尾部 $tailTokens tokens）")
            null
        } finally {
            compacting = false
        }
    }

    /**
     * 压缩条目落库。按「摘要文本 + tokensBefore」去重 —— 同一结果重复落库时不叠卡。
     */
    private fun appendCompactionEntry(
        summary: String,
        tokensBefore: Int,
        estimatedAfter: Int,
        reason: String? = null,
    ) {
        if (summary.isBlank()) return
        val last = currentMessages.lastOrNull()
        if (last is Msg.Compaction && last.tokensBefore == tokensBefore && last.summary == summary) return
        appendEntry(
            Msg.Compaction(
                tokensBefore = tokensBefore,
                saved = (tokensBefore - estimatedAfter).coerceAtLeast(0),
                summary = summary,
                reason = reason,
            )
        )
        Log.i(TAG, "上下文已压缩（${reason ?: "来源未知"}）：前 $tokensBefore → 估 $estimatedAfter（摘要 ${summary.length} 字）")
    }

    /** 首个思考增量到达时记起点（思考行右侧计时与落库 durationMs 都用它） */
    private fun noteThinkingDelta(text: String) {
        if (streamThinkingStartedAt == 0L) streamThinkingStartedAt = System.currentTimeMillis()
        streamThinking = text
    }

    /** 本轮思考落库为 [Msg.Thinking]（无思考内容时不落条目）；返回落下的条目 */
    private fun appendThinkingEntry(outcome: ChatOutcome): Msg.Thinking? {
        val msg = thinkingMsgOf(outcome) ?: return null
        // 记下它的上屏下标：回答落地后该块保持展开（Hermes live preview 的 latch）
        liveThinkingIndex = appendEntry(msg)
        return msg
    }

    /**
     * 思考条目构造（思考模式关闭 / 服务商未回思考内容 → null，不落条目也不渲染折叠行）。
     * level 取 pi 思考级别字面量（minimal…xhigh，与 pi 会话条目同口径）。
     */
    private fun thinkingMsgOf(outcome: ChatOutcome): Msg.Thinking? {
        val text = outcome.thinking.trim()
        if (text.isEmpty()) return null
        val level = if (thinkingEnabled) thinkingLevel.piValue else "off"
        val started = streamThinkingStartedAt
        val duration = if (started > 0L) System.currentTimeMillis() - started else null
        return Msg.Thinking(level, text, duration)
    }

    /**
     * 重新生成时同步思考条目：该条助手消息前面已有思考条目 → 原位替换；否则**插入**一条
     * （消息流插到下一位，条目树里插成 前一条目 → 新思考条目 → 该助手条目 的一段链）。
     *
     * @return 助手消息在思考条目落位后的最新下标（插入时 = index + 1）
     */
    private fun upsertThinkingBefore(index: Int, outcome: ChatOutcome): Int {
        val msg = thinkingMsgOf(outcome) ?: return index
        val sid = currentSessionId ?: return index
        val list = messagesBySession[sid] ?: return index
        if (list.getOrNull(index - 1) is Msg.Thinking) {
            replaceMessageAt(index - 1, msg)
            return index
        }
        insertEntryAt(index, msg)
        return index + 1
    }

    /** 当前会话 root→leaf 的条目路径（条目树遍历的唯一实现，消息流下标 = 路径下标） */
    private fun leafPath(sid: String): List<SessionEntry> {
        val entries = entriesBySession[sid] ?: return emptyList()
        val byId = entries.associateBy { it.id }
        val path = mutableListOf<SessionEntry>()
        var cur = leafBySession[sid]?.let { byId[it] }
        while (cur != null) {
            path += cur
            cur = cur.parentId?.let { byId[it] }
        }
        path.reverse()
        return path
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
        val target = leafPath(sid).getOrNull(index) ?: return
        val i = entries.indexOfFirst { it.id == target.id }
        if (i >= 0) entries[i] = entries[i].copy(msg = msg)
    }

    /**
     * API 上下文重建（2026-09-13：**参考 Operit 的上下文管线**重做）。
     *
     * ① **切片**：从最后一条压缩摘要（含）起 —— Operit `getMemoryFromMessages` 同款；
     * ② **附件进请求**：附件以「名称 · 路径」文本随该条用户消息发出，
     *    历史回合里的图片/音视频按「保留最近 N 个用户回合」裁剪、更早的写占位文案
     *    —— Operit `limitImageLinksInChatHistory` / `limitMediaLinksInChatHistory` 同款；
     * ③ 压缩摘要本身以一条 user 消息进请求（Operit 把 summary 作为 USER 角色发送）；
     * ④ 思考条目不进上下文；**旧会话里已有的工具结果**以文本形态进历史（见 ToolResult 分支），
     *    工具卡本身（参数 / diff）不进。
     */
    private fun buildApiHistory(): List<Pair<String, String>> = apiHistoryOf(currentMessages)

    private fun apiHistoryOf(msgs: List<Msg>): List<Pair<String, String>> {
        val cfg = selectedModel?.provider?.let { AiConfigStore.configs[it] }
        val slice = ContextPolicy.sliceFromLastCompaction(msgs)
        val windows = ContextPolicy.attachmentWindows(
            slice,
            cfg?.maxImageHistoryTurnsValue ?: ContextPolicy.DEFAULT_MAX_IMAGE_HISTORY_TURNS,
            cfg?.maxMediaHistoryTurnsValue ?: ContextPolicy.DEFAULT_MAX_MEDIA_HISTORY_TURNS,
        )
        val out = mutableListOf<Pair<String, String>>()
        // 工具结果入历史的累积缓冲（见下方 when 分支的注释）
        val pendingTools = mutableListOf<Pair<String, String>>()
        fun flushTools() {
            if (pendingTools.isEmpty()) return
            out += "user" to toolResultsBlock(pendingTools)
            pendingTools.clear()
        }
        slice.forEachIndexed { i, m ->
            when (m) {
                is Msg.User -> {
                    flushTools()
                    val text = ContextPolicy.promptTextFor(m, windows[i])
                    out += "user" to (m.quote?.toPrompt(text) ?: text)
                }
                is Msg.Assistant -> {
                    flushTools()
                    if (!m.error) out += "assistant" to m.markdown
                }
                is Msg.Compaction -> {
                    flushTools()
                    out += "user" to m.summary
                }
                // **旧会话里已有的工具结果以文本形态进历史**：这些条目是工具能力移除前的记录，
                // 继续拼进上下文能让老会话保持连贯（模型把它当环境回执读）；新会话不会再产生它们。
                is Msg.ToolResult -> pendingTools += m.toolName to toolHistoryBody(m)
                // 调用意图已由结果的 [name] 前缀表达；参数/diff 体积大又不影响后续推理，不入历史
                is Msg.ToolCall -> Unit
                else -> Unit // 思考块不参与对话上下文
            }
        }
        flushTools()
        return out
    }

    /**
     * 旧工具结果的上下文形态：`工具执行结果：\n\n[read] …`（仅历史重建路径使用）。
     */
    private fun toolResultsBlock(entries: List<Pair<String, String>>): String =
        "工具执行结果：\n\n" + entries.joinToString("\n\n") { (name, body) -> "[$name] $body" }

    /**
     * 单个工具结果入历史时的截断：**每个结果最多 2000 字符**（`preview` 是界面用的短摘要，
     * 优先用 `full` 的截断）。整段历史另有 [trimToContextBudget] 的字符预算兜底。
     */
    private fun toolHistoryBody(m: Msg.ToolResult): String =
        (m.full ?: m.preview).take(TOOL_HISTORY_CHARS)

    /**
     * 上下文长度预算裁剪（配置页「上下文长度」K Tokens 生效）：
     * 粗略估算 token ≈ 字符数/2（中英混排折中），超预算丢最旧条目；
     * 但永远保留最新一条用户消息（本轮提问不可丢）。
     *
     * 定位 = **兜底**：常规路径是「先压缩、再发送」（[maybeAutoCompact] → [runCompaction]），
     * 只有摘要失败或 keepRecentTokens 配得过大时才轮到这条硬裁剪。
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
        if (piChannelEnabled) bgScope.launch { PiRpc.abort() }   // pi 侧也要停（否则它继续跑）
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
    val branchTree: SessionTreeNode?
        get() = piTree ?: buildBranchTree(currentSessionId)   // 绑了 pi：画布直接用 pi 的树

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
        // ── 绑了 pi 会话 → **会话内分支交给 pi**（同一个文件里移动活跃叶，TUI /tree 同款）──
        // 走扩展命令 /pient-nav：pi 侧会切换上下文（必要时还能生成被放弃分支的摘要），
        // 之后我们只拉一次 pi 的树刷新画布；本地 leaf 同步一份让 UI 立刻响应。
        val rec = sessionRecord(sid)
        if (piChannelEnabled && piTree != null && !rec?.piSessionFile.isNullOrBlank() && PiRpc.usable()) {
            // 画布的节点 id 就来自 pi 的树（= pi entry id），这里原样交给 pi
            leafBySession[sid] = nodeId
            bgScope.launch {
                runCatching { PiRpc.navigate(nodeId) }
                    .onFailure { Log.w(TAG_CHAT, "pi 会话内分支跳转失败：${it.message}") }
                refreshPiTree()
            }
            return true
        }
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
    var contextPercent by mutableStateOf(0f)

    /**
     * 最近一次请求的实际上下文占用（token；输入+缓存读写+输出 —— Operit 的
     * `getLastCurrentWindowSize` 等价物）：触发式总结的用量阈值判定用它。
     */
    var contextUsedTokens by mutableStateOf(0)

    /** 手动压缩进行中（用量卡的「压缩上下文」动作据此显示进度，避免连点） */
    var compacting by mutableStateOf(false)
        private set

    /**
     * 本轮运行条目的插入游标（null = 追加到 leaf，发送路径语义）。
     * 重新生成时 = 目标回答的上屏下标：本轮思考条目插到它之前，每插一条自增，
     * 始终指向目标回答的当前位置（见 [appendEntry] / [insertEntryAt]）。
     */
    private var runInsertAt: Int? = null
    var windowTokens by mutableStateOf(61200)
    var maxWindowTokens by mutableStateOf(180000)
    var connectionLabel by mutableStateOf("已连接")
    // 系统提示词只读展示（2026-09-01，对齐 pi-web system 面板）：
    // **真实值 = 内核每次发请求时构造的那一份**（`Prompts.systemPrompt`），由发送路径写入 ——
    // 面板显示的必须是模型真正收到的内容。
    var systemPrompt by mutableStateOf("")
    // 上下文用量分类明细（Hermes 上下文卡片口径；UI 原型 mock，合计 = windowTokens）
    // 分类经 pi 源码核实：「记忆」「子代理」不参与；2026-09-14 工具 / 技能整体移除后
    // 「工具定义」「技能」两行一并去掉。
    var contextCategories by mutableStateOf(
        listOf(
            ContextCategory("conversation", "对话", 40000),
            ContextCategory("system_prompt", "系统提示词", 9200),
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
    // （用户真机实测发现，2026-09-12）。
    //
    // 状态 = **每会话的上屏窗口起点**（翻到哪记哪，仅内存不落盘）。不能记「已展开屏数」
    // 再从最新一条重算起点：单条超长消息会顶住累加（见 showEarlierMessages），
    // 「再加一屏」算出来的起点原地不动 → 新增 0 条 → 点击既不加载也不滚动
    // （2026-09-12 用户报「点好几次才加载出消息」）。
    private val messageWindowStartBySession = mutableStateMapOf<String, Int>()

    /**
     * 上屏窗口起点（0 = 全部消息都在窗口内）。
     *
     * = 用户翻到的位置（翻过页就尊重它：之后到达的新消息不会把窗口推回去），
     * 但**不超过**「最近 [WINDOW_SCREENS] 屏」的自动窗口（会话变短 / 切分支后自动让位：
     * [rebuildMessagesFromLeaf] 会清掉该会话的记录）。
     */
    fun messageWindowStart(sessionId: String?, messages: List<Msg>, screenDp: Float): Int {
        if (sessionId == null || messages.isEmpty() || screenDp <= 0f) return 0
        val auto = autoWindowStart(messages, screenDp)
        val paged = messageWindowStartBySession[sessionId] ?: return auto
        return paged.coerceIn(0, auto)
    }

    /** 「最近 [WINDOW_SCREENS] 屏」自动窗口：从最新一条往前累计估算高度，条数下限兜底 */
    private fun autoWindowStart(messages: List<Msg>, screenDp: Float): Int {
        val targetDp = WINDOW_SCREENS * screenDp
        var acc = 0f
        var count = 0
        var i = messages.lastIndex
        while (i >= 0) {
            acc += estimatedMessageHeightDp(messages[i])
            count++
            i--
            if (acc >= targetDp && count >= WINDOW_MIN_MESSAGES) break
        }
        return withTailRule(messages, (i + 1).coerceAtLeast(0), screenDp)
    }

    /** 剩余更早内容估算不足 [WINDOW_TAIL_SCREENS] 屏时直接全显（按钮不出现，避免「点一下没变化」） */
    private fun withTailRule(messages: List<Msg>, start: Int, screenDp: Float): Int {
        if (start <= 0) return 0
        var older = 0f
        for (k in 0 until start) older += estimatedMessageHeightDp(messages[k])
        return if (older < screenDp * WINDOW_TAIL_SCREENS) 0 else start
    }

    /**
     * 「显示更早的消息」：把窗口起点再往前推**一屏**；返回**本次新增条数**。
     *
     * 关键 = 从**当前起点**本地往前量，而不是按「已展开屏数 × 一屏」从最新一条重算：
     * 单条消息可能远比一屏高（实测 6.2 万字符 ≈ 68 屏），从最新一条重算时累加会被它顶住，
     * 起点算出来还是原值 → 新增 0 条 → 点击既不加载也不滚动（2026-09-12 实测：连点 6 次
     * 界面逐字零变化，按公式要连点 ≈75 次才越过那条消息）。本地量保证**每次点击至少往前 1 条**：
     * 一屏装不下的那条整条放进来（消息不能切半条），其余情况仍是一屏。
     */
    fun showEarlierMessages(sessionId: String?, messages: List<Msg>, screenDp: Float): Int {
        if (sessionId == null || messages.isEmpty() || screenDp <= 0f) return 0
        val before = messageWindowStart(sessionId, messages, screenDp)
        if (before <= 0) return 0
        var acc = 0f
        var i = before - 1
        while (i >= 0) {
            acc += estimatedMessageHeightDp(messages[i])
            i--
            if (acc >= screenDp * WINDOW_PAGE_SCREENS) break
        }
        val after = withTailRule(messages, (i + 1).coerceAtLeast(0), screenDp)
        messageWindowStartBySession[sessionId] = after
        return before - after
    }

    /** 定位跳转前把目标消息纳入窗口（窗口起点直接落到目标处，一次到位） */
    fun ensureMessageVisible(sessionId: String?, messages: List<Msg>, screenDp: Float, index: Int) {
        if (sessionId == null || messages.isEmpty() || index < 0 || screenDp <= 0f) return
        if (index >= messageWindowStart(sessionId, messages, screenDp)) return
        messageWindowStartBySession[sessionId] = index
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
