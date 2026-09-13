package com.pient.app.runtime

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import android.util.Log
import com.pient.app.data.SettingsStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * SAF 文件操作桥（路线 A 第一片，2026-09-14）。
 *
 * **为什么需要它**：SAF 只给 tree URI 令牌、不给文件系统路径，而 pi 的七个工具在 Node 宿主里按真路径工作。
 * Operit 的解法是在 Java 侧直接实现一整套 SAF 文件工具（`SafFileSystemTools.kt`，1677 行）；Pient 的对应物
 * 就是这里 —— 宿主侧的 `node:fs` 门面把 `/saf/…` 路径转成 HTTP 调用打到本桥，由本桥用 ContentResolver 落到
 * **用户原目录**（不再物化副本、不再手动回写）。
 *
 * **路径方案**：`/saf/<书签名>/<相对路径…>`，书签名取自 `SettingsStore.safBookmarks`（与项目选择器同一份）。
 * 解析只允许落在已授权书签的树内（`/saf` 之外一律拒绝），不做任何路径猜测。
 *
 * **支持的操作**（对齐 `node:fs` 里 pi 实际用到的那部分）：
 * `stat` / `readdir` / `read`（base64）/ `write`（base64，可 create+truncate）/ `mkdir` / `delete`（目录递归）/
 * `rename`。全部走 DocumentsContract，单次游标查询（`ProjectFiles` 同款做法：不用 DocumentFile，避免逐项 IPC）。
 */
object SafFileBridge {

    private const val TAG = "PiHost"
    private const val SAF_PREFIX = "/saf"
    private const val MAX_READ_BYTES = 8L * 1024 * 1024   // 单次读取上限（base64 后更大，够工具用）

    private val PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    private val MIME_DIR = DocumentsContract.Document.MIME_TYPE_DIR

    fun handle(context: Context, req: JSONObject): JSONObject {
        val op = req.optString("op")
        val path = req.optString("path")
        if (path.isEmpty()) return err("bad-request", "缺少 path")
        return runCatching {
            when (op) {
                "stat", "readdir", "read" -> {
                    val target = resolve(context, path)
                        ?: return err("not-found", "路径不在已授权书签内或不存在：$path")
                    when (op) {
                        "stat" -> stat(context, target)
                        "readdir" -> readdir(context, target)
                        else -> read(context, target)
                    }
                }
                // 写类操作允许叶子还不存在（新建文件/目录是常态）：解析「父目录 + 名字」，叶子存在就用它
                "write", "mkdir" -> {
                    val (parent, leaf) = resolveParent(context, path)
                        ?: return err("no-parent", "父目录不在已授权书签内或不存在：$path")
                    val existing = listChildren(context, parent.treeUri, parent.docId)
                        .firstOrNull { it.name == leaf }
                    if (op == "mkdir") mkdir(context, parent, leaf, existing)
                    else write(context, parent, leaf, existing, req)
                }
                "delete", "rename" -> {
                    val target = resolve(context, path)
                        ?: return err("not-found", "路径不在已授权书签内或不存在：$path")
                    if (op == "delete") delete(context, target) else rename(context, target, req)
                }
                else -> err("bad-op", "不支持的操作：$op")
            }
        }.getOrElse {
            Log.w(TAG, "SAF 桥 $op 失败：${it.message}")
            err("fs-failed", it.message ?: it.javaClass.simpleName)
        }
    }

    // ─────────────────────────── 路径解析 ───────────────────────────

    private class Target(
        val key: String,          // 书签名
        val treeUri: Uri,
        val docId: String,
        val isRoot: Boolean,
    )

    /**
     * `/saf/<书签>/a/b.txt` → tree URI + 目标 documentId。
     * 根（`/saf/<书签>`）直接返回树根；中间任一环不存在 → null。
     */
    private fun resolve(context: Context, path: String): Target? {
        if (!path.startsWith(SAF_PREFIX)) return null
        val rest = path.removePrefix(SAF_PREFIX).trim('/')
        if (rest.isEmpty()) return null
        val segs = rest.split('/').filter { it.isNotEmpty() }
        val key = segs.first()
        val bookmark = SettingsStore.safBookmarks.firstOrNull { it.name == key } ?: return null
        val treeUri = runCatching { Uri.parse(bookmark.uri) }.getOrNull() ?: return null
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        if (segs.size == 1) return Target(key, treeUri, rootId, isRoot = true)

        var curId = rootId
        for (seg in segs.drop(1)) {
            val child = listChildren(context, treeUri, curId).firstOrNull { it.name == seg } ?: return null
            curId = child.docId
        }
        return Target(key, treeUri, curId, isRoot = false)
    }

    /** `/saf/<书签>/a/b.txt` → (父目录 Target, "b.txt")；父目录必须已存在（写类操作允许叶子不存在） */
    private fun resolveParent(context: Context, path: String): Pair<Target, String>? {
        val idx = path.lastIndexOf('/')
        if (idx <= 0 || idx == path.length - 1) return null
        val parent = resolve(context, path.substring(0, idx)) ?: return null
        return parent to path.substring(idx + 1)
    }

    private class Entry(
        val docId: String,
        val name: String,
        val isDir: Boolean,
        val size: Long,
        val modified: Long,
    )

