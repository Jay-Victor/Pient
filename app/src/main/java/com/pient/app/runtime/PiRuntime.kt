package com.pient.app.runtime

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import java.io.File

/**
 * pi 宿主运行时（工具层）在设备上的落点与就绪判定。
 *
 * **打包形态 = 混合（2026-09-12 拍板）**：随 APK 带 `node` + 它的动态依赖 + `rg`/`fd`
 * （离线即可起宿主），pi 官方 npm 包按需获取（体积大、可独立升级）。
 *
 * 分两处存放，**这不是随意选的位置**：
 *
 * 1. **可执行文件**（Node 宿主、rg、fd）——必须放在应用 native lib 目录
 *    （`/data/app/.../lib/<abi>/libpient_*.so`），随 APK 以 jniLibs 形式分发。
 *    原因：Android 10 起应用不得 execve 自己私有目录里的文件——SELinux 策略里
 *    `untrusted_app × app_data_file` 只给 `execute` 不给 `execute_no_trans`，
 *    实测 avc：`denied { execute_no_trans } ... tclass=file permissive=0`；
 *    而 `appdomain × apk_data_file`（即 /data/app 下的一切，含 native lib 目录）
 *    是给 `execute_no_trans` 的，所以 lib 目录里的二进制可以 exec。
 *
 * 2. **被加载的库与数据**（HOME/TMPDIR、pi 包、以及**指向库的软链**）——放应用私有目录：
 * ```
 * files/pient-rt/
 *   usr/lib/<SONAME>      → 软链到 native lib 里的 libpient_*.so（Node 动态依赖，LD_LIBRARY_PATH 指向它）
 *   app/node_modules/…    pi 官方包 @earendil-works/pi-coding-agent（按需下载/更新）
 *   bin/{node,rg,fd}      → 指向 native lib 目录二进制的软链（供 PATH 查找 rg/fd）
 *   home/  tmp/           HOME / TMPDIR
 * ```
 * 为什么要软链：native lib 目录里只能叫 `lib*.so`（jniLibs 规则），而
 * ① pi 的 grep/find 用 `spawn("rg")` 走 PATH 找 **`rg`/`fd`** 这个名字；
 * ② Node 的动态依赖按 **SONAME** 找（`libicuuc.so.78`、`libz.so.1`…），带后缀的名字
 *    连 ASO 都不收，构建期只能改名成 `libpient_*.so`。
 * SELinux 校验的是软链的**最终目标**（apk_data_file），所以经软链 exec/mmap 都成立。
 * 运行时库的对应关系由构建期生成的 assets 表 `pient_runtime_libs.txt` 给出（见 `syncPientRuntimeLibs`）。
 */
object PiRuntime {

    private const val TAG = "PiHost"
    const val DIR_NAME = "pient-rt"

    /** 官方 RPC 入口（pi 包内自带 bundle） */
    private const val RPC_ENTRY =
        "node_modules/@earendil-works/pi-coding-agent/dist/bundle/rpc-entry.js"

    /** SONAME → jniLib 文件名 映射表（构建期生成，随 APK 以 assets 分发） */
    private const val LIBS_MANIFEST_ASSET = "pient_runtime_libs.txt"

    /** 权限守门扩展（assets 里随包，同步到宿主 HOME 的 extensions/ 下） */
    private const val PIENT_GATE_ASSET = "pient-gate.ts"

    /** 逻辑名 → native lib 文件名（jniLibs 打包规则：二进制改名成 lib*.so） */
    private val NATIVE_BINARIES = mapOf(
        "node" to "libpient_node.so",
        "rg" to "libpient_rg.so",
        "fd" to "libpient_fd.so",
        // 终端层：PRoot 与它的 ELF loader（两者都要被 execve，只能走 native lib 目录）。
        // loader 也在 bin/ 下留一条软链，PROOT_LOADER 直接指过去（命令行里好写、且随 APK 更新自动重指）。
        "proot" to "libpient_proot.so",
        "proot-loader" to "libpient_proot_loader.so",
        // pi 的 bash 工具 shellPath（把命令交给 rootfs 里的 GNU bash；见 runtime/terminal/pient-shell.sh）
        "pient-shell" to "libpient_shell.so",
    )

    fun root(context: Context): File = File(context.filesDir, DIR_NAME)
    fun usrDir(context: Context): File = File(root(context), "usr")
    fun libDir(context: Context): File = File(usrDir(context), "lib")
    fun appDir(context: Context): File = File(root(context), "app")

    /** 符号链接目录（PATH 用） */
    fun linkDir(context: Context): File = File(root(context), "bin")

