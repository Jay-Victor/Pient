/**
 * Pient 系统命令扩展（Pient 内置，随 App 分发 → 宿主 HOME 的 `~/.pi/agent/extensions/`）。
 *
 * 作用：给模型一个跑 **Android 系统命令**（am / pm / dumpsys / getprop / settings / cmd …）的工具。
 * 为什么不让 `bash` 工具直接跑这些：pi 的 bash 工具经 `spawn(shellPath, …)` 执行，而 Android 系统命令要的
 * 权限通道（Shizuku 的 `IShizukuService.newProcess`、`su`）只能在 App 的 Java/binder 侧调用——shell 链路到不了。
 * 所以走「扩展工具 → 本机回桥（PiExecServer）→ Java 侧执行」这条官方扩展通道（开发计划 §6）。
 *
 * 端点由 App 通过环境变量 `PIENT_EXEC_ENDPOINT` 注入（`PiRuntime.environment`）。
 * 未解锁 Shizuku / Root 时，回桥返回 `no-privilege`，本工具如实把原因作为工具结果回给模型。
 */
const ENDPOINT = process.env.PIENT_EXEC_ENDPOINT;

const PARAMS = {
  type: "object",
  properties: {
    command: {
      type: "string",
      description:
        "Android system command to run, e.g. 'am start -a android.intent.action.VIEW -d https://…', 'dumpsys battery', 'getprop ro.build.version.release', 'pm list packages | head'.",
    },
    timeoutMs: { type: "number", description: "Timeout in milliseconds (default 120000)." },
  },
  required: ["command"],
};

export default function (pi) {
  pi.registerTool({
    name: "android_shell",
    label: "Android shell",
    description:
      "Run an Android system command (am / pm / dumpsys / getprop / settings / cmd …) through Pient's privileged channel " +
      "(Shizuku ADB-level or root). Use this for Android system operations; use the bash tool for GNU/Linux work inside Ubuntu.",
    promptSnippet: "Run Android system commands (am / pm / dumpsys / getprop …) through the privileged channel (Shizuku / root)",
    promptGuidelines: [
      "Use android_shell for Android system operations (activities, packages, properties, settings, dumpsys);",
      "Use bash for GNU/Linux work inside the Ubuntu environment instead.",
    ],
    parameters: PARAMS,
    async execute(_toolCallId, params) {
      if (!ENDPOINT) {
        return {
          content: [{ type: "text", text: "系统命令通道未就绪：宿主回桥未启动（重启应用后重试）" }],
          details: { error: "endpoint-missing" },
        };
      }
      let data;
      try {
        const res = await fetch(`${ENDPOINT}/exec`, {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify({ command: params.command, timeoutMs: params.timeoutMs }),
        });
        data = await res.json();
      } catch (e) {
        return {
          content: [{ type: "text", text: `系统命令通道调用失败：${e?.message ?? e}` }],
          details: { error: "bridge-failed" },
        };
      }
      if (data?.error) {
        return {
          content: [{ type: "text", text: `[${data.error}] ${data.message ?? ""}`.trim() }],
          details: { error: data.error, channel: data.channel },
        };
      }
      const body = typeof data.output === "string" && data.output.length > 0 ? data.output : "(无输出)";
      // 通道要写进结果：同一句命令在不同档下能力天差地别（标准=应用身份、shizuku=uid 2000、su=uid 0），
      // 模型看到 [standard] 里的权限报错时才知道该建议用户去升级档位，而不是以为自己写错了命令。
      const tier = typeof data.channel === "string" ? data.channel : "?";
      return {
        content: [{ type: "text", text: `$ ${params.command}\n[${tier}] ${body}` }],
        details: { channel: data.channel, code: data.code },
      };
    },
  });
}
