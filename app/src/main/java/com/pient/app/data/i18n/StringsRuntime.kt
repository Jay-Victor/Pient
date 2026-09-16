package com.pient.app.data.i18n

/** runtime 组文案（由 i18n 流水线生成；zh = 源码原文字面量） */
interface RuntimeStrings {
    fun turnException(a0: Any?): String
    fun requestFailed(a0: Any?): String
    fun installComponentsLabel(a0: Any?, a1: Any?): String
    val newSessionTitle: String
    val invalidSkillName: String
    val blockedPiBusy: String
    val noModelConfigured: String
    val unknownError: String
    val noProviderOrModel: String
    val compactFailedPrefix: String
    fun timeoutSuffix(a0: Any?): String
    val commandFailed: String
    val frontmatterDescRequired: String
    fun skillExistsSlug(a0: Any?): String
    val writeFailed: String
    val networkRequestFailed: String
    val mirrorFailed: String
    val projectNameEmpty: String
    val projectNameExists: String
    val projectNotFound: String
    val projectPathInvalid: String
    fun dirNameExists(a0: Any?): String
    val dirRenameFailed: String
    val noProviderOrModelHint: String
    val piNotUnpacked: String
    val piChannelStartFailed: String
    val piChannelExitedImmediately: String
    val piChannelUnresponsive: String
    val piNotReadyNotSent: String
    val noModelConfiguredDetail: String
    val busyTryLater: String
    val noContextAvailable: String
    val piChannelStartFailedHint: String
    val piNotReadyTurnNotSent: String
    val branchTitlePrefix: String
    val sessionTitle: String
    val piChannelDisconnected: String
    val piChannelTimeout: String
    fun toolPreviewTruncated(a0: Any?): String
    val piRejectedRequest: String
    val piChannelNotReady: String
    val compactInProgress: String
    val compactNoResponse: String
    val piRejectedCompact: String
    val appContextNotReady: String
    val piChannelNotReadyHint: String
    fun systemPromptFetchFailed(a0: Any?): String
    val systemPromptEmpty: String
    val aiReplying: String
    val connected: String
    val compactReasonManual: String
    val compactReasonThreshold: String
    val compactReasonOverflow: String
    val compactReasonDefault: String
    val shellBackendNone: String
    val shellBackendShizuku: String
    val shellNeedsPrivilege: String
    val shellSwitchTierHint: String
    val shellNoShizukuApp: String
    val shellShizukuNotRunning: String
    val shellShizukuUnauthorized: String
    val shellReadyShizuku: String
    val shellReadyRoot: String
    val shellNoSu: String
    fun suStartFailed(a0: Any?): String
    val shizukuSpawnFailed: String
    fun installComponentsStep(a0: Any?): String
    val launcherMissing: String
    val outputHeadOmitted: String
    val scriptRunning: String
    val keepAliveChannelName: String
    val keepAliveChannelDesc: String
    val residentRunning: String
    val keepAliveInterruptedTitle: String
    val keepAliveInterruptedText: String
    val replyChannelSilent: String
    val replyChannelSound: String
    val replyChannelVibration: String
    val replyChannelBoth: String
    val replyChannelDesc: String
    fun keepAliveBigModelThinking(a0: Any?, a1: Any?): String
    fun keepAliveBigRuntime(a0: Any?, a1: Any?): String
    val piPackagesSession: String
    val ubuntuNotReady: String
    fun piListFailed(a0: Any?): String
    fun installSource(a0: Any?): String
    fun removeSource(a0: Any?): String
    fun updateSource(a0: Any?): String
    val updateAllPackages: String
    val packageFiltered: String
    val rpcNotReady: String
    fun rpcLauncherMissing(a0: Any?): String
    val noRootfsArchive: String
    val rootfsNotUnpacked: String
    val bashArchPrefix: String
    val bashArchHostMismatch: String
    val bashPermissionIssue: String
    val unpackAlreadyRunning: String
    val apkNoUbuntuPrefix: String
    fun apkNoUbuntuHint(a0: Any?): String
    fun cannotCreatePath(a0: Any?): String
    val extractingArchive: String
    fun unpackingMb(a0: Any?): String
    fun unpackingDirs(a0: Any?, a1: Any?): String
    val unpackDone: String
    val unpackNoBash: String
    fun unpackException(a0: Any?): String
    val treeTruncated: String
    val frontmatterMissing: String
    val cannotOpenFile: String
    val zipNoFiles: String
    val zipNoSkillMd: String
    val skillMdReadFailed: String
    val skillMdFrontmatterMissing: String
    val unzipFailed: String
    fun skillExistsName(a0: Any?): String
    val frontmatterMissingShort: String
    val descriptionEmpty: String
    val skillMarketSession: String
    val enterKeyword: String
    fun installSkillLabel(a0: Any?): String
    val marketNoNpx: String
    fun searchFailed(a0: Any?, a1: Any?): String
    val searchFailedNoResult: String
    val aiMirrorSession: String
    val aiMirrorBanner: String
    fun sessionName(a0: Any?): String
    val sessionProcessRestarting: String
    fun writeFailedDetail(a0: Any?): String
    val probeTimeout: String
    val rootfsUnpackingTask: String
    val apkNoRootfsArchive: String
    val rootfsAutoUnpacking: String
    val unpackDoneStartSession: String
    val autoUnpackFailed: String
    fun terminalRuntimeMissing(a0: Any?): String
    fun sessionStartFailed(a0: Any?): String
    val terminalSessionRunning: String
    fun sessionExited(a0: Any?): String
    val installDone: String
    fun installFailed(a0: Any?): String
}

