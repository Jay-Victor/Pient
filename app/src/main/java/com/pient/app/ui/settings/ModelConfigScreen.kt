package com.pient.app.ui.settings

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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import com.pient.app.data.ProviderCatalog
import com.pient.app.data.ProviderConfig
import com.pient.app.data.ProviderInfo
import com.pient.app.ui.components.DividerLine
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.components.SectionHeader
import com.pient.app.ui.theme.LocalPientIsDark
import com.pient.app.ui.theme.MonoFont
import com.pient.app.ui.theme.PientPanel
import kotlinx.coroutines.launch

/**
 * 服务商与模型配置（2026-08-30 整体重制；2026-09-09 真实化）：
 * ① 选择服务商卡片：卡内首行标题，第二行服务商展示栏（logo + 名称 + 向下箭头）；
 *    点击弹出选择弹窗（顶部搜索框 + 服务商列表，列表项 = 左侧 logo + 名称）。
 *    服务商清单/名称/默认端点对齐 pi-0.84.2 providers 目录；logo 对齐
 *    pi-web ModelsConfig 的 PROVIDER_ICONS（lobehub icons；Mono 用主题色着色、Color 原色）。
 * ② API设置卡片三块：API端点（输入框默认填入所选服务商端点，可编辑，旁向下箭头
 *    弹窗切换多端点）、API密钥（遮蔽输入）、模型列表（输入框 + 图案按钮弹出
 *    模型选择弹窗：搜索框 + 端点可用模型列表，点选自动填入）。
 * ③ 上下文设置卡片：上下文长度 / 最大输出长度（单位 K Tokens）。
 * ④ 模型参数设置卡片：温度（开关 + 数值）、Top_P / Top_K（开关 + 数值）。
 * 2026-09-09 起全部真实化：配置读写 AiConfigStore（自动持久化）、「测试连接」与
 * 「刷新模型列表」走真实 API（AiBackend.listModels）；成功置 chatState.aiConfigured。
 * 2026-09-10：模型列表弹窗的刷新按钮以「已填 API 密钥」为前置条件——未配置密钥只弹轻提示、
 * 不发起拉取；浮层内的所有反馈改走 Toast（页面上的 testState 被 scrim 遮住看不见）。
 */
