package com.pient.app.tools

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * **工具包装载（内置包 + 用户包）与启停状态**。
 *
 * 两个来源：
 * - assets 里的 `toolpkg/` 目录：随 APK 分发的**内置包**（Pient 四层工具的声明就在这里，Kotlin 只留执行体）；
 * - `filesDir/toolpkg/`：用户/市场装的包（形态与内置包一致；JS 实现体在 M2 之后接）。
 *
 * 启用状态落 `filesDir/pient_data/toolpkg.json`（`{"disabled":["包名", …]}`）：
 * 与 pi 对技能/包的口径一致 —— **默认启用**，用户关掉的才记一笔（这样新增内置包不需要迁移状态）。
 */
object ToolPkgLoader {

    private const val TAG = "PientTools"
    private const val ASSET_DIR = "toolpkg"
    private const val USER_DIR = "toolpkg"
    private const val STATE_FILE = "toolpkg.json"

    /** 装好的包（含启用状态）；扫描结果按需缓存，[reload] 后失效 */
    @Volatile
    private var cached: List<ToolPackageState>? = null

    /** 解析诊断（工具页展示"为什么某个包没生效"） */
    @Volatile
    var warnings: List<String> = emptyList()
        private set

    @Synchronized
    fun packages(context: Context, force: Boolean = false): List<ToolPackageState> {
        cached?.takeIf { !force }?.let { return it }
        val disabled = disabledNames(context)
        val out = ArrayList<ToolPackageState>()
        val warns = ArrayList<String>()

        // ① 内置包（assets）
        runCatching { context.assets.list(ASSET_DIR)?.sorted()?.forEach { fileName ->
            if (!fileName.endsWith(".js")) return@forEach
            val text = runCatching {
                context.assets.open("$ASSET_DIR/$fileName").bufferedReader().readText()
            }.getOrNull() ?: return@forEach
            val res = ToolPkgParser.parse(text, "assets/$ASSET_DIR/$fileName", builtIn = true) ?: return@forEach
            warns += res.warnings
            out += ToolPackageState(res.pkg, enabled = res.pkg.name !in disabled)
        } }.onFailure { warns += "内置包扫描失败：${it.message}" }

        // ② 用户包（filesDir/toolpkg）
        val userDir = File(context.filesDir, USER_DIR)
        userDir.listFiles()?.sortedBy { it.name }?.forEach { f ->
            if (!f.isFile || !f.name.endsWith(".js")) return@forEach
            val res = ToolPkgParser.parse(f.readText(), f.absolutePath, builtIn = false) ?: return@forEach
            warns += res.warnings
            if (out.any { it.pkg.name == res.pkg.name }) {
                warns += "${f.name}：与内置包同名（${res.pkg.name}），已忽略用户包"
                return@forEach
            }
            out += ToolPackageState(res.pkg, enabled = res.pkg.name !in disabled)
        }

        warnings = warns
        cached = out
        Log.i(TAG, "工具包装载：${out.size} 个（${out.count { it.enabled }} 启用）")
        return out
    }

    /** 已启用的包 */
    fun enabled(context: Context): List<ToolPackageState> = packages(context).filter { it.enabled }

    /** 某个工具由哪个包提供（诊断/工具页用；找不到返回 null） */
    fun ownerOf(context: Context, toolName: String): ToolPackageState? =
        packages(context).firstOrNull { st -> st.effectiveTools.any { it.name == toolName } }

    /** 切换启用状态（写盘后缓存失效） */
    fun setEnabled(context: Context, name: String, enabled: Boolean) {
        val f = stateFile(context)
        val obj = runCatching {
            if (f.exists()) JSONObject(f.readText()) else JSONObject()
        }.getOrElse { JSONObject() }
        val arr = obj.optJSONArray("disabled") ?: org.json.JSONArray().also { obj.put("disabled", it) }
        val list = (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }.toMutableSet()
        if (enabled) list.remove(name) else list.add(name)
        val next = org.json.JSONArray()
        list.sorted().forEach { next.put(it) }
        obj.put("disabled", next)
        runCatching {
            f.parentFile?.mkdirs()
            f.writeText(obj.toString())
        }.onFailure { Log.w(TAG, "工具包状态写入失败：${it.message}") }
        reload()
    }

    /** 丢弃缓存（安装/删除包、或工具页下拉刷新时调） */
    fun reload() {
        cached = null
    }

    private fun disabledNames(context: Context): Set<String> = runCatching {
        val f = stateFile(context)
        if (!f.exists()) return@runCatching emptySet<String>()
        val arr = JSONObject(f.readText()).optJSONArray("disabled") ?: return@runCatching emptySet<String>()
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }.toSet()
    }.getOrDefault(emptySet())

    private fun stateFile(context: Context): File =
        File(File(context.filesDir, "pient_data"), STATE_FILE)
}
