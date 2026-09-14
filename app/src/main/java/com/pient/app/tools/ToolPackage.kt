package com.pient.app.tools

import org.json.JSONObject

/**
 * **工具包（ToolPkg）** —— 工具的「声明」载体，照 Operit 的机制（见 `Docx/Pient 工具层设计.md` §3）。
 *
 * 一个包 = 一份声明（本文件）+ 可选实现：
 * - 声明写在包文件顶部以 METADATA 开头的块注释里（见 `assets/toolpkg/pient_app.js`），字段：`name / display_name / description /
 *   category / layer / enabledByDefault / tools[]`；
 * - `tools[].name` **就是执行体的名字**（内置包由 Kotlin 四层按名字绑定，见 [ToolDispatcher]）；
 * - 参数只有 4 个字段 `name / type / description / required`（与 Operit 一致，避免每包一套方言）。
 *
 * 为什么把声明搬出 Kotlin：① 加一个工具不再需要改代码；② 用户包/未来的 JS 包走同一条路
 * （内置包只是 `source = assets` 的包）；③ 工具页可以按包展示「谁提供了什么」。
 *
 * @param layer 包内工具默认归属的层（`system` / `terminal` / `app` / `extension`），
 *   决定调度器把它们路由到哪个执行体
 * @param source `assets`（随包内置）或 `user`（`filesDir/toolpkg` 下的用户包）
 */
data class ToolPackage(
    val name: String,
    val displayName: String,
    val description: String,
    val category: String,
    val layer: ToolLayer,
    val enabledByDefault: Boolean,
    val builtIn: Boolean,
    val tools: List<PackageTool>,
    /** 包文件在设备上的绝对路径（详情/诊断用；内置包为 assets 路径） */
    val path: String,
)

/** 包里的一个工具：声明（名字/描述/参数）。**不含实现**——实现由执行体按名字提供。 */
data class PackageTool(
    val name: String,
    val label: String,
    val description: String,
    val parameters: JSONObject,
    val advice: Boolean = false,
)

/** 包 + 它在设备上的启用状态（工具页与调度器共用） */
data class ToolPackageState(
    val pkg: ToolPackage,
    val enabled: Boolean,
) {
    /** 实际会下发给模型的工具（`advice` 只作提示、不下发，与 Operit 同口径） */
    val effectiveTools: List<PackageTool> get() = pkg.tools.filter { !it.advice }
}
