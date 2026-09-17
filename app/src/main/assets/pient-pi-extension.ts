/**
 * Pient ↔ pi 的桥接扩展（随 APK 预置，安装到 guest 的 `~/.pi/agent/extensions/pient.ts`）。
 *
 * 这里一共四样东西：
 *  1. `/pient-nav <entryId>` —— 会话内分支跳转（补 RPC 缺的那条能力）；
 *  2. `/pient-sysprompt` —— 把 pi 真实生效的系统提示词回流给 App 的只读面板；
 *  3. `/pient-meta <base64(JSON)>` —— 把「引用 + 附件清单（含文件名）」的元数据写进会话（`pient_meta` custom 条目）；
 *  4. `android_shell` **工具** —— 让 AI 能在 **Android 系统**里执行命令（Ubuntu 做不到的那些：
 *     `pm`/`am`/`cmd`/`dumpsys` 等系统命令、装应用、改系统设置、读别的 app 私有数据、操作硬件）；
 *  5. **系统提示词的 Pient 化** —— `before_agent_start` 每轮改写（Pient 身份句 + 运行环境段）；
 *     `/pient-sysprompt` 回流时套同一个改写函数，保证面板显示的 = 模型真实收到的那一份。
 *
 * ── 为什么 `/pient-nav` 要自己写 ─────────────────────────────────────────────
 * pi 的官方 RPC 暴露了会话/树/分叉的**大部分**能力
 * （`get_tree` / `get_entries` / `get_fork_messages` / `fork` / `clone` / `new_session` /
 * `switch_session` / `set_session_name` / `get_session_stats`），
 * 但**没有"把活跃叶移到树里另一个节点"的命令** —— 那正是 Pi TUI 里 `/tree` 干的事，
 * 只做成内部 API（`ctx.navigateTree`）暴露给扩展。Pient 的「会话内分支」（画布上点某条
 * 历史消息继续）等于 pi 的树导航，所以这里注册一条命令补上这个缺口：
 *
 *   /pient-nav <entryId> [--summarize] [--label <文字>] [--instructions <文字>]
 *
 * pi 的 RPC 说明里写明扩展命令属于 `get_commands` 并且 **"available for invocation via prompt"**，
 * 因此 Pient 侧只要 `{"type":"prompt","message":"/pient-nav <id>"}` 即可触发。
 *
 * ── 用户消息条目：锚点标记（2026-09-15）────────────────────────────────────────
 * `navigateTree` 对 **role=user 的条目**走的是 TUI 的"重编辑"语义：叶退到该条目的**父条目**，
 * 消息文本作为 `editorText` 回填编辑器（`agent-session.ts:3236-3251`）。Pient 的
 * 「创建分支 / 切换分支」要的是**停在该消息本身**——选中节点 = 上下文切到这次对话（画布节点
 * 就是这次对话），此后发消息从它长出新枝。对**尚无回答**的节点尤其重要：它的回合链是空的、
 * 没有任何可用的锚点条目，pi 里"叶 = 某条用户消息"这个状态只有重载（文件末条目）才偶然出现。
 *
 * 做法（**全程 pi 官方 API，不碰内部字段**）：
 *   ① 先把活跃叶移到该用户消息（`sessionManager.branch`，公开方法）；
 *   ② 用官方扩展 API `pi.appendEntry()` 在它下面补一条**锚点标记**（`pient_anchor`，
 *      pi 的 `custom` 条目：`docs/session-format.md` 明写 **Does NOT participate in LLM context**，
 *      Pient 画布也只认 `type=message` 的条目 → 两边都不可见，纯结构锚点）；
 *   ③ 对这条标记走 `ctx.navigateTree(marker)` —— 非用户条目 ⇒ 叶 = 该条目，并把
 *      `agent.state.messages` 按新叶重建（含目标用户消息本身）。
 * 幂等：同一节点复用已有标记，反复点不会往会话文件里堆条目。
 *
 * ── `android_shell` 为什么走 127.0.0.1 回桥 ──────────────────────────────────
 * 本扩展跑在 **Ubuntu(PRoot) 的 guest 里**，而 Android 侧的通道只有两条，都不在 guest 里：
 * Shizuku 的 `IShizukuService.newProcess` 是 Java 侧的 binder（shell 链路根本到不了）、
 * `su` 是系统的 uid 0 通道。所以「guest → Android 系统」只有一条现实路径：
 * **应用在 loopback 上开一个执行端点**（`ExecBridge.kt`），把端口与令牌写在
 * `~/.pi/agent/.pient-exec-bridge.json`（这个文件 guest 看得见），本扩展读它、POST 命令，
 * 应用按**当前档位**（Shizuku / Root）执行后回包。PRoot 与应用共享网络命名空间，所以
 * `127.0.0.1` 就是同一台设备上的同一个应用进程。
 */