@Composable
fun ModelConfigScreen(nav: NavController, chatState: ChatState) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    /** 轻提示：浮层（模型列表弹窗）内触发时用——页面上的 testState 被 scrim 遮住看不见 */
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    // ── 状态 ──
    // 已配置服务商列表（AiConfigStore 持久化；2026-09-09 起无预置——用户自行添加）
    val configuredIds = AiConfigStore.configs.keys.toList()
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
    var keyVisible by remember { mutableStateOf(false) }       // API密钥显隐
    var testState by remember { mutableStateOf<String?>(null) } // 测试连接/刷新反馈
    var refreshing by remember { mutableStateOf(false) }       // 模型列表刷新中

    /** 更新当前服务商配置（写入 AiConfigStore，自动持久化） */
    fun updateConfig(transform: (ProviderConfig) -> ProviderConfig) {
        val id = selectedId ?: return
        val cur = AiConfigStore.configs[id] ?: return
        AiConfigStore.configs[id] = transform(cur)
    }

    /**
     * 新增服务商：加入已配置列表并切换为当前；重复添加则仅切换。
     * 只带入目录里的默认端点 —— **API 密钥与模型列表保持为空**（2026-09-10 用户要求）：
     * 模型列表由用户手动填写或点「刷新」从服务商 /models 端点拉取，不预填任何模型名。
     */
    fun addProvider(id: String) {
        val p = ProviderCatalog.find(id)
        if (id !in AiConfigStore.configs) {
            AiConfigStore.configs[id] = ProviderConfig(
                providerId = id,
                endpoint = p.defaultEndpoint,
            )
        }
        selectedId = id
        testState = null
        providerPickerOpen = false
    }

    /** 删除当前服务商：从已配置列表移除，切换到剩余第一个（可能为空） */
    fun deleteCurrent() {
        val id = selectedId ?: return
        AiConfigStore.configs.remove(id)
        selectedId = AiConfigStore.configs.keys.firstOrNull()
        testState = null
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
            onMessage("请先填写 API 端点")
            return
        }
        if (cur.apiKey.isBlank()) {
            onMessage("请先填写 API 密钥")
            return
        }
        scope.launch {
            try {
                onSuccess(AiBackend.listModels(cur))
            } catch (e: Exception) {
                onMessage("连接失败：${e.message ?: "未知错误"}")
            }
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
                Icons.Outlined.ArrowBack, "返回",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = { nav.popBackStack() }),
            )
            Text(
                "服务商与模型配置",
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
                            "选择服务商",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        ActionChipButton(
                            icon = Icons.Outlined.Add,
                            text = "服务商",
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
                                        "未选择服务商 · 点击上方 +服务商 添加",
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
                                    text = "删除",
                                    danger = true,
                                    onClick = { confirmDeleteOpen = true },
                                )
                                ActionChipButton(
                                    icon = Icons.Outlined.Dns, // 对齐 Operit ModelConfigScreen 测试连接按钮（Icons.Default.Dns）
                                    text = "测试连接",
                                    onClick = {
                                        testState = "测试中…"
                                        callModels { models ->
                                            testState = if (models.isEmpty()) "连接成功（未返回模型）"
                                            else "✓ 连接成功"
                                            // 连接成功 = AI 配置完成（2026-09-08：聊天页首次引导第二步）
                                            chatState.aiConfigured = true
                                        }
                                    },
                                )
                            }
                        }
                        if (testState != null) {
                            Text(
                                testState.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    testState == "✓ 连接成功" || testState == "连接成功（未返回模型）" ->
                                        MaterialTheme.colorScheme.primary
                                    testState?.startsWith("连接失败") == true ||
                                        testState?.startsWith("请先填写") == true ->
                                        MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                            )
                        }
                    }
                }
            }

            // ── ② API设置（仅在已选择服务商时显示） ──
            if (provider != null && cfg != null) {
                item {
                    Column {
                        SectionHeader("API设置", icon = Icons.Outlined.Api)
                        ConfigCard {
                            // 2.1 API端点
                            ConfigFieldLabel("API端点")
                            FieldHint("服务商 API 地址 · 切换服务商后自动填入，可手动修改")
                            EndpointField(
                                value = cfg.endpoint,
                                onValueChange = { v -> updateConfig { it.copy(endpoint = v) } },
                                onOpenPicker = { endpointPickerOpen = true },
                            )
                            DividerLine()
                            // 2.2 API密钥
                            ConfigFieldLabel("API密钥")
                            FieldHint("仅保存在本机 · 用于请求签名，界面不回显")
                            ApiKeyField(
                                value = cfg.apiKey,
                                onValueChange = { v -> updateConfig { it.copy(apiKey = v) } },
                                visible = keyVisible,
                                onToggleVisible = { keyVisible = !keyVisible },
                            )
                            DividerLine()
                            // 2.3 模型列表
                            ConfigFieldLabel("模型列表")
                            FieldHint("多个模型用英文分号 ; 分隔 · 点击右侧按钮批量选择")
                            ModelListField(
                                value = cfg.modelList,
                                onValueChange = { v -> updateConfig { it.copy(modelList = v) } },
                                onOpenPicker = { modelPickerOpen = true },
                            )
                        }
                    }
                }
            }

            // ── ③ 上下文设置（与 API设置 同构：卡外标题行 + 分区块；仅在已选择服务商时显示） ──
            if (provider != null && cfg != null) {
                item {
                    Column {
                        SectionHeader("上下文设置", icon = Icons.Outlined.MenuBook)
                        ConfigCard {
                            // 3.1 上下文长度
                            ConfigFieldLabel("上下文长度")
                            FieldHint("单次会话可用的最大上下文窗口 · 过大可能超出服务商上限")
                            TokenInputField(
                                value = cfg.ctxLenK,
                                onValueChange = { v ->
                                    updateConfig { it.copy(ctxLenK = v.filter { c -> c.isDigit() }) }
                                },
                                placeholder = "200",
                            )
                            DividerLine()
                            // 3.2 最大输出长度
                            ConfigFieldLabel("最大输出长度")
                            FieldHint("单次回复最多生成的 token 数")
                            TokenInputField(
                                value = cfg.maxOutK,
                                onValueChange = { v ->
                                    updateConfig { it.copy(maxOutK = v.filter { c -> c.isDigit() }) }
                                },
                                placeholder = "64",
                            )
                        }
                    }
                }
            }

            // ── ④ 模型参数设置（与 API设置 同构：卡外标题行 + 开关区块；仅在已选择服务商时显示） ──
            if (provider != null && cfg != null) {
                item {
                    Column {
                        SectionHeader("模型参数设置", icon = Icons.Outlined.Tune)
                        ConfigCard {
                            // 4.1 温度
                            ParamBlock(
                                label = "温度（Temperature）",
                                hint = "调节采样随机性 · 关闭时不传该参数",
                                enabled = cfg.tempEnabled,
                                onToggle = { updateConfig { it.copy(tempEnabled = !it.tempEnabled) } },
                            )
                            if (cfg.tempEnabled) {
                                ParamInputField(
                                    value = cfg.tempValue,
                                    onValueChange = { v -> updateConfig { it.copy(tempValue = v) } },
                                    placeholder = "1.0",
                                    rangeHint = "取值范围 0.0~2.0",
                                )
                            }
                            DividerLine()
                            // 4.2 Top-K 采样
                            ParamBlock(
                                label = "Top-K 采样",
                                hint = "只从概率最高的 K 个 token 中采样 · 关闭时不传该参数",
                                enabled = cfg.topKEnabled,
                                onToggle = { updateConfig { it.copy(topKEnabled = !it.topKEnabled) } },
                            )
                            if (cfg.topKEnabled) {
                                ParamInputField(
                                    value = cfg.topKValue,
                                    onValueChange = { v -> updateConfig { it.copy(topKValue = v) } },
                                    placeholder = "0",
                                    rangeHint = "取值范围 0~100（整数）",
                                )
                            }
                            DividerLine()
                            // 4.3 核采样（Top-P 采样）
                            ParamBlock(
                                label = "核采样（Top-P采样）",
                                hint = "从累计概率达到 P 的最小 token 集合中采样 · 关闭时不传该参数",
                                enabled = cfg.topPEnabled,
                                onToggle = { updateConfig { it.copy(topPEnabled = !it.topPEnabled) } },
                            )
                            if (cfg.topPEnabled) {
                                ParamInputField(
                                    value = cfg.topPValue,
                                    onValueChange = { v -> updateConfig { it.copy(topPValue = v) } },
                                    placeholder = "1.0",
                                    rangeHint = "取值范围 0.0~1.0",
                                )
                            } else {
                                // 关闭态：无输入框，补底部间距（与开启态 ParamInputField 的 bottom 10dp 对齐）
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 弹窗：删除二次确认 ──
    if (confirmDeleteOpen) {
        PientDialog(
            title = "删除服务商",
            onDismiss = { confirmDeleteOpen = false },
            confirmText = "删除",
            onConfirm = { deleteCurrent() },
            showClose = false, // 底部已有取消按钮，右上角 × 重复（见 skills-plugins-page-pitfalls）
        ) {
            Text(
                "确定删除 ${provider?.name.orEmpty()} 吗？\n该服务商的 API 密钥与相关配置将一并移除。",
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
            topBarTitle = "服务商选择列表",
            topBarAction = {
                FilterIconButton(onClick = { providerFilterOpen = true })
            },
            searchPlaceholder = "搜索服务商",
            entries = filteredProviderEntries,
            showCancel = true,
            onDismiss = { providerPickerOpen = false },
            onSelect = { e -> addProvider(e.key) },
        )
        // 筛选选项弹窗（全部 / 已添加 / 未添加）
        if (providerFilterOpen) {
            SearchPickerPopup(
                title = "筛选",
                entries = listOf(
                    PickerEntry(key = "all", title = "全部", selected = providerFilter == "all"),
                    PickerEntry(key = "added", title = "已添加", selected = providerFilter == "added"),
                    PickerEntry(key = "unadded", title = "未添加", selected = providerFilter == "unadded"),
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
            title = "API端点",
            entries = endpointEntries,
            onDismiss = { endpointPickerOpen = false },
            onSelect = { e ->
                updateConfig { it.copy(endpoint = e.key) }
                endpointPickerOpen = false
            },
        )
    }

    // ── 弹窗：模型列表（多选 + 底部取消/确定；顶栏刷新 = 真实拉取服务商模型） ──
    if (modelPickerOpen) {
        val modelEntries = (cfg?.models ?: emptyList()).map {
            PickerEntry(key = it, title = it, mono = true, selected = it in (cfg?.modelList?.split(";") ?: emptyList()))
        }
        SearchPickerPopup(
            topBarTitle = "模型选择列表",
            topBarAction = {
                RefreshIconButton(
                    loading = refreshing,
                    onClick = {
                        // 仅在已填入 API 密钥后才发起拉取；未配置则只弹提示、不进 loading 态
                        if (cfg?.apiKey.isNullOrBlank()) {
                            toast("未配置 API 密钥，无法获取模型列表")
                        } else {
                            refreshing = true
                            callModels(
                                onMessage = { msg ->
                                    toast(msg)
                                    refreshing = false // 失败/前置校验未通过：必须结束 loading，否则转圈卡死
                                },
                            ) { models ->
                                if (models.isEmpty()) toast("未获取到模型列表")
                                else updateConfig { it.copy(modelList = models.joinToString(";")) }
                                refreshing = false
                            }
                        }
                    },
                )
            },
            searchPlaceholder = "搜索模型",
            entries = modelEntries,
            multiSelect = true,
            onDismiss = { modelPickerOpen = false },
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

/** 参数区块头：左列标签+辅助说明，右侧开关（M3 默认尺寸，勿 size 压缩） */
@Composable
private fun ParamBlock(
    label: String,
    hint: String,
    enabled: Boolean,
    onToggle: () -> Unit,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = enabled,
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
        InputBox(
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

/** 服务商 logo：Mono 图标用主题文字色着色、Color 图标原色（对齐 pi-web hasColor 语义）；
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
        InputBox(
            value = value,
            onValueChange = onValueChange,
            placeholder = "https://api.example.com/v1",
            modifier = Modifier.weight(1f),
        )
        FieldIconButton(
            icon = { Icons.Outlined.KeyboardArrowDown },
            contentDescription = "切换API端点",
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
        InputBox(
            value = value,
            onValueChange = onValueChange,
            placeholder = "输入 API Key…",
            password = !visible,
            modifier = Modifier.weight(1f),
        )
        FieldIconButton(
            icon = { if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility },
            contentDescription = if (visible) "隐藏密钥" else "显示密钥",
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
        InputBox(
            value = value,
            onValueChange = onValueChange,
            placeholder = "model1;model2（英文分号分隔）",
            modifier = Modifier.weight(1f),
        )
        FieldIconButton(
            icon = { Icons.Outlined.Apps },
            contentDescription = "选择模型",
            onClick = onOpenPicker,
        )
    }
}

/** 通用输入框（surfaceContainerHigh 底 + hairline 边 + 10dp 圆角；suffix 显示在输入框内最右侧） */
@Composable
private fun InputBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    number: Boolean = false,
    suffix: String? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodySmall.copy(
            fontFamily = MonoFont,
            color = MaterialTheme.colorScheme.onBackground,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) else KeyboardOptions.Default,
        singleLine = true,
        modifier = modifier
            .height(38.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFont),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    inner()
                }
                if (suffix != null) {
                    Text(
                        suffix,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFont),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
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
            Icons.Outlined.FilterList, "筛选",
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
                Icons.Outlined.Refresh, "刷新",
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
    InputBox(
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

// ───────────────────────────── ④ 模型参数设置 ─────────────────────────────

// ───────────────────────────── 弹窗 ─────────────────────────────

/**
 * 居中选择浮层（页面内全屏 scrim + 居中卡片，与 PientDialog / 聊天页浮层同构）：
 * 顶部 = 标题（可选）或搜索框；下方 = LazyColumn 列表；点外/返回键关闭。
 *
 * **不用平台 Popup 窗口**（2026-09-10 修）：Popup 是独立窗口，窗口高度取自
 * 「可见显示区 − IME」——键盘抬起时打开浮层，窗口只有 1080×1454px（正常 1080×2274），
 * 于是 fillMaxSize 的 scrim 只铺到键盘上沿、页面下半截没有变暗；且键盘随后收起
 * （Popup focusable 抢走焦点）窗口也不会再长回去（实测 3s 后仍 1454）。
 * 页面内浮层随应用窗口（全屏、不随 IME 缩放）布局，天然不受键盘影响。
 */
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
)

@Composable
private fun SearchPickerPopup(
    entries: List<PickerEntry>,
    onDismiss: () -> Unit,
    searchPlaceholder: String? = null,
    title: String? = null,
    topBarTitle: String? = null,
    topBarAction: (@Composable () -> Unit)? = null,
    showCancel: Boolean = false,                       // 底部"取消"（单选弹窗）
    multiSelect: Boolean = false,                      // 多选模式：底部"取消 + 确定"
    onSelect: (PickerEntry) -> Unit = {},              // 单选回调
    onConfirmMulti: (Set<String>) -> Unit = {},        // 多选确定回调
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
                            "取消",
                            onClick = onDismiss,
                            primary = false,
                            modifier = Modifier.weight(1f),
                        )
                        if (multiSelect) {
                            PientButton(
                                "确定",
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

/** 弹窗顶部搜索框（放大镜 + 输入 + 底部 hairline，pi-web picker 同构） */
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
private fun PickerRow(entry: PickerEntry, onClick: () -> Unit) {
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
