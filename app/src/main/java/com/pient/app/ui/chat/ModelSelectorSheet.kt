package com.pient.app.ui.chat

import com.pient.app.data.i18n.L
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pient.app.data.AiBackend
import com.pient.app.data.AiConfigStore
import com.pient.app.data.AiModel
import com.pient.app.data.ChatState
import com.pient.app.data.ProviderCatalog
import com.pient.app.ui.components.ThinkingLevelSlider
import com.pient.app.ui.theme.PientPanel

/**
 * 模型选择器浮层（设计计划 3.4.1；2026-08-27 按 Operit 重构 + 三轮优化）：
 * ① 思考折叠栏（Operit ClassicThinkingSettingsItem 风格）：
 *    折叠态 = 图标 + "思考" + 思考程度名称（最低/低/中/高/最高；关闭显示 off）+ 折叠箭头；
 *    点击展开 → "思考模式"开关行，开启后出现思考程度滑块
 *    （标题行：思考程度 + 档位名；滑道加粗并带五档指示点，最低/最高位于两端；2026-08-27 重设计）。
 * ② Operit 风格头部："模型: 当前模型"（粗体主色，ellipsis）
 * ③ 模型列表（Operit config-row 规格：13sp 行高 34dp 圆角 4dp；选中 = 主色 15%
 *    底 + 粗体主色字；**一次只展开一个服务商**；展开列表左缩进 12dp 且左右边距对称，
 *    无底色块；卡片定宽 268.8dp（336dp 的 4/5）、贴屏幕右侧（右距屏 6dp）；边距体系收紧
 *    （列 12dp / 行 8dp / 展开区 12dp / 列表块 12dp），内容仍居卡片中央）
 * ④ "管理模型配置"（跳转设置页模型配置，Operit manage-button 同语义）
 * 选择即时生效；点外关闭（无"完成"按钮）。最大高 60% 屏。
 */
