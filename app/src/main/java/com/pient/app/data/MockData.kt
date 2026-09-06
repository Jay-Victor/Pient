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
        Msg.User("登录页在 Android 14 上必现崩溃，帮我看看日志定位一下"),
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
            markdown = "已定位根因：**`LoginViewModel.kt:42`** 在首启时 `tokenStorage` 尚未初始化，\n" +
                "`get()` 调用抛 NPE。\n\n修复方案：\n\n1. 在 `init` 中以 `runCatching` 包裹 token 读取\n" +
                "2. 对 `tokenStorage` 提供 `emptyToken()` 兜底\n\n" +
                "```kotlin\n" +
                "val token = runCatching { tokenStorage.get() }\n" +
                "    .getOrNull() ?: emptyToken()\n" +
                "```\n\n" +
                "> 注：Android 14 首启时序更严格，建议一并核对 `onResume`。",
            usage = Usage(inTokens = 4208, outTokens = 612, cacheTokens = 1873, costUsd = 0.018),
        ),
        Msg.User("好的，按方案 1 修，改完帮我跑一下测试"),
        Msg.BranchBar(label = "分支 A（修复 NPE 后）", branchCount = 2),
        Msg.Assistant(
            markdown = "已应用修复并执行 `./gradlew test`：\n\n- ✅ `LoginViewModelTest` 12/12 通过\n" +
                "- ⚠️ `LoginActivityTest` 1 项与设备旋转相关，与本修复无关\n\n需要我继续处理吗？",
            usage = Usage(inTokens = 5330, outTokens = 288, cacheTokens = 4208, costUsd = 0.021),
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
 *  节点 = 用户消息；exchange 不含 BranchBar（导航切换时由 navigateToNode 注入）。 */
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
                        MockMessages.fixLoginCrash[7],
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
        "收到。我先梳理一下任务要点：\n\n- **目标**：$1\n- 已加载当前项目上下文（`my-android-app`）\n\n" +
            "```bash\npi run \"$1\"\n```\n\n> 原型演示：此处为 mock 流式回复，接入 Pi 运行时后由真实 Agent 输出替换。",
        "好的，这是一个典型的工程任务。我的思路：\n\n1. 先定位相关代码路径\n2. 分析现有实现\n3. 给出可执行方案\n\n需要我直接动手改代码吗？",
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
    val globalSkills = mutableStateListOf(
        SkillItem("web-research", "联网检索与资料整理", enabled = true, global = true),
        SkillItem("pdf-utils", "PDF 文档解析与导出", enabled = false, global = true),
        SkillItem("code-review", "代码审查助手", enabled = true, global = true),
    )
    val projectSkills = mutableStateListOf(
        SkillItem("android-debug", "Android 崩溃定位与日志分析", enabled = true, global = false),
        SkillItem("kmp-migration", "Kotlin Multiplatform 迁移检查", enabled = false, global = false),
    )
    val globalPlugins = mutableStateListOf(
        PluginItem("pi-plugin-git", "npm:@x/pi-plugin-git", enabled = true, global = true),
        PluginItem("pi-plugin-web", "npm:@x/pi-plugin-web", enabled = false, global = true),
    )
    val projectPlugins = mutableStateListOf(
        PluginItem("pi-plugin-grep", "git:example/pi-plugin-grep", enabled = true, global = false),
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
