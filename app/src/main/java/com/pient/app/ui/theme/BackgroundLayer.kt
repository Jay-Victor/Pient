package com.pient.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pient.app.R
import com.pient.app.data.BackgroundMediaType
import com.pient.app.data.SettingsStore
import kotlinx.coroutines.launch
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.effect.RgbAdjustment
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 聊天页自定义背景层（2026-08-31 实现，参照 Operit AppBackgroundLayer）：
 * - 图片：Compose Image + Modifier.blur（Operit 同款，API 31+ 生效）+ ColorMatrix 亮度（10%..150%）
 * - 视频：ExoPlayer + StyledPlayerView(texture_view) + setRenderEffect 高斯模糊 + RgbAdjustment 亮度；静音循环
 * - 未设置背景时不绘制，页面底色由 PientApp 根部的 colorScheme.background 承担。
 */
@Composable
fun AppBackgroundLayer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mediaType = SettingsStore.backgroundMediaType
    val imageUri = SettingsStore.backgroundImageUri
    val videoUri = SettingsStore.backgroundVideoUri
    val blurEnabled = SettingsStore.backgroundBlurEnabled
    val blurRadiusDp = SettingsStore.backgroundBlurRadius
    val brightness = SettingsStore.backgroundBrightness

    Box(modifier.fillMaxSize()) {
        when (mediaType) {
            BackgroundMediaType.IMAGE -> imageUri?.let { uri ->
                ImageBackground(uri, blurEnabled, blurRadiusDp, brightness)
            }
            BackgroundMediaType.VIDEO -> videoUri?.let { uri ->
                VideoBackground(context, lifecycleOwner, uri, blurEnabled, blurRadiusDp, brightness)
            }
        }
    }
}

/**
 * 背景图片内存缓存（2026-09-12 修「打开 Pient 一瞬间背景没加载出来」）：
 * 按 `uri@宽x高` 缓存解码结果，冷启动时由 [preloadBackgroundImage] 在进程启动阶段
 * 提前解码（与 Compose 启动重叠），聊天页首帧就能同步命中缓存、不再等 IO。
 * 只留最近 2 张（换背景时旧图很快被淘汰）。
 */
private val backgroundImageCache = android.util.LruCache<String, ImageBitmap>(2)

private fun backgroundCacheKey(uri: String, w: Int, h: Int): String = "$uri@${w}x$h"

/**
 * 解码（或取缓存）背景图片；可供启动阶段预加载与 [ImageBackground] 共用。
 * 命中缓存直接返回，未命中才走磁盘解码（应在 IO 线程调用）。
 */
suspend fun loadBackgroundImage(
    context: Context,
    uri: String,
    targetW: Int,
    targetH: Int,
): ImageBitmap? {
    val key = backgroundCacheKey(uri, targetW, targetH)
    backgroundImageCache.get(key)?.let { return it }
    val bmp = withContext(Dispatchers.IO) {
        runCatching {
            decodeSampled(context, Uri.parse(uri), targetW = targetW, targetH = targetH)?.asImageBitmap()
        }.getOrNull()
    } ?: return null
    backgroundImageCache.put(key, bmp)
    return bmp
}

/** 启动阶段预加载（MainActivity onCreate 调用；未设图片背景时直接返回） */
fun preloadBackgroundImage(context: Context) {
    val uri = SettingsStore.backgroundImageUri ?: return
    if (SettingsStore.backgroundMediaType != BackgroundMediaType.IMAGE) return
    val metrics = context.resources.displayMetrics
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        loadBackgroundImage(context.applicationContext, uri, metrics.widthPixels, metrics.heightPixels)
    }
}

/** 图片背景：降采样解码 + 模糊（Modifier.blur，Operit 同款）+ ColorMatrix 亮度缩放 */
@Composable
private fun ImageBackground(uri: String, blur: Boolean, blurRadiusDp: Float, brightness: Float) {
    val context = LocalContext.current
    // 先同步命中缓存（启动阶段预加载完成后即有值 → 首帧就带背景），未命中再异步解码
    val metrics = remember { context.resources.displayMetrics }
    var bitmap by remember(uri) {
        mutableStateOf(backgroundImageCache.get(backgroundCacheKey(uri, metrics.widthPixels, metrics.heightPixels)))
    }
    LaunchedEffect(uri) {
        if (bitmap == null) {
            bitmap = loadBackgroundImage(context, uri, metrics.widthPixels, metrics.heightPixels)
        }
    }
    val bmp = bitmap ?: return
    // 亮度 10%..150%：RGB 通道等比缩放（≤1 压暗，>1 提亮）
    val matrix = remember(brightness) {
        ColorMatrix().apply { setToScale(brightness, brightness, brightness, 1f) }
    }
    Image(
        bitmap = bmp,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .then(if (blur) Modifier.blur(blurRadiusDp.dp) else Modifier),
        contentScale = ContentScale.Crop,
        colorFilter = ColorMatrixColorFilter(matrix),
        filterQuality = FilterQuality.High,
    )
}

