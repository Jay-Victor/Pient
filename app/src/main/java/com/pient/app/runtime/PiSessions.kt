package com.pient.app.runtime

import android.content.Context
import android.util.Log
import com.pient.app.data.AiConfigStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/**
 * Pient 会话 ↔ pi 会话的映射（2026-09-12）。
 *
 * 背景：宿主的会话是 pi 自己管的（`~/.pi/agent/sessions/<ISO>_<sessionId>.jsonl`，格式 v3）。
 * App 侧会话（`ChatStore`）与它各记一套，必须显式对上——否则在 UI 里换会话/换项目时，
 * 宿主会把上一条会话的上下文接着用（错误上下文）。
 *
 * 映射落盘在 `files/pient_data/pi_sessions.json`：`{ "<Pient 会话 id>": "<pi sessionId>" }`。
 * 首次建立映射时还会把 Pient 侧历史**物化**成 pi 会话文件（见 [materialize]）——否则新映射出来的
 * pi 会话是空的，而用户在 Pient 里明明看着一长串对话（决策口径：Pient 会话树继续当界面记录，
 * 只在边界把历史喂给 pi）。
 * 切换时的动作（RPC 官方命令）：
 * - 有映射且文件在 → `switch_session {sessionPath}`（sessionPath 由会话目录里「时间戳_<sessionId>.jsonl」
 *   推导，pi 的会话文件名格式见 `PiRuntime.sessionsDir`）；
 * - 无映射/文件不在 → `new_session`，再 `get_state` 取回新 sessionId 记入映射。
 */
object PiSessions {

    private const val TAG = "PiHost"
    private const val FILE_NAME = "pi_sessions.json"

    private fun file(context: Context) = File(context.filesDir, "pient_data/$FILE_NAME")

    private fun load(context: Context): JSONObject = runCatching {
        val f = file(context)
        if (f.exists()) JSONObject(f.readText()) else JSONObject()
    }.getOrDefault(JSONObject())

    private fun save(context: Context, obj: JSONObject) {
        runCatching {
            val f = file(context)
            f.parentFile?.mkdirs()
            f.writeText(obj.toString())
        }.onFailure { Log.w(TAG, "会话映射落盘失败：${it.message}") }
    }

