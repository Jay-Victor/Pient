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
import kotlin.math.roundToInt
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
 * ★ 动画曲线＝**从产品 logo 里量出来的 π 边框**（2026-09-12 重做，替换 Hermes 的通用玫瑰曲线）：
 *   玫瑰曲线是 Hermes 自己的图形语汇；本产品的 π 就在 `pient_logo.png` 里（logo = 蓝圆盘 +
 *   负形挖出的「海豚 + π」复合标志）。π 的**三条笔画互不相连**（横杠 / 左腿 / 右腿，两处窄缝），
 *   所以「边框」= 三条各自闭合的曲线；用「膨胀合并成一块再描外轮廓」得到的单条曲线必然要
 *   用直线跨过窄缝、并把笔锋磨圆（旧版 100 点轮廓即如此），无法贴合，故改为逐块精确提取：
 *   ① 取 alpha=127.5 等值线（双线性插值场 8× 上采样后 Moore 追踪，亚像素精度）；
 *   ② 按 8 邻接连通域分出三块笔画，各自 Ramer–Douglas–Peucker(ε=0.20px) 简化——
 *      不做膨胀、不做平滑，最大偏差 0.19px@512（= 显示尺寸 0.04dp）；
 *   ③ 三环统一朝向，各自把「接缝点」旋为首点：接缝＝粒子入笔处，取三环两两最近点附近，
 *      使跨环跳变最小（6.0 / 11.9 / 9.8 视口单位）；
 *   ④ 等比居中归一化到 0..100 视口（占 8..92，与原单轮廓同尺寸、同位置）。
 *   三条曲线**各跑各的彗尾**（互不影响）：每条彗尾的拖尾只落在自己的曲线上，不跨曲线、
 *   不画跨接直线；粒子数按周长占比分摊源码的 78 粒（≈26 / 27 / 25），点距与原单彗尾版一致。
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
    /** 每条曲线彗尾的粒子数（源码值 78；三条曲线各自一条彗尾，按周长占比分摊到各环） */
    const val PARTICLES = 78
    /** 底纹描边宽度（0..100 视口单位；Hermes 为 4.5） */
    const val STROKE_WIDTH = 4.5f
    /** PageLoader 口径描边缩放 */
    const val STROKE_SCALE = 0.72f
    const val PATH_OPACITY = 0.1f
    /** 拖尾长度（占三环总长的比例；Hermes rose-curve 为 0.32） */
    const val TRAIL_SPAN = 0.32f
    /** 粒子半径区间（0..100 视口单位；Hermes 为 0.9→3.6） */
    const val RADIUS_MIN = 0.9f
    const val RADIUS_SPAN = 2.7f
    /** 彗尾绕轮廓一圈 */
    const val DURATION_MS = 5400f
}

/** π 横杠笔画边框（pient_logo.png 的 alpha=127.5 等值线提取；闭合环，起点=接缝） */
private val BRAND_PI_RING_BAR = floatArrayOf(
    51.81f, 23.93f,
    75.04f, 23.92f,
    77.18f, 23.80f,
    79.08f, 23.31f,
    81.22f, 22.27f,
    82.57f, 21.35f,
    83.73f, 20.31f,
    85.63f, 17.92f,
    87.10f, 15.29f,
    88.14f, 12.84f,
    89.12f, 10.02f,
    89.31f, 9.04f,
    89.61f, 8.55f,
    89.43f, 8.00f,
    86.98f, 10.14f,
    84.59f, 11.61f,
    82.33f, 12.59f,
    80.92f, 13.02f,
    78.29f, 13.51f,
    74.61f, 13.63f,
    39.22f, 13.63f,
    36.71f, 13.82f,
    32.80f, 14.73f,
    30.41f, 15.65f,
    27.65f, 17.18f,
    25.20f, 19.14f,
    23.12f, 21.47f,
    21.71f, 23.61f,
    20.12f, 27.04f,
    19.14f, 30.35f,
    18.78f, 32.55f,
    19.45f, 32.12f,
    19.63f, 31.69f,
    21.22f, 29.98f,
    23.18f, 28.39f,
    24.59f, 27.47f,
    26.61f, 26.37f,
    28.76f, 25.45f,
    32.37f, 24.41f,
    35.24f, 24.04f,
    37.63f, 23.92f,
)

