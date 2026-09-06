package com.pient.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 项目真实文件系统读写（2026-09-02 文件树真实功能）：
 * - 本地项目（uri=null）：path 指向的真实目录（File API）
 * - SAF 项目（uri!=null）：tree URI 经 DocumentFile 读写（依赖持久化授权）
 * - 目录不存在返回 null，UI 层回退演示数据（内置 mock 项目）
 */
object ProjectFiles {

    private const val MAX_DEPTH = 12        // 递归深度上限（防超深目录拖死 UI）
    private const val MAX_CHILDREN = 1000   // 单目录子项上限

    /** 读取项目根树；目录不可读返回 null */
    fun loadTree(context: Context, project: Project): FileNode? {
        return if (project.uri != null) {
            val doc = DocumentFile.fromTreeUri(context, Uri.parse(project.uri)) ?: return null
            if (!doc.isDirectory) return null
            loadSaf(doc, 0)
        } else {
            val dir = File(project.path)
            if (!dir.isDirectory) null else loadLocal(dir, 0)
        }
    }

    private fun loadLocal(dir: File, depth: Int): FileNode {
        val children = dir.listFiles()?.take(MAX_CHILDREN)?.mapNotNull { f ->
            if (f.isDirectory) {
                if (depth >= MAX_DEPTH) FileNode(f.name, isDir = true, source = f.absolutePath)
                else loadLocal(f, depth + 1)
            } else {
                FileNode(
                    name = f.name,
                    isDir = false,
                    size = f.length(),
                    modifiedAt = f.lastModified(),
                    source = f.absolutePath,
                )
            }
        } ?: emptyList()
        return FileNode(dir.name, isDir = true, children = children, source = dir.absolutePath)
    }

    private fun loadSaf(doc: DocumentFile, depth: Int): FileNode {
        val children = doc.listFiles().take(MAX_CHILDREN).mapNotNull { child ->
            if (child.isDirectory) {
                if (depth >= MAX_DEPTH) FileNode(child.name ?: "", isDir = true, source = child.uri.toString())
                else loadSaf(child, depth + 1)
            } else {
                FileNode(
                    name = child.name ?: "",
                    isDir = false,
                    size = child.length(),
                    modifiedAt = child.lastModified(),
                    source = child.uri.toString(),
                )
            }
        }
        return FileNode(
            doc.name ?: "",
            isDir = true,
            children = children,
            size = doc.length(),
            modifiedAt = doc.lastModified(),
            source = doc.uri.toString(),
        )
    }

