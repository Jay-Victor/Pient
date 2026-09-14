package com.pient.app.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import com.pient.app.data.SettingsStore
import android.system.Os
import android.util.Log
import java.io.File
import java.util.zip.ZipFile

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

    /** 系统命令扩展（同上；提供 android_shell 工具，经宿主回桥执行 Shizuku / su 通道的系统命令） */
    private const val PIENT_SYSTEM_ASSET = "pient-system.ts"

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

    /**
     * 让 guest 里有 DNS：ubuntu-base 自带的 `/etc/resolv.conf` 是**空文件**（实测 0 字节），
     * 表现为 guest 内 `apt-get update` / `getent` 全部 `Temporary failure resolving …`。
     * 取系统的 DNS（ConnectivityManager → LinkProperties.dnsServers）写进去；读不到时给公共兜底。
     * 幂等：内容一致就不落盘（每次会话启动都会调一次，网络切换后自动跟上）。
     */
    fun syncResolvConf(context: Context) {
        val file = File(File(rootfsDir(context), "etc"), "resolv.conf")
        if (!file.parentFile.exists()) {
            Log.w(TAG, "rootfs 未就绪，跳过 resolv.conf：${file.absolutePath}")
            return
        }
        val servers = linkedSetOf<String>()
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
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

    /** root 侧启动器（assets 随包；运行时落到私有目录，由 `su -c "sh …"` 以 root 身份执行） */
    private const val ROOT_WRAPPER_ASSET = "pient-root-wrapper.sh"

    /**
     * 执行环境文件：`android`（系统 shell）/ `ubuntu`（PRoot）/ `ubuntu-chroot`（su + chroot）。
     * 包装脚本（pi 的 shellPath）**每次被执行时现读**它 —— 与 pi 的 bash 工具同一条链路，
     * 所以改完下一个命令/新会话即生效（已在跑的会话进程不换环境）。
     */
    fun execEnvFile(context: Context): File = File(root(context), "exec_env")

    /** 工作区路径文件（应用写：bash 工具的 cwd 与 Ubuntu 里的 /workspace 都由它定） */
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
     * 否则 PRoot 绑定挂载会失败、agent 的 cwd 也会指向不可访问的目录。
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
     * 终端层准备（宿主启动、终端会话启动、切换执行环境时各调一次；幂等）：
     * 1) guest 的 DNS —— ubuntu-base 自带的 `resolv.conf` 是空文件，不写就连 apt 都跑不动；
     * 2) root 侧启动器落盘；
     * 3) **执行环境** → `exec_env` 文件（`android` / `ubuntu` / `ubuntu-chroot`，取当前选择）。
     *
     * 环境与权限档位是两件事：档位（标准 / 调试 / Root）管「能拿到什么系统能力」，
     * 环境管「AI 的工具命令在哪跑」。chroot 环境需要 su（Root 档的通道），但选它不改档位。
     */
    fun prepareTerminal(context: Context) {
        syncResolvConf(context)
        runCatching {
            context.assets.open(ROOT_WRAPPER_ASSET).use { input ->
                File(root(context), ROOT_WRAPPER_ASSET).outputStream().use { input.copyTo(it) }
            }
        }.onFailure { Log.w(TAG, "root 侧启动器写入失败：${it.message}") }
        val env = SettingsStore.execEnv.id
        val file = execEnvFile(context)
        if (!file.parentFile.exists()) return
        runCatching {
            if (!file.exists() || file.readText().trim() != env) {
                file.writeText(env)
                Log.i(TAG, "执行环境已写入：$env")
            }
        }.onFailure { Log.w(TAG, "执行环境写入失败：${it.message}") }
        ensureRootfsAsync(context)   // 环境按需自补：Operit 口径，无需用户手动点解包（见函数注释）
        ensureAppRuntimeAsync(context)   // 宿主运行时（pi 包 + npm）同理：装完 APK 首启自己铺好
        // 宿主回桥尽早起来：系统命令通道（android_shell）与 SAF 文件桥都挂在它上面，
        // 早于 pi 宿主启动也没关系（幂等）；此前只在宿主 spawn 时才初次创建端点。
        runCatching { PiExecServer.ensureStarted(context) }
            .onFailure { Log.w(TAG, "宿主回桥启动失败：${it.message}") }
    }

    /** 解包进度/原因的公开快照（页面与终端页都要显示「正在解包 …%」） */
    @Volatile
    private var unpackNote: String = ""

    fun unpackNote(): String = unpackNote

    /**
     * **按需自动解包**（对齐 Operit 的口径：环境缺什么，用之前就地补，不需要用户手动点）。
     *
     * Operit 把 `install_ubuntu()` 写进生成的启动脚本（common.sh），首次起终端会话自动解包并把进度
     * 回显到终端；Pient 在这里做到更进一步——**只要运行时准备过一次**（App 起、开会话、进终端页、
     * 进环境配置页都会调 [prepareTerminal]），就已经在后台解包，用户连终端页都不用打开。
     *
     * 三重条件缺一不可：rootfs 未就绪 / 没有别的解包在跑 / **APK 里确实带了归档**（自建 arm64 包
     * 常见漏拉 rootfs；这种情况返回 false，由 UI 给出「换用含归档的包」的明确说明，不再静默失败）。
     */
    fun ensureRootfsAsync(context: Context): Boolean {
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

    /** 随包的 rootfs 归档（assets；构建期由 syncPientRootfsArchive 放进来） */
    private const val ROOTFS_ARCHIVE_ASSET = "pient-rootfs.tgz"   // 见 build.gradle：别用 .gz 后缀

    /** 随包的**宿主运行时**归档（pi 官方包 + npm 本体；构建期由 syncPientAppArchive 生成） */
    private const val APP_ARCHIVE_ASSET = "pient-app.tgz"

    /** npm 本体（纯 JS，随包）：pi 的包管理器经 settings 的 `npmCommand` 指到它 */
    fun npmCli(context: Context): File = File(root(context), "npm/bin/npm-cli.js")

    /**
     * **本 APK 是否内置了 rootfs 归档**。
     *
     * 为什么必须单独判一次：`syncPientRootfsArchive` 是 `onlyIf { cache 里有 ubuntu-base-*.tar.gz }` ——
     * 没跑过 `fetch_rootfs.py --abi <该 ABI>` 时任务被静默跳过，**APK 里就没有这个资产**（arm64 真机
     * 第一次踩到：界面只会说「缺少 rootfs」，点「一键配置」抛 FileNotFoundException 且当时无任何提示，
     * 表现为"点了没反应"）。有它之后 UI 才能给出准确说明。
     */
    fun rootfsArchiveAvailable(context: Context): Boolean =
        runCatching { context.assets.list("")?.contains(ROOTFS_ARCHIVE_ASSET) == true }.getOrDefault(false)

    /** 是否已有解包在跑（避免终端页 + 环境页并发解包同一份归档） */
    @Volatile
    private var unpacking = false

    fun isUnpacking(): Boolean = unpacking

    /** 宿主运行时解包中（与 rootfs 分开计数：两件事可以并行） */
    @Volatile
    private var appUnpacking = false

    fun isAppUnpacking(): Boolean = appUnpacking

    /** 本 APK 是否内置了宿主运行时归档（pi 包 + npm） */
    fun appArchiveAvailable(context: Context): Boolean =
        runCatching { context.assets.list("")?.contains(APP_ARCHIVE_ASSET) == true }.getOrDefault(false)

    /** 宿主运行时是否就绪（pi 官方包入口 + npm 本体都在） */
    fun appRuntimeReady(context: Context): Boolean = rpcEntry(context).isFile && npmCli(context).isFile

    /**
     * **按需解包宿主运行时**（pi 官方包 + npm 本体）：`assets/pient-app.tgz` → `files/pient-rt/`。
     *
     * 为什么要有它：此前 pi 包只能靠 `deploy_app_runtime.sh` 用 adb 推到设备——用户没有 adb，
     * 「装完 APK 就能起宿主」不成立。这里复用 rootfs 那条链路（AssetManager → 私有目录 →
     * toybox `gunzip | tar`，见 [extractRootfs]），一口气把 pi 包与 npm 都铺好。
     */
    fun ensureAppRuntimeAsync(context: Context): Boolean {
        if (appRuntimeReady(context) || appUnpacking) return false
        if (!appArchiveAvailable(context)) {
            Log.w(TAG, "宿主运行时未就绪，且 APK 未内置 $APP_ARCHIVE_ASSET（构建时未生成归档）")
            return false
        }
        Thread {
            appUnpacking = true
            try {
                val root = root(context)
                root.mkdirs()
                val tar = File(tmpDir(context), APP_ARCHIVE_ASSET)
                tar.parentFile?.mkdirs()
                Log.i(TAG, "宿主运行时解包开始（随包归档，首启一次性）")
                context.assets.open(APP_ARCHIVE_ASSET).use { input ->
                    tar.outputStream().use { input.copyTo(it) }
                }
                val mb = tar.length() / 1048576
                val cmd = "cd ${root.absolutePath} && gunzip -c ${tar.absolutePath} | tar -x"
                val proc = ProcessBuilder("/system/bin/sh", "-c", cmd)
                    .redirectErrorStream(true)
                    .start()
                val tail = proc.inputStream.bufferedReader().readText()
                val code = proc.waitFor()
                if (code != 0) Log.w(TAG, "宿主运行时解包退出码 $code：${tail.takeLast(300)}")
                Log.i(
                    TAG,
                    "宿主运行时解包完成（归档 $mb MB）：rpc-entry=${rpcEntry(context).isFile} " +
                        "npm=${npmCli(context).isFile}",
                )
                runCatching { tar.delete() }
            } catch (e: Exception) {
                Log.w(TAG, "宿主运行时解包异常：${e.message}")
            } finally {
                appUnpacking = false
            }
        }.apply {
            isDaemon = true
            name = "pient-app-unpack"
        }.start()
        return true
    }

    /**
     * rootfs 是否已解包且**架构正确**（终端可用性的判据）。
     *
     * 只判 `/bin/bash` 存在是不够的：单 ABI 出包装错机器时，解出来的 rootfs 是**另一个架构**的
     * （实测：x86_64 包装到 arm64 真机 → `rootfs/bin/bash` 是 x86_64 ELF，83MB 都在、bash 也在），
     * 于是 App 认为「已解包」永不重解，直到终端里出现 exec 格式错误。所以连 ELF 架构一起判，
     * 换包后自动重解（见 [ensureRootfsAsync]）。
     */
    fun rootfsReady(context: Context): Boolean =
        rootfsBash(context).isFile && elfMachine(rootfsBash(context)) == hostMachine()

    /** 本机跑得动的 ELF 架构（Debian 口径，与 `fetch_rootfs.py --abi` 一致） */
    fun hostMachine(): String = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a", "arm64" -> "aarch64"
        "x86_64" -> "x86_64"
        "armeabi-v7a", "armeabi" -> "armhf"
        else -> Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
    }

    /** ELF 的 e_machine（小端，偏移 18）：0x3E = x86_64、0xB7 = aarch64、0x28 = armhf；读不到返回 null */
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

    /** ubuntu-base 的顶层目录（解包是流式的：这些目录按序出现，可当进度刻度） */
    private val ROOTFS_TOP_ENTRIES = listOf(
        "bin", "boot", "dev", "etc", "home", "lib", "lib64", "media", "mnt",
        "opt", "proc", "root", "run", "sbin", "srv", "sys", "tmp", "usr", "var",
    )

    /**
     * 首启解包：把随包的 rootfs 归档铺到私有目录（`files/pient-rt/rootfs`）。
     *
     * 为什么由应用自己跑 `/system/bin/sh` 解包：Linux rootfs 里有符号链接、权限位、上万个文件，
     * Kotlin 侧逐个写不现实；toybox 的 `gunzip | tar -x` 在应用上下文里实测可用（就是当初手工铺
     * rootfs 用的那条路），而且**解出来的文件属主就是应用自己**——正是 PRoot 要的。
     * 进度：tar 的成员是按目录序排列的，数顶层目录出现的个数即可（比递归统计文件数便宜得多）。
     */
    fun extractRootfs(context: Context, onProgress: (Float, String) -> Unit): Boolean {
        if (unpacking) {
            onProgress(0f, "已有一个解包任务在进行中…")
            return false
        }
        if (!rootfsArchiveAvailable(context)) {
            // 极常见的自建包情形：构建时没跑 fetch_rootfs.py --abi <本机 ABI>，任务被 onlyIf 跳过
            val msg = "此 APK 未内置 rootfs 归档（构建时未拉取本机 ABI 的 rootfs：先跑 " +
                "runtime/scripts/fetch_rootfs.py --abi ${abiLabel()} 再打包）"
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
        val tar = File(tmpDir(context), "pient-rootfs.tgz")
        // **全新安装必踩**：tmp 目录此前只由 PiRpcClient（宿主启动时）创建 —— 首启自动解包跑在宿主之前，
        // 归档拷贝直接 `open failed: ENOENT (No such file or directory)`（模拟器快照回退后实测复现）。
        // 解包自己保证 tmp 存在，别依赖别的组件。
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
                // ubuntu-base 里只有两个（perl5.38.2 / uncompress 这类别名），所以真正的判据是
                // 「/bin/bash 在不在」，不是 tar 的退出码。
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

    /** Ubuntu 环境的组成件清单（环境配置页的就绪判定用；纯文件系统判定，不起进程） */
    fun ubuntuChecks(context: Context): List<Pair<String, Boolean>> {
        val rootfs = rootfsDir(context)
        val bash = rootfsBash(context)
        return listOf(
            "Ubuntu rootfs（${abiLabel()}，已解包）" to
                (rootfsReady(context) && File(rootfs, "etc/os-release").exists()),
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
            listOf(PIENT_GATE_ASSET, PIENT_SYSTEM_ASSET).forEach { asset ->
                context.assets.open(asset).use { input ->
                    File(extDir, asset).outputStream().use { input.copyTo(it) }
                }
            }
        }.onFailure { Log.w(TAG, "扩展写入失败：${it.message}") }
        val policy = File(agentDir(context), "pient_gate.json")
        if (!policy.exists()) {
            runCatching { policy.writeText(DEFAULT_TOOL_POLICY) }
                .onFailure { Log.w(TAG, "默认工具策略写入失败：${it.message}") }
        }
    }

    data class Readiness(
        val ready: Boolean,
        val missing: List<String>,
        /** 本 APK 实际带了哪些 ABI 的 pi 运行时（只在不就绪时才去读，见 [check]） */
        val apkAbis: List<String> = emptyList(),
        val deviceAbi: String = "",
        /** 首启场景：APK 里带了宿主运行时归档、但还没解包完（此时「重试启动」等一会儿就好） */
        val firstRunUnpack: Boolean = false,
    ) {
        /**
         * 设备 ABI 与 APK 内运行时 ABI 不符 —— 单 ABI 出包的必然产物：模拟器构建（x86_64）
         * 装到 arm64 真机上时，Android 只解压与设备 ABI 匹配的原生库目录，pi 的
         * node/rg/fd/… 一个都不落盘，**文件清单看起来和「真没部署」一模一样**，
         * 但点「重试启动」永远不会成功（每次都查同一批不存在的文件）。
         */
        val abiMismatch: Boolean
            get() = !ready && apkAbis.isNotEmpty() &&
                deviceAbi.isNotEmpty() && deviceAbi !in apkAbis

        val summary: String
            get() = when {
                ready -> "就绪"
                abiMismatch -> "此安装包只含 ${apkAbis.joinToString(" / ")} 的 pi 运行时，" +
                    "本机是 $deviceAbi：架构不符，node 宿主起不来。" +
                    "请改用 $deviceAbi 的安装包（构建时加 -PpientRuntimeAbi=arm64-v8a 或 x86_64）"
                firstRunUnpack -> "宿主运行时正在随包解包（首装一次性，几秒）——完成后点「重试启动」即可"
                else -> "缺少：" + missing.joinToString("、")
            }
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
        if (missing.isEmpty()) {
            ensureLinks(context)
            return Readiness(true, emptyList())
        }
        // 失败路径上才读 APK 条目：区分「真没部署」与「装错了架构」（后者重试无用，见 Readiness.abiMismatch）。
        // 列 zip 条目只读中央目录，不读内容，成本可忽略；但不放在成功热路径上。
        return Readiness(
            ready = false,
            missing = missing,
            apkAbis = apkRuntimeAbis(context),
            deviceAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            // 首启（或换包）时 pi 包还没铺开：归档在手 → 说「正在解包」，别报「缺少一大堆文件」。
            // 注意**不要**在这里排除「解包进行中」：解包就 1~2 秒，排除掉反而会让提示条在那个窗口
            // 里退回「缺少：…rpc-entry.js」（实测过一次，看着像装坏了）
            firstRunUnpack = !rpcEntry(context).exists() && appArchiveAvailable(context),
        )
    }

    /**
     * 本 APK 带了哪些 ABI 的 pi 运行时（读 APK 里 `lib/<abi>/libpient_node.so` 条目）。
     * 模拟器包只会有 `x86_64`、真机包只会有 `arm64-v8a`（单 ABI 出包，见 build.gradle.kts）。
     */
    fun apkRuntimeAbis(context: Context): List<String> = runCatching {
        val prefix = "lib/"
        val nodeLib = NATIVE_BINARIES.getValue("node")
        ZipFile(context.applicationInfo.sourceDir).use { zip ->
            val abis = linkedSetOf<String>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val name = entries.nextElement().name
                if (name.startsWith(prefix) && name.endsWith("/$nodeLib")) {
                    abis += name.removePrefix(prefix).substringBefore('/')
                }
            }
            abis.toList()
        }
    }.getOrDefault(emptyList())

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
        // 宿主回桥端点：pient-system 扩展的系统命令工具经它回调 App（Java 侧才能用 Shizuku / su）
        "PIENT_EXEC_ENDPOINT" to (PiExecServer.endpoint(context) ?: ""),
    )
}
