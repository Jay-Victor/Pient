package com.pient.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 普通材质面板（用户决策 2026-08-27：全面弃用玻璃拟态，使用正常组件材质）。
 *
 * 风格（设计计划 1.1"扁平容器"支柱）：
 * - 纯色面板底（surfaceContainer 令牌）+ 1dp hairline 边框（outlineVariant）
 * - 圆角统一 12/16dp 两级（M3 shapes）
 * - 不用阴影、不用模糊、无玻璃
 *
 * 应用面：顶栏 / 输入栏 dock / 侧栏抽屉 / 文件树面板 / 浮层卡片 /
 * 弹窗 / 引导卡片 / 终端工具栏 —— 全部容器统一走本组件。
 *
 * ★ 实现注意：单层 Box 结构。不要用「外层 wrap Box + 内层 matchParentSize」
 * 两层结构——外层高度未定时 matchParentSize 子项会塌缩为 0，
 * 导致高度自适应的卡片（权限卡/引导介绍板块等）整体消失。
 */
@Composable
fun PientPanel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    tint: Color = MaterialTheme.colorScheme.surfaceContainer,
    borderAlpha: Float = 0.65f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .background(tint, shape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = borderAlpha),
                shape = shape,
            ),
    ) {
        content()
    }
}
