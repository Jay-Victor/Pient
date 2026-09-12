package com.pient.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.File

/**
 * SAF 项目 → agent 工作区：**物化副本**方案（2026-09-12 用户拍板「开桥」）。
 *
 * 为什么需要它：pi 的官方七工具与 Ubuntu 终端只认**文件系统路径**，而 SAF 只给「tree URI 令牌」
 * （用户在选择器里点名的目录，走 ContentResolver 读写；按 `/storage/emulated/0/...` 普通路径开
 * 一律 Permission denied——分区存储，实测过）。于是 SAF 项目对 agent 是隐形的。
 *
 * 做法：
 *   1. 进入 SAF 项目时把整个目录**物化**到 `files/saf_work/<项目名>/`（应用私有目录 →
 *      agent 走真路径、Ubuntu 也能把它当工作区绑进去）；
 *   2. AI 在副本上干活；
 *   3. 用户点「保存回原目录」时把副本**回写**进 SAF 树（同名覆盖，没有的按原名新建）。
 *
 * 刻意不做自动回写：SAF 原目录可能同时被别的 App 改，静默覆盖会丢东西 —— 回写必须是显式动作。
 */
object SafWorkspace {
    private const val TAG = "SafWorkspace"

    /** 副本目录（`files/saf_work/<项目名>`，在应用私有目录内） */
    fun stageDir(context: Context, projectName: String): File =
        File(File(context.filesDir, "saf_work"), projectName)

    fun lastSyncFile(context: Context, projectName: String): File =
        File(stageDir(context, projectName), ".pient_sync.json")

    // ─────────────────────────── SAF → 副本 ───────────────────────────

    /** 物化：递归把 SAF 目录拷进副本（覆盖同名）。返回 (文件数, 字节数)。IO 线程调用。 */
    fun stage(context: Context, project: Project): Pair<Int, Long> {
        val uri = project.uri?.let(Uri::parse) ?: return 0 to 0L
        val root = stageDir(context, project.name)
        if (!root.exists() && !root.mkdirs()) return 0 to 0L
        var files = 0
        var bytes = 0L
        ProjectFiles.safWalk(context, uri) { entry, relPath ->
            val target = File(root, relPath)
            if (entry.isDir) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                val childUri = DocumentsContract.buildDocumentUriUsingTree(uri, entry.docId)
                runCatching {
                    context.contentResolver.openInputStream(childUri)?.use { input ->
                        target.outputStream().use { input.copyTo(it) }
                    } ?: return@runCatching
                    files++
                    bytes += target.length()
                }.onFailure { Log.w(TAG, "物化失败 ${entry.name}：${it.message}") }
            }
        }
        runCatching { lastSyncFile(context, project.name).writeText(System.currentTimeMillis().toString()) }
        Log.i(TAG, "物化完成 ${project.name}：$files 文件 / $bytes 字节 → ${root.absolutePath}")
        return files to bytes
    }

    // ─────────────────────────── 副本 → SAF ───────────────────────────

    /** 回写：把副本里的文件递归写回 SAF 树（同名覆盖，没有的按原名新建）。返回写入文件数。IO 线程调用。 */
    fun saveBack(context: Context, project: Project): Int {
        val uri = project.uri?.let(Uri::parse) ?: return 0
        val root = stageDir(context, project.name)
        if (!root.isDirectory) return 0
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return 0
        var written = 0

        fun walk(dir: File, parentDocId: String) {
            val children = ProjectFiles.listSafChildren(context, uri, parentDocId).associateBy { it.name }
            dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                if (f.name.startsWith(".pient_sync")) return@forEach
                val existing = children[f.name]
                if (f.isDirectory) {
                    val childDocId = existing?.docId
                        ?: runCatching {
                            DocumentsContract.createDocument(
                                context.contentResolver,
                                DocumentsContract.buildDocumentUriUsingTree(uri, parentDocId),
                                DocumentsContract.Document.MIME_TYPE_DIR,
                                f.name,
                            )
                        }.getOrNull()?.let { DocumentsContract.getDocumentId(it) }
                    if (childDocId != null) walk(f, childDocId)
                } else {
                    val docUri = existing?.docId?.let { DocumentsContract.buildDocumentUriUsingTree(uri, it) }
                        ?: runCatching {
                            DocumentsContract.createDocument(
                                context.contentResolver,
                                DocumentsContract.buildDocumentUriUsingTree(uri, parentDocId),
                                "application/octet-stream",
                                f.name,
                            )
                        }.getOrNull()
                    if (docUri == null) {
                        Log.w(TAG, "回写失败（建文档）${f.name}")
                        return@forEach
                    }
                    runCatching {
                        // "wt" = 截断写入（否则会覆盖前 N 字节、留下旧尾部）
                        context.contentResolver.openOutputStream(docUri, "wt")?.use { out ->
                            f.inputStream().use { it.copyTo(out) }
                        }
                        written++
                    }.onFailure { Log.w(TAG, "回写失败 ${f.name}：${it.message}") }
                }
            }
        }
        walk(root, rootDocId)
        Log.i(TAG, "回写完成 ${project.name}：$written 文件")
        return written
    }
}