/** π 左腿笔画边框（pient_logo.png 的 alpha=127.5 等值线提取；闭合环，起点=接缝） */
private val BRAND_PI_RING_LEFT = floatArrayOf(
    47.31f, 27.95f,
    47.43f, 27.65f,
    47.24f, 27.10f,
    46.57f, 27.04f,
    42.29f, 27.41f,
    39.71f, 27.96f,
    38.31f, 28.45f,
    36.35f, 29.43f,
    35.31f, 30.35f,
    34.94f, 30.96f,
    34.39f, 33.16f,
    34.20f, 34.69f,
    33.90f, 35.61f,
    32.73f, 42.41f,
    32.43f, 43.33f,
    32.24f, 44.98f,
    31.94f, 45.90f,
    31.27f, 50.06f,
    30.96f, 50.98f,
    30.78f, 52.51f,
    30.47f, 53.43f,
    30.29f, 54.96f,
    29.98f, 55.88f,
    29.80f, 57.41f,
    29.49f, 58.27f,
    28.82f, 62.00f,
    27.35f, 67.69f,
    25.88f, 72.04f,
    24.41f, 75.47f,
    22.94f, 78.22f,
    20.92f, 81.16f,
    19.63f, 82.63f,
    17.49f, 84.71f,
    14.92f, 86.61f,
    12.84f, 87.65f,
    10.82f, 88.20f,
    10.39f, 88.45f,
    11.92f, 89.55f,
    15.41f, 91.02f,
    17.18f, 91.51f,
    19.69f, 91.94f,
    23.49f, 92.00f,
    26.00f, 91.51f,
    28.45f, 90.47f,
    31.02f, 88.63f,
    33.22f, 86.24f,
    35.12f, 83.31f,
    36.65f, 80.06f,
    37.63f, 77.49f,
    37.76f, 76.76f,
    38.06f, 76.20f,
    38.73f, 73.51f,
    39.04f, 72.84f,
    39.71f, 69.59f,
    40.02f, 68.80f,
    40.27f, 67.08f,
    40.51f, 66.47f,
    41.18f, 62.12f,
    41.55f, 60.71f,
    41.67f, 59.24f,
    41.98f, 58.20f,
    42.16f, 56.31f,
    42.47f, 55.27f,
    42.65f, 53.37f,
    42.96f, 52.33f,
    43.14f, 50.43f,
    43.45f, 49.39f,
    44.12f, 44.55f,
    44.49f, 43.14f,
    44.61f, 41.67f,
    44.98f, 40.33f,
    45.10f, 38.92f,
    45.96f, 34.76f,
    46.14f, 32.98f,
    46.45f, 32.00f,
    46.63f, 30.53f,
    46.94f, 29.73f,
    47.06f, 28.63f,
)

/** π 右腿笔画边框（pient_logo.png 的 alpha=127.5 等值线提取；闭合环，起点=接缝） */
private val BRAND_PI_RING_RIGHT = floatArrayOf(
    58.87f, 30.69f,
    57.96f, 34.94f,
    57.29f, 40.27f,
    56.92f, 41.92f,
    56.80f, 43.63f,
    56.43f, 45.35f,
    55.33f, 54.22f,
    55.02f, 55.51f,
    54.84f, 57.65f,
    54.53f, 58.88f,
    54.35f, 61.02f,
    53.98f, 62.86f,
    53.61f, 67.39f,
    53.67f, 71.55f,
    53.98f, 73.27f,
    54.47f, 74.80f,
    55.45f, 76.82f,
    56.43f, 78.22f,
    57.41f, 79.27f,
    59.49f, 80.73f,
    60.59f, 81.22f,
    62.49f, 81.71f,
    65.98f, 81.71f,
    68.37f, 81.16f,
    70.51f, 80.18f,
    72.47f, 78.78f,
    74.37f, 76.76f,
    76.27f, 73.69f,
    77.80f, 70.14f,
    79.02f, 65.92f,
    78.96f, 65.67f,
    78.65f, 65.61f,
    76.33f, 68.06f,
    74.43f, 69.47f,
    72.47f, 70.39f,
    71.31f, 70.63f,
    69.29f, 70.57f,
    67.94f, 69.90f,
    66.78f, 68.55f,
    66.16f, 67.02f,
    66.04f, 65.73f,
    66.10f, 63.04f,
    66.71f, 56.73f,
    67.51f, 51.41f,
    67.69f, 48.96f,
    68.00f, 47.61f,
    68.18f, 45.22f,
    68.49f, 43.94f,
    68.67f, 41.55f,
    68.98f, 40.27f,
    69.16f, 37.94f,
    69.47f, 36.65f,
    69.65f, 34.45f,
    69.96f, 33.29f,
    70.14f, 31.14f,
    70.45f, 30.04f,
    70.63f, 28.02f,
    70.82f, 27.47f,
    70.82f, 27.04f,
    70.33f, 26.86f,
    67.63f, 27.04f,
    64.94f, 27.47f,
    64.39f, 27.71f,
    63.29f, 27.90f,
    60.71f, 28.94f,
    59.43f, 29.86f,
    58.88f, 30.53f,
)

/** 每环的等弧长采样表（按周长占比分配样本数；粒子 O(1) 取点） */
private const val CONTOUR_LUT_SAMPLES = 2048

