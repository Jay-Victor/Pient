package com.pient.app.ui.settings

import com.pient.app.data.i18n.L
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pient.app.data.AiBackend
import com.pient.app.data.AiConfigStore
import com.pient.app.data.ChatState
import com.pient.app.data.ModelSetting
import com.pient.app.data.PiAgentFiles
import com.pient.app.data.ProviderCatalog
import com.pient.app.data.ProviderConfig
import com.pient.app.data.ReasoningFormat
import com.pient.app.data.ProviderInfo
import com.pient.app.runtime.PiRpc
import com.pient.app.ui.components.DividerLine
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.components.PientInputBox
import com.pient.app.ui.components.PientTextArea
import com.pient.app.ui.components.SectionHeader
import com.pient.app.ui.theme.LocalPientIsDark
import com.pient.app.ui.theme.MonoFont
import com.pient.app.ui.theme.PientPanel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 逐模型测试的一行（[ok] = null 表示还没测到）。
 */
private data class TestRow(val model: String, val ok: Boolean?, val detail: String? = null)

/** 逐模型测试的并发批大小（全串行太慢、全并发容易被服务商限流 → 429 会误判成「模型不可用」） */
private const val TEST_BATCH = 3

/**
 * 服务商与模型配置：
 * ① 选择服务商卡片：卡内首行标题，第二行服务商展示栏（logo + 名称 + 向下箭头）；
 *    点击弹出选择弹窗（顶部搜索框 + 服务商列表，列表项 = 左侧 logo + 名称）。
 *    服务商清单/名称/默认端点对齐 pi-0.84.2 providers 目录；logo 用
 *    lobehub icons（Mono 用主题色着色、Color 原色）。
 * ② API设置卡片三块：API端点（输入框默认填入所选服务商端点，可编辑，旁向下箭头
 *    弹窗切换多端点）、API密钥（遮蔽输入）、模型列表（输入框 + 图案按钮弹出
 *    模型选择弹窗：搜索框 + 端点可用模型列表，点选自动填入）。
 * ③ 上下文设置卡片：上下文长度 / 最大输出长度（单位 K Tokens）。
 * ③b 思考设置卡片：思考参数格式（自动识别/不发送/OpenAI/DeepSeek·Kimi/
 *    智谱 GLM/通义千问/硅基流动）——决定「思考模式开关」在请求体里怎么表达：
 *    关闭 = 显式禁用字面量、开启 = 显式启用。
 * ④ 模型参数设置卡片：温度（开关 + 数值）、Top_P / Top_K（开关 + 数值）。
 * 配置读写 AiConfigStore（自动持久化）、「测试连接」与
 * 「刷新模型列表」走真实 API（AiBackend.listModels）；成功置 chatState.aiConfigured。
 * 模型列表弹窗的刷新按钮以「已填 API 密钥」为前置条件——未配置密钥只弹轻提示、
 * 不发起拉取；浮层内的所有反馈改走 Toast（页面上的 testState 被 scrim 遮住看不见）。
 */
