package com.pient.app.data.i18n

/** models 组文案（由 i18n 流水线生成；zh = 源码原文字面量） */
interface ModelsStrings {
    fun connectFailedDetail(a0: Any?): String
    fun usageTotalCostNote(a0: Any?): String
    fun costWan(a0: Any?): String
    val billingPerToken: String
    val billingPerRequest: String
    val connectionOkNoModels: String
    val connectionOk: String
    val apiEndpoint: String
    val filterTitle: String
    val usageChartPerRequest: String
    val usageAllModels: String
    val usageChartCost: String
    val providerCustom: String
    val providerRadius: String
    val needEndpoint: String
    val needApiKey: String
    val selectProvider: String
    val provider: String
    val noProviderSelected: String
    val testConnection: String
    val testing: String
    val connectFailed: String
    val fillFirstPrefix: String
    val apiSettings: String
    val endpointHint: String
    val apiType: String
    val apiTypeHint: String
    fun providerPresetArg(a0: Any?): String
    val apiMatchesPi: String
    val apiOverridesPi: String
    val followProviderPreset: String
    val apiOptionHint: String
    val apiKey: String
    val apiKeyHint: String
    val modelList: String
    val modelListHint: String
    val capabilities: String
    val imageSupport: String
    val imageSupportHint: String
    val audioSupport: String
    val audioSupportHint: String
    val videoSupport: String
    val videoSupportHint: String
    val contextSettings: String
    val contextLength: String
    val contextLengthHint: String
    val maxOutputLength: String
    val maxOutputLengthHint: String
    val thinkingSettings: String
    val thinkingFormat: String
    fun thinkingAutoActive(a0: Any?, a1: Any?): String
    fun thinkingProviderPreset(a0: Any?): String
    val paramSettings: String
    val temperature: String
    val temperatureHint: String
    val temperatureRange: String
    val topK: String
    val topKHint: String
    val topKRange: String
    val topP: String
    val topPHint: String
    val topPRange: String
    val compactionTitle: String
    val compactionHint: String
    val compactionAuto: String
    val compactionAutoHint: String
    val reserveTokens: String
    val reserveTokensHint: String
    val keepRecentTokens: String
    val keepRecentTokensHint: String
    val compactInstructions: String
    val compactInstructionsHint: String
    val compactInstructionsPlaceholder: String
    val deleteProvider: String
    fun deleteProviderConfirm(a0: Any?): String
    val providerPickerTitle: String
    val providerSearch: String
    val filterAdded: String
    val filterNotAdded: String
    val modelPickerTitle: String
    val noApiKey: String
    val noModelsReturned: String
    val modelSearch: String
    val switchEndpoint: String
    val apiKeyPlaceholder: String
    val hideKey: String
    val showKey: String
    val modelListPlaceholder: String
    val selectModel: String
    val usageRangeToday: String
    val usageRangeYesterday: String
    val usageRangeLast7: String
    val usageRangeLast30: String
    val usageRangeWeek: String
    val usageRangeMonth: String
    val usageRangeCustom: String
    val usageSelectRange: String
    val usageTimeRange: String
    val usageAmountSpent: String
    val usageApiRequests: String
    val usageRateSaved: String
    val usageRateInvalid: String
    val usageDateIncomplete: String
    fun usageSelectLabel(a0: Any?): String
    val usageRanking: String
    val usageEditPricingHint: String
    val usageEmpty: String
    val usageTotal: String
    val usageCustomRangeTitle: String
    val usageStartDate: String
    val usageEndDate: String
    val usageSelectDate: String
    fun usageBillingSummaryToken(a0: Any?, a1: Any?, a2: Any?, a3: Any?): String
    fun usageBillingSummaryCount(a0: Any?, a1: Any?): String
    val usageRateTitle: String
    val usageRateHint: String
    val usageRatePlaceholder: String
    fun usageEditPricingTitle(a0: Any?): String
    val usageCurrencyNote: String
    val usageBillingMode: String
    val usageTokenPriceHeader: String
    val usageInputPrice: String
    val usageCachedInputPrice: String
    val usageOutputPrice: String
    val usageRequestPriceHeader: String
    val usageRequestPrice: String
    val usageInvalidPrice: String
}

