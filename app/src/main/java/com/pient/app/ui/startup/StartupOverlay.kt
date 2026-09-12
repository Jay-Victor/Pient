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
import kotlin.math.hypot
import kotlin.math.pow
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
 * 页面构成（自上而下）：品牌 logo → 品牌加载动画 → 解码文字。
 *
 * 参考实现（Hermes 桌面端源码，`%LOCALAPPDATA%\hermes\hermes-agent\apps\desktop\src`）：
 * - 覆盖层形态 / 主题底色 / 退出编排：`components/gateway-connecting-overlay.tsx`
 *   （全屏底色 + 居中动画元素；退出 = 内容淡出下移 → 覆盖层淡出）
 * - 加载动画的**机制**：`components/ui/loader.tsx` 的 `Loader` ——
 *   常显底纹路径（opacity 0.1）+ 一束沿路径拖尾的渐隐粒子
 *   （`fade = (1-tailOffset)^0.56`、半径 0.9→3.6 ×strokeScale、透明度 0.04→1.0、
 *   描边 round 连接、粒子数 78、尾长 0.32、一圈 5400ms；PageLoader 口径 strokeScale=0.72、size-10）
 * - 解码文字：`components/ui/decode-text.tsx`（45ms 一拍、每拍解半个字符、全解后停 16 拍再循环、
 *   字符集 `/\\|-_=+<>~:*`、前 prefix 个字符不解码、光标方块 1s 硬闪）
 *   品牌字标用法同 `components/pane-shell/tree/renderer/tree-group.tsx`
 *   （`<DecodeText text="HERMES" cursor prefix={1} />`），此处同款写作 "PIENT"。
 *
 * ★ 动画曲线＝**从产品 logo 里量出来的 π 轮廓**（2026-09-12 三改，替换 Hermes 的通用玫瑰曲线）：
 *   玫瑰曲线是 Hermes 自己的图形语汇；本产品的 π 就在 `pient_logo.png` 里（logo = 蓝圆盘 +
 *   负形挖出的「海豚 + π」复合标志）。提取流程（可复现）：
 *   ① 取盘内 alpha<40 的负形，连通域分析 → π 由三块独立笔画组成（横杠 / 左腿 / 右腿，
 *      面积 2649 / 2913 / 3394，bbox 合计 160×169@512 画布）；
 *   ② 三块互不相连，先按 8 邻接膨胀 4px 合并成一块；
 *   ③ Moore 邻域追踪外轮廓（917 点，自检相邻点 8 邻接）；
 *   ④ Ramer–Douglas–Peucker(ε=1.6) → 48 点，Chaikin 角割 3 轮平滑，按弧长重采样到 100 点；
 *   ⑤ 等比居中归一化到 0..100 视口（占 8..92，即 84 单位）。
 *   粒子彗尾沿这条闭合轮廓跑，视觉上就是「logo 里那个 π 被光描了一遍」。
 *
 * 与桌面端的有意差异（数值已在注释里标注）：
 * - **不旋转**：Hermes 的曲线整组 28s 转一圈，但 π 是有方向的字形，旋转会破坏可读性。
 * - **不做形状脉冲**：Hermes 的 detailScale 让参数曲线呼吸变形，轮廓是固定字形，不需要。
 * - 退出编排缩短为「内容淡出 300ms → 覆盖层淡出 300ms」并去掉 300ms 停留：
 *   桌面端启动以秒计，移动端首屏数据常在数百毫秒内就绪，照搬 1.18s 会像卡住。
 * - 底色用 `@color/pient_splash_bg`（与系统启动画面**同一个 day/night 资源**），
 *   使「系统启动画面 → 本加载页」无缝衔接；数据就绪后再淡出到应用主题界面。
 */

/** 品牌 π 加载动画参数（机制数值沿用 Hermes `Loader`） */
private object BrandPi {
    const val PARTICLES = 78
    /** 底纹描边宽度（0..100 视口单位；Hermes 为 4.5） */
    const val STROKE_WIDTH = 4.5f
    /** PageLoader 口径描边缩放 */
    const val STROKE_SCALE = 0.72f
    const val PATH_OPACITY = 0.1f
    /** 拖尾长度（占整条轮廓的比例；Hermes rose-curve 为 0.32） */
    const val TRAIL_SPAN = 0.32f
    /** 粒子半径区间（0..100 视口单位；Hermes 为 0.9→3.6） */
    const val RADIUS_MIN = 0.9f
    const val RADIUS_SPAN = 2.7f
    /** 彗尾绕轮廓一圈 */
    const val DURATION_MS = 5400f
}

