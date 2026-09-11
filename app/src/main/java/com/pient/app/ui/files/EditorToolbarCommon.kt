package com.pient.app.ui.files

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pient.app.ui.theme.MonoFont
import com.pient.app.ui.theme.MonoNoLigatures
import com.pient.app.ui.theme.PientPanel

/**
 * 文件预览页底部工具栏的共用外壳与按键（2026-09-11 抽出）：
 * - 外壳 [EditorToolbarContainer]：markdown 格式工具栏与代码符号工具栏**同一份实现**
 *   （顶圆角 20dp、0.5dp 顶部分隔线、触摸屏 64dp / 平板 56dp 高、IME 随键盘上移）
 * - 按键 [EditorToolbarButton] / 分组竖线 [EditorToolbarDivider]
 *
 * 规格来源 `Refences/Mdcito-1.2.0`（`ui/editor/EditorToolbar.kt`）逐值对齐后按用户反馈收窄：
 * 按键 32dp（小屏；Mdcito 原值 44dp → 2026-09-11 先收到 40dp，用户仍嫌「按键之间的间距太大」再收到 32dp）/
 * 平板 30dp、8dp 圆角、按下缩放 0.85（tween 100ms）、图标 20dp（平板 18dp）、文字键 13sp、
 * 分组竖线 1dp × 24dp（outline 30%、左右各 2dp）。
 *
 * 相对 Mdcito 的差异：容器用 PientPanel（Pient 统一材质：纯色面板底 + 1dp 描边，不用阴影）
 * 而非 Mdcito 的 Surface 阴影。
 */

/** 工具栏高度（触摸屏 64dp / 平板 56dp，Mdcito 同口径；FAB 抬升量按此计算） */
@Composable
internal fun editorToolbarHeight(): Dp =
    if (LocalConfiguration.current.smallestScreenWidthDp < 600) 64.dp else 56.dp

/**
 * 同种符号串的向心位移（见 [EditorToolbarButton] 的 `compact`）：
 * 成对键（`()` `[]` `{}` `<>`）两枚各向对内侧移 3.5dp，配合键宽收窄（32→18dp），
 * 使同种符号之间明显贴紧（字形间距 32dp → 11dp），而异种符号之间/分组竖线两侧的距离保持不变。
 */
internal val EditorToolbarPairShift: Dp = 3.5.dp

/**
 * 工具栏外壳：面板底 + 顶部分隔线 + 单行按键区（按键横排、由调用方决定是否横向滚动）。
 * `navigationBarsPadding` → `imePadding` 的顺序与聊天页输入栏 dock 一致：IME 抬起时整条工具栏
 * 随键盘上移（面板底色覆盖到屏底，圆角顶边贴在键盘上沿）。
 * 使用本外壳的编辑区必须传 `applyImePadding = false`，避免编辑器再叠一份 IME 留白。
 */
@Composable
internal fun EditorToolbarContainer(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val isTouch = LocalConfiguration.current.smallestScreenWidthDp < 600
    PientPanel(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().imePadding(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column {
            // 顶部 0.5dp 分隔线（Mdcito：outline 10%，压在面板上沿）
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(editorToolbarHeight())
                    .padding(horizontal = 8.dp, vertical = if (isTouch) 10.dp else 6.dp),
                content = content,
            )
        }
    }
}

/**
 * 工具栏按键：图标键（[icon]）或文字键（[text]），整块可点（32dp / 平板 30dp）、按下缩放 0.85。
 * [monospace] = 文字键用等宽字体（代码符号键用；markdown 的 H1 / B I 沿用正文字体）。
 * [compact] = 同种符号键（如 `(`/`)`）：键宽收窄 14dp（32→18dp，平板 30→16dp）。
 * [glyphShift] = 字形横向位移（同种符号成对时向心内移 [EditorToolbarPairShift]，视觉上贴成一对）。
 *
 * 成对键的间距数学（务必守恒，否则「其余间距」会被静默带窄）：设标准键宽 B、成对键宽 W、向心位移 δ，
 * 则 同种符号之间 = W − 2δ（越小越紧）、异种符号之间 = (B − W)/2 − 2δ + ... + B/2 —— 要求恒等于 B，
 * 即需要串尾补偿 = (B − W)/2 − 2δ ≥ 0。当前 W = B − 14dp、δ = 3.5dp 时补偿恰好为 0：
 * (32−18)/2 − 7 = 0 ✓（平板 (30−16)/2 − 7 = 0 ✓）。**想更紧只能同时收窄 W**——
 * 上限是 同种符号之间 = (3W − B)/2（W 越小越紧），δ 单独加大到 (B−W)/2 以上就会压缩异种间距。
 */
@Composable
internal fun EditorToolbarButton(
    icon: ImageVector? = null,
    text: String? = null,
    desc: String? = null,
    boldText: Boolean = false,
    monospace: Boolean = false,
    compact: Boolean = false,
    glyphShift: Dp = 0.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val isTouch = LocalConfiguration.current.smallestScreenWidthDp < 600
    val btnSize = if (isTouch) 32.dp else 30.dp
    val btnWidth = if (compact) btnSize - 14.dp else btnSize
    val iconSize = if (isTouch) 20.dp else 18.dp
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = tween(100),
        label = "editor-toolbar-btn",
    )
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    }

    Box(
        modifier = Modifier
            .size(width = btnWidth, height = btnSize)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (text != null) {
            Text(
                text,
                modifier = Modifier.offset(x = glyphShift),
                style = MaterialTheme.typography.labelMedium.copy(
                    // 符号键用等宽字体时同样关闭连字：`!=` 键上要显示 `!=` 本身，不能显示成 ≠
                    fontFeatureSettings = if (monospace) MonoNoLigatures else null,
                ),
                fontSize = if (boldText) 11.sp else 13.sp,
                fontWeight = if (boldText) FontWeight.Bold else FontWeight.Normal,
                fontFamily = if (monospace) MonoFont else null,
                textAlign = TextAlign.Center,
                color = tint,
            )
        } else if (icon != null) {
            Icon(icon, desc, tint = tint, modifier = Modifier.size(iconSize).offset(x = glyphShift))
        }
    }
}

/** 分组竖线（Mdcito ToolbarDivider 1dp × 24dp / outline 30%；左右内边距 4dp→2dp = 按键间距收窄） */
@Composable
internal fun EditorToolbarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 8.dp)
            .size(width = 1.dp, height = 24.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
    )
}
