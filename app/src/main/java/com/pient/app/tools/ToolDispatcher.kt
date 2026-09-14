package com.pient.app.tools

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * **Kotlin 内置调度（工具调用的唯一入口）** —— 名字 → 层 → 就绪检查 → 授权门 → 执行 → 统一结果。
 *
 * 两条路径都进这里：
 * - 直连路径：`ChatState.runDirectChat` 的工具循环逐条调 [dispatch]；
 * - 宿主路径：pi 的扩展工具经回桥 `POST /tool` 调 [dispatch]（M3 起）。
 *
 * 这一层**只知道怎么调度**：不认识模型、不认识界面，也不认识任何一层的内部实现
 * （那三件事分别在 `ChatState` / `ToolGate` 弹窗 / 各 `ToolPart` 里）。
 */
object ToolDispatcher {

    private const val TAG = "PientTools"

    /** 一轮对话里最多几轮工具调用（防止模型来回空转） */
    const val MAX_ROUNDS = 8

    /**
     * 调度一次工具调用。
     *
     * @param authorize ASK 策略时的询问回调（由调用方提供：直连路径弹应用内对话框，宿主路径
     *   由守门扩展自己弹）；为 null 视为「无法询问」→ 拒绝执行（不静默放行）。
     */
    suspend fun dispatch(
        context: Context,
        call: ToolCall,
        authorize: (suspend (ToolCall) -> Boolean)? = null,
    ): ToolOutcome = withContext(Dispatchers.IO) {
        val spec = ToolRegistry.specOf(call.name)
            ?: return@withContext ToolOutcome.err(
                "未知工具：${call.name}（可用：${ToolRegistry.names().joinToString(", ")}）",
            )
        val part = ToolRegistry.partOf(spec.layer)

        // ① 就绪检查：层没就绪就不执行，把原因如实回给模型（模型据此知道是环境问题而非自己写错）
        part.notReady(context)?.let { return@withContext ToolOutcome.err(it) }

        // ② 授权门（唯一实现：同一份 pient_gate.json）
        when (ToolGate.policyFor(context, call.name)) {
            ToolGate.FORBID -> return@withContext ToolOutcome.err(
                "已拒绝：Pient 权限策略禁止调用 ${call.name}",
            )
            ToolGate.ASK -> {
                val allowed = authorize?.invoke(call) ?: false
                if (!allowed) return@withContext ToolOutcome.err(
                    "已拒绝：用户未授权执行「${call.name}」（下次调用会再问一次）",
                )
            }
        }

        // ③ 执行（异常统一收口：任何一层抛错都变成一条错误结果，不把整轮对话带崩）
        val args = runCatching { JSONObject(call.arguments.ifBlank { "{}" }) }.getOrElse { JSONObject() }
        runCatching { part.run(context, call.name, args) }.getOrElse { e ->
            Log.w(TAG, "工具 ${call.name} 执行失败：${e.message}")
            ToolOutcome.err("工具 ${call.name} 执行失败：${e.message ?: e::class.java.simpleName}")
        }
    }
}