/**
 * 从 `pient_logo.png` 负形追踪出的 π 轮廓（100 点，闭合，0..100 视口单位）。
 * 提取参数见文件头注释；改 logo 后需重新生成。
 */
private val BRAND_PI_CONTOUR = floatArrayOf(
    87.6f, 8.0f,
    89.7f, 11.0f,
    88.4f, 15.3f,
    86.4f, 19.3f,
    83.6f, 22.7f,
    79.8f, 25.0f,
    75.6f, 26.1f,
    72.8f, 29.4f,
    71.5f, 33.6f,
    70.6f, 38.0f,
    69.9f, 42.3f,
    69.4f, 46.8f,
    68.9f, 51.2f,
    68.5f, 55.6f,
    68.2f, 60.0f,
    68.2f, 64.5f,
    70.7f, 67.1f,
    74.4f, 64.7f,
    78.5f, 63.5f,
    79.4f, 67.5f,
    78.1f, 71.8f,
    76.1f, 75.8f,
    73.2f, 79.2f,
    69.4f, 81.4f,
    65.1f, 82.2f,
    60.6f, 82.0f,
    56.5f, 80.5f,
    53.5f, 77.3f,
    52.2f, 73.1f,
    52.0f, 68.7f,
    52.1f, 64.2f,
    52.5f, 59.8f,
    52.9f, 55.4f,
    53.5f, 51.0f,
    54.1f, 46.6f,
    54.7f, 42.2f,
    55.4f, 37.8f,
    56.2f, 33.4f,
    57.5f, 29.2f,
    58.5f, 26.6f,
    54.1f, 26.5f,
    50.9f, 29.4f,
    49.0f, 33.4f,
    47.7f, 37.7f,
    46.7f, 42.0f,
    45.9f, 46.4f,
    45.2f, 50.8f,
    44.4f, 55.1f,
    43.7f, 59.5f,
    42.9f, 63.9f,
    42.0f, 68.2f,
    41.0f, 72.6f,
    39.8f, 76.9f,
    38.3f, 81.0f,
    36.3f, 85.0f,
    33.2f, 88.2f,
    29.5f, 90.6f,
    25.2f, 91.8f,
    20.8f, 92.0f,
    16.4f, 91.5f,
    12.2f, 90.1f,
    10.3f, 86.4f,
    13.6f, 83.9f,
    17.1f, 81.2f,
    20.1f, 77.8f,
    22.5f, 74.1f,
    24.4f, 70.1f,
    26.0f, 66.0f,
    27.2f, 61.7f,
    28.2f, 57.3f,
    29.1f, 53.0f,
    30.0f, 48.6f,
    30.8f, 44.3f,
    31.6f, 39.9f,
    32.5f, 35.5f,
    33.4f, 31.2f,
    35.8f, 27.7f,
    35.9f, 26.3f,
    31.6f, 27.4f,
    27.6f, 29.3f,
    24.0f, 31.9f,
    20.4f, 34.2f,
    18.4f, 31.0f,
    19.2f, 26.7f,
    21.0f, 22.6f,
    23.6f, 19.0f,
    27.0f, 16.2f,
    31.0f, 14.3f,
    35.4f, 13.7f,
    39.8f, 13.4f,
    44.3f, 13.2f,
    48.7f, 13.1f,
    53.2f, 13.0f,
    57.6f, 13.0f,
    62.1f, 12.9f,
    66.5f, 12.8f,
    70.9f, 12.6f,
    75.4f, 12.3f,
    79.8f, 11.7f,
    83.8f, 9.7f,
)

/** 等弧长采样表数（按周长均匀取样，粒子 O(1) 取点） */
private const val CONTOUR_LUT_SAMPLES = 512

/** 预计算的 π 轮廓几何（顶层 object 持有，进程内只算一次） */
private object BrandPiGeometry {
    /** 等弧长采样点，[x0,y0,x1,y1,…] */
    val lut: FloatArray

    /** 底纹路径（闭合轮廓） */
    val path: Path

