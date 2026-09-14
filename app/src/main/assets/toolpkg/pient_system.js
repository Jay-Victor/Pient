/* METADATA
{
  "name": "pient_system",
  "display_name": "系统命令（Android shell）",
  "description": "特权后门：借 Shizuku（ADB 级）或 Root 身份直接对 Android 系统下令；标准权限下以应用身份跑。执行体在 Kotlin 侧（tools/system/SystemPart.kt）。",
  "category": "System",
  "layer": "system",
  "enabledByDefault": true,
  "tools": [
    {
      "name": "shell",
      "label": "系统命令（Android）",
      "description": "Run an Android system command (am / pm / cmd / dumpsys / getprop / settings ...) through Pient's privileged channel (Shizuku ADB-level or root, falling back to the app's own uid). Use this for Android system operations; use `terminal` for GNU/Linux work inside Ubuntu.",
      "parameters": {
        "type": "object",
        "properties": {
          "command": { "type": "string", "description": "Android system command to run, e.g. 'dumpsys battery' or 'pm list packages'" },
          "timeoutMs": { "type": "number", "description": "Timeout in milliseconds (default 120000)" }
        },
        "required": ["command"]
      }
    }
  ]
}*/

// 执行体：tools/system/SystemPart.kt（su → Shizuku 用户服务 → 标准三通道，唯一实现）
export const PIENT_PACKAGE_KIND = "declaration-only";
