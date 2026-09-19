package com.pient.app.data

import android.content.Context
import android.os.Build
import com.pient.app.runtime.EnvProvision
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** 更新日志里的一条版本记录（字段与参考实现 Mdcito 的 `ChangelogEntry` 对齐） */
data class ChangelogEntry(
    val versionName: String,
    val releaseDate: String,
    val title: String,
    val changes: List<String>,
    val isLatest: Boolean = false,
    val releaseUrl: String = "",
)

/** 备用下载地址（「镜像下载源」按键上的名字 + 完整 URL） */
data class MirrorSource(val name: String, val url: String)

/** 更新源偏好：决定从哪个平台的 Releases API 取版本信息（两个平台的内容各自独立） */
enum class UpdateSource(val id: String) {
    AUTO("auto"),
    GITEE("gitee"),
    GITHUB("github"),
    ;

    companion object {
        fun fromId(id: String?): UpdateSource = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/**
 * 应用自身的版本检查 = 读 **Gitee / GitHub 的 Releases API**（照参考实现 Mdcito 的做法）。
 *
 * 发版动作 = 打 tag（版本号）+ 上传 APK 附件 + 写 release 说明；平台就是登记处 ——
 * 版本号取自 `tag_name`、下载直链取自 APK 附件的 `browser_download_url`、大小取自 `size`、
 * 更新内容取自 release 正文，**不需要在仓库里额外维护版本清单文件**。
 *
 * 「更新源」设置决定先读哪个平台（自动 = Gitee 优先、失败退 GitHub）。
 * 两个平台的字段名一致（`tag_name` / `assets[]` / `html_url` / `published_at`），所以一份解析代码通吃。
 */
object AppUpdate {

    private const val TAG = "PientUpdate"

    private const val GITEE_API = "https://gitee.com/api/v5/repos/Jay-Victor/Pient/releases?per_page=30"
    private const val GITHUB_API = "https://api.github.com/repos/Jay-Victor/Pient/releases?per_page=30"

    /**
     * GitHub 下载加速前缀（照参考实现的清单）。只在「这个包在 GitHub 上」时才派生这些备用地址；
     * 主源一般是 Gitee（国内直连），用不上它们。
     */
    private val GITHUB_MIRRORS = listOf(
        "gh-proxy" to "https://gh-proxy.com/",
        "ghfast" to "https://ghfast.top/",
        "ghproxy" to "https://ghproxy.net/",
    )

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    /** 解析出来的一个 release（内部用，两个平台的字段并成一份） */
    private data class Release(
        val tagName: String,
        val name: String,
        val body: String,
        val htmlUrl: String,
        val publishedAt: String,
        val apkUrl: String,
        val apkSize: Long,
        val source: UpdateSource,
    ) {
        val version: String get() = tagName.trimStart('v').trim()
    }

    /** 按「更新源」设置排出先读哪个平台 */
    private fun sourceOrder(): List<UpdateSource> = when (SettingsStore.updateSource) {
        UpdateSource.AUTO -> listOf(UpdateSource.GITEE, UpdateSource.GITHUB)
        UpdateSource.GITEE -> listOf(UpdateSource.GITEE)
        UpdateSource.GITHUB -> listOf(UpdateSource.GITHUB)
    }

    /**
     * 拉取 release 列表（当前平台取到就返回；两个平台都没取到返回 null —— 调用方如实报「检查更新失败」）。
     * 空列表 = 平台答上来了但还没发过 release（这是正常状态，不是失败）。
     */
    suspend fun fetchReleases(): List<ChangelogEntry>? = withContext(Dispatchers.IO) {
        fetchRawReleases()?.let { releases -> changelogOf(releases) }
    }

    /** 官方最新版本的信息（给「检查更新」用）；没有 release 时返回 null 并把 hasReleases 交给调用方判断 */
    suspend fun fetchLatest(): LatestRelease? = withContext(Dispatchers.IO) {
        val releases = fetchRawReleases() ?: return@withContext null
        val latest = releases.firstOrNull() ?: return@withContext LatestRelease.None
        LatestRelease.Found(
            versionName = latest.version,
            notes = parseReleaseNotes(latest.body),
            releaseDate = latest.publishedAt.substringBefore('T'),
            releaseUrl = latest.htmlUrl,
            apkUrl = latest.apkUrl,
            apkSize = latest.apkSize,
            mirrors = mirrorsFor(latest, releases),
        )
    }

    /** 最新 release 的查询结果：没发过 release（[None]）与查不到（null）是两回事 */
    sealed interface LatestRelease {
        data object None : LatestRelease

        data class Found(
            val versionName: String,
            val notes: List<String>,
            val releaseDate: String,
            val releaseUrl: String,
            val apkUrl: String,
            val apkSize: Long,
            val mirrors: List<MirrorSource>,
        ) : LatestRelease
    }

    /** 从远端拉并解析 release 列表（null = 两个平台都没答上来；空列表 = 答上来了但还没发过 release） */
    private suspend fun fetchRawReleases(): List<Release>? {
        val order = sourceOrder()
        var contacted = false
        for (source in order) {
            val api = if (source == UpdateSource.GITHUB) GITHUB_API else GITEE_API
            val body = runCatching {
                client.newCall(Request.Builder().url(api).build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.string()
                    } else {
                        PientLog.w(TAG, "release 列表 HTTP ${resp.code}：$api")
                        null
                    }
                }
            }.onFailure { PientLog.w(TAG, "release 列表拉取失败：$api（${it.message}）") }.getOrNull()
            val list = body?.let { parseReleases(it, source) } ?: continue
            contacted = true
            // 这个平台还没发过 release：继续看下一个平台，别就此收工
            if (list.isEmpty()) {
                PientLog.w(TAG, "release 列表：${source.id} 还没有 release")
                continue
            }
            PientLog.i(TAG, "release 列表：${source.id} 共 ${list.size} 条")
            return list.filter { it.version.isNotEmpty() && it.apkUrl.isNotEmpty() }
                .sortedWith { a, b ->
                    when {
                        EnvProvision.isNewer(a.version, b.version) -> -1
                        EnvProvision.isNewer(b.version, a.version) -> 1
                        else -> b.publishedAt.compareTo(a.publishedAt)
                    }
                }
        }
        if (contacted) {
            PientLog.w(TAG, "release 列表：${order.size} 个平台都还没有 release")
        } else {
            PientLog.w(TAG, "release 列表不可用：${order.size} 个平台都没取到")
        }
        return if (contacted) emptyList() else null
    }

    /**
     * 备用下载地址：另一个平台上的同名附件 + GitHub 的加速前缀地址（去重、剔除直链本身）。
     * 主源是 Gitee 时，第一项就是 GitHub 的原始地址 —— 国内多半连不上，但聊胜于无，排在前缀镜像之前。
     */
    private fun mirrorsFor(latest: Release, all: List<Release>): List<MirrorSource> {
        val queue = ArrayList<MirrorSource>()
        fun add(name: String, url: String) {
            if (url.isBlank() || url == latest.apkUrl) return
            if (queue.any { it.url == url }) return
            queue += MirrorSource(name, url)
        }
        val other = all.firstOrNull { it.version == latest.version && it.source != latest.source }
        if (other != null) add(other.source.id, other.apkUrl)
        if (latest.apkUrl.contains("github.com")) {
            GITHUB_MIRRORS.forEach { (name, prefix) -> add(name, prefix + latest.apkUrl) }
        }
        return queue
    }

    /** release 列表 → 更新日志条目（列表已按版本从新到旧排；排头标「最新」） */
    private fun changelogOf(releases: List<Release>): List<ChangelogEntry> =
        releases.mapIndexed { index, release ->
            ChangelogEntry(
                versionName = release.version,
                releaseDate = release.publishedAt.substringBefore('T'),
                title = release.name.trim().ifBlank { "Pient v${release.version}" },
                changes = parseReleaseNotes(release.body),
                isLatest = index == 0,
                releaseUrl = release.htmlUrl,
            )
        }

    /** 解析 releases JSON：两个平台的字段名一致，一份解析通吃；不合法一律当作空 */
    private fun parseReleases(json: String, source: UpdateSource): List<Release>? = runCatching {
        val arr = JSONArray(json)
        val list = ArrayList<Release>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optBoolean("draft") || obj.optBoolean("prerelease")) continue
            val asset = obj.optJSONArray("assets")?.let { assets ->
                (0 until assets.length())
                    .mapNotNull { assets.optJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            } ?: continue
            list += Release(
                tagName = obj.optString("tag_name").trim(),
                name = obj.optString("name").trim(),
                body = obj.optString("body"),
                htmlUrl = obj.optString("html_url").trim(),
                publishedAt = obj.optString("published_at").trim().ifBlank { obj.optString("created_at").trim() },
                apkUrl = asset.optString("browser_download_url").trim(),
                apkSize = asset.optLong("size", 0L),
                source = source,
            )
        }
        list
    }.onFailure { PientLog.w(TAG, "release 列表解析失败：${it.message}") }.getOrNull()

