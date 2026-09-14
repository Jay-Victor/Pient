package com.pient.app.ui.chat

import android.widget.Toast

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ContentCopy
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.pient.app.data.extOf
import com.pient.app.ui.files.fileIcon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
internal const val ScaffoldFade = 0.67f // 脚手架静息透明度（styles.css data-conversation-scaffold）
private const val ScaffoldRestAlpha = ScaffoldFade
internal const val ScaffoldCaretRestAlpha = 0.4f
private const val CaretRestAlpha = ScaffoldCaretRestAlpha

/**
 * 脚手架家族共用件（工具行、run 摘要行、思考标题行都是同一类「安静的一行」，
 * Hermes 用 `scaffold-row.tsx` 统一，避免各处自己挑灰色/字号而漂移）。
 */
@Composable
internal fun scaffoldLabelStyle(): TextStyle =
    TextStyle(fontSize = ToolFontSize, lineHeight = ToolLineHeight)

@Composable
internal fun scaffoldLabelColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f)

@Composable
internal fun scaffoldMetaColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.44f)

/** Hermes `DisclosureCaret`：chevron-right，展开时旋转 90°，150ms 过渡。 */
@Composable
internal fun ScaffoldCaret(open: Boolean, size: Dp = 12.dp) {
    val rotated by animateFloatAsState(
        targetValue = if (open) 90f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "scaffold-caret",
    )
    Icon(
        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = if (open) "收起" else "展开",
        tint = scaffoldLabelColor(),
        modifier = Modifier
            .size(size)
            .rotate(rotated)
            .alpha(if (open) 0.8f else CaretRestAlpha),
    )
}

/** Hermes 会话令牌色（`--ui-base` = 主题前景，各档按百分比混到背景上）。 */
private class ToolPalette(
    val scaffoldText: Color,
    val quinary: Color,
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
        quinary = base.copy(alpha = 0.03f),
        ok = if (isDark) Color(0xFF3FB950) else Color(0xFF1A7F37),
        warn = if (isDark) Color(0xFFFFB224) else Color(0xFFB26A00),
        error = MaterialTheme.colorScheme.error,
        accent = accent,
    )
}

private fun TextStyle.tool(color: Color) = copy(color = color)

/** mono = true 用于正文/预格式段（Hermes 只给 TOOL_SECTION_PRE 加 font-mono）；标题行走界面字体。 */
private fun toolStyle(size: TextUnit, mono: Boolean = true, weight: FontWeight? = null) =
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
    "bash", "terminal" -> ToolKind.RUN
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
    "bash", "terminal" -> if (pending) "正在运行命令" else "已运行命令"
    else -> if (pending) "正在运行 $name" else "已运行 $name"
}

/**
 * read 的行区间标签（Hermes `readFileLineLabel` / `readFileDisplayTarget`）：
 * 优先从结果文本里的 `[Showing lines 120-200 of …]` 取真实区间，否则退回参数 offset/limit
 * （pi 的 offset 是 **1-indexed**，所以直接把参数写进 `L120-200` 不需要换算）。
 */
private fun readLineLabel(call: Msg.ToolCall, output: String?): String {
    Regex("Showing lines (\\d+)-(\\d+) of").find(output.orEmpty())?.let {
        return "L${it.groupValues[1]}-${it.groupValues[2]}"
    }
    val o = argsOf(call.params) ?: return ""
    val offset = o.optInt("offset", -1)
    val limit = o.optInt("limit", -1)
    if (offset <= 0 && limit <= 0) return ""
    if (offset > 0) {
        return if (limit <= 1) "L$offset" else "L$offset-${offset + limit - 1}"
    }
    return if (limit > 1) "共 $limit 行" else ""
}

