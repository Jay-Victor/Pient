package com.pient.app.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowRight
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pient.app.data.Msg
import com.pient.app.data.ToolStatus
import com.pient.app.ui.theme.LocalPientIsDark
import com.pient.app.ui.theme.MonoFont
import org.json.JSONObject

/**
 * 工具调用 / 工具运行的行式渲染（2026-09-14 按 **Hermes 桌面端**重设计）。
 *
 * 参考源（真源逐项对齐，勿凭印象改数值）：
 *  - `apps/desktop/src/components/assistant-ui/tool/fallback.tsx`
 *      · ToolEntry：单行 = 14px glyph + 标题 + meta + 右侧 caret；**展开才套 5px 边框壳**，
 *        头部与正文之间一条 hairline，正文内 gap 6px；
 *      · ToolRun / ToolRunHeader：≥2 个活动型调用折成**一行灰色摘要**；运行时摘要（shimmer）
 *        + 一行滚动 ticker，结束态只有摘要、点开才铺开行；
 *      · TerminalTranscript：`$ 命令` 块 + `exit N` 徽标（0 绿 / 非 0 琥珀）；
 *      · ToolPayloadDisclosure：折叠的原始 args/result（mono 0.65rem）。
 *  - `.../tool/run-summary.ts`：摘要分句顺序 edit→explore→run→delegate→other；单条带目标写目标
 *    （"Explored wiring.tsx"），否则写计数（"ran 5 commands"）；**运行中的那一类用进行时**。
 *  - `.../chat/scaffold-row.tsx` + `styles.css` 会话令牌：
 *      `--conversation-tool-font-size` 0.6875rem(11px)、`--conversation-line-height` 1.125rem(18px)、
 *      meta 0.625rem(10px)、`--tool-row-gap` 0.375rem(6px)、`--scaffold-block-gap` 4px、
 *      scaffold 文本 = 前景 64% / meta 44%、脚手架**静息透明度 0.67**、
 *      壳 `rounded-[0.3125rem]`(5px) + `--ui-stroke-tertiary`(= accent 10% + base 5%)、
 *      段内 pre `max-h-20`(80dp)、段标签 0.65rem + tracking .08em。
 *  - `i18n/zh.ts` 的 `assistant.tool.*`：状态词、逐工具标题（已读取/正在读取…）、
 *      模板 `actionCommand` / `actionTarget` / `actionQuoted`。
 *
 * 触摸端的有意差异（Hermes 靠 hover 的地方）：
 *  - caret 常驻可见（静息透明度取 `--disclosure-caret-rest` 的 0.4）；
 *  - 运行中的 run 也允许点摘要铺开（Hermes 运行中不给 toggle）；
 *  - 悬停才现的复制按钮 / 行尾 × 不做（原型期无剪贴板写入件）。
 */

// ── 令牌（数值 = Hermes styles.css） ──
private val ToolFontSize = 11.sp            // 0.6875rem
private val ToolLineHeight = 18.sp          // 1.125rem
private val ToolMetaSize = 10.sp            // 0.625rem
private val ToolSectionLabelSize = 10.4.sp  // 0.65rem
private val ToolPreSize = 11.2.sp           // 0.7rem
private val ToolSectionTracking = 0.83.sp   // tracking .08em @ 0.65rem
private val ToolRowGap = 6.dp               // --tool-row-gap
private val ToolLineHeightDp = 18.dp        // --conversation-line-height（布局用；文本用 18.sp）
private val ToolShellRadius = 5.dp          // 0.3125rem
private val ToolScaffoldGap = 4.dp          // --scaffold-block-gap = turn/3
private const val ScaffoldRestAlpha = 0.67f
private const val CaretRestAlpha = 0.4f

/** Hermes 会话令牌色（`--ui-base` = 主题前景，各档按百分比混到背景上）。 */
private class ToolPalette(
    val scaffoldText: Color,
    val meta: Color,
    val secondary: Color,
    val stroke: Color,
    val fill: Color,
    val ok: Color,
    val warn: Color,
    val error: Color,
    val accent: Color,
)

