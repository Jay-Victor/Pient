package com.pient.app.tools.files

import android.content.Context
import com.pient.app.runtime.PiRuntime
import java.io.File

/**
 * **可访问根门控（唯一实现）** —— 自身工具层与「@ 引用 / 附件」走同一套判定。
 *
 * 可访问范围 = 工作区 + 应用私有目录整棵（`filesDir` 含 `attachments/`、`Projects/`、`pient-rt/`）
 * + 缓存目录 + 外部私有目录。应用私有目录本来就是 App 自己的沙箱，全放行不越界；
 * 外部存储的 SAF 项目另有物化副本（也在私有目录内，见 `references/saf-and-workspace.md`）。
 *
 * 2026-09-14 用户实测报「上传了附件但 AI 读不了」= 这里当时少放了 `filesDir`，模型只能看到
 * 工作区与 `pient-rt`，附件目录被拒——所以这份清单是**唯一出处**，别在别处再写一遍。
 */
object PathGuard {

    fun allowedRoots(context: Context): List<File> = listOfNotNull(
        PiRuntime.workspaceDir(context),
        context.filesDir,
        context.cacheDir,
        context.getExternalFilesDir(null),
    )

    private fun canonical(f: File): File = runCatching { f.canonicalFile }.getOrElse { f.absoluteFile }

    /** 路径是否落在可访问根内 */
    fun allowed(context: Context, f: File): Boolean {
        val canon = canonical(f)
        return allowedRoots(context).any { root ->
            val r = canonical(root).path
            canon.path == r || canon.path.startsWith(r + File.separator)
        }
    }

    /**
     * 路径解析：相对路径一律相对工作区；越界（不在可访问根内）直接拒绝并说明。
     * @return (解析后的文件, 错误说明)；文件为 null 时读错误说明
     */
    fun resolve(context: Context, raw: String?): Pair<File?, String?> {
        val p = raw?.trim()?.trim('"').orEmpty()
        if (p.isEmpty()) return null to "缺少 path 参数"
        val ws = PiRuntime.workspaceDir(context)
        val f = if (File(p).isAbsolute) File(p) else File(ws, p)
        val canon = canonical(f)
        return if (allowed(context, canon)) {
            canon to null
        } else {
            null to (
                "路径不可访问：$p（可访问范围 = 工作区 ${ws.absolutePath} 与应用私有目录 " +
                    "${context.filesDir.absolutePath}，含其中的 attachments/ 附件目录）"
                )
        }
    }
}
