package com.pient.app.ui.skills

import com.pient.app.data.i18n.L
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.components.PientSegmented
import com.pient.app.ui.theme.MonoFont
import android.net.Uri
import android.provider.OpenableColumns

/**
 * 导入技能弹窗（设计计划第 4 章，Operit SkillConfigScreen 参考）：
 * ZIP 导入 / 手动输入两页签；手动 = 名称（frontmatter name：1–64 字符
 * 小写/数字/连字符，非法即校验提示）+ 简介（description ≤1024 必填）+
 * 技能内容（生成 SKILL.md）。导入目标跟随分段（全局/项目）。
 *
 * **2026-09-16 两个页签都是真实现**：ZIP 页签走 SAF 文件选择器 + `PiSkills.importZip` 真解压；
 * 原来的「原型」痕迹（假文件名 `my-skill.zip`、`delay(900)` 假进度、假描述、假附件列表）已删。
 */
@Composable
fun ImportSkillDialog(
    global: Boolean,
    onDismiss: () -> Unit,
    onImported: (name: String, desc: String, skillMd: String?) -> Unit,
    /** ZIP 页签：把选中的 zip 交给屏幕侧做真实导入（IO + 落盘 + 刷新） */
    onImportZip: (uri: Uri) -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var zipUri by remember { mutableStateOf<Uri?>(null) }
    var zipName by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            zipUri = uri
            zipName = zipDisplayName(context, uri)
        }
    }
    val nameRegex = Regex("^[a-z0-9-]{1,64}$")
    val valid = nameRegex.matches(name) && desc.isNotBlank() && desc.length <= 1024

    Box(Modifier.fillMaxSize()) {
        PientDialog(
            title = L.skills.importTitle,
            onDismiss = onDismiss,
            confirmText = L.common.importText,
            confirmEnabled = (tab == 0 && zipUri != null) || (tab == 1 && valid) && !importing,
            showClose = false,
            onConfirm = {
                importing = true
                if (tab == 0) {
                    // 真实导入：交给屏幕侧（IO 线程解压 + 落盘 + 刷新 + Toast）
                    zipUri?.let { onImportZip(it) }
                } else {
                    onImported(name, desc, content.ifBlank { null })
                }
            },
        ) {
            Column(Modifier.padding(top = 12.dp)) {
                PientSegmented(
                    labels = listOf(L.skills.tabZip, L.skills.tabManual),
                    selected = tab,
                    onSelect = { tab = it },
                )
                Text(
                    L.skills.importTarget(if (global) L.skills.importTargetGlobal else L.skills.importTargetProject),
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
                            .clickable {
                                // 真 SAF 选择器（原来这里是 `zipPicked = "my-skill.zip"` 的假动作）
                                zipPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                            }
                            .padding(12.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AttachFile, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            zipName ?: L.skills.pickZip,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (zipName != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Text(
                        "校验结构：压缩包需含 SKILL.md（可整体套一层目录）；导入后落在「${
                            if (global) L.skills.globalPiSkillsPath else L.skills.projectPiSkillsPath
                        }」",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    // 手动输入
                    LabeledField(L.skills.nameLabel, L.skills.nameHint) {
                        BasicTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                nameError = if (nameRegex.matches(it) || it.isEmpty()) null
                                else L.skills.nameInvalid
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
                    LabeledField(L.skills.descLabel, L.skills.descHint) {
                        BasicTextField(
                            value = desc,
                            onValueChange = { desc = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            modifier = Modifier.fieldStyle(),
                        )
                    }
                    LabeledField(L.skills.contentLabel, L.skills.contentHint) {
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
                            L.skills.importing,
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

/** SAF 显示名（DISPLAY_NAME；取不到时回退一个占位，但确认键仍受 zipUri != null 保护） */
private fun zipDisplayName(context: android.content.Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: L.skills.zipChosen
