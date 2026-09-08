package com.pient.app.data

import androidx.compose.runtime.mutableStateListOf

// ─────────────────────────────────────────────────────────────
// UI 原型 mock 数据。红线：仅演示 UI 交互，不替代 pi 官方接口；
// 接入运行时后由 get_available_models / SessionManager 等官方机制替换。
// ─────────────────────────────────────────────────────────────

object MockProjects {
    val list = listOf(
        Project(
            name = "my-android-app",
            path = "/storage/emulated/0/Projects/my-android-app",
        ),
        Project(
            name = "pi-experiments",
            path = "/storage/emulated/0/Projects/pi-experiments",
        ),
    )
}

object MockSessions {
    val byProject: Map<String, List<Session>> = mapOf(
        "my-android-app" to listOf(
            Session("s-1024", "修复登录崩溃", "my-android-app", "2m"),
            Session("s-1023", "重构权限模块", "my-android-app", "昨天", running = true),
            Session("s-1021", "接入 CI 流水线", "my-android-app", "昨天"),
            Session("s-1018", "调优启动性能", "my-android-app", "更早"),
        ),
        "pi-experiments" to listOf(
            Session("s-2031", "调优 thinking 预算", "pi-experiments", "更早"),
            Session("s-2027", "插件市场调研", "pi-experiments", "更早"),
        ),
    )
}

/** 会话消息内容（mock 会话固定内容，新建会话为空） */
object MockMessages {
    val fixLoginCrash: List<Msg> = listOf(
        Msg.User(
            "登录页在 Android 14 上必现崩溃，帮我看看日志定位一下",
            attachments = listOf(
                Attachment("crash_log.txt", AttachmentKind.FILE),
                Attachment("screenshot.png", AttachmentKind.IMAGE),
            ),
        ),
        Msg.Thinking(
            level = "medium",
            text = "用户报告 Android 14 登录页必现崩溃。计划：先用 bash 抓取 logcat 崩溃堆栈，" +
                "再 grep 相关代码定位空指针来源，最后给出修复方案。",
        ),
        Msg.ToolCall(
            name = "bash",
            params = "adb logcat -d | grep -i \"fatal exception\" | tail -20",
            status = ToolStatus.DONE,
            detail = "stdout: FATAL EXCEPTION: main ... NullPointerException at com.example.LoginViewModel.access\$getToken(LoginViewModel.kt:42)\n" +
                "\nat com.example.LoginActivity.onCreate(LoginActivity.kt:87)",
        ),
        Msg.ToolResult(
            toolName = "bash",
            preview = "FATAL EXCEPTION: main\njava.lang.NullPointerException\n" +
                "  at LoginViewModel.kt:42",
            full = "FATAL EXCEPTION: main\nProcess: com.example.app, PID: 12345\n" +
                "java.lang.NullPointerException: Attempt to invoke virtual method 'java.lang.String com.example.token.TokenStorage.get()' on a null object reference\n" +
                "\tat com.example.LoginViewModel.loadToken(LoginViewModel.kt:42)\n" +
                "\tat com.example.LoginActivity.onCreate(LoginActivity.kt:87)",
        ),
        Msg.Assistant(
            markdown = "## 根因定位\n\n" +
                "已定位根因：**`LoginViewModel.kt:42`** 在首启时 `tokenStorage` 尚未初始化，\n" +
                "`get()` 调用抛 NPE。\n\n" +
                "## 修复方案\n\n" +
                "1. 在 `init` 中以 `runCatching` 包裹 token 读取\n" +
                "2. 对 `tokenStorage` 提供 `emptyToken()` 兜底\n\n" +
                "```kotlin\n" +
                "val token = runCatching { tokenStorage.get() }\n" +
                "    .getOrNull() ?: emptyToken()\n" +
                "```\n\n" +
                "> 注：Android 14 首启时序更严格，建议一并核对 `onResume`。\n\n" +
                "*预计 5 分钟改完，需要我继续吗？*",
            usage = Usage(inTokens = 4208, outTokens = 612, cacheTokens = 1873, costUsd = 0.018),
            model = "claude-sonnet-4-5",
        ),
        Msg.User("好的，按方案 1 修，改完帮我跑一下测试"),
        Msg.Assistant(
            markdown = "## 修复完成\n\n" +
                "已应用修复并执行 `./gradlew test`：\n\n" +
                "- ✅ `LoginViewModelTest` **12/12** 通过\n" +
                "- ⚠️ `LoginActivityTest` 1 项与设备旋转相关，与本修复无关\n\n" +
                "需要我继续处理吗？",
            usage = Usage(inTokens = 5330, outTokens = 288, cacheTokens = 4208, costUsd = 0.021),
            model = "claude-sonnet-4-5",
        ),
    )

