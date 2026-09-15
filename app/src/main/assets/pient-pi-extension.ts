/**
 * Pient ↔ pi 的桥接扩展（随 APK 预置，安装到 guest 的 `~/.pi/agent/extensions/pient.ts`）。
 *
 * 为什么需要它：pi 的官方 RPC 暴露了会话/树/分叉的**大部分**能力
 * （`get_tree` / `get_entries` / `get_fork_messages` / `fork` / `clone` / `new_session` /
 * `switch_session` / `set_session_name` / `get_session_stats`），
 * 但**没有"把活跃叶移到树里另一个节点"的命令** —— 那正是 Pi TUI 里 `/tree` 干的事，
 * 只做成内部 API（`ctx.navigateTree`）暴露给扩展。
 *
 * Pient 的「会话内分支」（分支节点画布页上点某条历史消息继续）等于 pi 的树导航，
 * 所以这里注册一条命令补上这个缺口：
 *
 *   /pient-nav <entryId> [--summarize] [--label <文字>] [--instructions <文字>]
 *
 * pi 的 RPC 说明里写明扩展命令属于 `get_commands` 并且 **"available for invocation via prompt"**，
 * 因此 Pient 侧只要 `{"type":"prompt","message":"/pient-nav <id>"}` 即可触发。
 *
 * ── 用户消息条目：锚点标记（2026-09-15）────────────────────────────────────────────
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
 */

import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";
import { homedir } from "node:os";
import { join } from "node:path";
import { mkdirSync, writeFileSync } from "node:fs";

/** 锚点标记的 customType（Pient 与 pi 都视其为元数据） */
const ANCHOR_TYPE = "pient_anchor";

/** 系统提示词回流文件（Pient 的「系统提示词」面板读它；App 侧不再持有提示词） */
const SYSPROMPT_FILE = ".pient-sysprompt.txt";

export default function (pi: ExtensionAPI) {
  // ── 系统提示词回流（2026-09-15）──────────────────────────────────────────────
  // 提示词的持有者是 pi（base prompt + 项目 context 文件 + 扩展改写），App 看不到。
  // Pient 的「系统提示词」只读面板打开时触发这条命令 → 把**真实下发的那一份**写到
  // `~/.pi/agent/.pient-sysprompt.txt`（App 侧从 rootfs 里读回）。
  pi.registerCommand("pient-sysprompt", {
    description: "Pient: 把当前生效的系统提示词写到 ~/.pi/agent/.pient-sysprompt.txt（供 App 只读展示）",
    handler: async (_args, ctx) => {
      const text = ctx.getSystemPrompt();
      try {
        const dir = join(homedir(), ".pi", "agent");
        mkdirSync(dir, { recursive: true });
        writeFileSync(join(dir, SYSPROMPT_FILE), text, "utf8");
        ctx.ui.notify(`pient-sysprompt: 已写出系统提示词（${text.length} 字）`, "info");
      } catch (e) {
        ctx.ui.notify(`pient-sysprompt 写文件失败：${e instanceof Error ? e.message : String(e)}`, "error");
      }
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
