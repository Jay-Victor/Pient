package com.pient.app.runtime

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import java.io.File

/**
 * 终端层（Ubuntu rootfs / PRoot）在设备上的落点、随包资源与**首启解包**。
 *
 * **打包形态（2026-09-14 设计基线，用户口径）**：Ubuntu 24.04 rootfs **随 APK 分发**
 * （`assets/pient-rootfs.tgz`），首次进入应用时在后台解包到应用私有目录 —— 用户不需要下载
 * Ubuntu；而 node / python 等环境**不随包**，由用户在环境配置页 / 首启弹窗里下载安装
 * （参考 Operit 的「装开发包」口径）。
 *
 * 分两处存放，**这不是随意选的位置**：
 *
 * 1. **可执行文件**（proot、它的 ELF loader、以及 pient-shell 包装脚本）必须放在应用 native lib
 *    目录（`/data/app/.../lib/<abi>/libpient_*.so`），随 APK 以 jniLibs 形式分发。
 *    原因：Android 10 起应用不得 execve 自己私有目录里的文件——SELinux 策略里
 *    `untrusted_app × app_data_file` 只给 `execute`(mmap)、不给 `execute_no_trans`(execve)，
 *    实测 avc：`denied { execute_no_trans } … permissive=0`；私有目录里的 **ELF 与 shebang 脚本
 *    两种都 exec 不了**。而 `apk_data_file`（/data/app 下的一切，含 native lib 目录）允许 execve。
 *
 * 2. **整棵 rootfs 与按 SONAME 命名的依赖软链**留应用私有目录：rootfs 里的 GNU 程序由 loader 以
 *    **mmap** 方式加载（SELinux 只拦 execve、不拦 mmap execute），所以 rootfs 不必进 APK，
 *    首启解包即可。依赖库（`libtalloc.so.2` 等）在 `usr/lib/<SONAME>` 建软链指向 native lib 里的
 *    `libpient_*.so`，映射表由构建期生成（见 build.gradle.kts 的 `writePientRuntimeLibsManifest`）。
 *
 * 首启解包触发点 = [ensureRootfsAsync]（MainActivity 启动时调用，后台线程）。
 */
object PiRuntime {

    private const val TAG = "PientTerm"

    /** 运行时根目录名（应用私有目录下） */
    const val DIR_NAME = "pient-rt"

    /** SONAME → jniLib 文件名 映射表（构建期生成，随 APK 以 assets 分发） */
    private const val LIBS_MANIFEST_ASSET = "pient_runtime_libs.txt"

    /**
     * 随包的 rootfs 归档（assets；构建期由 `syncPientRootfsArchive` 放进来）。
     * **后缀不能是 .gz**：aapt 会把 assets 里的 `*.gz` 自动解压并去掉后缀（实测 30MB 的 tar.gz
     * 变成 84MB 的 assets/pient-rootfs.tar）。
     */
    private const val ROOTFS_ARCHIVE_ASSET = "pient-rootfs.tgz"

    /**
     * 逻辑名 → native lib 文件名（jniLibs 打包规则：可执行文件也得叫 `lib*.so`）。
     * 三项都是「需要被 execve 的文件」，所以只能走 native lib 目录。
     */
    private val NATIVE_BINARIES = mapOf(
        "proot" to "libpient_proot.so",
        "proot-loader" to "libpient_proot_loader.so",
        "pient-shell" to "libpient_shell.so",
    )

    fun root(context: Context): File = File(context.filesDir, DIR_NAME)

    /** 按 SONAME 命名的依赖软链目录（PROOT 的 LD_LIBRARY_PATH 指向它） */
    fun libDir(context: Context): File = File(root(context), "usr/lib")

    /** 逻辑名软链目录（PATH 用；proot 包装脚本里的 `$P/bin/proot` 也走它） */
    fun linkDir(context: Context): File = File(root(context), "bin")

    /** PRoot 的 TMPDIR（PROOT_TMP_DIR；Android 的 /data/local/tmp 不可写） */
    fun tmpDir(context: Context): File = File(root(context), "tmp")

