package com.pient.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pient.app.R
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.theme.DarkBrandPurple
import com.pient.app.ui.theme.LightBrandPurple
import com.pient.app.ui.theme.LocalPientIsDark
import com.pient.app.ui.theme.PientPanel
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch

/**
 * 首启引导页（P10，设计计划第 2 章）
 * 三页顺序：欢迎页 → 基础权限设置页 → 系统权限选项页，全程不可跳过权限页。
 * - 整体：玻璃卡片浮于品牌渐变背景之上（Hermes 同源）
 * - 基础权限页为真实系统授权（2026-09-02 接入，Operit 同款口径）：存储/悬浮窗/
 *   电池优化豁免跳系统设置页，位置走运行时权限弹窗；进入页面与从设置返回（ON_RESUME）自动重检。
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val purple = if (LocalPientIsDark.current) DarkBrandPurple else LightBrandPurple

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.background,
                    0.55f to MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    1f to purple.copy(alpha = 0.16f),
                ),
            ),
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
        ) { p ->
            when (p) {
                0 -> WelcomePage(onNext = { page = 1 })
                1 -> PermissionPage(
                    onNext = { page = 2 },
                    onBackToWelcome = { page = 0 },
                )
                else -> SystemPermissionPage(
                    onDone = onDone,
                    onBackToWelcome = { page = 0 },
                    onBackToBasics = { page = 1 },
                )
            }
        }
    }
}

// ═══════════════════════ 第 1 页：欢迎页 ═══════════════════════

@Composable
private fun WelcomePage(onNext: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 上方：logo + "Pient"（2026-09-01 重做：原大号渐变字样改为 logo + 产品名；无入场动画）
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val purple = if (LocalPientIsDark.current) DarkBrandPurple else LightBrandPurple
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(96.dp),
                        shape = CircleShape,
                        tonalElevation = 1.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.pient_logo),
                                contentDescription = null,
                                modifier = Modifier.size(96.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Pient",
                        style = TextStyle(
                            brush = Brush.linearGradient(
                                listOf(MaterialTheme.colorScheme.primary, purple),
                            ),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 48.sp,
                            letterSpacing = (-1).sp,
                        ),
                    )
                }
            }

            // 产品介绍卡片（4 条：图标 + 标题 + 加长辅助说明，2026-09-01 文字量增加）
            PientPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FeatureLine(Icons.Outlined.SmartToy, "AI Agent 工作台", "基于 Pi Agent 的移动端工作台，支持深度任务编排与多步推理，全程本地运行")
                FeatureLine(Icons.Outlined.Extension, "插件与 Skill 生态", "完整兼容 Pi 官方插件体系与 Skill 机制，桌面端生态能力无降级迁移")
                FeatureLine(Icons.Outlined.Lock, "本地优先 · 数据自持", "会话与配置全部存储于设备本地，数据不上云、完全自持，隐私可控")
                FeatureLine(Icons.Outlined.Shield, "三级权限体系", "标准 / 调试 / Root 三级权限按任务动态授权，兼顾能力与安全")
            }
            }

            Spacer(Modifier.height(20.dp))
            PientButton(
                "确定",
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                height = 48,
            )
        }
    }
}

@Composable
private fun FeatureLine(icon: ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// ═══════════════════════ 第 2 页：基础权限设置页 ═══════════════════════

private data class PermItem(
    val key: String,
    val name: String,
    val desc: String,
    val icon: ImageVector,
)

private val permItems = listOf(
    PermItem("storage", "存储权限", "读取项目文件 / 会话数据", Icons.Outlined.Storage),
    PermItem("battery", "电池优化豁免", "保活 / 后台任务", Icons.Outlined.BatterySaver),
    PermItem("location", "位置权限", "位置相关工具调用", Icons.Outlined.LocationOn),
    PermItem("overlay", "悬浮窗权限", "悬浮终端 / 快捷面板", Icons.Outlined.OpenInFull),
)

@Composable
private fun PermissionPage(onNext: () -> Unit, onBackToWelcome: () -> Unit) {
    val context = LocalContext.current
    val granted = remember { mutableStateMapOf<String, Boolean>() }
    var checking by remember { mutableStateOf(false) }
    var lastCheck by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // ── 真实权限状态检查（Operit PermissionGuideViewModel.checkPermissions 同款口径）──
    fun checkPermissions() {
        // 存储：Android 11+ 走「所有文件访问」；10 及以下走运行时权限
        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
        val hasBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
        val hasLocation =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
        granted["storage"] = hasStorage
        granted["battery"] = hasBattery
        granted["location"] = hasLocation
        granted["overlay"] = hasOverlay
    }

    // Android 10 及以下：存储运行时权限请求（Operit storagePermissionLauncher 同款）
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { checkPermissions() }

    // 位置运行时权限请求（Operit locationPermissionLauncher 同款：FINE + COARSE 一起请求）
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { checkPermissions() }

    // ── 系统设置页请求（Operit PermissionGuideScreen 同款：intent 直接跳设置）──
    fun requestStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .setData(Uri.parse("package:${context.packageName}")),
                )
            } catch (e: Exception) {
                // 回退到通用「所有文件访问」设置页
                try {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (e2: Exception) {
                    Toast.makeText(context, "无法打开存储权限设置", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            storageLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ),
            )
        }
    }

    fun requestOverlay() {
        try {
            // 直接使用包名跳转到悬浮窗权限页面
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开悬浮窗权限设置", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestBattery() {
        try {
            // 直接请求忽略电池优化，无需用户搜索应用
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${context.packageName}")),
            )
        } catch (e: Exception) {
            // 直接请求失败时回退到电池优化设置列表页
            try {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(context, "无法打开电池优化设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 进入页面即检查；从系统设置/系统弹窗返回后 ON_RESUME 自动重检（v1 真实实现）
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) { checkPermissions() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 32.dp),
    ) {
        // 步骤指示器 + 标题（指示器可点击切页：未完成基础权限前无法跳到系统权限页）
        StepHeader(
            current = 1,
            title = "基础权限设置",
            onSelect = { target ->
                when (target) {
                    0 -> onBackToWelcome()
                    2 -> {
                        checkPermissions()
                        if (granted.values.all { it }) {
                            onNext()
                        } else {
                            Toast.makeText(context, "请先完成基础权限授权", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            permItems.forEach { item ->
                val ok = granted[item.key] == true
                PermissionCard(
                    icon = item.icon,
                    name = item.name,
                    desc = item.desc,
                    granted = ok,
                    onGrant = {
                        when (item.key) {
                            "storage" -> requestStorage()
                            "battery" -> requestBattery()
                            "location" -> locationLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                            else -> requestOverlay()
                        }
                    },
                )
            }
        }

        // 检查权限状态（2026-09-01 重做交互：点击 → 重检动画 → 结果摘要；不再只显示时间）
        // 状态行：未检查 / 检查中 / 上次检查结果
        val ready = permItems.count { granted[it.key] == true }
        Text(
            when {
                checking -> "正在检查权限状态…"
                lastCheck != null -> "上次检查：$lastCheck  ·  $ready/4 项已授权"
                else -> "权限状态实时展示 · 点击下方重检"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
        )
        PientButton(
            if (checking) "正在检查…" else "检查权限状态",
            onClick = {
                checking = true
                scope.launch {
                    // 真实重检（Operit onRefresh 同款：同步读取系统当前授权状态）
                    checkPermissions()
                    lastCheck = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                        .format(java.util.Date())
                    checking = false
                }
            },
            primary = false,
            enabled = !checking,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        )
        PientButton(
            if (ready == 4) "下一步" else "下一步（$ready/4 已就绪）",
            onClick = onNext,
            enabled = ready == 4,
            modifier = Modifier.fillMaxWidth(),
            height = 48,
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    name: String,
    desc: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    PientPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(
            icon, null,
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(name, style = MaterialTheme.typography.labelLarge)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (granted) {
            Text(
                "已授权 ✓",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onGrant)
                    .padding(6.dp),
            ) {
                Text(
                    "去授权 →",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    " ⚠",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    }
}

// ═══════════════════════ 第 3 页：系统权限选项页 ═══════════════════════

@Composable
private fun SystemPermissionPage(
    onDone: () -> Unit,
    onBackToWelcome: () -> Unit,
    onBackToBasics: () -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }

    val options = listOf(
        Triple("标准权限", "使用系统标准权限模型：网络、存储、通知等常规授权，无需安装任何额外工具，覆盖日常 Agent 任务所需能力", "推荐"),
        Triple("调试权限", "借助 Shizuku 获得 ADB 级调试能力：UI 自动化、应用管理、系统设置读写；无需解锁 Bootloader，设备重启后需重新激活", "需安装 Shizuku"),
        Triple("Root 权限", "以 Root 身份运行：chroot 容器、系统级文件操作与完整工具链；权限等级最高、能力全部解锁，安全风险需自行评估", "⚠ 需设备已 Root"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 32.dp),
    ) {
        // 步骤指示器 + 标题（指示器可点击回退到前两页）
        StepHeader(
            current = 2,
            title = "系统权限选项",
            onSelect = { target ->
                when (target) {
                    0 -> onBackToWelcome()
                    1 -> onBackToBasics()
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEachIndexed { i, opt ->
                val sel = i == selected
                val borderColor = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(if (sel) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
                        .background(
                            if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
                            RoundedCornerShape(16.dp),
                        )
                        .clickable(onClick = { selected = i })
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            opt.first,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        // 推荐徽标
                        if (opt.third == "推荐") {
                            Text(
                                "〔推荐〕",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        // 单选圆点
                        Box(
                            Modifier
                                .padding(start = 10.dp)
                                .size(16.dp)
                                .border(1.5.dp, borderColor, CircleShape)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent),
                        )
                    }
                    Text(
                        opt.second,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (opt.third == "需安装 Shizuku") {
                        Text(
                            opt.third,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (opt.third.startsWith("⚠")) {
                        Text(
                            opt.third,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        PientButton(
            "确定，进入 Pient",
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            height = 48,
        )
    }
}

// ───────────────────────────── 步骤指示器 ─────────────────────────────

@Composable
private fun StepHeader(current: Int, title: String, onSelect: (Int) -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(3) { i ->
                val active = i == current
                // 点击热区 28dp（远大于 7/10dp 圆点，移动端整块可点红线）
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(if (active) 10.dp else 7.dp)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            ),
                    )
                }
                if (i < 2) {
                    Box(
                        Modifier
                            .widthIn(min = 18.dp, max = 18.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
