package com.pient.app.ui.startup

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pient.app.R
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 开屏加载页（2026-09-12）。
 *
 * 背景：冷启动时系统启动画面（starting window）会一直挂到应用画出第一帧，而此前
 * 首屏数据（state.json / AI 配置 / 价格表 / 用量台账）是在**组合期同步读盘**的，
 * 主线程被占住 → 实测启动画面停留 3.6s；且 `android:windowBackground` 写死深色
 * #0D1117，亮色主题用户看到的就是一记「黑频」。
 *
 * 参考实现（Hermes 桌面端源码，`%LOCALAPPDATA%\hermes\hermes-agent\apps\desktop\src`）：
 * - 覆盖层形态 / 主题底色 / 退出编排：`components/gateway-connecting-overlay.tsx`
 *   （全屏底色 + 居中动画元素；退出 = 内容淡出下移 360ms → 停留 300ms → 覆盖层淡出 520ms）
 * - 加载动画本体：`components/ui/loader.tsx` 的 `Loader`，type = rose-curve（逐值移植：
 *   78 粒子、5400ms 走一圈、4600ms 脉冲、尾长 0.32、28000ms 旋转、路径底纹 opacity 0.1、
 *   PageLoader 口径 pathSteps=220 / strokeScale=0.72 / size-10）
 * - 解码文字：`components/ui/decode-text.tsx`（45ms 一拍、每拍解半个字符、全解后停 16 拍再循环、
 *   字符集 `/\\|-_=+<>~:*`、前 prefix 个字符不解码、光标方块 1s 硬闪）
 *   品牌字标用法同 `components/pane-shell/tree/renderer/tree-group.tsx`
 *   （`<DecodeText text="HERMES" cursor prefix={1} />`），此处同款写作 "PIENT"。
 *
 * 与桌面端的有意差异（数值已在注释里标注）：
 * - 覆盖层退出编排缩短为「内容淡出 300ms → 覆盖层淡出 300ms」并去掉 300ms 停留：
 *   桌面端启动以秒计，移动端首屏数据常在数百毫秒内就绪，照搬 1.18s 会像卡住。
 * - 底色用 `@color/pient_splash_bg`（与系统启动画面**同一个 day/night 资源**），
 *   使「系统启动画面 → 本加载页」无缝衔接；数据就绪后再淡出到应用主题界面。
 */

private const val TWO_PI = 6.2831855f

/** Hermes `Loader` rose-curve 参数（components/ui/loader.tsx + PageLoader 口径）。 */
private object RoseCurve {
    const val K = 5
    const val DURATION_MS = 5400f
    const val PULSE_DURATION_MS = 4600f
    const val ROTATION_DURATION_MS = 28000f
    const val PARTICLE_COUNT = 78
    const val STROKE_WIDTH = 4.5f
    const val TRAIL_SPAN = 0.32f
    const val PATH_STEPS = 220
    const val STROKE_SCALE = 0.72f
    const val PATH_OPACITY = 0.1f
}

/** 解码文字参数（components/ui/decode-text.tsx）。 */
private const val TICK_MS = 45L
private const val HOLD_TICKS = 16
private const val SCRAMBLE_CHARS = "/\\|-_=+<>~:*"

/** 退出编排（移动端缩短版；括号内为 Hermes 桌面端原值）。 */
private const val CONTENT_OUT_MS = 300      // 桌面端 360
private const val OVERLAY_OUT_MS = 300      // 桌面端 520 + 300ms 停留

private enum class StartupPhase { LIVE, CONTENT_OUT, OVERLAY_OUT, GONE }

/**
 * 冷启动加载覆盖层：首屏数据加载期间盖住下层界面（含尚未恢复数据时的
 * 「创建项目 / 配置 AI」引导误闪），数据就绪后按退出编排淡出。
 */