    val refactorPermissions: List<Msg> = listOf(
        Msg.User("把权限申请从 Activity 抽到统一的 PermissionManager，保持行为不变"),
        Msg.Thinking(
            level = "high",
            text = "重构任务。先通读现有权限调用点，确认全部请求路径（存储/位置/相机），" +
                "再设计 PermissionManager API，最后逐处替换并编译验证。",
        ),
        Msg.ToolCall(
            name = "grep",
            params = "requestPermissions(\" / -r app/src/main/java",
            status = ToolStatus.DONE,
        ),
        Msg.ToolResult("grep", "4 处调用点：\nMainActivity.kt:120\nCameraFragment.kt:88\nLocationService.kt:56\nStorageHelper.kt:33"),
        Msg.ToolCall(
            name = "bash",
            params = "./gradlew assembleDebug",
            status = ToolStatus.RUNNING,
        ),
    )
}

/** /tree 画布页演示树（2026-09-02 分支功能设计 §2.2）：
 *  分支 A = 现有 flat mock 内容（活跃）；分支 B = 新增演示分支。
 *  节点 = 用户消息；exchange 为纯消息流（会话内分支在画布页展示，聊天流不注入分支卡片）。 */
object MockTrees {
    val fixLoginCrash: SessionTreeNode by lazy {
        SessionTreeNode(
            id = "u1",
            userText = (MockMessages.fixLoginCrash[0] as Msg.User).text,
            exchange = MockMessages.fixLoginCrash.subList(0, 5),
            active = true,
            children = listOf(
                SessionTreeNode(
                    id = "u2a",
                    userText = (MockMessages.fixLoginCrash[5] as Msg.User).text,
                    exchange = listOf(
                        MockMessages.fixLoginCrash[5],
                        MockMessages.fixLoginCrash[6],
                    ),
                    branchLabel = "分支 A（修复 NPE 后）",
                    active = true,
                ),
                SessionTreeNode(
                    id = "u2b",
                    userText = "先帮我写个回归用例，覆盖 tokenStorage 初始化的场景",
                    exchange = listOf(
                        Msg.User("先帮我写个回归用例，覆盖 tokenStorage 初始化的场景"),
                        Msg.Assistant(
                            markdown = "已创建 `LoginViewModelRegressionTest`，覆盖首启 token 未初始化的回归场景：\n\n" +
                                "- ✅ `token_returns_null_on_first_boot` 通过\n" +
                                "- ✅ `empty_token_fallback_does_not_crash` 通过\n\n" +
                                "共 8 个用例，可在 `./gradlew test` 中执行。",
                            usage = Usage(
                                inTokens = 5210,
                                outTokens = 344,
                                cacheTokens = 4208,
                                costUsd = 0.024,
                            ),
                            model = "claude-sonnet-4-5",
                        ),
                    ),
                    branchLabel = "分支 B（先写回归用例）",
                    active = false,
                ),
            ),
        )
    }
}

object MockModels {
    val providers = listOf(
        AiModel("anthropic/claude-sonnet-4-5", "claude-sonnet-4-5", "anthropic"),
        AiModel("anthropic/claude-haiku-4-5", "claude-haiku-4-5", "anthropic"),
        AiModel("anthropic/claude-opus-4-1", "claude-opus-4-1", "anthropic"),
        AiModel("anthropic/claude-3-5-sonnet", "claude-3-5-sonnet", "anthropic"),
        AiModel("openai/gpt-5", "gpt-5", "openai"),
        AiModel("openai/gpt-4o", "gpt-4o", "openai"),
        AiModel("openai/o3", "o3", "openai"),
        AiModel("local/mnn-llama-8b", "MNN Llama-3-8B", "local"),
        AiModel("local/ollama-qwen2.5-7b", "Ollama qwen2.5:7b", "local"),
        AiModel("local/ollama-deepseek-r1-14b", "Ollama deepseek-r1:14b", "local"),
    )

    val providerNames = mapOf(
        "anthropic" to "Anthropic",
        "openai" to "OpenAI",
        "local" to "本地 (MNN / Ollama)",
    )
}

