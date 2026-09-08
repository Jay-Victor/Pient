package com.pient.app.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pient.app.data.ChatState
import com.pient.app.data.Msg
import com.pient.app.data.Panel
import com.pient.app.data.SessionTreeNode
import com.pient.app.ui.components.MarkdownText
import com.pient.app.ui.theme.PientPanel
import kotlin.math.min

/**
 * 会话内分支画布（P12 /tree，内嵌聊天页顶栏下方区域，与终端/文件页同款承载方式；
 * 2026-09-02《Pient 分支功能设计》v0.1 §3 + 2026-09-02 用户视觉重设计要求）：
 * - 树**从左向右发散**：根在最左，深度沿 x 轴展开，分支沿 y 轴排布（叶子顺序计数、
 *   内部节点取子树行中心）；
 * - 节点卡片左右边框带**圆点**作为连线端点（左=入点，右=出点）；
 * - 连线为**三次贝塞尔曲线**（父右缘出点 → 子左缘入点，自然弯曲），
 *   活跃路径 primary、其余 outlineVariant；
 * - 单指平移、双指缩放（0.6×–1.5×），初始视口自动适配活跃分支；
 * - 右下三枚 FAB：查看详情 / 从此处分支 / 切换分支（仅叶子）；
 *   后两者 = navigateToNode 后切回消息区（顶栏分支键再点切换回聊天）。
 */
