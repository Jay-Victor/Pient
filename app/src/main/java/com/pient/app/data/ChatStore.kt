package com.pient.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 项目会话记录持久化（2026-09-09 实现）：项目 / 会话 / 消息记录全量落盘
 * filesDir/pient_data/state.json，重启后恢复（load）——侧栏项目与历史会话、
 * 聊天消息记录跨重启保留。保存由 PientApp 的 snapshotFlow 防抖触发（save）。
 * 序列化用系统自带 org.json（无新依赖）。
 */
object ChatStore {

    private fun file(context: Context) = File(context.filesDir, "pient_data/state.json")

    fun save(context: Context, state: ChatState) {
        try {
            val root = JSONObject()
            root.put("currentProject", state.currentProject ?: JSONObject.NULL)
            root.put("currentSessionId", state.currentSessionId ?: JSONObject.NULL)
            root.put("selectedModelId", state.selectedModelId)
            root.put("thinkingEnabled", state.thinkingEnabled)
            root.put("thinkingLevel", state.thinkingLevel.name)
            root.put("streamingOutputEnabled", state.streamingOutputEnabled)

            val projects = JSONArray()
            state.projects.forEach { p ->
                projects.put(
                    JSONObject()
                        .put("name", p.name)
                        .put("path", p.path)
                        .put("uri", p.uri ?: JSONObject.NULL),
                )
            }
            root.put("projects", projects)

            val sessions = JSONObject()
            state.sessions.forEach { (project, list) ->
                val arr = JSONArray()
                list.forEach { s ->
                    arr.put(
                        JSONObject()
                            .put("id", s.id)
                            .put("title", s.title)
                            .put("project", s.project)
                            .put("updatedAt", s.updatedAt)
                            .put("running", s.running)
                            .put("pinned", s.pinned),
                    )
                }
                sessions.put(project, arr)
            }
            root.put("sessions", sessions)

            val messages = JSONObject()
            state.messagesBySession.forEach { (id, list) ->
                val arr = JSONArray()
                list.forEach { m -> arr.put(serializeMsg(m)) }
                messages.put(id, arr)
            }
            root.put("messages", messages)

            val f = file(context)
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, "state.json.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(f)) {
                f.writeText(root.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            // 落盘失败不阻断使用
        }
    }

    fun load(context: Context, state: ChatState) {
        val f = file(context)
        if (!f.exists()) return
        try {
            val root = JSONObject(f.readText())

            val projects = root.optJSONArray("projects")
            if (projects != null) {
                for (i in 0 until projects.length()) {
                    val p = projects.getJSONObject(i)
                    val uri = if (p.isNull("uri")) null else p.optString("uri")
                    state.addProjectSilently(p.optString("name"), p.optString("path"), uri)
                }
            }

            val sessions = root.optJSONObject("sessions")
            if (sessions != null) {
                for (key in sessions.keys()) {
                    val list = state.sessions.getOrPut(key) { androidx.compose.runtime.mutableStateListOf() }
                    val arr = sessions.getJSONArray(key)
                    for (i in 0 until arr.length()) {
                        val s = arr.getJSONObject(i)
                        val updatedAt = s.optLong("updatedAt", 0)
                        list += Session(
                            id = s.optString("id"),
                            title = s.optString("title", "新建会话"),
                            project = s.optString("project", key),
                            running = false, // 运行态不跨重启
                            pinned = s.optBoolean("pinned", false),
                            updatedAt = updatedAt,
                        )
                    }
                }
            }

            val messages = root.optJSONObject("messages")
            if (messages != null) {
                for (key in messages.keys()) {
                    val list = state.messagesBySession.getOrPut(key) {
                        androidx.compose.runtime.mutableStateListOf()
                    }
                    val arr = messages.getJSONArray(key)
                    for (i in 0 until arr.length()) {
                        deserializeMsg(arr.getJSONObject(i))?.let { list += it }
                    }
                }
            }

            state.currentProject = root.optString("currentProject").takeIf { it.isNotEmpty() }
            state.currentSessionId = root.optString("currentSessionId").takeIf { it.isNotEmpty() }
            state.selectedModelId = root.optString("selectedModelId")
            state.thinkingEnabled = root.optBoolean("thinkingEnabled", false)
            state.thinkingLevel = runCatching {
                ThinkingLevel.valueOf(root.optString("thinkingLevel", "MEDIUM"))
            }.getOrDefault(ThinkingLevel.MEDIUM)
            state.streamingOutputEnabled = root.optBoolean("streamingOutputEnabled", true)
            state.normalizeAfterLoad()
        } catch (e: Exception) {
            // 记录损坏：忽略（按全新状态处理）
        }
    }

    // ───────────────────────── 消息序列化 ─────────────────────────

    private fun serializeMsg(m: Msg): JSONObject = when (m) {
        is Msg.User -> JSONObject()
            .put("type", "user")
            .put("text", m.text)
            .put("attachments", JSONArray().apply {
                m.attachments.forEach { a ->
                    put(
                        JSONObject()
                            .put("name", a.name)
                            .put("kind", a.kind.name)
                            .put("path", a.path ?: JSONObject.NULL),
                    )
                }
            })
        is Msg.Assistant -> JSONObject()
            .put("type", "assistant")
            .put("markdown", m.markdown)
            .put("model", m.model ?: JSONObject.NULL)
            .put("error", m.error)
            .put("usage", m.usage?.let { u ->
                JSONObject()
                    .put("in", u.inTokens)
                    .put("out", u.outTokens)
                    .put("cache", u.cacheTokens)
                    .put("cost", u.costUsd)
            } ?: JSONObject.NULL)
        is Msg.Thinking -> JSONObject()
            .put("type", "thinking")
            .put("level", m.level)
            .put("text", m.text)
        is Msg.ToolCall -> JSONObject()
            .put("type", "toolcall")
            .put("name", m.name)
            .put("params", m.params)
            .put("status", m.status.name)
            .put("detail", m.detail ?: JSONObject.NULL)
        is Msg.ToolResult -> JSONObject()
            .put("type", "toolresult")
            .put("toolName", m.toolName)
            .put("preview", m.preview)
            .put("full", m.full ?: JSONObject.NULL)
        is Msg.Compaction -> JSONObject()
            .put("type", "compaction")
            .put("tokensBefore", m.tokensBefore)
            .put("saved", m.saved)
            .put("summary", m.summary)
    }

    private fun deserializeMsg(o: JSONObject): Msg? = try {
        when (o.optString("type")) {
            "user" -> {
                val atts = mutableListOf<Attachment>()
                val arr = o.optJSONArray("attachments")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val a = arr.getJSONObject(i)
                        atts += Attachment(
                            name = a.optString("name"),
                            kind = runCatching {
                                AttachmentKind.valueOf(a.optString("kind", "FILE"))
                            }.getOrDefault(AttachmentKind.FILE),
                            path = if (a.isNull("path")) null else a.optString("path"),
                        )
                    }
                }
                Msg.User(o.optString("text"), atts)
            }
            "assistant" -> {
                val u = o.optJSONObject("usage")
                Msg.Assistant(
                    markdown = o.optString("markdown"),
                    usage = u?.let {
                        Usage(it.optInt("in"), it.optInt("out"), it.optInt("cache"), it.optDouble("cost"))
                    },
                    model = if (o.isNull("model")) null else o.optString("model"),
                    error = o.optBoolean("error", false),
                )
            }
            "thinking" -> Msg.Thinking(o.optString("level"), o.optString("text"))
            "toolcall" -> Msg.ToolCall(
                name = o.optString("name"),
                params = o.optString("params"),
                status = runCatching {
                    ToolStatus.valueOf(o.optString("status", "DONE"))
                }.getOrDefault(ToolStatus.DONE),
                detail = if (o.isNull("detail")) null else o.optString("detail"),
            )
            "toolresult" -> Msg.ToolResult(
                toolName = o.optString("toolName"),
                preview = o.optString("preview"),
                full = if (o.isNull("full")) null else o.optString("full"),
            )
            "compaction" -> Msg.Compaction(
                tokensBefore = o.optInt("tokensBefore"),
                saved = o.optInt("saved"),
                summary = o.optString("summary"),
            )
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}