    /** native lib 目录里的可执行文件（随 APK 分发，安装时解压到 /data/app/.../lib/<abi>/） */
    fun nativeBinary(context: Context, name: String): File =
        File(context.applicationInfo.nativeLibraryDir, NATIVE_BINARIES.getValue(name))

    fun nodeBinary(context: Context): File = nativeBinary(context, "node")
    fun rpcEntry(context: Context): File = File(appDir(context), RPC_ENTRY)

    /** 宿主 HOME（pi 的配置/会话/凭据都落这里，与桌面 pi/pi-web 文件同构） */
    fun homeDir(context: Context): File = File(root(context), "home")

    /** 宿主 TMPDIR（Node 的 os.tmpdir() 默认指向 /data/local/tmp，不可写） */
    fun tmpDir(context: Context): File = File(root(context), "tmp")

    /**
     * 终端层：Ubuntu rootfs 根目录（首启解包；rootfs 里的 GNU 程序由 PRoot 的 loader 以
     * **mmap** 方式加载——SELinux 对私有目录只拦 execve、不拦 mmap execute，所以 rootfs
     * 可以留在私有目录，不必像 node/proot 那样进 APK 的 native lib 目录）。
     */
    fun rootfsDir(context: Context): File = File(root(context), "rootfs")

    /** 终端环境的检测清单（环境配置页用；纯文件系统判定，不起进程） */
    fun terminalChecks(context: Context): List<Pair<String, Boolean>> {
        val rootfs = rootfsDir(context)
        val bash = rootfsBash(context)
        return listOf(
            "Ubuntu 24.04 rootfs（${abiLabel()}，已解包）" to (bash.isFile && File(rootfs, "etc/os-release").exists()),
            "GNU bash + coreutils（minbase）" to (bash.isFile && File(rootfs, "usr/bin/env").isFile),
            "PRoot 运行时（proot + ELF loader）" to (prootBinary(context).isFile && prootLoader(context).isFile),
            "shell 包装脚本（随 APK 分发）" to shellPath(context).isFile,
        )
    }