@Composable
fun StartupOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    var phase by remember { mutableStateOf(if (visible) StartupPhase.LIVE else StartupPhase.GONE) }
    // 显示过就走到退出编排结束——避免 ready 抖动时中途「闪没」
    var shown by remember { mutableStateOf(visible) }
    if (visible) shown = true

    LaunchedEffect(visible) {
        if (visible || !shown || phase == StartupPhase.GONE) return@LaunchedEffect
        phase = StartupPhase.CONTENT_OUT
        delay(CONTENT_OUT_MS.toLong())
        phase = StartupPhase.OVERLAY_OUT
        delay(OVERLAY_OUT_MS.toLong())
        phase = StartupPhase.GONE
    }

    if (phase == StartupPhase.GONE) return

    val accent = MaterialTheme.colorScheme.primary
    // 与系统启动画面同一个资源：values 亮 / values-night 暗，衔接无跳色
    val splashBg = colorResource(R.color.pient_splash_bg)
    val leaving = phase != StartupPhase.LIVE
    val overlayAlpha by animateFloatAsState(
        targetValue = if (phase == StartupPhase.OVERLAY_OUT) 0f else 1f,
        animationSpec = tween(OVERLAY_OUT_MS, easing = LinearEasing),
        label = "startup-overlay-alpha",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (leaving) 0f else 1f,
        animationSpec = tween(CONTENT_OUT_MS, easing = LinearEasing),
        label = "startup-content-alpha",
    )
    // 桌面端：translate-y-2（8px）下移淡出
    val contentShift by animateDpAsState(
        targetValue = if (leaving) 8.dp else 0.dp,
        animationSpec = tween(CONTENT_OUT_MS, easing = LinearEasing),
        label = "startup-content-shift",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha }
            .background(splashBg)
            // 启动期间吞掉点击，不穿透到下层界面
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {}
            .semantics { contentDescription = "启动加载中" },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer { alpha = contentAlpha }
                .offset(y = contentShift),
        ) {
            Image(
                painter = painterResource(id = R.drawable.pient_logo),
                contentDescription = null,
                modifier = Modifier.size(80.dp),
            )
            Spacer(Modifier.height(24.dp))
            HermesRoseCurveLoader(modifier = Modifier.size(40.dp), color = accent)
            Spacer(Modifier.height(20.dp))
            DecodeText(text = "PIENT", prefix = 1, cursor = true, color = accent)
        }
    }
}

/**
 * Hermes 桌面端 `Loader`（type = rose-curve）的 Compose 移植：78 个粒子沿玫瑰曲线拖尾，
 * 整组 28s 转一圈；曲线本身带 4.6s 周期的「呼吸」脉冲（detailScale 0.52↔1.0）。
 */
