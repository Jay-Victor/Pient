package com.pient.app.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.pient.app.data.PanelMaterial
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquid
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState

/**
 * 玻璃材质基础设施（2026-09-12，输入框设置「输入框材质」的实现层）。
 *
 * 与 Mdcito 同款依赖与装配方式：
 * - `com.kyant.backdrop` 采样「背景捕获层」纹理 → 高斯模糊 + 振动饱和 + 边缘高光 + 投影（磨砂玻璃）；
 * - `io.github.fletchmckee.liquid` 在标记为 liquefiable 的层上做水玻璃流体折射/色散（液态玻璃）。
 *
 * ★ 装配红线（照抄 Mdcito GlassThemeProvisioning 的结论，踩过必崩）：
 *   `content()`（真正使用玻璃的界面）必须与「背景捕获层」**同级**，不能在其内部——
 *   否则 drawBackdrop 的输出会被 layerBackdrop 再次捕获，渲染树自引用 →
 *   RenderNode::prepareTreeImpl 无限递归 → 栈溢出。
 *
 * ★ 效果依赖 RuntimeShader（AGSL），Android 13（API 33）起可用；更低版本自动降级为
 *   「投影 + 描边 + 半透明色调 + 高光」静态玻璃（与 Mdcito 降级路径一致，不会崩，只是没有真实模糊）。
 */
fun isPientGlassSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** 背景捕获层（kyant backdrop）：由 [PientGlassProvisioning] 装配 */
val LocalGlassBackdrop = compositionLocalOf<Backdrop?> { null }

/** 水玻璃状态（fletchmckee liquid）：由 [PientGlassProvisioning] 装配；API < 33 时为 null */
val LocalWaterGlassState = compositionLocalOf<LiquidState?> { null }

/**
 * 玻璃基础设施装配（挂在主题层，PientApp 根部调用一次）。
 *
 * 层级：
 * ```
 * Box(fillMaxSize)
 *  ├─ Box.layerBackdrop(backdrop)   ← 背景捕获层（仅含背景，禁放玻璃组件）
 *  │    └─ Box.liquefiable { backgroundContent() }
 *  └─ content()                     ← 应用内容（玻璃组件在这里采样背景）
 * ```
 *
 * @param backgroundContent 背景层内容（页面底色 + 自定义背景图/视频）
 * @param content 应用内容
 */
@Composable
fun PientGlassProvisioning(
    backgroundContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val backdrop = if (isPientGlassSupported()) rememberLayerBackdrop() else null
    val waterGlassState = if (isPientGlassSupported()) rememberLiquidState() else null

    CompositionLocalProvider(
        LocalGlassBackdrop provides backdrop,
        LocalWaterGlassState provides waterGlassState,
    ) {
        Box(Modifier.fillMaxSize()) {
            // 背景捕获层：layerBackdrop 把这棵子树的绘制结果录成纹理供玻璃采样
            // （API < 33 无 RuntimeShader，玻璃走静态降级路径，不再装配捕获层）
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
            ) {
                if (waterGlassState != null) {
                    Box(Modifier.fillMaxSize().liquefiable(waterGlassState)) { backgroundContent() }
                } else {
                    backgroundContent()
                }
            }
            content()
        }
    }
}

/**
 * 玻璃容器（输入框 / 侧边栏 / 后续卡片类玻璃面板共用）：
 * - [PanelMaterial.DEFAULT] → 走全应用统一的 [PientPanel]（纯色底 + hairline 描边，无玻璃）；
 * - [PanelMaterial.FROSTED] → kyant backdrop 背景模糊（Mdcito 磨砂玻璃卡片同款参数）；
 * - [PanelMaterial.LIQUID] → fletchmckee 水玻璃流体折射（Mdcito 液态玻璃卡片同款参数）。
 *
 * ★ 采样范围（2026-09-12 修「输入框下像有遮罩、内容滑过不透」）：
 * 玻璃的观感取决于「它采样到了什么」。只采样主题背景层时，页面底色是纯色的场合
 * 玻璃 ≈ 一块纯色板（看着就是遮罩）；Operit 的输入栏是**覆盖在聊天内容之上**的浮层，
 * 因此这里同时采样两路 backdrop：[extraBackdrop]（输入栏背后的实时内容，由调用方在
 * 内容层上 layerBackdrop 录制）+ 主题背景层（[LocalGlassBackdrop]），用
 * rememberCombinedBackdrop 合成 → 内容滑过输入框时能从玻璃里透出模糊的内容。
 *
 * [floating] 用于贴身程度相关的参数（模糊半径 / 叠加浓度），口径对齐 Operit
 * ClassicChatInputSection：模糊 悬浮 16dp / 贴底 20dp，叠加 磨砂 0.06/0.10、液态 0.04/0.08。
 */
