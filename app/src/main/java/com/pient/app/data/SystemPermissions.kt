package com.pient.app.data

import com.pient.app.data.i18n.L
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * 权限档位 —— 开发计划 6.1「三级权限体系」的 L0/L1/L2，全项目唯一文案与语义出处
 * （首启引导页 P10 的「系统权限选项页」与设置页 P8 的「系统权限设置」页共用本枚举）。
 *
 *  - STANDARD L0：Android 运行时权限 + 用户手动开启的服务，开箱即用；
 *  - DEBUGGER L1：Shizuku（ADB 级）通道，无需 Root，设备重启后需重新激活；
 *  - ROOT     L2：su 通道，能力最全、风险自评。
 */
enum class PermissionTier(
    val code: String,          // L0 / L1 / L2
    val recommended: Boolean = false,
    val warn: Boolean = false,
) {
    STANDARD(code = "L0", recommended = true),
    DEBUGGER(code = "L1"),
    ROOT(code = "L2", warn = true);

    /**
     * 卡片标题 / 说明 / 徽标。**必须是计算属性**：枚举构造参数只在类加载时求值一次，
     * 直接写 `L.…` 会把文案冻结成首帧语言（切语言不刷新）。
     */
    val title: String get() = when (this) {
        STANDARD -> L.perm.tierStandard
        DEBUGGER -> L.perm.tierDebug
        ROOT -> L.perm.tierRoot
    }

    val desc: String get() = when (this) {
        STANDARD -> L.perm.tierStandardDesc
        DEBUGGER -> L.perm.tierDebugDesc
        ROOT -> L.perm.tierRootDesc
    }

    /** 首启卡片徽标文案（推荐 / 需安装 Shizuku / ⚠ 需设备已 Root） */
    val badge: String get() = when (this) {
        STANDARD -> L.perm.recommended
        DEBUGGER -> L.perm.shizukuRequired
        ROOT -> L.perm.rootedDeviceRequired
    }
}

/**
 * 四项基础权限（存储 / 电池优化豁免 / 位置 / 悬浮窗）的**检查与授权入口唯一实现**
 * （Operit PermissionGuideViewModel.checkPermissions 同款口径）。
 *
 * 首启引导页「基础权限设置页」与设置页「系统权限设置」页都从本对象取状态、走同一套跳转，
 * 避免两处各写一份后漂移（同语义一份实现原则）。
 */
object SystemPermissions {

    /** 基础权限总数（引导页「下一步（N/4 已就绪）」与设置页状态计数共用） */
    const val TOTAL = 4

    /** 四项基础权限实时状态 */
    data class Status(
        val storage: Boolean,
        val battery: Boolean,
        val location: Boolean,
        val overlay: Boolean,
    ) {
        val readyCount: Int get() = listOf(storage, battery, location, overlay).count { it }
        val allReady: Boolean get() = readyCount == TOTAL
    }

    /** 实时读取四项授权状态（同步、主线程安全：都是本地系统查询） */
    fun status(context: Context): Status = Status(
        storage = storageGranted(context),
        battery = batteryGranted(context),
        location = locationGranted(context),
        overlay = overlayGranted(context),
    )

    /**
     * 档位就绪快照 —— 「某档位能不能作为当前档位」的**唯一判据**（用户口径 2026-09-16）：
     * 只有该档位卡里列出的**全部项**（基础权限 4 项 + 档位专属项）都配齐，才允许把它设为当前档位。
     * 「系统权限设置」页（「设为当前档位」门控 + 未配齐说明行）与首启引导页（进入 Pient 前的写入）共用本判定。
     */
    data class TierState(
        val status: Status,
        val shizukuInstalled: Boolean,
        val shizukuRunning: Boolean,
        val shizukuAuthorized: Boolean,
        val deviceRooted: Boolean,
        val rootGranted: Boolean,
    ) {
        /** 该档位权限清单是否全部配齐（标准 = 基础四项；调试 = 基础四项 + Shizuku 三步；Root = 基础四项 + Root 两项） */
        fun ready(tier: PermissionTier): Boolean = when (tier) {
            PermissionTier.STANDARD -> status.allReady
            PermissionTier.DEBUGGER -> status.allReady && shizukuInstalled && shizukuRunning && shizukuAuthorized
            PermissionTier.ROOT -> status.allReady && deviceRooted && rootGranted
        }

        /** 未配齐的原因（已配齐 = null）—— 文案复用各档位既有提示：先看基础权限，再看档位专属项 */
        fun missingHint(tier: PermissionTier): String? = when {
            ready(tier) -> null
            !status.allReady -> L.perm.basicPermissionsMissing
            tier == PermissionTier.DEBUGGER -> L.perm.shizukuSetupFirst
            else -> L.perm.rootDeviceRequired
        }
    }

