package com.pient.app.runtime

import android.content.Context
import android.util.Log
import com.pient.app.data.AiBackend
import com.pient.app.data.AiConfigStore
import com.pient.app.data.ProviderConfig
import com.pient.app.data.ReasoningFormat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * pi 侧配置生成：把 Pient 的「服务商与模型配置」（[AiConfigStore]）翻译成 pi 运行时认得的
 * 配置文件，让宿主自己能调模型——这是「模型接线」的核心，缺了它宿主的 `get_state` 里
 * `model=unknown`、`get_available_models` 为空，agent 循环起不来（工具也就永远不会被调用）。
 *
 * 落点（HOME 在 [PiRuntime.homeDir]，与桌面 pi / pi-web 文件同构）：
 * - `~/.pi/agent/models.json` —— 自定义服务商与模型（`providers.<id>.{baseUrl, api, apiKey, models[]}`）
 * - `~/.pi/agent/auth.json`   —— 凭据（`<id>.{type:"api_key", key}`，0600）
 *
 * 字段映射（依据 `Refences/pi-0.85.1/packages/coding-agent/docs/models.md`）：
 * - `api`：端点判定与 [AiBackend.isAnthropicProtocol] 同口径 → `anthropic-messages` / `openai-completions`
 * - 模型：`id`/`name` = Pient 的模型名；`contextWindow`/`maxTokens` = 配置页的 K Tokens × 1000；
 *   `reasoning` = 思考参数格式已知（非 NONE/AUTO）时为 true；
 *   `samplingParams` = 配置页启用的 temperature / top_p / top_k（pi 文档明确这是承载服务商私有参数的口子）
 */
object PiConfig {

    private const val TAG = "PiHost"

    fun agentDir(context: Context): File = File(File(PiRuntime.homeDir(context), ".pi"), "agent")
    fun modelsFile(context: Context): File = File(agentDir(context), "models.json")
    fun authFile(context: Context): File = File(agentDir(context), "auth.json")

    /** 写入 pi 配置；返回写入的服务商数量（0 = 没有可用配置，宿主会以无模型状态启动） */
    fun sync(context: Context): Int {
        val configs = AiConfigStore.configs.values.filter { it.endpoint.isNotBlank() && it.models.isNotEmpty() }
        if (configs.isEmpty()) return 0
        val dir = agentDir(context)
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "配置目录创建失败：${dir.absolutePath}")
            return 0
        }

        val providers = JSONObject()
        val auth = JSONObject()
        var count = 0
        for (cfg in configs) {
            val id = providerId(cfg.providerId)
            if (id.isEmpty()) continue
            val models = JSONArray()
            cfg.models.forEach { models.put(modelJson(it, cfg)) }
            val entry = JSONObject()
                .put("baseUrl", cfg.endpoint.trim().trimEnd('/'))
                .put("api", apiFor(cfg.endpoint))
                .put("models", models)
            if (cfg.apiKey.isNotBlank()) entry.put("apiKey", cfg.apiKey)
            providers.put(id, entry)
            if (cfg.apiKey.isNotBlank()) {
                auth.put(id, JSONObject().put("type", "api_key").put("key", cfg.apiKey))
            }
            count++
        }

        writeJson(modelsFile(context), JSONObject().put("providers", providers))
        writeJson(authFile(context), auth)
        Log.i(TAG, "pi 配置已写入：$count 个服务商 → ${modelsFile(context).absolutePath}")
        return count
    }

    /** pi 的服务商 id：允许字母数字与 `-._`，其余归一为 `-`（pi 侧 id 会出现在模型 id 里） */
    private fun providerId(raw: String): String = raw.trim().lowercase()
        .map { if (it.isLetterOrDigit() || it == '-' || it == '_' || it == '.') it else '-' }
        .joinToString("")
        .trim('-')

    /** 端点 → pi 的 api 字面量（[PiSessions] 物化会话头时复用，判定口径与 models.json 同一处） */
    internal fun apiFor(endpoint: String): String =
        if (AiBackend.isAnthropicProtocol(endpoint)) "anthropic-messages" else "openai-completions"

    private fun modelJson(name: String, cfg: ProviderConfig): JSONObject {
        val o = JSONObject().put("id", name).put("name", name)
        cfg.ctxLenK.trim().toIntOrNull()?.let { o.put("contextWindow", it * 1000) }
        cfg.maxOutK.trim().toIntOrNull()?.let { o.put("maxTokens", it * 1000) }
        // 思考：只有写法已知（非 NONE/AUTO）才声明 reasoning，AUTO 时保守不声明（模型按不支持思考处理）
        if (cfg.reasoningFormat != ReasoningFormat.NONE && cfg.reasoningFormat != ReasoningFormat.AUTO) {
            o.put("reasoning", true)
        }
        val sampling = JSONObject()
        if (cfg.tempEnabled) sampling.put("temperature", cfg.tempValue.trim().toDoubleOrNull() ?: 1.0)
        if (cfg.topPEnabled) sampling.put("top_p", cfg.topPValue.trim().toDoubleOrNull() ?: 1.0)
        if (cfg.topKEnabled) sampling.put("top_k", cfg.topKValue.trim().toIntOrNull() ?: 0)
        if (sampling.length() > 0) o.put("samplingParams", sampling)
        return o
    }

    /** 原子写 + 0600（凭据只在应用私有目录内可读） */
    private fun writeJson(file: File, json: JSONObject) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(json.toString())
                tmp.delete()
            }
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        }.onFailure { Log.w(TAG, "配置写入失败 ${file.name}：${it.message}") }
    }
}
