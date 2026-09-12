// agent 闭环探针：启动 pi 官方 RPC 宿主，发一条 prompt，把 agent 事件（工具调用/文本增量）逐条打出来。
// 用途：验证「模型 → tool_call → 宿主执行工具 → 结果回灌 → 最终回答」整条链路（不经 App UI）。
//
// 用法：PIENT_RT=<运行时根> node agent_loop_probe.mjs "<prompt>" [provider/model]
//   例：PIENT_RT=/data/local/tmp/pient-runtime node agent_loop_probe.mjs \
//       "读一下当前目录的 hello.txt 并复述内容：[[tool:read {\"path\":\"hello.txt\"}]]"
//
// 前置：
//   - 运行时根下 usr/bin/node 与 app/node_modules（fetch_runtime.py + deploy_dev.sh 部署）
//   - ~/.pi/agent/models.json 指向可达的模型端点（deploy_dev.sh 会写 mock 配置）
//   - mock 侧要能回工具调用：用户消息里写 `[[tool:名字 {"参数":"值"}]]`（见 mock_llm.py 文档头）
import { spawn } from "node:child_process";
import { readFileSync } from "node:fs";

const rt = process.env.PIENT_RT ?? "/data/local/tmp/pient-runtime";
const entry = `${rt}/app/node_modules/@earendil-works/pi-coding-agent/dist/bundle/rpc-entry.js`;
// prompt 支持 `@文件` 形式（避免经 adb shell 传含 JSON/引号的 prompt 时的多层引号地狱）
const rawPrompt = process.argv[2] ?? "读一下当前目录的 hello.txt 并复述内容";
const prompt = rawPrompt.startsWith("@") ? readFileSync(rawPrompt.slice(1), "utf8").trim() : rawPrompt;
const target = process.argv[3] ?? "mock/deepseek-chat";
const slash = target.indexOf("/");
const provider = slash > 0 ? target.slice(0, slash) : target;
const model = slash > 0 ? target.slice(slash + 1) : target;
const timeoutMs = Number(process.env.PROBE_TIMEOUT_MS ?? 90_000);

console.log(`probe: node=${process.execPath} entry=${entry}`);
console.log(`probe: provider=${provider} model=${model}`);

const child = spawn(
  process.execPath,
  [entry, "--mode", "rpc", "--no-session", "--provider", provider, "--model", `${provider}/${model}`],
  { cwd: process.cwd(), env: process.env, stdio: ["pipe", "pipe", "pipe"] },
);

let buf = "";
let text = "";
let toolCalls = 0;
let toolResults = 0;
let done = false;
const seen = new Map();

function send(obj) {
  child.stdin.write(JSON.stringify(obj) + "\n");
}

function handleEvent(ev) {
  const type = ev.type ?? "?";
  seen.set(type, (seen.get(type) ?? 0) + 1);
  if (type === "tool_execution_start") {
    toolCalls++;
    console.log(`[event] tool_execution_start tool=${ev.toolName} args=${JSON.stringify(ev.args ?? ev.input ?? {})}`);
  } else if (type === "tool_execution_end") {
    toolResults++;
    const out = JSON.stringify(ev).slice(0, 400);
    console.log(`[event] tool_execution_end ${out}`);
  } else if (type === "tool_execution_update") {
    // 流式输出更新：只记数量，避免刷屏
  } else if (type === "message_end") {
    // message_end 带完整消息体：取其中的文本块当「助手最终回答」（message_update 的增量形状随协议而异）
    const msg = ev.message ?? ev;
    const content = msg?.content;
    const blocks = Array.isArray(content) ? content : [];
    const joined = blocks.filter((b) => b?.type === "text").map((b) => b.text).join("");
    if (joined) text = joined;
    console.log(`[event] message_end role=${msg?.role ?? "?"} text=${JSON.stringify(joined.slice(0, 200))}`);
  } else if (type === "message_update") {
    const d = ev.delta ?? {};
    const piece = d?.text ?? d?.content ?? "";
    if (typeof piece === "string" && piece) text += piece;
  } else {
    console.log(`[event] ${type}`);
  }
  if (type === "prompt_done" || type === "agent_end") {
    finish();
  }
}

function handleLine(line) {
  if (process.env.PROBE_VERBOSE === "1") console.log(`[raw] ${line.slice(0, 400)}`);
  let ev;
  try {
    ev = JSON.parse(line);
  } catch {
    console.log(`[raw] ${line.slice(0, 200)}`);
    return;
  }
  if (ev.type === "response") {
    const data = ev.data ? JSON.stringify(ev.data).slice(0, 300) : "";
    console.log(`[response] ${ev.command} success=${ev.success} ${data}`);
    if (ev.command === "prompt" && ev.success) {
      // prompt 已受理，事件会继续流式到来
    }
    return;
  }
  handleEvent(ev);
}

function finish() {
  if (done) return;
  done = true;
  console.log("---- SUMMARY ----");
  console.log(`tool_calls=${toolCalls} tool_results=${toolResults}`);
  console.log(`assistant_text=${JSON.stringify(text.slice(0, 600))}`);
  console.log(`events=${JSON.stringify(Object.fromEntries([...seen.entries()].sort()))}`);
  child.kill();
  process.exit(toolCalls > 0 && toolResults > 0 ? 0 : 1);
}

child.stdout.on("data", (d) => {
  buf += d.toString("utf8");
  let i;
  while ((i = buf.indexOf("\n")) >= 0) {
    const line = buf.slice(0, i).replace(/\r$/, "");
    buf = buf.slice(i + 1);
    if (line.trim()) handleLine(line);
  }
});
child.stderr.on("data", (d) => {
  const s = d.toString("utf8").trim();
  if (s) console.log(`[stderr] ${s.slice(0, 300)}`);
});

setTimeout(() => {
  send({ id: "req-1", type: "prompt", message: prompt });
}, 1500);

setTimeout(() => {
  if (!done) {
    console.log("!! 超时未收到 prompt_done");
    finish();
  }
}, timeoutMs);