    /** pi 侧会话文件（按 sessionId 在会话目录里找 `<任意时间戳>_<sessionId>.jsonl`） */
    fun sessionFile(context: Context, piSessionId: String): File? {
        val dir = PiRuntime.sessionsDir(context)
        val suffix = "_$piSessionId.jsonl"
        return dir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(suffix) }
    }

    // ───────────────────── 历史物化（Pient 历史 → pi 会话文件 v3） ─────────────────────

    /**
     * 把 Pient 历史写成一个 pi 会话文件（v3 JSONL，与桌面 pi 同构）并 `switch_session` 过去。
     * 只写纯文本 user/assistant 条目（思考/工具条目不进 pi 上下文，与桌面会话同口径），
     * 线性 parentId 链；文件头与文件名照 pi 的口径（`<ISO 把 : . 换成 ->_<uuidv7>.jsonl`）。
     * 物化轮次没有可映射的真实用量，`usage` 记零——只影响 pi 自己的成本统计，不造假数。
     *
     * @return 新 pi sessionId；失败（含切不过去）返回 null，调用方退回空会话
     */
    private suspend fun materialize(
        context: Context,
        history: List<Pair<String, String>>,
        provider: String?,
        model: String?,
    ): String? = runCatching {
        val dir = PiRuntime.sessionsDir(context)
        if (!dir.exists() && !dir.mkdirs()) return@runCatching null
        val id = newSessionId()
        val now = System.currentTimeMillis()
        val iso = Instant.ofEpochMilli(now).toString()
        val file = File(dir, iso.replace(':', '-').replace('.', '-') + "_" + id + ".jsonl")
        val api = AiConfigStore.configs[provider].let { cfg ->
            cfg?.endpoint?.let { PiConfig.apiFor(it) } ?: "openai-completions"
        }

        val sb = StringBuilder()
        sb.append(
            JSONObject().put("type", "session").put("version", 3).put("id", id)
                .put("timestamp", iso).put("cwd", PiRuntime.appDir(context).absolutePath),
        ).append('\n')

        var parent: String? = null
        if (!provider.isNullOrBlank() && !model.isNullOrBlank()) {
            val mid = newEntryId()
            sb.append(
                JSONObject().put("type", "model_change").put("id", mid)
                    .put("parentId", JSONObject.NULL).put("timestamp", iso)
                    .put("provider", provider).put("modelId", model),
            ).append('\n')
            parent = mid
        }

        history.forEach { (role, text) ->
            val entryId = newEntryId()
            val message = JSONObject()
                .put("role", role)
                .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
                .put("timestamp", now)
            if (role == "assistant") {
                message.put("api", api)
                if (!provider.isNullOrBlank()) message.put("provider", provider)
                if (!model.isNullOrBlank()) message.put("model", model)
                message.put("usage", zeroUsage())
            }
            sb.append(
                JSONObject().put("type", "message").put("id", entryId)
                    .put("parentId", parent ?: JSONObject.NULL).put("timestamp", iso)
                    .put("message", message),
            ).append('\n')
            parent = entryId
        }

        file.writeText(sb.toString())

        val resp = PiHost.request("switch_session", JSONObject().put("sessionPath", file.absolutePath))
        val ok = resp?.optBoolean("success") == true &&
            !resp.optJSONObject("data").optBoolean("cancelled")
        if (!ok) {
            file.delete()
            return@runCatching null
        }
        id
    }.getOrElse {
        Log.w(TAG, "历史物化异常：${it.message}")
        null
    }

    /** pi 的会话 id = uuidv7（48 位毫秒 + 版本/变体位 + 随机尾）；照做以保持排序与校验口径一致 */
    private fun newSessionId(): String {
        val b = ByteArray(16)
        java.security.SecureRandom().nextBytes(b)
        val ms = System.currentTimeMillis()
        for (i in 0 until 6) b[i] = ((ms shr (8 * (5 - i))) and 0xFF).toByte()
        b[6] = ((b[6].toInt() and 0x0F) or 0x70).toByte()   // version 7
        b[8] = ((b[8].toInt() and 0x3F) or 0x80).toByte()   // variant 10xx
        val hex = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) +
            "-" + hex.substring(16, 20) + "-" + hex.substring(20)
    }

    /** 条目 id：pi 用 8 位 hex（碰撞重试），此处同口径 */
    private fun newEntryId(): String =
        java.util.UUID.randomUUID().toString().replace("-", "").take(8)

    /** 物化轮次的 usage 占位（全零；老消息没有可映射的真实用量） */
    private fun zeroUsage(): JSONObject = JSONObject()
        .put("input", 0).put("output", 0).put("cacheRead", 0).put("cacheWrite", 0)
        .put("reasoning", 0).put("totalTokens", 0)
        .put(
            "cost",
            JSONObject().put("input", 0).put("output", 0)
                .put("cacheRead", 0).put("cacheWrite", 0).put("total", 0),
        )

    /**
     * 让宿主切到与 [pientSessionId] 对应的 pi 会话（无则新建）。幂等：已在该会话时不发命令。
     * @return 当前 pi sessionId（宿主不可用时为 null）
     */
    suspend fun ensure(
        context: Context,
        pientSessionId: String,
        /** Pient 侧历史（role → 文本）；首次映射时物化进 pi 会话，别让宿主从空上下文开始 */
        history: List<Pair<String, String>> = emptyList(),
        provider: String? = null,
        model: String? = null,
    ): String? {
        val key = pientSessionId.ifBlank { "unsaved" }
        val map = load(context)

        val currentPi = PiHost.lastState.value?.optString("sessionId").orEmpty()
        val mapped = map.optString(key).takeIf { it.isNotBlank() }
        if (mapped != null && mapped == currentPi) return mapped // 已经对上，不打扰宿主

        if (mapped != null) {
            val path = sessionFile(context, mapped)
            if (path != null) {
                val resp = PiHost.request("switch_session", JSONObject().put("sessionPath", path.absolutePath))
                if (resp?.optBoolean("success") == true && !resp.optJSONObject("data").optBoolean("cancelled")) {
                    Log.i(TAG, "会话切换：Pient=$key → pi=$mapped")
                    return mapped
                }
                Log.w(TAG, "会话切换失败，改建新会话：$key")
            }
        }

        // 首次映射：有历史就物化成 pi 会话文件再切过去（宿主上下文 = 用户看到的对话）；
        // 没历史或物化失败才退回空会话
        if (history.isNotEmpty()) {
            val id = materialize(context, history, provider, model)
            if (id != null) {
                map.put(key, id)
                save(context, map)
                Log.i(TAG, "会话物化：Pient=$key → pi=$id（${history.size} 条历史）")
                return id
            }
            Log.w(TAG, "历史物化失败，改建新会话：$key")
        }

        val created = PiHost.request("new_session") ?: return null
        if (created.optBoolean("success") != true) return null
        val piId = PiHost.refresh()?.optString("sessionId").orEmpty()
        if (piId.isBlank()) return null
        map.put(key, piId)
        save(context, map)
        Log.i(TAG, "会话映射建立：Pient=$key → pi=$piId")
        return piId
    }
}
