package com.pient.app.ui.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pient.app.data.SkillItem
import com.pient.app.ui.components.MarkdownText
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.theme.MonoFont
import com.pient.app.ui.theme.PientPanel

/**
 * 技能详情弹窗（2026-09-06）：
 * ① 技能名称
 * ② 描述（pi-web SkillDetail 同款「Description」小标签 + 值；无描述整块隐藏）
 * ③ 「查看Skill.md」按键（无 SKILL.md 时禁用）→ 点击展开/收起 Markdown 渲染的预览窗口
 * ④ 技能路径
 * ⑤ 目录结构窗口（ASCII 树，等宽字体）
 * ⑥ 底部「删除」+「关闭」。
 * 无右上角 ×（带关闭按钮的弹窗按全局原则不显示 ×）；点 scrim 空白处同样关闭。
 */
@Composable
fun SkillDetailDialog(
    item: SkillItem,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMd by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        PientPanel(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(horizontal = 24.dp)
                .clickable(
                    onClick = {},
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(20.dp)) {
                // ① 技能名称
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFont),
                    color = MaterialTheme.colorScheme.onBackground,
                )

                // ② 描述（pi-web SkillDetail 同款：小标签 + 值，1.6 行高；无描述不显示）
                if (item.desc.isNotBlank()) {
                    Text(
                        "描述",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        item.desc,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.4,
                        ),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                // ③ 查看Skill.md 按键（无文件时禁用）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clickable(enabled = item.skillMd != null) { showMd = !showMd },
                ) {
                    Text(
                        "查看Skill.md",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.skillMd != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Icon(
                        if (showMd) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        "展开/收起",
                        tint = if (item.skillMd != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp),
                    )
                }

                // SKILL.md 预览窗口（Markdown 渲染；点击「查看Skill.md」后出现）
                if (showMd && item.skillMd != null) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                RoundedCornerShape(10.dp),
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(10.dp),
                            ),
                    ) {
                        MarkdownText(
                            markdown = item.skillMd,
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }

                // ④ 技能路径
                Text(
                    "路径",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    skillPath(item),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFont),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp),
                )

                // ⑤ 目录结构（ASCII 树窗口）
                Text(
                    "目录结构",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            RoundedCornerShape(10.dp),
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(10.dp),
                        )
                        .padding(12.dp),
                ) {
                    if (item.fileTree != null) {
                        Text(
                            item.fileTree,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = MonoFont,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    } else {
                        Text(
                            "（无目录信息）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ⑥ 删除 / 关闭
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PientButton("删除", onClick = onDelete, modifier = Modifier.weight(1f))
                    PientButton("关闭", onClick = onDismiss, primary = false, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** 技能目录路径：全局 ~/.pi/agent/skills/<name>/；项目 .pi/skills/<name>/ */
private fun skillPath(item: SkillItem): String =
    if (item.global) "~/.pi/agent/skills/${item.name}/"
    else ".pi/skills/${item.name}/"