@Composable
private fun toolPalette(): ToolPalette {
    val base = MaterialTheme.colorScheme.onSurface
    val isDark = LocalPientIsDark.current
    val accent = MaterialTheme.colorScheme.primary
    return ToolPalette(
        scaffoldText = base.copy(alpha = 0.64f),
        meta = base.copy(alpha = 0.44f),
        secondary = base.copy(alpha = 0.74f),
        // --ui-stroke-tertiary = accent 10% + base 5%
        stroke = accent.copy(alpha = 0.10f).compositeOver(base.copy(alpha = 0.05f)),
        fill = base.copy(alpha = 0.05f),
        ok = if (isDark) Color(0xFF3FB950) else Color(0xFF1A7F37),
        warn = if (isDark) Color(0xFFFFB224) else Color(0xFFB26A00),
        error = MaterialTheme.colorScheme.error,
        accent = accent,
    )
}

private fun TextStyle.tool(color: Color) = copy(color = color)

private fun toolStyle(size: androidx.compose.ui.unit.TextUnit, mono: Boolean = true, weight: FontWeight? = null) =
    TextStyle(
        fontSize = size,
        lineHeight = ToolLineHeight,
        fontFamily = if (mono) MonoFont else null,
        fontWeight = weight,
    )

// ───────────────────────── 分类（run-summary.ts 口径） ─────────────────────────

internal enum class ToolKind { EDIT, EXPLORE, RUN, OTHER }

internal fun toolKindOf(name: String): ToolKind = when (name) {
    "write", "edit", "patch" -> ToolKind.EDIT
    "read", "grep", "find", "ls" -> ToolKind.EXPLORE
    "bash" -> ToolKind.RUN
    else -> ToolKind.OTHER
}

/** 自带卡片的工具（不并进 run 摘要；Hermes `isCardTool`）：文件写入/编辑是交付物。 */
internal fun isCardTool(name: String): Boolean = toolKindOf(name) == ToolKind.EDIT

/** 活动型工具（可折进 run 摘要）：非交付物的读/搜/命令等。 */
internal fun isActivityTool(name: String): Boolean = !isCardTool(name)

// ───────────────────────── 标题 / 目标 / 计数 ─────────────────────────

private fun argsOf(params: String): JSONObject? = runCatching { JSONObject(params) }.getOrNull()

private fun firstArg(params: String, vararg keys: String): String {
    val o = argsOf(params) ?: return ""
    for (k in keys) {
        val v = o.optString(k, "")
        if (v.isNotEmpty()) return v
    }
    return ""
}

private fun basename(path: String): String =
    path.trimEnd('/').substringAfterLast('/').ifEmpty { path }

/** 命令摘要（Hermes `summarizeShellCommand`：压空白 + 截断）。 */
internal fun summarizeCommand(raw: String, max: Int = 160): String {
    val line = raw.replace(Regex("\\s+"), " ").trim()
    return if (line.length > max) line.take(max - 1) + "…" else line
}

private fun compact(raw: String, max: Int = 48): String {
    val line = raw.replace(Regex("\\s+"), " ").trim()
    return if (line.length > max) line.take(max - 1) + "…" else line
}

/** 逐工具标题（`i18n/zh.ts` 的 titles：done / pending）。 */
private fun baseTitle(name: String, pending: Boolean): String = when (name) {
    "read" -> if (pending) "正在读取文件" else "已读取文件"
    "write" -> if (pending) "正在编辑文件" else "已编辑文件"
    "edit" -> if (pending) "正在修补文件" else "已修补文件"
    "grep" -> if (pending) "正在搜索文件" else "已搜索文件"
    "find" -> if (pending) "正在查找文件" else "已查找文件"
    "ls" -> if (pending) "正在列出文件" else "已列出文件"
    "bash" -> if (pending) "正在运行命令" else "已运行命令"
    else -> if (pending) "正在运行 $name" else "已运行 $name"
}

