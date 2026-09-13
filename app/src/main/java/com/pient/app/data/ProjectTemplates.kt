package com.pient.app.data

import org.json.JSONObject
import java.io.File

/**
 * 项目类型模板（2026-09-14 对齐 Operit `WorkspaceSetup` 的项目类型）。
 *
 * Operit 在「在应用内创建新工作区」时按类型复制模板并写项目配置
 * （`createAndGetDefaultWorkspace` 的 when 分支 + `copyTemplateFiles` + `createProjectConfigIfNeeded`），
 * 类型有 android / flutter / node / typescript / python / java / go / office / blank / web。
 *
 * Pient 先落地 7 种**能给出真实可用内容**的模板；android / flutter 需要真脚手架（SDK / `flutter create`），
 * office 需要二进制文档模板 —— 造个空壳文件名不算模板，宁可先不给（要的话下一轮用 Ubuntu 里的真工具链生成）。
 */
enum class ProjectType(val id: String, val title: String, val desc: String) {
    BLANK("blank", "空白", "只建目录与项目配置"),
    NODE("node", "Node.js", "package.json + index.js"),
    TYPESCRIPT("typescript", "TypeScript", "tsconfig + src/index.ts"),
    PYTHON("python", "Python", "main.py + requirements.txt"),
    WEB("web", "Web", "index.html + style.css + app.js"),
    JAVA("java", "Java", "src/Main.java（javac 可直接编）"),
    GO("go", "Go", "go.mod + main.go");

    companion object {
        fun fromId(id: String?): ProjectType = entries.firstOrNull { it.id == id } ?: BLANK
    }
}

object ProjectTemplates {

    /** 项目配置文件名（对齐 Operit 的 createProjectConfigIfNeeded） */
    const val CONFIG_FILE = ".pient-project.json"

    /**
     * 在 [dir] 下物化模板，返回新写入的文件数（已存在的文件**不覆盖**——重置/重绑时别毁用户改动）。
     * 调用方须在 IO 线程执行。
     */
    fun create(dir: File, type: ProjectType, name: String): Int {
        if (!dir.exists() && !dir.mkdirs()) return 0
        var written = 0
        files(type, name).forEach { (rel, content) ->
            val f = File(dir, rel)
            if (f.exists()) return@forEach
            f.parentFile?.mkdirs()
            runCatching { f.writeText(content) }.onSuccess { written++ }
        }
        val cfg = File(dir, CONFIG_FILE)
        if (!cfg.exists()) {
            val json = JSONObject()
                .put("name", name)
                .put("type", type.id)
                .put("template", "pient/${type.id}")
                .put("createdAt", System.currentTimeMillis())
            runCatching { cfg.writeText(json.toString(2) + "\n") }.onSuccess { written++ }
        }
        return written
    }

    /** 模板内容：相对路径 → 文本。`$` 在 Kotlin 原始串里会插值，故模板里一律避开 shell 变量写法。 */
    fun files(type: ProjectType, name: String): Map<String, String> = when (type) {
        ProjectType.BLANK -> emptyMap()

        ProjectType.NODE -> mapOf(
            "package.json" to """
                {
                  "name": "${name.toPackageName()}",
                  "version": "1.0.0",
                  "type": "module",
                  "scripts": { "start": "node index.js" }
                }
            """.trimIndent() + "\n",
            "index.js" to """
                // 入口：node index.js
                const args = process.argv.slice(2);
                console.log("hello from " + "${name}" + (args.length ? " args=" + args.join(",") : ""));
            """.trimIndent() + "\n",
            "README.md" to readme(name, "Node.js", "node index.js"),
        )

        ProjectType.TYPESCRIPT -> mapOf(
            "package.json" to """
                {
                  "name": "${name.toPackageName()}",
                  "version": "1.0.0",
                  "type": "module",
                  "scripts": {
                    "build": "tsc",
                    "start": "node dist/index.js"
                  },
                  "devDependencies": { "typescript": "^5.6.0" }
                }
            """.trimIndent() + "\n",
            "tsconfig.json" to """
                {
                  "compilerOptions": {
                    "target": "ES2022",
                    "module": "ES2022",
                    "moduleResolution": "bundler",
                    "strict": true,
                    "outDir": "dist",
                    "rootDir": "src"
                  },
                  "include": ["src"]
                }
            """.trimIndent() + "\n",
            "src/index.ts" to """
                export function greet(who: string): string {
                  return "hello, " + who;
                }

                console.log(greet("${name}"));
            """.trimIndent() + "\n",
            "README.md" to readme(name, "TypeScript", "npm install && npm run build && npm start"),
        )

        ProjectType.PYTHON -> mapOf(
            "main.py" to """
                """ + "\"\"\"" + """
                ${name}：入口脚本（python3 main.py [参数…]）
                """ + "\"\"\"" + """

                import sys


                def main(argv: list[str]) -> int:
                    print("hello from ${name}", argv)
                    return 0


                if __name__ == "__main__":
                    raise SystemExit(main(sys.argv[1:]))
            """.trimIndent() + "\n",
            "requirements.txt" to "# 依赖（pip install -r requirements.txt）\n",
            "README.md" to readme(name, "Python", "python3 main.py"),
        )

        ProjectType.WEB -> mapOf(
            "index.html" to """
                <!doctype html>
                <html lang="zh-CN">
                  <head>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1" />
                    <title>${name}</title>
                    <link rel="stylesheet" href="style.css" />
                  </head>
                  <body>
                    <h1>${name}</h1>
                    <p id="out">hello</p>
                    <script src="app.js"></script>
                  </body>
                </html>
            """.trimIndent() + "\n",
            "style.css" to "body { font-family: system-ui, sans-serif; margin: 24px; }\n",
            "app.js" to "document.getElementById('out').textContent = 'hello from ${name}';\n",
            "README.md" to readme(name, "静态 Web", "起个静态服务器：python3 -m http.server 8080"),
        )

        ProjectType.JAVA -> mapOf(
            "src/Main.java" to """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("hello from ${name}");
                    }
                }
            """.trimIndent() + "\n",
            "README.md" to readme(name, "Java", "javac -d out src/Main.java && java -cp out Main"),
        )

        ProjectType.GO -> mapOf(
            "go.mod" to "module ${name.toPackageName()}\n\ngo 1.22\n",
            "main.go" to """
                package main

                import "fmt"

                func main() {
                	fmt.Println("hello from ${name}")
                }
            """.trimIndent() + "\n",
            "README.md" to readme(name, "Go", "go run ."),
        )
    }

    private fun readme(name: String, kind: String, run: String): String = """
        # ${name}

        ${kind} 项目（由 Pient 模板创建）。

        ## 运行

        ```
        ${run}
        ```
    """.trimIndent() + "\n"

    /** 目录名 → 合法的包/模块名（node/type 项目用） */
    private fun String.toPackageName(): String =
        lowercase().replace(Regex("[^a-z0-9._-]"), "-").trim('-').ifBlank { "app" }
}