import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";
import { Type } from "typebox";
import { homedir } from "node:os";
import { join } from "node:path";
import { mkdirSync, writeFileSync, readFileSync } from "node:fs";
import { Buffer } from "node:buffer";

/** 锚点标记的 customType（Pient 与 pi 都视其为元数据） */
const ANCHOR_TYPE = "pient_anchor";

/**
 * 用户消息元数据标记的 customType（App 侧常量 `ChatState.PI_META_TYPE` 必须与它一致）。
 * `custom` 条目 = extension state persistence，pi 明写「不进 LLM 上下文」——
 * 引用仍走用户消息正文（"> …" 块引用）、附件本体也照旧随请求发出，这条标记只负责
 * 让 App 重建上屏流时把**引用卡与附件清单（含文件名）**挂回去。
 *
 * 为什么附件名要单独存：pi 的 `image` 内容块只有 `data/mimeType`（协议里没有文件名字段），
 * 而直发时正文尾部那行 "[附件] 名称 · 路径" 又按 Operit「移除链接」口径被去掉 —— 于是
 * pi 会话文件里完全查不到名字，只有 App 的本地镜像知道。（旧版 `pient_quote` 只带引用，读侧仍认。）
 */
const META_TYPE = "pient_meta";

/** 系统提示词回流文件（Pient 的「系统提示词」面板读它；App 侧不再持有提示词） */
const SYSPROMPT_FILE = ".pient-sysprompt.txt";

/**
 * ── 系统提示词的 Pient 化（2026-09-17 用户拍板）────────────────────────────────
 * pi 的基座提示词由官方包生成（`core/system-prompt.ts` 的 `buildSystemPrompt()`），
 * Pient 不动 pi 源码，只做**确定性字符串改写**：
 *   ① 首句换成 Pient 身份句；② 末尾追加一段运行环境说明（Android 客户端 / `/workspace`
 *   含义 / 跟随用户语言 / 引用与附件是 Pient 注入的元数据）。
 * 两条硬口径：
 *   - `before_agent_start` 每轮改写（官方 hook：返回 `{ systemPrompt }` 即替换本轮提示词，
 *     pi 侧把它设为**本轮 override**，随下一轮 run 结束被清掉）；
 *   - `/pient-sysprompt` 写完前面套**同一个函数** —— 命令是回合之外执行的，那时 override
 *     已被清掉、`ctx.getSystemPrompt()` 回的是基座原文，不套就会把「pi 原文」当成
 *     「真实下发的一份」写进面板（面板跟模型收到的不是同一个东西）。
 * pi 升级若改了首句文案 → 替换不命中 → 原样下发（静默退回，不报错）。
 */
const BASE_OPENING = "You are an expert coding assistant operating inside pi, a coding agent harness.";
const PIENT_OPENING = "You are Pient, an expert coding assistant operating inside pi (a coding agent harness).";

/** 追加段开头这句同时充当幂等标记 */
const ENV_MARK = "You are running inside Pient";
const ENV_SECTION = `You are running inside Pient, the Android client for pi. The user interacts with you through a mobile chat interface; there is no terminal UI or keyboard shortcuts.
- /workspace is the project folder the user selected in Pient; the in-app terminal shares the same Ubuntu environment as your bash tool.
- Answer in the language the user writes in.
- Quoted blocks ("> ...") at the start of a user message and trailing "[附件] ..." / "[附件未直发] ..." lines are metadata injected by Pient (the message the user quoted / the files they attached), not text the user typed.
- "@path" mentions inside user messages are Pient's file-reference syntax: the path is relative to /workspace (a trailing "/" means a directory). Read the file to see its content; when the mention carries a line range (e.g. "@src/app.ts:120-160", or "@src/app.ts:42" for a single line), read only that range by passing it as the read tool's offset/limit.`;