@Composable
fun PientGlassSurface(
    material: PanelMaterial,
    shape: Shape,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    floating: Boolean = false,
    /** 「简约」材质透明度 0..100（100 = 完全透明；口径对齐 Mdcito 卡片透明度 alpha = 1 - t/100） */
    transparency: Float = 0f,
    /** 「磨砂玻璃」材质纹理强度 0..300（Mdcito：模糊 10 + 20×因子、叠加浓度 因子×0.30；默认 50） */
    frostIntensity: Float = 50f,
    extraBackdrop: Backdrop? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    if (material == PanelMaterial.DEFAULT) {
        PientPanel(
            modifier = modifier,
            shape = shape,
            tint = containerColor.copy(
                alpha = 1f - (transparency / 100f).coerceIn(0f, 1f),
            ),
            content = content,
        )
        return
    }

    // 两路 backdrop 合成：主题背景层（页面底色/自定义背景）+ 背后的实时内容
    val baseBackdrop = LocalGlassBackdrop.current
    val backdrop = when {
        baseBackdrop != null && extraBackdrop != null ->
            rememberCombinedBackdrop(baseBackdrop, extraBackdrop)
        else -> extraBackdrop ?: baseBackdrop
    }

    val isLightGlass = containerColor.luminance() >= 0.5f
    val glassModifier = if (material == PanelMaterial.FROSTED) {
        // 纹理强度（Mdcito）：0..300 → 0..1；模糊 10dp + 20dp×因子、叠加浓度 0.30×因子
        val intensityFactor = (frostIntensity / 300f).coerceIn(0f, 1f)
        Modifier.frostedGlass(
            backdrop = backdrop,
            shape = shape,
            containerColor = containerColor,
            isLightGlass = isLightGlass,
            shadowElevation = if (floating) 10.dp else 14.dp,
            blurRadius = (10f + 20f * intensityFactor).dp,
            overlayAlphaBoost = intensityFactor * 0.30f,
        )
    } else {
        Modifier.liquidGlass(
            shape = shape,
            containerColor = containerColor,
            isLightGlass = isLightGlass,
            shadowElevation = if (floating) 10.dp else 14.dp,
            overlayAlphaBoost = if (floating) 0.04f else 0.08f,
        )
    }

    // 不再额外叠暗色可读性增强层（Mdcito 卡片有、Operit 输入栏没有）——
    // 多一层 scrim 正是用户报的「输入框下像垫了一层遮罩」，观感以真实玻璃为准。
    Box(modifier.then(glassModifier)) {
        content()
    }
}