/** 行标题：优先「动作 + 目标」（Hermes `dynamicTitle` → `actionTarget` / `actionCommand` / `actionQuoted`）。 */
internal fun toolRowTitle(call: Msg.ToolCall): String {
    val pending = call.status == ToolStatus.RUNNING
    val verb = { past: String, present: String -> if (pending) present else past }
    return when (call.name) {
        "bash", "terminal" -> {
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
            else {
                val label = readLineLabel(call, call.detail)
                "${verb("已读取", "正在读取")} ${basename(p)}${if (label.isEmpty()) "" else " $label"}"
            }
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

/**
 * 标题拆成（动作词, 其余）——Hermes 的 `titleAction`：进行中只给**动作词**加 shimmer
 * （`{prefix}<span class="shimmer">{action}</span>{suffix}`），目标名是静态的。
 */
internal fun toolRowTitleParts(call: Msg.ToolCall): Pair<String, String> {
    val full = toolRowTitle(call)
    val verbs = listOf(
        "正在运行", "正在读取", "正在编辑", "正在修补", "正在写入",
        "正在搜索", "正在查找", "正在列出",
    )
    val verb = verbs.firstOrNull { full.startsWith(it) } ?: return full to ""
    return verb to full.removePrefix(verb)
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
        // edit：pi 的结果文本 = "Successfully replaced N block(s) in <path>." → 「N 处替换」
        "edit", "write" -> Regex("replaced (\\d+) block")
            .find(output)?.groupValues?.get(1)?.let { "$it 处替换" }
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
                else Modifier,
            )
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier),
    ) {
        ToolGlyph(call, palette)
        Spacer(Modifier.width(ToolRowGap))
        val style = scaffoldLabelStyle().copy(color = titleColor)
        if (pending) {
            val (action, rest) = toolRowTitleParts(call)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                ShimmerText(action, style)
                if (rest.isNotEmpty()) {
                    Text(rest, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            Text(
                toolRowTitle(call),
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Hermes：文件编辑行在 meta 位显示 **diff 统计**（+N 绿 / −M 红，mono 0.625rem tabular），
        // 其它行显示时长 / 结果计数
        val stats = if (isCardTool(call.name)) diffStats(call.diff) else null
        if (stats != null) {
            Row(Modifier.padding(start = ToolRowGap)) {
                if (stats.first > 0) {
                    Text(
                        "+${stats.first}",
                        style = toolStyle(ToolMetaSize).copy(color = palette.ok),
                    )
                }
                if (stats.second > 0) {
                    Text(
                        "−${stats.second}",
                        style = toolStyle(ToolMetaSize).copy(color = palette.error),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        } else {
            val meta = if (isCardTool(call.name)) countLabel(call, call.detail ?: "")
            // Hermes：非文件编辑行 meta = 计数 + 时长（countLabel 在前，durationLabel 在后）
            else listOfNotNull(countLabel(call, call.detail ?: ""), formatDuration(call.durationMs))
                .joinToString(" · ").ifEmpty { null }
            if (meta != null) {
                Text(
                    meta,
                    style = toolStyle(ToolMetaSize).copy(color = palette.meta),
                    modifier = Modifier.padding(start = ToolRowGap),
                    maxLines = 1,
                )
            }
        }
        if (onToggle != null) {
            Spacer(Modifier.weight(1f))
            ScaffoldCaret(open = open)
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
    "bash", "terminal" -> Icons.Outlined.Terminal
    "grep", "find" -> Icons.Outlined.Search
    "ls" -> Icons.Outlined.Folder
    else -> Icons.Outlined.Build
}

// ───────────────────────── 正文 ─────────────────────────

/** grep 命中的一行：`path:line:text`。 */
private data class GrepHit(val file: String, val line: String, val text: String)

/** 解析 pi grep 的结果文本（`path:line:text`，逐行）。 */
private fun parseGrepHits(output: String?): List<GrepHit> {
    if (output.isNullOrBlank()) return emptyList()
    return output.lines().mapNotNull { raw ->
        val m = Regex("^(.+?):(\\d+):(.*)$").find(raw) ?: return@mapNotNull null
        GrepHit(basename(m.groupValues[1]), m.groupValues[2], m.groupValues[3].trim())
    }
}

/** diff 的 +/- 统计（Hermes 文件卡 `+N −M`）。 */
private fun diffStats(diff: String?): Pair<Int, Int>? {
    if (diff.isNullOrBlank()) return null
    var add = 0
    var del = 0
    diff.lines().forEach { l ->
        when {
            l.startsWith("+++") || l.startsWith("---") -> Unit
            l.startsWith("+") -> add++
            l.startsWith("-") -> del++
        }
    }
    return if (add == 0 && del == 0) null else add to del
}

/** android_shell 的通道标签（结果文本以 `[standard]`/`[shizuku]`/`[su]` 开头）。 */
private fun shellChannelOf(output: String?): String? =
    Regex("^\\[(standard|shizuku|su)\\]", RegexOption.MULTILINE).find(output.orEmpty())
        ?.groupValues?.get(1)

/** 正文：先出命令块（有 command 参数时），再按工具给对应的段（命中列表 / 文件列表 / diff / 输出）。 */
@Composable
private fun ToolBody(call: Msg.ToolCall, output: String?, palette: ToolPalette) {
    val command = firstArg(call.params, "command")
    if (command.isNotEmpty()) {
        ToolCommandBlock(
            command = command,
            exitCode = exitCodeOf(output),
            channel = if (call.name == "android_shell") shellChannelOf(output) else null,
            palette = palette,
        )
    }
    if (call.status == ToolStatus.RUNNING) return
    val body = stripExitNote(output?.trim().orEmpty())

    // 工具专属视图（适配 Pient 的 pi 工具产出形态，Hermes 的对应视图见注释）
    when (call.name) {
        // edit：Hermes 的文件卡 = diff 面板（`FileDiffPanel`，max-h 12rem、行左 2px 边框、+/- 语义色）
        "edit" -> {
            if (!call.diff.isNullOrBlank()) {
                DiffPanel(diff = call.diff, palette = palette)
                return
            }
        }
        // read：pi 的输出是「行号|正文」逐行 → 行号槽 + 正文的代码块，
        // 不再把 `1|xxx` 原样当文本贴出来（2026-09-14 重设计，适配 pi 的产出格式）
        "read" -> {
            val lines = parseReadLines(body)
            if (lines.isNotEmpty()) {
                ReadLinesBlock(lines = lines, palette = palette)
                return
            }
        }
        // find / ls：输出是纯路径/条目清单 → 文件清单（类型图标 + 名称），
        // 图标走文件树同一函数 `fileIcon`（同一语义只有一份实现）
        "find" -> {
            val entries = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (entries.isNotEmpty()) {
                PathEntryList(entries = entries, palette = palette)
                return
            }
        }
        "ls" -> {
            val entries = body.lines().map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("（") }
            if (entries.isNotEmpty()) {
                PathEntryList(entries = entries, palette = palette)
                return
            }
        }
        // write：把**写进去的内容**摊开给用户看（文件名已在标题里），结果确认留给标题 meta
        "write" -> {
            val content = firstArg(call.params, "content")
            if (content.isNotEmpty()) {
                ToolSectionBlock(
                    label = "写入内容",
                    text = clipPreview(content),
                    palette = palette,
                    error = call.status == ToolStatus.FAILED,
                )
                return
            }
        }
        // grep：Hermes 的 SearchResultsList（命中 → 文件:行 + 摘要），不是一坨原始文本
        "grep" -> {
            val hits = parseGrepHits(body)
            if (hits.isNotEmpty()) {
                GrepHitsList(hits = hits, palette = palette)
                return
            }
        }
    }

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
        text = body,
        palette = palette,
        error = call.status == ToolStatus.FAILED,
    )
}

/**
 * read 输出 → (行号?, 正文) 列表。两种形态都认：`N|正文`（宿主 pi 与应用内工具同形）与裸文本行
 * （没带行号就按出现顺序补号），「[输出已截断]」这类提示行标成无行号。
 * 渲染不依赖产出侧格式 —— 所以两条路径（宿主 / 直连）共用同一份视图。
 */
private fun parseReadLines(output: String): List<Pair<Int?, String>> {
    if (output.isBlank()) return emptyList()
    val numbered = Regex("^\\s*(\\d+)\\|(.*)$")
    val out = ArrayList<Pair<Int?, String>>()
    for ((i, raw) in output.lines().withIndex()) {
        val m = numbered.find(raw)
        when {
            m != null -> out.add(m.groupValues[1].toIntOrNull() to m.groupValues[2])
            raw.startsWith("[") -> out.add(null to raw)   // 截断提示等（无行号）
            else -> out.add(i + 1 to raw)
        }
    }
    return out.take(400)
}

/** 长内容预览上限（写入内容可能很大）：超过 200 行或 8KB 截断并标注 */
private fun clipPreview(text: String, maxLines: Int = 200, maxBytes: Int = 8 * 1024): String {
    val lines = text.lines()
    var out = if (lines.size > maxLines) lines.take(maxLines).joinToString("\n") else text
    if (out.toByteArray().size > maxBytes) {
        out = out.toByteArray().copyOf(maxBytes).toString(Charsets.UTF_8)
    }
    return if (out.length < text.length) "$out\n…（内容过长，已截断）" else out
}

/**
 * read 结果的行号块：左侧行号槽（右对齐、弱化）+ 正文（mono、横向滚动）。
 * 行号口径与文件预览页一致（行号字号 = 正文 ×0.82）。
 */
@Composable
private fun ReadLinesBlock(lines: List<Pair<Int?, String>>, palette: ToolPalette) {
    val gutter = (lines.mapNotNull { it.first }.maxOrNull()?.toString()?.length ?: 2).coerceAtLeast(2)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
            .heightIn(max = 192.dp)
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState()),
    ) {
        for ((no, text) in lines) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    no?.toString().orEmpty().padStart(gutter),
                    style = toolStyle(ToolPreSize * 0.82f).copy(color = palette.meta),
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text,
                    style = toolStyle(ToolPreSize).copy(color = palette.secondary),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 路径清单（find / ls）：类型图标 + 名称（目录带 `/` 后缀与文件夹图标）。
 * 图标复用文件树的 [fileIcon]（同一语义只有一份实现）。
 */
@Composable
private fun PathEntryList(entries: List<String>, palette: ToolPalette) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
            .heightIn(max = 192.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (entry in entries.take(60)) {
            val isDir = entry.endsWith("/")
            val name = entry.trimEnd('/').substringAfterLast('/').ifEmpty { entry }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    if (isDir) Icons.Outlined.Folder else fileIcon(extOf(name)),
                    contentDescription = null,
                    tint = palette.meta,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    entry,
                    style = toolStyle(ToolPreSize).copy(color = palette.secondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp).weight(1f),
                )
            }
        }
        if (entries.size > 60) {
            Text(
                "另有 ${entries.size - 60} 项…",
                style = toolStyle(ToolSectionLabelSize).copy(color = palette.meta),
            )
        }
    }
}

/**
 * grep 命中列表（Hermes `SearchResultsList`）：每条 = `文件:行`（次亮）+ 摘要（弱化，最多 2 行）。
 * pi 的 grep 输出就是 `path:line:text` 逐行，正好是这套结构。
 */
@Composable
private fun GrepHitsList(hits: List<GrepHit>, palette: ToolPalette) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        hits.take(20).forEach { hit ->
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "${hit.file}:${hit.line}",
                    style = toolStyle(ToolPreSize, weight = FontWeight.Medium).copy(color = palette.secondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hit.text.isNotEmpty()) {
                    Text(
                        hit.text,
                        style = toolStyle(ToolPreSize).copy(color = palette.meta),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (hits.size > 20) {
            Text(
                "另有 ${hits.size - 20} 处…",
                style = toolStyle(ToolSectionLabelSize).copy(color = palette.meta),
            )
        }
    }
}

/**
 * diff 面板（Hermes `FileDiffPanel`：`max-h-[12rem]`(192dp) 滚动、mono 0.7rem、
 * 每行 `border-l-2 px-2.5 py-px`、+/- 用 emerald/rose 语义色，hunk/文件头弱化）。
 */
@Composable
private fun DiffPanel(diff: String, palette: ToolPalette) {
    val lines = diff.lines().filter { it.isNotEmpty() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
            .heightIn(max = 192.dp)   // Hermes max-h-[12rem]
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState()),
    ) {
        lines.take(400).forEach { line ->
            val (bar, tint, textColor) = when {
                line.startsWith("@@") -> Triple(palette.stroke, Color.Transparent, palette.meta)
                line.startsWith("+++") || line.startsWith("---") -> Triple(palette.stroke, Color.Transparent, palette.meta)
                line.startsWith("+") -> Triple(palette.ok, palette.ok.copy(alpha = 0.10f), palette.ok)
                line.startsWith("-") -> Triple(palette.error, palette.error.copy(alpha = 0.10f), palette.error)
                else -> Triple(Color.Transparent, Color.Transparent, palette.secondary)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tint)
                    .drawBehind {
                        // border-l-2：2dp 左边条
                        drawRect(bar, size = Size(2.dp.toPx(), size.height))
                    },
            ) {
                Text(
                    line,
                    style = toolStyle(ToolPreSize).copy(color = textColor),
                    maxLines = 1,
                    modifier = Modifier.padding(start = 10.dp, top = 1.dp, bottom = 1.dp),
                )
            }
        }
    }
}

private fun sectionLabelFor(name: String) = when (name) {
    "read" -> "内容"
    "grep", "find", "ls" -> "结果"
    else -> "输出"
}

/** `$ 命令` + `exit N` 徽标 +（android_shell）通道徽标（Hermes `TerminalTranscript` 几何/配色）。 */
@Composable
private fun ToolCommandBlock(command: String, exitCode: Int?, channel: String?, palette: ToolPalette) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.stroke, RoundedCornerShape(4.dp))
            .background(palette.quinary, RoundedCornerShape(4.dp))
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
        CopyButton(text = command, label = "复制命令")
        // android_shell 的通道徽标（Pient 专有工具：标准 / ADB(Shizuku) / Root 三档，Hermes 无此类工具）
        if (channel != null) {
            Text(
                channel,
                style = toolStyle(9.6.sp).copy(color = palette.accent),
                modifier = Modifier
                    .padding(start = ToolRowGap)
                    .background(palette.accent.copy(alpha = 0.10f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                label,
                style = toolStyle(ToolSectionLabelSize, mono = false).copy(
                    color = palette.meta,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = ToolSectionTracking,
                ),
            )
            Spacer(Modifier.weight(1f))
            CopyButton(text = text, label = "复制输出")
        }
        Text(
            text,
            style = toolStyle(ToolPreSize).copy(color = if (error) palette.error else palette.secondary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 8.dp, end = 8.dp, bottom = 6.dp)
                .heightIn(max = 80.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()),
        )
    }
}

/**
 * 段内复制键（Hermes `CopyButton appearance="inline"`：正文右上角的小图标键、默认很淡）。
 * 触摸端没有 hover → 常驻显示（静息 0.4 透明度，Hermes 的 `--disclosure-caret-rest` 同档）。
 */
@Composable
private fun CopyButton(text: String, label: String) {
    if (text.isBlank()) return
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Icon(
        Icons.Outlined.ContentCopy,
        contentDescription = label,
        tint = scaffoldMetaColor(),
        modifier = Modifier
            .size(16.dp)
            .alpha(CaretRestAlpha)
            .clickable {
                clipboard.setText(AnnotatedString(text))
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            },
    )
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
            ScaffoldCaret(open = open, size = 10.dp)
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
                ShimmerText(summary, scaffoldLabelStyle().copy(color = p.scaffoldText))
            } else {
                Text(
                    summary,
                    style = scaffoldLabelStyle().copy(color = p.scaffoldText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            ScaffoldCaret(open = expanded)
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