/** pi 基座提示词 → Pient 形态（幂等：首句命中才替换、追加段已存在则跳过） */
function pientify(prompt: string): string {
  let out = prompt.replace(BASE_OPENING, PIENT_OPENING);
  if (!out.includes(ENV_MARK)) out = `${out}\n\n${ENV_SECTION}`;
  return out;
}

/** Android shell 回桥的端点文件（应用写；结构见 ExecBridge.kt） */
const BRIDGE_FILE = ".pient-exec-bridge.json";

function agentDir(): string {
  return join(homedir(), ".pi", "agent");
}

interface BridgeInfo {
  port: number;
  token: string;
  backend: string;
}

/** 读回桥端点（文件不存在 / 内容不合法 ⇒ null，调用方给可读的说明） */
function bridgeInfo(): BridgeInfo | null {
  try {
    const raw = readFileSync(join(agentDir(), BRIDGE_FILE), "utf8");
    const j = JSON.parse(raw) as Partial<BridgeInfo>;
    if (typeof j.port === "number" && typeof j.token === "string") {
      return { port: j.port, token: j.token, backend: typeof j.backend === "string" ? j.backend : "unknown" };
    }
  } catch {
    /* 未就绪 */
  }
  return null;
}

interface BridgeResult {
  ok?: boolean;
  backend?: string;
  exit?: number;
  stdout?: string;
  stderr?: string;
  timeout?: boolean;
  note?: string;
}

/** 让 App 在 Android 侧执行一条命令（超时由应用侧兜底，这里再加一层 fetch 超时） */
async function androidExec(cmd: string, timeoutMs: number): Promise<BridgeResult> {
  const info = bridgeInfo();
  if (!info) {
    return {
      ok: false,
      note:
        "Android shell 回桥未就绪：App 还没写好端点文件（~/.pi/agent/.pient-exec-bridge.json）。" +
        "请确认 Pient 应用在前台运行过、且 Ubuntu 运行时已解包。",
    };
  }
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), timeoutMs + 10_000);
  try {
    const res = await fetch(`http://127.0.0.1:${info.port}/exec`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ token: info.token, cmd, timeoutMs }),
      signal: ctl.signal,
    });
    return (await res.json()) as BridgeResult;
  } catch (e) {
    return {
      ok: false,
      note: `回桥调用失败：${e instanceof Error ? e.message : String(e)}（应用可能已被系统回收，回到 Pient 前台再试）`,
    };
  } finally {
    clearTimeout(timer);
  }
}

/** 把结果格式化成模型好读的文本（stdout/stderr/退出码都给，别让模型猜） */
function formatResult(r: BridgeResult, cmd: string): string {
  if (r.note && !r.ok) return `${r.note}\n（命令未执行：${cmd}）`;
  const lines: string[] = [];
  lines.push(`# backend=${r.backend ?? "?"} exit=${r.exit ?? "?"}${r.timeout ? " （超时，已中断）" : ""}`);
  if (r.stdout && r.stdout.length > 0) lines.push(r.stdout);
  if (r.stderr && r.stderr.length > 0) lines.push(`[stderr]\n${r.stderr}`);
  if ((!r.stdout || r.stdout.length === 0) && (!r.stderr || r.stderr.length === 0)) lines.push("（无输出）");
  return lines.join("\n");
}

