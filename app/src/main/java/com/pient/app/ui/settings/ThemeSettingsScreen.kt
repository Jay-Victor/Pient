package com.pient.app.ui.settings

import android.content.Intent
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BlurLinear
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Rectangle
import androidx.compose.material.icons.outlined.RoundedCorner
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.pient.app.data.AccentPreset
import com.pient.app.data.AccentPresets
import com.pient.app.data.BackgroundMediaType
import com.pient.app.data.BuiltinFonts
import com.pient.app.data.DarkSchemes
import com.pient.app.data.FONT_SIZE_MAX
import com.pient.app.data.FONT_SIZE_MIN
import com.pient.app.data.FontSource
import com.pient.app.data.InputBarMaterial
import com.pient.app.data.InputBarStyle
import com.pient.app.data.LightSchemes
import com.pient.app.data.SettingsStore
import com.pient.app.data.ThemeMode
import com.pient.app.data.ThemeScheme
import com.pient.app.data.VideoCropMode
import com.pient.app.ui.components.DividerLine
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.components.PientSegmented
import com.pient.app.ui.components.PientSlider
import com.pient.app.ui.components.SectionHeader
import com.pient.app.ui.components.SettingsRow
import com.pient.app.ui.components.SettingsSwitchRow
import com.pient.app.ui.theme.LocalPientIsDark
import com.pient.app.ui.theme.decodeSampled
import com.pient.app.ui.theme.resolveFontFamily
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 主题与外观标签（展示顺序即声明顺序） */
private enum class ThemeTab(val label: String) {
    THEME("主题设置"),
    BACKGROUND("背景设置"),
    FONT("字体设置"),
    INPUT_BAR("输入框设置"),
}

/** 分段控制器展示顺序（用户指定：浅色 → 深色 → 跟随系统）与 ThemeMode 枚举的映射 */
private val modeLabels = listOf("浅色模式", "深色模式", "跟随系统")
private val modeValues = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)

/** 自定义主题色滑轨：色相环彩虹渐变（0° 红 → 360° 红；拖到哪、滑块处颜色即主题色） */
private val RainbowBrush = Brush.horizontalGradient(
    *((0..36).map { i ->
        i / 36f to Color.hsv(i / 36f * 360f, 0.85f, 1f)
    }).toTypedArray(),
)

/**
 * 主题与外观（2026-08-31 重设计）：顶部标签栏（主题设置/背景设置/字体设置/外观设置）。
 * 主题设置 = 主题模式卡 + 主题色卡（分区标题在卡片外上方，与服务商与模型配置/关于页同款）；
 * 其余标签暂不制作（占位）。
 */
