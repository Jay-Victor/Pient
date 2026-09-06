package com.pient.app.ui.chat

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
import androidx.compose.material.icons.outlined.Stream
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.pient.app.data.AiModel
import com.pient.app.data.ChatState
import com.pient.app.data.MockModels
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
    val providers = MockModels.providers.groupBy { it.provider }
    val screenH = LocalConfiguration.current.screenHeightDp
    var thinkingExpanded by remember { mutableStateOf(false) }
    var outputExpanded by remember { mutableStateOf(false) }
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
        ThinkingModeRow(
            enabled = chatState.thinkingEnabled,
            levelLabel = if (chatState.thinkingEnabled) chatState.thinkingLevel.label else "off",
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
                        "思考模式",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = chatState.thinkingEnabled,
                        onCheckedChange = { chatState.thinkingEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
                if (chatState.thinkingEnabled) {
                    ThinkingLevelSlider(
                        level = chatState.thinkingLevel,
                        onChange = { chatState.thinkingLevel = it },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }

        // ①b 输出折叠栏（流式输出开关，2026-08-27 新增）
        ThinkingModeRow(
            enabled = chatState.streamingOutputEnabled,
            levelLabel = if (chatState.streamingOutputEnabled) "流式" else "off",
            expanded = outputExpanded,
            onClick = { outputExpanded = !outputExpanded },
            icon = Icons.Outlined.Stream,
            title = "输出",
        )
        if (outputExpanded) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "流式输出",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = chatState.streamingOutputEnabled,
                        onCheckedChange = { chatState.streamingOutputEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
                Text(
                    if (chatState.streamingOutputEnabled) "回复逐字流式呈现"
                    else "回复生成完成后整段呈现",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
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
                "模型",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
            Text(
                chatState.selectedModel.name,
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

        // ③ 模型列表（Operit config-row 规格；单选展开）
        LazyColumn(Modifier.heightIn(max = (screenH * 0.40).dp)) {
            providers.forEach { (providerId, models) ->
                val isOpen = providerId == expandedProvider
                val isCurrent = providerId == chatState.selectedModel.provider
                item(key = "h-$providerId") {
                    ProviderHeader(
                        name = MockModels.providerNames[providerId] ?: providerId,
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
                "管理模型配置",
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
    title: String = "思考",
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
                "${models.size}个模型",
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
                models.firstOrNull()?.name ?: "未选择",
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