/** mock 流式回复（发送后按字符吐出） */
object MockReplies {
    val default = listOf(
        "收到。我先梳理一下任务要点：\n\n" +
            "## 目标\n\n" +
            "- **任务**：$1\n" +
            "- 已加载当前项目上下文（`my-android-app`）\n\n" +
            "## 执行计划\n\n" +
            "1. 定位相关代码路径\n" +
            "2. 分析现有实现\n" +
            "3. 给出可执行方案\n\n" +
            "```bash\n" +
            "pi run \"$1\"\n" +
            "```\n\n" +
            "> 原型演示：此处为 mock 流式回复，接入 Pi 运行时后由真实 Agent 输出替换。\n\n" +
            "*需要我直接动手改代码吗？*",
        "好的，这是一个典型的工程任务。我的思路：\n\n" +
            "### 第一步：先定位\n\n" +
            "用 `grep` 找到相关调用点，再通读上下文。\n\n" +
            "### 第二步：再动手\n\n" +
            "- 保持行为不变\n" +
            "- 补充回归用例\n\n" +
            "> 提示：修改前建议先 `git stash` 保存现场。\n\n" +
            "**预计 10 分钟完成**，需要我继续吗？",
    )
    private var idx = 0
    fun next(text: String): String {
        val t = default[idx % default.size]
        idx++
        return t.replace("$1", text)
    }
}

// ─────────────────────────────────────────────────────────────
// 技能 / 插件（mock；分段切换 = 全局 ~/.pi/agent/skills、项目 .pi/skills）
// ─────────────────────────────────────────────────────────────
object MockStore {
    // ── SKILL.md mock 内容（须声明在 globalSkills 之前，保证对象初始化顺序） ──
    private val WEB_RESEARCH_MD = """
        |---
        |name: web-research
        |description: 联网检索与资料整理
        |---
        |
        |# 联网检索与资料整理
        |
        |## 触发场景
        |- 需要检索最新资料、验证事实时
        |- 整理多来源信息为结构化笔记
        |
        |## 工作流程
        |1. 明确检索目标与关键词
        |2. 检索并筛选高信度来源
        |3. 交叉验证关键事实
        |4. 输出带引用的结构化摘要
    """.trimMargin()

    private val CODE_REVIEW_MD = """
        |---
        |name: code-review
        |description: 代码审查助手
        |---
        |
        |# 代码审查助手
        |
        |## 审查要点
        |- 安全：注入、越权、敏感信息泄漏
        |- 正确性：边界条件、空值处理
        |- 可维护性：命名、重复代码、死代码
        |
        |## 输出格式
        |按严重程度分级列出问题，附文件与行号。
    """.trimMargin()

    private val ANDROID_DEBUG_MD = """
        |---
        |name: android-debug
        |description: Android 崩溃定位与日志分析
        |---
        |
        |# Android 崩溃定位与日志分析
        |
        |## 触发场景
        |- Crash 堆栈分析
        |- ANR / 卡顿定位
        |- 系统日志过滤
        |
        |## 工具
        |- logcat 过滤与关联分析
        |- bugreport 解析
    """.trimMargin()

    private val PDF_UTILS_MD = """
        |---
        |name: pdf-utils
        |description: PDF 文档解析与导出
        |---
        |
        |# PDF 文档解析与导出
        |
        |## 触发场景
        |- 提取 PDF 文本与表格
        |- 生成带目录的导出文档
        |
        |## 工具
        |- pymupdf 解析
        |- 表格结构化输出
    """.trimMargin()

    private val KMP_MIGRATION_MD = """
        |---
        |name: kmp-migration
        |description: Kotlin Multiplatform 迁移检查
        |---
        |
        |# KMP 迁移检查
        |
        |检查 Java/Kotlin 项目迁移到 Kotlin Multiplatform 的兼容性：
        |- 平台相关 API 清单
        |- 共享模块边界建议
        |- expect/actual 拆分方案
    """.trimMargin()

    // ── 技能目录 ASCII 树（详情弹窗「目录结构」窗口） ──
    private val WEB_RESEARCH_TREE = """
        |web-research/
        |├── SKILL.md
        |├── references/
        |│   └── search-tips.md
        |└── scripts/
        |    ├── fetch.sh
        |    └── summarize.py
    """.trimMargin()

    private val PDF_UTILS_TREE = """
        |pdf-utils/
        |├── references/
        |│   └── pdf-spec.md
        |└── scripts/
        |    └── extract.py
    """.trimMargin()