    /**
     * release 正文 → 变更条目：按行去空、滤掉 Markdown 标题行、剥掉列表符号（照参考实现同一套）。
     * 发布者写正文时一行一条要点即可。
     */
    private fun parseReleaseNotes(body: String): List<String> {
        if (body.isBlank()) return emptyList()
        return body.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("#") }
            .map { line ->
                line.removePrefix("- ").removePrefix("* ").removePrefix("+ ").removePrefix("• ").trim()
            }
            .filter { it.isNotEmpty() }
    }

    /** 本机安装版本（versionName → versionCode）；读不到返回 null */
    @Suppress("DEPRECATION")
    fun localVersion(context: Context): Pair<String, Int>? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val name = info.versionName?.trim().orEmpty()
        if (name.isEmpty()) return@runCatching null
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            info.versionCode
        }
        name to code
    }.getOrNull()

    /**
     * 远端 tag 版本是否比本机版本新（`v0.2.0` > `0.1.0`）。
     * **本机版本号取自安装包，远端版本号取自 release 的 tag** —— 发版时两者要对齐（tag 写 `v0.2.0`，
     * `app/build.gradle.kts` 的 `versionName` 也要是 `0.2.0`，否则界面上的「当前版本」会对不上）。
     */
    fun isNewer(remoteVersion: String, localVersion: String): Boolean =
        EnvProvision.isNewer(remoteVersion, localVersion)
}