object ZhModels : ModelsStrings {
    override fun connectFailedDetail(a0: Any?): String = "连接失败：${a0}"
    override fun usageTotalCostNote(a0: Any?): String = "总费用按 1 USD = ${a0} CNY 折算"
    override fun costWan(a0: Any?): String = "¥${a0}万"
    override val billingPerToken: String = "按Token计费"
    override val billingPerRequest: String = "按次计费"
    override val connectionOkNoModels: String = "连接成功（未返回模型）"
    override val connectionOk: String = "✓ 连接成功"
    override val apiEndpoint: String = "API端点"
    override val filterTitle: String = "筛选"
    override val usageChartPerRequest: String = "按次"
    override val usageAllModels: String = "全部模型"
    override val usageChartCost: String = "费用"
    override val providerCustom: String = "自定义（OpenAI / Anthropic 兼容）"
    override val providerRadius: String = "Radius（pi 网关）"
    override val needEndpoint: String = "请先填写 API 端点"
    override val needApiKey: String = "请先填写 API 密钥"
    override val selectProvider: String = "选择服务商"
    override val provider: String = "服务商"
    override val noProviderSelected: String = "未选择服务商 · 点击上方 +服务商 添加"
    override val testConnection: String = "测试连接"
    override val testing: String = "测试中…"
    override val connectFailed: String = "连接失败"
    override val fillFirstPrefix: String = "请先填写"
    override val apiSettings: String = "API设置"
    override val endpointHint: String = "服务商 API 地址 · 切换服务商后自动填入，可手动修改"
    override val apiType: String = "API 类型"
    override val apiTypeHint: String = "pi-ai 的协议类型 · 默认取服务商预设；网关 / 自建端点可在这里改"
    override fun providerPresetArg(a0: Any?): String = "服务商预设（${a0}）"
    override val apiMatchesPi: String = "与 pi 侧该服务商的事实表一致"
    override val apiOverridesPi: String = "已覆盖 pi 侧的事实表（写进 models.json 的 provider.api）"
    override val followProviderPreset: String = "跟随服务商预设"
    override val apiOptionHint: String = "该服务商可用的协议之一"
    override val apiKey: String = "API密钥"
    override val apiKeyHint: String = "仅保存在本机 · 用于请求签名，界面不回显"
    override val modelList: String = "模型列表"
    override val modelListHint: String = "多个模型用英文分号 ; 分隔 · 点击右侧按钮批量选择 · 需要别名时写 模型id=别名（pi 的 models[].name）"
    override val capabilities: String = "模型能力"
    override val imageSupport: String = "模型支持识图"
    override val imageSupportHint: String = "启用后，图片将直接发送给AI处理；关闭时仅发送一行省略占位"
    override val audioSupport: String = "模型支持音频解析"
    override val audioSupportHint: String = "启用后，音频将直接发送给AI处理；关闭时仅发送一行省略占位"
    override val videoSupport: String = "模型支持视频解析"
    override val videoSupportHint: String = "启用后，视频将直接发送给AI处理；关闭时仅发送一行省略占位"
    override val contextSettings: String = "上下文设置"
    override val contextLength: String = "上下文长度"
    override val contextLengthHint: String = "单次会话可用的最大上下文窗口 · 过大可能超出服务商上限"
    override val maxOutputLength: String = "最大输出长度"
    override val maxOutputLengthHint: String = "单次回复最多生成的 token 数"
    override val thinkingSettings: String = "思考设置"
    override val thinkingFormat: String = "思考参数格式"
    override fun thinkingAutoActive(a0: Any?, a1: Any?): String = "自动识别 → 当前生效：${a0}（${a1}）"
    override fun thinkingProviderPreset(a0: Any?): String = "服务商预设：${a0}"
    override val paramSettings: String = "模型参数设置"
    override val temperature: String = "温度（Temperature）"
    override val temperatureHint: String = "调节采样随机性 · 关闭时不传该参数"
    override val temperatureRange: String = "取值范围 0.0~2.0"
    override val topK: String = "Top-K 采样"
    override val topKHint: String = "只从概率最高的 K 个 token 中采样 · 关闭时不传该参数"
    override val topKRange: String = "取值范围 0~100（整数）"
    override val topP: String = "核采样（Top-P采样）"
    override val topPHint: String = "从累计概率达到 P 的最小 token 集合中采样 · 关闭时不传该参数"
    override val topPRange: String = "取值范围 0.0~1.0"
    override val compactionTitle: String = "上下文压缩（全局）"
    override val compactionHint: String = "pi 侧是全局设置（~/.pi/agent/settings.json 的 compaction）· 所有服务商共用 · 改完立即生效"
    override val compactionAuto: String = "自动压缩上下文"
    override val compactionAutoHint: String = "上下文接近上限时由 pi 自动摘要旧内容（关闭后仍可在用量卡手动压缩）"
    override val reserveTokens: String = "为回复预留 Tokens"
    override val reserveTokensHint: String = "已用超过「上下文长度 − 预留」时触发压缩（pi 默认 16384）"
    override val keepRecentTokens: String = "保留最近 Tokens"
    override val keepRecentTokensHint: String = "压缩时最近这一段不摘要、原样保留（pi 默认 20000）"
    override val compactInstructions: String = "压缩指令"
    override val compactInstructionsHint: String = "用量卡点「压缩上下文」时交给 pi 的指令；留空 = pi 默认的 Goal / Progress / Next Steps 口径"
    override val compactInstructionsPlaceholder: String = "例如：重点保留文件路径与命令；忽略寒暄"
    override val deleteProvider: String = "删除服务商"
    override fun deleteProviderConfirm(a0: Any?): String = "确定删除 ${a0} 吗？\n该服务商的 API 密钥与相关配置将一并移除。"
    override val providerPickerTitle: String = "服务商选择列表"
    override val providerSearch: String = "搜索服务商"
    override val filterAdded: String = "已添加"
    override val filterNotAdded: String = "未添加"
    override val modelPickerTitle: String = "模型选择列表"
    override val noApiKey: String = "未配置 API 密钥，无法获取模型列表"
    override val noModelsReturned: String = "未获取到模型列表"
    override val modelSearch: String = "搜索模型"
    override val switchEndpoint: String = "切换API端点"
    override val apiKeyPlaceholder: String = "输入 API Key…"
    override val hideKey: String = "隐藏密钥"
    override val showKey: String = "显示密钥"
    override val modelListPlaceholder: String = "model1;model2（英文分号分隔）"
    override val selectModel: String = "选择模型"
    override val usageRangeToday: String = "今天"
    override val usageRangeYesterday: String = "昨天"
    override val usageRangeLast7: String = "近7天"
    override val usageRangeLast30: String = "近30天"
    override val usageRangeWeek: String = "本周"
    override val usageRangeMonth: String = "本月"
    override val usageRangeCustom: String = "自定义"
    override val usageSelectRange: String = "请选择时间范围"
    override val usageTimeRange: String = "时间维度"
    override val usageAmountSpent: String = "消费金额"
    override val usageApiRequests: String = "API请求次数"
    override val usageRateSaved: String = "汇率已保存"
    override val usageRateInvalid: String = "请输入大于 0 的汇率"
    override val usageDateIncomplete: String = "请选择完整的开始与结束日期"
    override fun usageSelectLabel(a0: Any?): String = "选择${a0}"
    override val usageRanking: String = "用量排行"
    override val usageEditPricingHint: String = " · 点击编辑定价和计费方式"
    override val usageEmpty: String = "所选范围内暂无用量记录 · 对话完成后自动统计"
    override val usageTotal: String = "合计"
    override val usageCustomRangeTitle: String = "自定义时间范围"
    override val usageStartDate: String = "开始日期"
    override val usageEndDate: String = "结束日期"
    override val usageSelectDate: String = "请选择"
    override fun usageBillingSummaryToken(a0: Any?, a1: Any?, a2: Any?, a3: Any?): String = "按Token计费 · 输入 ${a0}${a1}/百万 · 输出 ${a2}${a3}/百万"
    override fun usageBillingSummaryCount(a0: Any?, a1: Any?): String = "按次计费 · 每次 ${a0}${a1}"
    override val usageRateTitle: String = "汇率设置"
    override val usageRateHint: String = "美元计费模型会按此汇率折算为人民币总费用"
    override val usageRatePlaceholder: String = "USD → CNY 汇率"
    override fun usageEditPricingTitle(a0: Any?): String = "编辑模型定价 - ${a0}"
    override val usageCurrencyNote: String = "当前计价币种：CNY"
    override val usageBillingMode: String = "计费方式"
    override val usageTokenPriceHeader: String = "设置每百万Token价格（CNY）"
    override val usageInputPrice: String = "输入价格（每百万Token） (CNY)"
    override val usageCachedInputPrice: String = "缓存输入价格（每百万Token） (CNY)"
    override val usageOutputPrice: String = "输出价格（每百万Token） (CNY)"
    override val usageRequestPriceHeader: String = "设置每次API请求价格（CNY）"
    override val usageRequestPrice: String = "单次请求价格（CNY）"
    override val usageInvalidPrice: String = "请输入有效的非负价格"
}