    /** 读一次完整档位快照（基础四项 + Shizuku 三态 + Root 两项）；binder / 包查询走 IO 线程 */
    suspend fun tierState(context: Context): TierState = withContext(Dispatchers.IO) {
        TierState(
            status = status(context),
            shizukuInstalled = ShizukuGateway.installed(context),
            shizukuRunning = ShizukuGateway.running(),
            shizukuAuthorized = ShizukuGateway.authorized(),
            deviceRooted = RootGateway.deviceRooted(context),
            rootGranted = RootGateway.granted,
        )
    }

    /** 存储：Android 11+ 走「所有文件访问」（appops MANAGE_EXTERNAL_STORAGE）；10 及以下看运行时权限 */
    fun storageGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** 电池优化豁免：PowerManager.isIgnoringBatteryOptimizations */
    fun batteryGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }

    /** 位置：FINE 或 COARSE 任一授权即算已授权 */
    fun locationGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** 悬浮窗：Settings.canDrawOverlays */
    fun overlayGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

    // ── 授权入口（intent 直达本应用的权限详情页；返回 false = 无法跳转，由调用方 Toast） ──

    /**
     * 存储：Android 11+ 带 `package:` 的 ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
     * 会直接落到本应用的「所有文件访问」详情页；
     * **Android 10 及以下返回 false** —— 该版本没有这个页面，调用方需发起 [runtimeStoragePermissions] 运行时请求。
     */
    fun openStorageSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.parse("package:${context.packageName}")),
            )
            true
        } catch (e: Exception) {
            // 部分 ROM 无 per-app 页：回退到通用「所有文件访问」列表页
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    fun openOverlaySettings(context: Context): Boolean = try {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ),
        )
        true
    } catch (e: Exception) {
        false
    }

    fun openBatterySettings(context: Context): Boolean = try {
        // 直接请求忽略电池优化（带 package: 免去用户在列表里找应用）
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}")),
        )
        true
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            true
        } catch (e2: Exception) {
            false
        }
    }

    /** 是否走运行时权限弹窗路径（Android 10 及以下的存储权限） */
    val needsRuntimeStorage: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    /** <R 存储运行时权限组（Operit storagePermissionLauncher 同款） */
    val runtimeStoragePermissions = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )

    /** 位置运行时权限组（FINE + COARSE 一起请求，Operit locationPermissionLauncher 同款） */
    val runtimeLocationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
}

/**
 * Shizuku（L1 调试通道）状态与授权 —— 走**官方 SDK**（`dev.rikka.shizuku:api` 13.1.5，
 * 与 Operit 同版本；manifest 需声明 ShizukuProvider，缺了 provider 时 SDK 直接抛异常）。
 *
 * 三个状态的含义（对齐 Operit ShizukuAuthorizer）：
 *  - installed：设备装了 Shizuku 应用（或 Sui 已内置后端）；
 *  - running  ：Shizuku 服务在跑（binder 可达）——设备重启后为 false，需重新激活；
 *  - authorized：Pient 已获 Shizuku 授权（弹窗同意后为 true）。
 */
object ShizukuGateway {

    const val PACKAGE = "moe.shizuku.privileged.api"
    const val DOWNLOAD_URL = "https://shizuku.rikka.app/zh-hans/download/"
    const val GUIDE_URL = "https://shizuku.rikka.app/zh-hans/guide/setup/"