/** 三环的遍历顺序＝书写序：横杠 → 左腿 → 右腿 */
private val BRAND_PI_RINGS = arrayOf(BRAND_PI_RING_BAR, BRAND_PI_RING_LEFT, BRAND_PI_RING_RIGHT)

/** 预计算的 π 边框几何（顶层 object 持有，进程内只算一次） */
private object BrandPiGeometry {
    /** 每环的等弧长采样点，[x0,y0,x1,y1,…]（环闭合：末点的下一段即回首点） */
    val luts: Array<FloatArray>

    /** 每环分到的粒子数（按周长占比分摊源码的 78 粒，使点距与单彗尾版一致） */
    val particles: IntArray

    /** 底纹路径：三条笔画边框，各自闭合 */
    val path: Path

    init {
        val lens = FloatArray(BRAND_PI_RINGS.size)
        BRAND_PI_RINGS.forEachIndexed { k, ring ->
            val n = ring.size / 2
            var sum = 0f
            for (i in 0 until n) {
                val j = (i + 1) % n
                sum += hypot(ring[2 * j] - ring[2 * i], ring[2 * j + 1] - ring[2 * i + 1])
            }
            lens[k] = sum
        }
        val total = lens.sum()
        particles = IntArray(BRAND_PI_RINGS.size) { k ->
            maxOf(6, (BrandPi.PARTICLES * lens[k] / total).roundToInt())
        }

        luts = Array(BRAND_PI_RINGS.size) { k ->
            val ring = BRAND_PI_RINGS[k]
            val n = ring.size / 2
            val count = maxOf(16, (CONTOUR_LUT_SAMPLES * lens[k] / total).toInt())
            val out = FloatArray(count * 2)
            var seg = 0
            var acc = 0f
            for (s in 0 until count) {
                val target = lens[k] * s / count
                while (seg < n - 1) {
                    val j = seg + 1
                    val len = hypot(ring[2 * j] - ring[2 * seg], ring[2 * j + 1] - ring[2 * seg + 1])
                    if (acc + len >= target) break
                    acc += len
                    seg++
                }
                val i0 = seg
                val i1 = (seg + 1) % n
                val len = hypot(ring[2 * i1] - ring[2 * i0], ring[2 * i1 + 1] - ring[2 * i0 + 1])
                val f = if (len > 0f) ((target - acc) / len).coerceIn(0f, 1f) else 0f
                out[2 * s] = ring[2 * i0] + (ring[2 * i1] - ring[2 * i0]) * f
                out[2 * s + 1] = ring[2 * i0 + 1] + (ring[2 * i1 + 1] - ring[2 * i0 + 1]) * f
            }
            out
        }

        path = Path().apply {
            BRAND_PI_RINGS.forEach { ring ->
                val n = ring.size / 2
                moveTo(ring[0], ring[1])
                for (i in 1 until n) lineTo(ring[2 * i], ring[2 * i + 1])
                close()
            }
        }
    }

    /** 第 ring 条曲线上按**自身**弧长进度 t∈[0,1) 取点（环闭合；三条曲线互不影响） */
    fun pointAt(ring: Int, t: Float): Offset {
        val lut = luts[ring]
        val m = lut.size / 2
        val u = ((t % 1f) + 1f) % 1f
        var i0 = (u * m).toInt()
        if (i0 >= m) i0 = m - 1
        val i1 = (i0 + 1) % m
        val f = u * m - i0
        return Offset(
            lut[2 * i0] + (lut[2 * i1] - lut[2 * i0]) * f,
            lut[2 * i0 + 1] + (lut[2 * i1 + 1] - lut[2 * i0 + 1]) * f,
        )
    }
}

/** 源码 normalizeProgress：把进度收进 [0,1) */
private fun normalize(progress: Float): Float = ((progress % 1f) + 1f) % 1f

/**
 * 品牌 π 加载动画：常显的 π 边框底纹（横杠 / 左腿 / 右腿各一条闭合环）
 * + **三条曲线各自一条独立彗尾**（拖尾只落在自己的曲线上，不跨曲线）——
 *   机制与数值取自 Hermes `Loader`，曲线换成从 logo 精确提取的 π 边框。
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
            // 底纹：完整 π 边框（三条笔画各一条闭合环）
            drawPath(
                path = BrandPiGeometry.path,
                color = color.copy(alpha = BrandPi.PATH_OPACITY),
                style = Stroke(
                    width = BrandPi.STROKE_WIDTH * BrandPi.STROKE_SCALE,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            // 拖尾粒子：三条曲线**各自一条彗尾**，互不影响（拖尾只落在自己的曲线上）
            for (ring in BRAND_PI_RINGS.indices) {
                val count = BrandPiGeometry.particles[ring]
                for (index in 0 until count) {
                    val tailOffset = index / (count - 1).toFloat()
                    val p = BrandPiGeometry.pointAt(
                        ring,
                        normalize(progress - tailOffset * BrandPi.TRAIL_SPAN),
                    )
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
