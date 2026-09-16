package com.pient.app.data

import com.pient.app.data.i18n.L
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
enum class ProjectType(val id: String) {
    BLANK("blank"),
    NODE("node"),
    TYPESCRIPT("typescript"),
    PYTHON("python"),
    WEB("web"),
    JAVA("java"),
    GO("go");

    /**
     * 显示名 / 说明。**必须是计算属性**：枚举构造参数只在类加载时求值一次，
     * 直接写 `L.…` 会把文案冻结成首帧语言（切语言不刷新）。
     */
    val title: String get() = when (this) {
        BLANK -> L.project.typeBlank
        NODE -> "Node.js"
        TYPESCRIPT -> "TypeScript"
        PYTHON -> "Python"
        WEB -> "Web"
        JAVA -> "Java"
        GO -> "Go"
    }

    val desc: String get() = when (this) {
        BLANK -> L.project.typeBlankDesc
        NODE -> "package.json + index.js"
        TYPESCRIPT -> "tsconfig + src/index.ts"
        PYTHON -> "main.py + requirements.txt"
        WEB -> "index.html + style.css + app.js"
        JAVA -> L.project.typeJavaDesc
        GO -> "go.mod + main.go"
    }

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
        if (writeConfigIfNeeded(dir, name, type)) written++
        return written
    }

    /**
     * 写回项目标记文件（`.pient-project.json`；**已存在则不覆盖**）。返回是否写入。
     *
     * 单独抽出来（2026-09-16）：**重置工作区会把项目根目录清空、连标记一起删掉**，而参考实现里
     * 那个函数的语义是 `createProjectConfigIfNeeded` —— 重置 = 清内容，不代表这个目录不再是
     * Pient 项目（标记一丢，文件树/项目管理里就少了模板信息）。「新建项目」与「重置后补写」
     * 共用这一处实现。
     *
     * @param createdAt 沿用旧标记里的创建时间；null = 取当前时间
     */
    fun writeConfigIfNeeded(dir: File, name: String, type: ProjectType, createdAt: Long? = null): Boolean {
        val cfg = File(dir, CONFIG_FILE)
        if (cfg.exists()) return false
        val json = JSONObject()
            .put("name", name)
            .put("type", type.id)
            .put("template", "pient/${type.id}")
            .put("createdAt", createdAt ?: System.currentTimeMillis())
        return runCatching { cfg.writeText(json.toString(2) + "\n") }.isSuccess
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
            // 注意：这里**不能用字符串拼接 + 单个 .trimIndent()** —— Kotlin 里 `a + b + c.trimIndent()`
            // 只对最后一段生效，前几段保留原始缩进（实测生成的 main.py 文档字符串被缩进 16 空格 →
            // 模块级缩进 = IndentationError，脚本根本跑不了）。所以整段用一条原始串 + 行注释。
            "main.py" to """
                # ${name}：入口脚本（python3 main.py [参数…]）

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