@Composable
fun HermesRoseCurveLoader(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val animatorsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val phaseOffset = remember { Random.nextFloat() }
    // 相位随机偏移（源码 `phaseOffset = Math.random()`）；关动画时停在脉冲中段的一帧
    var timeMs by remember { mutableLongStateOf(if (animatorsEnabled) 0L else 1800L) }

    if (animatorsEnabled) {
        LaunchedEffect(Unit) {
            val start = withInfiniteAnimationFrameNanos { it }
            while (true) {
                withInfiniteAnimationFrameNanos { now -> timeMs = (now - start) / 1_000_000L }
            }
        }
    }

    Canvas(modifier) {
        val unit = size.minDimension / 100f
        val t = timeMs.toFloat()
        val detailScale = detailScaleFor(t, phaseOffset)
        val rotation = -(((t + phaseOffset * RoseCurve.ROTATION_DURATION_MS) % RoseCurve.ROTATION_DURATION_MS) /
            RoseCurve.ROTATION_DURATION_MS) * 360f
        val progress = ((t + phaseOffset * RoseCurve.DURATION_MS) % RoseCurve.DURATION_MS) / RoseCurve.DURATION_MS

        withTransform({ rotate(degrees = rotation, pivot = center) }) {
            // 曲线底纹：同一条曲线、opacity 0.1
            val path = Path()
            for (i in 0..RoseCurve.PATH_STEPS) {
                val p = rosePoint(i / RoseCurve.PATH_STEPS.toFloat(), detailScale)
                val x = p.x * unit
                val y = p.y * unit
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color.copy(alpha = RoseCurve.PATH_OPACITY),
                style = Stroke(
                    width = RoseCurve.STROKE_WIDTH * RoseCurve.STROKE_SCALE * unit,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            // 拖尾粒子：越靠尾越淡越小
            for (index in 0 until RoseCurve.PARTICLE_COUNT) {
                val tailOffset = index / (RoseCurve.PARTICLE_COUNT - 1).toFloat()
                val p = rosePoint(normalize(progress - tailOffset * RoseCurve.TRAIL_SPAN), detailScale)
                val fade = (1f - tailOffset).pow(0.56f)
                drawCircle(
                    color = color,
                    radius = (0.9f + fade * 2.7f) * RoseCurve.STROKE_SCALE * unit,
                    center = Offset(p.x * unit, p.y * unit),
                    alpha = 0.04f + fade * 0.96f,
                )
            }
        }
    }
}

/** 玫瑰曲线上的点（源码 roseCurve.point，k=5；坐标为 0..100 视口单位）。 */
private fun rosePoint(progress: Float, detailScale: Float): Offset {
    val t = progress * TWO_PI
    val a = 9.2f + detailScale * 0.6f
    val r = a * (0.72f + detailScale * 0.28f) * cos(RoseCurve.K * t)
    return Offset(50f + cos(t) * r * 3.25f, 50f + sin(t) * r * 3.25f)
}

/** 源码 detailScaleFor：0.52 + ((sin(angle + 0.55) + 1) / 2) * 0.48 */
private fun detailScaleFor(time: Float, phaseOffset: Float): Float {
    val pulseProgress = ((time + phaseOffset * RoseCurve.PULSE_DURATION_MS) % RoseCurve.PULSE_DURATION_MS) /
        RoseCurve.PULSE_DURATION_MS
    return 0.52f + ((sin(pulseProgress * TWO_PI + 0.55f) + 1f) / 2f) * 0.48f
}

/** 源码 normalizeProgress：把进度收进 [0,1) */
private fun normalize(progress: Float): Float = ((progress % 1f) + 1f) % 1f

/**
 * 解码文字（components/ui/decode-text.tsx 移植）：每 45ms 解半个字符，未解出的字符
 * 在 `/\\|-_=+<>~:*` 里随机跳（等宽 so 不跳字宽），全解后停 16 拍再从头循环；
 * 可选 1s 硬闪光标方块。
 */
@Composable
private fun DecodeText(
    text: String,
    color: Color,
    prefix: Int = 0,
    cursor: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // 前 prefix 个字符永不解码（渲染层直接切开，任何定时器都改不到）
    val staticPrefix = remember(text, prefix) { text.take(prefix) }
    val tail = remember(text, prefix) { text.drop(prefix) }
    val animatorsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    var displayed by remember(tail) { mutableStateOf(tail) }

    LaunchedEffect(tail, animatorsEnabled) {
        if (!animatorsEnabled || tail.isEmpty()) return@LaunchedEffect
        var resolved = 0f
        var hold = 0
        while (true) {
            delay(TICK_MS)
            if (resolved >= tail.length) {
                hold++
                if (hold > HOLD_TICKS) {
                    resolved = 0f
                    hold = 0
                }
                displayed = tail
            } else {
                resolved += 0.5f
                displayed = scrambled(tail, resolved.toInt())
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        // 桌面端 pl-[0.4em]：补掉末尾字距造成的视觉偏移
        modifier = modifier.padding(start = DECODE_PREFIX_PADDING),
    ) {
        Text(
            text = staticPrefix + displayed,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = DECODE_FONT_SP,
                letterSpacing = DECODE_TRACKING_SP,
                color = color,
            ),
        )
        if (cursor) DecodeCursor(color)
    }
}

/** 光标方块：size-2（8px）、1px 圆角、上移 1px、1s 硬闪（step-end）。 */
@Composable
private fun DecodeCursor(color: Color) {
    val transition = rememberInfiniteTransition(label = "decode-cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 499
                0f at 500
                0f at 999
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "decode-cursor-alpha",
    )
    Box(
        Modifier
            .padding(start = 2.dp)
            .offset(y = (-1).dp)
            .size(8.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(1.dp))
            .background(color),
    )
}

private fun scrambled(tail: String, resolvedCount: Int): String = buildString {
    tail.forEachIndexed { index, ch ->
        append(if (ch == ' ' || index < resolvedCount) ch else SCRAMBLE_CHARS.random())
    }
}

/** 0.64rem / tracking 0.4em（桌面端 text-[0.64rem] tracking-[0.4em]）；pl 为同一字距的 dp 版 */
private val DECODE_FONT_SP = (0.64f * 16f).sp
private val DECODE_TRACKING_SP = (0.64f * 16f * 0.4f).sp
private val DECODE_PREFIX_PADDING = (0.64f * 16f * 0.4f).dp