/** 行标题：优先「动作 + 目标」（Hermes `dynamicTitle` → `actionTarget` / `actionCommand` / `actionQuoted`）。 */
internal fun toolRowTitle(call: Msg.ToolCall): String {
    val pending = call.status == ToolStatus.RUNNING
    val verb = { past: String, present: String -> if (pending) present else past }
    return when (call.name) {
        "bash" -> {
            val cmd = firstArg(call.params, "command")
            if (cmd.isEmpty()) baseTitle(call.name, pending)
            else "${verb("已运行", "正在运行")} ${compact(summarizeCommand(cmd), 160)}"
        }
        "grep" -> {
            val q = firstArg(call.params, "pattern", "query")
            if (q.isEmpty()) baseTitle(call.name, pending)
            else "${verb("已搜索", "正在搜索")}“${compact(q)}”"
        }
        "read" -> {
            val p = firstArg(call.params, "path", "file")
            if (p.isEmpty()) baseTitle(call.name, pending)
            else "${verb("已读取", "正在读取")} ${basename(p)}"
        }
        "write" -> {
            // Hermes：文件编辑类工具（write/edit/patch）的标题就是**文件名本身**（动作由
            // Edit 图标 + 正文承载），不拼动词——见 fallback-model `dynamicTitle` 的 isFileEditTool 分支
            val p = firstArg(call.params, "path", "file")
            if (p.isEmpty()) baseTitle(call.name, pending) else basename(p)
        }
        "edit" -> {
            val p = firstArg(call.params, "path", "file")
            if (p.isEmpty()) baseTitle(call.name, pending) else basename(p)
        }
        "ls" -> {
            val p = firstArg(call.params, "path", "dir")
            if (p.isEmpty()) baseTitle(call.name, pending)
            else "${verb("已列出", "正在列出")} ${basename(p)}"
        }
        "find" -> {
            val p = firstArg(call.params, "pattern", "path", "name")
            if (p.isEmpty()) baseTitle(call.name, pending)
            else "${verb("已查找", "正在查找")} ${compact(p)}"
        }
        else -> {
            val target = firstArg(call.params, "path", "query", "command")
            if (target.isEmpty()) baseTitle(call.name, pending)
            else "${verb("已运行", "正在运行")} ${compact(target)}"
        }
    }
}

/** 停顿时长（Hermes `formatDurationSeconds`：<1s 用 ms、<10s 一位小数、<60s 秒、否则 分+秒）。 */
internal fun formatDuration(ms: Long?): String? {
    if (ms == null || ms < 0) return null
    val s = ms / 1000.0
    return when {
        s < 1 -> "${maxOf(1L, ms)}ms"
        s < 10 -> String.format("%.1fs", s)
        s < 60 -> "${s.toInt()}s"
        else -> "${(s / 60).toInt()}m${(s % 60).toInt()}s"
    }
}

/** 结果计数标签（Hermes `formatCountLabel` = `N <名词>`）。 */
private fun countLabel(call: Msg.ToolCall, output: String): String? {
    if (call.status == ToolStatus.RUNNING || output.isBlank()) return null
    val lines = output.lines().count { it.isNotBlank() }
    if (lines <= 0) return null
    return when (call.name) {
        "grep" -> "$lines 处匹配"
        "find" -> "$lines 个文件"
        "ls" -> "$lines 项"
        "read" -> "$lines 行"
        else -> null
    }
}

/** bash 结果中的退出码（pi 非零退出会追加 “Command exited with code N”）。 */
private fun exitCodeOf(output: String?): Int? =
    output?.let { Regex("Command exited with code (\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }

/** 去掉 pi 追加的退出码尾注（命令块徽标已呈现）。 */
private fun stripExitNote(text: String): String =
    text.replace(Regex("(?m)^\\s*Command exited with code \\d+\\s*$"), "").trim()

// ───────────────────────── 单行 ─────────────────────────

/**
 * 一行工具调用：头部（glyph + 标题 + meta + caret）+ 可展开正文。
 * 展开态才套边框壳（Hermes `TOOL_EXPANDED_SHELL_CLASS`），其余时候只是一行灰字。
 */
@Composable
internal fun ToolRow(
    call: Msg.ToolCall,
    result: Msg.ToolResult?,
    onPermissionDemo: (() -> Unit)? = null,
) {
    var open by remember(call.name, call.params) { mutableStateOf(false) }
    val p = toolPalette()
    val output = result?.full ?: result?.preview ?: call.detail

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (open) Modifier.border(1.dp, p.stroke, RoundedCornerShape(ToolShellRadius))
                else Modifier,
            )
            // Hermes：脚手架静息 0.67；展开（或命中）提到 1
            .alpha(if (open) 1f else ScaffoldRestAlpha),
    ) {
        ToolRowHeader(call = call, palette = p, open = open, padded = open, onToggle = { open = !open })
        if (open) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.stroke))
            Column(
                modifier = Modifier.fillMaxWidth().padding(ToolRowGap),
                verticalArrangement = Arrangement.spacedBy(ToolRowGap),
            ) {
                ToolBody(call = call, output = output, palette = p)
                if (onPermissionDemo != null && call.detail != null) {
                    Text(
                        "演示：权限请求弹窗 →",
                        style = toolStyle(ToolSectionLabelSize).copy(color = p.accent),
                        modifier = Modifier.clickable(onClick = onPermissionDemo),
                    )
                }
                ToolPayloadDisclosure(call = call, result = result, palette = p)
            }
        }
    }
}