    /** 当前出包的 ABI（单 ABI 出包，取主 ABI 即是本包运行时的架构） */
    private fun abiLabel(): String = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "arm64"
        "x86_64" -> "amd64"
        "armeabi-v7a" -> "armhf"
        else -> Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    }

    /** rootfs 里的 GNU bash（终端环境唯一入口，pi 的 bash 工具 shellPath 指它） */
    fun rootfsBash(context: Context): File = File(rootfsDir(context), "bin/bash")

    /** PRoot 的 ELF loader（走环境变量 PROOT_LOADER 告知 proot，默认值写死在 Termux 前缀里） */
    fun prootLoader(context: Context): File = nativeBinary(context, "proot-loader")

    /**
     * pi bash 工具的 shellPath（终端层的接缝）：一个随 APK 分发的包装脚本，
     * 把 `spawn(shellPath, ["-c", 命令])` 转成 `proot … /bin/bash -c 命令` —— 命令真正跑在 Ubuntu 里。
     * 必须是 native lib 目录里的文件：私有目录里的 shebang 脚本同样不能 exec（实测 EACCES）。
     */
    fun shellPath(context: Context): File = nativeBinary(context, "pient-shell")

    fun prootBinary(context: Context): File = nativeBinary(context, "proot")

    /** 宿主 HOME 下的 pi 目录（models/auth/会话/扩展都在这里，与桌面 pi 同构） */
    fun agentDir(context: Context): File = File(homeDir(context), ".pi/agent")

    /** pi 会话目录（`~/.pi/agent/sessions`，与桌面 pi/pi-web 同构；会话文件为 JSONL 格式 v3） */
    fun sessionsDir(context: Context): File = File(agentDir(context), "sessions")

    /** 默认工具策略（开发计划 §6.3：全局默认 ASK；只读四工具默认 ALLOW，写入/执行类走 ASK） */
    const val DEFAULT_TOOL_POLICY =
        "{\"default\":\"ASK\",\"tools\":{\"read\":\"ALLOW\",\"grep\":\"ALLOW\",\"find\":\"ALLOW\",\"ls\":\"ALLOW\"}}"

    /**
     * 随包资源同步进宿主 HOME：
     * - `extensions/pient-gate.ts` 权限守门扩展——**每次启动覆盖**（跟 App 版本走）；
     * - `pient_gate.json` 工具授权策略——**只在缺失时写默认**（用户选择不能被启动流程抹掉）。
     */
    fun syncAgentAssets(context: Context) {
        val extDir = File(agentDir(context), "extensions")
        if (!extDir.exists() && !extDir.mkdirs()) {
            Log.w(TAG, "扩展目录创建失败：${extDir.absolutePath}")
            return
        }
        runCatching {
            context.assets.open(PIENT_GATE_ASSET).use { input ->
                File(extDir, PIENT_GATE_ASSET).outputStream().use { input.copyTo(it) }
            }
        }.onFailure { Log.w(TAG, "权限守门扩展写入失败：${it.message}") }
        val policy = File(agentDir(context), "pient_gate.json")
        if (!policy.exists()) {
            runCatching { policy.writeText(DEFAULT_TOOL_POLICY) }
                .onFailure { Log.w(TAG, "默认工具策略写入失败：${it.message}") }
        }
    }

    data class Readiness(val ready: Boolean, val missing: List<String>) {
        val summary: String
            get() = if (ready) "就绪" else "缺少：" + missing.joinToString("、")
    }

    /** 运行时库映射（APK assets 里的表；缺失时返回空表——退回「自己往 usr/lib 部署」的旧路径） */
    private fun runtimeLibs(context: Context): List<Pair<String, String>> = runCatching {
        context.assets.open(LIBS_MANIFEST_ASSET).bufferedReader().useLines { seq ->
            seq.mapNotNull { line ->
                val parts = line.trim().split(' ').filter { it.isNotEmpty() }
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toList()
        }
    }.getOrDefault(emptyList())

    /** 就绪判定：native 二进制三项 + 随包运行时库 + RPC 入口（+ 缺失时补建软链） */
    fun check(context: Context): Readiness {
        val libDir = context.applicationInfo.nativeLibraryDir
        val missing = mutableListOf<String>()
        NATIVE_BINARIES.forEach { (_, fileName) ->
            if (!File(libDir, fileName).exists()) missing += "lib/$fileName"
        }
        val libs = runtimeLibs(context)
        val libsMissing = libs.count { !File(libDir, it.second).exists() }
        if (libsMissing > 0) missing += "随包运行时库缺 $libsMissing 个"
        if (!rpcEntry(context).exists()) missing += "app/$RPC_ENTRY"
        if (missing.isEmpty()) ensureLinks(context)
        return Readiness(missing.isEmpty(), missing)
    }

    /**
     * 建/更新软链（幂等；**每次启动重指一遍**——APK 更新后 /data/app 路径会变，旧链会悬空）：
     * - `bin/<name>` → native lib 里的可执行文件（供 PATH 查找 `rg`/`fd`）；
     * - `usr/lib/<SONAME>` → native lib 里的 `libpient_*.so`（Node 的动态依赖，见 [environment]）。
     * 同名真实文件（早期部署脚本放进去的那份）会被软链覆盖：库一律以 APK 内的为准。
     */
    fun ensureLinks(context: Context) {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        NATIVE_BINARIES.forEach { (name, fileName) ->
            link(File(nativeDir, fileName), File(linkDir(context), name))
        }
        runtimeLibs(context).forEach { (soname, jniName) ->
            link(File(nativeDir, jniName), File(libDir(context), soname))
        }
    }

    /** 建/更新一条软链：目标一致就跳过，否则删掉重建（含悬空链与同名真实文件） */
    private fun link(target: File, link: File) {
        if (!target.exists()) return
        link.parentFile?.mkdirs()
        // Os.readlink 判「这个路径本身就是软链」（File.exists() 会跟随链接 → 悬空链判不存在）
        val current = runCatching { Os.readlink(link.absolutePath) }.getOrNull()
        if (current == target.absolutePath) return
        if (current != null || link.exists()) runCatching { link.delete() }
        runCatching { Os.symlink(target.absolutePath, link.absolutePath) }
            .onFailure { Log.w(TAG, "建链接失败 ${link.name}：${it.message}") }
    }

    /**
     * 宿主的执行环境。`LD_LIBRARY_PATH` 是必需的——宿主 Node 的动态依赖指向
     * `usr/lib`（该目录里是按 SONAME 命名的**软链**，最终目标在 native lib 目录，
     * 随 APK 分发），系统 linker 的默认搜索路径里没有（实测：不设该变量时 exec 直接报
     * `CANNOT LINK EXECUTABLE ... library "libz.so.1" not found`）。
     * `PATH` 里放链接目录：pi 的 grep/find 用 PATH 找 `rg`/`fd`。
     */
    fun environment(context: Context): Map<String, String> = mapOf(
        "LD_LIBRARY_PATH" to libDir(context).absolutePath,
        "PATH" to "${linkDir(context).absolutePath}:" +
            System.getenv("PATH").orEmpty().ifEmpty { "/system/bin:/system/xbin" },
        "HOME" to homeDir(context).absolutePath,
        "TMPDIR" to tmpDir(context).absolutePath,
    )
}
