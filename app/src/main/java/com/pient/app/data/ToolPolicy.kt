package com.pient.app.data

import android.content.Context
import android.util.Log
import com.pient.app.runtime.PiRuntime
import org.json.JSONObject
import java.io.File

/**
 * 工具级授权策略（开发计划 §6.3「工具级动态权限请求」）：**全局默认 + 单工具例外**，三档
 * ALLOW / ASK / FORBID。
 *
 * 唯一落盘处 = 宿主 HOME 的 `~/.pi/agent/pient_gate.json`：权限守门扩展（`assets/pient-gate.ts`，
 * 由 PiRuntime.syncAgentAssets 同步进 `extensions/`）**每次工具调用现读**它，所以 App 侧改完立即
 * 生效；反过来 App 是这份文件的唯一写者（弹窗里选「始终允许」= 写一条 ALLOW 例外）。
 */
object ToolPolicy {

    private const val TAG = "PiHost"

    const val ALLOW = "ALLOW"
    const val ASK = "ASK"
    const val FORBID = "FORBID"

    /** 七工具（顺序 = 将来权限中心列表的展示顺序） */
    val TOOLS = listOf("read", "write", "edit", "bash", "grep", "find", "ls")

    /**
     * 弹窗三个选项的文案——**跨语言契约**：必须与 `assets/pient-gate.ts` 的 OPT_* 逐字一致，
     * 宿主按回传的字符串判断「仅本次 / 始终 / 拒绝」。
     */
    const val OPT_ONCE = "仅本次允许"
    const val OPT_ALWAYS = "始终允许"
    const val OPT_DENY = "拒绝"

    private fun file(context: Context): File = File(PiRuntime.agentDir(context), "pient_gate.json")

    private fun valid(v: String?): String? = v?.takeIf { it == ALLOW || it == ASK || it == FORBID }

    /** 策略快照：全局默认 + 每个工具的有效策略（文件缺失/损坏 → 内置默认，不写盘） */
    fun snapshot(context: Context): Pair<String, Map<String, String>> {
        val builtin = JSONObject(PiRuntime.DEFAULT_TOOL_POLICY)
        val defPolicy = valid(builtin.optString("default")) ?: ASK
        val builtinTools = builtin.optJSONObject("tools")
        val fallback = TOOLS.associateWith { t -> valid(builtinTools?.optString(t)) ?: defPolicy }
        val f = file(context)
        if (!f.exists()) return defPolicy to fallback
        return runCatching {
            val obj = JSONObject(f.readText())
            val d = valid(obj.optString("default")) ?: defPolicy
            val tools = obj.optJSONObject("tools")
            d to TOOLS.associateWith { t -> valid(tools?.optString(t)) ?: fallback.getValue(t) }
        }.getOrElse {
            Log.w(TAG, "工具策略解析失败：${it.message}")
            defPolicy to fallback
        }
    }

    /** 某工具当前生效的策略 */
    fun policyFor(context: Context, tool: String): String {
        val (def, tools) = snapshot(context)
        return tools[tool] ?: def
    }

    /**
     * 写盘前的「原始策略」（只含用户显式设过的键；文件缺失 → 内置默认）。
     * **不做 snapshot 那种逐工具补全**：否则会把整表固化成显式值，
     * 之后改「默认策略」再也影响不到那些本来跟随默认的工具。
     */
    private fun raw(context: Context): JSONObject {
        val f = file(context)
        val base = runCatching {
            if (f.exists()) JSONObject(f.readText()) else JSONObject(PiRuntime.DEFAULT_TOOL_POLICY)
        }.getOrElse { JSONObject(PiRuntime.DEFAULT_TOOL_POLICY) }
        return base
    }

    private fun write(context: Context, obj: JSONObject, log: String) {
        runCatching {
            val f = file(context)
            f.parentFile?.mkdirs()
            f.writeText(obj.toString())
            Log.i(TAG, log)
        }.onFailure { Log.w(TAG, "工具策略写入失败：${it.message}") }
    }

    /** 写一条单工具例外（其余键不动） */
    fun setToolPolicy(context: Context, tool: String, policy: String) {
        if (valid(policy) == null || tool.isBlank()) return
        val obj = raw(context)
        val tools = obj.optJSONObject("tools") ?: JSONObject().also { obj.put("tools", it) }
        tools.put(tool, policy)
        write(context, obj, "工具策略更新：$tool = $policy")
    }

    /** 写全局默认策略（工具例外一律保留；可在设置页「工具级授权」里改） */
    fun setDefault(context: Context, policy: String) {
        if (valid(policy) == null) return
        val obj = raw(context)
        obj.put("default", policy)
        write(context, obj, "工具默认策略更新：$policy")
    }
}

/**
 * 一次工具授权询问（权限守门扩展经扩展 UI 子协议抛上来的）：
 * [id] 用于回 `extension_ui_response`；[toolName]/[argsSummary] 是弹窗要显示的内容；
 * [dangerous] = 高危命令（`bash` 命中 rm -rf / sudo 等，弹窗走破坏色变体）。
 */
data class PendingPermission(
    val id: String,
    val toolName: String,
    val argsSummary: String,
    val dangerous: Boolean,
)