@Composable
fun ModelConfigScreen(nav: NavController, chatState: ChatState) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    /** 轻提示：浮层（模型列表弹窗）内触发时用——页面上的 testState 被 scrim 遮住看不见 */
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    // ── 状态 ──
    // 已配置服务商列表（AiConfigStore 持久化；无预置——用户自行添加）
    // 顺序用 AiConfigStore 维护的**添加序**（configs.keys 是哈希序，会跳）
    val configuredIds = AiConfigStore.orderedIds()
    var selectedId by remember { mutableStateOf<String?>(configuredIds.firstOrNull()) }
    val provider = selectedId?.let { ProviderCatalog.find(it) }
    val cfg = selectedId?.let { AiConfigStore.configs[it] }
    var providerPickerOpen by remember { mutableStateOf(false) } // +服务商：目录弹窗
    var providerFilter by remember { mutableStateOf("all") }      // 服务商列表筛选：all/added/unadded
    var providerFilterOpen by remember { mutableStateOf(false) }  // 筛选选项弹窗
    var configuredMenuOpen by remember { mutableStateOf(false) } // 展示栏：已配置服务商下拉
    var confirmDeleteOpen by remember { mutableStateOf(false) }  // 删除二次确认
    var endpointPickerOpen by remember { mutableStateOf(false) }
    var modelPickerOpen by remember { mutableStateOf(false) }
    var editingModel by remember { mutableStateOf<String?>(null) } // 逐模型参数浮层：模型列表里的条目原文
    var keyVisible by remember { mutableStateOf(false) }       // API密钥显隐
    var testState by remember { mutableStateOf<String?>(null) } // 测试连接/刷新反馈
    /** 反馈口径：true = 全通过（主色）/ false = 失败（error 色）/ null = 进行中或中性提示 */
    var testOk by remember { mutableStateOf<Boolean?>(null) }
    /** 逐模型测试结果（null = 本次没跑逐模型测试；列表非空即渲染明细行） */
    var testRows by remember { mutableStateOf<List<TestRow>?>(null) }
    var testing by remember { mutableStateOf(false) }          // 逐模型测试进行中（防重入）
    var refreshing by remember { mutableStateOf(false) }       // 模型列表刷新中
    var reasoningFormatOpen by remember { mutableStateOf(false) } // 思考参数格式下拉
    var apiTypeOpen by remember { mutableStateOf(false) }        // API 类型下拉（pi-ai 的 provider.api）

    /** 更新当前服务商配置（写入 AiConfigStore，自动持久化） */
    fun updateConfig(transform: (ProviderConfig) -> ProviderConfig) {
        val id = selectedId ?: return
        val cur = AiConfigStore.configs[id] ?: return
        AiConfigStore.configs[id] = transform(cur)
    }

    /**
     * 改「上下文压缩」**四参**（三旋钮 + 压缩指令）。
     *
     * 为什么单独一个入口：**这三项在 pi 侧是全局一份**（`~/.pi/agent/settings.json` 的 `compaction` 块，
     * settings-manager 里没有 per-provider 的概念）。画在「上下文设置」卡里、跟着当前服务商走的话，
     * 于是编辑第二个服务商的那份值其实不生效 —— 那是界面与 pi 不对味的地方。
     * 现在的口径：改任意一处 = 镜像到所有服务商配置（界面不出现两种值）+ 立即写盘（不等统一落盘的 debounce）。
     * **压缩指令也一并镜像**：它是 Pient 侧字段（`compactNow` 取当前模型的 provider 那份），但画在同一张
     * 「全局」卡里 —— 不镜像就会「在 A 服务商填了、切到 B 就没了」。
     */
    fun setGlobals(
        enabled: Boolean? = null,
        reserve: String? = null,
        keep: String? = null,
        instructions: String? = null,
    ) {
        val ids = AiConfigStore.configs.keys.toList()
        if (ids.isEmpty()) return
        ids.forEach { id ->
            val cur = AiConfigStore.configs[id] ?: return@forEach
            AiConfigStore.configs[id] = cur.copy(
                compactionEnabled = enabled ?: cur.compactionEnabled,
                reserveTokens = reserve ?: cur.reserveTokens,
                keepRecentTokens = keep ?: cur.keepRecentTokens,
                compactInstructions = instructions ?: cur.compactInstructions,
            )
        }
        // 这条是**绕过统一落盘 debounce 的立即写**（改一下就要马上落到 settings.json）——
        // 正因为它先写掉了，之后那次 save() 会看到「内容没变」、不会打脏标记 → 这里自己打。
        val changed = PiAgentFiles.writeSettings(context, AiConfigStore.primaryConfig())
        if (changed) PiRpc.markConfigDirty()
        // **即时生效**：pi 的 settings.json 只在进程启动时读一次，运行中的会话
        // 不会察觉我们刚写的文件 —— 官方给的热改入口就是 RPC `set_auto_compaction`
        // （`PiRpc.setAutoCompaction`）。通道没起来就不发（下次启动自然读到）。
        // 另两参（reserve/keep）pi 没有对应的 RPC：走上面的脏标记 —— 下一轮对话开始时
        // `PiRpc.start()` 会重启通道重读 settings.json，用户不必自己去切模型/重启应用。
        if (enabled != null && PiRpc.usable()) {
            scope.launch { PiRpc.setAutoCompaction(enabled) }
        }
    }

    /**
     * 新增服务商：加入已配置列表并切换为当前；重复添加则仅切换。
     * 只带入目录里的默认端点 —— **API 密钥与模型列表保持为空**：
     * 模型列表由用户手动填写或点「刷新」从服务商 /models 端点拉取，不预填任何模型名。
     */
    fun addProvider(id: String) {
        val p = ProviderCatalog.find(id)
        if (id !in AiConfigStore.configs) {
            // 全局四项（压缩三参 + 压缩指令）在 pi 侧只有一份 —— 新增服务商必须**继承现有值**：
            // 否则卡片显示默认值（开 / 16384 / 20000 / 空），而 settings.json（= pi 实际用的，
            // 取第一家那份）是另一套，界面与实际对不上。
            val globals = AiConfigStore.primaryConfig()
            val fresh = ProviderConfig(
                providerId = id,
                endpoint = p.defaultEndpoint,
                // 已知服务商的思考参数写法直接预置（自定义服务商 = AUTO 按模型名推断）
                reasoningFormat = p.reasoningFormat,
            )
            if (id !in AiConfigStore.providerOrder) AiConfigStore.providerOrder.add(id)
            AiConfigStore.configs[id] = if (globals == null) fresh else fresh.copy(
                compactionEnabled = globals.compactionEnabled,
                reserveTokens = globals.reserveTokens,
                keepRecentTokens = globals.keepRecentTokens,
                compactInstructions = globals.compactInstructions,
            )
        }
        selectedId = id
        testState = null
        testOk = null
        testRows = null
        providerPickerOpen = false
    }

    /** 删除当前服务商：从已配置列表移除，切换到剩余第一个（可能为空） */
    fun deleteCurrent() {
        val id = selectedId ?: return
        AiConfigStore.configs.remove(id)
        AiConfigStore.providerOrder.remove(id)
        selectedId = AiConfigStore.orderedIds().firstOrNull()
        // 删光服务商 = 回到「未配置」：完成标记跟着回落（否则首启引导第二步一直算已完成，
        // 而聊天页此时连消息都发不出去）
        if (AiConfigStore.configs.isEmpty()) chatState.aiConfigured = false
        testState = null
        testOk = null
        testRows = null
        confirmDeleteOpen = false
    }

    /**
     * 测试连接 / 刷新模型列表共用：GET 服务商模型端点。
     * 前置校验：API 端点与 API 密钥都必须已填写，缺一不发请求，只提示。
     * @param onMessage 反馈出口，默认写页面内的 testState；模型列表弹窗传入 Toast（浮层挡住页面文字）
     */
    fun callModels(
        onMessage: (String) -> Unit = { testState = it },
        onSuccess: (List<String>) -> Unit,
    ) {
        val cur = cfg ?: return
        if (cur.endpoint.isBlank()) {
            onMessage(L.models.needEndpoint)
            return
        }
        if (cur.apiKey.isBlank()) {
            onMessage(L.models.needApiKey)
            return
        }
        scope.launch {
            try {
                onSuccess(AiBackend.listModels(cur))
            } catch (e: Exception) {
                onMessage(L.models.connectFailedDetail(e.message ?: L.common.unknownError))
            }
        }
    }

    /**
     * **「测试连接」= 模型列表里的每一个模型都真发一次推理请求**。
     *
     * 只 GET `/models`（= 只证明「能列模型」）不够：套餐不含某模型 / 模型名写错 / 权限不对
     * 一律测不出来 —— 那不算「可用」。逐模型发 `max_tokens=16` 的最小请求，逐行给结果。
     *
     * 三个分支：
     * ① 模型列表为空 → 退回「列模型」探活，并如实说明没逐一测；
     * ② API 类型是应用测不了的（google / bedrock / mistral / pi-messages）→ 如实上报，
     *    **不假装失败**（这些协议的应用内请求构造不在本轮范围内）；
     * ③ 有模型 + 协议可测 → 每批 3 个并发（全串行太慢；全并发容易被限流 429 误判成不可用）。
     */
    fun startTest() {
        val cur = cfg ?: return
        testRows = null
        if (cur.endpoint.isBlank()) {
            testState = L.models.needEndpoint
            testOk = false
            return
        }
        if (cur.apiKey.isBlank()) {
            testState = L.models.needApiKey
            testOk = false
            return
        }
        val entries = cur.models
        if (AiBackend.chatProtocol(cur) == AiBackend.ChatProtocol.UNSUPPORTED) {
            testState = L.models.modelsTestUnsupported(
                cur.apiType.trim().ifBlank { ProviderCatalog.apiOf(cur.providerId) },
            )
            testOk = null
            return
        }
        if (entries.isEmpty()) {
            testState = L.models.testing
            testOk = null
            callModels { models ->
                testState = if (models.isEmpty()) L.models.connectionOkNoModels else L.models.connectionOk
                testOk = true
                // **不置 aiConfigured**：这一步只证明「能列模型」，不等于某个模型真能用 ——
                // 「AI 配置完成」的唯一口径 = 至少一个模型真跑通（见下面逐模型测试那段）。
            }
            return
        }
        if (testing) return
        testing = true
        testState = L.models.testing
        testOk = null
        testRows = entries.map { TestRow(it.substringBefore('=').trim(), null) }
        scope.launch {
            val done = ArrayList<TestRow>(entries.size)
            for (chunk in entries.chunked(TEST_BATCH)) {
                val got = coroutineScope {
                    chunk.map { e -> async { AiBackend.testModel(cur, e) } }.awaitAll()
                }
                got.forEach { p -> done += TestRow(p.model, p.ok, p.detail) }
                // 已测的在下、未测的保持转圈（列表顺序 = 用户填的顺序）
                testRows = done + entries.drop(done.size).map { TestRow(it.substringBefore('=').trim(), null) }
            }
            testing = false
            val ok = done.count { it.ok == true }
            testState = if (ok == done.size) L.models.modelsAllOk(done.size)
            else L.models.modelsTestPartial(ok, done.size)
            testOk = ok == done.size
            // 「AI 配置完成」= 至少一个模型真跑通（首启引导第二步靠它）
            if (ok > 0) chatState.aiConfigured = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ── 顶栏 ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Outlined.ArrowBack, L.common.back,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = { nav.popBackStack() }),
            )
            Text(
                L.settings.modelConfig,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── ① 选择服务商 ──
            item {
                Column {
                    // 标题行：左侧标题（图标+文字），右侧"+服务商"按钮（描边样式）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.SmartToy, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            L.models.selectProvider,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        ActionChipButton(
                            icon = Icons.Outlined.Add,
                            text = L.models.provider,
                            onClick = { providerPickerOpen = true },
                        )
                    }
                    ConfigCard {
                        // 服务商展示栏（点击展开已配置服务商下拉弹窗；无服务商时显示占位）
                        Box {
                            if (provider != null) {
                                ProviderBar(
                                    provider = provider,
                                    onClick = { configuredMenuOpen = true },
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                                ) {
                                    Text(
                                        L.models.noProviderSelected,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = configuredMenuOpen,
                                onDismissRequest = { configuredMenuOpen = false },
                                modifier = Modifier.width(280.dp),
                            ) {
                                // 注意：不能 forEach（lambda 非 @Composable 上下文），用 for 循环
                                for (id in configuredIds) {
                                    val p = ProviderCatalog.find(id)
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                ProviderLogo(p, size = 20.dp)
                                                Text(
                                                    p.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                                                )
                                                if (id == selectedId) {
                                                    Icon(
                                                        Icons.Outlined.Check, null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedId = id
                                            testState = null
                                            testOk = null
                                            testRows = null
                                            configuredMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        // 操作行：图案+文字小按钮，靠左（仅在有当前服务商时显示）
                        if (provider != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ActionChipButton(
                                    icon = Icons.Outlined.Delete,
                                    text = L.common.delete,
                                    danger = true,
                                    onClick = { confirmDeleteOpen = true },
                                )
                                ActionChipButton(
                                    icon = Icons.Outlined.Dns, // 测试连接按钮的图标
                                    text = L.models.testConnection,
                                    // **逐一测试模型列表里的每个模型**（见 startTest）
                                    onClick = { startTest() },
                                )
                            }
                        }
                        if (testState != null) {
                            Text(
                                testState.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                // 颜色只认 testOk（不按文案前缀判：文案随语言变，前缀判法会失效）
                                color = when (testOk) {
                                    true -> MaterialTheme.colorScheme.primary
                                    false -> MaterialTheme.colorScheme.error
                                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                            )
                        }
                        // 逐模型明细：执行中就渲染（未测到的行是转圈），测完每行给 ✓ / ✕ + 失败原因
                        testRows?.let { rows ->
                            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp)) {
                                for (r in rows) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    ) {
                                        Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                                            when (r.ok) {
                                                null -> CircularProgressIndicator(
                                                    modifier = Modifier.size(11.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                true -> Icon(
                                                    Icons.Outlined.Check, null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                                false -> Icon(
                                                    Icons.Outlined.Close, null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                            }
                                        }
                                        Column(Modifier.weight(1f).padding(start = 6.dp)) {
                                            Text(
                                                r.model,
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFont),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (r.detail != null) {
                                                Text(
                                                    r.detail,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(top = 1.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── ② API设置（仅在已选择服务商时显示） ──
            if (provider != null && cfg != null) {
                item {
                    Column {
                        SectionHeader(L.models.apiSettings, icon = Icons.Outlined.Api)
                        ConfigCard {
                            // 2.1 API端点
                            ConfigFieldLabel(L.models.apiEndpoint)
                            FieldHint(L.models.endpointHint)
                            EndpointField(
                                value = cfg.endpoint,
                                onValueChange = { v -> updateConfig { it.copy(endpoint = v) } },
                                onOpenPicker = { endpointPickerOpen = true },
                            )
                            DividerLine()
                            // 2.2 API 类型（pi-ai 的 `api`：provider 级默认值，逐模型可在 JSON 里覆盖）
                            ConfigFieldLabel(L.models.apiType)
                            FieldHint(L.models.apiTypeHint)
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { apiTypeOpen = true }
                                        .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            cfg.apiType.ifBlank { ProviderCatalog.apiOf(provider.id) },
                                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFont),
                                        )
                                        Text(
                                            if (cfg.apiType.isBlank()) {
                                                L.models.providerPresetArg(ProviderCatalog.apiOf(provider.id))
                                            } else if (cfg.apiType == ProviderCatalog.apiOf(provider.id)) {
                                                L.models.apiMatchesPi
                                            } else {
                                                L.models.apiOverridesPi
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                    Icon(
                                        Icons.Outlined.ExpandMore, null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = apiTypeOpen,
                                    onDismissRequest = { apiTypeOpen = false },
                                    modifier = Modifier.width(320.dp),
                                ) {
                                    // 第一项 = 跟随预设（空串）；其余是该服务商可用的 api 集合
                                    val options = listOf("") + ProviderCatalog.apiOptions(provider.id, cfg.apiType)
                                    for (api in options) {
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(
                                                            api.ifBlank { L.models.followProviderPreset },
                                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                                fontFamily = if (api.isBlank()) null else MonoFont,
                                                            ),
                                                        )
                                                        Text(
                                                            if (api.isBlank()) ProviderCatalog.apiOf(provider.id)
                                                            else L.models.apiOptionHint,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                    if (api == cfg.apiType) {
                                                        Icon(
                                                            Icons.Outlined.Check, null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                updateConfig { it.copy(apiType = api) }
                                                apiTypeOpen = false
                                            },
                                        )
                                    }
                                }
                            }
                            DividerLine()
                            // 2.3 API密钥
                            ConfigFieldLabel(L.models.apiKey)
                            FieldHint(L.models.apiKeyHint)
                            ApiKeyField(
                                value = cfg.apiKey,
                                onValueChange = { v -> updateConfig { it.copy(apiKey = v) } },
                                visible = keyVisible,
                                onToggleVisible = { keyVisible = !keyVisible },
                            )
                            DividerLine()
                            // 2.4 模型列表
                            ConfigFieldLabel(L.models.modelList)
                            FieldHint(L.models.modelListHint)
                            ModelListField(
                                value = cfg.modelList,
                                onValueChange = { v -> updateConfig { it.copy(modelList = v) } },
                                onOpenPicker = { modelPickerOpen = true },
                            )
                            FieldHint(L.models.perModelDefaultsNote)
                        }
                    }
                }
            }

            // ── ③b 思考设置（关闭思考模式 = 显式禁用，开启 = 显式启用） ──
            if (provider != null && cfg != null) {
                item {
                    Column {
                        SectionHeader(L.models.thinkingSettings, icon = Icons.Outlined.Psychology)
                        ConfigCard {
                            Box {
                                val effective = AiBackend.effectiveReasoningFormat(cfg)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { reasoningFormatOpen = true }
                                        .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            L.models.thinkingFormat,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            // 已知服务商：写明「服务商预设」，让用户知道这行不用自己填
                                            when {
                                                cfg.reasoningFormat == ReasoningFormat.AUTO ->
                                                    L.models.thinkingAutoActive(effective.label, effective.wire)
                                                provider.id != ProviderCatalog.CUSTOM_ID &&
                                                    cfg.reasoningFormat == provider.reasoningFormat ->
                                                    L.models.thinkingProviderPreset(cfg.reasoningFormat.wire)
                                                else -> cfg.reasoningFormat.wire
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                    Text(
                                        cfg.reasoningFormat.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 10.dp),
                                    )
                                    Icon(
                                        Icons.Outlined.ExpandMore, null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 2.dp).size(18.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = reasoningFormatOpen,
                                    onDismissRequest = { reasoningFormatOpen = false },
                                    modifier = Modifier.width(320.dp),
                                ) {
                                    // 注意：不能 forEach（lambda 非 @Composable 上下文），用 for 循环
                                    for (f in ReasoningFormat.entries) {
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(f.label, style = MaterialTheme.typography.bodyMedium)
                                                        Text(
                                                            f.wire,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                    if (f == cfg.reasoningFormat) {
                                                        Icon(
                                                            Icons.Outlined.Check, null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                updateConfig { it.copy(reasoningFormat = f) }
                                                reasoningFormatOpen = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── ⑤ 上下文压缩（**全局一份**）──
            // pi 侧只有一份：`~/.pi/agent/settings.json` 的 `compaction` 块（settings-manager 里没有
            // per-provider 的概念）。画在「上下文设置」卡里、跟着当前服务商走的话 —— 改第二个服务商
            // 那份值其实不生效，是界面与 pi 不对味的地方。单独成卡 + 改完立即写盘。
            if (provider != null && cfg != null) {
                item {
                    Column {
                        SectionHeader(L.models.compactionTitle, icon = Icons.Outlined.Compress)
                        ConfigCard {
                            FieldHint(L.models.compactionHint)
                            ParamBlock(
                                label = L.models.compactionAuto,
                                hint = L.models.compactionAutoHint,
                                enabled = cfg.compactionEnabled,
                                onToggle = { setGlobals(enabled = !cfg.compactionEnabled) },
                            )
                            if (cfg.compactionEnabled) {
                                ConfigFieldLabel(L.models.reserveTokens)
                                FieldHint(L.models.reserveTokensHint)
                                ContextNumberField(
                                    value = cfg.reserveTokens,
                                    onValueChange = { v ->
                                        setGlobals(reserve = v.filter { c -> c.isDigit() }.take(7))
                                    },
                                    placeholder = "16384",
                                    suffix = "Tokens",
                                )
                                ConfigFieldLabel(L.models.keepRecentTokens)
                                FieldHint(L.models.keepRecentTokensHint)
                                ContextNumberField(
                                    value = cfg.keepRecentTokens,
                                    onValueChange = { v ->
                                        setGlobals(keep = v.filter { c -> c.isDigit() }.take(7))
                                    },
                                    placeholder = "20000",
                                    suffix = "Tokens",
                                )
                            }
                            ConfigFieldLabel(L.models.compactInstructions)
                            FieldHint(L.models.compactInstructionsHint)
                            PientTextArea(
                                value = cfg.compactInstructions,
                                onValueChange = { v -> setGlobals(instructions = v) },
                                placeholder = L.models.compactInstructionsPlaceholder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                            )
                        }
                    }
                }

            }

        }
    }

    // ── 弹窗：删除二次确认 ──
    if (confirmDeleteOpen) {
        PientDialog(
            title = L.models.deleteProvider,
            onDismiss = { confirmDeleteOpen = false },
            confirmText = L.common.delete,
            onConfirm = { deleteCurrent() },
            showClose = false, // 底部已有取消按钮，右上角 × 重复（见 skills-plugins-page-pitfalls）
        ) {
            Text(
                L.models.deleteProviderConfirm(provider?.name.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // ── 弹窗：服务商选择列表（+服务商 新增；顶栏筛选 + 底部取消） ──
    if (providerPickerOpen) {
        val providerEntries = remember(selectedId, configuredIds) {
            (listOf(ProviderCatalog.custom) + ProviderCatalog.all).map {
                PickerEntry(
                    key = it.id,
                    title = it.name,
                    logoRes = it.logoRes,
                    logoResDark = it.logoResDark,
                    monoLogo = it.monoLogo,
                    selected = it.id == selectedId,
                    added = it.id in configuredIds,
                )
            }
        }
        // 筛选后的列表：全部 / 已添加 / 未添加
        val filteredProviderEntries = providerEntries.filter {
            when (providerFilter) {
                "added" -> it.added
                "unadded" -> !it.added
                else -> true
            }
        }
        SearchPickerPopup(
            topBarTitle = L.models.providerPickerTitle,
            topBarAction = {
                FilterIconButton(onClick = { providerFilterOpen = true })
            },
            searchPlaceholder = L.models.providerSearch,
            entries = filteredProviderEntries,
            showCancel = true,
            onDismiss = { providerPickerOpen = false },
            onSelect = { e -> addProvider(e.key) },
        )
        // 筛选选项弹窗（全部 / 已添加 / 未添加）
        if (providerFilterOpen) {
            SearchPickerPopup(
                title = L.models.filterTitle,
                entries = listOf(
                    PickerEntry(key = "all", title = L.common.all, selected = providerFilter == "all"),
                    PickerEntry(key = "added", title = L.models.filterAdded, selected = providerFilter == "added"),
                    PickerEntry(key = "unadded", title = L.models.filterNotAdded, selected = providerFilter == "unadded"),
                ),
                onDismiss = { providerFilterOpen = false },
                onSelect = { e ->
                    providerFilter = e.key
                    providerFilterOpen = false
                },
            )
        }
    }

    // ── 弹窗：切换 API 端点 ──
    if (endpointPickerOpen) {
        val endpointEntries = (provider?.endpoints ?: emptyList()).map {
            PickerEntry(key = it, title = it, mono = true, selected = it == cfg?.endpoint)
        }
        SearchPickerPopup(
            title = L.models.apiEndpoint,
            entries = endpointEntries,
            onDismiss = { endpointPickerOpen = false },
            onSelect = { e ->
                updateConfig { it.copy(endpoint = e.key) }
                endpointPickerOpen = false
            },
        )
    }

    // ── 弹窗：逐模型参数（模型列表里点铅笔） ──
    // 为什么要有它：pi 的 models.json 里 `contextWindow` / `maxTokens` / `input` / `samplingParams`
    // 都是**每个模型各写一份**；卡面上那三个字段只是
    // 「新加入列表的模型的默认值」，改它不会动已有模型。
    editingModel?.let { entry ->
        val modelId = entry.substringBefore('=').trim()
        // 「未单独设置」时该模型的默认思考支持 = 写法推断（与写盘同一份规则：AUTO 按模型名推）
        val fmtNow = cfg?.let { c ->
            if (c.reasoningFormat == ReasoningFormat.AUTO) AiBackend.inferReasoningFormat(modelId)
            else c.reasoningFormat
        }
        ModelSettingDialog(
            title = L.models.editModelTitle(entry),
            initial = cfg?.settingOf(modelId) ?: ModelSetting(),
            thinkingByFormat = fmtNow != null && fmtNow != ReasoningFormat.NONE,
            formatLabel = fmtNow?.label.orEmpty(),
            // 采样不转发警告：放在真正设值的地方
            unsupportedNote = cfg?.takeIf { !AiBackend.samplingSupported(it) }?.let { c ->
                L.models.samplingUnsupported(c.apiType.trim().ifBlank { ProviderCatalog.apiOf(c.providerId) })
            },
            onDismiss = { editingModel = null },
            onSave = { s ->
                updateConfig { it.copy(modelSettings = it.modelSettings + (modelId to s)) }
                editingModel = null
            },
        )
    }

    // ── 弹窗：模型列表（多选 + 底部取消/确定；顶栏刷新 = 真实拉取服务商模型） ──
    if (modelPickerOpen) {
        // 条目源与「已选」判定同源 = cfg.models（trim 过、去重过）：手写「a; b」这种带空格的列表
        // 也要每行都是勾选态（按未 trim 的 split 判 → b 不勾 → 点「确定」把它静默删掉）。
        val modelEntries = (cfg?.models ?: emptyList()).map {
            PickerEntry(key = it, title = it, mono = true, selected = true, editable = true)
        }
        SearchPickerPopup(
            topBarTitle = L.models.modelPickerTitle,
            topBarAction = {
                RefreshIconButton(
                    loading = refreshing,
                    onClick = {
                        // 仅在已填入 API 密钥后才发起拉取；未配置则只弹提示、不进 loading 态
                        if (cfg?.apiKey.isNullOrBlank()) {
                            toast(L.models.noApiKey)
                        } else {
                            refreshing = true
                            callModels(
                                onMessage = { msg ->
                                    toast(msg)
                                    refreshing = false // 失败/前置校验未通过：必须结束 loading，否则转圈卡死
                                },
                            ) { models ->
                                if (models.isEmpty()) toast(L.models.noModelsReturned)
                                else updateConfig { it.copy(modelList = mergeModelList(it.modelList, models)) }
                                refreshing = false
                            }
                        }
                    },
                )
            },
            searchPlaceholder = L.models.modelSearch,
            entries = modelEntries,
            multiSelect = true,
            onDismiss = { modelPickerOpen = false },
            // 铅笔 = 改**这一个模型**的窗口 / 识图 / 采样（pi 侧这些字段本来就是 per-model）
            // 顺手收起列表：编辑浮层要画在它上面，列表留着也会挡住（同一 Box 里后画的在上面也就算了，
            // 关掉还能避免多选状态被误清）
            onEditRow = { e ->
                editingModel = e.title
                modelPickerOpen = false
            },
            onConfirmMulti = { keys ->
                // 多选确认：以英文分号拼接填入输入框
                updateConfig {
                    it.copy(modelList = modelEntries.filter { e -> e.key in keys }.joinToString(";") { e -> e.key })
                }
                modelPickerOpen = false
            },
        )
    }
}

// ───────────────────────────── 卡片骨架 ─────────────────────────────

@Composable
private fun ConfigCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
    ) {
        content()
    }
}

/** 操作行小按钮：图案 + 文字、靠左（删除 = error 描边；测试连接 = 主色描边） */
@Composable
private fun ActionChipButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val fg = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(30.dp)
            .border(1.dp, fg.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
    ) {
        Icon(
            icon, null,
            tint = fg,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * 参数区块头：左列标签+辅助说明，右侧开关（M3 默认尺寸，勿 size 压缩）
 *
 * 同一视觉被上下文设置的「自动总结上下文」等区块复用 —— 不另写一份开关行。
 */
@Composable
private fun ParamBlock(
    label: String,
    hint: String,
    enabled: Boolean,
    onToggle: () -> Unit,
    /** 开关本身是否可操作（false = 该 API 类型下 pi 不转发这类参数 → 置灰，不摆假控件） */
    switchEnabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 10.dp, end = 6.dp),
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (switchEnabled) 1f else 0.5f),
            )
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (switchEnabled) 0.8f else 0.4f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = enabled,
            enabled = switchEnabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

/** 参数输入框区（开启后显示）：输入框 + 下方取值范围说明 */
@Composable
private fun ParamInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    rangeHint: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp),
    ) {
        PientInputBox(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            number = true,
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        rangeHint,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 10.dp),
    )
}


/**
 * 逐模型参数浮层：**单个模型**的上下文长度 / 最大输出 / 识图 / 采样。
 * 留空 = 不写该键（pi 用自己那一层的默认）。
 */
@Composable
private fun ModelSettingDialog(
    title: String,
    initial: ModelSetting,
    /** 该 API 类型下 pi 不转发采样参数时的说明（null = 不显示） */
    unsupportedNote: String?,
    /** 「思考设置」卡按当前写法推断出的**默认**是否支持思考（未单独设置时生效） */
    thinkingByFormat: Boolean,
    /** 该写法的人类可读名（用于说明行「当前：X」） */
    formatLabel: String,
    onDismiss: () -> Unit,
    onSave: (ModelSetting) -> Unit,
) {
    var ctxLenK by remember { mutableStateOf(initial.ctxLenK) }
    var maxOutK by remember { mutableStateOf(initial.maxOutK) }
    var image by remember { mutableStateOf(initial.image) }
    var temperature by remember { mutableStateOf(initial.temperature) }
    var topK by remember { mutableStateOf(initial.topK) }
    var topP by remember { mutableStateOf(initial.topP) }
    // 三态：null = 未单独设置（跟随写法推断）。**只有用户动过开关/恢复默认才会变** ——
    // 没动就原样回写，避免「只改了窗口」把思考支持也冻成显式值。
    var reasoning by remember { mutableStateOf(initial.reasoning) }
    val digits = { v: String -> v.filter { it.isDigit() } }
    val decimal = { v: String -> v.filter { it.isDigit() || it == '.' } }
    PientDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = L.common.save,
        onConfirm = {
            onSave(
                ModelSetting(
                    ctxLenK = ctxLenK.trim(),
                    maxOutK = maxOutK.trim(),
                    image = image,
                    temperature = temperature.trim(),
                    topK = topK.trim(),
                    topP = topP.trim(),
                    reasoning = reasoning,
                ),
            )
        },
    ) {
        Text(
            L.models.modelSettingEmptyHint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = 2.dp),
        )
        unsupportedNote?.let { note ->
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        DialogFieldRow(L.models.contextLength, ctxLenK, digits, "128", "K") { ctxLenK = it }
        DialogFieldRow(L.models.maxOutputLength, maxOutK, digits, "16", "K") { maxOutK = it }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            Text(
                L.models.imageSupport,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Switch(
                checked = image,
                onCheckedChange = { image = it },
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
            )
        }
        Text(
            L.models.imageSupportHint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Text(
                L.models.modelThinkingSupport,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Switch(
                checked = reasoning ?: thinkingByFormat,
                // 拨一下 = 显式指定该模型支持/不支持（写进 pi 的 models[].reasoning）
                onCheckedChange = { reasoning = it },
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
            )
        }
        if (reasoning == null) {
            Text(
                L.models.modelThinkingByFormat(formatLabel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Text(
                L.models.modelThinkingReset,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { reasoning = null },
            )
        }
        DialogFieldRow(L.models.temperature, temperature, decimal, "1.0", null) { temperature = it }
        DialogFieldRow(L.models.topK, topK, digits, "0", null) { topK = it }
        DialogFieldRow(L.models.topP, topP, decimal, "1.0", null) { topP = it }
    }
}

/** 逐模型浮层里的「标签 + 输入框」一行（留空 = 不传该参数，故不给开关，只给值） */
@Composable
private fun DialogFieldRow(
    label: String,
    value: String,
    filter: (String) -> String,
    placeholder: String,
    suffix: String?,
    onChange: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        PientInputBox(
            value = value,
            onValueChange = { v -> onChange(filter(v)) },
            placeholder = placeholder,
            number = true,
            suffix = suffix,
            modifier = Modifier.width(120.dp),
        )
    }
}

/** 字段标签行（"API端点"/"API密钥"/"模型列表"） */
@Composable
private fun ConfigFieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 2.dp),
    )
}

/** 字段辅助解释文字（label 下方） */
@Composable
private fun FieldHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
    )
}

// ───────────────────────────── ① 选择服务商 ─────────────────────────────

/** 服务商展示栏：logo + 名称 + 右侧向下箭头（整行点击弹出选择弹窗） */
@Composable
private fun ProviderBar(provider: ProviderInfo, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 12.dp),
    ) {
        ProviderLogo(provider, size = 26.dp)
        Text(
            provider.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        Icon(
            Icons.Outlined.KeyboardArrowDown, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 服务商 logo：Mono 图标用主题文字色着色、Color 图标原色；
 *  固有色在暗色卡片上不可见的图标（AWS 黑字 / Kimi 白 K）用 logoResDark 暗色变体 */
@Composable
private fun ProviderLogo(provider: ProviderInfo, size: androidx.compose.ui.unit.Dp) {
    if (provider.logoRes == 0) {
        Box(
            modifier = Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "+",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        return
    }
    val res = if (LocalPientIsDark.current && provider.logoResDark != 0) {
        provider.logoResDark
    } else provider.logoRes
    val tint = if (provider.monoLogo) {
        ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
    } else null
    Image(
        painter = painterResource(res),
        contentDescription = provider.name,
        colorFilter = tint,
        modifier = Modifier.size(size),
    )
}

// ───────────────────────────── ② API设置字段 ─────────────────────────────

/** API端点输入框 + 右侧向下箭头按钮（弹出端点切换弹窗） */
@Composable
private fun EndpointField(
    value: String,
    onValueChange: (String) -> Unit,
    onOpenPicker: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
    ) {
        PientInputBox(
            value = value,
            onValueChange = onValueChange,
            placeholder = "https://api.example.com/v1",
            modifier = Modifier.weight(1f),
        )
        FieldIconButton(
            icon = { Icons.Outlined.KeyboardArrowDown },
            contentDescription = L.models.switchEndpoint,
            onClick = onOpenPicker,
        )
    }
}

/** API密钥输入框（默认遮蔽）+ 右侧显隐图案按键 */
@Composable
private fun ApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisible: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
    ) {
        PientInputBox(
            value = value,
            onValueChange = onValueChange,
            placeholder = L.models.apiKeyPlaceholder,
            password = !visible,
            modifier = Modifier.weight(1f),
        )
        FieldIconButton(
            icon = { if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility },
            contentDescription = if (visible) L.models.hideKey else L.models.showKey,
            onClick = onToggleVisible,
        )
    }
}

/** 模型列表输入框 + 右侧图案按钮（弹出模型选择弹窗） */
@Composable
private fun ModelListField(
    value: String,
    onValueChange: (String) -> Unit,
    onOpenPicker: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
    ) {
        PientInputBox(
            value = value,
            onValueChange = onValueChange,
            placeholder = L.models.modelListPlaceholder,
            modifier = Modifier.weight(1f),
        )
        FieldIconButton(
            icon = { Icons.Outlined.Apps },
            contentDescription = L.models.selectModel,
            onClick = onOpenPicker,
        )
    }
}

/** 输入框旁的图标按钮（38dp 高、与输入框同风格） */
@Composable
private fun FieldIconButton(
    icon: @Composable () -> androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .height(38.dp)
            .width(38.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon(), contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 筛选图标按钮（服务商选择列表顶栏右侧，弹出 全部/已添加/未添加 筛选） */
@Composable
private fun FilterIconButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.FilterList, L.models.filterTitle,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 刷新图标按钮（loading 时显示转圈，用于模型列表弹窗刷新） */
@Composable
private fun RefreshIconButton(loading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                Icons.Outlined.Refresh, L.common.refresh,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ───────────────────────────── ③ 上下文设置 ─────────────────────────────

/** 数值输入框：单位 "K Tokens" 显示在输入框内最右侧 */
@Composable
private fun TokenInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    PientInputBox(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        number = true,
        suffix = "K Tokens",
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
    )
}

/**
 * 上下文管理数值行：比例阈值 / 条数阈值共用一行式输入框。
 *
 * `decimal = true` 时用文本键盘：`KeyboardType.Number` 在部分输入法上没有小数点键，
 * 而「0.70」这类占比必须要小数点（条数阈值仍走数字键盘）。
 */
@Composable
private fun ContextNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    suffix: String,
    decimal: Boolean = false,
) {
    PientInputBox(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        number = !decimal,
        suffix = suffix,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
    )
}

// ───────────────────────────── ④ 模型参数设置 ─────────────────────────────
// ───────────────────────────── 弹窗 ─────────────────────────────

/**
 * 居中选择浮层（页面内全屏 scrim + 居中卡片，与 PientDialog / 聊天页浮层同构）：
 * 顶部 = 标题（可选）或搜索框；下方 = LazyColumn 列表；点外/返回键关闭。
 *
 * **不用平台 Popup 窗口**：Popup 是独立窗口，窗口高度取自
 * 「可见显示区 − IME」——键盘抬起时打开浮层，窗口只有 1080×1454px（正常 1080×2274），
 * 于是 fillMaxSize 的 scrim 只铺到键盘上沿、页面下半截没有变暗；且键盘随后收起
 * （Popup focusable 抢走焦点）窗口也不会再长回去。
 * 页面内浮层随应用窗口（全屏、不随 IME 缩放）布局，天然不受键盘影响。
 */
/**
 * 刷新回来的 id 列表与手写列表合并（**不是整串覆盖**）：
 * - 同名 id 保留用户写的 `id=别名` 原文（别名是 pi 的 `models[].name`，覆盖掉就丢了）；
 * - 顺序按服务商返回的顺序重排；
 * - 服务商不再返回的 id 丢弃（那本来就是这个按钮的语义：以服务商当前可用列表为准）。
 */
private fun mergeModelList(old: String, fetched: List<String>): String {
    val original = HashMap<String, String>()
    old.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { entry ->
        original[entry.substringBefore('=').trim()] = entry
    }
    return fetched.joinToString(";") { id -> original[id] ?: id }
}

/** 弹窗选择项（服务商 / 端点 / 模型统一数据） */
private data class PickerEntry(
    val key: String,
    val title: String,
    @androidx.annotation.DrawableRes val logoRes: Int = 0,
    @androidx.annotation.DrawableRes val logoResDark: Int = 0,
    val monoLogo: Boolean = false,
    val mono: Boolean = false, // 等宽字体渲染（端点/模型）
    val selected: Boolean = false,
    val added: Boolean = false, // 已配置（服务商目录弹窗：右侧灰勾）
    val editable: Boolean = false, // 该行可编辑（模型弹窗：右侧铅笔 → 逐模型参数浮层）
)

@Composable
private fun SearchPickerPopup(
    entries: List<PickerEntry>,
    onDismiss: () -> Unit,
    searchPlaceholder: String? = null,
    title: String? = null,
    topBarTitle: String? = null,
    topBarAction: (@Composable () -> Unit)? = null,
    showCancel: Boolean = false,                       // 底部L.common.cancel（单选弹窗）
    multiSelect: Boolean = false,                      // 多选模式：底部"取消 + 确定"
    onSelect: (PickerEntry) -> Unit = {},              // 单选回调
    onConfirmMulti: (Set<String>) -> Unit = {},        // 多选确定回调
    onEditRow: ((PickerEntry) -> Unit)? = null,        // 行内编辑（模型弹窗：逐模型参数）
) {
    var query by remember { mutableStateOf("") }
    val filtered = if (searchPlaceholder == null) entries
    else entries.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    // 多选状态（初始 = entries 中 selected 项）
    val selMap = remember(entries) {
        mutableStateMapOf<String, Boolean>().apply {
            entries.filter { it.selected }.forEach { put(it.key, true) }
        }
    }
    // 浮层本体：页面内全屏 scrim + 居中卡片（与 PientDialog / 聊天页浮层同构，不另开平台窗口）。
    // 打开即收起键盘：浮层内除搜索框外无输入需求，也避免面板被键盘遮住。
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focusManager.clearFocus() }
    BackHandler { onDismiss() }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        PientPanel(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(horizontal = 16.dp)
                .clickable(
                    onClick = {},
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column {
                // 顶栏（标题 + 右侧操作，如模型弹窗"模型选择列表" + 刷新）
                if (topBarTitle != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 10.dp, end = 12.dp, bottom = 4.dp),
                    ) {
                        Text(
                            topBarTitle,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        topBarAction?.invoke()
                    }
                }
                // 标题（无搜索弹窗，如端点弹窗）
                if (title != null) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 10.dp),
                    )
                }
                if (searchPlaceholder != null) {
                    SearchBar(
                        placeholder = searchPlaceholder,
                        query = query,
                        onQueryChange = { query = it },
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(filtered, key = { it.key }) { e ->
                        PickerRow(
                            entry = if (multiSelect) e.copy(selected = selMap[e.key] == true) else e,
                            onEdit = if (onEditRow != null && e.editable) ({ onEditRow(e) }) else null,
                            onClick = {
                                if (multiSelect) {
                                    if (selMap[e.key] == true) selMap.remove(e.key) else selMap[e.key] = true
                                } else {
                                    onSelect(e)
                                }
                            },
                        )
                    }
                }
                // 底部按钮区：单选弹窗"取消"；多选弹窗"取消 + 确定"
                if (showCancel || multiSelect) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PientButton(
                            L.common.cancel,
                            onClick = onDismiss,
                            primary = false,
                            modifier = Modifier.weight(1f),
                        )
                        if (multiSelect) {
                            PientButton(
                                L.common.confirm,
                                onClick = { onConfirmMulti(selMap.filterValues { it }.keys) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 弹窗顶部搜索框（放大镜 + 输入 + 底部 hairline） */
@Composable
private fun SearchBar(placeholder: String, query: String, onQueryChange: (String) -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                Icons.Outlined.Search, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    inner()
                },
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        )
    }
}

/** 弹窗列表项：服务商 = 左侧 logo + 名称；端点/模型 = 等宽文本（选中 = 主色 15% 底 + 粗体主色字 + 勾） */
@Composable
private fun PickerRow(entry: PickerEntry, onClick: () -> Unit, onEdit: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(
                if (entry.selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        if (entry.logoRes != 0) {
            PickerEntryLogo(entry, size = 24.dp)
        }
        Text(
            entry.title,
            style = if (entry.mono) {
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = MonoFont,
                    fontSize = 12.sp,
                    fontWeight = if (entry.selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (entry.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                )
            } else {
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = if (entry.selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (entry.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                )
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = if (entry.logoRes != 0) 10.dp else 0.dp),
        )
        if (onEdit != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clickable(onClick = onEdit),
            ) {
                Icon(
                    Icons.Outlined.Edit, L.models.editModelTitle(entry.title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (entry.selected) {
            Icon(
                Icons.Outlined.Check, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        } else if (entry.added) {
            Icon(
                Icons.Outlined.Check, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 弹窗项 logo（Mono 用主题文字色着色、Color 原色；暗色主题可用 logoResDark 变体） */
@Composable
private fun PickerEntryLogo(entry: PickerEntry, size: androidx.compose.ui.unit.Dp) {
    val res = if (LocalPientIsDark.current && entry.logoResDark != 0) {
        entry.logoResDark
    } else entry.logoRes
    val tint = if (entry.monoLogo) ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null
    Image(
        painter = painterResource(res),
        contentDescription = entry.title,
        colorFilter = tint,
        modifier = Modifier.size(size),
    )
}
