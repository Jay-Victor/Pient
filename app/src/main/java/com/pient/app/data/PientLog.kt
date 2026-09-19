package com.pient.app.data

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 应用日志（「应用日志管理」功能的地基）。
 *
 * **为什么需要它**：`android.util.Log` 只进系统 logcat，
 * 用户手上没有任何通道能拿到（不接 adb 就等于没有日志）。本对象把每一次调用
 * **同时**做两件事：① 转发 `android.util.Log`（adb 工作流不变、tag 不变）；
 * ② 追加落盘 `files/logs/pient.log`（滚动：单文件 2MB、保留 [MAX_BACKUPS] 份），
 * 于是「设置 → 数据与权限 → 应用日志管理」能把日志导成文件发出去。
 *
 * 形状对齐 `android.util.Log`（`i/w/e(tag, msg)`、`e(tag, msg, tr)`）——调用点机械替换即可，
 * 不发明新的日志 DSL。异步单线程落盘 + 单条消息截断 + 崩溃保留上一轮日志；
 * 不做 per-package 分文件（Pient 没有 toolpkg 那层）。
 *
 * 线程模型：写入走一条 daemon 单线程 + 队列 —— 聊天流式期间每次 Log 都同步落盘会顶到调用线程。
 * 崩溃路径例外：用 [flushBlocking] 同步落盘（进程马上要死，队列来不及），见 [installCrashHandler]。
 *
 * 行格式：
 *     `2026-09-17 20:45:12.123 I/PientChat: 消息正文`
 * 多行消息（堆栈）保持原样续行 —— 续行没有级别前缀，读的时候按「前缀匹配的行」切段。
 */
object PientLog {

    private const val DIR_NAME = "logs"
    private const val FILE_NAME = "pient.log"

    /** 单文件上限（超过就轮转） */
    private const val MAX_FILE_BYTES = 2L * 1024 * 1024

    /** 轮转保留份数（pient.log.1 / .2）——总量上限 = 3 × 2MB */
    private const val MAX_BACKUPS = 2

    /** 单条消息上限：整份文件内容这类超长正文不该整段灌进日志（12k） */
    private const val MAX_MESSAGE_CHARS = 12_000

    private const val FLUSH_WAIT_MS = 300L
    private const val MAX_BATCH = 512

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val queue = LinkedBlockingQueue<String>()
    private val lock = Any()

    @Volatile
    private var app: Context? = null

    @Volatile
    private var worker: Thread? = null

    // ───────────────────────── 启停 ─────────────────────────

    /**
     * 应用启动时调用一次（[com.pient.app.MainActivity.onCreate]，越早越好）：
     * 注入上下文、开写入线程、挂崩溃记录、写一行启动标记（分隔每次运行，读日志时一眼看到边界）。
     */
    fun install(context: Context) {
        if (app != null) return
        app = context.applicationContext
        readyDir(context)
        ensureWorker()
        installCrashHandler()
        i(
            TAG, "── 应用启动 · Pient ${appVersion(context)} · " +
                "${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()} · " +
                "档位=${SettingsStore.permissionTier.code} · API ${Build.VERSION.SDK_INT} ──",
        )
    }

    private const val TAG = "PientLog"

