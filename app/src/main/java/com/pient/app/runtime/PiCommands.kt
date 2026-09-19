package com.pient.app.runtime

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pient.app.data.PientLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * pi 的**命令面**（RPC `get_commands` 的解析）—— 输入栏 `/` 候选卡的数据源（技能），
 * 同时供「这条消息是不是扩展命令」的判断（见 [isExtensionCommand]）。
 *
 * 为什么以 pi 的 RPC 为准，而不是自己扫盘（用户 2026-09-17 拍板）：
 * - `get_commands`（`docs/rpc.md` §get_commands、`modes/rpc/rpc-mode.ts` 的 get_commands 分支）
 *   回的就是 pi **此刻真会认的**命令：扩展注册命令（`pi.registerCommand`）+ prompt 模板 + 技能
 *   （技能注册成 `skill:<名字>`）—— 插进输入框的 `/…` 发出去必然被展开/执行；
 * - 扫盘会**多出** pi 不认的（重名冲突落选项、frontmatter 非法的）并**漏掉** pi 认的
 *   （插件带来的技能、settings `skills` 数组、`--skill` 路径）；卡片是「调用入口」，口径必须跟 pi 一致；
 * - 停用的技能（Pient 挪进 `.disabled/`）pi 的扫描器直接跳过 → 这里自然不出现。
 *
 * 每条命令的 `sourceInfo` 形状（2026-09-17 真机 vivo V2171A 实测）：
 * ```
 * {"path":"/root/.pi/agent/extensions/pient.ts","source":"auto",
 *  "scope":"user","origin":"top-level","baseDir":"/root/.pi/agent"}
 * ```
 * - `scope`：`user` = 全局、`project` = 项目（插件带来的资源取插件自己那一档的 scope）；
 * - `origin == "package"` 时 `source` = 包 source 串（与 `pi list` 打印的**同一份写法**）→ 用它归属插件；
 * - `path` / `baseDir` 指向包安装目录 → `pi list` 的 installedPath 前缀匹配作兜底。
 *
 * 已知边界（pi 侧的事实，不是本模块的 bug）：pi 在**进程启动时**加载技能/插件，
 * 所以刚从技能页导入或 `pi install` 完，这里要等 pi 通道重启才反映（0.85.1 没有 RPC reload）。
 */
object PiCommands {
    private const val TAG = "PiCommands"

    /** 命令类型（对应 `get_commands` 的 `source` 字段） */
    enum class Kind { COMMAND, SKILL, PROMPT }

    /** 全局 / 项目两档（与技能页、插件页的分段控制器同口径） */
    enum class Scope { GLOBAL, PROJECT }

    data class Item(
        /** pi 的命令名原样（技能 = `skill:<技能名>`） */
        val name: String,
        /** 展示名（技能去掉 `skill:` 前缀） */
        val label: String,
        /** 插进输入框的文本（`/名字 `；尾随空格便于接着打参数，与 pi-web applySlashCommand 同口径） */
        val insert: String,
        val kind: Kind,
        val scope: Scope,
        val description: String,
        /** 来自插件时 = 该包的 source 串（与 `pi list` 同一份写法）；应用自己的扩展 = null */
        val pluginSource: String?,
        val path: String,
    )

    /** 解析结果（每次 [refresh] 整体替换） */
    val items = mutableStateListOf<Item>()

    var loaded by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set

    /** 最近一次 [refresh] 是否拿到了 pi 的答复（false = 通道没回话；卡片据此如实显示「未就绪」） */
    var reachable by mutableStateOf(false); private set

    /** 技能（`/` 卡的候选）—— 按名字排序，方便翻阅 */
    fun skills(): List<Item> =
        items.filter { it.kind == Kind.SKILL }.sortedBy { it.label.lowercase() }

    /**
     * 文本是不是 pi 的**扩展命令**（`/名字 [args]`）。
     *
     * 为什么要单独判：扩展命令在 pi 里是**立即执行**、不产生任何 agent 事件
     * （`agent-session.ts` 的 `prompt()` 先走 `_tryExecuteExtensionCommand`，handled 即 return；
     * rpc.md 同口径）—— 当普通回合发出去，App 会把它标成「运行中」并且**永远等不到结束事件**，
     * 界面卡在运行中（真机实测）。所以发送侧要先判一次，走「即发即走」那条路。
     * 注意：prompt 模板与技能命令不算 —— 它们是**展开**成普通消息，照旧产生 agent 事件。
     */
    fun isExtensionCommand(text: String): Boolean {
        val t = text.trimStart()
        if (!t.startsWith("/")) return false
        val name = t.substring(1).substringBefore(' ').substringBefore('\n').substringBefore('\r')
        if (name.isBlank()) return false
        return items.any { it.kind == Kind.COMMAND && it.name == name }
    }

