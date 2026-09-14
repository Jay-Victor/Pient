/* METADATA
{
  "name": "pient_terminal",
  "display_name": "终端（Ubuntu）",
  "description": "Ubuntu 沙盘（PRoot）：真 GNU bash、apt、Node、Python 都在这一层。执行体在 Kotlin 侧（tools/terminal/TerminalPart.kt），会话引擎与终端页共用（tools/terminal/TerminalSessions.kt）。",
  "category": "Terminal",
  "layer": "terminal",
  "enabledByDefault": true,
  "tools": [
    {
      "name": "terminal",
      "label": "执行命令（Ubuntu）",
      "description": "Execute a bash command in the workspace (Ubuntu). Returns stdout and stderr, truncated to the last 2000 lines or 50KB. Optionally provide a timeout in seconds.",
      "parameters": {
        "type": "object",
        "properties": {
          "command": { "type": "string", "description": "Shell command to execute" },
          "timeout": { "type": "number", "description": "Timeout in seconds (optional, default 120)" }
        },
        "required": ["command"]
      }
    }
  ]
}*/

// 执行体：tools/terminal/TerminalPart.kt（工作区 = 当前项目，cwd 落在 guest 的 /workspace）
export const PIENT_PACKAGE_KIND = "declaration-only";