/** 头部行（Hermes `DisclosureRow`）：glyph 格 14dp + 标题 + meta + 右侧 caret。 */
@Composable
private fun ToolRowHeader(
    call: Msg.ToolCall,
    palette: ToolPalette,
    open: Boolean,
    padded: Boolean,
    onToggle: (() -> Unit)?,
) {
    val pending = call.status == ToolStatus.RUNNING
    val titleColor = when (call.status) {
        ToolStatus.FAILED -> palette.error
        ToolStatus.RUNNING -> palette.meta
        ToolStatus.DONE -> palette.scaffoldText
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (padded) Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                else Modifier.padding(vertical = 1.dp),
            )
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier),
    ) {
        ToolGlyph(call, palette)
        Spacer(Modifier.width(ToolRowGap))
        val style = toolStyle(ToolFontSize, weight = FontWeight.Medium).copy(color = titleColor)
        if (pending) {
            ShimmerText(toolRowTitle(call), style)
        } else {
            Text(
                toolRowTitle(call),
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val meta = if (isCardTool(call.name)) null
        else listOfNotNull(formatDuration(call.durationMs), countLabel(call, call.detail ?: ""))
            .firstOrNull()
        if (meta != null) {
            Text(
                meta,
                style = toolStyle(ToolMetaSize).copy(color = palette.meta),
                modifier = Modifier.padding(start = ToolRowGap),
                maxLines = 1,
            )
        }
        if (onToggle != null) {
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Outlined.ArrowRight,
                contentDescription = if (open) "收起" else "展开",
                tint = palette.scaffoldText,
                modifier = Modifier
                    .size(12.dp)
                    .alpha(if (open) 0.8f else CaretRestAlpha),
            )
        }
    }
}

