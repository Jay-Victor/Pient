/**
 * Pient 权限守门扩展（Pient 内置，随 App 分发 → 宿主 HOME 的 `~/.pi/agent/extensions/`）。
 *
 * 机制全部是 pi 官方能力（开发计划 §6.3 指定的做法）：
 * - `tool_call` 事件：工具执行前触发，**可 block**（返回 `{ block: true, reason }`）；
 * - 扩展 UI 子协议：RPC 模式下 `ctx.ui.select()` 会发一条 `extension_ui_request` 到 stdout 并阻塞，
 *   客户端（Pient）回 `extension_ui_response` 才继续。
 *
 * 策略文件 `~/.pi/agent/pient_gate.json`（**App 侧是唯一写者**，本扩展每次调用现读，故改了立即生效）：
 *   { "default": "ASK", "tools": { "read": "ALLOW", "write": "FORBID" } }
 *
 * 询问标题里带机器可读载荷（Pient 侧解析）：`PientGate|<工具名>|<参数 JSON 单行>|<高危 0|1>`；
 * 三个选项的文案是**跨语言契约**（Kotlin 侧 ToolPolicy.OPT_* 必须逐字一致）。
 */
import * as fs from "node:fs";
import * as os from "node:os";
import * as path from "node:path";

const POLICY_FILE = path.join(os.homedir(), ".pi", "agent", "pient_gate.json");

const OPT_ONCE = "仅本次允许";
const OPT_ALWAYS = "始终允许";
const OPT_DENY = "拒绝";

/** 高危命令叠加拦截（即便策略是 ALLOW 也要二次确认）：破坏性/提权/刷机类 */
const HIGH_RISK = [
  /\brm\s+-[a-z]*r[a-z]*f/i,
  /\brm\s+-[a-z]*f[a-z]*r/i,
  /\bsudo\b/i,
  /\bsu\s+-c\b/i,
  /\bmkfs(\s|\.)/i,
  /\bdd\s+if=/i,
  /\bchmod\s+-R\s+777\b/i,
  /\b(reboot|poweroff|halt)\b/i,
  /:\s*\(\s*\)\s*\{/,
];

interface GatePolicy {
  default: string;
  tools: Record<string, string>;
}

function readPolicy(): GatePolicy {
  try {
    const raw = JSON.parse(fs.readFileSync(POLICY_FILE, "utf8"));
    return { default: raw?.default ?? "ASK", tools: raw?.tools ?? {} };
  } catch {
    return { default: "ASK", tools: {} };
  }
}

function policyFor(tool: string): string {
  const p = readPolicy();
  const v = p.tools[tool] ?? p.default;
  return v === "ALLOW" || v === "FORBID" ? v : "ASK";
}

function isHighRisk(tool: string, input: any): boolean {
  if (tool !== "bash") return false;
  const cmd = String(input?.command ?? input?.cmd ?? "");
  return HIGH_RISK.some((re) => re.test(cmd));
}

export default function (pi: any) {
  pi.on("tool_call", async (event: any, ctx: any) => {
    const tool = String(event.toolName ?? "");
    const policy = policyFor(tool);
    const high = isHighRisk(tool, event.input);

    if (policy === "FORBID") {
      return { block: true, reason: `Pient 权限策略禁止调用 ${tool}` };
    }
    if (policy === "ALLOW" && !high) return;

    let args = "{}";
    try {
      args = JSON.stringify(event.input ?? {});
    } catch {
      args = "{}";
    }
    if (args.length > 400) args = args.slice(0, 400) + "…";

    const choice = await ctx.ui.select(
      `PientGate|${tool}|${args}|${high ? 1 : 0}`,
      [OPT_ONCE, OPT_ALWAYS, OPT_DENY],
      { timeout: 300000 },
    );
    if (choice === OPT_DENY || choice === undefined) {
      return { block: true, reason: `用户拒绝执行 ${tool}` };
    }
    return;
  });
}