    /**
     * 问 pi 要一次命令面并解析。返回 true = 拿到（哪怕为空）；false = 通道没回话（pi 未就绪）。
     * 调用点：`/` 候选卡打开时（RPC 往返很短，缓存在 [items] 里，卡开着就即时显示）。
     */
    suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        loading = true
        try {
            val data = PiRpc.getCommands()
            if (data == null) {
                loaded = true
                reachable = false
                PientLog.w(TAG, "get_commands 无回包（pi 通道未就绪？）")
                false
            } else {
                val project = runCatching { PiRuntime.workspaceDir(context) }.getOrNull()
                val list = parse(data, project)
                items.clear()
                items.addAll(list)
                loaded = true
                reachable = true
                PientLog.i(
                    TAG,
                    "命令面：技能 ${list.count { it.kind == Kind.SKILL }} 个" +
                        "（全局 ${list.count { it.kind == Kind.SKILL && it.scope == Scope.GLOBAL }}" +
                        " / 项目 ${list.count { it.kind == Kind.SKILL && it.scope == Scope.PROJECT }}" +
                        "）、扩展命令 ${list.count { it.kind == Kind.COMMAND }} 个" +
                        "、提示模板 ${list.count { it.kind == Kind.PROMPT }} 个" +
                        "（其中插件贡献 ${list.count { it.pluginSource != null }} 个）",
                )
                true
            }
        } finally {
            loading = false
        }
    }

    /**
     * pi 侧磁盘变了（技能页导入 / 插件页装删）→ 让 pi 重扫一次再拉命令面。
     *
     * 为什么必须有这一步：pi 只在**进程启动**时扫描技能与插件目录 → 不重扫的话，
     * 刚导入的技能 / 刚装的插件（含插件带来的技能）在输入栏 `/` 卡里要等通道重启才出现。
     *
     * 两条前置：① **通道得在跑**（pi 是按需启动的：聊过天 / 开过画布才起）——
     * 没跑就跳过，反正下次启动通道时 pi 自己会读到新技能 / 新插件；
     * ② **空闲**（没在流式）—— reload 会重建扩展运行时，不插进正在跑的一轮里。
     */
    suspend fun reload(context: Context): Boolean {
        if (!PiRpc.running()) {
            PientLog.i(TAG, "pi 通道没在跑：跳过 reload（下次启动通道时自然读到新技能 / 插件）")
            return false
        }
        if (PiRpc.isStreamingNow() == true) {
            PientLog.i(TAG, "pi 在流式中：跳过 reload（命令面留待下次开卡或重启通道）")
            return false
        }
        val ok = PiRpc.reloadResources()
        if (ok) refresh(context)
        PientLog.i(TAG, "reload 技能/插件 = $ok" + if (ok) "（命令面已随之刷新）" else "（pi 没收下，命令面保持原样）")
        return ok
    }

    /** [reload] 的即发即忘版：磁盘写入方（技能导入 / 插件装删）调它，失败静默、不阻塞调用方 */
    fun reloadAsync(context: Context) {
        scope.launch { runCatching { reload(context) } }
    }

    /** 进程级作用域（只服务 [reloadAsync]；与界面生命周期无关） */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** `get_commands` 的 `data` 段 → 条目表（[projectDir] 用于临时来源的全局/项目归类兜底） */
    internal fun parse(data: JSONObject, projectDir: File?): List<Item> {
        val arr = data.optJSONArray("commands") ?: return emptyList()
        val out = ArrayList<Item>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            val kind = when (o.optString("source")) {
                "skill" -> Kind.SKILL
                "prompt" -> Kind.PROMPT
                else -> Kind.COMMAND
            }
            // sourceInfo 是 0.85.1 的形状；location/path 是 rpc.md 老写法的兜底键
            val info = o.optJSONObject("sourceInfo")
            val path = info?.optString("path").orEmpty().ifBlank { o.optString("path") }
            val baseDir = info?.optString("baseDir").orEmpty()
            val scopeRaw = info?.optString("scope").orEmpty().ifBlank { o.optString("location") }
            val origin = info?.optString("origin").orEmpty()
            val from = info?.optString("source").orEmpty()
            val projectPath = projectDir?.absolutePath.orEmpty()
            val scope = when (scopeRaw) {
                "user" -> Scope.GLOBAL
                "project" -> Scope.PROJECT
                // 插件以外的临时来源（settings `skills` 数组 / --skill 路径）：按落点归类，不臆造第三档
                else -> if (projectPath.isNotEmpty() &&
                    (path.startsWith(projectPath) || baseDir.startsWith(projectPath))
                ) Scope.PROJECT else Scope.GLOBAL
            }
            out += Item(
                name = name,
                label = if (kind == Kind.SKILL) name.removePrefix("skill:") else name,
                insert = "/$name ",
                kind = kind,
                scope = scope,
                description = o.optString("description").orEmpty(),
                pluginSource = from.takeIf { origin == "package" && it.isNotBlank() },
                path = path.ifBlank { baseDir },
            )
        }
        return out
    }
}