@Composable
fun TreeCanvasPanel(chatState: ChatState) {
    val tree = chatState.branchTree
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // 布局常量（px，画布内容坐标）
    val cardW = with(density) { 248.dp.toPx() }
    val cardH = with(density) { 72.dp.toPx() }
    val colStep = with(density) { 304.dp.toPx() }
    val rowStep = with(density) { 120.dp.toPx() }
    val pad = with(density) { 28.dp.toPx() }

    // 水平树布局：x = 深度 × colStep；y = 行号 × rowStep（叶子顺序计数，内部节点取子树中心）
    val layout = remember(tree) {
        val nodes = mutableListOf<NodeLayout>()
        var order = 0
        var nextRow = 0
        fun assign(n: SessionTreeNode, depth: Int): Float {
            // 进节点即取序号快照（递归返回后共享计数器已递增，不能迟取）
            val myOrder = ++order
            if (n.children.isEmpty()) {
                val row = nextRow.toFloat()
                nextRow += 1
                nodes += NodeLayout(n, depth, row, myOrder, null)
                return row
            }
            val childRows = n.children.map { assign(it, depth + 1) }
            val row = (childRows.first() + childRows.last()) / 2f
            nodes += NodeLayout(n, depth, row, myOrder, null)
            return row
        }
        if (tree != null) assign(tree, 0)
        // parent 引用事后按路径补（assign 时父节点尚未创建）
        fun assignParent(n: SessionTreeNode, parent: NodeLayout?) {
            val nl = nodes.firstOrNull { it.node === n }
            if (nl != null) nl.parent = parent
            n.children.forEach { assignParent(it, nl) }
        }
        if (tree != null) assignParent(tree, null)
        nodes
    }

    val maxDepth = layout.maxOfOrNull { it.depth } ?: 0
    val maxRow = layout.maxOfOrNull { it.row.toInt() } ?: 0
    val contentW = pad * 2 + maxDepth * colStep + cardW
    val contentH = pad * 2 + maxRow * rowStep + cardH

    fun xOf(depth: Int) = pad + depth * colStep
    fun yOf(row: Float) = pad + row * rowStep

    var selectedId by remember { mutableStateOf<String?>(null) }
    var detailOpen by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var fitted by remember { mutableStateOf(false) }

    // pointerInput(Unit) 闭包内读状态会拿到首帧快照（不随重组更新），
    // 手势处理一律走 rememberUpdatedState 的最新值。
    val panNow by rememberUpdatedState(pan)
    val zoomNow by rememberUpdatedState(zoom)
    val selectedNow by rememberUpdatedState(selectedId)

    // 初始视口：平移 + 缩放使活跃路径完整可见（一次）
    LaunchedEffect(viewport, tree) {
        if (fitted || viewport == IntSize.Zero || layout.isEmpty()) return@LaunchedEffect
        val actives = layout.filter { it.node.active }
        val minX = actives.minOf { xOf(it.depth) }
        val maxX = actives.maxOf { xOf(it.depth) } + cardW
        val minY = actives.minOf { yOf(it.row) }
        val maxY = actives.maxOf { yOf(it.row) } + cardH
        val margin = with(density) { 32.dp.toPx() }
        val bbW = maxX - minX
        val bbH = maxY - minY
        zoom = min((viewport.width - margin * 2) / bbW, (viewport.height - margin * 2) / bbH)
            .coerceIn(0.6f, 1.5f)
        pan = Offset(
            (viewport.width - bbW * zoom) / 2f - minX * zoom,
            (viewport.height - bbH * zoom) / 2f - minY * zoom,
        )
        fitted = true
    }

    val selectedLayout = layout.firstOrNull { it.node.id == selectedId }
    val selectedNode = selectedLayout?.node

    Box(Modifier.fillMaxSize()) {
        // ── 画布 ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { viewport = it }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val contentPos = Offset(
                            (pos.x - panNow.x) / zoomNow,
                            (pos.y - panNow.y) / zoomNow,
                        )
                        val hit = layout.firstOrNull { nl ->
                            val left = xOf(nl.depth)
                            val top = yOf(nl.row)
                            contentPos.x in left..(left + cardW) &&
                                contentPos.y in top..(top + cardH)
                        }
                        selectedId = if (hit == null) null
                        else if (selectedNow == hit.node.id) null else hit.node.id
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        zoom = (zoomNow * zoomChange).coerceIn(0.6f, 1.5f)
                        pan = panNow + panChange
                    }
                },
        ) {
            if (tree == null || layout.isEmpty()) {
                Text(
                    "当前会话暂无分支树",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(
                            width = with(density) { contentW.toDp() },
                            height = with(density) { contentH.toDp() },
                        )
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                ) {
                    // 连线（三次贝塞尔曲线；活跃路径 primary，其余 outlineVariant）
                    // 颜色在 Composable 上下文取好再传入 DrawScope（Canvas lambda 非 @Composable）
                    val primary = MaterialTheme.colorScheme.primary
                    val faint = MaterialTheme.colorScheme.outlineVariant
                    Canvas(Modifier.fillMaxSize()) {
                        layout.forEach { nl ->
                            val parent = nl.parent ?: return@forEach
                            val start = Offset(
                                xOf(parent.depth) + cardW,
                                yOf(parent.row) + cardH / 2f,
                            )
                            val end = Offset(xOf(nl.depth), yOf(nl.row) + cardH / 2f)
                            val color =
                                if (parent.node.active && nl.node.active) primary else faint
                            val dx = (end.x - start.x) / 2f
                            val path = Path().apply {
                                moveTo(start.x, start.y)
                                cubicTo(
                                    start.x + dx, start.y,
                                    end.x - dx, end.y,
                                    end.x, end.y,
                                )
                            }
                            drawPath(
                                path = path,
                                color = color,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                ),
                            )
                        }
                    }
                    // 节点卡片
                    layout.forEach { nl ->
                        NodeCard(
                            node = nl.node,
                            order = nl.order,
                            selected = selectedId == nl.node.id,
                            hasParent = nl.parent != null,
                            hasChildren = nl.node.children.isNotEmpty(),
                            onClick = {
                                selectedId = if (selectedId == nl.node.id) null else nl.node.id
                            },
                            modifier = Modifier.offset(
                                x = with(density) { xOf(nl.depth).toDp() },
                                y = with(density) { yOf(nl.row).toDp() },
                            ),
                        )
                    }
                }
            }
        }

        // ── 右下三枚悬浮操作按键（自下而上 = 切换分支/从此处分支/查看详情）──
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
        ) {
            TreeFab(
                icon = Icons.Outlined.Visibility,
                desc = "查看节点详情",
                enabled = selectedNode != null,
                onClick = { detailOpen = true },
            )
            TreeFab(
                icon = Icons.Outlined.CallSplit,
                desc = "从此处分支",
                enabled = selectedNode != null,
                onClick = { switchTo(chatState, selectedId) },
            )
            TreeFab(
                icon = Icons.Outlined.AltRoute,
                desc = "切换分支",
                enabled = selectedNode?.children?.isEmpty() == true,
                onClick = { switchTo(chatState, selectedId) },
            )
        }
    }

    // ── 节点详情弹窗卡片（点外关闭；无 × 无底部关闭键——与点外重复的元素不加）──
    if (detailOpen && selectedNode != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim)
                    .clickable(onClick = { detailOpen = false }),
            )
            val userMsg = selectedNode.exchange.firstOrNull() as? Msg.User
            val answer = selectedNode.exchange
                .filterIsInstance<Msg.Assistant>()
                .joinToString("\n\n") { it.markdown }
            PientPanel(
                modifier = Modifier
                    .width(configuration.screenWidthDp.dp - 48.dp)
                    .heightIn(max = (configuration.screenHeightDp * 0.6f).dp)
                    // 卡片自身吞掉点击，避免点卡片内容穿透到点外关闭层
                    .clickable(
                        onClick = {},
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "节点详情",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "#${selectedLayout?.order ?: 0}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (userMsg != null) {
                        Text(
                            userMsg.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(12.dp),
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Text(
                        "AI 回答",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (answer.isNotEmpty()) {
                        MarkdownText(answer, modifier = Modifier.padding(top = 6.dp))
                    } else {
                        Text(
                            "（暂无回答）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 切换到选中节点并切回消息区（FAB2/FAB3 共用，底层 = navigate_tree 封装命令原型） */
private fun switchTo(chatState: ChatState, nodeId: String?) {
    if (nodeId == null) return
    chatState.navigateToNode(nodeId)
    chatState.activePanel = Panel.MESSAGES
}

private class NodeLayout(
    val node: SessionTreeNode,
    val depth: Int,
    val row: Float,
    val order: Int,
    var parent: NodeLayout?,
)

/**
 * 56dp 圆形 FAB（M3 1.4.0 FloatingActionButton 无 enabled 参数，手动实现禁用态）：
 * 2026-09-03 改：未选中节点时 = 蓝底白图案（primary + onPrimary，与文件页 FAB 同款），
 * 选中节点后 = 粉底黑图案（M3 默认 FAB 容器色，保持不变）；禁用态无阴影、点击无效果。
 */
@Composable
private fun TreeFab(
    icon: ImageVector,
    desc: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FloatingActionButton(
        onClick = { if (enabled) onClick() },
        shape = CircleShape,   // 圆形（与文件页 FAB 统一；M3 默认 large 圆角方形）
        containerColor = if (enabled) FloatingActionButtonDefaults.containerColor
        else MaterialTheme.colorScheme.primary,
        // 启用态交给 FAB 默认内容色（M3 1.4.0 无 contentColorFor，Unspecified = 默认行为）
        contentColor = if (enabled) Color.Unspecified
        else MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = if (enabled) 6.dp else 0.dp,
            pressedElevation = if (enabled) 12.dp else 0.dp,
        ),
        modifier = Modifier.size(56.dp),
    ) {
        Icon(icon, desc)
    }
}

/**
 * 节点卡片：左侧入点圆点（非根）/ 右侧出点圆点（有子节点）为连线端点；
 * 活跃路径 = primary 20% 底 + primary 35% 描边；选中 = 纯 primary 1.5dp 描边。
 */
@Composable
private fun NodeCard(
    node: SessionTreeNode,
    order: Int,
    selected: Boolean,
    hasParent: Boolean,
    hasChildren: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (node.active) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    else MaterialTheme.colorScheme.surfaceContainerLow
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        node.active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val portColor = if (selected || node.active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier.size(width = 248.dp, height = 72.dp),
    ) {
        // 分支名标签（卡片上方空隙，取自节点 branchLabel）
        // 注意：必须放上方——放下方会与下一行卡片重叠被遮挡（行距 120dp、
        // 空隙仅 48dp，标签在 +76dp 处会进入下一行卡片区域）
        if (node.branchLabel != null) {
            Text(
                node.branchLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (node.active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 4.dp, y = (-20).dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(bg)
                .border(if (selected) 1.5.dp else 1.dp, borderColor, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                "#$order",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val preview = node.userText.take(40) + if (node.userText.length > 40) "…" else ""
            Text(
                preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // 连线端点圆点（6dp，跨在边框上）：左 = 入点（非根），右 = 出点（有子节点）
        if (hasParent) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-3).dp)
                    .size(6.dp)
                    .background(portColor, CircleShape),
            )
        }
        if (hasChildren) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 3.dp)
                    .size(6.dp)
                    .background(portColor, CircleShape),
            )
        }
    }
}
