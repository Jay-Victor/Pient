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
        /**
         * 工具自带的 details（pi：`result.details`）——目前只取文件编辑的 unified diff
         * （pi `edit` 工具返回 `details: { diff, patch, firstChangedLine }`）。
         * null = 该工具没有 details（read/bash/grep/find/ls 都没有）。
         */
        val diff: String? = null,
    ) : PiAgentEvent()

    /** 本轮用量（pi 的 usage：input/output/cacheRead/cacheWrite/totalTokens/cost） */
    data class UsageEvent(val usage: Usage) : PiAgentEvent()

    /**
     * 压缩开始（pi `compaction_start`，2026-09-13 接：之前宿主压缩过程对 App 完全不可见）。
     * reason = manual / threshold / overflow（见 pi packages/coding-agent/docs/rpc.md）。
     */
    data class CompactionStart(val reason: String) : PiAgentEvent()

    /**
     * 压缩完成（pi `compaction_end`）：`result` 里是摘要与前后 token 估值；
     * aborted/errorMessage 分别对应「被中止」与「摘要失败（如配额）」两种失败态。
     */
    data class CompactionEnd(
        val reason: String,
        val summary: String?,
        val tokensBefore: Int,
        val estimatedAfter: Int,
        val aborted: Boolean,
        val errorMessage: String?,
    ) : PiAgentEvent()

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
