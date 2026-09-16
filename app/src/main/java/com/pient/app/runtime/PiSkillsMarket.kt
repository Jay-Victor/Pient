package com.pient.app.runtime

import com.pient.app.data.i18n.L
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/**
 * **技能市场**（2026-09-15，要求 4）：通信照 pi-web 的做法，落到 Pient 里。
 *
 * 参考实现（`Refences/pi-web-0.8.9`）的两条通道，逐字对齐：
 *  - **搜索** `app/api/skills/search/route.ts`：先打 `https://skills.sh/api/search?q=<q>&limit=<n>`
 *    （JSON `{skills:[{id,name,source,installs}]}`；条目拼成 `source@name`，按安装量倒序）；
 *    该接口不通时回落到 **`npx skills find <查询词>`**，解析它的文本输出
 *    （`owner/repo@skill   NNK installs`，下一行 `└ https://…`）。
 *  - **安装** `app/api/skills/install/route.ts`：**`npx skills add <包> -y --agent pi`**
 *    （全局再加 `-g`；项目作用域用 cwd = 项目目录）。`--agent pi` 是关键 —— 让 skills CLI
 *    按 pi 的目录约定落地（这与「技能页要适配 pi」是同一件事）。
 *
 * 安装命令**跑在终端页的会话里**（与 pi 包管理同口径：过程可见、报错原文可见），
 * 会话固定 Ubuntu（见 [GuestScripts]）。
 */
object PiSkillsMarket {
    private const val TAG = "PiSkillsMarket"

    /** 安装/搜索在终端页用哪个会话（用户可切过去看全过程） */
    /** 专用会话名（计算属性：object 里的 val 只求值一次，写 `L.…` 会冻结成首帧语言） */
    val SESSION: String get() = L.runtime.skillMarketSession

    /** 市场服务地址（pi-web 里是环境变量 SKILLS_API_URL，默认 skills.sh） */
    private const val SEARCH_API_BASE = "https://skills.sh"

    private const val DEFAULT_LIMIT = 30

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 一条市场结果 */
    data class Hit(
        val name: String,
        /** skills CLI 认的「包」标识：`source@name`（安装时原样传给 `skills add`） */
        val pkg: String,
        /** 已排版好的安装量（`12.3K installs`；拿不到为空串） */
        val installs: String,
        val url: String,
    ) {
        /** 列表里显示的名字（包尾段，去掉 @技能名） */
        val label: String get() = pkg.substringBefore('@').substringAfterLast('/').ifBlank { pkg }
    }