// ─────────────────────────────────────────────────────────────
// 磨砂玻璃：kyant backdrop（enableLens = false，只模糊不折射）
// ─────────────────────────────────────────────────────────────
@Composable
private fun Modifier.frostedGlass(
    backdrop: Backdrop?,
    shape: Shape,
    containerColor: Color,
    isLightGlass: Boolean,
    shadowElevation: Dp,
    blurRadius: Dp,
    overlayAlphaBoost: Float,
): Modifier {
    val bd = if (isPientGlassSupported()) backdrop else null
    val shadowColor = Color.Black.copy(alpha = if (isLightGlass) 0.10f else 0.18f)
    val borderColor = Color.White.copy(alpha = if (isLightGlass) 0.28f else 0.16f)
    val gloss = Color.White.copy(alpha = if (isLightGlass) 0.12f else 0.06f)

    // 降级路径：API < 33 或未装配捕获层 → 投影 + 描边 + 半透明色调 + 高光
    if (bd == null) {
        val tint = containerColor.copy(alpha = if (isLightGlass) 0.16f else 0.24f)
        return this
            .shadow(
                elevation = shadowElevation.coerceAtLeast(10.dp),
                shape = shape,
                clip = false,
                ambientColor = shadowColor,
                spotColor = shadowColor,
            )
            .clip(shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .background(color = tint, shape = shape)
            .drawWithContent {
                drawContent()
                drawRect(gloss)
            }
    }

    val surfaceTint =
        containerColor.copy(alpha = (if (isLightGlass) 0.16f else 0.23f) + overlayAlphaBoost)
            .let { it.copy(alpha = it.alpha.coerceIn(0f, 0.48f)) }

    return this
        .clip(shape)
        .drawBackdrop(
            backdrop = bd,
            shape = { shape },
            effects = {
                vibrancy()
                blur(blurRadius.toPx())
                // 磨砂玻璃：不开透镜折射（enableLens = false）
            },
            highlight = {
                Highlight(
                    width = 0.42.dp,
                    blurRadius = 0.42.dp * 2.4f,
                    alpha = if (isLightGlass) 0.62f else 0.50f,
                )
            },
            shadow = {
                Shadow(radius = shadowElevation.coerceAtLeast(12.dp), color = shadowColor)
            },
            onDrawSurface = { drawRect(surfaceTint) },
        )
}

// ─────────────────────────────────────────────────────────────
// 液态玻璃：fletchmckee liquid 水玻璃（流体折射 + 色散）
// ─────────────────────────────────────────────────────────────
@Composable
private fun Modifier.liquidGlass(
    shape: Shape,
    containerColor: Color,
    isLightGlass: Boolean,
    shadowElevation: Dp,
    overlayAlphaBoost: Float,
): Modifier {
    // 水玻璃不消费 backdrop：它采样所有被 Modifier.liquefiable(state) 标记的层
    // （主题背景层 + 调用方标记的「输入栏背后内容」层，见 PientGlassProvisioning / ChatScreen）
    val state = if (isPientGlassSupported()) LocalWaterGlassState.current else null
    val shadowColor = Color.Black.copy(alpha = if (isLightGlass) 0.10f else 0.18f)
    val borderColor = Color.White.copy(alpha = if (isLightGlass) 0.18f else 0.10f)
    val gloss = Color.White.copy(alpha = if (isLightGlass) 0.10f else 0.05f)

    // 降级路径：API < 33 或未装配水玻璃状态 → 静态玻璃（同磨砂降级口径）
    if (state == null) {
        val tint = containerColor.copy(alpha = if (isLightGlass) 0.14f else 0.22f)
        return this
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                clip = false,
                ambientColor = shadowColor,
                spotColor = shadowColor,
            )
            .clip(shape)
            .border(width = 0.7.dp, color = borderColor, shape = shape)
            .background(color = tint, shape = shape)
            .drawWithContent {
                drawContent()
                drawRect(gloss)
            }
    }

    val tint = containerColor.copy(alpha = (if (isLightGlass) 0.09f else 0.16f) + overlayAlphaBoost)
        .let { it.copy(alpha = it.alpha.coerceIn(0f, 0.56f)) }

    return this
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = shadowColor,
            spotColor = shadowColor,
        )
        .clip(shape)
        .border(width = 0.7.dp, color = borderColor, shape = shape)
        .liquid(state) {
            this.shape = shape
            this.frost = if (isLightGlass) 6.dp else 8.dp
            this.curve = if (isLightGlass) 0.40f else 0.30f
            this.refraction = if (isLightGlass) 0.12f else 0.09f
            this.dispersion = if (isLightGlass) 0.18f else 0.13f
            this.saturation = if (isLightGlass) 0.40f else 0.32f
            this.contrast = if (isLightGlass) 1.22f else 1.40f
            this.tint = tint
        }
}
