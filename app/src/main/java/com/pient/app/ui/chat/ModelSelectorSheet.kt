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
import com.pient.app.data.THINKING_LEVEL_FALLBACK
import com.pient.app.data.ThinkingLevel
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
        // 档位表：pi 对**当前模型**的回答优先；没有（没通道 / 刚切完模型）就用目录/配置本地算。
        // 只有本地也算不出来（目录读不到 + 模型不在配置里）才退回内置表 —— 见 ChatState.selectedThinkingInfo。
        val info = remember(chatState.selectedModel, chatState.piThinkingLevels) {
            chatState.selectedThinkingInfo()
        }
        val levels = piLevels ?: info?.levels
        val effortSupported = info?.effortSupported ?: true
        val piSupportsThinking = levels == null || levels.any { it != "off" }
        // **模型不支持思考 → 整项不出现**（2026-09-17 用户拍板）：pi 只回 `["off"]` 时，折叠栏、
        // 开关、滑轨、说明全都不画 —— 一个用不上的功能项比一条解释更干扰（用户原话：不用出现「思考」一项）。
        // `piLevels == null`（通道没起 / 还没答）时按支持渲染，避免刚开机闪一下消失、或无谓地藏起来。
        // 用户的偏好（`thinkingEnabled` / `thinkingLevel`）照旧保留，切回支持思考的模型自动恢复。
        // 模型**关不掉思考**（pi 的档位表里没有 off，1354 个模型里 334 个）→ 开关置为常开 + 说明，
        // 不能摆一个按下去无效的假开关（用户口径：假控件零容忍）。未知（通道没起）时按可关处理。
        val canDisable = (levels?.contains("off") ?: true) && piSupportsThinking
        val thinkingOn = if (canDisable) chatState.thinkingEnabled && piSupportsThinking else piSupportsThinking
        if (piSupportsThinking) {
            ThinkingModeRow(
                enabled = thinkingOn,
                // 折叠栏显示**生效档位**：pi 的真值优先（它可能把偏好夹到别的档），还没回读就先用偏好；
                // 偏好不在该模型的档位表里时**收敛到最高档**（同展开面板口径：不显示这个模型没有的档位）
                levelLabel = if (thinkingOn) {
                    ThinkingLevel.labelOf(
                        piNow?.takeIf { it != "off" }
                            ?: chatState.thinkingLevel.takeIf { levels == null || it in levels }
                            ?: levels?.lastOrNull { it != "off" }
                            ?: chatState.thinkingLevel
                    )
                } else "off",
                expanded = thinkingExpanded,
                onClick = { thinkingExpanded = !thinkingExpanded },
            )
        }
        if (piSupportsThinking && thinkingExpanded) {
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
                        enabled = canDisable,
                        onCheckedChange = {
                            chatState.thinkingEnabled = it
                            chatState.syncThinkingToPi()
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
                if (!canDisable) {
                    Text(
                        L.chat.thinkingAlwaysOn,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // 到这里必然是「模型支持思考」（不支持时整项不出现）
                if (thinkingOn) {
                    // **档位 = pi 报的可用档位**（2026-09-17 照 pi-web 改）：pi 只列该模型真能用的档，
                    // 界面就画几个停位 —— 不再拿固定五档去等距映射（那会在 `thinkingLevelMap` 砍过档、
                    // 或只有一两档的模型上出现两个停位等价、「拖了没变化」的观感）。
                    // pi 还没答上来（通道没起 / 首次打开）时回退到内置档位表 [THINKING_LEVEL_FALLBACK]。
                    // pi 沉默时用内置回退表；**偏好不在表里也补进去**（如 `max`）—— 否则滑块会默默落到
                    // 首档、与旁边那行档位名对不上（2026-09-17 自查的边角；pi 答了就以 pi 的列表为准，
                    // 那时偏好不在列表里是正常的：pi 会夹取，真值由 `piNow` 显示）
                    val stops = levels?.filter { it != "off" }?.takeIf { it.isNotEmpty() }
                        ?: THINKING_LEVEL_FALLBACK.let { fb ->
                            if (chatState.thinkingLevel in fb) fb else fb + chatState.thinkingLevel
                        }
                    // 选中态：pi 的真值优先（可能已把偏好夹到别的档），其次用户偏好；
                    // **偏好不在该模型的档位表里时收敛到最后一档**（例：偏好 `max` 切到最高只有 `high`
                    // 的模型 —— 否则标签会显示这个模型根本没有的档位，与旁边的说明自相矛盾）。
                    // 这里只影响显示：应用不写偏好，pi 收到越界档位时自己会夹取。
                    val selected = piNow?.takeIf { it != "off" && it in stops }
                        ?: chatState.thinkingLevel.takeIf { it in stops || it == "off" }
                        ?: stops.last()
                    val cfg = chatState.selectedModel?.provider?.let { AiConfigStore.configs[it] }
                    // 真值行（piNow != null）优先；否则给「预计」——估算行只在拿不到 pi 真值时出现。
                    // **pi 专有档位（如 `max`）不在应用枚举里** —— 不能拿 `?: MEDIUM` 兜底：
                    // 那会把「开到最大」说成「预计服务商收到：medium」（实测踩到；DeepSeek V4.1 Flash
                    // 的档位是 low/high/max，medium 只是官方兼容映射，根本不是真档位）。
                    // 应用枚举里没有的档位 → 估算就是档位名原样（与 levelWire 默认分支同口径）。
                    val wire = if (piLevels == null && piNow == null) {
                        cfg?.let { c ->
                            ThinkingLevel.byPi(selected)
                                ?.let { AiBackend.levelWire(c, it) }
                                ?: AiBackend.LevelWire.Word(selected)
                        }
                    } else null
                    if (stops.size <= 1) {
                        // 只有一档：画滑轨也没得选（2026-09-17）—— 如实说一句，不摆死控件
                        Text(
                            ThinkingLevel.labelOf(selected),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            L.chat.thinkingSingleLevel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        ThinkingLevelSlider(
                            levels = stops,
                            selected = selected,
                            onChange = {
                                chatState.thinkingLevel = it
                                chatState.syncThinkingToPi()
                            },
                            enabled = piLevels != null || wire != AiBackend.LevelWire.Unsupported,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    Text(
                        when {
                            // 目录说这个模型的档位不上线（只发开关）→ 如实说，不装成「服务商收到 X」
                            !effortSupported -> L.chat.thinkingEffortNotSent
                            piNow != null -> L.chat.providerReceives(piNow)
                            // 下面两条是**应用侧估算**（pi 没答上来时），措辞用「预计」——旧文案写成
                            // 「服务商实际收到」，读起来像真值（2026-09-17 自查）
                            wire is AiBackend.LevelWire.Word -> L.chat.providerExpected(wire.value)
                            wire is AiBackend.LevelWire.Budget -> L.chat.providerExpected("${wire.tokens} tokens")
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
