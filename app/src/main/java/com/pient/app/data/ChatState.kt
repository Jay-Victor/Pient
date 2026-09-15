package com.pient.app.data

import com.pient.app.AppCtx
import com.pient.app.runtime.PiKeepAlive

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
import com.pient.app.runtime.PiRuntime
import java.io.File
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

/**
 * 系统提示词回流文件（pi 侧写在 `~/.pi/agent/` 下，见 [ChatState.refreshSystemPrompt]）。
 * 面板显示的就是 pi 真实下发的那一份 —— App 侧不再自己持有提示词。
 */
private const val SYS_PROMPT_FILE = ".pient-sysprompt.txt"

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
        val id = newSessionId()
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

    /**
     * 会话 id：`s-<epochMs>`（《分支功能设计》§4.2 口径），**同毫秒冲突时加 `-2/-3…` 后缀**。
     * 为什么必须防撞：会话列表是 LazyColumn，key 重复会直接抛
     * `Key "s-…" was already used` 闪退（实测踩过：同一毫秒内建了两个会话）。
     */
    private fun newSessionId(): String {
        val used = sessions.values.flatten().map { it.id }.toHashSet()
        val base = "s-${System.currentTimeMillis()}"
        if (base !in used) return base
        var n = 2
        while ("$base-$n" in used) n++
        return "$base-$n"
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
        // pi 侧那份会话记录也要清（2026-09-16）：先取出来，再删本地记录
        val piFile = sessions[proj]?.firstOrNull { it.id == id }?.piSessionFile
        sessions[proj]?.removeAll { it.id == id }
        messagesBySession.remove(id)
        entriesBySession.remove(id)
        leafBySession.remove(id)
        if (currentSessionId == id) currentSessionId = sessionsFor(proj).firstOrNull()?.id
        piDesiredLeaf.remove(id)
        if (piTreeSessionId == id) {
            piTree = null
            piTreeSessionId = null
        }
        // 删除最后一个会话后自动新建（2026-09-09 用户定：侧边栏会话列表恒有会话）
        if (sessionsFor(proj).isEmpty()) newSession()
        discardPiSession(piFile)
    }

    /** 跨项目按 id 删除会话（项目管理页使用；含消息记录与当前会话指针处理） */
    fun deleteSessionById(id: String) {
        val piFile = sessions.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == id } }?.piSessionFile
        for (list in sessions.values) {
            if (list.removeAll { it.id == id }) break
        }
        messagesBySession.remove(id)
        entriesBySession.remove(id)
        leafBySession.remove(id)
        if (currentSessionId == id) {
        piDesiredLeaf.remove(id)
        if (piTreeSessionId == id) {
            piTree = null
            piTreeSessionId = null
        }
            currentSessionId = sessionsFor(currentProject ?: "").firstOrNull()?.id
        }
        discardPiSession(piFile)
    }

    /**
     * 清理 pi 侧那份会话记录（2026-09-16）。
     *
     * 两件事：① 如果删的正是 pi **当前打开**的那个文件，先让 pi 换到新会话 —— 否则它还会
     * 往这个已删文件追加，下一次「按 sessionFile 映射」又把旧上下文拉回来；② 删文件本体。
     * 走 IO 线程（文件删除 + 两次 RPC 往返），失败只记日志、不影响本地删除结果。
     */
    private fun discardPiSession(file: String?) {
        val f = file?.takeIf { it.isNotBlank() } ?: return
        val ctx = AppCtx.get() ?: return
        bgScope.launch(Dispatchers.IO) {
            runCatching {
                if (PiRpc.usable()) {
                    val current = PiRpc.getSessionStats()?.optString("sessionFile").orEmpty()
                    if (current == f) {
                        PiRpc.newSession()
                        Log.i(TAG_CHAT, "删除的正是 pi 当前会话 → 已让 pi 换到新会话")
                    }
                }
                val gone = PiAgentFiles.deleteSessionFile(ctx, f)
                Log.i(TAG_CHAT, "清理 pi 侧会话记录：$f → ${if (gone) "已删除" else "文件不存在"}")
            }.onFailure { Log.w(TAG_CHAT, "清理 pi 侧会话记录失败：${it.message}") }
        }
    }

    /** 会话的消息（内容检索 / 导出用；未上屏过的会话返回空表） */
    fun messagesIn(id: String): List<Msg> = messagesBySession[id].orEmpty()

    /**
     * 导出用的消息序列（2026-09-16）：**优先上屏流，空则回退条目树**。
     *
     * 为什么必须回退：有条目树的会话**不写扁平消息流**（`ChatStore` 那条「不再重复存」的优化），
     * 所以「没在本次运行里打开过的会话」`messagesBySession` 是空的 —— 直接用它导出会得到
     * 一个只有标题的空文档（真机实测：36 条条目的会话导出后只有 715 字节）。
     */
    fun messagesForExport(id: String): List<Msg> =
        messagesIn(id).ifEmpty { entriesBySession[id].orEmpty().map { it.msg } }

    /**
     * 终端镜像：工具开始那一行（2026-09-16）。
     *
     * bash 写成 `$ <原始命令>`（与用户在终端里敲的一模一样）；文件类工具写路径；其它退回落 JSON 摘要。
     * 目的是「在终端页里看得见 AI 在 Ubuntu 里干了什么」（对照 Operit 的工具走 TerminalManager）。
     */
    private fun mirrorStartLine(name: String, args: JSONObject?): String {
        val detail = when (name) {
            "bash" -> args?.optString("command").orEmpty()
            "read", "write", "edit", "ls" ->
                args?.optString("path").orEmpty().ifBlank { args?.optString("file").orEmpty() }
            "grep", "find" ->
                listOf(args?.optString("pattern").orEmpty(), args?.optString("path").orEmpty())
                    .filter { it.isNotBlank() }.joinToString(" ")
            else -> args?.toString().orEmpty()
        }.trim().replace('\n', ' ')
        val body = detail.take(400)
        return if (name == "bash") "$ $body" else "[$name] $body"
    }

    /** 终端镜像：工具结束那一行（结果首行截断，方便一眼看出跑没跑通） */
    private fun mirrorEndLine(name: String, failed: Boolean, result: String): String {
        val head = result.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(120).orEmpty()
        return "  ↳ " + (if (failed) "失败" else "完成") + (if (head.isNotEmpty()) " · $head" else "")
    }

    /** 一条消息的可检索文本（内容检索用；工具类条目把名称与参数也算进去） */
    fun msgText(m: Msg): String = when (m) {
        is Msg.User -> m.text + (m.quote?.let { "\n" + it.text } ?: "")
        is Msg.Assistant -> m.markdown
        is Msg.Thinking -> m.text
        is Msg.ToolCall -> m.name + " " + m.params + (m.detail?.let { " " + it } ?: "")
        is Msg.ToolResult -> m.toolName + " " + m.preview + (m.full?.let { " " + it } ?: "")
        is Msg.Compaction -> m.summary
    }

    /**
     * 会话**内容检索**（2026-09-16，此前只搜标题）：标题之外再搜消息正文与条目文本，
     * 返回 会话 id → 命中片段（抽屉行做副标题用）。
     *
     * 只在用户输入搜索词时调用（IO 线程由调用方保证），命中即停（一条会话只报第一处）。
     */
    fun searchSessionContents(query: String): Map<String, String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        sessions.values.forEach { list ->
            for (s in list) {
                if (out.containsKey(s.id)) continue
                val pool = messagesIn(s.id) + entriesBySession[s.id].orEmpty().map { it.msg }
                for (m in pool) {
                    val t = msgText(m)
                    val at = t.indexOf(q, ignoreCase = true)
                    if (at >= 0) {
                        out[s.id] = snippetAround(t, at, q.length)
                        break
                    }
                }
            }
        }
        return out
    }

    /** 命中片段：前后各留一点上下文、压平换行（正文里的 \n → 空格，长度不变所以下标仍然有效） */
    private fun snippetAround(text: String, at: Int, len: Int): String {
        val flat = text.replace('\n', ' ')
        val start = (at - 14).coerceAtLeast(0)
        val end = (at + len + 24).coerceAtMost(flat.length)
        return (if (start > 0) "…" else "") + flat.substring(start, end) + (if (end < flat.length) "…" else "")
    }

    /**
     * 供 UI 调用的**非挂起**入口：刷新挂在 ChatState 自己的 scope 上 ——
     * 面板关掉/重组导致组合域取消时，这次刷新不会半途夭折
     * （2026-09-16 实测日志：`命令发送失败：The coroutine scope left the composition`）。
     */
    fun requestContextUsage() {
        bgScope.launch { runCatching { refreshContextUsage() } }
    }

    /**
     * 上下文用量（**pi 真值**；2026-09-16 取代两个原型常量）。
     *
     * 来源 = pi 官方 `get_session_stats` 的 `contextUsage { tokens, contextWindow, percent }`
     * （pi 侧 `agent-session.ts:getContextUsage`：按**最后一次压缩之后**的助手 usage 反推，
     * 所以压缩后还没再对话时它会给 null —— 此时卡上照 pi-web 口径显示 `?`，不编数字）。
     * pi 不提供分类明细（pi-web 也只显示聚合百分比），故分类明细收成一行「对话」。
     */
    suspend fun refreshContextUsage() {
        // **先真问一次**，拿不到才起通道 —— 不能用 `PiRpc.usable()` 当「进程活着」的判据：
        // 它的实现是 `process?.isAlive || (rootfsReady && piReady)`，只要运行时部署齐全就返回 true
        // （2026-09-16 实测踩到：新装包、还没发过消息时 usable()=true 但进程没起 → 卡片只有 `? / —`，
        // 而直连 pi 问 get_session_stats 明明回了 tokens/contextWindow/percent）。
        var data = PiRpc.getSessionStats()
        if (data == null) {
            piChannelTarget()?.let { t -> PiRpc.start(t.first, t.second) }
            data = PiRpc.getSessionStats()
        }
        // 绑到「当前会话」那个文件（通道刚起时 pi 可能还停在上次的文件上；不绑会读到别的会话的用量）
        val file = sessionRecord(currentSessionId ?: "")?.piSessionFile
        if (!file.isNullOrBlank() && data?.optString("sessionFile").orEmpty() != file) {
            PiRpc.switchSession(file)
            data = PiRpc.getSessionStats()
        }
        val cu = data?.optJSONObject("contextUsage")
        contextUsageKnown = cu != null
        if (cu == null) {
            windowTokens = 0
            contextPercent = 0f
            return
        }
        val tokens = cu.optInt("tokens", 0)
        val win = cu.optInt("contextWindow", 0)
        if (win > 0) maxWindowTokens = win
        windowTokens = tokens
        contextPercent = cu.optDouble("percent", 0.0).toFloat().coerceIn(0f, 100f)
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
     * pi 运行时就绪态（2026-09-15 用户拍板 B：**pi 是唯一产品路径**）。
     * 未就绪 = 聊天页明确阻断 + 给「环境配置」修复入口，**不再静默改走直连内核**。
     */
    enum class PiReadiness { Unknown, Ready, Unready }

    var piReadiness by mutableStateOf(PiReadiness.Unknown)
        private set

    /** 未就绪原因（一句中文，直接给用户看） */
    var piUnreadyReason by mutableStateOf("")
        private set

    /** 一次性的界面提示（被阻断的发送等；ChatScreen 消费后清空） */
    var blockedNote by mutableStateOf<String?>(null)

    /**
     * 直连内核只作**开发诊断通道**（debug 包）：release 包里 pi 起不来就是起不来，
     * 不允许悄悄换一条没有工具能力的路（用户 2026-09-15 拍板 B）。
     */
    /**
     * pi 通道的**目标**（providerId, modelId）：统一口径 = 输入栏**选中的模型**（拿不到才退到该服务商列表首个）。
     *
     * 2026-09-15 修：`runChat`（用「本次有效模型」）与 `bindPiSession`（用「服务商列表首个」）各算各的 ——
     * 服务商配了多个模型时两边不一致，于是**每一轮都会把健康的通道重启一次**（实测 churn：
     * `通道重启：状态不是 Running（当前 Running(mock, mock-model)）；新 key=mock/mock-model`），
     * 中途重启还可能把正在跑的那一轮打断（消息只落本地镜像）。现在三处（发送 / 绑定 / 就绪探测）用同一个来源。
     */
    private fun piChannelTarget(): Pair<String, String>? {
        val m = selectedModel ?: return null
        val cfg = AiConfigStore.configs[m.provider] ?: return null
        val model = m.name.takeIf { it.isNotBlank() } ?: cfg.models.firstOrNull() ?: return null
        return cfg.providerId to model
    }

    private fun directFallbackAllowed(): Boolean {
        val ctx = AppCtx.get() ?: return false
        return (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /** 重测 pi 就绪态（必要时尝试起一次通道）；结果写 [piReadiness] / [piUnreadyReason] */
    fun refreshPiReadiness() {
        bgScope.launch {
            val ctx = AppCtx.get() ?: return@launch
            val result = withContext(Dispatchers.IO) { probePiReadiness(ctx) }
            piReadiness = result.first
            piUnreadyReason = result.second
            Log.i(TAG_CHAT, "pi 就绪态：${result.first}${if (result.second.isBlank()) "" else "（${result.second}）"}")
        }
    }

    private suspend fun probePiReadiness(ctx: Context): Pair<PiReadiness, String> {
        val target = piChannelTarget()
        if (target == null) {
            return PiReadiness.Unready to "没有可用的服务商 / 模型：先到「服务商与模型配置」里配好"
        }
        if (!PiRuntime.rootfsReady(ctx)) {
            // 原因文案与「环境配置」页共用一处（rootfsIssue）：避免两处说法不一致（2026-09-15）
            return PiReadiness.Unready to PiRuntime.rootfsIssue(ctx)
        }
        if (!PiRuntime.piReady(ctx)) {
            return PiReadiness.Unready to "pi 未就绪：随包运行时还没解出来（可在「环境配置」里重新检测）"
        }
        if (!PiRpc.start(target.first, target.second)) {
            val tail = PiRpc.stderrText().lines().lastOrNull { it.isNotBlank() }.orEmpty()
            return PiReadiness.Unready to ("pi 通道启动失败" + if (tail.isBlank()) "" else "：$tail")
        }
        // 起得来 ≠ 活着：进程秒退（rootfs 不可执行 / proot 报错）时 start() 仍返回 true（2026-09-15 实测）
        if (!PiRpc.aliveAfterStartup()) {
            val tail = PiRpc.stderrText().lines().lastOrNull { it.isNotBlank() }.orEmpty()
            PiRpc.stop()
            return PiReadiness.Unready to ("pi 通道起来后立刻退出" + if (tail.isBlank()) "" else "：$tail")
        }
        // 再要一次真实往返（RPC 通了才算真的可用；失败 = 管道/进程有问题）
        if (PiRpc.getState() == null) {
            val tail = PiRpc.stderrText().lines().lastOrNull { it.isNotBlank() }.orEmpty()
            PiRpc.stop()
            return PiReadiness.Unready to ("pi 通道无响应" + if (tail.isBlank()) "" else "：$tail")
        }
        return PiReadiness.Ready to ""
    }

    /** pi 通道最近一次报错（回合内收敛，供本回合失败时如实抛给 UI） */
    private var lastPiError: String? = null

    /**
     * 发送消息并请求 AI 回复（**pi 唯一路径**）：只把本条用户回合的文本交给 pi 的 `prompt`，
     * 上下文由 pi 自己维护；思考级别 / 流式开关 / 模型参数来自输入栏与配置页状态。
     * 请求失败以 error 助手消息呈现（不进上下文）。
     */
    suspend fun streamReply(userText: String, quote: Quote? = null) {
        // ── pi 唯一产品路径的门控（2026-09-15 拍板 B）─────────────────────────────
        // 已知未就绪 → 明确阻断：不发送、不落任何条目、草稿留在输入栏（ChatScreen 给提示）
        if (piReadiness == PiReadiness.Unready) {
            blockedNote = "pi 运行时未就绪：消息未发送（点上方提示条的「环境配置」修复）"
            Log.w(TAG_CHAT, "发送被阻断：pi 未就绪（$piUnreadyReason）")
            return
        }
        isStreaming = true
        streamDraft = ""
        streamThinking = ""
        streamThinkingStartedAt = 0L
        // 2026-09-15 收口：App 侧不再拼历史、不再自己压缩（`buildApiHistory` / `maybeAutoCompact`
        // 与 `data/Compaction.kt` 一并删除）；这里只组装**本条用户回合**的文本，交给 pi 的 prompt。
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
        // 本轮用户回合的请求文本：附件以「名称 · 路径」附在正文后（本条是最新回合，媒体恒保留）；
        // 这段文本就是交给 pi 的那条用户消息 —— 上下文由 pi 维护，App 不再拼历史。
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
        val userTurnText = ContextPolicy.promptTextFor(textMsg) +
            (if (inline.notes.isNotEmpty()) "\n\n" + inline.notes.joinToString("\n") else "")
        val promptText = quote?.toPrompt(userTurnText) ?: userTurnText
        try {
            val outcome = runChat(
                cfg = cfg.copy(modelList = effectiveModel),
                userTurnText = promptText,
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
        // 重新生成 = 拿「该消息之前最近的一条用户消息」再问一次（上下文由 pi 维护，App 不拼历史）
        val prevUser = list.subList(0, index).lastOrNull { it is Msg.User } as? Msg.User
            ?: return "缺少可用的上下文"
        val prevText = ContextPolicy.promptTextFor(prevUser)
            .let { t -> prevUser.quote?.toPrompt(t) ?: t }
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
                userTurnText = prevText,
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
     * 单次对话请求（**pi 通道 = 唯一路径**，2026-09-15 收口）：
     * 只把用户回合的文本 + 本次要直发的媒体部件交给 pi 的 `prompt`，pi 在同一进程里累积上下文；
     * 流式增量与 usage 由 [runChatViaPi] 接回 UI。pi 起不来一律**阻断**——
     * 不再有直连内核回退（见《会话与上下文管理设计》§7.1，§8.3 作废）。
     */
    private suspend fun runChat(
        cfg: ProviderConfig,
        userTurnText: String,
        onDelta: (String) -> Unit,
        onThinking: (String) -> Unit = {},
        /** 本回合要直发的附件部件（媒体能力开关；只作用于最新一条用户消息） */
        media: List<WirePart> = emptyList(),
    ): ChatOutcome {
        val target = piChannelTarget()
        if (target != null && PiRpc.usable() && PiRpc.start(target.first, target.second)) {
            bindPiSession()          // 会话映射：懒建 / 切到本会话对应的 pi 会话文件（失败不阻断本轮）
            if (piReadiness != PiReadiness.Ready) {
                piReadiness = PiReadiness.Ready
                piUnreadyReason = ""
            }
            return runChatViaPi(cfg, userTurnText, media, onDelta, onThinking)
        }
        // ── pi 起不来：**不换路**（2026-09-15 拍板 B + 收口）────────────────────────
        // 直连没有任何工具能力，落回去会让用户以为自己在用 agent；§8.3 作废后本地也不存在
        // 「只在本地的新消息」这种状态 —— 所以只有一条路：如实报错，让用户去修环境。
        Log.w(TAG_CHAT, "pi 通道不可用，本轮没走 pi：${PiRpc.stderrText().takeLast(300)}")
        piReadiness = PiReadiness.Unready
        if (piUnreadyReason.isBlank()) piUnreadyReason = "pi 通道启动失败：可到「环境配置」里检测/更新"
        throw AiException("pi 运行时未就绪：本轮没有发送。请到「终端 → 环境配置」检查 Ubuntu / pi。")
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

    /** pi 树的归属会话（2026-09-15）：切会话后旧树立即作废（否则画布会显示上一个会话的树） */
    var piTreeSessionId by mutableStateOf<String?>(null)
        private set

    /**
     * 画布节点（用户条目 id）→ **锚点条目 id**（该节点回合末尾的**非用户**条目）。
     * 这是「会话内分支」的导航目标（《Pient 会话与上下文管理设计》§4.2）：必须交锚点，
     * **不能**交用户消息条目本身 —— 后者会触发 pi「叶退到父 + 文本回填编辑器」的原生语义
     * （`agent-session.ts:3236-3251`），位置会退到该节点**之前**。
     * 表里没有的节点 = 尚无回答（无锚点）→ 按 pi 原生语义处理（退回该消息之前 + 回填输入栏）。
     */
    var piAnchorOf by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /**
     * **期望位置**（pi 条目 id）：对账基准（《会话与上下文管理设计》§4.3）。
     * pi 的活跃叶只在内存里、重启/切会话后回到文件末尾，所以位置由 Pient 记并在每次绑定时拉回来。
     */
    val piDesiredLeaf = mutableStateMapOf<String, String>()

    /** 画布/详情用的 pi 条目索引（解析 get_tree 时留存；普通字段，避免无谓重组） */
    private var piEntryById: Map<String, JSONObject> = emptyMap()
    private var piParentOf: Map<String, String?> = emptyMap()

    /**
     * 绑（或切到）当前 Pient 会话对应的 pi 会话文件。
     * **懒建**：没有就让 pi `new_session`（顺带把 Pient 的标题 `set_session_name` 同步过去），
     * 拿到 sessionFile 记回会话记录并立刻落盘；已有则 `switch_session`。
     */
    suspend fun bindPiSession() {
        val id = currentSessionId ?: return
        val rec = sessionRecord(id) ?: return
        runCatching {
            // 通道没起就自己起（画布、切会话这些入口没有"发送"那一路的启动逻辑）——
            // 否则 getTree/switchSession 全落空，画布会是空的
            piChannelTarget()?.let { t ->            // 统一口径（见 [piChannelTarget] 注释）
                if (PiRpc.usable()) PiRpc.start(t.first, t.second)
            }
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
                // **同一个会话文件不要重载**：switch_session 会按文件末尾重新定位活跃叶，
                // 把用户刚在画布上切好的分支位置冲掉（实测：切到 #2 后发消息，上下文又回到全量 4 条来回）。
                // 只有 pi 当前打开的不是这个文件时才切。
                val current = PiRpc.getSessionStats()?.optString("sessionFile").orEmpty()
                if (current != file) PiRpc.switchSession(file)
            }
            // 会话标题**回流**：pi 里的 `sessionName` 是真相源（改名的去程是 set_session_name，
            // 但会话可能在别处被改名 —— pi 的 TUI/脚本、或同一文件的另一处引用），这里拉回来对齐，
            // 免得两边标题长期不一致（对齐后两边相等，下次进来就是 no-op）。
            val piName = PiRpc.getState()?.optString("sessionName").orEmpty()
            if (piName.isNotBlank() && piName != rec.title) {
                renameSession(id, piName)
                Log.i(TAG_CHAT, "会话标题按 pi 回流：$piName")
            }
            refreshPiTree()
            syncMessagesFromPi()
            // 位置对账（T1/T2/T3）：通道重启、切会话后 pi 的叶会回到文件末尾 —— 拉回 Pient 记的位置
            reconcilePiLeaf(id)
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
    suspend fun refreshPiTree(follow: Boolean = false) {
        val sid = currentSessionId ?: return
        val rec = sessionRecord(sid) ?: return
        if (rec.piSessionFile.isNullOrBlank() || !PiRpc.usable()) return
        val data = PiRpc.getTree() ?: return
        piLeafId = data.optString("leafId").takeIf { it.isNotBlank() && it != "null" }
        val parsed = piParseTree(data) ?: return
        piTree = parsed.tree
        piTreeSessionId = sid            // 归属：切会话后旧树立即作废
        piAnchorOf = parsed.anchorOf
        piEntryById = parsed.entryById
        piParentOf = parsed.parentOf
        // follow = 一轮结束/切到某分支之后：期望位置跟随后端（pi 追加后的叶就是"当前所在"）
        if (follow) normalizedPiLeaf()?.let { piDesiredLeaf[sid] = it }
    }

    /**
     * 规范化叶（§4.3）：叶指向 label / branch_summary / session_info 等**元数据条目**时（重载后常见），
     * 沿父链回退到最近的 message 条目再比较 —— 否则每次绑定都会白跑一次导航。
     */
    private fun normalizedPiLeaf(): String? {
        var cur = piLeafId
        var hops = 0
        while (cur != null && hops++ < 64) {
            val t = piEntryById[cur]?.optString("type")
            if (t == null || t == "message") return cur
            cur = piParentOf[cur]
        }
        return piLeafId
    }

    /**
     * **位置对账**（幂等；《会话与上下文管理设计》§4.3 T1/T2/T3）：pi 的叶只在内存、
     * 重启/切会话后回到文件末尾 —— 发送前把 pi 拉回 Pient 记的期望位置；一致时一个 RPC 都不发。
     */
    private suspend fun reconcilePiLeaf(sid: String) {
        val want = piDesiredLeaf[sid] ?: return
        if (!piEntryById.containsKey(want)) {
            Log.w(TAG_CHAT, "位置对账：目标条目 $want 不在当前树里 → 丢弃该期望位置")
            piDesiredLeaf.remove(sid)
            return
        }
        val have = normalizedPiLeaf()
        if (have == want) return
        Log.i(TAG_CHAT, "位置对账：pi 叶 $have ≠ 目标 $want → /pient-nav")
        runCatching { PiRpc.navigate(want, summarize = false) }
            .onFailure { Log.w(TAG_CHAT, "位置对账失败：${it.message}") }
        refreshPiTree()
        syncMessagesFromPi()
    }

    /**
     * **用 pi 的当前上下文重建消息流**（2026-09-14）：切会话 / 切分支之后调用。
     *
     * 为什么需要：pi 的会话文件才是真相源 —— 用户在画布上切了分支，pi 的上下文已经换了，
     * 界面必须跟着换（否则会「pi 在 A 分支、界面还停在 B 分支」）。rpc 的 `get_messages`
     * 返回的就是**当前活跃路径**上的消息。
     *
     * 本切片只做**文本消息**的镜像（工具卡/附件等内容部件的还原是下一步），
     * 因此只在 pi 侧发生结构性变化（切会话/切分支）时调用，**不在每轮结束后调用** ——
     * 免得把流式过程中本地渲染的工具卡冲掉。
     */
    suspend fun syncMessagesFromPi() {
        val id = currentSessionId ?: return
        val rec = sessionRecord(id) ?: return
        if (rec.piSessionFile.isNullOrBlank() || !PiRpc.usable()) return
        // ── **本轮在飞 / 有未落盘的用户消息时不要重建**（2026-09-15 实测 bug）──────────────
        // `streamReply` 先 `appendEntry(用户消息)` 上屏，紧接着 `runChat` 里 `bindPiSession()` 会走到这里；
        // 而 pi 是在更后面的 `prompt` 里才记录这条消息 —— 此刻 clear+addAll 会把它冲掉，
        // 表现为「画布创建分支后回聊天页，第一条消息不显示在聊天页（但回答照收、画布节点也在）」。
        if (isStreaming) {
            Log.i(TAG_CHAT, "本轮在飞：跳过按 pi 重建消息流（保住刚上屏的用户消息）")
            return
        }
        val arr = PiRpc.getMessages()?.optJSONArray("messages") ?: return
        val rebuilt = ArrayList<Msg>(arr.length())
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            when (m.optString("role")) {
                "user" -> {
                    val text = piText(m).trim()
                    if (text.isNotEmpty()) rebuilt += Msg.User(text)
                }
                "assistant" -> {
                    val text = piText(m).trim()
                    if (text.isNotEmpty()) rebuilt += Msg.Assistant(text, null)
                    // 助手消息里带的**工具调用块**（pi 的 content 里 type="toolCall"）→ 还原成工具行。
                    // 不做这一步，每轮开头的重建就会把工具行冲掉（实测踩过：第二轮进来看不到上一轮的工具卡）。
                    val content = m.opt("content")
                    if (content is JSONArray) {
                        for (j in 0 until content.length()) {
                            val b = content.optJSONObject(j) ?: continue
                            if (b.optString("type") != "toolCall") continue
                            rebuilt += Msg.ToolCall(
                                name = b.optString("name"),
                                // params 存原样 JSON 串（ToolRows 按 JSON 解析出 command/path/…）
                                params = b.opt("arguments")?.toString().orEmpty(),
                                status = ToolStatus.DONE,
                            )
                        }
                    }
                }
                "toolResult" -> {
                    val text = piText(m).trim()
                    rebuilt += Msg.ToolResult(
                        toolName = m.optString("toolName"),
                        preview = piPreview(text),
                        full = text.takeIf { it.isNotBlank() },
                    )
                }
            }
        }
        if (rebuilt.isEmpty()) return
        val list = messagesBySession.getOrPut(id) { mutableStateListOf() }
        // 兜底：本地尾部若是一条 pi 还不认识的用户消息（刚发出、pi 未落盘），重建后补回去 ——
        // 用户看得见自己发过的话，比「报告一个更完整的历史」重要。
        val pending = list.lastOrNull() as? Msg.User
        // 判重口径：pi 侧同一条消息可能带附件/引用（它的正文是本地文本的超集，或本地是它的超集）→ 双向包含匹配
        val known = rebuilt.any { it is Msg.User && (it.text.contains(pending?.text.orEmpty()) || pending?.text.orEmpty().contains(it.text)) }
        list.clear()
        list.addAll(rebuilt)
        if (pending != null && pending.text.isNotBlank() && !known) {
            list += pending
            Log.i(TAG_CHAT, "重建后补回未被 pi 记录的用户消息：${pending.text.take(24)}")
        }
        // 位置**不进本地条目树**（2026-09-15）：pi 的条目 id 与本地镜像 id 不同源，写进来会让
        // leafPath 落空、会话在界面上变空白（实测踩过）。pi 侧位置由 [piDesiredLeaf] 单独记。
        Log.i(TAG_CHAT, "消息流已按 pi 上下文重建：${rebuilt.size} 条")
    }

    /** 解析 get_tree 的产物：画布树 + 锚点表 + 条目索引（锚点/对账都要原始 id 空间） */
    private class PiParsed(
        val tree: SessionTreeNode,
        val anchorOf: Map<String, String>,
        val entryById: Map<String, JSONObject>,
        val parentOf: Map<String, String?>,
    )

    /** pi `get_tree` → 画布树 + 锚点（节点 = 用户消息，与其后的助手文本做 exchange —— 与本地画布同口径） */
    private fun piParseTree(data: JSONObject): PiParsed? {
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
            val pid = e.optString("parentId").takeIf { it.isNotBlank() && it != "null" }
            parentOf[id] = pid
            // childrenOf 以前只声明没填（这棵树的解析全靠 parentOf 反查），按子树取回合内容时才发现 —— 实测踩过
            if (pid != null) childrenOf.getOrPut(pid) { mutableListOf() } += id
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

        // 《Pient 分支功能设计》§2.1/§7：**节点 = 一条用户消息**，该回合的 AI 回答/思考/工具条目
        // 压缩进节点（不各自建卡）。pi 条目里 role 有 user/assistant/toolResult，且**工具结果也是
        // role=user 的条目**（内容块是 toolResult）—— 所以三重判定：message 条目 + role=user +
        // 有文本块且无 toolResult 块，避免助手/工具结果被当成节点（实测踩过：画布 28 个节点）。
        fun isUserTurn(id: String): Boolean = isPiUserNode(entryById[id])
        val userIds = order.filter { isUserTurn(it) }
        fun textOf(id: String) = piText(entryById[id]?.optJSONObject("message"))
        val idxOf = HashMap<String, Int>().apply { userIds.forEachIndexed { i, u -> put(u, i) } }
        // 每个用户消息的 exchange = **它自己这一回合**里的助手文本：从它往下走，遇到下一个用户消息就停。
        // 早先按拍平顺序（order）切段，分叉后会串味（另一条支的助手文本被算给上一个用户消息、
        // 新支末尾的用户消息拿到空段显示「（暂无回答）」）—— 实测踩过。
        fun assistantTextsInTurn(rootId: String): List<Msg> {
            val out = ArrayList<Msg>()
            val queue = ArrayDeque<String>()
            childrenOf[rootId]?.forEach { queue += it }
            while (queue.isNotEmpty()) {
                val id = queue.removeFirst()
                if (idxOf.containsKey(id)) continue          // 下一个用户消息 = 本回合的边界
                val t = textOf(id).trim()
                if (t.isNotEmpty() && piRole(entryById[id]) == "assistant") out += Msg.Assistant(t, null)
                childrenOf[id]?.forEach { queue += it }
            }
            return out
        }
        val exchangeOf = HashMap<String, List<Msg>>()
        userIds.forEach { exchangeOf[it] = assistantTextsInTurn(it) }
        // 父节点 = 该用户消息上游最近的那个用户消息
        fun nearestUserAncestor(id: String): String? {
            var p = parentOf[id]
            while (p != null) {
                if (idxOf.containsKey(p)) return p
                p = parentOf[p]
            }
            return null
        }
        // 锚点（§4.2）：节点 → 该回合**末尾的非用户条目**。回合内容 = 紧跟其后的 message 条目
        // （助手回答 / 工具结果），遇下一条用户消息停；元数据条目（label / branch_summary /
        // session_info / compaction / model_change / thinking_level_change）不算回合内容、也不算分叉。
        fun isTurnContent(id: String): Boolean =
            entryById[id]?.optString("type") == "message" && !idxOf.containsKey(id)
        val anchorOf = HashMap<String, String>()
        userIds.forEach { u ->
            var cur = u
            while (true) {
                val kids = childrenOf[cur].orEmpty().filter { isTurnContent(it) }
                if (kids.size != 1) break
                cur = kids[0]
            }
            if (cur != u) anchorOf[u] = cur      // cur == u ⇒ 该节点尚无回答（无锚点）
        }
        fun build(id: String): SessionTreeNode {
            // ⚠️ 必须再加 `idxOf.containsKey(it)`：光判「最近用户祖先 = id」的话，**所有助手/工具条目**
            // 都会挂成子节点（它们的最近用户祖先也是这个 id）→ 画布节点数从 11 变 28、蓝色扭成折线。
            // 文档 §7：节点数 = 用户消息数（回合内容压缩进节点，走 exchange）。
            val kids = order.filter { idxOf.containsKey(it) && nearestUserAncestor(it) == id }
            return SessionTreeNode(
                id = id,
                userText = textOf(id).trim(),
                exchange = exchangeOf[id].orEmpty(),
                children = kids.map { build(it) },
                active = activePath.contains(id),
            )
        }
        val topNodes = userIds.filter { nearestUserAncestor(it) == null }
        val built = topNodes.map { build(it) }.toMutableList()
        // 兜底：pi 侧还没给 leaf（或 leaf 不在任何用户节点路径上）时，把最后一个顶层节点标成"当前"，
        // 保证画布至少有一个 active（否则画布的初始视口适配会抛 NoSuchElementException，实测崩过）
        if (built.isNotEmpty() && built.none { it.active }) {
            built[built.lastIndex] = built.last().copy(active = true)
        }
        // 画布只认单根：多个根（分叉起点不同）时包一个合成根
        val tree = if (built.size == 1) built[0] else SessionTreeNode(
            id = "pi-root",
            userText = "",
            exchange = emptyList(),
            children = built,
            active = built.any { it.active },
        )
        return PiParsed(tree, anchorOf, entryById, parentOf)
    }

    /**
     * pi 条目是不是「画布节点」= 一条**用户消息**（《Pient 分支功能设计》§2.1 / §7）。
     * 三重判定：`type=message` + `role=user` + 有文本块且**无 toolResult 块** ——
     * pi 的工具结果条目内容块是 `toolResult`（老版本 role 也可能是 user），不这样判会把
     * 助手/工具条目也当成节点（实测踩过：画布 28 个节点）。详情卡的回合边界用同一条判据。
     */
    private fun isPiUserNode(e: JSONObject?): Boolean {
        if (e == null || e.optString("type") != "message") return false
        val msg = e.optJSONObject("message") ?: return false
        if (msg.optString("role") != "user") return false
        val arr = msg.optJSONArray("content") ?: return msg.optString("content").isNotBlank()
        var hasText = false
        for (i in 0 until arr.length()) {
            val b = arr.optJSONObject(i) ?: continue
            when (b.optString("type")) {
                "toolResult" -> return false            // 工具结果载体，不算用户消息
                "text" -> if (b.optString("text").isNotBlank()) hasText = true
            }
        }
        return hasText
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
        val newId = newSessionId()
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
        userTurnText: String,
        media: List<WirePart>,
        onDelta: (String) -> Unit,
        onThinking: (String) -> Unit,
    ): ChatOutcome = coroutineScope {
        val userText = userTurnText
        // 附件直发：pi 的 `prompt` 支持 images（ImageContent = {type,data,mimeType}，见 pi docs/rpc.md）。
        // media 里已经是 base64 部件（[MediaInline] 按媒体开关与上限筛过），这里只挑图片 ——
        // pi 的 ImageContent 只有图片这一种；音频/视频在 pi 通道给一行占位说明，不让用户误以为发出去了。
        val piImages = media.filter { it.type == "image" }.map { part ->
            JSONObject()
                .put("type", "image")
                .put("data", part.base64)
                .put("mimeType", part.mime.ifBlank { "image/png" })
        }
        val skippedMedia = media.count { it.type != "image" }
        val promptText = if (skippedMedia > 0) {
            "$userText\n\n[附件未直发] 另有 $skippedMedia 个非图片附件（pi 通道只直发图片）"
        } else userText
        val text = StringBuilder()
        val think = StringBuilder()
        var usage: Usage? = null
        val settled = CompletableDeferred<Unit>()
        lastPiError = null
        // pi 的工具事件 → 消息区（这两类消息的 UI 一直都在：ToolRows 渲染 ToolCall + 紧跟的 ToolResult）
        val toolCallAt = HashMap<String, Int>()   // toolCallId → ToolCall 消息下标
        val toolResAt = HashMap<String, Int>()    // toolCallId → ToolResult 消息下标
        val collector = launch {
            PiRpc.events.collect { ev ->
                val evType = ev.optString("type")
                // 打点：非流式事件都记一行（message_update 太多不记）——排查"工具事件有没有到收集器"
                if (evType != "message_update") Log.i(TAG_CHAT, "pi 事件：$evType")
                when (evType) {
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
                    "tool_execution_start" -> {
                        val callId = ev.optString("toolCallId")
                        val name = ev.optString("toolName")
                        val msgs = currentMessages
                        toolCallAt[callId] = msgs.size
                        appendEntry(
                            Msg.ToolCall(
                                name = name,
                                // **存原样的 args JSON**：ToolRows 用 JSONObject(params) 解析出
                                // command/path/pattern 来渲染标题（实测踩过：塞纯命令串 → 标题只剩「已运行命令」）
                                params = ev.optJSONObject("args")?.toString().orEmpty(),
                                status = ToolStatus.RUNNING,
                                startedAtMs = System.currentTimeMillis(),
                            ),
                        )
                        toolResAt[callId] = msgs.size
                        appendEntry(Msg.ToolResult(toolName = name, preview = ""))
                        Log.i(TAG_CHAT, "pi 工具开始：$name")
                        // 终端镜像（2026-09-16）：与 Operit 的观感对齐 —— AI 在 Ubuntu 里跑什么，终端页看得见
                        AppCtx.get()?.let {
                            com.pient.app.runtime.TerminalSessions.mirror(it, mirrorStartLine(name, ev.optJSONObject("args")))
                        }
                    }
                    "tool_execution_update" -> {
                        val callId = ev.optString("toolCallId")
                        val idx = toolResAt[callId] ?: return@collect
                        val text = piResultText(ev.optJSONObject("partialResult"))
                        if (text.isNotBlank()) {
                            replaceMessageAt(idx, Msg.ToolResult(ev.optString("toolName"), piPreview(text), text))
                        }
                    }
                    "tool_execution_end" -> {
                        val callId = ev.optString("toolCallId")
                        val name = ev.optString("toolName")
                        val failed = ev.optBoolean("isError")
                        val text = piResultText(ev.optJSONObject("result"))
                        val callIdx = toolCallAt[callId]
                        val resIdx = toolResAt[callId]
                        if (resIdx != null && resIdx < currentMessages.size) {
                            replaceMessageAt(resIdx, Msg.ToolResult(name, piPreview(text), text.ifBlank { null }))
                        }
                        if (callIdx != null && callIdx < currentMessages.size) {
                            val old = currentMessages[callIdx] as? Msg.ToolCall ?: Msg.ToolCall(name, "")
                            val started = old.startedAtMs
                            val dur = started?.let { System.currentTimeMillis() - it }
                            replaceMessageAt(
                                callIdx,
                                old.copy(
                                    status = if (failed) ToolStatus.FAILED else ToolStatus.DONE,
                                    durationMs = dur,
                                    // pi `edit` 会在 details 里给 unified diff（文件卡的 +N/−M 与 diff 面板靠它）
                                    diff = ev.optJSONObject("result")?.optJSONObject("details")
                                        ?.optString("diff")?.takeIf { it.isNotBlank() } ?: old.diff,
                                ),
                            )
                        }
                        Log.i(TAG_CHAT, "pi 工具结束：$name 失败=$failed")
                        AppCtx.get()?.let {
                            com.pient.app.runtime.TerminalSessions.mirror(it, mirrorEndLine(name, failed, text))
                        }
                    }
                    "agent_settled", "channel_closed" -> settled.complete(Unit)
                    "error" -> {
                        val msg = ev.optJSONObject("error")?.optString("message").orEmpty()
                            .ifBlank { ev.optString("message") }
                        if (msg.isNotBlank()) lastPiError = msg
                    }
                }
            }
        }
        val res = PiRpc.prompt(promptText, piImages)
        if (res != null && !res.optBoolean("success", true)) {
            collector.cancel()
            throw AiException(res.optString("error").ifBlank { "pi 拒绝了这次请求" })
        }
        val ok = withTimeoutOrNull(600_000) { settled.await() } != null
        collector.cancel()
        // 通道中途断开（channel_closed）且本轮没拿到任何文本 → 如实报错，
        // 别落一条空回答让用户以为"AI 回了但看不到内容"（2026-09-15 实测：pi 秒退时就这样）
        if (!PiRpc.processAlive() && text.isBlank() && think.isBlank()) {
            val tail = PiRpc.stderrText().lines().lastOrNull { it.isNotBlank() }.orEmpty()
            throw AiException("pi 通道中途断开（本轮未完成）" + if (tail.isBlank()) "" else "：$tail")
        }
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
        // 期望位置跟随后端（§4.3）：本轮追加后 pi 的叶就是新的"当前所在"（不跟则下次绑定会拉回旧位置）
        runCatching { refreshPiTree(follow = true) }
            .onFailure { Log.w(TAG_CHAT, "回合结束刷新 pi 树失败：${it.message}") }
        ChatOutcome(out, usage?.takeIf { it.inTokens + it.outTokens > 0 }, think.toString())
    }

    /** pi 工具结果里的文本（content 是 [{type:"text",text:…}] 形态） */
    private fun piResultText(result: JSONObject?): String {
        val arr = result?.optJSONArray("content") ?: return ""
        return buildString {
            for (i in 0 until arr.length()) {
                val b = arr.optJSONObject(i) ?: continue
                if (b.optString("type") == "text") append(b.optString("text"))
            }
        }
    }

    /** 工具行预览（截断；全文进 [Msg.ToolResult.full]，点开才看） */
    private fun piPreview(text: String): String =
        if (text.length <= 800) text else text.take(800) + "\n…（共 ${text.length} 字）"

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

    // ─────────── 上下文压缩（**pi 原生**；2026-09-15 收口：App 侧实现已删） ───────────

    /**
     * 手动压缩上下文（= 桌面端的 `/compact`；移动端等价入口 = 上下文用量卡里那个动作）。
     *
     * 为什么需要它：**Android 上没有命令行入口**，而 pi 的手动压缩是 `/compact` 命令 ——
     * 移动端的等价入口就是这个动作。
     *
     * 2026-09-15 收口：压缩**整体归 pi**（官方 RPC `compact`）—— App 不判触发、不切片、不生成
     * 摘要（旧的内核自实现 `data/Compaction.kt` 已删；自动压缩由 pi 按 settings.json 的
     * `compaction` 自己判，配置页那三个旋钮即写进那里）。pi 完成后会重建 `agent.state.messages`，
     * 所以这里随后刷新画布与上屏流。
     * 返回 null = 成功；非空 = 如实回报的原因。
     */
    suspend fun compactNow(): String? {
        val target = piChannelTarget() ?: return "没有可用的服务商 / 模型"
        if (!PiRpc.usable() || !PiRpc.start(target.first, target.second)) {
            return "pi 通道未就绪：先到「环境配置」检查 Ubuntu / pi"
        }
        if (compacting) return "上一次压缩还在进行中"
        compacting = true
        return try {
            val cfg = selectedModel?.provider?.let { AiConfigStore.configs[it] }
            val res = PiRpc.compact(cfg?.compactInstructions?.ifBlank { null })
                ?: return "压缩没有完成：pi 通道无响应"
            if (!res.optBoolean("success", true)) {
                return "压缩失败：" + res.optString("error").ifBlank { "pi 拒绝了这次压缩" }
            }
            // 压缩后上下文换了形态（pi 重建了活跃消息）→ 画布与上屏流都要跟上
            runCatching { refreshPiTree(follow = true) }
            runCatching { syncMessagesFromPi() }
            Log.i(TAG_CHAT, "上下文已压缩（pi 原生 compact）：${res.toString().take(200)}")
            null
        } catch (e: Exception) {
            "压缩失败：" + (e.message ?: "未知错误")
        } finally {
            compacting = false
        }
    }

    /**
     * **系统提示词**（只读面板的数据源，2026-09-15）：提示词的持有者是 **pi**
     * （base prompt + 项目 context 文件 + 扩展改写，App 看不到）。走扩展命令 `/pient-sysprompt`
     * 让 pi 把 `ctx.getSystemPrompt()` 写到 `~/.pi/agent/.pient-sysprompt.txt`，再读回来 ——
     * 面板显示的就是**真实下发的那一份**（旧实现显示的是 App 侧自己拼的提示词，直连内核收口后
     * 那份已经不存在了）。
     * 返回 null = 成功（[systemPrompt] 已更新）；非空 = 原因。
     */
    suspend fun refreshSystemPrompt(): String? {
        val ctx = AppCtx.get() ?: return "应用上下文未就绪"
        val target = piChannelTarget() ?: return "没有可用的服务商 / 模型"
        if (!PiRpc.usable() || !PiRpc.start(target.first, target.second)) {
            return "pi 通道未就绪（先到「环境配置」检查 Ubuntu / pi）"
        }
        val file = File(PiAgentFiles.agentDir(ctx), SYS_PROMPT_FILE)
        val before = file.lastModified()
        runCatching { PiRpc.prompt("/pient-sysprompt") }
            .onFailure { return "取系统提示词失败：${it.message}" }
        // 命令处理里同步写文件，但响应与落盘之间可能有几十毫秒 → 轮询等它变
        var waited = 0
        while (waited < 4000 && file.lastModified() <= before) {
            kotlinx.coroutines.delay(120)
            waited += 120
        }
        val text = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()?.trim().orEmpty()
        if (text.isEmpty()) return "没拿到系统提示词（pi 可能还没起，或扩展命令未加载）"
        systemPrompt = text
        Log.i(TAG_CHAT, "系统提示词已按 pi 回流：${text.length} 字")
        return null
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
        // 自愈（2026-09-15）：叶不在条目树里（历史 bug：把 pi 的 entry id 写进本地叶，或记录损坏）
        // → 回退到**末条目**。否则 leafPath 为空、会话在界面上直接变成"空白"（实测：
        // leaves[s-…] = 78ceb77d ∈ pi 条目而 ∉ 本地条目 → 重启后整个会话读不出消息）。
        if (cur == null && entries.isNotEmpty()) {
            cur = entries.last()
            leafBySession[sid] = cur.id
            Log.w(TAG_CHAT, "叶自愈：会话 $sid 的 leaf 不在条目树里 → 回退到末条目 ${cur.id}")
        }
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

    private fun markRunning(running: Boolean) {
        // 前台保活（2026-09-16，M5）：一轮在跑时把进程挂进前台服务 —— 用户切走 / 熄屏后
        // pi 子进程与它的管道不会被系统清掉（清掉 = 本轮直接消失）。
        // 挂在 markRunning 上是因为它是「本轮是否在跑」的唯一收口点：
        // 开始 / 正常结束 / 中止 / 出错 / 「重新生成」都经过它。
        if (running) PiKeepAlive.acquire(AppCtx.get(), "chat", "AI 正在回复…")
        else {
            PiKeepAlive.release(AppCtx.get(), "chat")
            // 回合收尾时刷新上下文用量真值（get_session_stats.contextUsage；2026-09-16）
            bgScope.launch { runCatching { refreshContextUsage() } }
        }
        val proj = currentProject ?: return
        val list = sessions[proj] ?: return
        val id = currentSessionId ?: return
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) list[i] = list[i].copy(running = running)
    }

    /**
     * 手动终止本轮（输入栏「停止」）。
     *
     * **位置必须跟随后端**（2026-09-15 修 bug）：本回合的 [piDesiredLeaf] 此刻是**过期的** ——
     * 上次写入是上一回合结束时的 `refreshPiTree(follow = true)`，本回合被中止就永远没来得及更新。
     * 留着它，下一次位置对账（开画布、或下一次发送的 T1）会把 pi 的叶**拉回上一回合**，于是
     * 「刚发的那条消息」下面再发一条会挂成它的**兄弟节点** —— 画布上就是「节点有了，但下一条
     * 不顺下去、凭空冒出一条分支」（用户实测）。
     * 所以：① 立刻丢掉过期的期望位置（宁可不对账，也不要拉回旧位置）；② 等 pi 收尾（它要把
     * 已生成的部分落成 aborted 条目）后把位置跟随后端，并按 pi 重建上屏流。
     */
    fun abort() {
        streamJob?.cancel()
        // 已收到的部分先在本地留住（pi 侧那份由 pi 自己落库，重建消息流时会覆盖成同一内容）
        if (streamDraft.isNotEmpty()) {
            appendEntry(Msg.Assistant(streamDraft, null))
            streamDraft = ""
        }
        currentSessionId?.let { piDesiredLeaf.remove(it) }
        isStreaming = false
        markRunning(false)
        bgScope.launch {
            runCatching { PiRpc.abort() }        // pi 侧也要停（否则它继续跑）
            // pi 收尾是异步的：等它不再 streaming（最多 8s）再对位，别在中间态上对账
            var waited = 0
            while (waited < 8000 && PiRpc.isStreamingNow() == true) {
                kotlinx.coroutines.delay(250)
                waited += 250
            }
            runCatching { refreshPiTree(follow = true) }   // 位置跟随后端（叶若落到本回合条目上就记住它）
            runCatching { syncMessagesFromPi() }           // 上屏流按 pi 重建（含中止时的部分回答）
            Log.i(TAG_CHAT, "已终止本轮：位置跟随后端（叶=$piLeafId，等待 ${waited}ms）")
        }
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
        get() {
            // 只有**归属当前会话**的 pi 树才算数：切会话后旧树立即作废（否则画布会显示上一个会话的树）
            val sid = currentSessionId
            val pi = if (sid != null && piTreeSessionId == sid) piTree else null
            return pi ?: buildBranchTree(sid)
        }

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
     * **节点回合全文**（画布 FAB1 节点详情卡的数据源，《Pient 分支功能设计》§3.6）：
     * 该节点（一条用户消息）+ 其后到下一个用户节点之前的全部条目，**按会话顺序**映射成 [Msg] ——
     * 用户消息 → 思考（`thinking` 块）→ 工具调用（`toolCall` 块）+ 结果（`toolResult` 条目）→
     * AI 回答（`text` 块）。详情卡直接复用聊天页那套渲染组件（ThinkingDisclosure / ToolRow）。
     *
     * 数据源 = 解析 pi `get_tree` 时留存的原始条目（[piEntryById] / [piParentOf]，真相源）；
     * pi 树不可用时回落到本地条目树的回合链（与 [buildBranchTree] 同口径）。
     */
    fun turnTranscript(nodeId: String): List<Msg> {
        val sid = currentSessionId ?: return emptyList()
        if (piTreeSessionId == sid && piEntryById.containsKey(nodeId)) {
            val out = ArrayList<Msg>()
            pushPiMessage(piEntryById[nodeId]?.optJSONObject("message"), out)   // 节点自身（用户消息）
            // 子表按条目顺序建（get_tree 的解析顺序 = 会话顺序）
            val kids = HashMap<String, MutableList<String>>()
            piEntryById.keys.forEach { id -> piParentOf[id]?.let { p -> kids.getOrPut(p) { mutableListOf() } += id } }
            fun walk(id: String) {
                kids[id].orEmpty().forEach { c ->
                    val e = piEntryById[c] ?: return@forEach
                    if (isPiUserNode(e)) return@forEach          // 下一个用户节点 = 本回合边界
                    if (e.optString("type") == "message") pushPiMessage(e.optJSONObject("message"), out)
                    walk(c)
                }
            }
            walk(nodeId)
            return out
        }
        // 回落：本地条目树（pi 树不可用）—— 用户条目 + 其后的单链回合条目
        val entries = entriesBySession[sid]?.toList().orEmpty()
        val self = entries.firstOrNull { it.id == nodeId && it.msg is Msg.User } ?: return emptyList()
        val out = mutableListOf<Msg>(self.msg)
        var cur = self
        while (true) {
            val next = entries.filter { it.parentId == cur.id && it.msg !is Msg.User }
            if (next.size != 1) break
            out += next[0].msg
            cur = next[0]
        }
        return out
    }

    /**
     * pi 的 `message` 对象 → [Msg]（按内容块顺序：thinking / text / toolCall）；
     * 工具结果在 pi 里是**独立条目**（消息体 role=toolResult），单独走一条。
     */
    private fun pushPiMessage(msg: JSONObject?, out: MutableList<Msg>) {
        if (msg == null) return
        when (msg.optString("role")) {
            "user" -> piText(msg).trim().takeIf { it.isNotEmpty() }?.let { out += Msg.User(it) }
            "assistant" -> {
                val arr = msg.optJSONArray("content") ?: return
                for (i in 0 until arr.length()) {
                    val b = arr.optJSONObject(i) ?: continue
                    when (b.optString("type")) {
                        // 思考块：pi 条目里没有时长（durationMs = null → 折叠行按「已思考」呈现）
                        "thinking" -> b.optString("thinking").trim().takeIf { it.isNotEmpty() }?.let {
                            val level = if (thinkingEnabled) thinkingLevel.piValue else "off"
                            out += Msg.Thinking(level, it, null)
                        }
                        "text" -> b.optString("text").trim().takeIf { it.isNotEmpty() }
                            ?.let { out += Msg.Assistant(it, null) }
                        // 工具调用：params 存原样 JSON 串（ToolRows 按 JSON 解析出 command/path/…）
                        "toolCall" -> out += Msg.ToolCall(
                            name = b.optString("name"),
                            params = b.opt("arguments")?.toString().orEmpty(),
                            status = ToolStatus.DONE,
                        )
                    }
                }
            }
            "toolResult" -> {
                val text = piText(msg).trim()
                out += Msg.ToolResult(
                    toolName = msg.optString("toolName"),
                    preview = piPreview(text),
                    full = text.takeIf { it.isNotBlank() },
                )
            }
        }
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
        // pi 唯一路径（2026-09-15 收口：不再有 piChannelEnabled 开关）
        if (piTreeSessionId == sid && piTree != null &&
            !rec?.piSessionFile.isNullOrBlank() && PiRpc.usable()
        ) {
            // **导航目标 = 锚点**（该节点回合末尾的非用户条目），不是节点自身：把用户消息条目交给 pi
            // 的 navigateTree 会触发它「叶退到父 + 文本回填编辑器」的重编辑语义，位置退到该节点**之前**。
            // **尚无回答的节点没有锚点** —— 交给 pi 的扩展命令，由它给这条用户消息补一条**锚点标记**
            // 再定位（assets/pient-pi-extension.ts 的 resolveTarget）：语义统一为「停在该消息本身」，
            // 有没有回答行为一致（2026-09-15 用户定）。
            val anchor = piAnchorOf[nodeId]
            val target = anchor ?: nodeId
            piDesiredLeaf[sid] = target
            bgScope.launch {
                // 摘要是可选的：pi 会为此**调用一次模型**（用户偏好见 SettingsStore.branchSummarize）
                runCatching { PiRpc.navigate(target, summarize = SettingsStore.branchSummarize) }
                    .onFailure { Log.w(TAG_CHAT, "pi 会话内分支跳转失败：${it.message}") }
                refreshPiTree()
                syncMessagesFromPi()
                Log.i(TAG_CHAT, "会话内分支跳转：节点 $nodeId → 目标 $target（pi 叶=$piLeafId）")
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
        val newId = newSessionId()
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
    // 上下文用量（**pi 真值**，2026-09-16 取代原型常量 61200 / 180000）：
    // 由 [refreshContextUsage] 从 `get_session_stats.contextUsage` 填；窗口未知时卡上显示 `—`。
    var windowTokens by mutableStateOf(0)
    var maxWindowTokens by mutableStateOf(0)
    // 用量是否已知：pi 在「压缩后还没有新回复」时给不出 tokens（agent-session.ts 的口径），
    // 此时卡上照 pi-web 显示 `?`，不编数字。
    var contextUsageKnown by mutableStateOf(false)
    var connectionLabel by mutableStateOf("已连接")
    // 系统提示词只读展示（2026-09-01，对齐 pi-web system 面板）：
    // **真实值 = pi 当前生效的那一份**（base prompt + 项目 context 文件 + 扩展改写），
    // 打开面板时由 [refreshSystemPrompt] 经扩展命令 `/pient-sysprompt` 回流写入 ——
    // 面板显示的必须是模型真正收到的内容（App 侧不再持有自己的提示词）。
    var systemPrompt by mutableStateOf("")

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