    /**
     * 读取文本内容；>2MB、读取失败或非 UTF-8 文本（二进制）返回 null。
     * UI 层按 node.size 与结果区分「文件过大」/「二进制不可预览」。
     */
    fun readText(context: Context, node: FileNode): String? {
        val src = node.source ?: return null
        if (node.size > 2L * 1024 * 1024) return null
        return try {
            val bytes = if (src.startsWith("content://")) {
                val ins = context.contentResolver.openInputStream(Uri.parse(src)) ?: return null
                ins.use { it.readBytes() }
            } else {
                File(src).readBytes()
            }
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            try {
                decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            } catch (e: java.nio.charset.CharacterCodingException) {
                null // 非 UTF-8 文本 → 二进制
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 读取图片 bitmap（非图片/失败返回 null） */
    fun readBitmap(context: Context, node: FileNode): Bitmap? {
        val src = node.source ?: return null
        return try {
            if (src.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(src))?.use {
                    BitmapFactory.decodeStream(it)
                }
            } else {
                BitmapFactory.decodeFile(src)
            }
        } catch (e: Exception) {
            null
        }
    }

    // ── 写操作（新建 / 重命名 / 删除；成功返回 true） ──

    /** 在项目根创建文件/文件夹 */
    fun createEntry(context: Context, project: Project, name: String, isFile: Boolean): Boolean {
        if (name.isBlank() || name.contains('/') || name.contains('\\')) return false
        return try {
            if (project.uri != null) {
                val doc = DocumentFile.fromTreeUri(context, Uri.parse(project.uri)) ?: return false
                if (isFile) doc.createFile("application/octet-stream", name) != null
                else doc.createDirectory(name) != null
            } else {
                val parent = File(project.path)
                if (!parent.isDirectory) return false
                val target = File(parent, name)
                if (target.exists()) return false
                if (isFile) target.createNewFile() else target.mkdirs()
            }
        } catch (e: Exception) {
            false
        }
    }

    /** 重命名节点（父目录 = source 的上一级；SAF 用 DocumentFile.renameTo） */
    fun renameEntry(context: Context, node: FileNode, newName: String): Boolean {
        if (newName.isBlank() || newName.contains('/') || newName.contains('\\')) return false
        val src = node.source ?: return false
        return try {
            if (src.startsWith("content://")) {
                DocumentFile.fromSingleUri(context, Uri.parse(src))?.renameTo(newName) ?: false
            } else {
                val f = File(src)
                if (!f.exists()) return false
                f.renameTo(File(f.parentFile, newName))
            }
        } catch (e: Exception) {
            false
        }
    }

    /** 删除节点（目录递归删除） */
    fun deleteEntry(context: Context, node: FileNode): Boolean {
        val src = node.source ?: return false
        return try {
            if (src.startsWith("content://")) {
                DocumentFile.fromSingleUri(context, Uri.parse(src))?.delete() ?: false
            } else {
                val f = File(src)
                if (!f.exists()) return false
                if (f.isDirectory) f.deleteRecursively() else f.delete()
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 删除项目整个根文件夹及其中所有文件（2026-09-03「删除项目」真实化）。
     * 本地项目：File.deleteRecursively；目录已不存在视为成功（无物可删）。
     * SAF 项目：DocumentFile.delete——TreeDocumentFile 底层 DocumentsContract.deleteDocument
     * 对树文档递归删除整棵子树。
     */
    fun deleteProjectRoot(context: Context, project: Project): Boolean {
        return try {
            if (project.uri != null) {
                DocumentFile.fromTreeUri(context, Uri.parse(project.uri))?.delete() ?: false
            } else {
                val f = File(project.path)
                !f.exists() || f.deleteRecursively()
            }
        } catch (e: Exception) {
            false
        }
    }

    /** 节点统计（详细信息，2026-09-02）：返回 大小 to 最近修改时间；目录递归汇总 */
    fun nodeStat(context: Context, node: FileNode): Pair<Long, Long> {
        val src = node.source ?: return 0L to 0L
        if (!node.isDir) return node.size to node.modifiedAt
        var bytes = 0L
        var modified = 0L
        return try {
            if (src.startsWith("content://")) {
                val doc = DocumentFile.fromSingleUri(context, Uri.parse(src)) ?: return bytes to modified
                fun walk(d: DocumentFile) {
                    for (c in d.listFiles()) {
                        if (c.isDirectory) walk(c) else {
                            bytes += c.length()
                            modified = maxOf(modified, c.lastModified())
                        }
                    }
                }
                walk(doc)
                modified = maxOf(modified, doc.lastModified())
            } else {
                val dir = File(src)
                if (dir.isDirectory) {
                    dir.walkTopDown().forEach { f ->
                        if (f.isFile) {
                            bytes += f.length()
                            modified = maxOf(modified, f.lastModified())
                        }
                    }
                    modified = maxOf(modified, dir.lastModified())
                }
            }
            bytes to modified
        } catch (e: Exception) {
            bytes to modified
        }
    }

    // ── 导入 / 导出（2026-09-02：SAF 多选文件 / 目录树，递归流拷贝） ──

    /** 导入多个文件到项目根（返回成功数）；文件类型不限 */
    fun importFiles(context: Context, project: Project, uris: List<Uri>): Int {
        val dstRoot = projectRoot(context, project) ?: return 0
        var ok = 0
        for (uri in uris) {
            val src = DocumentFile.fromSingleUri(context, uri) ?: continue
            if (copyRecursive(context, src, dstRoot)) ok++
        }
        return ok
    }

    /** 导入整个文件夹（含子目录）到项目根 */
    fun importFolder(context: Context, project: Project, treeUri: Uri): Boolean {
        val src = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        val dstRoot = projectRoot(context, project) ?: return false
        return copyRecursive(context, src, dstRoot)
    }

    /** 导出整个项目到用户选择的目录（2026-09-02 改为打包 zip：项目文件夹整体压缩导出） */
    fun exportProject(context: Context, project: Project, targetTreeUri: Uri): Boolean {
        val src = projectRoot(context, project) ?: return false
        val dst = DocumentFile.fromTreeUri(context, targetTreeUri) ?: return false
        if (!dst.isDirectory) return false
        val name = src.name ?: "project"
        val zipFile = File(context.cacheDir, "$name.zip")
        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                if (!addDocToZip(context, src, zos, "")) return false
            }
            copyZipInto(context, zipFile, dst)
        } catch (e: Exception) {
            zipFile.delete()
            false
        } finally {
            zipFile.delete()
        }
    }

    /**
     * 批量导出（2026-09-02）：选中 1 个文件 → 直接拷贝；选中多个文件/文件夹 → 打包 zip。
     * 目标 = SAF 目录（tree URI）。
     */
    fun exportSelected(
        context: Context,
        project: Project,
        nodes: List<FileNode>,
        targetTreeUri: Uri,
    ): Boolean {
        if (nodes.isEmpty()) return false
        val dst = DocumentFile.fromTreeUri(context, targetTreeUri) ?: return false
        if (!dst.isDirectory) return false
        // 单选文件：直接流拷贝
        if (nodes.size == 1 && !nodes[0].isDir) {
            val src = docOf(context, nodes[0]) ?: return false
            return copyRecursive(context, src, dst)
        }
        val zipName = (project.name.ifBlank { "pient" }) + "-export-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".zip"
        val zipFile = File(context.cacheDir, zipName)
        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                nodes.forEach { node ->
                    val ok = node.source?.let { src ->
                        if (src.startsWith("content://")) {
                            addDocToZip(context, DocumentFile.fromSingleUri(context, Uri.parse(src)) ?: return@let false, zos, "")
                        } else {
                            addDocToZip(context, DocumentFile.fromFile(File(src)), zos, "")
                        }
                    } ?: false
                    if (!ok) return false
                }
            }
            copyZipInto(context, zipFile, dst)
        } catch (e: Exception) {
            zipFile.delete()
            false
        } finally {
            zipFile.delete()
        }
    }

    /** FileNode → DocumentFile 视图（本地/SAF） */
    private fun docOf(context: Context, node: FileNode): DocumentFile? {
        val src = node.source ?: return null
        return if (src.startsWith("content://")) DocumentFile.fromSingleUri(context, Uri.parse(src))
        else DocumentFile.fromFile(File(src))
    }

    /** 目录/文件递归写入 zip（目录名带尾斜杠；文件流拷贝） */
    private fun addDocToZip(
        context: Context,
        doc: DocumentFile,
        zos: ZipOutputStream,
        parentPath: String,
    ): Boolean {
        val entryPath = if (parentPath.isEmpty()) (doc.name ?: return false)
        else "$parentPath/${doc.name ?: return false}"
        return if (doc.isDirectory) {
            zos.putNextEntry(ZipEntry("$entryPath/"))
            zos.closeEntry()
            doc.listFiles().all { addDocToZip(context, it, zos, entryPath) }
        } else {
            val ins = context.contentResolver.openInputStream(doc.uri) ?: return false
            try {
                zos.putNextEntry(ZipEntry(entryPath))
                ins.copyTo(zos)
                zos.closeEntry()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /** cache 中的 zip 拷贝进 SAF 目标目录（createDocument 不追加扩展名，名称原样） */
    private fun copyZipInto(context: Context, zipFile: File, dst: DocumentFile): Boolean {
        val out = dst.findFile(zipFile.name)
            ?: dst.createFile("application/zip", zipFile.name)
            ?: return false
        val outs = context.contentResolver.openOutputStream(out.uri, "wt") ?: return false
        return try {
            zipFile.inputStream().use { i -> outs.use { o -> i.copyTo(o) } }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 项目根统一视图（本地 = fromFile；SAF = fromTreeUri） */
    private fun projectRoot(context: Context, project: Project): DocumentFile? {
        return if (project.uri != null) {
            DocumentFile.fromTreeUri(context, Uri.parse(project.uri))
        } else {
            DocumentFile.fromFile(File(project.path))
        }
    }

    /** 递归流拷贝（重名覆盖）；目录同名复用 */
    private fun copyRecursive(context: Context, src: DocumentFile, dstDir: DocumentFile): Boolean {
        if (!src.canRead()) return false
        return if (src.isDirectory) {
            val newDir = dstDir.findFile(src.name ?: "") ?: dstDir.createDirectory(src.name ?: "")
                ?: return false
            if (!newDir.isDirectory) return false
            src.listFiles().all { copyRecursive(context, it, newDir) }
        } else {
            val name = src.name ?: return false
            val ins = context.contentResolver.openInputStream(src.uri) ?: return false
            try {
                if (dstDir.uri.scheme == "file") {
                    // 本地目标：File 直写——RawDocumentFile.createFile 会按 MIME 推断扩展名
                    // 并追加到文件名（text/plain→.txt、octet-stream→.bin，2026-09-02 实测），
                    // 必须绕开它才能原样保留文件名。
                    val target = File(dstDir.uri.path ?: return false, name)
                    ins.use { i -> target.outputStream().use { o -> i.copyTo(o) } }
                } else {
                    // SAF 目标：createDocument 不追加扩展名，MIME 仅作元数据
                    val out = dstDir.findFile(name)
                        ?: dstDir.createFile("application/octet-stream", name)
                        ?: return false
                    val outs = context.contentResolver.openOutputStream(out.uri, "wt") ?: return false
                    ins.use { i -> outs.use { o -> i.copyTo(o) } }
                }
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
