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
 * 注意：本文件用 **jiti** 直接加载（pi 的扩展加载器），不需要编译。
 */

import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";

export default function (pi: ExtensionAPI) {
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

      const before = ctx.sessionManager.getLeafId();
      const result = await ctx.navigateTree(entryId, {
        summarize,
        label,
        customInstructions: instructions,
      });
      const after = ctx.sessionManager.getLeafId();
      if (result?.cancelled) {
        ctx.ui.notify(`pient-nav 被取消（仍在 ${after ?? before ?? "?"}）`, "warning");
        return;
      }
      // 通知里带上事实，Pient 侧日志/终端里能直接看到（不靠猜）
      ctx.ui.notify(`pient-nav: ${before ?? "(空)"} → ${after ?? "(空)"}${summarize ? " （含分支摘要）" : ""}`, "info");
    },
  });
}