@Composable
fun ModelSelectorSheet(
    chatState: ChatState,
    onClose: () -> Unit,
    onManageModels: () -> Unit = {},
    modifier: Modifier = Modifier,
    bottomOffset: Dp = 8.dp, // 弹窗底部到屏幕底的距离（锚定到模型按键上缘）
) {
    // 模型数据源（2026-09-09 起）= 已配置服务商的模型列表（AiConfigStore）
    val providers = chatState.availableModels.groupBy { it.provider }
    val screenH = LocalConfiguration.current.screenHeightDp
    var thinkingExpanded by remember { mutableStateOf(false) }
    // 单选展开：一次只展开一个服务商（用户决策 2026-08-27）；默认全部收起（2026-08-30）
    var expandedProvider by remember { mutableStateOf<String?>(null) }

    PientPanel(
        modifier = modifier
            .padding(end = 6.dp, bottom = bottomOffset)
            .width(268.8.dp), // 336dp 的 4/5；靠右、右距屏 6dp
        shape = RoundedCornerShape(16.dp),
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth() // 高度 wrap，避免撑满父级
            .padding(12.dp),
    ) {
        // ① 思考折叠栏（Operit ClassicThinkingSettingsItem 风格）
        // 2026-09-17：思考参数由 **pi** 发出去，所以这里的开关/滑轨必须推到 pi 才算数。
        // 打开面板 = 把界面偏好推给 pi 并回读（pi 会按模型能力夹取）；通道没起时自动跳过。
        LaunchedEffect(Unit) { chatState.syncThinkingToPi() }
        val piLevels = chatState.piThinkingLevels
        val piNow = chatState.piThinkingLevel
        val piSupportsThinking = piLevels == null || piLevels.any { it != "off" }
        // **生效态 = 用户偏好 && 模型支持**：模型不支持思考时按「关」渲染，但**不覆写用户的偏好**
        // （否则切到不支持思考的模型再切回来，用户原本开着的开关会被静默关掉）
        val thinkingOn = chatState.thinkingEnabled && piSupportsThinking
        ThinkingModeRow(
            enabled = thinkingOn,
            levelLabel = if (thinkingOn) chatState.thinkingLevel.label else "off",
            expanded = thinkingExpanded,
            onClick = { thinkingExpanded = !thinkingExpanded },
        )
        if (thinkingExpanded) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        L.chat.thinkingMode,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = thinkingOn,
                        // 模型不支持思考（pi 只给 off）→ 开关不可点，不摆假控件
                        enabled = piSupportsThinking,
                        onCheckedChange = {
                            chatState.thinkingEnabled = it
                            chatState.syncThinkingToPi()
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
                // 模型不支持思考（pi 只给 off）→ 说明**常显**：开关按「关」渲染（`thinkingOn`），
                // 不能只让用户看到「开关没打开」而不知道原因。
                if (!piSupportsThinking) {
                    Text(
                        L.chat.thinkingUnsupportedModel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else if (chatState.thinkingEnabled) {
                    // 档位判据：**以 pi 为准**（`get_available_thinking_levels` + `get_state`）；
                    // pi 还没答上来（通道没起/首次）才回落到旧的 AiBackend.levelWire 估算。
                    val cfg = chatState.selectedModel?.provider?.let { AiConfigStore.configs[it] }
                    val wire = if (piLevels == null) cfg?.let { AiBackend.levelWire(it, chatState.thinkingLevel) } else null
                    ThinkingLevelSlider(
                        level = chatState.thinkingLevel,
                        onChange = {
                            chatState.thinkingLevel = it
                            chatState.syncThinkingToPi()
                        },
                        enabled = wire != AiBackend.LevelWire.Unsupported,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        when {
                            piNow != null -> L.chat.providerReceives(piNow)
                            wire is AiBackend.LevelWire.Word -> L.chat.providerReceives(wire.value)
                            wire is AiBackend.LevelWire.Budget -> L.chat.thinkingBudget(wire.tokens)
                            else -> L.chat.levelUnsupported
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ② Operit 风格头部：图标 + "模型" + 当前模型名（粗体主色）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            Icon(
                Icons.Outlined.SmartToy, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                L.common.model,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
            Text(
                chatState.selectedModel?.name ?: L.chat.noModelSelected,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        // ③ 模型列表（Operit config-row 规格；单选展开；无已配置模型时提示引导）
        if (providers.isEmpty()) {
            Text(
                L.chat.noModelsAvailable,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        } else {
        LazyColumn(Modifier.heightIn(max = (screenH * 0.40).dp)) {
            providers.forEach { (providerId, models) ->
                val isOpen = providerId == expandedProvider
                val isCurrent = providerId == chatState.selectedModel?.provider
                item(key = "h-$providerId") {
                    ProviderHeader(
                        name = ProviderCatalog.find(providerId).name,
                        models = models,
                        isOpen = isOpen,
                        isCurrent = isCurrent,
                        onClick = {
                            expandedProvider =
                                if (expandedProvider == providerId) null else providerId
                        },
                    )
                }
                if (isOpen) {
                    item(key = "l-$providerId") {
                        ModelListBlock(models, chatState)
                    }
                }
            }
        }
        }

        // ④ 管理模型配置（Operit manage-button 同语义）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clickable(onClick = onManageModels)
                .padding(horizontal = 8.dp),
        ) {
            Text(
                L.chat.manageModels,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    }
}

/**
 * 折叠栏（Operit ClassicThinkingSettingsItem 折叠态规格）：
 * 图标（启用主色/关闭弱化）+ 标题 + 状态名（粗体主色/off 弱化）+ 折叠箭头。
 * 思考栏与输出栏共用（图标参数化）。
 */
@Composable
private fun ThinkingModeRow(
    enabled: Boolean,
    levelLabel: String,
    expanded: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.Psychology,
    title: String = L.chat.thinking,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(
            icon, null,
            tint = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            levelLabel,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(18.dp),
        )
    }
}

/**
 * 服务商行（Operit model-selector-config-row 规格）：
 * min-height 34dp · padding 6/8dp · 圆角 4dp · 名称 13sp ellipsis；
 * 多模型尾部 "N个模型"（11sp 弱化）+ 折叠箭头，单模型直接显示模型名；
 * 当前服务商：主色 15% 底 + 粗体主色名。
 */
@Composable
private fun ProviderHeader(
    name: String,
    models: List<AiModel>,
    isOpen: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else Color.Transparent,
                RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (models.size > 1) {
            Text(
                L.chat.modelCount(models.size),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                if (isOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Text(
                models.firstOrNull()?.name ?: L.chat.notSelected,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp),
            )
        }
    }
}

/**
 * 展开的模型列表块（Operit model-selector-model-list 规格）：
 * 左右边距对称（16+16+8 = 40dp）；左缩进相对头部行形成层级。
 * 原"表面变体底"已移除：暗色下呈纯黑块、亮色下与面板同色不可见，无分组价值。
 */
@Composable
private fun ModelListBlock(models: List<AiModel>, chatState: ChatState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        models.forEach { model ->
            val selected = model.id == chatState.selectedModelId
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 34.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else Color.Transparent,
                        RoundedCornerShape(4.dp),
                    )
                    .clickable(onClick = { chatState.selectedModelId = model.id })
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    model.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
