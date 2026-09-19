package com.pient.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 检查更新状态：打开即重新查；每个分支都有终态，不会卡在「检查中」 */
sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState

    data object Checking : UpdateCheckState

    /** 已是最新（版本号取本机安装包） */
    data object Latest : UpdateCheckState

    /** 远端有更新的版本 */
    data class Available(val info: UpdateInfo) : UpdateCheckState

    /** 没取到清单（没网 / 清单不存在）—— 如实报「检查更新失败」，不当作「已是最新」 */
    data object Failed : UpdateCheckState
}

/** 远端「可更新」的完整信息：版本 + 更新内容 + 安装包地址与备用源 */
data class UpdateInfo(
    val versionName: String,
    val notes: List<String>,
    val releaseDate: String,
    val downloadPageUrl: String,
    val apkUrl: String,
    val apkSize: Long,
    val mirrors: List<MirrorSource>,
) {
    /** 落盘文件名：从直链末段取（带回退名，保证是个 .apk） */
    val fileName: String
        get() = apkUrl.substringAfterLast('/').substringBefore('?')
            .takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: "Pient-$versionName.apk"
}

/**
 * 更新中心：检查更新、下载安装包、发起安装的状态与动作（弹窗与关于页都走它，开屏自动检查也走它）。
 *
 * 三块状态都是 Compose 状态，浮层直接读：
 * - [checkState] 检查结果（四态）；
 * - [dialogVisible] 弹窗是否显示（开屏自动检查也要能弹，所以状态提升到这一层、由根布局渲染）；
 * - 下载进度在 [ApkDownloader.state]。
 */
object UpdateCenter {

    private const val TAG = "PientUpdate"

    var checkState by mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle)
        private set

    var dialogVisible by mutableStateOf(false)
        private set

    /** 点「立即安装」但系统还没给「安装未知应用」权限时为 true（弹窗里显示安装权限卡） */
    var installPermissionNeeded by mutableStateOf(false)
        private set

    /** 开屏自动检查只自动弹一次窗（用户关掉后不在本次运行里再打扰） */
    private var autoChecked = false

    /** 下载协程的载体：进程级 scope —— 关掉弹窗/离开页面也不该把下载掐掉 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ─────────────────────── 检查 ───────────────────────

    /** 打开弹窗（不发起检查时用，例如从失败态重新点进来） */
    fun open() {
        dialogVisible = true
    }

    fun dismissDialog() {
        dialogVisible = false
        installPermissionNeeded = false
    }

    /**
     * 查远端版本清单并与本机安装版本比对。
     *
     * [auto] = 开屏自动检查：只有查到更新的版本才弹窗（已是最新/失败都不打扰），且本次运行只自动弹一次。
     */
    fun check(context: Context, auto: Boolean = false) {
        if (!auto) {
            dialogVisible = true
        }
        checkState = UpdateCheckState.Checking
        scope.launch {
            val remote = AppUpdate.fetchLatest()
            val local = AppUpdate.localVersion(context)
            val state = when {
                local == null || remote == null -> UpdateCheckState.Failed
                // 平台答上来了但还没发过 release：没有更新的版本（不是失败）
                remote is AppUpdate.LatestRelease.None -> UpdateCheckState.Latest
                else -> {
                    val found = remote as AppUpdate.LatestRelease.Found
                    if (AppUpdate.isNewer(found.versionName, local.first)) {
                        UpdateCheckState.Available(
                            UpdateInfo(
                                versionName = found.versionName,
                                notes = found.notes,
                                releaseDate = found.releaseDate,
                                downloadPageUrl = found.releaseUrl,
                                apkUrl = found.apkUrl,
                                apkSize = found.apkSize,
                                mirrors = found.mirrors,
                            ),
                        )
                    } else {
                        UpdateCheckState.Latest
                    }
                }
            }
            checkState = state
            if (auto && state is UpdateCheckState.Available) {
                dialogVisible = true
            }
        }
    }

    /** 开屏调用：设置里开着「自动检查更新」且本次运行还没查过时，后台查一次 */
    fun autoCheckIfEnabled(context: Context) {
        if (autoChecked || !SettingsStore.updateAutoCheck) return
        autoChecked = true
        check(context, auto = true)
    }

    // ─────────────────────── 下载 ───────────────────────

    /**
     * 开始下载安装包。[mirrorUrl] 非空 = 用户点了某个镜像按键，从它开始。
     *
     * 按「指定镜像 → 直链 → 其余镜像」排队依次试，某个源失败自动换下一个（换源重下前会清掉旧文件，
     * 不同源的包内容可能不同，不能接着断点续传）。
     */
    fun startDownload(context: Context, mirrorUrl: String? = null) {
        val info = (checkState as? UpdateCheckState.Available)?.info ?: return
        val queue = buildUrlQueue(info, mirrorUrl)
        if (queue.isEmpty()) return
        scope.launch {
            for ((index, url) in queue.withIndex()) {
                ApkDownloader.reset()
                ApkDownloader.clearFiles(context)
                if (index > 0) PientLog.w(TAG, "上一个下载源失败，改试第 ${index + 1}/${queue.size} 个源：$url")
                val path = ApkDownloader.download(context, url, info.fileName)
                if (path != null) return@launch
            }
            PientLog.w(TAG, "全部 ${queue.size} 个下载源都失败")
        }
    }

    private fun buildUrlQueue(info: UpdateInfo, mirrorUrl: String?): List<String> {
        val queue = ArrayList<String>()
        fun add(url: String) {
            if (url.isNotBlank() && url !in queue) queue += url
        }
        if (mirrorUrl != null) add(mirrorUrl)
        add(info.apkUrl)
        info.mirrors.forEach { add(it.url) }
        return queue
    }

    fun pauseDownload() = ApkDownloader.pause()

    fun resumeDownload() = ApkDownloader.resume()

    fun cancelDownload(context: Context) = ApkDownloader.cancel(context)

    // ─────────────────────── 安装 ───────────────────────

    /**
     * 把已下载的安装包交给系统安装器。
     *
     * Android 8 起要用户先给「安装未知应用」权限（`canRequestPackageInstalls`）——
     * 没给就只把 [installPermissionNeeded] 置上，由弹窗显示安装权限卡去引导，不静默失败。
     */
    fun install(context: Context, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            PientLog.w(TAG, "安装包不存在：$filePath")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            installPermissionNeeded = true
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            installPermissionNeeded = false
            PientLog.i(TAG, "已交给系统安装器：$filePath")
        }.onFailure { PientLog.w(TAG, "调起安装器失败：${it.message}") }
    }

    /** 跳「安装未知应用」授权页（带包名，直接落在 Pient 那一项） */
    fun openInstallPermissionSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.onFailure { PientLog.w(TAG, "打开安装权限设置失败：${it.message}") }
    }
}
