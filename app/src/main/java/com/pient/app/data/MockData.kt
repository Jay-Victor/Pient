package com.pient.app.data

import androidx.compose.runtime.mutableStateListOf

// ─────────────────────────────────────────────────────────────
// UI 原型 mock 数据。红线：仅演示 UI 交互，不替代 pi 官方接口；
// 接入运行时后由 get_available_models / SessionManager 等官方机制替换。
// 2026-09-08：mock 项目/会话/聊天消息/文件树已全部移除——初次进入无项目，
// 聊天页显示创建项目（绑定文件夹）与配置 AI 引导；项目/会话/文件均真实数据。
// 2026-09-09：mock 模型/MockReplies 移除——AI 对话走真实服务商 API（AiBackend），
// 模型来自已配置服务商（AiConfigStore）。剩余 mock：技能/插件/终端。
// ─────────────────────────────────────────────────────────────

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
            version = "1.4.2", latestVersion = "1.5.0", status = PluginStatus.LOADED,  // 有更新（演示「检查更新 → 更新」分支）
            resources = GIT_PLUGIN_RESOURCES),
        PluginItem("pi-plugin-web", "npm:@x/pi-plugin-web", enabled = false, global = true,
            desc = "网页抓取与聚合搜索",
            readmeMd = WEB_PLUGIN_README,
            version = "0.9.1", latestVersion = "0.9.1", status = PluginStatus.LOADED,  // 已是最新（演示提示分支）
            resources = WEB_PLUGIN_RESOURCES),
    )
    val projectPlugins = mutableStateListOf(
        PluginItem("pi-plugin-grep", "git:example/pi-plugin-grep", enabled = true, global = false,
            desc = "项目内高性能代码搜索",
            readmeMd = GREP_PLUGIN_README,
            version = "2.1.0", latestVersion = "2.1.0", configuredVersion = "2.0.0", status = PluginStatus.INSTALLED,
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

    // 会话与命令执行已改为真实实现：见 runtime/PiTerminal（每会话一个经 PRoot 落到 rootfs 的
    // GNU bash 子进程）。本对象只保留品牌常量（Logo / 横幅），供终端首屏与关于页共用。
}