object EnModels : ModelsStrings {
    override fun connectFailedDetail(a0: Any?): String = "Connection failed: ${a0}"
    override fun usageTotalCostNote(a0: Any?): String = "Total cost converted at 1 USD = ${a0} CNY"
    override fun costWan(a0: Any?): String = "¥${a0}k"
    override val billingPerToken: String = "Per Token"
    override val billingPerRequest: String = "Per Request"
    override val connectionOkNoModels: String = "Connected (no models returned)"
    override val connectionOk: String = "✓ Connected"
    override val apiEndpoint: String = "API endpoint"
    override val filterTitle: String = "Filter"
    override val usageChartPerRequest: String = "Per request"
    override val usageAllModels: String = "All models"
    override val usageChartCost: String = "Cost"
    override val providerCustom: String = "Custom (OpenAI / Anthropic compatible)"
    override val providerRadius: String = "Radius (pi gateway)"
    override val needEndpoint: String = "Enter the API endpoint first"
    override val needApiKey: String = "Enter the API key first"
    override val selectProvider: String = "Select provider"
    override val provider: String = "Provider"
    override val noProviderSelected: String = "No provider selected · Tap +Provider above to add"
    override val testConnection: String = "Test connection"
    override val testing: String = "Testing…"
    override val connectFailed: String = "Connection failed"
    override val fillFirstPrefix: String = "Enter"
    override val apiSettings: String = "API settings"
    override val endpointHint: String = "Provider API URL · Filled in automatically when switching providers; editable manually"
    override val apiType: String = "API type"
    override val apiTypeHint: String = "pi-ai protocol type · Defaults to the provider preset; change it here for gateways or self-hosted endpoints"
    override fun providerPresetArg(a0: Any?): String = "Provider preset (${a0})"
    override val apiMatchesPi: String = "Matches the pi-side fact table for this provider"
    override val apiOverridesPi: String = "Overrides the pi-side fact table (written to provider.api in models.json)"
    override val followProviderPreset: String = "Follow provider preset"
    override val apiOptionHint: String = "One of the APIs available for this provider"
    override val apiKey: String = "API key"
    override val apiKeyHint: String = "Stored on this device only · Used to sign requests, never shown in the UI"
    override val modelList: String = "Model list"
    override val modelListHint: String = "Separate multiple models with a semicolon ; · Tap the button on the right to select in bulk · To set an alias, write model-id=alias (pi's models[].name)"
    override val capabilities: String = "Model capabilities"
    override val imageSupport: String = "Model supports image input"
    override val imageSupportHint: String = "When on, images are sent straight to the AI; when off, only a one-line placeholder is sent"
    override val audioSupport: String = "Model supports audio input"
    override val audioSupportHint: String = "When on, audio is sent straight to the AI; when off, only a one-line placeholder is sent"
    override val videoSupport: String = "Model supports video input"
    override val videoSupportHint: String = "When on, video is sent straight to the AI; when off, only a one-line placeholder is sent"
    override val contextSettings: String = "Context settings"
    override val contextLength: String = "Context length"
    override val contextLengthHint: String = "Max context window per session · Too large a value may exceed the provider limit"
    override val maxOutputLength: String = "Max output length"
    override val maxOutputLengthHint: String = "Max tokens generated per reply"
    override val thinkingSettings: String = "Thinking settings"
    override val thinkingFormat: String = "Thinking parameter format"
    override fun thinkingAutoActive(a0: Any?, a1: Any?): String = "Auto-detect → Currently active: ${a0} (${a1})"
    override fun thinkingProviderPreset(a0: Any?): String = "Provider preset: ${a0}"
    override val paramSettings: String = "Model parameters"
    override val temperature: String = "Temperature"
    override val temperatureHint: String = "Adjusts sampling randomness · Not sent when off"
    override val temperatureRange: String = "Range 0.0~2.0"
    override val topK: String = "Top-K sampling"
    override val topKHint: String = "Sample only from the K most likely tokens · Not sent when off"
    override val topKRange: String = "Range 0~100 (integer)"
    override val topP: String = "Nucleus sampling (Top-P)"
    override val topPHint: String = "Sample from the smallest token set whose cumulative probability reaches P · Not sent when off"
    override val topPRange: String = "Range 0.0~1.0"
    override val compactionTitle: String = "Context compaction (global)"
    override val compactionHint: String = "A global setting on the pi side (compaction in ~/.pi/agent/settings.json) · Shared by all providers · Applied immediately"
    override val compactionAuto: String = "Auto-compact context"
    override val compactionAutoHint: String = "pi summarizes old content automatically as the context nears its limit (manual compaction stays available on the usage card when off)"
    override val reserveTokens: String = "Tokens reserved for the reply"
    override val reserveTokensHint: String = "Compaction triggers when usage passes \"context length − reserved\" (pi default 16384)"
    override val keepRecentTokens: String = "Recent Tokens to keep"
    override val keepRecentTokensHint: String = "The most recent stretch is kept as-is, not summarized, during compaction (pi default 20000)"
    override val compactInstructions: String = "Compaction instructions"
    override val compactInstructionsHint: String = "Instructions passed to pi when you tap \"Compact context\" on the usage card; empty = pi's default Goal / Progress / Next Steps outline"
    override val compactInstructionsPlaceholder: String = "e.g. Keep file paths and commands; ignore small talk"
    override val deleteProvider: String = "Delete provider"
    override fun deleteProviderConfirm(a0: Any?): String = "Delete ${a0}?\nIts API key and related settings will be removed as well."
    override val providerPickerTitle: String = "Provider list"
    override val providerSearch: String = "Search providers"
    override val filterAdded: String = "Added"
    override val filterNotAdded: String = "Not added"
    override val modelPickerTitle: String = "Select models"
    override val noApiKey: String = "No API key configured, cannot fetch the model list"
    override val noModelsReturned: String = "No models returned"
    override val modelSearch: String = "Search models"
    override val switchEndpoint: String = "Switch API endpoint"
    override val apiKeyPlaceholder: String = "Enter API Key…"
    override val hideKey: String = "Hide key"
    override val showKey: String = "Show key"
    override val modelListPlaceholder: String = "model1;model2 (semicolon-separated)"
    override val selectModel: String = "Select model"
    override val usageRangeToday: String = "Today"
    override val usageRangeYesterday: String = "Yesterday"
    override val usageRangeLast7: String = "Last 7 days"
    override val usageRangeLast30: String = "Last 30 days"
    override val usageRangeWeek: String = "This week"
    override val usageRangeMonth: String = "This month"
    override val usageRangeCustom: String = "Custom"
    override val usageSelectRange: String = "Select a time range"
    override val usageTimeRange: String = "Time range"
    override val usageAmountSpent: String = "Amount spent"
    override val usageApiRequests: String = "API requests"
    override val usageRateSaved: String = "Exchange rate saved"
    override val usageRateInvalid: String = "Enter an exchange rate greater than 0"
    override val usageDateIncomplete: String = "Select both a start and an end date"
    override fun usageSelectLabel(a0: Any?): String = "Select ${a0}"
    override val usageRanking: String = "Usage ranking"
    override val usageEditPricingHint: String = " · Tap to edit pricing and billing mode"
    override val usageEmpty: String = "No usage records in the selected range · Counted after each conversation"
    override val usageTotal: String = "Total"
    override val usageCustomRangeTitle: String = "Custom time range"
    override val usageStartDate: String = "Start date"
    override val usageEndDate: String = "End date"
    override val usageSelectDate: String = "Select"
    override fun usageBillingSummaryToken(a0: Any?, a1: Any?, a2: Any?, a3: Any?): String = "Per Token · Input ${a0}${a1}/million · Output ${a0}${a2}/million"
    override fun usageBillingSummaryCount(a0: Any?, a1: Any?): String = "Per Request · ${a0}${a1} each"
    override val usageRateTitle: String = "Exchange rate"
    override val usageRateHint: String = "USD-priced models are converted to CNY at this rate for the total cost"
    override val usageRatePlaceholder: String = "USD → CNY rate"
    override fun usageEditPricingTitle(a0: Any?): String = "Edit model pricing - ${a0}"
    override val usageCurrencyNote: String = "Pricing currency: CNY"
    override val usageBillingMode: String = "Billing mode"
    override val usageTokenPriceHeader: String = "Set the price per million Tokens (CNY)"
    override val usageInputPrice: String = "Input price (per million Tokens) (CNY)"
    override val usageCachedInputPrice: String = "Cached input price (per million Tokens) (CNY)"
    override val usageOutputPrice: String = "Output price (per million Tokens) (CNY)"
    override val usageRequestPriceHeader: String = "Set the price per API request (CNY)"
    override val usageRequestPrice: String = "Price per request (CNY)"
    override val usageInvalidPrice: String = "Enter a valid non-negative price"
}