    /** `0.1.0 (1)`：导出文件的头部与应用内诊断共用一处（别在别处再拼一次） */
    fun appVersion(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }
        "${info.versionName} ($code)"
    }.getOrDefault("?")

    // ───────────────────────── 写日志（对齐 android.util.Log） ─────────────────────────

    fun v(tag: String, msg: String) = write('V', tag, msg, null)
    fun d(tag: String, msg: String) = write('D', tag, msg, null)
    fun i(tag: String, msg: String) = write('I', tag, msg, null)
    fun w(tag: String, msg: String) = write('W', tag, msg, null)
    fun e(tag: String, msg: String) = write('E', tag, msg, null)
    fun w(tag: String, msg: String, tr: Throwable?) = write('W', tag, msg, tr)
    fun e(tag: String, msg: String, tr: Throwable?) = write('E', tag, msg, tr)

    private fun write(level: Char, tag: String, msg: String, tr: Throwable?) {
        val text = if (tr == null) msg else msg + "\n" + Log.getStackTraceString(tr)
        // ① logcat（原样，保持既有 adb 取证口径）
        when (level) {
            'V' -> Log.v(tag, text)
            'D' -> Log.d(tag, text)
            'W' -> Log.w(tag, text)
            'E' -> Log.e(tag, text)
            else -> Log.i(tag, text)
        }
        // ② 文件（未 install 时静默丢弃：启动最早那一瞬的日志不值得为它建目录）
        if (app == null) return
        val body = if (text.length > MAX_MESSAGE_CHARS) {
            text.take(MAX_MESSAGE_CHARS) + "…（已截断，原 ${text.length} 字）"
        } else {
            text
        }
        queue.offer("${stamp()} $level/$tag: $body")
        ensureWorker()
    }

    /** 崩溃路径：把队列里压着的行同步写完（调用者随后就把进程交还给系统了） */
    fun flushBlocking() {
        if (app == null) return
        val sb = StringBuilder()
        while (true) {
            val line = queue.poll() ?: break
            sb.append(line).append('\n')
        }
        if (sb.isNotEmpty()) appendToFile(sb.toString())
    }

    /**
     * 未捕获异常 → 崩溃栈落进日志文件（用户下次进「应用日志管理」就能导出这次崩溃）。
     *
     * 只加记录、不改系统行为：先按原样转交给上一个 handler（Android 默认那个会打 logcat 并杀进程）；
     * 万一没有上一个（极罕见），自己杀 —— 不能把异常吞掉让应用带着坏状态继续跑。
     */
    private fun installCrashHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, err ->
            runCatching {
                write(
                    'E', "PientCrash",
                    "未捕获异常（线程 ${thread.name}）：${err.javaClass.name}: ${err.message}", err,
                )
                flushBlocking()
            }
            if (prev != null) {
                prev.uncaughtException(thread, err)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    // ───────────────────────── 文件读写 ─────────────────────────

    fun dir(context: Context): File = File(context.filesDir, DIR_NAME)

    /** 确保目录存在（不建目录时每次落盘都是 ENOENT，日志一条都写不出来） */
    private fun readyDir(ctx: Context): File {
        val d = dir(ctx)
        if (!d.isDirectory) runCatching { d.mkdirs() }
        return d
    }

    fun file(context: Context): File = File(dir(context), FILE_NAME)

    /** 现存日志文件，**从旧到新**（pient.log.2 → pient.log.1 → pient.log） */
    fun logFiles(context: Context): List<File> {
        val d = dir(context)
        return ((MAX_BACKUPS downTo 1).map { File(d, "$FILE_NAME.$it") } + File(d, FILE_NAME))
            .filter { it.isFile }
    }

    data class Stats(
        val bytes: Long,
        val lines: Int,
        /** 首行时间戳（`yyyy-MM-dd HH:mm:ss.SSS`，无日志时为空串） */
        val oldest: String,
        val newest: String,
        val files: Int,
    )

    /** 页面状态卡用：总量 / 行数 / 覆盖时间段。调用方放 IO 线程。 */
    fun stats(context: Context): Stats {
        var bytes = 0L
        var lines = 0
        var oldest = ""
        var newest = ""
        val files = logFiles(context)
        files.forEach { f ->
            bytes += f.length()
            runCatching {
                f.forEachLine { raw ->
                    val line = raw.trimEnd('\r')
                    if (line.isBlank()) return@forEachLine
                    lines++
                    val stamp = line.take(23)
                    if (isStamp(stamp)) {
                        if (oldest.isEmpty()) oldest = stamp
                        newest = stamp
                    }
                }
            }
        }
        return Stats(bytes, lines, oldest, newest, files.size)
    }

    /** 查看页/导出用：取末尾 [maxLines] 行（跨轮转文件，从新往旧凑） */
    fun readTail(context: Context, maxLines: Int): List<String> {
        val out = ArrayDeque<String>()
        for (f in logFiles(context).asReversed()) {
            val lines = runCatching { f.readLines() }.getOrDefault(emptyList())
            for (i in lines.indices.reversed()) {
                if (out.size >= maxLines) return out.toList()
                out.addFirst(lines[i].trimEnd('\r'))
            }
        }
        return out.toList()
    }

    /**
     * 全部日志文本（当前文件 + 轮转文件，**从旧到新**）——导出用。调用方放 IO 线程。
     * 段与段之间插一条轮转标记，读的人才知道这里换过文件。
     */
    fun readAll(context: Context): String {
        val files = logFiles(context)
        if (files.isEmpty()) return ""
        if (files.size == 1) return runCatching { files[0].readText() }.getOrDefault("")
        val sb = StringBuilder()
        files.forEachIndexed { idx, f ->
            if (idx > 0) sb.append("---- ${f.name} ----\n")
            sb.append(runCatching { f.readText().trimEnd('\n') }.getOrDefault("")).append('\n')
        }
        return sb.toString()
    }

    /** 清空：删掉全部日志文件（含轮转）。队列里压着的旧行也丢掉，别让它们把文件重新写出来。 */
    fun clear(context: Context) {
        synchronized(lock) {
            queue.clear()
            logFiles(context).forEach { runCatching { it.delete() } }
        }
    }

    private fun isStamp(s: String): Boolean =
        s.length == 23 && s[4] == '-' && s[10] == ' ' && s[13] == ':' && s[16] == ':'

    // ───────────────────────── 行解析（查看页 / 导出范围过滤共用一份） ─────────────────────────

    /**
     * 一条日志记录：`level`/`timeMs`/`tag` 取首行（续行 —— 堆栈、轮转标记 —— 为 null），
     * `lines` 是整条（含续行）。切段规则与文件格式同口径，见本对象头注释。
     */
    data class Record(val level: Char?, val timeMs: Long?, val tag: String?, val lines: List<String>)

    /** `2026-09-17 20:45:12.123 I/PientChat: 正文` → 'I'；不是行首前缀返回 null */
    fun levelOf(line: String): Char? {
        if (line.length < 26) return null
        if (line[4] != '-' || line[7] != '-' || line[10] != ' ' || line[13] != ':' ||
            line[16] != ':' || line[19] != '.' || line[23] != ' ' || line[25] != '/'
        ) {
            return null
        }
        val lv = line[24]
        return if (lv in "VDIWEA") lv else null
    }

    private fun timeOf(line: String): Long? {
        if (levelOf(line) == null) return null
        return synchronized(timeFormat) {
            runCatching { timeFormat.parse(line.take(23))?.time }.getOrNull()
        }
    }

    private fun tagOf(line: String): String? {
        if (levelOf(line) == null) return null
        val rest = line.substring(26)
        val idx = rest.indexOf(':')
        return if (idx <= 0) null else rest.take(idx)
    }

    fun parseRecords(raw: List<String>): List<Record> {
        val out = ArrayList<Record>()
        var cur: MutableList<String>? = null
        var level: Char? = null
        var timeMs: Long? = null
        var tag: String? = null
        fun flush() {
            cur?.let { out.add(Record(level, timeMs, tag, it)) }
            cur = null
        }
        raw.forEach { rawLine ->
            val line = rawLine.trimEnd('\r')
            val lv = levelOf(line)
            when {
                lv != null -> {
                    flush()
                    cur = mutableListOf(line)
                    level = lv
                    timeMs = timeOf(line)
                    tag = tagOf(line)
                }
                // 轮转标记自成一"条"（别把 `---- pient.log.1 ----` 粘到上一条的正文里）
                line.startsWith("---- ") -> {
                    flush()
                    out.add(Record(null, null, null, listOf(line)))
                }
                cur == null -> out.add(Record(null, null, null, listOf(line)))
                else -> cur!!.add(line)
            }
        }
        flush()
        return out
    }

    fun render(records: List<Record>): String =
        records.joinToString("\n") { it.lines.joinToString("\n") }

    /** 级别过滤：`minLevel = 'W'` 时保留 W/E 与其续行（整条一起进出） */
    fun atLeastLevel(records: List<Record>, minLevel: Char): List<Record> {
        val order = "VDIWEA"
        val min = order.indexOf(minLevel)
        return records.filter { it.level != null && order.indexOf(it.level) >= min }
    }

    /** 时间过滤：首行时间戳 ≥ [sinceMs] 的整条（时间戳缺失的行只在 sinceMs 为 0 时保留） */
    fun since(records: List<Record>, sinceMs: Long): List<Record> =
        records.filter { (it.timeMs ?: Long.MIN_VALUE) >= sinceMs }

    private fun stamp(): String = synchronized(timeFormat) { timeFormat.format(Date()) }

    // ───────────────────────── 写入线程 ─────────────────────────

    private fun ensureWorker() {
        if (worker?.isAlive == true) return
        synchronized(lock) {
            if (worker?.isAlive == true) return
            worker = Thread({ loop() }, "PientLogWriter").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun loop() {
        val sb = StringBuilder()
        while (true) {
            val first = try {
                queue.poll(FLUSH_WAIT_MS, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                null
            } ?: continue
            sb.setLength(0)
            sb.append(first).append('\n')
            var n = 0
            while (n < MAX_BATCH) {
                val line = queue.poll() ?: break
                sb.append(line).append('\n')
                n++
            }
            appendToFile(sb.toString())
        }
    }

    private fun appendToFile(text: String) {
        val ctx = app ?: return
        synchronized(lock) {
            runCatching {
                val f = File(readyDir(ctx), FILE_NAME)
                if (f.length() + text.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES) rotate(ctx)
                FileOutputStream(f, true).use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }.onFailure {
                // 落盘失败只回 logcat（**不能**再调 PientLog，会递归）
                Log.w(TAG, "日志落盘失败：${it.message}")
            }
        }
    }

    /** 轮转：pient.log.2 删、.1 → .2、当前 → .1，并往新文件里写一条标记 */
    private fun rotate(ctx: Context) {
        val d = readyDir(ctx)
        runCatching { File(d, "$FILE_NAME.$MAX_BACKUPS").delete() }
        for (i in MAX_BACKUPS - 1 downTo 1) {
            val from = File(d, "$FILE_NAME.$i")
            if (from.isFile) runCatching { from.renameTo(File(d, "$FILE_NAME.${i + 1}")) }
        }
        val cur = file(ctx)
        if (cur.isFile) runCatching { cur.renameTo(File(d, "$FILE_NAME.1")) }
        runCatching {
            FileOutputStream(cur, true).use {
                it.write("${stamp()} I/$TAG: 日志已轮转（${FILE_NAME}.1 → .2，当前文件重新开始）\n".toByteArray())
            }
        }
    }
}
