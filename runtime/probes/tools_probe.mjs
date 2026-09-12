// Pient 工具层探针：直接调用 pi 官方七工具（不经模型），在目标目录上跑一轮真实读写。
// 用法：PI_PKG_DIR=<含 node_modules 的目录> node tools_probe.mjs <workdir>
// 设备侧由 scripts/deploy_dev.sh 拉起（PATH 需含 rg/fd，LD_LIBRARY_PATH 需含 Node 依赖库）。
import { pathToFileURL } from "node:url";
import path from "node:path";

const pkgDir = process.env.PI_PKG_DIR;
if (!pkgDir) {
  console.error("缺少 PI_PKG_DIR（指向含 node_modules 的目录）");
  process.exit(2);
}
const entry = path.join(pkgDir, "node_modules", "@earendil-works", "pi-coding-agent", "dist", "index.js");
const { createCodingTools, createReadOnlyTools } = await import(pathToFileURL(entry).href);

const cwd = process.argv[2] ?? process.cwd();
const tools = [...createReadOnlyTools(cwd), ...createCodingTools(cwd)];
const byName = new Map();
for (const t of tools) if (!byName.has(t.name)) byName.set(t.name, t);

console.log(`PID=${process.pid} NODE=${process.version} PLATFORM=${process.platform} ARCH=${process.arch}`);
console.log(`CWD=${cwd}`);
console.log(`TOOLS=${[...byName.keys()].join(",")}`);

let ok = 0, fail = 0;
async function run(name, args) {
  const tool = byName.get(name);
  if (!tool) {
    fail++;
    return console.log(`[${name}] MISSING`);
  }
  const t0 = Date.now();
  try {
    const r = await tool.execute("probe-call", args, undefined, undefined, undefined);
    const text = (r?.content ?? []).filter((c) => c.type === "text").map((c) => c.text).join(" | ");
    ok++;
    console.log(`[${name}] ok ${Date.now() - t0}ms :: ${text.replace(/\n/g, "\\n").slice(0, 240)}`);
  } catch (e) {
    fail++;
    console.log(`[${name}] ERR ${Date.now() - t0}ms :: ${String(e?.message ?? e).slice(0, 240)}`);
  }
}

await run("ls", { path: "." });
await run("write", { path: "hello.txt", content: "line1\nline2\nneedle-here\n" });
await run("read", { path: "hello.txt" });
await run("edit", { path: "hello.txt", edits: [{ oldText: "line1", newText: "LINE-ONE" }] });
await run("read", { path: "hello.txt" });
await run("grep", { pattern: "LINE-ONE" });
await run("find", { pattern: "*.txt" });
await run("bash", { command: "echo bash-tool-ok; uname -srm; pwd" });

console.log(`SUMMARY ok=${ok} fail=${fail}`);
process.exit(fail === 0 ? 0 : 1);