    private val CODE_REVIEW_TREE = """
        |code-review/
        |├── SKILL.md
        |└── assets/
        |    └── checklist.md
    """.trimMargin()

    private val ANDROID_DEBUG_TREE = """
        |android-debug/
        |├── SKILL.md
        |└── scripts/
        |    └── logcat-filter.sh
    """.trimMargin()

    private val KMP_MIGRATION_TREE = """
        |kmp-migration/
        |└── references/
        |    └── migration-notes.md
    """.trimMargin()

    // ── 插件 README（详情弹窗「查看README.md」预览） ──
    private val GIT_PLUGIN_README = """
        |# pi-plugin-git
        |
        |Git 仓库管理插件：提交、分支、日志一站式操作。
        |
        |## 命令
        |
        |- `/git-log` — 查看提交历史
        |- `/git-status` — 工作区状态
        |- `/git-branch` — 分支切换与合并
        |
        |## 安装
        |
        |```bash
        |pi install npm:@x/pi-plugin-git
        |```
    """.trimMargin()

    private val WEB_PLUGIN_README = """
        |# pi-plugin-web
        |
        |网页内容抓取与聚合搜索插件。
        |
        |## 工具
        |
        |- `web_fetch` — 抓取网页正文（自动转 Markdown）
        |- `web_search` — 聚合多源搜索
        |
        |> 需要走代理时在 settings.json 配置 httpProxy。
        |
        |## 安装
        |
        |```bash
        |pi install npm:@x/pi-plugin-web
        |```
    """.trimMargin()

    private val GREP_PLUGIN_README = """
        |# pi-plugin-grep
        |
        |基于 ripgrep 的高性能代码搜索插件。
        |
        |## 使用
        |
        |```
        |/grep <pattern> [path]
        |```
        |
        |## 权限
        |
        |- 只读：搜索目录需加入白名单
    """.trimMargin()

    // ── 插件资源清单（详情弹窗「已解析资源」；pi-web ResourceList 结构） ──
    // pi-plugin-git：1 扩展 + 2 提示词；loaded、无 pinned
    private val GIT_PLUGIN_RESOURCES = listOf(
        PluginResource(PluginResourceKind.EXTENSION, "index.ts", "extensions/index.ts"),
        PluginResource(PluginResourceKind.PROMPT, "git-commit.md", "prompts/git-commit.md"),
        PluginResource(PluginResourceKind.PROMPT, "git-release.md", "prompts/git-release.md", enabled = false),
    )
    // pi-plugin-web：1 扩展 + 1 技能 + 1 提示词；包级禁用
    private val WEB_PLUGIN_RESOURCES = listOf(
        PluginResource(PluginResourceKind.EXTENSION, "index.ts", "extensions/index.ts"),
        PluginResource(PluginResourceKind.SKILL, "web-fetch", "skills/web-fetch/SKILL.md"),
        PluginResource(PluginResourceKind.PROMPT, "summarize.md", "prompts/summarize.md"),
    )
    // pi-plugin-grep：1 扩展 + 1 提示词；installed（未加载）+ pinned ref 已配置
    private val GREP_PLUGIN_RESOURCES = listOf(
        PluginResource(PluginResourceKind.EXTENSION, "grep.ts", "extensions/grep.ts"),
        PluginResource(PluginResourceKind.PROMPT, "grep-report.md", "prompts/grep-report.md"),
    )

    val globalSkills = mutableStateListOf(
        SkillItem("web-research", "联网检索与资料整理", enabled = true, global = true,
            skillMd = WEB_RESEARCH_MD, fileTree = WEB_RESEARCH_TREE),
        SkillItem("pdf-utils", "PDF 文档解析与导出", enabled = false, global = true,
            skillMd = PDF_UTILS_MD, fileTree = PDF_UTILS_TREE),
        SkillItem("code-review", "代码审查助手", enabled = true, global = true,
            skillMd = CODE_REVIEW_MD, fileTree = CODE_REVIEW_TREE),
    )
    val projectSkills = mutableStateListOf(
        SkillItem("android-debug", "Android 崩溃定位与日志分析", enabled = true, global = false,
            skillMd = ANDROID_DEBUG_MD, fileTree = ANDROID_DEBUG_TREE),
        SkillItem("kmp-migration", "Kotlin Multiplatform 迁移检查", enabled = false, global = false,
            skillMd = KMP_MIGRATION_MD, fileTree = KMP_MIGRATION_TREE),
    )
    val globalPlugins = mutableStateListOf(
        PluginItem("pi-plugin-git", "npm:@x/pi-plugin-git", enabled = true, global = true,
            desc = "Git 仓库管理：提交、分支、日志",
            readmeMd = GIT_PLUGIN_README,
            version = "1.4.2", status = PluginStatus.LOADED,
            resources = GIT_PLUGIN_RESOURCES),
        PluginItem("pi-plugin-web", "npm:@x/pi-plugin-web", enabled = false, global = true,
            desc = "网页抓取与聚合搜索",
            readmeMd = WEB_PLUGIN_README,
            version = "0.9.1", status = PluginStatus.LOADED,
            resources = WEB_PLUGIN_RESOURCES),
    )
    val projectPlugins = mutableStateListOf(
        PluginItem("pi-plugin-grep", "git:example/pi-plugin-grep", enabled = true, global = false,
            desc = "项目内高性能代码搜索",
            readmeMd = GREP_PLUGIN_README,
            version = "2.1.0", configuredVersion = "2.0.0", status = PluginStatus.INSTALLED,
            resources = GREP_PLUGIN_RESOURCES),
    )

