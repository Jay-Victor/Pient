package com.pient.app.tools

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * **工具包声明解析** —— 把包文件顶部的 `/* METADATA … *&#47;` 块读成 [ToolPackage]。
 *
 * 解析口径（v1，刻意保守）：
 * - 只用**正则取块**（`/\*\s*METADATA\s*([\s\S]*?)\*/`，与 Operit 一致）；
 * - 块内按 **JSON** 解析，允许两种便利写法：无引号的键、末尾多余逗号（这两样是手写包最常见的笔误）；
 * - **不支持** Operit 那种「成员之间漏逗号」的 HJSON —— Pient 的内置包自己写，一律写规范 JSON；
 *   将来要收第三方 HJSON 包，再补一个宽松解析器（届时只换 [normalize] 这一处）。
 *
 * 非法块一律**跳过并记诊断**，不让一个坏包把整个工具层带崩（与 pi 对技能包"警告但宽容"同口径）。
 */
object ToolPkgParser {

    private const val TAG = "PientTools"

    private val BLOCK = Regex("""/\*\s*METADATA\s*([\s\S]*?)\*/""")

    /** 解析结果：包 + 诊断信息（页面展示/日志用） */
    data class Result(val pkg: ToolPackage, val warnings: List<String>)

    /** 解析一个包文件；没有 METADATA 块或内容非法时返回 null（并把原因写进 [warnings]） */
    fun parse(text: String, path: String, builtIn: Boolean): Result? {
        val warnings = ArrayList<String>()
        val raw = BLOCK.find(text)?.groupValues?.get(1)
        if (raw.isNullOrBlank()) {
            warnings += "$path：没有 METADATA 块"
            Log.w(TAG, warnings.last())
            return null
        }
        val json = runCatching { JSONObject(normalize(raw)) }.getOrElse { e ->
            warnings += "$path：METADATA 不是合法 JSON（${e.message}）"
            Log.w(TAG, warnings.last())
            return null
        }
        val name = json.optString("name").trim()
        if (name.isEmpty()) {
            warnings += "$path：包缺少 name"
            Log.w(TAG, warnings.last())
            return null
        }
        val layer = layerOf(json.optString("layer"), warnings, path)
        val tools = parseTools(json.optJSONArray("tools"), warnings, path)
        if (tools.isEmpty()) {
            warnings += "$name：没有可用工具（tools 为空或全部非法）"
            Log.w(TAG, warnings.last())
        }
        return Result(
            ToolPackage(
                name = name,
                displayName = json.optString("display_name").trim().ifEmpty { name },
                description = json.optString("description").trim(),
                category = json.optString("category").trim().ifEmpty { "Other" },
                layer = layer,
                enabledByDefault = json.optBoolean("enabledByDefault", true),
                builtIn = builtIn,
                tools = tools,
                path = path,
            ),
            warnings,
        )
    }

    private fun layerOf(id: String, warnings: MutableList<String>, path: String): ToolLayer {
        val layer = ToolLayer.entries.firstOrNull { it.id == id.trim().lowercase() }
        if (layer == null) {
            warnings += "$path：layer 缺失或未知（`$id`）→ 归入扩展层"
            return ToolLayer.EXTENSION
        }
        return layer
    }

    private fun parseTools(arr: JSONArray?, warnings: MutableList<String>, path: String): List<PackageTool> {
        if (arr == null) return emptyList()
        val out = ArrayList<PackageTool>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            if (name.isEmpty()) {
                warnings += "$path：第 ${i + 1} 个工具缺少 name，已跳过"
                continue
            }
            if (out.any { it.name == name }) {
                warnings += "$path：工具名重复（$name），已忽略后一个"
                continue
            }
            out += PackageTool(
                name = name,
                label = o.optString("label").trim().ifEmpty { name },
                description = o.optString("description").trim(),
                parameters = o.optJSONObject("parameters") ?: emptyObject(),
                advice = o.optBoolean("advice", false),
            )
        }
        return out
    }

    /** 空参数 schema（无参工具） */
    fun emptyObject(): JSONObject = JSONObject()
        .put("type", "object")
        .put("properties", JSONObject())
        .put("required", JSONArray())

    /**
     * 手写包的两种便利写法 → 规范 JSON：
     * - 无引号的键：`{ name: "x" }` → `{ "name": "x" }`；
     * - 末尾多余逗号：`{"a":1,}` → `{"a":1}`。
     */
    private fun normalize(raw: String): String {
        var s = raw.trim()
        s = Regex("""([{,]\s*)([A-Za-z_][A-Za-z0-9_]*)(\s*:)""").replace(s) { m ->
            "${m.groupValues[1]}\"${m.groupValues[2]}\"${m.groupValues[3]}"
        }
        s = Regex(""",(\s*[}\]])""").replace(s) { m -> m.groupValues[1] }
        return s
    }
}