/** 状态 glyph：运行中=转圈、失败=红叹号、成功=**工具图标**（Hermes `leadingStatus`：成功是安静的）。 */
@Composable
private fun ToolGlyph(call: Msg.ToolCall, palette: ToolPalette) {
    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
        when (call.status) {
            ToolStatus.RUNNING -> CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                strokeWidth = 1.5.dp,
                color = palette.scaffoldText,
            )
            ToolStatus.FAILED -> Icon(
                Icons.Outlined.ErrorOutline, contentDescription = "错误",
                tint = palette.error, modifier = Modifier.size(14.dp),
            )
            ToolStatus.DONE -> Icon(
                toolIconOf(call.name), contentDescription = null,
                tint = palette.scaffoldText, modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun toolIconOf(name: String) = when (name) {
    "read" -> Icons.Outlined.Description
    "write", "edit" -> Icons.Outlined.Edit
    "bash" -> Icons.Outlined.Terminal
    "grep", "find" -> Icons.Outlined.Search
    "ls" -> Icons.Outlined.Folder
    else -> Icons.Outlined.Build
}

// ───────────────────────── 正文 ─────────────────────────

/** 正文：先出命令块（有 command 参数时），再出一个带标签的输出段。 */
@Composable
private fun ToolBody(call: Msg.ToolCall, output: String?, palette: ToolPalette) {
    val command = firstArg(call.params, "command")
    if (command.isNotEmpty()) {
        ToolCommandBlock(command = command, exitCode = exitCodeOf(output), palette = palette)
    }
    if (call.status == ToolStatus.RUNNING) return
    val body = output?.trim().orEmpty()
    if (body.isEmpty()) {
        if (command.isEmpty()) {
            Text(
                compact(call.params, 200),
                style = toolStyle(ToolPreSize).copy(color = palette.secondary),
            )
        }
        return
    }
    ToolSectionBlock(
        label = sectionLabelFor(call.name),
        text = stripExitNote(body),
        palette = palette,
        error = call.status == ToolStatus.FAILED,
    )
}

private fun sectionLabelFor(name: String) = when (name) {
    "read" -> "内容"
    "grep", "find", "ls" -> "结果"
    else -> "输出"
}

/** `$ 命令` + `exit N` 徽标（Hermes `TerminalTranscript` 同款几何/配色）。 */
@Composable
private fun ToolCommandBlock(command: String, exitCode: Int?, palette: ToolPalette) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.stroke, RoundedCornerShape(4.dp))
            .background(palette.fill, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text("$", style = toolStyle(ToolPreSize).copy(color = palette.accent))
        Spacer(Modifier.width(ToolRowGap))
        Text(
            command,
            style = toolStyle(ToolPreSize).copy(color = palette.secondary),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (exitCode != null) {
            Text(
                "exit $exitCode",
                style = toolStyle(9.6.sp).copy(color = if (exitCode == 0) palette.ok else palette.warn),
                modifier = Modifier
                    .padding(start = ToolRowGap)
                    .background(palette.fill, RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/** 段标签（0.65rem / tracking .08em）+ 限高 80dp 滚动的 mono 正文（Hermes `TOOL_SECTION_*`）。 */
@Composable
private fun ToolSectionBlock(
    label: String,
    text: String,
    palette: ToolPalette,
    error: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = toolStyle(ToolSectionLabelSize).copy(
                color = palette.meta,
                fontWeight = FontWeight.Medium,
                letterSpacing = ToolSectionTracking,
            ),
        )
        val scroll = rememberScrollState()
        Text(
            text,
            style = toolStyle(ToolPreSize).copy(color = if (error) palette.error else palette.secondary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .heightIn(max = 80.dp)
                .verticalScroll(scroll)
                .horizontalScroll(rememberScrollState()),
        )
    }
}

/** 「工具负载」折叠披露（Hermes `ToolPayloadDisclosure`：原始 args/result，默认收起）。 */
@Composable
private fun ToolPayloadDisclosure(call: Msg.ToolCall, result: Msg.ToolResult?, palette: ToolPalette) {
    var open by remember(call.params, call.detail) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { open = !open },
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowRight, contentDescription = null,
                tint = palette.meta,
                modifier = Modifier.size(10.dp).alpha(if (open) 0.8f else CaretRestAlpha),
            )
            Spacer(Modifier.width(ToolRowGap))
            Text(
                "工具负载",
                style = toolStyle(ToolSectionLabelSize).copy(
                    color = palette.meta,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = ToolSectionTracking,
                ),
            )
        }
        if (open) {
            Text(
                payloadText(call, result),
                style = toolStyle(ToolSectionLabelSize).copy(color = palette.secondary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .heightIn(max = 160.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
            )
        }
    }
}

private fun payloadText(call: Msg.ToolCall, result: Msg.ToolResult?): String {
    val args = runCatching { JSONObject(call.params).toString(2) }.getOrElse { call.params }
    val out = (result?.full ?: call.detail).orEmpty()
    return buildString {
        append("args\n").append(args)
        if (out.isNotEmpty()) append("\n\nresult\n").append(out)
    }
}

// ───────────────────────── 运行摘要 ─────────────────────────

private data class CatCopy(val noun: String, val past: String, val present: String)

private val CatCopyOf = mapOf(
    ToolKind.EDIT to CatCopy("文件", "已编辑", "正在编辑"),
    ToolKind.EXPLORE to CatCopy("文件", "已读取", "正在读取"),
    ToolKind.RUN to CatCopy("命令", "已运行", "正在运行"),
    ToolKind.OTHER to CatCopy("工具", "已使用", "正在使用"),
)

/** 分句顺序固定（Hermes `CATEGORY_ORDER`）：编辑 → 读取 → 运行 → 其他。 */
private val CatOrder = listOf(ToolKind.EDIT, ToolKind.EXPLORE, ToolKind.RUN, ToolKind.OTHER)

private fun runTarget(call: Msg.ToolCall): String = when (toolKindOf(call.name)) {
    ToolKind.RUN -> summarizeCommand(firstArg(call.params, "command", "code"))
    else -> {
        val path = firstArg(call.params, "path", "file", "filepath")
        if (path.isNotEmpty()) basename(path) else firstArg(call.params, "query", "url", "pattern")
    }
}

/**
 * 一行摘要（Hermes `summarizeToolRun`）：单条带目标写目标（"已读取 wiring.tsx"），
 * 否则写计数（"已运行 5 条命令"）；运行中的那一类改用进行时。分句以「、」相连。
 */
internal fun summarizeToolRun(calls: List<Msg.ToolCall>, live: Boolean): String {
    val narrating = if (live) calls.firstOrNull { it.status == ToolStatus.RUNNING } ?: calls.lastOrNull() else null
    val liveKind = narrating?.let { toolKindOf(it.name) }
    val byKind = LinkedHashMap<ToolKind, MutableList<Msg.ToolCall>>()
    calls.forEach { byKind.getOrPut(toolKindOf(it.name)) { mutableListOf() }.add(it) }
    val clauses = CatOrder.mapNotNull { kind ->
        val group = byKind[kind] ?: return@mapNotNull null
        val copy = CatCopyOf.getValue(kind)
        val verb = if (kind == liveKind) copy.present else copy.past
        val target = if (group.size == 1) runTarget(group[0]) else ""
        // 一条「已结束」的命令不写命令行（Hermes：命令行只在正等着它的时候占位置）
        if (target.isNotEmpty() && (kind == liveKind || kind != ToolKind.RUN)) "$verb $target"
        else "$verb ${group.size} 个${copy.noun}"
    }
    return clauses.joinToString("、")
}

/**
 * 一次「工具运行」= 一行摘要 +（运行中：一行 ticker；点开：完整行列表）。
 * 单条调用不走这里（它自己就是一行；Hermes `ToolRun` 在 count < 2 时直接早退）。
 */
@Composable
internal fun ToolRunGroup(
    calls: List<Msg.ToolCall>,
    results: List<Msg.ToolResult?>,
    live: Boolean,
    onPermissionDemo: ((Msg.ToolCall) -> Unit)? = null,
) {
    var expanded by remember(calls.size, calls.firstOrNull()?.params) { mutableStateOf(false) }
    val p = toolPalette()
    val summary = summarizeToolRun(calls, live)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ToolScaffoldGap),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
        ) {
            if (live) {
                ShimmerText(summary, toolStyle(ToolFontSize, mono = false).copy(color = p.scaffoldText))
            } else {
                Text(
                    summary,
                    style = toolStyle(ToolFontSize, mono = false).copy(color = p.scaffoldText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Outlined.ArrowRight,
                contentDescription = if (expanded) "收起" else "展开",
                tint = p.scaffoldText,
                modifier = Modifier.size(12.dp).alpha(if (expanded) 0.8f else CaretRestAlpha),
            )
        }

        if (live && !expanded) {
            // 运行中：单行窗口里只露当前那条（Hermes `ToolRunTicker` 的原地翻牌）
            Box(Modifier.fillMaxWidth().height(ToolLineHeightDp).clipToBounds()) {
                ToolRowHeader(
                    call = calls.last(),
                    palette = p,
                    open = false,
                    padded = false,
                    onToggle = null,
                )
            }
        } else if (expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ToolRowGap),
            ) {
                calls.forEachIndexed { i, call ->
                    ToolRow(
                        call = call,
                        result = results.getOrNull(i),
                        onPermissionDemo = onPermissionDemo?.let { demo -> { demo(call) } },
                    )
                }
            }
        }
    }
}

// ───────────────────────── 工具文本（shimmer） ─────────────────────────

@Composable
private fun ShimmerText(text: String, style: TextStyle) {
    val transition = rememberInfiniteTransition(label = "tool-shimmer")
    val a by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "tool-shimmer-alpha",
    )
    Text(
        text,
        style = style.copy(color = style.color.copy(alpha = style.color.alpha * a)),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