    private fun listChildren(context: Context, treeUri: Uri, parentDocId: String): List<Entry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val out = ArrayList<Entry>()
        context.contentResolver.query(childrenUri, PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = if (c.isNull(1)) "" else c.getString(1)
                val mime = if (c.isNull(2)) null else c.getString(2)
                out += Entry(
                    docId = id,
                    name = name,
                    isDir = mime == MIME_DIR,
                    size = if (c.isNull(3)) 0L else c.getLong(3),
                    modified = if (c.isNull(4)) 0L else c.getLong(4),
                )
            }
        }
        return out
    }

    private fun docUri(t: Target) = DocumentsContract.buildDocumentUriUsingTree(t.treeUri, t.docId)

    private fun entryJson(e: Entry) = JSONObject()
        .put("name", e.name)
        .put("dir", e.isDir)
        .put("size", e.size)
        .put("mtime", e.modified)

    // ─────────────────────────── 各操作 ───────────────────────────

    private fun stat(context: Context, t: Target): JSONObject {
        val meta = queryDoc(context, t) ?: return err("not-found", "文档不存在")
        return JSONObject().put("ok", true).put("stat", meta)
    }

    private fun queryDoc(context: Context, t: Target): JSONObject? {
        val uri = docUri(t)
        return context.contentResolver.query(uri, PROJECTION, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            JSONObject()
                .put("name", if (c.isNull(1)) "" else c.getString(1))
                .put("dir", (if (c.isNull(2)) null else c.getString(2)) == MIME_DIR)
                .put("size", if (c.isNull(3)) 0L else c.getLong(3))
                .put("mtime", if (c.isNull(4)) 0L else c.getLong(4))
        }
    }

    private fun readdir(context: Context, t: Target): JSONObject {
        if (t.isRoot) {
            // 书签根：列根下的条目
            val arr = JSONArray()
            listChildren(context, t.treeUri, t.docId).forEach { arr.put(entryJson(it)) }
            return JSONObject().put("ok", true).put("entries", arr)
        }
        val self = queryDoc(context, t) ?: return err("not-found", "目录不存在")
        if (!self.getBoolean("dir")) return err("not-dir", "不是目录：${self.optString("name")}")
        val arr = JSONArray()
        listChildren(context, t.treeUri, t.docId).forEach { arr.put(entryJson(it)) }
        return JSONObject().put("ok", true).put("entries", arr)
    }

    private fun read(context: Context, t: Target): JSONObject {
        val size = queryDoc(context, t)?.optLong("size") ?: 0L
        if (size > MAX_READ_BYTES) return err("too-large", "文件太大（$size 字节），超过桥的读取上限")
        val bytes = context.contentResolver.openInputStream(docUri(t))?.use { it.readBytes() }
            ?: return err("read-failed", "打不开文档流")
        return JSONObject()
            .put("ok", true)
            .put("size", bytes.size)
            .put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    /** 写入：叶子存在则 `wt` 截断覆盖，不存在则在父目录 createDocument */
    private fun write(context: Context, parent: Target, leaf: String, existing: Entry?, req: JSONObject): JSONObject {
        val bytes = runCatching { Base64.decode(req.optString("base64"), Base64.DEFAULT) }
            .getOrElse { return err("bad-base64", "base64 解码失败") }
        val uri = when {
            existing == null -> DocumentsContract.createDocument(
                context.contentResolver, docUri(parent), "application/octet-stream", leaf,
            ) ?: return err("create-failed", "无法创建文档：$leaf")
            existing.isDir -> return err("is-dir", "目标是目录：$leaf")
            else -> DocumentsContract.buildDocumentUriUsingTree(parent.treeUri, existing.docId)
        }
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: return err("write-failed", "打不开写入流")
        return JSONObject().put("ok", true).put("written", bytes.size)
    }

    private fun mkdir(context: Context, parent: Target, leaf: String, existing: Entry?): JSONObject {
        if (existing != null) return JSONObject().put("ok", true).put("existed", true)
        val created = DocumentsContract.createDocument(context.contentResolver, docUri(parent), MIME_DIR, leaf)
            ?: return err("mkdir-failed", "无法创建目录：$leaf")
        return JSONObject().put("ok", true).put("created", created.toString())
    }

    private fun delete(context: Context, t: Target): JSONObject {
        val meta = queryDoc(context, t)
        if (meta?.optBoolean("dir") == true) {
            // 递归删除子节点（ExternalStorageProvider 的 deleteDocument 对目录子树不一定递归，自己走更稳）
            listChildren(context, t.treeUri, t.docId).forEach { child ->
                val sub = Target(t.key, t.treeUri, child.docId, isRoot = false)
                delete(context, sub)
            }
        }
        val ok = DocumentsContract.deleteDocument(context.contentResolver, docUri(t))
        return if (ok) JSONObject().put("ok", true) else err("delete-failed", "提供商拒绝删除")
    }

    private fun rename(context: Context, t: Target, req: JSONObject): JSONObject {
        val newName = req.optString("newName")
        if (newName.isBlank() || newName.contains('/')) return err("bad-name", "非法的新名称")
        val uri = DocumentsContract.renameDocument(context.contentResolver, docUri(t), newName)
            ?: return err("rename-failed", "提供商拒绝改名")
        return JSONObject().put("ok", true).put("uri", uri.toString())
    }

    private fun err(code: String, message: String) =
        JSONObject().put("error", code).put("message", message)
}
