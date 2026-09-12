package com.pient.app.runtime

import android.util.Log
import com.pient.app.data.Usage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * 宿主驱动的对话（agent 事件流）。
 *
 * 事件口径（pi 官方 rpc.md + 设备实测）：
 * - `message_update`：增量事件，真正的载荷在 `assistantMessageEvent`（`thinking_start` /
 *   `thinking_delta` / `text_delta`…），文本取 `.content`；
 * - `message_end`：一条消息完成，`message.content` 是完整块数组（我们用它兜底取全文）；
 * - `tool_execution_start`：`{toolCallId, toolName, args}`；
 * - `tool_execution_end`：`{toolCallId, toolName, result:{content:[{type:"text",text}]}, isError}`；
 * - `agent_end` / `agent_settled`：本轮跑完（`agent_end` 带 `messages[]`）。
 */
sealed class PiAgentEvent {
    data class TextDelta(val text: String) : PiAgentEvent()
    data class ThinkingDelta(val text: String) : PiAgentEvent()
    data class ToolStart(val callId: String, val name: String, val args: String) : PiAgentEvent()
    data class ToolUpdate(val callId: String, val text: String) : PiAgentEvent()
    data class ToolEnd(
        val callId: String,
        val name: String,
        val output: String,
        val isError: Boolean,
    ) : PiAgentEvent()

    /** 本轮用量（pi 的 usage：input/output/cacheRead/cacheWrite/totalTokens/cost） */
    data class UsageEvent(val usage: Usage) : PiAgentEvent()

    /**
     * 扩展的 UI 请求（`extension_ui_request`）：权限守门扩展的授权询问即走这里。
     * 载荷在 title：`PientGate|<工具名>|<参数 JSON>|<高危 0|1>`（见 assets/pient-gate.ts）。
     */
    data class UiRequest(
        val id: String,
        val method: String,
        val options: List<String>,
        val toolName: String,
        val argsSummary: String,
        val dangerous: Boolean,
    ) : PiAgentEvent()

    data class Done(val text: String) : PiAgentEvent()
    data class Failed(val message: String) : PiAgentEvent()
}

object PiChat {

    private const val TAG = "PiHost"

    /**
     * 发一条 prompt 并回调 agent 事件；返回本轮助手正文（失败/超时抛 [IllegalStateException]）。
     * 必须在 [PiHost.ensureStarted] 成功（宿主在跑且有模型）之后调用。
     */
    suspend fun prompt(
        message: String,
        timeoutMs: Long = 300_000,
        onEvent: (PiAgentEvent) -> Unit,
    ): String = coroutineScope {
        val text = StringBuilder()
        val finished = CompletableDeferred<Boolean>()

        val collector = launch {
            PiHost.events.collect { ev ->
                when (ev.optString("type")) {
                    "message_update" -> {
                        ev.optJSONObject("usage")?.let { u -> usageOf(u)?.let { onEvent(PiAgentEvent.UsageEvent(it)) } }
                        val ame = ev.optJSONObject("assistantMessageEvent") ?: return@collect
                        val piece = ame.optString("content").ifEmpty {
                            ame.optString("text").ifEmpty { ame.optString("delta") }
                        }
                        if (piece.isEmpty()) return@collect
                        when (ame.optString("type")) {
                            "thinking_delta" -> onEvent(PiAgentEvent.ThinkingDelta(piece))
                            "text_delta" -> {
                                text.append(piece)
                                onEvent(PiAgentEvent.TextDelta(piece))
                            }
                        }
                    }

                    "message_end" -> {
                        // 兜底：以完整消息体覆盖累计文本（增量缺失/顺序异常时仍能得到正确全文）
                        val msg = ev.optJSONObject("message") ?: return@collect
                        if (msg.optString("role") != "assistant") return@collect
                        ev.optJSONObject("usage")?.let { u -> usageOf(u)?.let { onEvent(PiAgentEvent.UsageEvent(it)) } }
                        val full = textOf(msg.optJSONArray("content"))
                        if (full.isNotEmpty()) {
                            text.clear()
                            text.append(full)
                        }
                    }

                    "tool_execution_start" -> onEvent(
                        PiAgentEvent.ToolStart(
                            ev.optString("toolCallId"),
                            ev.optString("toolName"),
                            ev.optJSONObject("args")?.toString().orEmpty(),
                        )
                    )

                    "tool_execution_update" -> onEvent(
                        PiAgentEvent.ToolUpdate(
                            ev.optString("toolCallId"),
                            ev.optString("text").ifEmpty { ev.optString("delta") },
                        )
                    )

                    "tool_execution_end" -> {
                        val result = ev.optJSONObject("result")
                        val output = textOf(result?.optJSONArray("content"))
                        onEvent(
                            PiAgentEvent.ToolEnd(
                                ev.optString("toolCallId"),
                                ev.optString("toolName"),
                                output,
                                ev.optBoolean("isError", false),
                            )
                        )
                    }

                    "extension_ui_request" -> {
                        val title = ev.optString("title")
                        val head = title.split('|')
                        val gate = head.size >= 4 && head[0] == "PientGate"
                        val opts = ev.optJSONArray("options")
                        val options = buildList {
                            if (opts != null) for (i in 0 until opts.length()) add(opts.optString(i))
                        }
                        onEvent(
                            PiAgentEvent.UiRequest(
                                id = ev.optString("id"),
                                method = ev.optString("method"),
                                options = options,
                                toolName = if (gate) head[1] else "",
                                argsSummary = if (gate) head[2] else title,
                                dangerous = gate && head[3] == "1",
                            ),
                        )
                    }

                    "agent_end", "agent_settled" -> finished.complete(true)
                    "error" -> finished.completeExceptionally(
                        IllegalStateException(ev.optString("message", "宿主返回错误")),
                    )
                }
            }
        }

        val response = PiHost.request("prompt", JSONObject().put("message", message), 60_000)
        if (response == null || !response.optBoolean("success", true)) {
            collector.cancel()
            val err = response?.optString("error").orEmpty().ifEmpty { "宿主未受理本条消息" }
            onEvent(PiAgentEvent.Failed(err))
            throw IllegalStateException(err)
        }

        val settled = withTimeoutOrNull(timeoutMs) { runCatching { finished.await() }.getOrDefault(false) }
        collector.cancel()
        if (settled != true) {
            onEvent(PiAgentEvent.Failed("等待宿主响应超时"))
            throw IllegalStateException("等待宿主响应超时")
        }
        val result = text.toString()
        onEvent(PiAgentEvent.Done(result))
        result
    }

    /**
     * pi 的 usage JSON → Pient 的 [Usage]。字段口径与既有实现一致：
     * `input` 已是不含缓存的部分、`cacheRead`→cacheTokens、`cacheWrite`→cacheWriteTokens、`cost.total`→costUsd。
     */
    private fun usageOf(u: JSONObject): Usage? {
        val inTokens = u.optInt("input", 0)
        val outTokens = u.optInt("output", 0)
        val cacheRead = u.optInt("cacheRead", 0)
        val cacheWrite = u.optInt("cacheWrite", 0)
        val cost = u.optJSONObject("cost")?.optDouble("total", 0.0) ?: 0.0
        if (inTokens == 0 && outTokens == 0 && cacheRead == 0 && cacheWrite == 0) return null
        return Usage(inTokens, outTokens, cacheRead, cost, cacheWrite)
    }

    /** 从 pi 的消息内容块数组里取纯文本（多个 text 块拼接） */
    private fun textOf(content: org.json.JSONArray?): String {
        if (content == null) return ""
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") == "text") sb.append(block.optString("text"))
        }
        return sb.toString()
    }
}