    /** 技能市场搜索 mock（对应 pi-web /api/skills/search → skills.sh） */
    val marketSkills = listOf(
        SkillItem("mcp-builder", "用 MCP 构建工具链并生成 server 骨架", enabled = false),
        SkillItem("data-analyzer", "CSV/JSON 数据分析与图表生成", enabled = false),
        SkillItem("translation-assistant", "中英互译与术语一致性检查", enabled = false),
        SkillItem("screenshot-diff", "截图对比与 UI 回归检测", enabled = false),
    )
}

// ─────────────────────────────────────────────────────────────
// 文件树（mock 项目根；白名单 isPathWithinRoots 语义的 UI 侧演示数据）
// ─────────────────────────────────────────────────────────────
object MockFileTree {
    val root = FileNode(
        name = "my-android-app",
        isDir = true,
        children = listOf(
            FileNode(
                name = "README.md",
                isDir = false,
                size = 4096L,
                modifiedAt = 1754000000000L,
                content = "# my-android-app\n\n登录模块演示工程。\n\n## 快速开始\n\n```bash\n./gradlew assembleDebug\n```\n\n## 模块\n\n- `app` — 主工程\n- `Docx` — 项目文档\n\n> 本地文件链接示例：[登录修复笔记](Docx/notes.md)\n",
            ),
            FileNode(
                name = "build.gradle.kts",
                isDir = false,
                size = 2048L,
                modifiedAt = 1755000000000L,
                content = "plugins {\n    id(\"com.android.application\")\n    id(\"org.jetbrains.kotlin.android\")\n}\n\nandroid {\n    namespace = \"com.example.app\"\n    compileSdk = 36\n    defaultConfig {\n        minSdk = 26\n        targetSdk = 36\n    }\n}\n",
            ),
            FileNode(
                name = "app",
                isDir = true,
                children = listOf(
                    FileNode(name = "src", isDir = true, children = listOf(
                        FileNode(
                            name = "AndroidManifest.xml",
                            isDir = false,
                            size = 1024L,
                            modifiedAt = 1755500000000L,
                            content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n    <application android:label=\"Demo\">\n        <activity android:name=\".LoginActivity\" android:exported=\"true\"/>\n    </application>\n</manifest>\n",
                        ),
                        FileNode(name = "java", isDir = true, children = listOf(
                            FileNode(name = "com", isDir = true, children = listOf(
                                FileNode(name = "example", isDir = true, children = listOf(
                                    FileNode(
                                        name = "LoginViewModel.kt",
                                        isDir = false,
                                        size = 3072L,
                                        modifiedAt = 1756000000000L,
                                        content = "package com.example\n\nclass LoginViewModel(\n    private val tokenStorage: TokenStorage,\n) {\n    val token = runCatching { tokenStorage.get() }\n        .getOrNull() ?: emptyToken()\n\n    fun login(user: String, pass: String): Boolean {\n        return user.isNotBlank() && pass.length >= 8\n    }\n}\n",
                                    ),
                                )),
                            )),
                        )),
                    )),
                ),
            ),
            FileNode(
                name = "Docx",
                isDir = true,
                children = listOf(
                    FileNode(
                        name = "notes.md",
                        isDir = false,
                        size = 1536L,
                        modifiedAt = 1754500000000L,
                        content = "# 登录修复笔记\n\n**根因**：`tokenStorage` 首启未初始化。\n\n- 修复：`runCatching` + 兜底\n- 验证：单测 12/12\n",
                    ),
                ),
            ),
            FileNode(name = "assets", isDir = true, children = listOf(
                FileNode(
                    name = "logo.png",
                    isDir = false,
                    size = 20480L,
                    modifiedAt = 1740000000000L,
                    imageHint = "PNG 图像 · 72×72 · 预览占位（原型）",
                ),
            )),
        ),
    )
}