    /** Ubuntu rootfs 根（首启解包落点） */
    fun rootfsDir(context: Context): File = File(root(context), "rootfs")

    /** rootfs 里的 GNU bash（终端环境唯一入口） */
    fun rootfsBash(context: Context): File = File(rootfsDir(context), "bin/bash")

    /** native lib 目录里的随包可执行文件（安装时解压到 /data/app/.../lib/<abi>/） */
    fun nativeBinary(context: Context, name: String): File =
        File(context.applicationInfo.nativeLibraryDir, NATIVE_BINARIES.getValue(name))

    fun prootBinary(context: Context): File = nativeBinary(context, "proot")

    /** PRoot 的 ELF loader（走环境变量 PROOT_LOADER 告知 proot） */
    fun prootLoader(context: Context): File = nativeBinary(context, "proot-loader")

    /**
     * 终端会话的入口包装脚本：把 `pient-shell -c 命令` 转成
     * `proot … /bin/bash -c 命令`（命令真正跑在 Ubuntu 里，用户看到的与 AI 用的一致）。
     * 必须是 native lib 目录里的文件：私有目录里的 shebang 脚本不能 exec（实测 EACCES）。
     */
    fun shellPath(context: Context): File = nativeBinary(context, "pient-shell")

    /** 本机跑得动的 ELF 架构（Debian 口径，与 `fetch_rootfs.py --abi` 一致） */
    fun hostMachine(): String = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a", "arm64" -> "aarch64"
        "x86_64" -> "x86_64"
        "armeabi-v7a", "armeabi" -> "armhf"
        else -> Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
    }

    /**
     * ELF 的 e_machine（小端，偏移 18）：0x3E = x86_64、0xB7 = aarch64、0x28 = armhf；读不到返回 null。
     * 只判「文件在不在」不够：单 ABI 出包装错机器时，解出来的 rootfs 是**另一个架构**的
     * （实测 x86_64 包装到 arm64 真机：bash 在、整棵树都在），于是应用会认为「已解包」永不重解，
     * 直到终端里出现 exec 格式错误。所以连架构一起判（见 [rootfsReady]）。
     */
    fun elfMachine(file: File): String? = runCatching {
        java.io.RandomAccessFile(file, "r").use { raf ->
            val head = ByteArray(20)
            raf.readFully(head)
            when (head[18].toInt() and 0xff) {
                0x3e -> "x86_64"
                0xb7 -> "aarch64"
                0x28 -> "armhf"
                else -> null
            }
        }
    }.getOrNull()

    /** rootfs 是否已解包且**架构正确**（终端可用性的判据） */
    fun rootfsReady(context: Context): Boolean =
        rootfsBash(context).isFile && elfMachine(rootfsBash(context)) == hostMachine()

    /**
     * 本 APK 是否内置了 rootfs 归档。
     *
     * 为什么必须单独判一次：构建时没跑 `fetch_rootfs.py --abi <本机 ABI>` 的话，归档根本没进
     * assets（任务会出声失败，但自建包/换 ABI 时最容易漏），此时 UI 要能给出「这个包没带 Ubuntu」
     * 的准确说明，而不是让用户对着「点了没反应」猜。
     */
    fun rootfsArchiveAvailable(context: Context): Boolean =
        runCatching { context.assets.list("")?.contains(ROOTFS_ARCHIVE_ASSET) == true }
            .getOrDefault(false)

    /** 解包进度/原因的公开快照（终端页/环境页要显示「正在解包 …%」） */
    /**
     * rootfs 没就绪的**具体原因**（一句人话；2026-09-15）。
     * 旧写法只区分「解包中 / 其它」，于是任何"不是解包中"的情形都被说成「安装包未内置 Ubuntu」——
     * 实测误报：rootfs 明明在包里、只是 bash 不可执行/架构不符时也这么显示。
     */
    fun rootfsIssue(context: Context): String {
        val bash = rootfsBash(context)
        return when {
            !rootfsArchiveAvailable(context) -> "此安装包未内置 Ubuntu 环境（构建时未打包 rootfs 归档）"
            !bash.isFile -> "Ubuntu 运行时还没解包完（可在本页「重新检测」，或重启应用继续解包）"
            else -> "Ubuntu 运行时不可用：bash 架构 " + (elfMachine(bash) ?: "未知") +
                " ≠ 本机 " + hostMachine() + "，或文件权限异常（bash 需可读可执行）"
        }
    }
    @Volatile
    private var unpackNote: String = ""

    fun unpackNote(): String = unpackNote

    @Volatile
    private var unpacking = false

    fun isUnpacking(): Boolean = unpacking

    /** ubuntu-base 的顶层目录（解包是流式的：这些目录按序出现，可当进度刻度） */
    private val ROOTFS_TOP_ENTRIES = listOf(
        "bin", "boot", "dev", "etc", "home", "lib", "lib64", "media", "mnt",
        "opt", "proc", "root", "run", "sbin", "srv", "sys", "tmp", "usr", "var",
    )

    /**
     * **首启解包**：把随包的 rootfs 归档铺到私有目录（`files/pient-rt/rootfs`）。
     *
     * 为什么由应用自己跑 `/system/bin/sh` 解包：Linux rootfs 里有符号链接、权限位、上万个文件，
     * Kotlin 侧逐个写不现实；toybox 的 `gunzip | tar -x` 在应用上下文里实测可用，而且**解出来的
     * 文件属主就是应用自己**——正是 PRoot 要的。
     * 进度：tar 的成员按目录序排列，数顶层目录出现的个数即可（比递归统计文件数便宜得多）。
     */
    fun extractRootfs(context: Context, onProgress: (Float, String) -> Unit): Boolean {
        if (unpacking) {
            onProgress(0f, "已有一个解包任务在进行中…")
            return false
        }
        if (!rootfsArchiveAvailable(context)) {
            val msg = "此 APK 未内置 Ubuntu 环境（构建时未拉取本机 ABI 的 rootfs：" +
                "先跑 runtime/scripts/fetch_rootfs.py --abi ${abiLabel()} 再打包）"
            Log.w(TAG, msg)
            onProgress(0f, msg)
            return false
        }
        unpacking = true
        val rootfs = rootfsDir(context)
        if (!rootfs.exists() && !rootfs.mkdirs()) {
            unpacking = false
            onProgress(0f, "无法创建 ${rootfs.absolutePath}")
            return false
        }
        val tar = File(tmpDir(context), ROOTFS_ARCHIVE_ASSET)
        // tmp 目录由解包自己保证存在：首启解包跑在其它组件之前，别依赖别人先建好
        // （实测：tmp 不存在时归档拷贝直接 `open failed: ENOENT`）。
        tar.parentFile?.mkdirs()
        try {
            onProgress(0.02f, "释放归档…")
            context.assets.open(ROOTFS_ARCHIVE_ASSET).use { input ->
                tar.outputStream().use { input.copyTo(it) }
            }
            val mb = tar.length() / 1048576
            onProgress(0.05f, "解包中（$mb MB）…")
            val cmd = "cd ${rootfs.absolutePath} && gunzip -c ${tar.absolutePath} | tar -x"
            val proc = ProcessBuilder("/system/bin/sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader()
            while (proc.isAlive) {
                val done = ROOTFS_TOP_ENTRIES.count { File(rootfs, it).exists() }
                val pct = 0.05f + 0.9f * (done.toFloat() / ROOTFS_TOP_ENTRIES.size)
                onProgress(pct.coerceAtMost(0.95f), "解包中…（$done/${ROOTFS_TOP_ENTRIES.size} 顶层目录）")
                Thread.sleep(250)
            }
            val tail = runCatching { output.readText() }.getOrDefault("").trim()
            val code = proc.exitValue()
            if (code != 0) {
                // tar 对**硬链接**条目会报 Permission denied 并整体退出 1：SELinux 不允许
                // untrusted_app 建硬链接（实测 avc: denied { link } … app_data_file）。
                // ubuntu-base 里只有极少数这种条目，所以真正的判据是「/bin/bash 在不在」，
                // 不是 tar 的退出码。
                Log.w(TAG, "解包退出码 $code（多半只是硬链接被拒）：${tail.take(200)}")
            }
            val ok = rootfsReady(context)
            // 留一条可核对的证据行：终端层出问题时先看它（架构不符 / 半成品都在这里现形）
            Log.i(
                TAG,
                "解包结果 ok=$ok bash=${rootfsBash(context).isFile} " +
                    "arch=${elfMachine(rootfsBash(context))}（本机 ${hostMachine()}）",
            )
            onProgress(1f, if (ok) "解包完成" else "解包后仍未找到 /bin/bash")
            if (ok) ensurePiAsync(context)      // rootfs 一就绪就把预置的 pi 铺进 npm 全局位置
            return ok
        } catch (e: Exception) {
            Log.w(TAG, "解包异常：${e.message}")
            onProgress(0f, "解包异常：${e.message}")
            return false
        } finally {
            unpacking = false
            runCatching { tar.delete() }
        }
    }

    /**
     * **按需自动解包**（Operit 口径：环境缺什么，用之前就地补，不需要用户手动点）。
     *
     * Operit 把 `install_ubuntu()` 写进生成的启动脚本（`common.sh`），首次起终端会话时自动解包、
     * 进度回显在终端里；这里更进一步——应用一启动就在后台解（用户连终端页都不用打开）。
     *
     * 三个条件缺一不可：rootfs 未就绪 / 没有别的解包在跑 / **APK 里确实带了归档**。
     */
    fun ensureRootfsAsync(context: Context): Boolean {
        // 每次启动先把软链对一遍：**APK 更新后 /data/app 路径会变**，旧链会悬空
        // （实测：重装后 `env: exec …/bin/proot: No such file or directory`，
        //  要等首次进终端页才被 prepareTerminal 修好 —— 应用启动就该修）
        ensureLinks(context)
        installPiExtension(context)   // 应用启动即装（内容变了才重写；rootfs 没就绪也只是先写好文件）
        if (rootfsReady(context) || isUnpacking()) return false
        if (!rootfsArchiveAvailable(context)) {
            Log.w(TAG, "rootfs 未就绪，且 APK 未内置归档（构建时没跑 fetch_rootfs.py --abi ${abiLabel()}）")
            return false
        }
        Thread {
            // 遗留的异构 rootfs（换包/换架构）与上次中断的半成品：**先清空再解**，
            // 别往一棵架构不对的树里覆盖写（会留下混合内容，最难查）。只有「归档在手」才会走到这。
            val dir = rootfsDir(context)
            if (dir.isDirectory) {
                Log.w(TAG, "rootfs 不可用（bash 架构 ${elfMachine(rootfsBash(context))} ≠ 本机 ${hostMachine()}）→ 清空重解")
                runCatching { dir.deleteRecursively() }
                    .onFailure { Log.w(TAG, "清空 rootfs 失败：${it.message}") }
            }
            ensureLinks(context)
            Log.i(TAG, "自动解包开始（后台，无需用户操作）")
            val ok = extractRootfs(context) { pct, text -> unpackNote = "${(pct * 100).toInt()}% · $text" }
            unpackNote = if (ok) "" else unpackNote
            Log.i(TAG, if (ok) "自动解包完成：${rootfsBash(context).absolutePath}" else "自动解包失败：$unpackNote")
        }.apply {
            isDaemon = true
            name = "pient-rootfs-autounpack"
        }.start()
        return true
    }

    /** 当前出包的 ABI（单 ABI 出包，取主 ABI 即是本包运行时的架构） */
    private fun abiLabel(): String = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "arm64"
        "x86_64" -> "amd64"
        "armeabi-v7a" -> "armhf"
        else -> Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    }

    /** 运行时库映射（APK assets 里的表；缺失时返回空表） */
    private fun runtimeLibs(context: Context): List<Pair<String, String>> = runCatching {
        context.assets.open(LIBS_MANIFEST_ASSET).bufferedReader().useLines { seq ->
            seq.mapNotNull { line ->
                val parts = line.trim().split(' ').filter { it.isNotEmpty() }
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toList()
        }
    }.getOrDefault(emptyList())

    /**
     * 建/更新软链（幂等；**每次启动重指一遍**——APK 更新后 /data/app 路径会变，旧链会悬空）：
     * - `bin/<name>` → native lib 里的可执行文件（proot 包装脚本按 `$P/bin/proot` 找它）；
     * - `usr/lib/<SONAME>` → native lib 里的 `libpient_*.so`（PRoot 的动态依赖）。
     */
    fun ensureLinks(context: Context) {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        // 先清掉「已退场运行时」留下的旧链（APK 更新会让 /data/app 路径变化）——悬空链会让
        // 包装脚本 exec 直接失败（实测 `env: exec …/bin/proot: No such file or directory`）。
        linkDir(context).listFiles()?.forEach { f ->
            if (f.name in NATIVE_BINARIES) return@forEach
            if (runCatching { Os.readlink(f.absolutePath) }.isSuccess) {
                runCatching { f.delete() }
            }
        }
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

    // ─────────────── 终端会话的外围准备：DNS / root 启动器 / 执行环境 / 工作区 ───────────────

    /** root 侧启动器（assets 随包；运行时落到私有目录，由 `su -c "sh …"` 以 root 身份执行） */
    private const val ROOT_WRAPPER_ASSET = "pient-root-wrapper.sh"

    /** 随包工作区（没设过项目目录时的默认 cwd；guest 里 bind 到 /workspace） */
    fun appDir(context: Context): File = File(root(context), "app")

    /**
     * 执行环境文件：`android`（系统 shell，需 Shizuku / Root）/ `ubuntu`（PRoot，默认）/
     * `ubuntu-chroot`（su + chroot）。包装脚本**每次被执行时现读**它 —— 改完下一条命令/新会话
     * 即生效（已在跑的会话进程不换环境）。与权限档位是两条轴：档位管「能拿到什么系统能力」，
     * 这里管「命令在哪跑」。
     */
    fun execEnvFile(context: Context): File = File(root(context), "exec_env")

    /** 工作区路径文件（会话的 cwd 与 guest 的 /workspace 都由它定） */
    fun workspaceFile(context: Context): File = File(root(context), "workspace")

    /** 当前工作区目录；没设过/路径已消失时退回随包工作区 `app/` */
    fun workspaceDir(context: Context): File {
        val raw = runCatching { workspaceFile(context).takeIf { it.isFile }?.readText()?.trim() }.getOrNull()
        val dir = raw?.takeIf { it.isNotBlank() }?.let { File(it) }
        return if (dir != null && dir.isDirectory) dir else appDir(context)
    }

    /**
     * 设置工作区。**只接受真实存在的目录**：SAF 项目拿不到文件系统路径（`content://`，或位于
     * /sdcard 而应用受分区存储限制够不到），那种情况保持随包工作区 —— 别把够不到的路径写进去，
     * 否则 PRoot 绑定挂载会失败、cwd 也会指向不可访问的目录。
     */
    fun setWorkspace(context: Context, dir: File?) {
        val target = dir?.takeIf { it.isDirectory } ?: appDir(context)
        runCatching {
            val f = workspaceFile(context)
            if (!f.exists() || f.readText().trim() != target.absolutePath) {
                f.writeText(target.absolutePath)
                Log.i(TAG, "工作区已设为：${target.absolutePath}")
            }
        }.onFailure { Log.w(TAG, "工作区写入失败：${it.message}") }
    }

    /**
     * 让 guest 里有 DNS：ubuntu-base 自带的 `/etc/resolv.conf` 是**空文件**（实测 0 字节），
     * 表现为 guest 内 `apt-get update` / `getent` 全部 `Temporary failure resolving …`。
     * 取系统的 DNS（ConnectivityManager → LinkProperties.dnsServers）写进去；读不到时给公共兜底。
     * 幂等：内容一致就不落盘（会话启动都会调一次，网络切换后自动跟上）。
     */
    fun syncResolvConf(context: Context) {
        val file = File(File(rootfsDir(context), "etc"), "resolv.conf")
        if (!file.parentFile.exists()) {
            Log.w(TAG, "rootfs 未就绪，跳过 resolv.conf：${file.absolutePath}")
            return
        }
        val servers = linkedSetOf<String>()
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            cm.allNetworks.forEach { network ->
                cm.getLinkProperties(network)?.dnsServers?.forEach { addr ->
                    addr.hostAddress?.takeIf { it.isNotBlank() }?.let(servers::add)
                }
            }
        }.onFailure { Log.w(TAG, "读取系统 DNS 失败：${it.message}") }
        if (servers.isEmpty()) servers.addAll(listOf("223.5.5.5", "8.8.8.8"))
        val text = servers.joinToString("") { "nameserver $it" + "\n" }
        val shown = servers.joinToString(" ")
        runCatching {
            if (!file.exists() || file.readText() != text) {
                file.writeText(text)
                Log.i(TAG, "resolv.conf 已写入：$shown")
            }
        }.onFailure { Log.w(TAG, "写 resolv.conf 失败：${it.message}") }
    }

    /**
     * 终端层准备（应用启动 / 会话启动 / 切执行环境时各调一次；幂等）：
     * 1) guest 的 DNS；2) root 侧启动器落盘；3) 执行环境 → `exec_env`；4) 软链对一遍；
     * 5) 环境按需自补（rootfs 缺失时后台解包）。
     */
    fun prepareTerminal(context: Context) {
        syncResolvConf(context)
        runCatching {
            context.assets.open(ROOT_WRAPPER_ASSET).use { input ->
                File(root(context), ROOT_WRAPPER_ASSET).outputStream().use { input.copyTo(it) }
            }
        }.onFailure { Log.w(TAG, "root 侧启动器写入失败：${it.message}") }
        val env = com.pient.app.data.SettingsStore.execEnv.id
        val file = execEnvFile(context)
        if (!file.parentFile.exists()) return
        runCatching {
            if (!file.exists() || file.readText().trim() != env) {
                file.writeText(env)
                Log.i(TAG, "执行环境已写入：$env")
            }
        }.onFailure { Log.w(TAG, "执行环境写入失败：${it.message}") }
        appDir(context).mkdirs()
        ensureLinks(context)
        installPiExtension(context)
        // Android shell 回桥的端点文件（要求 5）：rootfs 刚解包好时这里补写一次 ——
        // 应用启动那一刻 rootfs 可能还没就绪，端点文件当时写不进去（父目录不存在）。
        ExecBridge.writeEndpointFile(context)
        ensureRootfsAsync(context)
        ensurePiAsync(context)      // rootfs 未就绪时它会直接返回，等解包完的链式调用再补
    }

    /**
     * 会话进程的宿主侧环境。`LD_LIBRARY_PATH` 是必需的——PRoot 的动态依赖（libtalloc 等）指向
     * `usr/lib` 里的按 SONAME 命名的**软链**（最终目标在 native lib 目录），系统 linker 的默认
     * 搜索路径里没有。`PATH` 里放链接目录。guest 侧环境由包装脚本用 `env -i` 显式给，不透传这些。
     */
    // ─────────────── pi 预置包（随 APK，首启解进 Ubuntu 的 npm 全局位置） ───────────────

    /** 随包的 pi 归档（assets；构建期由 `syncPientPiArchive` 生成） */
    private const val PI_ARCHIVE_ASSET = "pient-pi.tgz"

    /** rootfs 里的 **npm 全局目录**（预置归档的落点；`npm i -g` 也装这里） */
    fun nodeModulesDir(context: Context): File = File(rootfsDir(context), "usr/lib/node_modules")

    /**
     * pi 包的落点（npm 全局布局下的 `@earendil-works/pi-coding-agent`）：解出来就等同
     * 「`npm i -g` 装过」—— 用户装完 node 就能直接 `pi`，而「更新 pi」只要再跑一次
     * `npm install -g --ignore-scripts @earendil-works/pi-coding-agent`（装到同一处直接覆盖；
     * pi 带 npm-shrinkwrap.json，实测 `npm i -g` 同样是「包 + 内嵌 node_modules」的布局）。
     */
    fun piDir(context: Context): File =
        File(nodeModulesDir(context), "@earendil-works/pi-coding-agent")

    /** pi 的 CLI 入口（npm 包 `bin.pi` = `dist/bundle/cli.js`，自包含 bundle，无需 node_modules） */
    fun piEntry(context: Context): File = File(piDir(context), "dist/bundle/cli.js")

    /** pi 是否就绪（rootfs 已解 + pi 文件在） */
    fun piReady(context: Context): Boolean = rootfsReady(context) && piEntry(context).isFile

    /** 本 APK 是否内置了 pi 归档 */
    fun piArchiveAvailable(context: Context): Boolean =
        runCatching { context.assets.list("")?.contains(PI_ARCHIVE_ASSET) == true }.getOrDefault(false)

    /** Pient 的 pi 扩展文件名（注册 `/pient-nav`，补 RPC 缺的"会话内分支跳转"） */
    private const val PI_EXTENSION_ASSET = "pient-pi-extension.ts"

    /** guest 里扩展该落的位置（pi 的全局扩展目录，jiti 直接加载，不需要编译） */
    fun piExtensionFile(context: Context): File =
        File(rootfsDir(context), "root/.pi/agent/extensions/pient.ts")

    /**
     * 把 Pient 的 pi 扩展装进 guest（**幂等**：内容变了才重写）。
     *
     * 为什么走扩展：pi 官方 RPC 有树/分叉的大部分命令，却没有「把活跃叶移到树里另一个节点」
     * （那是 TUI `/tree` 的能力，只作为内部 API `ctx.navigateTree` 暴露给扩展）。
     * Pient 的「会话内分支」正是它，所以用一条扩展命令 `/pient-nav <entryId>` 补上，
     * 再经 RPC 的 `prompt` 触发。
     */
    fun installPiExtension(context: Context): Boolean = runCatching {
        val target = piExtensionFile(context)
        val text = context.assets.open(PI_EXTENSION_ASSET).use {
            it.readBytes().toString(Charsets.UTF_8)
        }
        if (target.isFile && target.readText() == text) return@runCatching true
        target.parentFile?.mkdirs()
        target.writeText(text)
        Log.i(TAG, "pi 扩展已安装：${target.absolutePath}")
        true
    }.getOrElse {
        Log.w(TAG, "pi 扩展安装失败：${it.message}")
        false
    }

    /** pi 的版本（读解出来的 package.json；读不到返回空串） */
    fun piVersion(context: Context): String = runCatching {
        Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")
            .find(File(piDir(context), "package.json").readText())?.groupValues?.get(1).orEmpty()
    }.getOrDefault("")

    /** pi 解包中（与 rootfs 分开计数：两件事可以接力） */
    @Volatile
    private var piUnpacking = false

    fun isPiUnpacking(): Boolean = piUnpacking

    /**
     * **首启把预置的 pi 解进 rootfs 的 npm 全局目录**，并建 `/usr/bin/pi` 软链（相对链接，npm 同款）。
     * rootfs 未就绪时返回 false —— 调用点有两条：应用启动时直接调（[MainActivity]）与 rootfs
     * 解包完成后的链式调用（见 [extractRootfs] 的成功分支），保证「先解 Ubuntu、再铺 pi」。
     */
    fun ensurePiAsync(context: Context): Boolean {
        if (piReady(context) || piUnpacking) return false
        if (!rootfsReady(context)) return false
        if (!piArchiveAvailable(context)) {
            Log.w(TAG, "pi 未预置，且 APK 未内置 $PI_ARCHIVE_ASSET（构建时未生成归档）")
            return false
        }
        Thread {
            piUnpacking = true
            try {
                // 归档布局 = npm 全局 node_modules 的内容（@earendil-works/… 顶层），所以解到 node_modules
                val dir = nodeModulesDir(context)
                if (File(dir, "@earendil-works/pi-coding-agent").isDirectory) {
                    // 上次中断的半成品：先清空再解（判据是 piEntry，走到这里说明它不在）
                    Log.w(TAG, "pi 目录不完整 → 清空重解：${piDir(context).absolutePath}")
                    runCatching { File(dir, "@earendil-works/pi-coding-agent").deleteRecursively() }
                }
                dir.mkdirs()
                val tar = File(tmpDir(context), PI_ARCHIVE_ASSET)
                tar.parentFile?.mkdirs()
                context.assets.open(PI_ARCHIVE_ASSET).use { input ->
                    tar.outputStream().use { input.copyTo(it) }
                }
                val cmd = "cd ${dir.absolutePath} && gunzip -c ${tar.absolutePath} | tar -x"
                val proc = ProcessBuilder("/system/bin/sh", "-c", cmd)
                    .redirectErrorStream(true)
                    .start()
                val out = proc.inputStream.bufferedReader().readText()
                val code = proc.waitFor()
                if (code != 0) Log.w(TAG, "pi 解包退出码 $code：${out.takeLast(200)}")
                // /usr/bin/pi → 相对软链（与 npm 的做法一致）
                val link = File(rootfsDir(context), "usr/bin/pi")
                runCatching {
                    link.delete()
                    Os.symlink(
                        "../lib/node_modules/@earendil-works/pi-coding-agent/dist/bundle/cli.js",
                        link.absolutePath,
                    )
                }.onFailure { Log.w(TAG, "pi 软链建立失败：${it.message}") }
                // **必须显式加执行位**：Android 侧 tar 解出来的文件是 0600（umask 077），
                // 少了 +x 就是 `/usr/bin/pi: Permission denied`（实测踩过）。
                listOf("dist/bundle/cli.js", "dist/bundle/rpc-entry.js").forEach { rel ->
                    val exe = File(piDir(context), rel)
                    if (exe.isFile) {
                        runCatching { Os.chmod(exe.absolutePath, 0b111101101) }   // 0755
                    }
                }
                runCatching { tar.delete() }
                Log.i(
                    TAG,
                    "pi 预置完成 ok=${piReady(context)} version=${piVersion(context)} " +
                        "exe=${piEntry(context).canExecute()}",
                )
            } catch (e: Exception) {
                Log.w(TAG, "pi 解包异常：${e.message}")
            } finally {
                piUnpacking = false
            }
        }.apply {
            isDaemon = true
            name = "pient-pi-unpack"
        }.start()
        return true
    }

    fun environment(context: Context): Map<String, String> = mapOf(
        "LD_LIBRARY_PATH" to libDir(context).absolutePath,
        "PATH" to "${linkDir(context).absolutePath}:" +
            System.getenv("PATH").orEmpty().ifEmpty { "/system/bin:/system/xbin" },
        "HOME" to File(root(context), "home").absolutePath,
        "TMPDIR" to tmpDir(context).absolutePath,
    )

    /**
     * **应用内部**调用 guest 时的环境变量：强制 Ubuntu（PRoot）。
     *
     * 为什么必须强制：`exec_env` 是给「用户/终端页/工具命令」选的落点（Ubuntu PRoot / Ubuntu chroot /
     * Android shell）。但 **pi 自己**（App 的 RPC 通道、`pi list/install/remove`、技能扫描脚本）永远
     * 只能跑在 Ubuntu 里 —— node 与 pi 都装在那棵 rootfs 里；一旦跟着 `exec_env=android` 走，
     * 通道会以「Android 上没有 pi」的方式整体失效（实测前的设计约束，别省这一步）。
     *
     * @param execEnv 传 `ubuntu-chroot` 可让 pi 走 chroot（只在明确的场景下用）
     */
    fun guestEnv(context: Context, execEnv: String = "ubuntu"): Map<String, String> =
        environment(context) + mapOf("PIENT_EXEC_ENV" to execEnv)
}
