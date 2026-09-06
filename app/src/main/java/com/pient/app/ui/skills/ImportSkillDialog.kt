package com.pient.app.ui.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.components.PientSegmented
import com.pient.app.ui.theme.MonoFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 导入技能弹窗（设计计划第 4 章，Operit SkillConfigScreen 参考）：
 * ZIP 导入 / 手动输入两页签；手动 = 名称（frontmatter name：1–64 字符
 * 小写/数字/连字符，非法即校验提示）+ 简介（description ≤1024 必填）+
 * 技能内容（生成 SKILL.md）+ 技能附件列表。导入目标跟随分段（全局/项目）。
 */
@Composable
fun ImportSkillDialog(
    global: Boolean,
    onDismiss: () -> Unit,
    onImported: (name: String, desc: String) -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    val attachments = remember { mutableStateListOf<String>() }
    var importing by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var zipPicked by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val nameRegex = Regex("^[a-z0-9-]{1,64}$")
    val valid = nameRegex.matches(name) && desc.isNotBlank() && desc.length <= 1024

    Box(Modifier.fillMaxSize()) {
        PientDialog(
            title = "导入技能",
            onDismiss = onDismiss,
            confirmText = "导入",
            confirmEnabled = (tab == 0 && zipPicked != null) || (tab == 1 && valid) && !importing,
            showClose = false,
            onConfirm = {
                importing = true
                scope.launch {
                    delay(900) // 原型导入进度
                    if (tab == 0) {
                        val n = zipPicked!!.removeSuffix(".zip")
                        onImported(n, "由 ZIP 导入的技能")
                    } else {
                        onImported(name, desc)
                    }
                }
            },
        ) {
            Column(Modifier.padding(top = 12.dp)) {
                PientSegmented(
                    labels = listOf("ZIP 导入", "手动输入"),
                    selected = tab,
                    onSelect = { tab = it },
                )
                Text(
                    "导入目标：${if (global) "全局 ~/.pi/agent/skills/" else "当前项目 .pi/skills/"}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFont),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )

                if (tab == 0) {
                    // ZIP 导入
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .clickable(onClick = { zipPicked = "my-skill.zip" })
                            .padding(12.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AttachFile, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            zipPicked ?: "选择 .zip 文件（解压导入）",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (zipPicked != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Text(
                        "校验结构：需含 SKILL.md 或技能目录（原型演示）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    // 手动输入
                    LabeledField("技能名称", "1–64 字符 · 小写/数字/连字符（frontmatter name）") {
                        BasicTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                nameError = if (nameRegex.matches(it) || it.isEmpty()) null
                                else "名称仅限小写字母、数字、连字符，1–64 字符"
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = MonoFont,
                                color = MaterialTheme.colorScheme.onBackground,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            modifier = Modifier.fieldStyle(),
                        )
                        if (nameError != null) {
                            Text(
                                nameError!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    LabeledField("技能简介", "必填 · ≤1024 字符（frontmatter description）") {
                        BasicTextField(
                            value = desc,
                            onValueChange = { desc = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            modifier = Modifier.fieldStyle(),
                        )
                    }
                    LabeledField("技能内容", "生成 SKILL.md 正文") {
                        BasicTextField(
                            value = content,
                            onValueChange = { content = it },
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = MonoFont,
                                color = MaterialTheme.colorScheme.onBackground,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier.fieldStyle(),
                        )
                    }
                    LabeledField("技能附件", "多附件列表（可删除）") {
                        attachments.forEachIndexed { i, f ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(f, style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFont), modifier = Modifier.weight(1f))
                                Icon(
                                    Icons.Outlined.Close, "移除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable(onClick = { attachments.removeAt(i) }),
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable(onClick = { attachments += "attachment_${attachments.size + 1}.py" }),
                        ) {
                            Icon(
                                Icons.Outlined.Add, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                "添加附件",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }

                if (importing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            "导入中…（完成后自动刷新列表）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    hint: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(top = 10.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Box(Modifier.padding(top = 4.dp)) { content() }
    }
}

@Composable
private fun Modifier.fieldStyle(): Modifier = this
    .fillMaxWidth()
    .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
    .padding(horizontal = 12.dp, vertical = 10.dp)