/** 视频背景：ExoPlayer（静音/循环/裁剪区间可调）+ TextureView 级模糊 + RgbAdjustment 亮度；随生命周期暂停/恢复 */
@Composable
private fun VideoBackground(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    uri: String,
    blur: Boolean,
    blurRadiusDp: Float,
    brightness: Float,
) {
    val muted = SettingsStore.videoBackgroundMuted
    val loop = SettingsStore.videoBackgroundLoop
    val trimStart = SettingsStore.videoTrimStartSec
    val trimEnd = SettingsStore.videoTrimEndSec
    val cropMode = SettingsStore.videoCropMode
    val speed = SettingsStore.videoPlaybackSpeed

    fun mediaItem(): MediaItem {
        val builder = MediaItem.Builder().setUri(uri)
        if (trimStart != null || trimEnd != null) {
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(((trimStart ?: 0f) * 1000).toLong())
                    .setEndPositionMs(((trimEnd ?: 0f) * 1000).toLong())
                    .build(),
            )
        }
        return builder.build()
    }

    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            volume = if (muted) 0f else 1f
            setMediaItem(mediaItem())
            setVideoEffects(listOf(rgbAdjustment(brightness)))
            prepare()
            playWhenReady = true
        }
    }
    // 播放设置实时生效——按副作用粒度拆分（2026-08-31 修复"拖动亮度滑轨视频暂停"）：
    // 此前五元组 LaunchedEffect 中亮度每帧变化都执行 setMediaItem+prepare() 重新加载媒体 → 播放中断。
    // 拆分后：亮度只走 GL 特效（不重载）；静音/循环只改属性；只有裁剪区间变更才重载媒体。
    LaunchedEffect(muted, loop) {
        player.repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        player.volume = if (muted) 0f else 1f
    }
    LaunchedEffect(trimStart, trimEnd) {
        player.setMediaItem(mediaItem())
        player.prepare()
        player.playWhenReady = true
    }
    LaunchedEffect(brightness) {
        player.setVideoEffects(listOf(rgbAdjustment(brightness)))
    }
    LaunchedEffect(speed) {
        player.setPlaybackSpeed(speed)
    }
    DisposableEffect(player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> player.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    val blurPx = with(LocalDensity.current) { blurRadiusDp.dp.toPx() }
    // 画面裁剪：按目标宽高比放大 TextureView 内容，裁掉超出屏幕的部分（居中裁剪）
    val cropTransform: android.graphics.Matrix? = remember(cropMode) {
        val targetAspect = when (cropMode) {
            com.pient.app.data.VideoCropMode.ORIGINAL -> null
            com.pient.app.data.VideoCropMode.SQUARE -> 1f
            com.pient.app.data.VideoCropMode.RATIO_16_9 -> 16f / 9f
            com.pient.app.data.VideoCropMode.RATIO_9_16 -> 9f / 16f
        }
        targetAspect?.let { t ->
            val w = context.resources.displayMetrics.widthPixels.toFloat()
            val h = context.resources.displayMetrics.heightPixels.toFloat()
            val screenAspect = w / h
            val scale = if (screenAspect < t) h * t / w else 1f
            if (scale > 1.001f) {
                android.graphics.Matrix().apply { setScale(scale, scale, w / 2f, h / 2f) }
            } else null
        }
    }
    AndroidView(
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.view_background_texture_player, null, false) as PlayerView)
                .apply {
                    this.player = player
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setKeepContentOnPlayerReset(true)
                    if (Build.VERSION.SDK_INT >= 31 && blur) {
                        setRenderEffect(RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP))
                    }
                    cropTransform?.let { (videoSurfaceView as? android.view.TextureView)?.setTransform(it) }
                }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            if (Build.VERSION.SDK_INT >= 31) {
                view.setRenderEffect(
                    if (blur) RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP) else null,
                )
            }
            (view.videoSurfaceView as? android.view.TextureView)?.setTransform(cropTransform)
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** 亮度 0.1..1.5 → RgbAdjustment RGB 等比缩放（与图片 ColorMatrix setToScale 语义一致：≤1 压暗、>1 提亮） */
private fun rgbAdjustment(brightness: Float): RgbAdjustment =
    RgbAdjustment.Builder()
        .setRedScale(brightness)
        .setGreenScale(brightness)
        .setBlueScale(brightness)
        .build()

/**
 * 按目标边长等比降采样解码。
 * - 仅给 maxDim：结果最长边 ≤ maxDim（预览窗用，2 的幂采样即可）。
 * - 给 targetW/targetH：背景层用——采样取「结果仍 ≥ 目标」的最大 2 的幂，再精确缩放到
 *   Cover 尺寸（≥ 屏幕且不留欠采样放大），保证铺满屏幕不放大、大图不超量载入。
 */
fun decodeSampled(
    context: Context,
    uri: Uri,
    maxDim: Int = 1600,
    targetW: Int = 0,
    targetH: Int = 0,
): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val outW = bounds.outWidth
    val outH = bounds.outHeight
    if (targetW > 0 && targetH > 0) {
        var sample = 1
        while (outW / (sample * 2) >= targetW && outH / (sample * 2) >= targetH) sample *= 2
        // 峰值内存保护：解码结果最多 4× 目标像素，超限再降一级（12MP 级大图轻微 1.2× 放大换内存）
        val maxPixels = targetW.toLong() * targetH * 4
        if ((outW.toLong() / sample) * (outH / sample) > maxPixels) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        // 精确 Cover 缩放；源图小于屏幕（宽幅图等）不预放大，交给绘制端 Crop 处理
        val scale = maxOf(targetW.toFloat() / bitmap.width, targetH.toFloat() / bitmap.height)
        if (scale >= 1f) return bitmap
        val w = maxOf(targetW, (bitmap.width * scale).toInt() + 1)
        val h = maxOf(targetH, (bitmap.height * scale).toInt() + 1)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
    var sample = 1
    while (outW / sample > maxDim || outH / sample > maxDim) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}