    /** 授权请求码（回调走 [addPermissionResultListener]） */
    const val REQUEST_CODE = 1001

    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: Exception) {
        false
    }

    /** 服务运行中：pingBinder 可达（Sui 后端退化看 binder 存活，Operit isSuiBackendAvailable 同款） */
    fun running(): Boolean = try {
        if (Shizuku.pingBinder()) true else Shizuku.getBinder()?.isBinderAlive == true
    } catch (e: Exception) {
        false
    }

    /** Pient 是否已获 Shizuku 授权 */
    fun authorized(): Boolean = try {
        running() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    /** 发起授权请求（弹 Shizuku 授权框）；服务不可用时返回 false，调用方提示先启动服务 */
    fun requestPermission(): Boolean = try {
        if (!running()) {
            false
        } else {
            Shizuku.requestPermission(REQUEST_CODE)
            true
        }
    } catch (e: Exception) {
        false
    }

    fun addPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.addRequestPermissionResultListener(listener)
        } catch (e: Exception) {
            // provider 未就绪：忽略，状态刷新会自然回落
        }
    }

    fun removePermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.removeRequestPermissionResultListener(listener)
        } catch (e: Exception) {
        }
    }

    /** 打开 Shizuku 应用（启动/重新激活服务） */
    fun openApp(context: Context): Boolean = try {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        if (intent == null) {
            false
        } else {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }
    } catch (e: Exception) {
        false
    }

    /** 打开外部链接（下载页 / 激活教程） */
    fun openUrl(context: Context, url: String): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * Root（L2 通道）检测与授权 —— 用 `su -c <cmd>` 探测（Operit RootAuthorizer 的 exec 路径，
 * 适用于 Magisk / KernelSU / APatch 等提供 su 的 Root 管理器，零额外依赖）。
 *
 * 两级语义（对齐 Operit 的 isDeviceRooted / requestRootPermission 分工）：
 *  - [deviceRooted]：**轻量检测、无副作用**（su 二进制 / Root 管理器存在性）→ 用于「设备已 Root」状态行，
 *    打开页面不会弹授权框；
 *  - [requestAccess]：**真执行一次 su**（首次会弹 Root 管理器授权框）→ 用于「请求 Root 授权」，
 *    返回 uid=0 才算已授予。
 *
 * 说明：Operit 还接了 libsu（com.github.topjohnwu.libsu，仅 JitPack 发布）；本工程不做 Root 命令执行，
 * 只做检测与授权，走 exec 探测即可，将来接 Root 执行器时替换点集中在 [runSu]。
 */
object RootGateway {

    /** 常见 su 可执行文件落点（Magisk / KernelSU / APatch） */
    private val suPaths = arrayOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
        "/debug_ramdisk/su", "/data/adb/ksu/bin/su", "/data/adb/ap/bin/su",
    )

    /** Root 管理器应用（Magisk / KernelSU / KernelSU-Next / APatch） */
    private val managerPackages = arrayOf(
        "com.topjohnwu.magisk", "me.weishu.kernelsu", "com.rifsxd.ksunext", "me.bmax.apatch",
    )

    /**
     * 设备已 Root（轻量检测，不触发授权弹窗）：
     * su 可执行文件存在，或设备装了任一 Root 管理器应用。
     */
    fun deviceRooted(context: Context): Boolean =
        suPaths.any { runCatching { java.io.File(it).exists() }.getOrDefault(false) } ||
            managerPackages.any { pkg ->
                runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
            }

    /**
     * 「**已授权**」缓存（2026-09-15 加）：[requestAccess] 真跑通过一次 su（uid=0）后置真。
     * 为什么需要它：`deviceRooted` 只说明设备**看起来**有 root，su 每次调用都可能弹授权框或被拒；
     * 而 Android shell 通道要按「现在到底能不能用」门控（工具描述、终端页选项都以它为准）。
     */
    @Volatile
    var granted: Boolean = false
        private set

    /**
     * 「**已跑过一次 su**」标记（2026-09-16 加）：区分状态行的「未验证 / 已拒绝」，
     * 也让权限页的刷新键知道该不该复检 su（未申请过就不主动弹 Root 授权框）。
     */
    @Volatile
    var probed: Boolean = false
        private set

    /** 请求 Root 授权：调用一次 su（首次触发授权弹窗），返回是否拿到 uid=0 */
    suspend fun requestAccess(): Boolean = withContext(Dispatchers.IO) {
        val ok = runSu("id")?.contains("uid=0") == true
        probed = true
        granted = ok
        ok
    }

    private fun runSu(command: String): String? = try {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (process.waitFor() == 0) output else null
    } catch (e: Exception) {
        null   // 无 su 可执行文件 / 权限被拒
    }
}
