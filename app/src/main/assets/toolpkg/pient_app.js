/* METADATA
{
  "name": "pient_app",
  "display_name": "自身工具（原生文件）",
  "description": "App 的手脚：原生直读文件，不走 shell。read/write/edit/grep/find/ls 六个工具的执行体在 Kotlin 侧（tools/files/FilesPart.kt）。",
  "category": "App",
  "layer": "app",
  "enabledByDefault": true,
  "tools": [
    {
      "name": "read",
      "label": "读取文件",
      "description": "Read the contents of a file. Supports text files. Output is truncated to 2000 lines or 50KB (whichever is hit first). Use offset/limit for large files.",
      "parameters": {
        "type": "object",
        "properties": {
          "path": { "type": "string", "description": "Path to the file to read (relative or absolute)" },
          "offset": { "type": "number", "description": "Line number to start reading from (1-indexed)" },
          "limit": { "type": "number", "description": "Maximum number of lines to read" }
        },
        "required": ["path"]
      }
    },
    {
      "name": "write",
      "label": "写入文件",
      "description": "Create or overwrite a file with the given content. Creates parent directories as needed. Use write only for new files or complete rewrites.",
      "parameters": {
        "type": "object",
        "properties": {
          "path": { "type": "string", "description": "Path to the file to write (relative or absolute)" },
          "content": { "type": "string", "description": "Content to write to the file" }
        },
        "required": ["path", "content"]
      }
    },
    {
      "name": "edit",
      "label": "编辑文件",
      "description": "Apply targeted replacements to a file. Each oldText must be unique in the original file and must not overlap with other edits in the same call.",
      "parameters": {
        "type": "object",
        "properties": {
          "path": { "type": "string", "description": "Path to the file to edit (relative or absolute)" },
          "oldText": { "type": "string", "description": "Exact text for one targeted replacement (must be unique in the file)" },
          "newText": { "type": "string", "description": "Replacement text for this targeted edit" },
          "edits": {
            "type": "array",
            "description": "One or more targeted replacements (alternative to oldText/newText). Each edit is matched against the original file, not incrementally.",
            "items": {
              "type": "object",
              "properties": {
                "oldText": { "type": "string", "description": "Exact text to replace" },
                "newText": { "type": "string", "description": "Replacement text" }
              },
              "required": ["oldText", "newText"]
            }
          }
        },
        "required": ["path"]
      }
    },
    {
      "name": "grep",
      "label": "内容搜索",
      "description": "Search file contents for a pattern. Returns matching lines with file paths and line numbers. Output is truncated to 100 matches or 50KB (whichever is hit first).",
      "parameters": {
        "type": "object",
        "properties": {
          "pattern": { "type": "string", "description": "Search pattern (regex or literal string)" },
          "path": { "type": "string", "description": "Directory or file to search (default: current directory)" },
          "glob": { "type": "string", "description": "Filter files by glob pattern (supports * and **), e.g. '*.md'" },
          "ignoreCase": { "type": "boolean", "description": "Case-insensitive search (default: false)" },
          "literal": { "type": "boolean", "description": "Treat pattern as literal string instead of regex (default: false)" },
          "context": { "type": "number", "description": "Number of lines to show before and after each match (default: 0)" },
          "limit": { "type": "number", "description": "Maximum number of matches to return (default: 100)" }
        },
        "required": ["pattern"]
      }
    },
    {
      "name": "find",
      "label": "按名找文件",
      "description": "Search for files by glob pattern. Returns matching file paths relative to the search directory. Output is truncated to 1000 results or 50KB (whichever is hit first).",
      "parameters": {
        "type": "object",
        "properties": {
          "pattern": { "type": "string", "description": "Glob pattern to match files (supports * and **), e.g. '*.md'" },
          "path": { "type": "string", "description": "Directory to search in (default: current directory)" },
          "limit": { "type": "number", "description": "Maximum number of results (default: 1000)" }
        },
        "required": ["pattern"]
      }
    },
    {
      "name": "ls",
      "label": "列目录",
      "description": "List directory contents. Returns entries sorted alphabetically, with a slash suffix for directories. Includes dotfiles. Output is truncated to 500 entries.",
      "parameters": {
        "type": "object",
        "properties": {
          "path": { "type": "string", "description": "Directory to list (default: current directory)" },
          "limit": { "type": "number", "description": "Maximum number of entries to return (default: 500)" }
        }
      }
    }
  ]
}*/

// 内置包只有「声明」：执行由 Kotlin 四层按工具名绑定（tools/files/FilesPart.kt）。
// 注意：METADATA 块里**不能出现连续的星号加斜杠**（会提前结束注释块）——写 glob 例子时用 `* and **` 这种说法。
export const PIENT_PACKAGE_KIND = "declaration-only";