object ZhRuntime : RuntimeStrings {
    override fun turnException(a0: Any?): String = "本轮异常：${a0}"
    override fun requestFailed(a0: Any?): String = "⚠️ 请求失败：${a0}"
    override fun installComponentsLabel(a0: Any?, a1: Any?): String = "环境配置 · 安装 ${a0} 个组件（${a1}）"
    override val newSessionTitle: String = "新建会话"
    override val invalidSkillName: String = "技能名不合法"
    override val blockedPiBusy: String = "AI 还在处理上一条消息，本条没有发出去（等它收尾后再发）"
    override val noModelConfigured: String = "尚未配置可用模型"
    override val unknownError: String = "未知错误"
    override val noProviderOrModel: String = "没有可用的服务商 / 模型"
    override val compactFailedPrefix: String = "压缩失败："
    override fun timeoutSuffix(a0: Any?): String = "\n（超时 ${a0}s）"
    override val commandFailed: String = "命令执行失败"
    override val frontmatterDescRequired: String = "frontmatter 里的 description 不能为空（pi 靠它决定要不要加载）"
    override fun skillExistsSlug(a0: Any?): String = "已存在同名技能：${a0}"
    override val writeFailed: String = "写入失败"
    override val networkRequestFailed: String = "网络请求失败"
    override val mirrorFailed: String = "失败"
    override val projectNameEmpty: String = "项目名不能为空"
    override val projectNameExists: String = "已存在同名项目"
    override val projectNotFound: String = "项目不存在"
    override val projectPathInvalid: String = "项目路径异常，无法改名"
    override fun dirNameExists(a0: Any?): String = "目录已存在同名文件夹：${a0}"
    override val dirRenameFailed: String = "目录改名失败（可能被占用）"
    override val noProviderOrModelHint: String = "没有可用的服务商 / 模型：先到「服务商与模型配置」里配好"
    override val piNotUnpacked: String = "pi 未就绪：随包运行时还没解出来（可在「环境配置」里重新检测）"
    override val piChannelStartFailed: String = "pi 通道启动失败"
    override val piChannelExitedImmediately: String = "pi 通道起来后立刻退出"
    override val piChannelUnresponsive: String = "pi 通道无响应"
    override val piNotReadyNotSent: String = "pi 运行时未就绪：消息未发送（点上方提示条的「环境配置」修复）"
    override val noModelConfiguredDetail: String = "⚠️ 尚未配置可用模型：请在「服务商与模型配置」中添加服务商，填入 API 密钥后填写模型列表或点「刷新」拉取。"
    override val busyTryLater: String = "当前已有消息在处理中，请稍后再试"
    override val noContextAvailable: String = "缺少可用的上下文"
    override val piChannelStartFailedHint: String = "pi 通道启动失败：可到「环境配置」里检测/更新"
    override val piNotReadyTurnNotSent: String = "pi 运行时未就绪：本轮没有发送。请到「终端 → 环境配置」检查 Ubuntu / pi。"
    override val branchTitlePrefix: String = "（分支）"
    override val sessionTitle: String = "会话"
    override val piChannelDisconnected: String = "pi 通道中途断开（本轮未完成）"
    override val piChannelTimeout: String = "pi 通道超时（10 分钟未见 agent_settled）"
    override fun toolPreviewTruncated(a0: Any?): String = "\n…（共 ${a0} 字）"
    override val piRejectedRequest: String = "pi 拒绝了这次请求"
    override val piChannelNotReady: String = "pi 通道未就绪：先到「环境配置」检查 Ubuntu / pi"
    override val compactInProgress: String = "上一次压缩还在进行中"
    override val compactNoResponse: String = "压缩没有完成：pi 通道无响应"
    override val piRejectedCompact: String = "pi 拒绝了这次压缩"
    override val appContextNotReady: String = "应用上下文未就绪"
    override val piChannelNotReadyHint: String = "pi 通道未就绪（先到「环境配置」检查 Ubuntu / pi）"
    override fun systemPromptFetchFailed(a0: Any?): String = "取系统提示词失败：${a0}"
    override val systemPromptEmpty: String = "没拿到系统提示词（pi 可能还没起，或扩展命令未加载）"
    override val aiReplying: String = "AI 正在回复…"
    override val connected: String = "已连接"
    override val compactReasonManual: String = "手动"
    override val compactReasonThreshold: String = "上下文接近上限"
    override val compactReasonOverflow: String = "超出上限"
    override val compactReasonDefault: String = "上下文压缩"
    override val shellBackendNone: String = "不可用"
    override val shellBackendShizuku: String = "Shizuku（ADB 级）"
    override val shellNeedsPrivilege: String = "Android shell 需要特权通道：当前档位是「标准权限」。"
    override val shellSwitchTierHint: String = "到「系统权限设置」切到「调试权限（Shizuku）」或「Root 权限」后可用。"
    override val shellNoShizukuApp: String = "Android shell 不可用：未安装 Shizuku 应用（调试权限档）"
    override val shellShizukuNotRunning: String = "Android shell 不可用：Shizuku 服务没在运行（设备重启后需重新激活）"
    override val shellShizukuUnauthorized: String = "Android shell 不可用：Pient 还没拿到 Shizuku 授权"
    override val shellReadyShizuku: String = "Android shell 就绪：Shizuku（ADB 级，shell 身份）"
    override val shellReadyRoot: String = "Android shell 就绪：Root（su，uid 0）"
    override val shellNoSu: String = "Android shell 不可用：设备没有可用的 su（Root 权限档）"
    override fun suStartFailed(a0: Any?): String = "su 启动失败：${a0}"
    override val shizukuSpawnFailed: String = "Shizuku 创建进程失败（服务可能已被系统回收）"
    override fun installComponentsStep(a0: Any?): String = "安装 ${a0} 个组件"
    override val launcherMissing: String = "Ubuntu 终端启动器缺失（终端层未就绪）"
    override val outputHeadOmitted: String = "…（前段输出已省略）…\n"
    override val scriptRunning: String = "终端命令执行中…"
    override val keepAliveChannelName: String = "Pient 运行状态"
    override val keepAliveChannelDesc: String = "AI 回合 / 终端命令执行中的前台通知（避免进程被系统清掉）"
    override val residentRunning: String = "Pient 后台常驻中"
    override val keepAliveInterruptedTitle: String = "后台保活已中断"
    override val keepAliveInterruptedText: String = "前台服务到达系统时限被停止（Android 15+ 对 dataSync 类型有「后台累计 6 小时 / 24 小时」上限），后台保活已中断；重新打开 Pient 即可恢复"
    override val replyChannelSilent: String = "Pient 消息通知"
    override val replyChannelSound: String = "Pient 消息通知（提示音）"
    override val replyChannelVibration: String = "Pient 消息通知（震动）"
    override val replyChannelBoth: String = "Pient 消息通知（提示音和震动）"
    override val replyChannelDesc: String = "AI 回复完成后的系统通知（应用不在前台时）"
    override fun keepAliveBigModelThinking(a0: Any?, a1: Any?): String = "模型：${a0} · 思考：${a1}"
    override fun keepAliveBigRuntime(a0: Any?, a1: Any?): String = "终端会话：${a0} · 工具调用：${a1}"
    override val piPackagesSession: String = "pi 包管理"
    override val ubuntuNotReady: String = "Ubuntu 还没就绪（缺 pient-shell）"
    override fun piListFailed(a0: Any?): String = "pi list 失败（退出码 ${a0}）"
    override fun installSource(a0: Any?): String = "安装 ${a0}"
    override fun removeSource(a0: Any?): String = "移除 ${a0}"
    override fun updateSource(a0: Any?): String = "更新 ${a0}"
    override val updateAllPackages: String = "更新全部包"
    override val packageFiltered: String = "该包在本项目被过滤（filtered）"
    override val rpcNotReady: String = "Ubuntu/pi 未就绪"
    override fun rpcLauncherMissing(a0: Any?): String = "缺少终端启动器：${a0}"
    override val noRootfsArchive: String = "此安装包未内置 Ubuntu 环境（构建时未打包 rootfs 归档）"
    override val rootfsNotUnpacked: String = "Ubuntu 运行时还没解包完（可在本页「重新检测」，或重启应用继续解包）"
    override val bashArchPrefix: String = "Ubuntu 运行时不可用：bash 架构 "
    override val bashArchHostMismatch: String = " ≠ 本机 "
    override val bashPermissionIssue: String = "，或文件权限异常（bash 需可读可执行）"
    override val unpackAlreadyRunning: String = "已有一个解包任务在进行中…"
    override val apkNoUbuntuPrefix: String = "此 APK 未内置 Ubuntu 环境（构建时未拉取本机 ABI 的 rootfs："
    override fun apkNoUbuntuHint(a0: Any?): String = "先跑 runtime/scripts/fetch_rootfs.py --abi ${a0} 再打包）"
    override fun cannotCreatePath(a0: Any?): String = "无法创建 ${a0}"
    override val extractingArchive: String = "释放归档…"
    override fun unpackingMb(a0: Any?): String = "解包中（${a0} MB）…"
    override fun unpackingDirs(a0: Any?, a1: Any?): String = "解包中…（${a0}/${a1} 顶层目录）"
    override val unpackDone: String = "解包完成"
    override val unpackNoBash: String = "解包后仍未找到 /bin/bash"
    override fun unpackException(a0: Any?): String = "解包异常：${a0}"
    override val treeTruncated: String = "…（截断）\n"
    override val frontmatterMissing: String = "缺少 frontmatter（文件要以 --- 开头，里面有 name 和 description）"
    override val cannotOpenFile: String = "打不开所选文件"
    override val zipNoFiles: String = "压缩包里没有文件"
    override val zipNoSkillMd: String = "压缩包里没有 SKILL.md（技能目录必须含 SKILL.md）"
    override val skillMdReadFailed: String = "SKILL.md 读取失败"
    override val skillMdFrontmatterMissing: String = "SKILL.md 缺少 frontmatter（文件要以 --- 开头，里面有 name 和 description）"
    override val unzipFailed: String = "解压失败"
    override fun skillExistsName(a0: Any?): String = "已存在同名技能：${a0}"
    override val frontmatterMissingShort: String = "缺少 frontmatter"
    override val descriptionEmpty: String = "description 为空"
    override val skillMarketSession: String = "技能市场"
    override val enterKeyword: String = "请输入关键词"
    override fun installSkillLabel(a0: Any?): String = "安装技能 ${a0}"
    override val marketNoNpx: String = "技能市场不可用：Ubuntu 里还没有 Node/npx（去「环境配置 → Node.js」装一次即可）"
    override fun searchFailed(a0: Any?, a1: Any?): String = "搜索失败（退出码 ${a0}）：${a1}"
    override val searchFailedNoResult: String = "没有匹配的技能（npx skills find 无结果）"
    override val aiMirrorSession: String = "AI 执行"
    override val aiMirrorBanner: String = "pi 工具执行镜像（只读）：AI 在 Ubuntu 里跑的每一步都记在这里；自己的命令请用别的会话。"
    override fun sessionName(a0: Any?): String = "会话${a0}"
    override val sessionProcessRestarting: String = "会话进程已退出，正在重建…"
    override fun writeFailedDetail(a0: Any?): String = "写入失败：${a0}"
    override val probeTimeout: String = "(探针超时)"
    override val rootfsUnpackingTask: String = "rootfs 正在解包（已有任务在跑）—— 完成后重开本页即可"
    override val apkNoRootfsArchive: String = "此 APK 未内置 rootfs 归档（构建时没跑 fetch_rootfs.py --abi 本机 ABI）—— 无法自动解包；请换用含归档的包"
    override val rootfsAutoUnpacking: String = "rootfs 未初始化：自动解包中（约 30MB / 1–2 分钟，进度见下）…"
    override val unpackDoneStartSession: String = "解包完成，启动会话…"
    override val autoUnpackFailed: String = "自动解包失败：原因见上；也可到「环境配置」页重试"
    override fun terminalRuntimeMissing(a0: Any?): String = "终端运行时缺失：${a0}"
    override fun sessionStartFailed(a0: Any?): String = "会话启动失败：${a0}"
    override val terminalSessionRunning: String = "终端会话运行中…"
    override fun sessionExited(a0: Any?): String = "[会话进程已退出，退出码 ${a0}]"
    override val installDone: String = "[环境配置] 安装完成（退出码 0）"
    override fun installFailed(a0: Any?): String = "[环境配置] 安装失败（退出码 ${a0}）"
}