    init {
        val n = BRAND_PI_CONTOUR.size / 2
        val cum = FloatArray(n + 1)
        for (i in 0 until n) {
            val j = (i + 1) % n
            cum[i + 1] = cum[i] + hypot(
                BRAND_PI_CONTOUR[2 * j] - BRAND_PI_CONTOUR[2 * i],
                BRAND_PI_CONTOUR[2 * j + 1] - BRAND_PI_CONTOUR[2 * i + 1],
            )
        }
        val total = cum[n]
        val samples = FloatArray(CONTOUR_LUT_SAMPLES * 2)
        var seg = 0
        for (s in 0 until CONTOUR_LUT_SAMPLES) {
            val target = total * s / (CONTOUR_LUT_SAMPLES - 1)
            while (seg < n - 1 && cum[seg + 1] < target) seg++
            val segLen = cum[seg + 1] - cum[seg]
            val f = if (segLen > 0f) (target - cum[seg]) / segLen else 0f
            val i0 = seg
            val i1 = (seg + 1) % n
            samples[2 * s] =
                BRAND_PI_CONTOUR[2 * i0] + (BRAND_PI_CONTOUR[2 * i1] - BRAND_PI_CONTOUR[2 * i0]) * f
            samples[2 * s + 1] = BRAND_PI_CONTOUR[2 * i0 + 1] +
                (BRAND_PI_CONTOUR[2 * i1 + 1] - BRAND_PI_CONTOUR[2 * i0 + 1]) * f
        }
        lut = samples
        path = Path().apply {
            moveTo(BRAND_PI_CONTOUR[0], BRAND_PI_CONTOUR[1])
            for (i in 1 until n) lineTo(BRAND_PI_CONTOUR[2 * i], BRAND_PI_CONTOUR[2 * i + 1])
            close()
        }
    }
}

/** 按弧长进度（0..1）取 π 轮廓上的点 */
private fun contourPointAt(t: Float): Offset {
    val x = t.coerceIn(0f, 1f) * (CONTOUR_LUT_SAMPLES - 1)
    val i0 = x.toInt().coerceAtMost(CONTOUR_LUT_SAMPLES - 2)
    val f = x - i0
    return Offset(
        BrandPiGeometry.lut[2 * i0] + (BrandPiGeometry.lut[2 * (i0 + 1)] - BrandPiGeometry.lut[2 * i0]) * f,
        BrandPiGeometry.lut[2 * i0 + 1] +
            (BrandPiGeometry.lut[2 * (i0 + 1) + 1] - BrandPiGeometry.lut[2 * i0 + 1]) * f,
    )
}

/** 源码 normalizeProgress：把进度收进 [0,1) */
private fun normalize(progress: Float): Float = ((progress % 1f) + 1f) % 1f

/**
 * 品牌 π 加载动画：常显的 π 轮廓底纹 + 沿轮廓跑的粒子彗尾
 * （机制与数值取自 Hermes `Loader`，曲线换成从 logo 追踪出的 π）。
 */
@Composable
fun BrandPiLoader(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val animatorsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    // 相位随机偏移（源码 `phaseOffset = Math.random()`）；关动画时停在轮廓中段的一帧
    val phaseOffset = remember { Random.nextFloat() }
    var timeMs by remember { mutableLongStateOf(if (animatorsEnabled) 0L else 2700L) }

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
        val progress = ((timeMs.toFloat() + phaseOffset * BrandPi.DURATION_MS) % BrandPi.DURATION_MS) /
            BrandPi.DURATION_MS

        withTransform({ scale(unit, unit, pivot = Offset.Zero) }) {
            // 底纹：完整 π 轮廓（品牌符号任何时候都在）
            drawPath(
                path = BrandPiGeometry.path,
                color = color.copy(alpha = BrandPi.PATH_OPACITY),
                style = Stroke(
                    width = BrandPi.STROKE_WIDTH * BrandPi.STROKE_SCALE,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            // 拖尾粒子：越靠尾越淡越小
            for (index in 0 until BrandPi.PARTICLES) {
                val tailOffset = index / (BrandPi.PARTICLES - 1).toFloat()
                val p = contourPointAt(normalize(progress - tailOffset * BrandPi.TRAIL_SPAN))
                val fade = (1f - tailOffset).pow(0.56f)
                drawCircle(
                    color = color,
                    radius = (BrandPi.RADIUS_MIN + fade * BrandPi.RADIUS_SPAN) * BrandPi.STROKE_SCALE,
                    center = p,
                    alpha = 0.04f + fade * 0.96f,
                )
            }
        }
    }
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
            BrandPiLoader(modifier = Modifier.size(40.dp), color = accent)
            Spacer(Modifier.height(20.dp))
            DecodeText(text = "PIENT", prefix = 1, cursor = true, color = accent)
        }
    }
}

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