export default function (pi: ExtensionAPI) {
  // ── 系统提示词的 Pient 化（2026-09-17）──────────────────────────────────────
  // 官方 hook（docs/extensions.md「before_agent_start」）：返回 { systemPrompt } =
  // **替换本轮系统提示词**（chained across extensions）。内容没变就不返回（别把
  // 「无改写」也标成 modified）。
  pi.on("before_agent_start", async (event) => {
    const next = pientify(event.systemPrompt);
    return next === event.systemPrompt ? undefined : { systemPrompt: next };
  });

  // ── 系统提示词回流（2026-09-15）──────────────────────────────────────────────
  // 提示词的持有者是 pi（base prompt + 项目 context 文件 + 扩展改写），App 看不到。
  // Pient 的「系统提示词」只读面板打开时触发这条命令 → 把**真实下发的那一份**写到
  // `~/.pi/agent/.pient-sysprompt.txt`（App 侧从 rootfs 里读回）。
  // 改写只对运行中的那一轮有效，命令是回合外跑的 → 套 [pientify]（与 before_agent_start
  // 同一个函数）才等于模型真实收到的形态；对已改写的文本幂等。
  pi.registerCommand("pient-sysprompt", {
    description: "Pient: 把当前生效的系统提示词写到 ~/.pi/agent/.pient-sysprompt.txt（供 App 只读展示）",
    handler: async (_args, ctx) => {
      const text = pientify(ctx.getSystemPrompt());
      try {
        mkdirSync(agentDir(), { recursive: true });
        writeFileSync(join(agentDir(), SYSPROMPT_FILE), text, "utf8");
        ctx.ui.notify(`pient-sysprompt: 已写出系统提示词（${text.length} 字）`, "info");
      } catch (e) {
        ctx.ui.notify(`pient-sysprompt 写文件失败：${e instanceof Error ? e.message : String(e)}`, "error");
      }
    },
  });

  // ── 用户消息元数据标记（2026-09-17）──────────────────────────────────────────
  // Pient 把「引用」拼成用户消息正文开头的 markdown 块引用、把附件清单拼成正文尾部的
  // "[附件] 名称 · 路径" 行 —— 两者都**只有文本**：一旦 App 按 pi 重建上屏流（开画布 / 切分支 /
  // fork / 中止 / 压缩），引用卡就退化成 "> …" 字面行；而**直发**的图片更彻底：那行清单按
  // Operit「移除链接」口径被去掉、`image` 内容块又只有 data/mimeType（协议无文件名字段）
  // → pi 会话文件里连名字都查不到。这条命令把这些元数据另存一份结构化副本：
  //
  //   /pient-meta <base64(JSON)>  →  pi.appendEntry("pient_meta", { v, quote?, attachments?, at })
  //   JSON 形态：{ quote: {text, role}, attachments: [{name, kind, path}] }
  //
  // 追加在当前叶之下，**紧随其后 prompt 追加的用户消息就是它的子条目**；App 侧按
  // 「user 条目的 parent 是不是 pient_meta」把引用卡与附件名挂回去（引用另按文本形态校验，防错挂）。
  // custom 条目不进 LLM 上下文、也不进 Pient 画布（画布只认 type=message）。
  // 参数走 base64：引用原文可能多行、含引号，而命令参数 = 「第一个空格之后的整段」——
  // 单行 base64 是唯一不会踩到解析的形态。
  pi.registerCommand("pient-meta", {
    description: "Pient: 给紧随其后的用户消息挂一条元数据（引用 + 附件清单；自定义条目，不进 LLM 上下文）",
    handler: async (args, ctx) => {
      const raw = (args || "").trim();
      if (!raw) {
        ctx.ui.notify("用法：/pient-meta <base64(JSON {quote?, attachments?})>", "error");
        return;
      }
      let payload: {
        quote?: { text?: string; role?: string };
        attachments?: { name?: string; kind?: string; path?: string | null }[];
      };
      try {
        payload = JSON.parse(Buffer.from(raw, "base64").toString("utf8")) as typeof payload;
      } catch (e) {
        ctx.ui.notify(`pient-meta: 参数解析失败（${e instanceof Error ? e.message : String(e)}）`, "error");
        return;
      }
      const quoteText = (payload.quote?.text ?? "").trim();
      const attachments = (payload.attachments ?? [])
        .map((a) => ({
          name: a?.name ?? "",
          kind: a?.kind === "IMAGE" || a?.kind === "URL" ? a.kind : "FILE",
          path: a?.path ?? null,
        }))
        .filter((a) => a.name !== "" || a.path !== null);
      if (quoteText === "" && attachments.length === 0) {
        ctx.ui.notify("pient-meta: 没有可写的内容，未写标记", "warning");
        return;
      }
      if (!ctx.isIdle()) {
        // 标记必须落在「本轮用户消息」之前（appendEntry 写在当前叶之下）；流式中写会挪动活跃叶 ——
        // 宁可不写：App 侧认不出标记就当普通文本 / 按 image 块推断处理，不会有副作用
        ctx.ui.notify("pient-meta: agent 正在运行，本次不写元数据标记", "warning");
        return;
      }
      pi.appendEntry(META_TYPE, {
        v: 2,
        ...(quoteText !== "" ? { quote: { text: quoteText, role: payload.quote?.role ?? "user" } } : {}),
        ...(attachments.length > 0 ? { attachments } : {}),
        at: Date.now(),
      });
    },
  });

  // ── Android 系统 shell（Pient 要求 5）───────────────────────────────────────
  // 只要注册就一定会出现在模型可见的工具表里；不可用时**如实报错**（而不是静默失败或装作做过）。
  pi.registerTool({
    name: "android_shell",
    label: "Android Shell",
    description:
      "在 Android **系统 shell** 里执行一条命令（以 shell / root 身份）。用于 Ubuntu(PRoot) 做不到的系统级操作：" +
      "`pm`（安装/卸载/查询应用）、`am`（启动 Activity/服务、发送广播）、`cmd`、`dumpsys`、`settings`、" +
      "`getprop`/`setprop`、`svc`（网络/电源）、`input`（模拟点击输入）等；Root 档下还能读改 `/data/data` 下其它应用的私有数据、" +
      "直接改系统文件。命令经 `sh -c` 执行，`;`、`&&`、管道、重定向均可。\n" +
      "这条通道**完全独立于 Ubuntu 终端**：命令不经过 Ubuntu、不经过终端会话，由系统直接执行；" +
      "而且**即发即走** —— 每次调用都是新进程，`cd` / `export` 之类的状态**不跨调用保留**" +
      "（需要先切目录就写在同一句里：`cd /sdcard && ls`）。反过来，Ubuntu 沙盘里的文件、构建、包管理一律用 bash 工具。\n" +
      "需要用户先在 Pient 里开启「调试权限（Shizuku）」或「Root 权限」——标准档下会返回明确的不可用说明。",
    promptSnippet: "在 Android 系统 shell 里执行命令（pm/am/cmd/dumpsys/settings 等；需 Shizuku 或 Root）",
    promptGuidelines: [
      "android_shell 用于操作 Android 系统本身（安装应用、启动组件、改系统设置、读系统属性、模拟输入）；Ubuntu 内的文件与构建操作一律用 bash。",
      "android_shell 需要用户在 Pient 里开启 Shizuku（调试权限）或 Root 权限；工具返回不可用说明时，如实转告用户并给出开启路径，不要改用别的方式硬凑。",
      "android_shell 以 shell 或 root 身份执行，命令会真实作用于用户的设备：先想清楚再执行，破坏性命令（卸载、删除、格式化）务必先向用户确认。",
    ],
    parameters: Type.Object({
      command: Type.String({ description: "要在 Android 系统 shell 里执行的命令（经 sh -c）" }),
      timeout_ms: Type.Optional(
        Type.Number({ description: "超时毫秒数，默认 30000；安装应用等慢操作可加大（上限 600000）" }),
      ),
    }),
    async execute(_toolCallId, params, signal) {
      const cmd = (params.command ?? "").trim();
      if (!cmd) {
        return { content: [{ type: "text", text: "command 为空：android_shell 需要一条要执行的命令。" }], details: {} };
      }
      if (signal?.aborted) {
        return { content: [{ type: "text", text: "已取消（调用被中止）。" }], details: {} };
      }
      const timeoutMs = Math.max(1000, Math.min(600000, Math.floor(params.timeout_ms ?? 30000)));
      const r = await androidExec(cmd, timeoutMs);
      return { content: [{ type: "text", text: formatResult(r, cmd) }], details: { backend: r.backend ?? null } };
    },
  });

  pi.registerCommand("pient-nav", {
    description: "Pient: 会话内分支跳转（把活跃叶移动到指定 entry，等价 TUI 的 /tree 选择）",
    handler: async (args, ctx) => {
      const parts = (args || "").trim().split(/\s+/).filter(Boolean);
      const entryId = parts.shift();
      if (!entryId) {
        ctx.ui.notify("用法：/pient-nav <entryId> [--summarize] [--label <文字>] [--instructions <文字>]", "error");
        return;
      }
      let summarize = false;
      let label: string | undefined;
      let instructions: string | undefined;
      for (let i = 0; i < parts.length; i++) {
        const p = parts[i];
        if (p === "--summarize") summarize = true;
        else if (p === "--label") label = parts[++i];
        else if (p === "--instructions") instructions = parts[++i];
      }

      if (!ctx.isIdle()) {
        // pi 硬约束：流式中禁止导航（`agent-session.ts:3117-3119`）——如实回报，别静默失败
        ctx.ui.notify("pient-nav: agent 正在运行，等它结束后再切分支", "warning");
        return;
      }

      const target = resolveTarget(pi, ctx, entryId);
      if (!target) {
        ctx.ui.notify(`pient-nav: 条目 ${entryId} 不在当前会话里`, "error");
        return;
      }

      const before = ctx.sessionManager.getLeafId();
      const result = await ctx.navigateTree(target, {
        summarize,
        label,
        customInstructions: instructions,
      });
      const after = ctx.sessionManager.getLeafId();
      if (result?.cancelled) {
        ctx.ui.notify(`pient-nav 被取消（仍在 ${after ?? before ?? "?"}）`, "warning");
        return;
      }
      // 通知里带上事实（含"经过锚点标记"这一跳），Pient 侧日志/终端里能直接看到，不靠猜
      const via = target === entryId ? "" : `（经锚点 ${target}）`;
      ctx.ui.notify(
        `pient-nav: ${before ?? "(空)"} → ${after ?? "(空)"}${via}${summarize ? " （含分支摘要）" : ""}`,
        "info",
      );
    },
  });
}