object EnRuntime : RuntimeStrings {
    override fun turnException(a0: Any?): String = "Turn failed: ${a0}"
    override fun requestFailed(a0: Any?): String = "⚠️ Request failed: ${a0}"
    override fun installComponentsLabel(a0: Any?, a1: Any?): String = "Environment · installing ${a0} components (${a1})"
    override val newSessionTitle: String = "New session"
    override val invalidSkillName: String = "Invalid skill name"
    override val blockedPiBusy: String = "The AI is still processing the previous message; this one was not sent (try again once it finishes)"
    override val noModelConfigured: String = "No model configured"
    override val unknownError: String = "Unknown error"
    override val noProviderOrModel: String = "No Provider / Model available"
    override val compactFailedPrefix: String = "Compaction failed: "
    override fun timeoutSuffix(a0: Any?): String = "\n(timeout ${a0}s)"
    override val commandFailed: String = "Command failed"
    override val frontmatterDescRequired: String = "The description in frontmatter cannot be empty (pi uses it to decide whether to load the skill)"
    override fun skillExistsSlug(a0: Any?): String = "A skill with the same name already exists: ${a0}"
    override val writeFailed: String = "Write failed"
    override val networkRequestFailed: String = "Network request failed"
    override val mirrorFailed: String = "Failed"
    override val projectNameEmpty: String = "Project name cannot be empty"
    override val projectNameExists: String = "A project with the same name already exists"
    override val projectNotFound: String = "Project not found"
    override val projectPathInvalid: String = "The project path is invalid, cannot rename"
    override fun dirNameExists(a0: Any?): String = "A folder with the same name already exists: ${a0}"
    override val dirRenameFailed: String = "Failed to rename the directory (it may be in use)"
    override val noProviderOrModelHint: String = "No Provider / Model available: configure one in \"Providers & Models\" first"
    override val piNotUnpacked: String = "pi is not ready: the bundled runtime has not been unpacked yet (re-check it in \"Environment\")"
    override val piChannelStartFailed: String = "Failed to start the pi channel"
    override val piChannelExitedImmediately: String = "The pi channel exited right after it started"
    override val piChannelUnresponsive: String = "The pi channel is not responding"
    override val piNotReadyNotSent: String = "pi runtime is not ready: the message was not sent (tap \"Environment\" in the banner above to fix it)"
    override val noModelConfiguredDetail: String = "⚠️ No model configured: add a Provider in \"Providers & Models\", enter the API Key, then fill in the model list or tap \"Refresh\" to fetch it."
    override val busyTryLater: String = "A message is already being processed; try again later"
    override val noContextAvailable: String = "No usable Context available"
    override val piChannelStartFailedHint: String = "Failed to start the pi channel: check/update it in \"Environment\""
    override val piNotReadyTurnNotSent: String = "pi runtime is not ready: this turn was not sent. Check Ubuntu / pi under \"Terminal → Environment\"."
    override val branchTitlePrefix: String = "(Branch) "
    override val sessionTitle: String = "Session"
    override val piChannelDisconnected: String = "The pi channel disconnected mid-turn (this turn did not finish)"
    override val piChannelTimeout: String = "The pi channel timed out (no agent_settled within 10 minutes)"
    override fun toolPreviewTruncated(a0: Any?): String = "\n… (${a0} characters total)"
    override val piRejectedRequest: String = "pi rejected this request"
    override val piChannelNotReady: String = "pi channel is not ready: check Ubuntu / pi in \"Environment\" first"
    override val compactInProgress: String = "A compaction is already running"
    override val compactNoResponse: String = "Compaction did not finish: the pi channel is not responding"
    override val piRejectedCompact: String = "pi rejected this compaction"
    override val appContextNotReady: String = "App context is not ready"
    override val piChannelNotReadyHint: String = "pi channel is not ready (check Ubuntu / pi in \"Environment\" first)"
    override fun systemPromptFetchFailed(a0: Any?): String = "Failed to fetch the system prompt: ${a0}"
    override val systemPromptEmpty: String = "Got no system prompt (pi may not be running yet, or the extension command is not loaded)"
    override val aiReplying: String = "AI is replying…"
    override val connected: String = "Connected"
    override val compactReasonManual: String = "Manual"
    override val compactReasonThreshold: String = "Context near limit"
    override val compactReasonOverflow: String = "Over limit"
    override val compactReasonDefault: String = "Context compaction"
    override val shellBackendNone: String = "Unavailable"
    override val shellBackendShizuku: String = "Shizuku (ADB level)"
    override val shellNeedsPrivilege: String = "Android shell needs a privileged channel: the current tier is \"Standard permissions\"."
    override val shellSwitchTierHint: String = "Switch to \"Debug permissions (Shizuku)\" or \"Root permissions\" in \"System permissions\" to enable it."
    override val shellNoShizukuApp: String = "Android shell unavailable: the Shizuku app is not installed (Debug permissions tier)"
    override val shellShizukuNotRunning: String = "Android shell unavailable: the Shizuku service is not running (it must be reactivated after a device restart)"
    override val shellShizukuUnauthorized: String = "Android shell unavailable: Pient has not been granted Shizuku permission yet"
    override val shellReadyShizuku: String = "Android shell ready: Shizuku (ADB level, shell identity)"
    override val shellReadyRoot: String = "Android shell ready: Root (su, uid 0)"
    override val shellNoSu: String = "Android shell unavailable: no usable su on this device (Root permissions tier)"
    override fun suStartFailed(a0: Any?): String = "Failed to start su: ${a0}"
    override val shizukuSpawnFailed: String = "Failed to create a Shizuku process (the service may have been reclaimed by the system)"
    override fun installComponentsStep(a0: Any?): String = "Installing ${a0} components"
    override val launcherMissing: String = "Ubuntu terminal launcher missing (terminal layer not ready)"
    override val outputHeadOmitted: String = "…(earlier output omitted)…\n"
    override val scriptRunning: String = "Terminal command running…"
    override val keepAliveChannelName: String = "Pient status"
    override val keepAliveChannelDesc: String = "Foreground notification while an AI turn / terminal command runs (keeps the process from being killed by the system)"
    override val residentRunning: String = "Pient is running in the background"
    override val keepAliveInterruptedTitle: String = "Background keep-alive stopped"
    override val keepAliveInterruptedText: String = "The foreground service hit the system time limit (Android 15+ caps dataSync services at 6 hours of background runtime per 24 hours), so keep-alive has stopped; reopen Pient to restore it"
    override val replyChannelSilent: String = "Pient message notifications"
    override val replyChannelSound: String = "Pient message notifications (sound)"
    override val replyChannelVibration: String = "Pient message notifications (vibration)"
    override val replyChannelBoth: String = "Pient message notifications (sound and vibration)"
    override val replyChannelDesc: String = "System notification when an AI reply finishes (while the app is in the background)"
    override fun keepAliveBigModelThinking(a0: Any?, a1: Any?): String = "Model: ${a0} · Thinking: ${a1}"
    override fun keepAliveBigRuntime(a0: Any?, a1: Any?): String = "Terminal sessions: ${a0} · Tool calls: ${a1}"
    override val piPackagesSession: String = "pi packages"
    override val ubuntuNotReady: String = "Ubuntu is not ready yet (pient-shell is missing)"
    override fun piListFailed(a0: Any?): String = "pi list failed (exit code ${a0})"
    override fun installSource(a0: Any?): String = "Install ${a0}"
    override fun removeSource(a0: Any?): String = "Remove ${a0}"
    override fun updateSource(a0: Any?): String = "Update ${a0}"
    override val updateAllPackages: String = "Update all packages"
    override val packageFiltered: String = "This package is filtered in this project (filtered)"
    override val rpcNotReady: String = "Ubuntu/pi not ready"
    override fun rpcLauncherMissing(a0: Any?): String = "Terminal launcher missing: ${a0}"
    override val noRootfsArchive: String = "This build does not bundle the Ubuntu environment (the rootfs archive was not packaged at build time)"
    override val rootfsNotUnpacked: String = "Ubuntu runtime is not fully unpacked yet (tap \"Re-check\" on this page, or restart the app to continue unpacking)"
    override val bashArchPrefix: String = "Ubuntu runtime unavailable: bash architecture "
    override val bashArchHostMismatch: String = " ≠ the host machine "
    override val bashPermissionIssue: String = ", or the file permissions are wrong (bash must be readable and executable)"
    override val unpackAlreadyRunning: String = "An unpacking task is already in progress…"
    override val apkNoUbuntuPrefix: String = "This APK does not bundle the Ubuntu environment (the rootfs for this device's ABI was not fetched at build time: "
    override fun apkNoUbuntuHint(a0: Any?): String = "run runtime/scripts/fetch_rootfs.py --abi ${a0} before building)"
    override fun cannotCreatePath(a0: Any?): String = "Cannot create ${a0}"
    override val extractingArchive: String = "Unpacking archive…"
    override fun unpackingMb(a0: Any?): String = "Unpacking (${a0} MB)…"
    override fun unpackingDirs(a0: Any?, a1: Any?): String = "Unpacking… (${a0}/${a1} top-level directories)"
    override val unpackDone: String = "Unpacking complete"
    override val unpackNoBash: String = "Unpacking finished but /bin/bash was still not found"
    override fun unpackException(a0: Any?): String = "Unpacking failed: ${a0}"
    override val treeTruncated: String = "…(truncated)\n"
    override val frontmatterMissing: String = "Missing frontmatter (the file must start with --- and include name and description)"
    override val cannotOpenFile: String = "Cannot open the selected file"
    override val zipNoFiles: String = "The archive contains no files"
    override val zipNoSkillMd: String = "The archive has no SKILL.md (a skill directory must contain SKILL.md)"
    override val skillMdReadFailed: String = "Failed to read SKILL.md"
    override val skillMdFrontmatterMissing: String = "SKILL.md is missing frontmatter (the file must start with --- and include name and description)"
    override val unzipFailed: String = "Extraction failed"
    override fun skillExistsName(a0: Any?): String = "A skill with the same name already exists: ${a0}"
    override val frontmatterMissingShort: String = "Missing frontmatter"
    override val descriptionEmpty: String = "description is empty"
    override val skillMarketSession: String = "Skill market"
    override val enterKeyword: String = "Enter a keyword"
    override fun installSkillLabel(a0: Any?): String = "Install skill ${a0}"
    override val marketNoNpx: String = "Skill market unavailable: Node/npx is not installed in Ubuntu (install it once under \"Environment → Node.js\")"
    override fun searchFailed(a0: Any?, a1: Any?): String = "Search failed (exit code ${a0}): ${a1}"
    override val searchFailedNoResult: String = "No matching skills (npx skills find returned nothing)"
    override val aiMirrorSession: String = "AI execution"
    override val aiMirrorBanner: String = "pi tool execution mirror (read-only): every step the AI runs in Ubuntu is recorded here; use another session for your own commands."
    override fun sessionName(a0: Any?): String = "Session ${a0}"
    override val sessionProcessRestarting: String = "The session process exited; restarting…"
    override fun writeFailedDetail(a0: Any?): String = "Write failed: ${a0}"
    override val probeTimeout: String = "(probe timed out)"
    override val rootfsUnpackingTask: String = "rootfs is being unpacked (a task is already running) — reopen this page once it finishes"
    override val apkNoRootfsArchive: String = "This APK does not bundle a rootfs archive (fetch_rootfs.py --abi <host ABI> was not run at build time) — automatic unpacking is unavailable; use a build that includes the archive"
    override val rootfsAutoUnpacking: String = "rootfs is not initialized: auto-unpacking (about 30MB / 1–2 minutes, progress below)…"
    override val unpackDoneStartSession: String = "Unpacking complete, starting the session…"
    override val autoUnpackFailed: String = "Auto-unpacking failed: see the reason above; you can also retry on the \"Environment\" page"
    override fun terminalRuntimeMissing(a0: Any?): String = "Terminal runtime missing: ${a0}"
    override fun sessionStartFailed(a0: Any?): String = "Failed to start the session: ${a0}"
    override val terminalSessionRunning: String = "Terminal session running…"
    override fun sessionExited(a0: Any?): String = "[Session process exited, exit code ${a0}]"
    override val installDone: String = "[Environment] install finished (exit code 0)"
    override fun installFailed(a0: Any?): String = "[Environment] install failed (exit code ${a0})"
}