// ─────────────────────────────────────────────────────────────
// 终端（Ubuntu 24.04 ARM64 rootfs；与 Agent bash 通道共用同一 rootfs）
// ─────────────────────────────────────────────────────────────
object MockTerminal {
    /** 产品名 slant 字体 ASCII Logo 行（终端横幅与关于页共用；32 字符等宽对齐） */
    val logoLines = listOf(
        "    ____  ___________   ________",
        "   / __ \\/  _/ ____/ | / /_  __/",
        "  / /_/ // // __/ /  |/ / / /   ",
        " / ____// // /___/ /|  / / /    ",
        "/_/   /___/_____/_/ |_/ /_/     ",
    )

    /**
     * 终端横幅（规范排版：最顶部 Logo → 空一行 → Slogan）：
     * 5 行 slant 字体 "PIENT"（BANNER=品牌蓝）+ 空行隔开图案与文字（避免拥挤）
     * + Slogan（SLOGAN=青）。
     */
    val banner = buildList {
        logoLines.forEach { add(TerminalLine(it, TerminalLineKind.BANNER)) }
        add(TerminalLine(" ", TerminalLineKind.OUTPUT))
        add(TerminalLine(">> Your private local terminal environment on Android <<", TerminalLineKind.SLOGAN))
    }

    val sessions = mutableStateListOf(
        TerminalSession(1, "会话1").apply {
            lines.addAll(banner)
            lines += TerminalLine("~ \$ ls", TerminalLineKind.COMMAND)
            lines += TerminalLine("README.md  app/  Docx/  assets/", TerminalLineKind.OUTPUT)
            lines += TerminalLine("~ \$ python3 --version", TerminalLineKind.COMMAND)
            lines += TerminalLine("Python 3.12.3", TerminalLineKind.OUTPUT)
        },
        TerminalSession(2, "会话2").apply {
            lines.addAll(banner)
            lines += TerminalLine("~ \$ git status", TerminalLineKind.COMMAND)
            lines += TerminalLine("On branch main\nnothing to commit, working tree clean", TerminalLineKind.OUTPUT)
        },
    )

    /** 会话编号计数（删除后新建不重名） */
    private var sessionCounter = 2

    /** 新建终端会话（Operit onNewSession 同款：横幅 + 切换选中） */
    fun newSession(): TerminalSession {
        sessionCounter++
        val s = TerminalSession(sessionCounter, "会话$sessionCounter")
        s.lines.addAll(banner)
        sessions += s
        return s
    }

    /** mock 命令执行：返回输出行列表；null = 不支持 */
    fun run(command: String): List<String> {
        val c = command.trim()
        val parts = c.split(Regex("\\s+"))
        return when {
            c.isEmpty() -> emptyList()
            c == "ls" || c.startsWith("ls ") -> listOf("README.md  app/  Docx/  assets/")
            c == "pwd" -> listOf("/root")
            c == "python3 --version" || c == "python --version" ->
                listOf("Python 3.12.3 (main, Apr 10 2025, 05:13:16) [GCC 13.2.0] on linux")
            c == "uname -a" -> listOf("Linux localhost 6.1.99-android14 pient #1 SMP aarch64 GNU/Linux")
            c == "git status" -> listOf("On branch main", "nothing to commit, working tree clean")
            c == "help" || c == "?" -> listOf(
                "可用演示命令：",
                "  ls / pwd / uname -a / python3 --version",
                "  echo <text> / git status / apt install <pkg>",
                "  help",
            )
            c.startsWith("echo ") -> listOf(c.removePrefix("echo "))
            c.startsWith("apt ") -> listOf(
                "Reading package lists... Done",
                "Building dependency tree... Done",
                "${parts.getOrElse(1) { "pkg" }} is already the newest version.",
                "0 upgraded, 0 newly installed.",
            )
            c.startsWith("cat ") -> listOf("# 文件内容预览请使用「文件」面板（原型演示）")
            c == "clear" -> emptyList()
            else -> listOf("bash: ${parts.first()}: command not found")
        }
    }
}
