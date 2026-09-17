package com.pient.app.data

import com.pient.app.data.i18n.L
import com.pient.app.AppCtx
import com.pient.app.runtime.KeepAliveDetail
import com.pient.app.runtime.PiKeepAlive
import com.pient.app.runtime.PiPolish

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
import com.pient.app.runtime.PiRpc
import com.pient.app.runtime.PiRpcState
import com.pient.app.runtime.PiRuntime
import com.pient.app.runtime.ReplyNotify
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

/**
 * pi 侧「用户消息元数据」标记的 customType（`custom` 条目：不进 LLM 上下文、不进画布）。
 * 必须与 `assets/pient-pi-extension.ts` 的 META_TYPE 一致 —— 发送前写标记、重建时按它把
 * 引用与**附件清单（含文件名）**挂回来。
 *
 * 为什么附件清单也要写：pi 的 `image` 内容块只有 `data/mimeType`（协议里没有文件名），
 * 直发时正文里那行「[附件] 名称 · 路径」又按 Operit「移除链接」口径被去掉 —— 于是
 * **pi 侧完全没有名字**，只有本地镜像知道。标记把名字固化进会话文件（跨重建/重装/换端都在）。
 */
private const val PI_META_TYPE = "pient_meta"

/** 旧版标记（只带引用，2026-09-17 上半场）；读侧仍认，写侧已不用 */
private const val PI_QUOTE_TYPE = "pient_quote"

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

    /**
     * 本轮工具调用数（2026-09-16）：只服务前台保活通知卡片上的「工具调用：N」。
     * 回合开始归零（[markRunning]），每来一条 `tool_execution_start` 加一。
     */
    var toolCallsThisTurn by mutableStateOf(0)

    init {
        // 通知卡片明细（2026-09-16）：runtime 层不反向依赖 data 层，这里把数据源注册进去 ——
        // 模型名 / 思考等级 / 终端会话数（不含「AI 执行」镜像）/ 本轮工具调用数。
        PiKeepAlive.detailProvider = {
            KeepAliveDetail(
                model = selectedModel?.name,
                thinking = if (thinkingEnabled) ThinkingLevel.labelOf(thinkingLevel) else "off",
                sessions = runCatching {
                    com.pient.app.runtime.TerminalSessions.sessions.count { !it.aiMirror }
                }.getOrDefault(0),
                toolCalls = toolCallsThisTurn,
            )
        }
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
        val list = entriesOf(sid)
        val id = newEntryId(list)
        list += SessionEntry(id, leafBySession[sid], msg)
        leafBySession[sid] = id
        val screen = messagesBySession.getOrPut(sid) { mutableStateListOf() }
        screen += msg
        return screen.lastIndex
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
        // 启动恢复：把当前项目的目录写给 guest（否则它落在 pi 自己的运行目录里）
        syncWorkspace(currentProject)
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
        onSessionChanged()   // 换项目＝换会话：环归位并按新会话的 pi 文件读回
        syncWorkspace(name)  // 工作区指针：guest 的 /workspace 跟着换项目（AI 与终端都落在里面）
        // 项目切换：清空文件预览标签与树展开状态（标签/展开路径属于原项目的文件树，
        // 2026-09-02 修复：切项目后标签栏仍显示上一项目文件）
        openTabs.clear()
        activeTabIndex = 0
        expandedDirs.clear()
    }

    /**
     * 工作区指针同步：**切到哪个项目，AI 与终端就落在哪个项目的文件夹里**。
     *
     * guest 的 `/workspace` 是包装脚本按 `$P/workspace` 指针文件**现读**挂上的 bind 挂载
     * （PRoot 档 `pient-shell.sh:26-27,56-57`；chroot 档 `pient-root-wrapper.sh:30`）。
     * 这个指针此前只有「项目改名」会写，切换 / 新建 / 启动恢复 / 删除回退都没写 —— 指针缺失时
     * 包装脚本退回 `$P/app`，AI 实际落在**自己的运行目录**里（2026-09-16 真机实测：guest `ls /workspace`
     * 列出的是 pi 运行时那几个文件，而不是项目目录里的 `.pient-project.json`）。
     * `PiSkills` 的项目技能根（`<workspaceDir>/.pi/skills`）也吃这条，所以它一并被修好。
     *
     * 指针变了要重启 pi 通道：proot 的绑定挂载是**进程级**的，已在跑的 pi 仍看着旧目录，而它每次
     * spawn 的 `bash` 工具都重读指针 → 两者会看到不同目录。pi 正忙时不打断它，记一个 pending，
     * 等它空闲（下次发送前）再换（见 [applyPendingWorkspaceRestart]）。
     */
    private fun syncWorkspace(projectName: String?) {
        val ctx = AppCtx.get() ?: return
        val dir = projectName?.let { n -> projects.firstOrNull { it.name == n }?.path }?.let { java.io.File(it) }
        val before = runCatching { PiRuntime.workspaceDir(ctx).absolutePath }.getOrNull()
        PiRuntime.setWorkspace(ctx, dir)
        val after = runCatching { PiRuntime.workspaceDir(ctx).absolutePath }.getOrNull()
        if (before == after) return
        PientLog.i(TAG_CHAT, "工作区已切到：$after（原：$before）")
        bgScope.launch { applyPendingWorkspaceRestart() }
    }

    /**
     * 启动就绪后（`AppCtx` 已注入）把当前项目的目录写给 guest。
     *
     * 为什么单独留一个入口：读盘（[ChatStore.load] → [normalizeAfterLoad]）跑在 `AppCtx.set` **之前**，
     * 那时 `AppCtx.get()` 还是 null → 那条调用会静默跳过（2026-09-16 实测：装包后启动，指针文件没被写）。
     */
    fun syncWorkspaceToCurrentProject() = syncWorkspace(currentProject)

    /** 工作区换了但 pi 正忙 → 记下来，等它空闲（下次发送前）再重启通道 */
    private var workspaceRestartPending = false

    /**
     * 把 pi 通道重启到新工作区（**只在该通道真的在跑时**才重启 —— 没在跑下次启动自然读到新指针，
     * 也就不会在冷启动时把 pi 拉起来）。重启后要重绑本会话的 pi 文件。
     */
    private suspend fun applyPendingWorkspaceRestart() {
        if (!PiRpc.running()) {
            workspaceRestartPending = false
            return
        }
        if (PiRpc.isStreamingNow(timeoutMs = 600) == true) {
            workspaceRestartPending = true
            PientLog.i(TAG_CHAT, "工作区已换，但 pi 正忙：等它收尾后重启通道")
            return
        }
        workspaceRestartPending = false
        val target = piChannelTarget() ?: return
        PiRpc.stop()
        if (PiRpc.start(target.first, target.second)) {
            bindPiSession()   // 新进程要重新绑回本会话的 pi 文件（否则 get_state 落在别的会话上）
            val name = AppCtx.get()?.let { runCatching { PiRuntime.workspaceDir(it).name }.getOrNull() }.orEmpty()
            PientLog.i(TAG_CHAT, "pi 通道已按新工作区重启（工作区=$name）")
        }
    }

    fun selectSession(id: String) {
        currentSessionId = id
        activePanel = Panel.MESSAGES
        liveThinkingIndex = -1   // 换会话：上一次的流式思考块不再享受展开（Hermes 历史态收起）
        onSessionChanged()       // 环跟手切到该会话的用量（先归位，真值随后按它的 pi 文件读回）
    }

    fun newSession(): String {
        val proj = currentProject ?: return "" // 未绑定项目：调用方 Toast 提示
        val list = sessions.getOrPut(proj) { mutableStateListOf() }
        val id = newSessionId()
        list.add(0, Session(id, L.runtime.newSessionTitle, proj, updatedAt = System.currentTimeMillis()))
        currentSessionId = id
        messagesBySession[id] = mutableStateListOf()
        entriesBySession[id] = mutableStateListOf()
        leafBySession[id] = null
        liveThinkingIndex = -1
        activePanel = Panel.MESSAGES
        onSessionChanged()   // 新会话还没有 pi 文件 → 用量判未知、环立刻归零（不沿用上一个会话的数字）
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
        val autoTitle = if (old.title == L.runtime.newSessionTitle && !firstUserText.isNullOrBlank()) {
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
        if (currentSessionId == id) {
            currentSessionId = sessionsFor(proj).firstOrNull()?.id
            onSessionChanged()   // 当前会话被删：环不能继续显示它的数字
        }
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
            onSessionChanged()   // 当前会话被删：环不能继续显示它的数字
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
                        PientLog.i(TAG_CHAT, "删除的正是 pi 当前会话 → 已让 pi 换到新会话")
                    }
                }
                val gone = PiAgentFiles.deleteSessionFile(ctx, f)
                PientLog.i(TAG_CHAT, "清理 pi 侧会话记录：$f → ${if (gone) "已删除" else "文件不存在"}")
            }.onFailure { PientLog.w(TAG_CHAT, "清理 pi 侧会话记录失败：${it.message}") }
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
        return "  ↳ " + (if (failed) L.runtime.mirrorFailed else L.common.done) + (if (head.isNotEmpty()) " · $head" else "")
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
     * **当前会话变了**（新建 / 切换 / 删除 / 分支 / 换项目）：上下文用量**先归位、再取真值**。
     *
     * 环上的数字只属于「当前会话」—— 任何一个换会话入口不收口，环都会沿用上一个会话的数字
     * （2026-09-16 用户实报：新建会话后环仍显示上一个会话的用量，且「刷新」也救不回来）。
     * 归位保证「绝不显示别的会话的数字」；真值交给 [refreshContextUsage]（新会话还没有 pi 文件时
     * 它自己判未知，不会误读进程里停着的那个旧会话）。
     */
    fun onSessionChanged() {
        contextUsageKnown = false
        windowTokens = 0
        contextPercent = 0f
        requestContextUsage()
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
        // **当前会话还没有 pi 侧文件 = 无可读的用量**（Pient 对 pi 会话文件是懒建的：首条消息、
        // 开画布才建）。这时**绝不能去问 pi** —— pi 进程里停着的是上一个会话，`get_session_stats`
        // 回的是**它的**真值，等于把别的会话的用量当成当前会话的（2026-09-16 用户实报：新建会话后
        // 环沿用上一个会话的数字，且"刷新"也救不回来）。本守卫必须先于任何 RPC。
        val file = sessionRecord(currentSessionId ?: "")?.piSessionFile
        if (file.isNullOrBlank()) {
            contextUsageKnown = false
            windowTokens = 0
            contextPercent = 0f
            return
        }
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
        // file 已在函数开头取出且非空（守卫已返回），这里只需判「pi 当前打开的到底是不是它」
        if (data?.optString("sessionFile").orEmpty() != file) {
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

    /**
     * 重命名项目。返回 null = 成功；非空 = 失败原因（调用方直接 Toast 原文）。
     *
     * **本地项目要连磁盘目录一起改名**（2026-09-16 修 bug）：旧实现只改记录里的 name/path ——
     * 目录不动 → 改名后 path 指向一个不存在的目录（文件树变空、Ubuntu workspace 静默回退到随包目录），
     * 而原目录会以「已解绑项目」的身份在本页冒出来（它的名字对不上任何项目的 path）。
     * SAF 项目（content:// tree URI）无法按文件名重建 URI，保持「只改显示名、path 不变」。
     */
    fun renameProject(oldName: String, newNameRaw: String): String? {
        val newName = newNameRaw.trim()
        if (newName.isBlank()) return L.runtime.projectNameEmpty
        if (newName == oldName) return null
        if (projects.any { it.name == newName }) return L.runtime.projectNameExists
        val i = projects.indexOfFirst { it.name == oldName }
        if (i < 0) return L.runtime.projectNotFound
        val old = projects[i]
        val saf = old.path.startsWith("content://")
        var newPath = old.path
        if (!saf) {
            val src = java.io.File(old.path)
            val parent = src.parentFile ?: return L.runtime.projectPathInvalid
            val dst = java.io.File(parent, newName)
            if (src.exists()) {
                if (dst.exists()) return L.runtime.dirNameExists(newName)
                if (!src.renameTo(dst)) return L.runtime.dirRenameFailed
            }
            newPath = dst.absolutePath
        }
        projects[i] = old.copy(name = newName, path = newPath)
        val moved = sessions.remove(oldName) ?: mutableStateListOf()
        for (j in moved.indices) moved[j] = moved[j].copy(project = newName)
        sessions[newName] = moved
        if (currentProject == oldName) {
            currentProject = newName
            // 当前项目改了目录名：工作区指针（$P/workspace）跟着走，否则终端 / pi 还指着旧路径
            syncWorkspace(newName)
        }
        return null
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
            syncWorkspace(currentProject)   // 删的是当前项目：工作区跟着回退到剩下的第一个项目
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
     * 起一轮对话。**跑在 ChatState 自己的 scope 上 —— 不是聊天页的 UI scope**。
     *
     * 2026-09-16 真机实测的修复：发送原来是 `rememberCoroutineScope().launch { streamReply(...) }`，
     * 一旦离开聊天页（返回退出 / 页面被销毁），那个 scope 被取消 → 本轮在应用侧"静默结束"，
     * 但**没有任何代码通知 pi**（全仓唯一会发 `PiRpc.abort()` 的是 [abort]）→ pi 继续跑那一轮、
     * 界面却显示空闲 → 下一条消息被 pi 拒（"Agent is already processing"，用户看到一条英文报错）。
     * PiKeepAlive 的意义本来就是让回合在后台跑完；要停必须走 [abort]（它会通知 pi 并等它收尾）。
     */
    fun startTurn(text: String, quote: Quote? = null) =
        launchTurn { streamReply(text, quote) }

    /**
     * 把一轮任务挂到进程 scope 上跑，并保证**异常不逃逸**：
     * 取消照常上抛（abort 路径要用），其它异常由默认 handler 兜 → 会崩掉应用，所以这里收口成提示。
     */
    private fun launchTurn(block: suspend () -> Unit) {
        streamJob = bgScope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                PientLog.e(TAG_CHAT, "本轮异常逃逸：${e.message}", e)
                blockedNote = L.runtime.turnException(e.message ?: L.common.unknownError)
            }
        }
    }


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
     * 被拦下的发送要把**文本还给输入栏**（值 = 待还文本；ChatScreen 取走后置 null）。
     * [blockedNote] 只能给一句 Toast，而"这条压根没发出去"必须把草稿还回去
     * （输入栏在点击时已自行清空文本）—— 见 [streamReply] 开头「pi 还在处理上一轮」的拦截。
     */
    var draftRestore by mutableStateOf<String?>(null)

    // ── 输入栏：润色提示词（2026-09-16，用户 spec）────────────────────────
    // 交互：点润色键 → 输入框只读、键转圈 → 结果回填输入框、键变回退键 →
    // 再点变回原文、键变回润色键；**一旦用户在润色结果上动过手，回退态即作废**
    // （回退键变回润色键）。润色本身走 pi 的单次模式（见 [com.pient.app.runtime.PiPolish]）。

    /** 正在润色：输入框转只读（润色期间不得改动提示词）、发送键与全屏输入一并禁用 */
    var polishing by mutableStateOf(false)
        private set

    /**
     * 润色前原文（非空 = 输入框当前内容来自润色 → 润色键显示为**回退键**）。
     * 「用户在润色结果上修改」= 聊天页调 [clearPolishRevert]，回退态作废。
     */
    var polishRevertTarget by mutableStateOf<String?>(null)
        private set

    /** 润色结果 / 回退内容的一次性回填信号（ChatScreen 取走后置 null；口径同 [draftRestore]） */
    var polishApply by mutableStateOf<String?>(null)

    /** 用户改动了输入框内容 → 润色态作废（回退键变回润色键） */
    fun clearPolishRevert() {
        if (polishRevertTarget != null) polishRevertTarget = null
    }

    /**
     * 润色输入框里的提示词。跑在**进程 scope** 上：这是一次真实模型调用，
     * 不该随聊天页销毁被静默取消（与一轮对话同口径，见 [startTurn]）。
     */
    fun polishInput(text: String) {
        if (polishing || polishRevertTarget != null) return   // 已在润色 / 当前是回退态：按键不是润色键
        if (text.isBlank()) return
        val target = piChannelTarget()
        if (target == null) {
            blockedNote = L.chat.polishFailed(L.runtime.noProviderOrModel)
            return
        }
        val ctx = AppCtx.get()
        if (ctx == null) {
            blockedNote = L.chat.polishFailed(L.runtime.appContextNotReady)
            return
        }
        polishing = true
        bgScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { PiPolish.polish(ctx, text, target.first, target.second) }
            }
            polishing = false
            result
                .onSuccess { polished ->
                    polishRevertTarget = text   // 回退目标 = 润色前原文
                    polishApply = polished
                }
                .onFailure { e ->
                    blockedNote = L.chat.polishFailed(e.message ?: L.common.unknownError)
                }
        }
    }

    /** 回退到润色前的提示词（回退键的动作）；随后按键回到润色态 */
    fun revertPolish() {
        val original = polishRevertTarget ?: return
        polishRevertTarget = null
        polishApply = original
    }

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
        // 送 pi 的必须是**模型 id**：列表条目可能是 `id=别名`（别名只是 Pient 侧写进 `models[].name` 的
        // 展示/匹配写法），整条 `id=别名` 送过去 pi 的 `--model` 认不出来（2026-09-17 真机实测发现）。
        val model = m.name.substringBefore('=').trim()
            .takeIf { it.isNotBlank() }
            ?: cfg.models.firstOrNull()?.substringBefore('=')?.trim()
            ?: return null
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
            PientLog.i(TAG_CHAT, "pi 就绪态：${result.first}${if (result.second.isBlank()) "" else "（${result.second}）"}")
            if (result.first == PiReadiness.Ready) syncThinkingToPi()
        }
    }

    private suspend fun probePiReadiness(ctx: Context): Pair<PiReadiness, String> {
        val target = piChannelTarget()
        if (target == null) {
            return PiReadiness.Unready to L.runtime.noProviderOrModelHint
        }
        if (!PiRuntime.rootfsReady(ctx)) {
            // 原因文案与「环境配置」页共用一处（rootfsIssue）：避免两处说法不一致（2026-09-15）
            return PiReadiness.Unready to PiRuntime.rootfsIssue(ctx)
        }
        if (!PiRuntime.piReady(ctx)) {
            return PiReadiness.Unready to L.runtime.piNotUnpacked
        }
        if (!PiRpc.start(target.first, target.second)) {
            val tail = PiRpc.stderrText().lines().lastOrNull { it.isNotBlank() }.orEmpty()
            return PiReadiness.Unready to (L.runtime.piChannelStartFailed + if (tail.isBlank()) "" else "：$tail")
        }
        // 起得来 ≠ 活着：进程秒退（rootfs 不可执行 / proot 报错）时 start() 仍返回 true（2026-09-15 实测）
        if (!PiRpc.aliveAfterStartup()) {
            val tail = PiRpc.stderrText().lines().lastOrNull { it.isNotBlank() }.orEmpty()
            PiRpc.stop()
            return PiReadiness.Unready to (L.runtime.piChannelExitedImmediately + if (tail.isBlank()) "" else "：$tail")
        }
        // 再要一次真实往返（RPC 通了才算真的可用；失败 = 管道/进程有问题）
        if (PiRpc.getState() == null) {
            val tail = PiRpc.stderrText().lines().lastOrNull { it.isNotBlank() }.orEmpty()
            PiRpc.stop()
            return PiReadiness.Unready to (L.runtime.piChannelUnresponsive + if (tail.isBlank()) "" else "：$tail")
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
            blockedNote = L.runtime.piNotReadyNotSent
            PientLog.w(TAG_CHAT, "发送被阻断：pi 未就绪（$piUnreadyReason）")
            return
        }
        // **pi 侧还在跑就别硬发**（2026-09-16 真机实测）：pi 对「流式中且未指定 streamingBehavior」的
        // prompt 会**直接拒绝**（docs/rpc.md:56-65；抛错点 core/agent-session.ts:1213），硬发的代价 =
        // 一条废用户消息 + 一句用户看不懂的英文报错。可能撞上的窗口：上一轮在别处被留下（旧版 UI scope
        // 取消的遗留）、刚中止还没收尾、画布/终端那边正在跑。这里不落任何条目，把文本还给输入栏。
        // 工作区换过但当时 pi 正忙：趁现在（它空闲）把通道重启到新目录，再发本轮
        if (workspaceRestartPending) applyPendingWorkspaceRestart()
        if (PiRpc.isStreamingNow() == true) {
            draftRestore = userText
            blockedNote = L.runtime.blockedPiBusy
            PientLog.w(TAG_CHAT, "发送被拦：pi 仍在处理上一轮（isStreaming=true）")
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
                    L.runtime.noModelConfiguredDetail,
                    error = true,
                )
            )
            isStreaming = false
            streamDraft = ""
            markRunning(false)
            return
        }
        // 所选模型可能不在配置的模型列表内（列表被改）→ 取配置列表首个。
        // 比对按 **id**（列表条目可能是 `id=别名`；不剥掉别名会把「同一条」判成「不在列表里」，
        // 于是逐模型的窗口 / 识图落到第一个模型上 —— 见 settingOf / MediaInline）。
        val effectiveModel = model.name.substringBefore('=').trim()
            .takeIf { id -> cfg.models.any { it.substringBefore('=').trim() == id } }
            ?: cfg.models.firstOrNull()?.substringBefore('=')?.trim().orEmpty()

        // 引用消息：正文以 markdown 块引用注入（Quote.toPrompt；UI 仍只显示用户正文）
        // 本轮用户回合的请求文本：附件以「名称 · 路径」附在正文后（本条是最新回合，媒体恒保留）；
        // 这段文本就是交给 pi 的那条用户消息 —— 上下文由 pi 维护，App 不再拼历史。
        // 附件直发（2026-09-14 照 Operit 的三个媒体开关）：开启的类别把文件本体转成内容部件随请求发出，
        // 已直发的附件不再重复列路径（Operit 的「移除链接」）；关闭的类别**不拦消息**，
        // 只追加一行 Operit 原文占位（「图片内容已省略，当前模型不支持图片处理」）。
        val appCtx = AppCtx.get()
        val inline = if (appCtx != null) {
            MediaInline.parts(appCtx, currentMsg.attachments, cfg, effectiveModel)
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
                quote = quote,
                attachments = currentMsg.attachments,
                onDelta = { draft -> streamDraft = draft },
                onThinking = { noteThinkingDelta(it) },
                media = inline.parts,
            )
            // 思考已按阶段落库（runChatViaPi 的 flushThinking），这里只落回答
            appendEntry(Msg.Assistant(outcome.text, outcome.usage, effectiveModel))
            // 用量台账（用量页数据源）：完成即记一笔（usage 为空 = 服务商未返回用量，不记）
            UsageStore.record(cfg.providerId, effectiveModel, outcome.usage)
            // 消息通知（2026-09-16 行为设置）：应用不在前台时，给这条回复发一条系统通知
            ReplyNotify.notifyReply(AppCtx.get(), currentSession?.title, outcome.text)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // abort：保留 abort() 对 draft 的处理
        } catch (e: Exception) {
            appendEntry(
                Msg.Assistant(
                    L.runtime.requestFailed(e.message ?: L.common.unknownError),
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
        /** 本轮的引用块与附件清单（写进 pi 会话的 `pient_meta` 标记；见 [writeMsgMeta]） */
        quote: Quote? = null,
        attachments: List<Attachment> = emptyList(),
    ): ChatOutcome {
        val target = piChannelTarget()
        if (target != null && PiRpc.usable() && PiRpc.start(target.first, target.second)) {
            bindPiSession()          // 会话映射：懒建 / 切到本会话对应的 pi 会话文件（失败不阻断本轮）
            // **发送前把思考档位推给 pi 并等它落地**：通道可能刚重启（pi 的档位回到默认 medium），
            // 不 await 的话本轮会跑在旧档位上（异步推送与 prompt 赛跑，2026-09-17 实测）。
            syncThinkingToPiNow()
            if (piReadiness != PiReadiness.Ready) {
                piReadiness = PiReadiness.Ready
                piUnreadyReason = ""
            }
            return runChatViaPi(cfg, userTurnText, media, onDelta, onThinking, quote, attachments)
        }
        // ── pi 起不来：**不换路**（2026-09-15 拍板 B + 收口）────────────────────────
        // 直连没有任何工具能力，落回去会让用户以为自己在用 agent；§8.3 作废后本地也不存在
        // 「只在本地的新消息」这种状态 —— 所以只有一条路：如实报错，让用户去修环境。
        PientLog.w(TAG_CHAT, "pi 通道不可用，本轮没走 pi：${PiRpc.stderrText().takeLast(300)}")
        piReadiness = PiReadiness.Unready
        if (piUnreadyReason.isBlank()) piUnreadyReason = L.runtime.piChannelStartFailedHint
        throw AiException(L.runtime.piNotReadyTurnNotSent)
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
                    PientLog.i(TAG_CHAT, "会话已映射到 pi 文件：$newFile")
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
                PientLog.i(TAG_CHAT, "会话标题按 pi 回流：$piName")
            }
            refreshPiTree()
            syncMessagesFromPi()
            // 位置对账（T1/T2/T3）：通道重启、切会话后 pi 的叶会回到文件末尾 —— 拉回 Pient 记的位置
            reconcilePiLeaf(id)
        }.onFailure { PientLog.w(TAG_CHAT, "绑 pi 会话失败：${it.message}") }
        // 思考档位同步（2026-09-17）：通道就绪 / 换会话后把界面的开关与档位推给 pi（不等 = no-op）
        syncThinkingToPi()
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
            PientLog.w(TAG_CHAT, "位置对账：目标条目 $want 不在当前树里 → 丢弃该期望位置")
            piDesiredLeaf.remove(sid)
            return
        }
        val have = normalizedPiLeaf()
        if (have == want) return
        PientLog.i(TAG_CHAT, "位置对账：pi 叶 $have ≠ 目标 $want → /pient-nav")
        runCatching { PiRpc.navigate(want, summarize = false) }
            .onFailure { PientLog.w(TAG_CHAT, "位置对账失败：${it.message}") }
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
            PientLog.i(TAG_CHAT, "本轮在飞：跳过按 pi 重建消息流（保住刚上屏的用户消息）")
            return
        }
        val arr = PiRpc.getMessages()?.optJSONArray("messages") ?: return
        val rebuilt = ArrayList<Msg>(arr.length())
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            // 还原口径 = **按内容块顺序**（thinking / text / toolCall 各自在原位置），与画布「节点详情」
            // 用的 pushPiMessage 同一实现（2026-09-16）：旧写法固定「正文 → 再工具行」且整块丢掉思考，
            // 每轮回读后聊天页的顺序就和节点详情卡对不上。
            pushPiMessage(m, rebuilt)
        }
        // 引用卡 / 附件还原（2026-09-17）：pi 侧那条用户消息的文本里含着引用块与附件清单，
        // 这里按文本对位还原成结构（正文干净、引用回 quote、附件回 attachments）。
        // 不还原的话「开画布 / 切分支 / fork / 中止 / 压缩」之后气泡里会直接冒出 "> …" 与
        // "[附件] 名称 · 路径" 字面行，引用卡与附件 chip 也再也回不来。
        val restoredMsgs = piUserMsgs(localAttachByText(id))
        if (restoredMsgs.isNotEmpty()) {
            var restored = 0
            for (i in rebuilt.indices) {
                val u = rebuilt[i] as? Msg.User ?: continue
                val r = restoredMsgs[u.text.trim()] ?: continue
                rebuilt[i] = r
                restored++
            }
            if (restored > 0) PientLog.i(TAG_CHAT, "按 pi 重建后还原用户消息（引用/附件）：$restored 条")
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
            // 只记「条数 + 长度」，不记正文（2026-09-17 用户拍板的隐私口径：日志里不留用户内容）
            PientLog.i(TAG_CHAT, "重建后补回未被 pi 记录的用户消息：1 条（${pending.text.length} 字）")
        }
        // 位置**不进本地条目树**（2026-09-15）：pi 的条目 id 与本地镜像 id 不同源，写进来会让
        // leafPath 落空、会话在界面上变空白（实测踩过）。pi 侧位置由 [piDesiredLeaf] 单独记。
        PientLog.i(TAG_CHAT, "消息流已按 pi 上下文重建：${rebuilt.size} 条")
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
        /** 用户条目的还原形态（正文剥离引用块 / 附件清单，引用与附件进各自字段）；无可还原项返回 null */
        val localAttach = localAttachByText(currentSessionId)
        fun userMsgOf(id: String): Msg.User? =
            if (!isUserTurn(id)) null
            else piUserMsg(id, entryById[id]?.optJSONObject("message"), entryById, parentOf, localAttach)

        fun textOf(id: String): String {
            val raw = piText(entryById[id]?.optJSONObject("message"))
            if (!isUserTurn(id)) return raw
            // 画布节点预览显示**用户原话**（引用与附件分别由详情卡 / 卡片图标呈现，2026-09-17）
            return userMsgOf(id)?.text ?: raw
        }
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
                attachments = userMsgOf(id)?.attachments.orEmpty(),
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
     * **会话外分支：从某条上屏消息创建新会话（pi 原生实现，2026-09-17）**。
     *
     * 口径 = 《Pient 分支功能设计》§4.2「含 fork 点」：新会话包含这条消息**及其回答**。
     * pi 自己的 `fork(entryId)` 是「这条消息**之前**」语义（`agent-session-runtime.ts:262-287`，
     * position 默认 before、且要求目标是用户消息），做不到含 fork 点 —— 所以走两步原生命令：
     * ① `/pient-nav <锚点>` 把活跃叶移到该消息**回合末尾**（锚点 = 该回合最后一个非用户条目；
     *    尚无回答的节点由扩展补一条锚点标记）；② pi 官方 `clone`（= `fork(leafId, { position: "at" })`）
     * 以当前叶为准 fork 出新会话文件。**两条都是 pi 原生能力，应用侧不拼任何上下文。**
     *
     * 长按 AI 回答时落点 = 该回答所属回合的末尾（正常情况就是那条回答本身）；回合中途的旁白
     * 不单独切 —— 切在半截会让新会话以「toolCall 没有结果」收尾，下一轮请求会被上游拒。
     */
    fun forkFromMessage(messageIndex: Int) {
        val proj = currentProject ?: return
        val sid = currentSessionId ?: return
        val rec = sessionRecord(sid) ?: return
        if (rec.piSessionFile.isNullOrBlank() || !PiRpc.usable()) {
            blockedNote = L.runtime.piNotReadyNotSent
            return
        }
        if (isStreaming || currentSession?.running == true) {
            blockedNote = L.runtime.blockedPiBusy
            return
        }
        val msgs = currentMessages.toList()
        if (messageIndex !in msgs.indices) return
        // 目标用户消息 = 本条（用户消息）或往前最近的一条（AI 回答走它所属的回合）
        val userIdx = if (msgs[messageIndex] is Msg.User) messageIndex
        else (messageIndex downTo 0).firstOrNull { msgs[it] is Msg.User } ?: -1
        val title = L.runtime.branchTitlePrefix + forkTitle(msgs, messageIndex)  // 「（分支）」前缀：与源会话区分（用户 2026-09-17 定）
        bgScope.launch {
            try {
                refreshPiTree()                                  // 目标在新鲜的树上解析（锚点表一并刷新）
                val nodeId = piNodeIdForUserIndex(userIdx)
                if (nodeId == null) {
                    blockedNote = L.chat.forkUnavailable
                    PientLog.w(TAG_CHAT, "会话外分支：消息 #$messageIndex 在 pi 树里定位不到，已放弃")
                    return@launch
                }
                PiRpc.navigate(piAnchorOf[nodeId] ?: nodeId)     // 活跃叶 → 该回合末尾（含这条消息及其回答）
                PiRpc.clone()                                    // pi 原生：以当前叶为准 fork 出新会话文件
                val file = PiRpc.getSessionStats()?.optString("sessionFile").orEmpty()
                if (file.isBlank()) {
                    blockedNote = L.chat.forkUnavailable
                    return@launch
                }
                PiRpc.setSessionName(title)                      // 标题去程：pi 的 sessionName 是真相源
                val newId = newSessionId()
                sessions.getOrPut(proj) { mutableStateListOf() }
                    .add(0, Session(newId, title, proj, updatedAt = System.currentTimeMillis(), piSessionFile = file))
                messagesBySession[newId] = mutableStateListOf()
                entriesBySession[newId] = mutableStateListOf()
                leafBySession[newId] = null
                currentSessionId = newId
                onSessionChanged()   // 新分支会话：环归位，真值随后按它的 pi 文件读回
                refreshPiTree()
                syncMessagesFromPi()
                AppCtx.get()?.let { ChatStore.save(it, this@ChatState) }
                blockedNote = L.chat.sessionCreated
                PientLog.i(TAG_CHAT, "会话外分支已建（pi 原生 navigate+clone，含 fork 点）：节点 $nodeId → $file")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                blockedNote = L.chat.forkUnavailable
                PientLog.w(TAG_CHAT, "会话外分支失败：${e.message}")
            }
        }
    }

    /** pi 树里 root→leaf 的节点链（piParseTree 按「到叶的路径」标 active） */
    private fun piActivePathNodes(): List<SessionTreeNode> {
        val out = ArrayList<SessionTreeNode>()
        var node: SessionTreeNode? = piTree ?: return out
        while (node != null) {
            out += node
            node = node.children.firstOrNull { it.active }
        }
        return out
    }

    /**
     * 上屏的用户消息 → pi 树节点 id（= pi 的用户消息条目 id；找不到返回 null）。
     *
     * ① 主路：把上屏用户消息与活跃路径节点**从末尾配对**（两侧都是同一条会话的用户消息，
     *    从末尾数不受「压缩把老消息移出上下文」影响），配上的再拿文本校验；
     * ② 兜底：按文本在活跃路径上找（双向包含 —— 注入过引用/附件的文本是显示文本的超集），
     *    多个命中取最靠近末尾的一个。
     * 都没有就如实返回 null（调用方给一句提示，别猜一个 id 去导航）。
     */
    private fun piNodeIdForUserIndex(userIdx: Int): String? {
        val msgs = currentMessages
        if (userIdx !in msgs.indices) return null
        val text = (msgs[userIdx] as? Msg.User)?.text ?: return null
        val path = piActivePathNodes()
        if (path.isEmpty()) return null
        val users = msgs.withIndex().filter { it.value is Msg.User }
        val kFromEnd = users.size - users.indexOfFirst { it.index == userIdx }   // 1 = 末尾那条
        path.getOrNull(path.size - kFromEnd)?.let { if (piTextSame(it.userText, text)) return it.id }
        return path.lastOrNull { piTextSame(it.userText, text) }?.id
    }

    /** 用户消息同一性判定：完全相等，或一方包含另一方（pi 侧文本可能带引用/附件注入） */
    private fun piTextSame(a: String, b: String): Boolean {
        val x = a.trim()
        val y = b.trim()
        if (x.isEmpty() || y.isEmpty()) return false
        return x == y || x.contains(y) || y.contains(x)
    }

    // ── 用户消息元数据标记（pi `custom` 条目，2026-09-17）────────────────────────
    // 引用卡与附件清单不能只活在本地镜像里：pi 的会话文件里只留得下**文本**，而
    //   · 引用 = 正文开头的 "> …" 块引用（结构由 pient_meta 标记兜底）；
    //   · 附件 = 正文尾部的 "[附件] 名称 · 路径" 清单 —— **直发时这行按「移除链接」口径被去掉**，
    //     而 `image` 内容块只有 data/mimeType（协议里没有文件名字段）→ pi 侧完全没有名字。
    // 所以发送前把「引用 + 附件清单（含文件名/类型/路径）」作为一条 `pient_meta` custom 条目
    // 写进会话（`session-format.md` 明写不进 LLM 上下文、不进画布），读侧按「user 条目的
    // parent 是不是它」还原 —— 跨重建、重装、换端都在。

    /**
     * 把「引用 + 附件清单」写进 pi 会话（扩展命令 `/pient-meta <base64(JSON)>` → `pi.appendEntry`）。
     * 为什么走扩展命令：pi 的 RPC 没有「追加自定义条目」这条命令，官方扩展 API 有
     * （锚点标记 `pient_anchor` 走的就是同一条路）。**必须在 prompt 之前发** ——
     * 标记写在当前叶之下，随后追加的用户消息才成为它的子条目。
     */
    private suspend fun writeMsgMeta(quote: Quote?, attachments: List<Attachment>) {
        if (quote == null && attachments.isEmpty()) return
        val payload = JSONObject()
        quote?.let { payload.put("quote", JSONObject().put("text", it.text).put("role", it.role)) }
        if (attachments.isNotEmpty()) {
            payload.put("attachments", JSONArray().apply {
                attachments.forEach { a ->
                    put(
                        JSONObject()
                            .put("name", a.name)
                            .put("kind", a.kind.name)
                            .put("path", a.path ?: JSONObject.NULL),
                    )
                }
            })
        }
        val b64 = android.util.Base64.encodeToString(
            payload.toString().toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP,
        )
        val res = runCatching { PiRpc.prompt("/pient-meta $b64") }.getOrNull()
        if (res == null || !res.optBoolean("success", true)) {
            PientLog.w(TAG_CHAT, "消息元数据未写入 pi（本轮照发）：${res?.optString("error").orEmpty()}")
        } else {
            PientLog.i(TAG_CHAT, "消息元数据已写入 pi 会话（引用=${quote != null} 附件=${attachments.size} 个）")
        }
    }

    /** pi 条目的元数据标记（`pient_meta`；旧版 `pient_quote` 只带引用） */
    private data class PiMeta(val quote: Quote?, val attachments: List<Attachment>)

    /**
     * pi 条目 → 它挂着的元数据标记（parent 是 `custom/(pient_meta|pient_quote)` 时取它的 data）。
     *
     * `entries` / `parents` 默认取实例上那份（`refreshPiTree` 后的全局状态）；**画布建树时必须显式传
     * `piParseTree` 自己的局部表** —— 那一刻实例字段还是上一棵树（首次刷新时是空的），
     * 用它会让画布节点预览退回 "> …" 原文（2026-09-17 实测踩过）。
     */
    private fun piMetaOf(
        entryId: String,
        entries: Map<String, JSONObject> = piEntryById,
        parents: Map<String, String?> = piParentOf,
    ): PiMeta? {
        val pid = parents[entryId] ?: return null
        val parent = entries[pid] ?: return null
        if (parent.optString("type") != "custom") return null
        val ct = parent.optString("customType")
        if (ct != PI_META_TYPE && ct != PI_QUOTE_TYPE) return null
        val d = parent.optJSONObject("data") ?: return null
        val qo = d.optJSONObject("quote") ?: d            // 新形态 data.quote / 旧形态直接挂 data
        val qText = qo.optString("text").trim()
        val quote = qText.takeIf { it.isNotEmpty() }?.let {
            Quote(it, if (qo.optString("role") == "assistant") "assistant" else "user")
        }
        val atts = ArrayList<Attachment>()
        d.optJSONArray("attachments")?.let { arr ->
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                val name = a.optString("name")
                val kind = runCatching { AttachmentKind.valueOf(a.optString("kind", "FILE")) }
                    .getOrDefault(AttachmentKind.FILE)
                val path = a.optString("path").takeIf { it.isNotBlank() }
                if (name.isNotBlank() || path != null) atts += Attachment(name, kind, path)
            }
        }
        if (quote == null && atts.isEmpty()) return null
        return PiMeta(quote, atts)
    }

    /** 本地镜像里「用户消息文本 → 附件」（重建时把 pi 侧认不出的附件名补回来；条目树与消息流都看） */
    private fun localAttachByText(sid: String?): Map<String, List<Attachment>> {
        if (sid == null) return emptyMap()
        val out = HashMap<String, List<Attachment>>()
        entriesBySession[sid]?.forEach { e ->                    // 条目树：持久化的那份
            val u = e.msg as? Msg.User ?: return@forEach
            if (u.attachments.isNotEmpty()) out[u.text.trim()] = u.attachments
        }
        messagesBySession[sid]?.forEach { m ->                   // 上屏流：当前正在显示的那份
            val u = m as? Msg.User ?: return@forEach
            if (u.attachments.isNotEmpty()) out[u.text.trim()] = u.attachments
        }
        return out
    }

    /**
     * pi 用户条目 → 上屏用的 [Msg.User]（正文剥离元数据、附件与引用挂回各自字段）。
     * 返回 null = 这条条目没有可还原的东西（纯文本、无标记、无附件），调用方按 piText 原样用。
     *
     * 附件名的三级来源（前面的优先）：① 标记里的清单（pi 会话文件里那份，最权威）；
     * ② 本地镜像里同文本那条（老消息没有标记时的兜底）；③ pi 侧推断（清单行 / image 块，
     * 直发图片在这一级**没有名字**）。
     */
    private fun piUserMsg(
        entryId: String,
        msg: JSONObject?,
        entries: Map<String, JSONObject> = piEntryById,
        parents: Map<String, String?> = piParentOf,
        localAttach: Map<String, List<Attachment>> = emptyMap(),
    ): Msg.User? {
        val raw = piText(msg).trim()
        if (raw.isEmpty()) return null
        var body = raw
        val meta = piMetaOf(entryId, entries, parents)
        var quote = meta?.quote
        if (quote != null) {
            val split = Quote.splitInjected(body)
            if (split != null && split.first == quote.text.trim()) body = split.second
            else quote = null      // 文本形态对不上 = 孤儿标记（写了标记但本轮没发出去）→ 不认
        }
        val (text, listed) = ContextPolicy.splitAttachments(body)
        var attachments = listed + imageAttachments(msg)
        when {
            meta?.attachments?.isNotEmpty() == true -> attachments = meta.attachments
            else -> {
                val local = localAttach[text.trim()]
                if (local != null && local.size == attachments.size && attachments.any { it.name.isBlank() }) {
                    attachments = local
                }
            }
        }
        if (quote == null && attachments.isEmpty()) return null
        return Msg.User(text.ifBlank { body }, attachments, quote)
    }

    /** pi 消息 content 里的 image 块 → 附件（直发的图片不写清单行、名字不留痕，只能按类型兜底） */
    private fun imageAttachments(msg: JSONObject?): List<Attachment> {
        val arr = msg?.optJSONArray("content") ?: return emptyList()
        val out = ArrayList<Attachment>()
        for (i in 0 until arr.length()) {
            val b = arr.optJSONObject(i) ?: continue
            if (b.optString("type") == "image") out += Attachment("", AttachmentKind.IMAGE, null)
        }
        return out
    }

    /** 重建上屏流时按「pi 条目文本」对位还原用户消息（`get_messages` 不带条目 id，只能按文本认） */
    private fun piUserMsgs(localAttach: Map<String, List<Attachment>> = emptyMap()): Map<String, Msg.User> {
        val out = HashMap<String, Msg.User>()
        for ((id, e) in piEntryById) {
            if (!isPiUserNode(e)) continue
            val msg = e.optJSONObject("message")
            val raw = piText(msg).trim()
            if (raw.isEmpty()) continue
            piUserMsg(id, msg, piEntryById, piParentOf, localAttach)?.let { out[raw] = it }
        }
        return out
    }


    /**
     * **走 pi 通道跑一轮**（2026-09-14）：只送最后一条用户消息 —— pi 在同一条 RPC 会话里
     * 自己累积上下文（含工具结果），这也是官方客户端（pi-web / SDK）的口径。
     *
     * 流式：`message_update.assistantMessageEvent` 的 `text_delta` / `thinking_delta`；
     * usage：事件顶层的累积值（provider 不上报时为 0，回合结束以 `message_end` 为准不动）；
     * 回合结束判据：**`agent_settled`**（pi 口径：重试、压缩重试、排队续写都settled了才算完）。
     *
     * 条目按 pi 的内容块顺序**按阶段落库**（2026-09-16）：pi 的一次模型调用 = `{thinking, text, toolCall[]}`，
     * 工具调用与回合收尾前各 flush 一次（[flushThinking] / [flushText]），最后一段正文 = 本回合最终回答。
     */
    private suspend fun runChatViaPi(
        cfg: ProviderConfig,
        userTurnText: String,
        media: List<WirePart>,
        onDelta: (String) -> Unit,
        onThinking: (String) -> Unit,
        quote: Quote? = null,
        attachments: List<Attachment> = emptyList(),
    ): ChatOutcome = coroutineScope {
        val userText = userTurnText
        // 附件直发：pi 的 `prompt` 支持 images（ImageContent = {type,data,mimeType}，见 pi docs/rpc.md）。
        // media 里已经是 base64 的图片部件（[MediaInline] 按「模型支持识图」与上限筛过）——
        // pi 的 ImageContent 只有图片这一种，音频 / 视频不进这里（它们按普通文件走：正文里仍带
        // 「名称 · 路径」，AI 可以用自己的工具去读），所以也不会再出现「未直发」提示。
        val piImages = media.filter { it.type == "image" }.map { part ->
            JSONObject()
                .put("type", "image")
                .put("data", part.base64)
                .put("mimeType", part.mime.ifBlank { "image/png" })
        }
        val promptText = userText
        val text = StringBuilder()           // 本轮全部正文（通道存活/错误判定用；条目按阶段落库，见 flushText）
        val textPhase = StringBuilder()      // **当前助手消息**的正文：工具调用前落成一条 Msg.Assistant
        val think = StringBuilder()          // 本轮全部思考（ChatOutcome 用；条目按阶段落库，见 flushThinking）
        val thinkPhase = StringBuilder()     // **当前阶段**的思考：工具调用/回合收尾时落成一条 Msg.Thinking
        var usage: Usage? = null
        val settled = CompletableDeferred<Unit>()
        lastPiError = null
        // pi 的工具事件 → 消息区（这两类消息的 UI 一直都在：ToolRows 渲染 ToolCall + 紧跟的 ToolResult）
        val toolCallAt = HashMap<String, Int>()   // toolCallId → ToolCall 消息下标
        val toolResAt = HashMap<String, Int>()    // toolCallId → ToolResult 消息下标
        /**
         * 把**当前阶段**的思考落成一条 [Msg.Thinking]（2026-09-16 起按阶段落库）。
         *
         * 为什么按阶段：一次模型调用 = 一段思考 + 它随后的工具调用（pi 的条目顺序也正是
         * `assistant{thinking,toolCall}` → `toolResult` → `assistant{thinking,…}`）。旧实现把整轮思考
         * 攒到回合末尾才落库，条目顺序就变成「工具行… → 思考 → 回答」；节点详情卡按 pi 的顺序渲染，
         * 聊天页又把它并进回答卡 → 两处观感都对不上真实流程。
         */
        fun flushThinking() {
            val t = thinkPhase.toString().trim()
            thinkPhase.setLength(0)
            streamThinking = ""
            if (t.isEmpty()) {
                streamThinkingStartedAt = 0L
                return
            }
            val started = streamThinkingStartedAt
            val duration = if (started > 0L) System.currentTimeMillis() - started else null
            val level = if (thinkingEnabled) thinkingLevel else "off"
            // 上屏下标 = live preview latch：刚落下的这块保持展开，历史载入的一律收起
            liveThinkingIndex = appendEntry(Msg.Thinking(level, t, duration))
            streamThinkingStartedAt = 0L
        }

        /**
         * 把**当前助手消息**的正文落成一条 [Msg.Assistant]（2026-09-16 起与思考同样按阶段落库）。
         *
         * 为什么按阶段：pi 的一条 assistant 消息 = `{thinking, text, toolCall[]}` —— 正文排在它自己的
         * 工具调用**之前**。旧实现把整轮（一个回合可能跨 4~5 次模型调用）的正文累加进同一个
         * StringBuilder、直到回合末尾才落库 → 聊天页里那唯一一条回答把几段正文**粘成一坨**（连分隔符
         * 都没有）摆到回合末尾，观感就是"工具前的旁白被搬到了工具下面"；画布「节点详情」按 pi 的内容块
         * 顺序渲染，两处自然对不上（用户实报：正文要跟它那次工具调用在一起，与节点详情同序）。
         *
         * 落库位置沿用 [appendEntry]：一轮的正文一律按顺序追加到末尾。
         * 无正文不落条目（pi 里也只有真产出正文的消息才有 text 块）；不带模型标签与 usage —— 那两样
         * 只挂在回合末尾那条最终回答上（保持"一轮一个标签 + 一行用量"的观感）。
         */
        fun flushText() {
            val t = textPhase.toString().trim()
            textPhase.setLength(0)
            streamDraft = ""
            if (t.isEmpty()) return
            appendEntry(Msg.Assistant(t, null, null))
        }

        val collector = launch {
            PiRpc.events.collect { ev ->
                val evType = ev.optString("type")
                // 打点：非流式事件都记一行（message_update 太多不记）——排查"工具事件有没有到收集器"
                if (evType != "message_update") PientLog.i(TAG_CHAT, "pi 事件：$evType")
                when (evType) {
                    "message_update" -> {
                        ev.optJSONObject("usage")?.let { u -> piUsage(u)?.let { usage = it } }
                        val d = ev.optJSONObject("assistantMessageEvent") ?: return@collect
                        when (d.optString("type")) {
                            "text_delta" -> {
                                val delta = d.optString("delta")
                                text.append(delta)
                                // 上屏给的是**当前这条助手消息**的正文（不是整轮累加值）：一次工具调用
                                // 到来就把这段正文落成自己的条目，下一段正文从零开始（见 flushText）
                                textPhase.append(delta)
                                onDelta(textPhase.toString())
                            }
                            "thinking_delta" -> if (thinkingEnabled) {
                                val delta = d.optString("delta")
                                think.append(delta)
                                thinkPhase.append(delta)
                                onThinking(thinkPhase.toString())
                            }
                        }
                    }
                    "tool_execution_start" -> {
                        // 这段思考与正文都发生在这次工具调用**之前** → 先落库，
                        // 条目顺序才是 思考 → 正文 → 工具（pi 的一条 assistant 消息就是这个形状）
                        flushThinking()
                        flushText()
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
                        // 通知卡片明细（2026-09-16）：工具调用数 +1，顺手刷一次卡片
                        toolCallsThisTurn += 1
                        PiKeepAlive.detailChanged(AppCtx.get())
                        PientLog.i(TAG_CHAT, "pi 工具开始：$name")
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
                        PientLog.i(TAG_CHAT, "pi 工具结束：$name 失败=$failed")
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
        // 引用与附件清单先写进 pi 会话（`pient_meta` custom 条目，parent = 当前叶 → 紧随其后的
        // 用户消息成为它的子条目）：按 pi 重建上屏流时靠它把引用卡与**附件名**挂回去（2026-09-17）。
        // 失败只记一行日志 —— 正文里那份 "> …" 注入本来就是模型侧的兜底，标记只服务 UI 还原。
        if (quote != null || attachments.isNotEmpty()) writeMsgMeta(quote, attachments)
        val res = PiRpc.prompt(promptText, piImages)
        if (res != null && !res.optBoolean("success", true)) {
            collector.cancel()
            throw AiException(piErrorText(res.optString("error")))
        }
        val ok = withTimeoutOrNull(600_000) { settled.await() } != null
        collector.cancel()
        // 收尾：最后一段思考落在回答之前（没有工具时就是「思考 → 回答」这一条链）。
        // 最后一段**正文**不在这里落库 —— 它就是本回合的最终回答，由调用方带 usage/模型标签落。
        flushThinking()
        // 通道中途断开（channel_closed）且本轮没拿到任何文本 → 如实报错，
        // 别落一条空回答让用户以为"AI 回了但看不到内容"（2026-09-15 实测：pi 秒退时就这样）
        if (!PiRpc.processAlive() && text.isBlank() && think.isBlank()) {
            val tail = PiRpc.stderrText().lines().lastOrNull { it.isNotBlank() }.orEmpty()
            throw AiException(L.runtime.piChannelDisconnected + if (tail.isBlank()) "" else "：$tail")
        }
        if (!ok) {
            bgScope.launch { PiRpc.abort() }
            throw AiException(L.runtime.piChannelTimeout)
        }
        lastPiError?.let { err ->
            lastPiError = null
            if (text.isEmpty()) throw AiException(err)
        }
        usage?.let { updateContextPercent(it, cfg) }
        // 最终回答 = 最后一条助手消息的正文（pi 同口径）；此前各阶段的正文已各自落成条目
        val out = textPhase.toString().trim()
        if (out.isNotEmpty()) onDelta(out)
        // 期望位置跟随后端（§4.3）：本轮追加后 pi 的叶就是新的"当前所在"（不跟则下次绑定会拉回旧位置）
        runCatching { refreshPiTree(follow = true) }
            .onFailure { PientLog.w(TAG_CHAT, "回合结束刷新 pi 树失败：${it.message}") }
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
        if (text.length <= 800) text else text.take(800) + L.runtime.toolPreviewTruncated(text.length)

    /**
     * pi 的报错 → 面向用户的文案。pi 的错误串是给调用方（开发者）看的英文，直接上屏用户读不懂；
     * 已知的按语义翻译，**其余原样透出**（不编造、不静默）。
     */
    private fun piErrorText(raw: String): String = when {
        raw.isBlank() -> L.runtime.piRejectedRequest
        raw.contains("Agent is already processing", ignoreCase = true) ->
            L.runtime.blockedPiBusy
        else -> raw
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
     * 上下文占用百分比：本轮用量（输入 + 缓存读/写 + 输出）÷ 该模型的上下文长度。
     * 数据源 = 服务商返回的 usage 真值；长度缺失/为 0 时不改。
     *
     * 窗口**逐模型**（pi `models[].contextWindow`）：取当前选用模型的生效值，
     * 不是服务商级那一个（多模型窗口不同时，环要按自己那个模型的窗口算）。
     */
    private fun updateContextPercent(usage: Usage, cfg: ProviderConfig) {
        // 窗口**逐模型**（pi `models[].contextWindow`）：取当前选用模型的生效值，不是服务商级那一个。
        // 注意用 `name`（= 模型列表里的条目原文）而不是 `id`：`AiModel.id` 是 `provider/条目` 形态，
        // 拿去查 modelSettings 会查不到（`mock-model=快速` 的键是 `mock-model`，见 settingOf）。
        val limitK = cfg.settingOf(selectedModel?.name.orEmpty()).ctxLenK.trim().toIntOrNull() ?: return
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
        val target = piChannelTarget() ?: return L.runtime.noProviderOrModel
        if (!PiRpc.usable() || !PiRpc.start(target.first, target.second)) {
            return L.runtime.piChannelNotReady
        }
        if (compacting) return L.runtime.compactInProgress
        compacting = true
        return try {
            val cfg = selectedModel?.provider?.let { AiConfigStore.configs[it] }
            val res = PiRpc.compact(cfg?.compactInstructions?.ifBlank { null })
                ?: return L.runtime.compactNoResponse
            if (!res.optBoolean("success", true)) {
                return L.runtime.compactFailedPrefix + res.optString("error").ifBlank { L.runtime.piRejectedCompact }
            }
            // 压缩后上下文换了形态（pi 重建了活跃消息）→ 画布与上屏流都要跟上
            runCatching { refreshPiTree(follow = true) }
            runCatching { syncMessagesFromPi() }
            PientLog.i(TAG_CHAT, "上下文已压缩（pi 原生 compact）：${res.toString().take(200)}")
            null
        } catch (e: Exception) {
            L.runtime.compactFailedPrefix + (e.message ?: L.runtime.unknownError)
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
        val ctx = AppCtx.get() ?: return L.runtime.appContextNotReady
        val target = piChannelTarget() ?: return L.runtime.noProviderOrModel
        if (!PiRpc.usable() || !PiRpc.start(target.first, target.second)) {
            return L.runtime.piChannelNotReadyHint
        }
        val file = File(PiAgentFiles.agentDir(ctx), SYS_PROMPT_FILE)
        val before = file.lastModified()
        runCatching { PiRpc.prompt("/pient-sysprompt") }
            .onFailure { return L.runtime.systemPromptFetchFailed(it.message) }
        // 命令处理里同步写文件，但响应与落盘之间可能有几十毫秒 → 轮询等它变
        var waited = 0
        while (waited < 4000 && file.lastModified() <= before) {
            kotlinx.coroutines.delay(120)
            waited += 120
        }
        val text = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()?.trim().orEmpty()
        if (text.isEmpty()) return L.runtime.systemPromptEmpty
        systemPrompt = text
        PientLog.i(TAG_CHAT, "系统提示词已按 pi 回流：${text.length} 字")
        return null
    }

    /** 首个思考增量到达时记起点（思考行右侧计时与落库 durationMs 都用它） */
    private fun noteThinkingDelta(text: String) {
        if (streamThinkingStartedAt == 0L) streamThinkingStartedAt = System.currentTimeMillis()
        streamThinking = text
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
            PientLog.w(TAG_CHAT, "叶自愈：会话 $sid 的 leaf 不在条目树里 → 回退到末条目 ${cur.id}")
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
        // 开始 / 正常结束 / 中止 / 出错 都经过它。
        if (running) {
            // 新一轮：工具计数归零（通知卡片上的「工具调用：N」）
            toolCallsThisTurn = 0
            PiKeepAlive.acquire(AppCtx.get(), "chat", L.runtime.aiReplying)
        } else {
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
            PientLog.i(TAG_CHAT, "已终止本轮：位置跟随后端（叶=$piLeafId，等待 ${waited}ms）")
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
            attachments = u.msg.attachments,
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
            val nodeMsg = piEntryById[nodeId]?.optJSONObject("message")
            // 节点自身（用户消息）：引用块 / 附件清单剥回结构 —— 与聊天页气泡同口径
            val selfMsg = if (nodeMsg != null) piUserMsg(nodeId, nodeMsg, localAttach = localAttachByText(sid)) else null
            if (selfMsg != null) out += selfMsg else pushPiMessage(nodeMsg, out)
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
                            val level = if (thinkingEnabled) thinkingLevel else "off"
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
                // 纯切分支：不带 --summarize（分支摘要按设计不生成，不额外花一次模型调用）
                runCatching { PiRpc.navigate(target) }
                    .onFailure { PientLog.w(TAG_CHAT, "pi 会话内分支跳转失败：${it.message}") }
                refreshPiTree()
                syncMessagesFromPi()
                PientLog.i(TAG_CHAT, "会话内分支跳转：节点 $nodeId → 目标 $target（pi 叶=$piLeafId）")
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

    /** fork 新会话默认标题：fork 点用户消息前 20 字（AI 消息则取其前一条用户消息） */
    private fun forkTitle(msgs: List<Msg>, index: Int): String {
        val userIdx = if (msgs[index] is Msg.User) index
        else (index downTo 0).firstOrNull { msgs[it] is Msg.User }
        if (userIdx == null) return L.runtime.newSessionTitle
        val text = (msgs[userIdx] as Msg.User).text.trim()
        val t = text.take(20)
        return if (text.length > 20) "$t…" else t
    }

    // ── 输入栏：模型选择器 ────────────────────────────────
    // 模型数据源（2026-09-09 起）：已配置服务商的模型列表（AiConfigStore）；
    // id = "providerId/modelName"。
    var selectedModelId by mutableStateOf("")
    var thinkingEnabled by mutableStateOf(false)

    /**
     * 用户偏好档位 = **pi 的档位字面量**（`minimal`…`max`，2026-09-17 改）。
     *
     * 为什么不再用应用的五档枚举存偏好：界面现在**按 pi 报的档位渲染**（pi-web 口径），
     * 存字面量就不需要「五档 ↔ n 档」的等距映射（那层映射会让档位少的模型出现两个停位等价、
     * 「拖了没变化」的观感）。切到档位少的模型时 pi 会夹取并回读，偏好本身不被改写。
     */
    var thinkingLevel by mutableStateOf("medium")

    /**
     * **pi 侧此刻的档位**（真值在 pi：`get_state.thinkingLevel`）。null = 未知（通道没起 / 还没问过）。
     *
     * 为什么要这份镜像（2026-09-17）：思考参数是**由 pi 发出去的**（应用侧不再拼请求体），
     * 所以开关与滑轨若不推到 pi 就只是界面装饰 —— 关掉开关 ≠ 真关思考。现在的口径 =
     * 界面存的是「用户偏好」、[syncThinkingToPi] 推给 pi、再回读 pi 的夹取结果上屏。
     */
    var piThinkingLevel by mutableStateOf<String?>(null)

    /** pi 报的当前模型可用档位（含 `off`）；null = 未知 */
    var piThinkingLevels by mutableStateOf<List<String>?>(null)

    /**
     * **当前选中模型的档位表**：pi 的回答（只属于通道**正在跑**的那个模型，切完模型要等下次发送才换）
     * 对不上时，用目录/配置本地算（[PiAgentFiles.effectiveThinkingLevels]）。null = 连本地也算不出来。
     *
     * 这样模型面板切模型时**立刻**按新模型的档位表重画，不再出现「明明 3 档却画回退表 4 档」。
     */
    fun selectedThinkingInfo(): ThinkingInfo? {
        val ctx = AppCtx.get() ?: return null
        val m = selectedModel ?: return null
        val c = AiConfigStore.configs[m.provider] ?: return null
        val entry = m.name.ifBlank { m.id.substringAfter('/') }
        return runCatching {
            ThinkingInfo(
                levels = PiAgentFiles.effectiveThinkingLevels(ctx, c, entry),
                effortSupported = PiAgentFiles.effectiveEffortSupported(ctx, c, entry),
            )
        }.getOrNull()
    }

    /** 面板要的两件事：该模型有哪些档位、以及**档位会不会真发上线** */
    data class ThinkingInfo(val levels: List<String>, val effortSupported: Boolean)

    /**
     * 该发给 pi 的档位：关思考 = `off`，开 = 偏好本身（偏好就是 pi 的档位字面量，**不用再映射**）。
     * 偏好不在当前模型的可用档里时由 pi 夹取（`clampThinkingLevel`），界面再回读真值上屏。
     *
     * 例外：模型**关不掉思考**（档位表里没有 `off`）时，发**最低那档**而不是 `off` —— pi 的
     * `clampThinkingLevel` 从 off 往上找第一个可用档，结果一样，但显式发出去后界面回读到的就是
     * 同一档，不会出现「开关显示关、实际在思考」的错觉。
     */
    fun targetPiLevel(): String {
        if (thinkingEnabled) return thinkingLevel
        val levels = piThinkingLevels
        if (levels != null && "off" !in levels) return levels.firstOrNull() ?: "off"
        return "off"
    }

    /** 记录 pi 报的可用档位（**不改用户存的偏好**：模型不支持思考时由界面按 `pref && support` 渲染成关） */
    private fun applyPiLevels(levels: List<String>) {
        piThinkingLevels = levels
    }

    /**
     * **把界面的思考开关/档位同步给 pi**（执行方是 pi，界面只是偏好）：
     * - 通道不可用 → 清空镜像（下次通道就绪时会再推一次）；
     * - 只在目标 ≠ pi 现值时才推（每变一次 pi 会往会话 append 一条 `thinking_level_change`）；
     * - 推完**回读**真值（pi 会按模型能力夹取，界面据此显示实际档位）。
     */
    fun syncThinkingToPi() {
        if (!PiRpc.usable()) {
            piThinkingLevel = null
            piThinkingLevels = null
            return
        }
        bgScope.launch { syncThinkingToPiNow() }
    }

    /**
     * 上一条的**挂起版**：发送路径必须 `await` 它（2026-09-17 实测发现）——
     * 通道刚重启时 pi 的档位还是默认 `medium`，若推送与 `prompt` 并行发出，**本轮会跑在旧档位上**
     * （只有下一条消息才对）。所以发送前一律先 await 同步完再发。
     */
    /**
     * 通道**当前实际在跑**的模型（`provider/model`）；未运行 = null。
     * 用途：判断 pi 对档位表/档位的回答属于**哪个模型**（见 [syncThinkingToPiNow]）。
     */
    private fun piRunningKey(): String? =
        (PiRpc.state.value as? PiRpcState.Running)?.let { "${it.provider}/${it.model}" }

    suspend fun syncThinkingToPiNow() {
        if (!PiRpc.usable()) return
        // **通道跑的是别的模型**（刚在面板里切了模型，但通道要等下次发送才重启）→ pi 此刻对
        // `get_available_thinking_levels` / `get_state` 的回答都属于**上一个模型**：照收就会拿 A 模型的
        // 档位表去说 B 模型（切完模型第一眼显示错的档位数、错的真值行）。一律当未知，走界面回退。
        val t = piChannelTarget()
        if (t != null && piRunningKey() != "${t.first}/${t.second}") {
            piThinkingLevel = null
            piThinkingLevels = null
            return
        }
        PiRpc.availableThinkingLevels()?.let { applyPiLevels(it) }
        val target = targetPiLevel()
        val now = PiRpc.thinkingLevelNow()
        if (now != target) PiRpc.setThinkingLevel(target)
        // **只记 pi 确认过的值**：不能再 `?: target` 回落 —— 那样通道没起时界面会把「偏好」当成
        // pi 的真值念出来（2026-09-17 真机自查：没通道时那行写「服务商实际收到：low」）。
        // 拿不到就留 null，由界面走「预计…」的估算分支（见 ModelSelectorSheet）。
        piThinkingLevel = PiRpc.thinkingLevelNow() ?: now
    }

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

    // 上下文用量（**pi 真值**，2026-09-16 取代原型常量 61200 / 180000）：
    // 由 [refreshContextUsage] 从 `get_session_stats.contextUsage` 填；窗口未知时卡上显示 `—`。
    var windowTokens by mutableStateOf(0)
    var maxWindowTokens by mutableStateOf(0)
    // 用量是否已知：pi 在「压缩后还没有新回复」时给不出 tokens（agent-session.ts 的口径），
    // 此时卡上照 pi-web 显示 `?`，不编数字。
    var contextUsageKnown by mutableStateOf(false)
    var connectionLabel by mutableStateOf(L.runtime.connected)
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
    // 文件树长按菜单「@ 提及插入输入框」请求（**成品引用文本**：`@路径 ` / `@"含空格 路径" `，
    // 由 FileTreePanel 按 mentionTextFor 生成；ChatScreen 消费后置 null）
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