@Composable
fun ThemeSettingsScreen(nav: NavController) {
    var selectedTab by remember { mutableStateOf(ThemeTab.THEME) }

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Outlined.ArrowBack, "返回",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = { nav.popBackStack() }),
            )
            Text(
                "主题与外观",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        // 标签栏（Operit ThemeSettingsTabbedContent 同款参数）
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ThemeTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.label) },
                )
            }
        }

        when (selectedTab) {
            ThemeTab.THEME -> ThemeTabContent()
            ThemeTab.BACKGROUND -> BackgroundTabContent()
            ThemeTab.FONT -> FontTabContent()
            ThemeTab.INPUT_BAR -> InputBarTabContent()
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 主题设置标签：主题模式 + 主题色
// ─────────────────────────────────────────────────────────────
@Composable
private fun ThemeTabContent() {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        // ── 主题模式 ──
        item { SectionHeader("主题模式", icon = Icons.Outlined.Brightness4) }
        item {
            // PientSegmented 自带容器（surfaceContainerLow 底 + 描边 + 10dp 圆角），不再外套 Card
            PientSegmented(
                labels = modeLabels,
                selected = modeValues.indexOf(SettingsStore.themeMode),
                onSelect = { i -> SettingsStore.themeMode = modeValues[i] },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }

        // ── 主题色 ──
        item { SectionHeader("主题色", icon = Icons.Outlined.ColorLens) }
        item {
            Card {
                Column(Modifier.padding(12.dp)) {
                    // 两行 × 六列；48dp 圆形色块 + 2dp 描边（无名称标注，名称存于 contentDescription）
                    AccentPresets.chunked(6).forEachIndexed { rowIndex, rowPresets ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = if (rowIndex == 0) 12.dp else 0.dp),
                        ) {
                            rowPresets.forEach { preset ->
                                AccentColorCard(preset = preset, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    // ── 自定义主题色（2026-09-01）：开关 → 彩虹色相滑轨，拖动即时改全局主题色 ──
                    DividerLine(Modifier.padding(top = 12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Colorize, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "自定义主题色",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(start = 6.dp),
                        )
                        Switch(
                            checked = SettingsStore.customAccentEnabled,
                            onCheckedChange = { SettingsStore.customAccentEnabled = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                    if (SettingsStore.customAccentEnabled) {
                        PientSlider(
                            value = SettingsStore.customAccentHue,
                            onValueChange = { SettingsStore.customAccentHue = it },
                            valueRange = 0f..360f,
                            customTrackBrush = RainbowBrush,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        // ── 主题方案（标题与内容随实际明暗联动：浅色模式/亮系统 → 浅色主题方案，深色模式/暗系统 → 深色主题方案） ──
        item {
            val dark = LocalPientIsDark.current
            SectionHeader(
                if (dark) "深色主题方案" else "浅色主题方案",
                icon = if (dark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
            )
        }
        item {
            Card {
                Column(Modifier.padding(12.dp)) {
                    val dark = LocalPientIsDark.current
                    val schemes = if (dark) DarkSchemes else LightSchemes
                    val current = if (dark) SettingsStore.darkScheme else SettingsStore.lightScheme
                    schemes.forEachIndexed { i, scheme ->
                        if (i > 0) DividerLine()
                        SchemeRow(
                            scheme = scheme,
                            selected = scheme == current,
                            onClick = {
                                if (dark) SettingsStore.darkScheme = scheme else SettingsStore.lightScheme = scheme
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 主题方案选项行：双色预览块（上半=页面底、下半=卡片底）+ 方案名 + 辅助说明 + 选中对勾 */
@Composable
private fun SchemeRow(scheme: ThemeScheme, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(8.dp),
                ),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth().background(scheme.background))
            Box(Modifier.weight(1f).fillMaxWidth().background(scheme.surfaceContainerLow))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(scheme.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                scheme.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun AccentColorCard(preset: AccentPreset, modifier: Modifier = Modifier) {
    // 自定义主题色开启时预设色块全部不显示选中（自定义色为当前主色）
    val selected = SettingsStore.accent == preset && !SettingsStore.customAccentEnabled
    // 色板按当前明暗显示对应变体（所见即所得：亮色下白字对比度达标）
    val swatch = if (LocalPientIsDark.current) preset.dark else preset.light
    Box(
        modifier = modifier.height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(swatch)
                .border(
                    2.dp,
                    if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
                    CircleShape,
                )
                .semantics { contentDescription = preset.name }
                .clickable(onClick = {
                    // 点预设色块 = 切回预设并关闭自定义
                    SettingsStore.customAccentEnabled = false
                    SettingsStore.accent = preset
                }),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check, null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 背景设置标签：自定义背景（分段控制器 + 预览 + 移除/选择按键）+ 背景效果（高斯模糊开关滑轨 + 亮度滑轨）
// ─────────────────────────────────────────────────────────────
@Composable
private fun BackgroundTabContent() {
    // 视频裁剪弹窗状态提升到 LazyColumn 之外（PientDialog 的 fillMaxSize 需要页面级约束；
    // 放在 item 内会被无限高度约束压塌、弹窗不可见——2026-08-31 修复"点击视频裁剪无跳转"）
    var showTrimDialog by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            // ── 自定义背景 ──
            item { SectionHeader("自定义背景", icon = Icons.Outlined.Image) }
            item { CustomBackgroundCard(onTrimVideo = { showTrimDialog = true }) }

            // ── 背景效果 ──
            item { SectionHeader("背景效果", icon = Icons.Outlined.BlurOn) }
            item { BackgroundEffectsCard() }
        }
        if (showTrimDialog && SettingsStore.backgroundVideoUri != null) {
            VideoTrimDialog(
                videoUri = SettingsStore.backgroundVideoUri!!,
                onDismiss = { showTrimDialog = false },
            )
        }
    }
}

/** 自定义背景卡：图片/视频分段 → 预览窗（200dp，Operit 同款）→ 移除/选择按键（随分段联动文案） */
@Composable
private fun CustomBackgroundCard(onTrimVideo: () -> Unit) {
    val context = LocalContext.current
    val isImage = SettingsStore.backgroundMediaType == BackgroundMediaType.IMAGE
    val currentUri = if (isImage) SettingsStore.backgroundImageUri else SettingsStore.backgroundVideoUri

    // ── 图片裁剪（Operit CropImageContract 同款：导入即裁剪 + 预览角标二次裁剪） ──
    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val cropped = result.uriContent
            if (cropped != null) {
                // 裁剪输出在 cache（随时可能被清），复制到 filesDir 保证重启后仍可解码
                val internal = copyToInternalStorage(context, cropped)
                if (internal != null) {
                    SettingsStore.backgroundImageUri = internal.toString()
                    SettingsStore.backgroundMediaType = BackgroundMediaType.IMAGE
                } else {
                    Toast.makeText(context, "图片保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (result.error != null) {
            Toast.makeText(context, "图片裁剪失败", Toast.LENGTH_SHORT).show()
        }
    }
    // 裁剪页主题色（launchImageCrop 为非 Composable 局部函数，颜色在 Composable 作用域预解析；
    // 图标色 = Operit 同款 isNightMode 逻辑：暗色白图标、亮色黑图标——不能直接用 onPrimary）
    val cropPrimary = MaterialTheme.colorScheme.primary.toArgb()
    val cropIconColor = if (LocalPientIsDark.current) Color.White.toArgb() else Color.Black.toArgb()
    val cropSurface = MaterialTheme.colorScheme.surfaceContainer.toArgb()
    fun launchImageCrop(uri: Uri) {
        cropLauncher.launch(
            CropImageContractOptions(
                uri,
                CropImageOptions().apply {
                    guidelines = CropImageView.Guidelines.ON
                    outputCompressFormat = android.graphics.Bitmap.CompressFormat.JPEG
                    outputCompressQuality = 90
                    fixAspectRatio = false
                    cropMenuCropButtonTitle = "裁剪"
                    activityTitle = "裁剪图片"
                    toolbarColor = cropPrimary
                    toolbarBackButtonColor = cropIconColor
                    toolbarTitleColor = cropIconColor
                    activityBackgroundColor = cropSurface
                    backgroundColor = cropSurface
                    activityMenuIconColor = cropIconColor
                    showCropOverlay = true
                    showProgressBar = true
                    multiTouchEnabled = true
                    autoZoomEnabled = true
                },
            ),
        )
    }

    val scope = rememberCoroutineScope()
    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
        if (result != null) {
            when (SettingsStore.backgroundMediaType) {
                // 导入图片 → 立即进入裁剪（Operit 同款流程：选图后直接 launchImageCrop）
                BackgroundMediaType.IMAGE -> launchImageCrop(result)
                BackgroundMediaType.VIDEO -> {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(result, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    // 视频也复制到内部存储（Operit copyFileToInternalStorage 同款）：
                    // media documents URI 对 MediaMetadataRetriever/ExoPlayer 读取不稳，
                    // file:// 内部副本保证预览取帧、时长读取与播放全部可靠。
                    scope.launch {
                        val internal = withContext(Dispatchers.IO) { copyVideoToInternalStorage(context, result) }
                        if (internal != null) {
                            SettingsStore.backgroundVideoUri = internal.toString()
                        } else {
                            Toast.makeText(context, "视频保存失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    Card {
        Column(Modifier.padding(12.dp)) {
            PientSegmented(
                labels = listOf("图片", "视频"),
                selected = SettingsStore.backgroundMediaType.ordinal,
                onSelect = { SettingsStore.backgroundMediaType = BackgroundMediaType.entries[it] },
                icons = listOf(Icons.Outlined.Image, Icons.Outlined.Videocam),
                modifier = Modifier.fillMaxWidth(),
            )
            BackgroundPreview(SettingsStore.backgroundMediaType, currentUri)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                PientButton(
                    if (isImage) "移除图片" else "移除视频",
                    onClick = {
                        if (isImage) SettingsStore.backgroundImageUri = null
                        else SettingsStore.backgroundVideoUri = null
                    },
                    primary = false,
                    enabled = currentUri != null,
                    modifier = Modifier.weight(1f),
                )
                PientButton(
                    if (isImage) "选择图片" else "选择视频",
                    onClick = {
                        mediaLauncher.launch(arrayOf(if (isImage) "image/*" else "video/*"))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            // ── 图片：图片裁剪行；视频：视频裁剪/视频声音/视频循环三行（2026-08-31 从预览角标移入） ──
            if (isImage) {
                DividerLine(Modifier.padding(top = 12.dp))
                SettingsRow(
                    icon = Icons.Filled.Crop,
                    title = "图片裁剪",
                    onClick = {
                        SettingsStore.backgroundImageUri?.let { launchImageCrop(Uri.parse(it)) }
                            ?: Toast.makeText(context, "请先选择图片", Toast.LENGTH_SHORT).show()
                    },
                    subtitle = "重新裁剪当前的背景图片",
                )
            } else {
                DividerLine(Modifier.padding(top = 12.dp))
                SettingsRow(
                    icon = Icons.Filled.Schedule,
                    title = "视频剪辑",
                    onClick = {
                        if (SettingsStore.backgroundVideoUri != null) {
                            onTrimVideo()
                        } else {
                            Toast.makeText(context, "请先选择视频", Toast.LENGTH_SHORT).show()
                        }
                    },
                    subtitle = "截取播放时段与画面",
                )
                DividerLine()
                SettingsSwitchRow(
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    title = "视频声音",
                    checked = !SettingsStore.videoBackgroundMuted,
                    onChecked = { SettingsStore.videoBackgroundMuted = !it },
                    desc = "开启或关闭视频背景的声音",
                )
                DividerLine()
                SettingsSwitchRow(
                    icon = Icons.Outlined.Loop,
                    title = "视频循环",
                    checked = SettingsStore.videoBackgroundLoop,
                    onChecked = { SettingsStore.videoBackgroundLoop = it },
                    desc = "开启或关闭视频背景的循环播放",
                )
            }
        }
    }
}

/**
 * 预览窗：图片直接解码展示；视频取 MediaMetadataRetriever 帧（裁剪后取裁剪起点帧）；
 * 未选择/解码失败时显示占位提示（Operit theme_no_bg_selected 同款文案）。
 * 注：操作入口（裁剪/声音/循环）已移至按键行下方设置行，预览窗保持纯净。
 */
@Composable
private fun BackgroundPreview(mediaType: BackgroundMediaType, uri: String?) {
    val context = LocalContext.current
    var bitmap by remember(uri, mediaType, SettingsStore.videoTrimStartSec) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri, mediaType, SettingsStore.videoTrimStartSec) {
        bitmap = if (uri != null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    when (mediaType) {
                        BackgroundMediaType.IMAGE -> decodeSampled(context, Uri.parse(uri))?.asImageBitmap()
                        BackgroundMediaType.VIDEO -> {
                            val mmr = MediaMetadataRetriever()
                            try {
                                // 直接传文件路径（file:// 经 ContentResolver 解析不可靠）
                                val path = Uri.parse(uri).path
                                if (path == null) null
                                else {
                                    mmr.setDataSource(path)
                                    // 裁剪后预览取裁剪起点帧
                                    val t = ((SettingsStore.videoTrimStartSec ?: 0f) * 1000).toLong()
                                    mmr.getFrameAtTime(t * 1000)?.asImageBitmap()
                                }
                            } finally {
                                mmr.release()
                            }
                        }
                    }
                }.getOrNull()
            }
        } else null
    }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(top = 12.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                "点击选择按钮来添加背景",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 视频裁剪对话框：截取播放时段（开始/结束双滑轨）+ 画面裁剪（原始/1:1/16:9/9:16 分段）。
 * 与未裁剪接近时视为不裁剪（存 null）。
 */
@Composable
private fun VideoTrimDialog(videoUri: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // null = 时长读取中；>0 = 就绪；读取失败 → Toast + 关闭。
    // 修复（2026-08-31）：此前 durationSec 初始 0，异步读取未完成时首次组合命中
    // "durationSec <= 0f 直接关闭"分支 → 弹窗闪现即消失，看起来像"没跳转"。
    var durationSec by remember { mutableStateOf<Float?>(null) }
    var startSec by remember { mutableStateOf(SettingsStore.videoTrimStartSec ?: 0f) }
    var endSec by remember { mutableStateOf(SettingsStore.videoTrimEndSec ?: 0f) }
    var cropMode by remember { mutableStateOf(SettingsStore.videoCropMode) }
    var speed by remember { mutableStateOf(SettingsStore.videoPlaybackSpeed) }
    LaunchedEffect(videoUri) {
        // file:// URI 经 ContentResolver 解析在 MediaMetadataRetriever 上不可靠（实测返回 0），
        // 直接传文件路径（path 重载绕过 URI 解析）。
        val path = Uri.parse(videoUri).path
        val dur = withContext(Dispatchers.IO) {
            runCatching {
                if (path == null) return@runCatching null
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(path)
                    mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toFloat()?.div(1000f)
                } finally {
                    mmr.release()
                }
            }.getOrNull() ?: 0f
        }
        if (dur > 0f) {
            durationSec = dur
            if (SettingsStore.videoTrimEndSec == null) endSec = dur
        } else {
            Toast.makeText(context, "无法读取视频时长", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dur = durationSec
    PientDialog(
        title = "视频剪辑",
        onDismiss = onDismiss,
        confirmText = "确定",
        confirmEnabled = dur != null,
        showClose = false,
        onConfirm = {
            if (dur != null) {
                SettingsStore.videoTrimStartSec = if (startSec > 0.5f) startSec else null
                SettingsStore.videoTrimEndSec = if (endSec < dur - 0.5f) endSec else null
                SettingsStore.videoCropMode = cropMode
                SettingsStore.videoPlaybackSpeed = speed
            }
            onDismiss()
        },
    ) {
        if (dur == null) {
            Text(
                "正在读取视频信息…",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            Column {
                // ── 截取播放时段 ──
                Text(
                    "开始时间",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                )
                PientSlider(value = startSec, onValueChange = { startSec = it }, valueRange = 0f..dur)
                Text(
                    "${startSec.roundToInt()} 秒",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.End),
                )
                Text(
                    "结束时间",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    modifier = Modifier.padding(top = 8.dp),
                )
                PientSlider(value = endSec, onValueChange = { endSec = it }, valueRange = 0f..dur)
                Text(
                    "${endSec.roundToInt()} 秒",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.End),
                )
                // ── 画面裁剪 ──
                Text(
                    "画面裁剪",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    modifier = Modifier.padding(top = 12.dp),
                )
                PientSegmented(
                    labels = VideoCropMode.entries.map { it.label },
                    selected = VideoCropMode.entries.indexOf(cropMode),
                    onSelect = { cropMode = VideoCropMode.entries[it] },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                // ── 视频倍速 ──
                Text(
                    "视频倍速",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    modifier = Modifier.padding(top = 12.dp),
                )
                PientSlider(value = speed, onValueChange = { speed = it }, valueRange = 0.5f..2f)
                Text(
                    "${"%.1f".format(speed)}x",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

/** 复制 uri 到应用私有 filesDir/background/（替换旧图片，保证重启后仍可解码） */
private fun copyToInternalStorage(context: android.content.Context, uri: Uri): Uri? = runCatching {
    val dir = java.io.File(context.filesDir, "background").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val ext = context.contentResolver.getType(uri)?.substringAfterLast('/') ?: "jpg"
    val file = java.io.File(dir, "bg_${System.currentTimeMillis()}.$ext")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    } ?: return null
    Uri.fromFile(file)
}.getOrNull()

/** 复制视频到应用私有 filesDir/background/（替换旧视频；media documents URI 读取不稳，内部副本保证可靠）
 *  注意：MediaMetadataRetriever 走 media server 进程（不同 uid），私有目录默认 600 读不了
 *  （实测时长读取返回 0），必须 setReadable 后 media 服务才能解析。 */
private fun copyVideoToInternalStorage(context: android.content.Context, uri: Uri): Uri? = runCatching {
    val dir = java.io.File(context.filesDir, "background").apply { mkdirs() }
    dir.listFiles()?.filter { it.name.startsWith("bg_video") }?.forEach { it.delete() }
    val ext = context.contentResolver.getType(uri)?.substringAfterLast('/')?.takeIf { it != "*" } ?: "mp4"
    val file = java.io.File(dir, "bg_video_${System.currentTimeMillis()}.$ext")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    } ?: return null
    file.setReadable(true, false) // 其它进程（media server）可读
    Uri.fromFile(file)
}.getOrNull()

/** 按目标边长等比降采样解码（预览窗只需 ~屏宽分辨率，避免大图整幅载入；实现共享于 ui/theme/BackgroundLayer.kt） */
// decodeSampled 定义移至 com.pient.app.ui.theme.decodeSampled（背景层与预览共用）

/**
 * 背景效果卡：高斯模糊（图标 + 标题/辅助说明 + 开关 + 1..25 模糊强度滑轨）
 * + 亮度（图标 + 标题/辅助说明 + 10%..150% 滑轨，默认 100%）。
 * 滑轨 = PientSlider（思考程度同款样式）；滑轨下方一行：左 = 范围标注、右 = 实时数值（主色加粗）。
 */
@Composable
private fun BackgroundEffectsCard() {
    Card {
        Column(Modifier.padding(12.dp)) {
            // ── 高斯模糊 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.BlurLinear, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text("高斯模糊", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "为背景图片或视频添加高斯模糊效果",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Switch(
                    checked = SettingsStore.backgroundBlurEnabled,
                    onCheckedChange = { SettingsStore.backgroundBlurEnabled = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                )
            }
            if (SettingsStore.backgroundBlurEnabled) {
                PientSlider(
                    value = SettingsStore.backgroundBlurRadius,
                    onValueChange = { SettingsStore.backgroundBlurRadius = it },
                    valueRange = 1f..25f,
                )
                SliderRangeRow(
                    range = "范围：1 - 25",
                    value = SettingsStore.backgroundBlurRadius.roundToInt().toString(),
                )
            }
            DividerLine(Modifier.padding(vertical = 12.dp))
            // ── 亮度 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Brightness6, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text("亮度", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "调节背景图片或视频的明暗程度",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            PientSlider(
                value = SettingsStore.backgroundBrightness,
                onValueChange = { SettingsStore.backgroundBrightness = it },
                valueRange = 0.1f..1.5f,
            )
            SliderRangeRow(
                range = "范围：10% - 150%",
                value = "${(SettingsStore.backgroundBrightness * 100).roundToInt()}%",
            )
        }
    }
}

/** 滑轨下方信息行：左侧范围标注（labelSmall 次要色）、右侧实时数值（主色加粗，同思考程度档位名） */
@Composable
private fun SliderRangeRow(range: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            range,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 字体设置标签：字体样式（内置/自定义分段 + 字体选择 + 预览）+ 字体大小（数值 + 滑轨 + 预览）
// ─────────────────────────────────────────────────────────────
/** 导入字体的 SAF MIME 过滤（仅 ttf/otf 及其 mime 变体写法，2026-08-31 用户要求只允许这两种格式） */
private val FONT_MIME_TYPES = arrayOf(
    "font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype",
    "application/vnd.ms-opentype",
)

@Composable
private fun FontTabContent() {
    // 字体选择弹窗状态提升到 LazyColumn 之外（PientDialog 的 fillMaxSize 需要页面级约束）
    var showPicker by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            // ── 字体样式 ──
            item { SectionHeader("字体样式", icon = Icons.Outlined.FontDownload) }
            item { FontStyleCard(onPickFont = { showPicker = true }) }

            // ── 字体大小 ──
            item { SectionHeader("字体大小", icon = Icons.Outlined.FormatSize) }
            item { FontSizeCard() }
        }
        if (showPicker) {
            FontPickerDialog(onDismiss = { showPicker = false })
        }
    }
}

/** 当前生效字体（共享解析：内置 → 系统字体族/文件/资源/assets；自定义 → filesDir/fonts） */
@Composable
private fun currentFontFamily(): FontFamily {
    val context = LocalContext.current
    return remember(
        SettingsStore.fontSource,
        SettingsStore.builtinFontName,
        SettingsStore.customFontPath,
    ) { resolveFontFamily(context) }
}

/** 字体样式卡：内置/自定义分段 → 字体选择行（图案 + 标题 + 下拉箭头）→ 自定义时加导入行 → 三行预览窗 */
@Composable
private fun FontStyleCard(onPickFont: () -> Unit) {
    val context = LocalContext.current
    val isBuiltin = SettingsStore.fontSource == FontSource.BUILTIN
    // 导入字体：SAF 选 ttf/otf → 复制 filesDir/fonts/ → Typeface 校验可解析 → 自动选中
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
        if (result != null) {
            val imported = copyFontToInternalStorage(context, result)
            if (imported != null) {
                SettingsStore.customFontPath = imported.second
                SettingsStore.customFontLabel = imported.first
            } else {
                Toast.makeText(context, "字体文件无效", Toast.LENGTH_SHORT).show()
            }
        }
    }
    Card {
        Column(Modifier.padding(12.dp)) {
            PientSegmented(
                labels = listOf("内置字体", "自定义字体"),
                selected = SettingsStore.fontSource.ordinal,
                onSelect = { SettingsStore.fontSource = FontSource.entries[it] },
                modifier = Modifier.fillMaxWidth(),
            )
            FontPickerRow(
                icon = Icons.Outlined.TextFields,
                label = if (isBuiltin) "内置字体" else "自定义字体",
                subtitle = if (isBuiltin) "使用系统内置的字体" else "使用导入的字体文件",
                onClick = onPickFont,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (!isBuiltin) {
                FontPickerRow(
                    icon = Icons.Outlined.Add,
                    label = "导入字体文件",
                    subtitle = "支持 TTF / OTF 格式",
                    onClick = { importLauncher.launch(FONT_MIME_TYPES) },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            FontPreview(
                family = currentFontFamily(),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** 字体选择行：图案 + 标题/辅助说明（左）+ 下拉箭头（右），点击弹出字体列表 */
@Composable
private fun FontPickerRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Icon(
            Icons.Outlined.KeyboardArrowDown, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 字体选择弹窗：内置 → 系统字体族列表；自定义 → 已导入字体列表（无则提示先导入；行内删除按钮） */
@Composable
private fun FontPickerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isBuiltin = SettingsStore.fontSource == FontSource.BUILTIN
    // 弹窗内暂存选择，确定才提交（取消丢弃）
    val current = if (isBuiltin) SettingsStore.builtinFontName else SettingsStore.customFontPath
    var pending by remember { mutableStateOf(current) }
    val options: List<Pair<String, String>> = if (isBuiltin) {
        BuiltinFonts.map { it.name to it.name }
    } else {
        // 显示名 = 导入时保存的原始文件名（无则回退去扩展名的文件名）；键 = 文件名
        java.io.File(context.filesDir, "fonts").listFiles()
            ?.filter { it.extension.lowercase() in listOf("ttf", "otf") }
            ?.sortedBy { it.lastModified() }
            ?.map {
                val label = if (SettingsStore.customFontPath == it.name) SettingsStore.customFontLabel
                    else null
                (label ?: it.nameWithoutExtension) to it.name
            }
            ?: emptyList()
    }
    // 删除导入字体：删文件 + 若为当前选中则清空（预览回退默认字体）
    val deleteFont: (String) -> Unit = { key ->
        java.io.File(context.filesDir, "fonts/$key").delete()
        if (SettingsStore.customFontPath == key) {
            SettingsStore.customFontPath = null
            SettingsStore.customFontLabel = null
        }
        if (pending == key) pending = null
    }
    PientDialog(
        title = "选择字体",
        onDismiss = onDismiss,
        showClose = false,
        onConfirm = {
            if (pending != null) {
                if (isBuiltin) SettingsStore.builtinFontName = pending!!
                else SettingsStore.customFontPath = pending
            }
            onDismiss()
        },
    ) {
        if (options.isEmpty()) {
            Text(
                "暂无自定义字体，请先导入字体文件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            Column {
                options.forEachIndexed { i, (label, key) ->
                    if (i > 0) DividerLine()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pending = key }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (!isBuiltin) {
                            IconButton(
                                onClick = { deleteFont(key) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Delete, "删除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        if (pending == key) {
                            Icon(
                                Icons.Outlined.Check, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp).padding(start = if (!isBuiltin) 4.dp else 0.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 字体样式预览窗：三行预览文本（当前语言 / 英文全字母句 / 数字） */
@Composable
private fun FontPreview(family: FontFamily, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
    ) {
        Text("这是一段预览文本，用于展示当前语言的显示效果。", style = MaterialTheme.typography.bodyMedium, fontFamily = family)
        Text("The quick brown fox jumps over the lazy dog.", style = MaterialTheme.typography.bodyMedium, fontFamily = family)
        Text("0123456789", style = MaterialTheme.typography.bodyMedium, fontFamily = family)
    }
}

/** 字体大小卡：图案 + 标题 + 右侧实时数值（sp）→ 两端 A 字滑轨 → 最小/最大标注 → 单行预览窗 */
@Composable
private fun FontSizeCard() {
    Card {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.FormatSize, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text(
                        "字体大小",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "调节界面文字的显示大小",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    "${SettingsStore.fontSize.roundToInt()}sp",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            // 滑轨：左右两端一小一大两个 A（亮度滑轨同款 PientSlider；字号取整步进；13 档节点小圆点）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("A", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PientSlider(
                    value = SettingsStore.fontSize,
                    onValueChange = {
                        SettingsStore.fontSize = it.roundToInt().toFloat()
                            .coerceIn(FONT_SIZE_MIN, FONT_SIZE_MAX)
                    },
                    valueRange = FONT_SIZE_MIN..FONT_SIZE_MAX,
                    dots = (FONT_SIZE_MAX - FONT_SIZE_MIN).toInt() + 1,
                    modifier = Modifier.weight(1f),
                )
                Text("A", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 最小/最大标注：左侧对齐小 A、右侧对齐大 A
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "${FONT_SIZE_MIN.roundToInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${FONT_SIZE_MAX.roundToInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FontSizePreview(
                family = currentFontFamily(),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** 字体大小预览窗：单行「预览文本 Preview」，按当前字号与字体渲染 */
@Composable
private fun FontSizePreview(family: FontFamily, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 20.dp),
    ) {
        Text(
            "预览文本 Preview",
            fontSize = SettingsStore.fontSize.sp,
            fontFamily = family,
        )
    }
}

/** 复制字体文件到 filesDir/fonts/ 并校验 Typeface 可解析（无效删除并返回 null）；返回 (原始文件名, 内部文件名) */
private fun copyFontToInternalStorage(context: android.content.Context, uri: Uri): Pair<String, String>? {
    val dir = java.io.File(context.filesDir, "fonts").apply { mkdirs() }
    // SAF 原始文件名（DISPLAY_NAME）作为显示名；非法字符清洗后拼入内部文件名
    val display = runCatching {
        context.contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()
    val base = display?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
        ?.replace(Regex("[\\\\/:*?\"<>|]"), "_")?.take(40)
        ?: "font"
    val ext = runCatching { context.contentResolver.getType(uri) }
        .getOrNull()
        ?.substringAfterLast('/')
        ?.lowercase()
        ?.takeIf { it == "ttf" || it == "otf" }
        ?: display?.substringAfterLast('.', "")?.lowercase()?.takeIf { it == "ttf" || it == "otf" }
        ?: "ttf"
    val file = java.io.File(dir, "font_${base}_${System.currentTimeMillis()}.$ext")
    if (context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } == null
    ) {
        return null
    }
    // 校验可解析（createFromFile 失败返回 null）；无效文件删除
    if (Typeface.createFromFile(file) == null) {
        file.delete()
        return null
    }
    return (display?.substringBeforeLast('.') ?: base) to file.name
}

// ─────────────────────────────────────────────────────────────
// 输入框设置标签：输入框样式 + 输入框材质（2026-09-12）
// ─────────────────────────────────────────────────────────────
/**
 * 输入框设置（用户 spec 2026-09-12；原「外观设置」占位标签及页面已移除）：
 * - 输入框样式：贴底输入框（默认）/ 悬浮输入框（输入框变为悬浮的全圆角矩形）；
 * - 输入框材质：默认 / 磨砂玻璃 / 液态玻璃（材质效果与依赖参考 Mdcito 的卡片风格）。
 * 两处即时生效于聊天页输入栏，并写 prefs 持久化。
 */
@Composable
private fun InputBarTabContent() {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { SectionHeader("输入框样式", icon = Icons.Outlined.SpaceBar) }
        item {
            Card {
                InputBarStyleOptions.forEachIndexed { i, option ->
                    if (i > 0) DividerLine()
                    InputBarOptionRow(
                        icon = option.icon,
                        title = option.title,
                        desc = option.desc,
                        selected = SettingsStore.inputBarStyle == option.style,
                        onClick = { SettingsStore.inputBarStyle = option.style },
                    )
                }
            }
        }

        item { SectionHeader("输入框材质", icon = Icons.Outlined.BlurOn) }
        item {
            Card {
                InputBarMaterialOptions.forEachIndexed { i, option ->
                    if (i > 0) DividerLine()
                    InputBarOptionRow(
                        icon = option.icon,
                        title = option.title,
                        desc = option.desc,
                        selected = SettingsStore.inputBarMaterial == option.material,
                        onClick = { SettingsStore.inputBarMaterial = option.material },
                    )
                }
            }
        }
    }
}

/** 输入框样式选项（顺序即卡片内排列顺序；「默认」项按用户 spec 写进标题） */
private data class InputBarStyleOption(
    val style: InputBarStyle,
    val icon: ImageVector,
    val title: String,
    val desc: String,
)

private val InputBarStyleOptions = listOf(
    InputBarStyleOption(
        style = InputBarStyle.BOTTOM,
        icon = Icons.Outlined.VerticalAlignBottom,
        title = "贴底输入框（默认）",
        desc = "输入框与屏幕底边齐平，仅上方两角圆角。",
    ),
    InputBarStyleOption(
        style = InputBarStyle.FLOATING,
        icon = Icons.Outlined.RoundedCorner,
        title = "悬浮输入框",
        desc = "输入框变为悬浮的全圆角矩形，四周留白浮在页面上。",
    ),
)

/** 输入框材质选项（说明文案沿用 Mdcito 卡片风格的同名项） */
private data class InputBarMaterialOption(
    val material: InputBarMaterial,
    val icon: ImageVector,
    val title: String,
    val desc: String,
)

private val InputBarMaterialOptions = listOf(
    InputBarMaterialOption(
        material = InputBarMaterial.DEFAULT,
        icon = Icons.Outlined.Rectangle,
        title = "默认",
        desc = "纯色面板与描边，与其他页面容器材质一致。",
    ),
    InputBarMaterialOption(
        material = InputBarMaterial.FROSTED,
        icon = Icons.Outlined.BlurLinear,
        title = "磨砂玻璃",
        desc = "半通透模糊效果，方向光照浮雕纹理。",
    ),
    InputBarMaterialOption(
        material = InputBarMaterial.LIQUID,
        icon = Icons.Outlined.WaterDrop,
        title = "液态玻璃",
        desc = "水润折射色散质感。",
    ),
)

/** 输入框设置选项行：图标 + 标题 + 说明 + 选中对勾（整行可点，选中态图标/标题变主色） */
@Composable
private fun InputBarOptionRow(
    icon: ImageVector,
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Icon(
            icon, null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            )
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp).size(18.dp),
            )
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
    ) {
        content()
    }
}