    /**
     * 搜索技能市场。返回 `(命中列表, 错误文本)` —— 错误非空时命中可能为空，
     * **如实把原因带回界面**（连不上 / npx 未装 / 没有结果，三者在界面上是不同的文案）。
     */
    suspend fun search(context: Context, query: String, limit: Int = DEFAULT_LIMIT): Pair<List<Hit>, String?> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList<Hit>() to L.runtime.enterKeyword
        val api = runCatching { searchViaApi(q, limit) }
        api.getOrNull()?.let { hits ->
            Log.i(TAG, "skills.sh 搜索「$q」：${hits.size} 条")
            return hits to null
        }
        val apiErr = api.exceptionOrNull()
        Log.w(TAG, "skills.sh 搜索失败（回落 npx）：${apiErr?.message}")
        val fallback = searchViaNpx(context, q, limit)
        return fallback
    }

    /**
     * 安装（或更新）一条市场技能：`npx skills add <pkg> -y --agent pi [-g]`。
     *
     * **前置自检 git**（2026-09-16 真机实测）：skills CLI 的市场条目都是 `owner/repo@skill`，
     * 它靠 `git clone` 拉仓库 —— guest 里没有 git 时它只打印
     * `Failed to clone …: Error: spawn git ENOENT`，退回页面只有一句「安装失败（退出码 …）」，
     * 用户看不出该干什么。这里先查一次并给出可执行的下一步（git 在「环境配置 → 基础与开发」里）。
     */
    fun install(
        context: Context,
        pkg: String,
        global: Boolean,
        onDone: ((Int) -> Unit)? = null,
    ): TerminalSessions.Session {
        val args = buildString {
            append("npx -y skills add ").append(shellQuote(pkg)).append(" -y --agent pi")
            if (global) append(" -g")
        }
        val guard = "if ! command -v git >/dev/null 2>&1; then " +
            "echo '[技能市场] 缺少 git —— 技能仓库是用 git 克隆的。请到「环境配置 → 基础与开发」" +
            "勾选 Git 安装一次，再回来装技能。'; exit 3; fi; "
        // 项目作用域要在项目目录里跑（skills CLI 按 cwd 找 .agents/skills）；全局则无所谓
        val cmd = if (global) guard + args else "cd /workspace 2>/dev/null; " + guard + args
        return GuestScripts.runInTerminal(context, SESSION, L.runtime.installSkillLabel(pkg), cmd, onDone)
    }

    // ─────────────────────────── 搜索实现 ───────────────────────────

    /**
     * 主通道：skills.sh 的 JSON 接口（与 pi-web 完全一致）。
     *
     * **必须在 IO 线程**（2026-09-16 实测修的 bug）：这里是阻塞式 `execute()`，
     * 而调用方是 Compose 的 `LaunchedEffect`（Main dispatcher）——直接调会抛
     * `NetworkOnMainThreadException`，它的 `message` 是 **null**，日志里只看得到
     * 「skills.sh 搜索失败（回落 npx）：null」，很容易被当成网络问题。
     */
    private suspend fun searchViaApi(query: String, limit: Int): List<Hit> = withContext(Dispatchers.IO) {
        val url = "$SEARCH_API_BASE/api/search?q=${enc(query)}&limit=$limit"
        val req = Request.Builder().url(url).header("accept", "application/json").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("skills.sh HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            val skills = JSONObject(body).optJSONArray("skills") ?: return@withContext emptyList()
            val hits = ArrayList<Hit>()
            for (i in 0 until skills.length()) {
                val o = skills.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                val source = o.optString("source").trim()
                val slug = o.optString("id").trim()
                if (name.isEmpty() || (source.isEmpty() && slug.isEmpty())) continue
                val base = source.ifEmpty { slug }
                hits += Hit(
                    name = name,
                    pkg = "$base@$name",
                    installs = formatInstalls(o.optInt("installs", 0)),
                    url = if (slug.isNotEmpty()) "$SEARCH_API_BASE/$slug" else "",
                )
            }
            return@withContext hits.sortedByDescending { parseInstalls(it.installs) }
        }
    }

    /**
     * 回落通道：`npx skills find <q>`（pi-web 同款）。
     * 需要的 Node 环境由「环境配置」页提供；没装 node 时给出可读的一步指引，而不是丢一段 stderr。
     */
    private suspend fun searchViaNpx(context: Context, query: String, limit: Int): Pair<List<Hit>, String?> {
        val (code, out) = GuestExec.run(
            context,
            "command -v npx >/dev/null 2>&1 || { echo '[pient] 缺少 npx —— 请到「环境配置」安装 Node.js'; exit 3; }; " +
                "npx -y skills find ${shellQuote(query)} 2>&1",
            timeoutMs = 60_000,
        )
        val text = GuestExec.stripAnsi(out)
        val hits = parseFindOutput(text).take(limit)
        return when {
            hits.isNotEmpty() -> hits to null
            code == 3 || text.contains("缺少 npx") ->
                emptyList<Hit>() to L.runtime.marketNoNpx
            code != 0 -> emptyList<Hit>() to L.runtime.searchFailed(code, text.trim().lines().lastOrNull().orEmpty().take(160))
            else -> emptyList<Hit>() to L.runtime.searchFailedNoResult
        }
    }

    /**
     * 解析 `npx skills find` 的输出（照 pi-web 的 parseSearchOutput）：
     * 一行 `owner/repo@skill   12.3K installs`，紧随其后可能有一行 `└ https://…`。
     */
    internal fun parseFindOutput(raw: String): List<Hit> {
        val pkgRe = Regex("^([\\w.\\-]+/[\\w.\\-@:]+)\\s+([\\d.,]+[KMB]?\\s+installs)$")
        val hits = ArrayList<Hit>()
        val lines = raw.split('\n')
        for ((i, line0) in lines.withIndex()) {
            val line = line0.trim().removePrefix("└").trim()
            val m = pkgRe.find(line) ?: continue
            val pkg = m.groupValues[1]
            val installs = m.groupValues[2]
            val next = lines.getOrNull(i + 1)?.trim()?.removePrefix("└")?.trim().orEmpty()
            hits += Hit(
                name = pkg.substringAfterLast('@'),
                pkg = pkg,
                installs = installs,
                url = if (next.startsWith("https://")) next else "",
            )
        }
        return hits.sortedByDescending { parseInstalls(it.installs) }
    }

    /** `12.3K installs` → 数字（排序用） */
    private fun parseInstalls(text: String): Double {
        val m = Regex("^([\\d.]+)([KMB])?").find(text.trim()) ?: return 0.0
        val v = m.groupValues[1].toDoubleOrNull() ?: return 0.0
        return v * when (m.groupValues[2]) {
            "B" -> 1_000_000_000.0
            "M" -> 1_000_000.0
            "K" -> 1_000.0
            else -> 1.0
        }
    }

    /** 与 pi-web 的 formatInstalls 同口径（1.2M / 12.3K / 42 installs） */
    internal fun formatInstalls(count: Int): String = when {
        count <= 0 -> ""
        count >= 1_000_000 -> trimZero(count / 1_000_000.0) + "M installs"
        count >= 1_000 -> trimZero(count / 1_000.0) + "K installs"
        count == 1 -> "1 install"
        else -> "$count installs"
    }

    private fun trimZero(v: Double): String {
        val s = "%.1f".format(v)
        return s.removeSuffix(".0")
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    /** 单引号包裹（安装命令里要经 guest 的 shell 再解析一层） */
    internal fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