/** 条目是不是"用户消息"（pi 的 TUI 重编辑语义只对它生效） */
function isUserMessageEntry(ctx: { sessionManager: { getEntry(id: string): unknown } }, id: string): boolean {
  const entry = ctx.sessionManager.getEntry(id) as
    | { type?: string; message?: { role?: string } }
    | undefined;
  return entry?.type === "message" && entry.message?.role === "user";
}

/** 该用户消息下已有的锚点标记（幂等复用；没有则 undefined） */
function anchorMarkerOf(
  ctx: { sessionManager: { getEntries(): readonly unknown[] } },
  userEntryId: string,
): string | undefined {
  for (const raw of ctx.sessionManager.getEntries()) {
    const e = raw as { id?: string; type?: string; customType?: string; parentId?: string | null };
    if (e.type === "custom" && e.customType === ANCHOR_TYPE && e.parentId === userEntryId && e.id) {
      return e.id;
    }
  }
  return undefined;
}

/**
 * 把 Pient 的导航目标折算成 pi 能接受的非用户条目：
 * 非用户条目原样返回；用户消息条目 → 锚点标记（没有就补一条）。
 */
function resolveTarget(
  pi: ExtensionAPI,
  ctx: {
    sessionManager: {
      getEntry(id: string): unknown;
      getEntries(): readonly unknown[];
      getLeafId(): string | null;
    };
  },
  entryId: string,
): string | undefined {
  if (!ctx.sessionManager.getEntry(entryId)) return undefined;
  if (!isUserMessageEntry(ctx as never, entryId)) return entryId;

  const existing = anchorMarkerOf(ctx, entryId);
  if (existing) return existing;

  // ① 叶先移到这条用户消息（appendEntry 写在当前叶之下 → 标记才会挂成它的子条目）
  (ctx.sessionManager as unknown as { branch(id: string): void }).branch(entryId);
  // ② 官方扩展 API 追加锚点标记（custom 条目：不进 LLM 上下文、不进 Pient 画布）
  pi.appendEntry(ANCHOR_TYPE, { v: 1, for: entryId, at: Date.now() });
  return anchorMarkerOf(ctx, entryId);
}
