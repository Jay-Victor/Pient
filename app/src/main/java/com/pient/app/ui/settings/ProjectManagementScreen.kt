package com.pient.app.ui.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.data.ChatState
import com.pient.app.data.ProjectFiles
import com.pient.app.data.Session
import com.pient.app.ui.components.DetailRow
import com.pient.app.ui.components.DividerLine
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.components.SectionHeader
import com.pient.app.ui.components.computeProjectInfo
import java.io.File

/**
 * 项目管理设置（2026-09-03 制作并二版迭代；原设置页「项目记录管理」占位行接入）：
 * 两组「图案 + 标题 + 卡片」：
 * - 项目记录：搜索框（placeholder 说明）+ 全部项目列表（分隔线分隔，点击单选高亮；
 *   行尾竖直三点菜单 = 详细信息 / 重命名 / 解绑 / 删除，语义与聊天页侧边栏项目菜单一致）；
 * - 会话记录：搜索框（placeholder 说明）+ 「已选择 0/N 条」/「全选」「取消」（取消仅在
 *   已选择后亮蓝）+ 全部会话列表（跨项目，圆形选择框，选中圆内 √；行尾三点菜单 = 重命名 / 删除）+
 *   选中后底部出现「操作已选会话（N）」→ 弹窗（导出会话 / 删除会话 选择项 + 取消 / 确定）。
 *   导出为原型占位 Toast；删除真实执行。
 */
@Composable
fun ProjectManagementScreen(nav: NavController, chatState: ChatState) {
    val context = LocalContext.current
    var projectQuery by remember { mutableStateOf("") }
    var sessionQuery by remember { mutableStateOf("") }
    var selectedProject by remember { mutableStateOf<String?>(null) }
    val selectedSessions = remember { mutableStateListOf<String>() }
    var actionDialogOpen by remember { mutableStateOf(false) }
    var actionChoice by remember { mutableStateOf<Int?>(null) } // 0 = 导出会话；1 = 删除会话

    // ── 项目行三点菜单 / 弹窗状态 ──
    var projectMenuFor by remember { mutableStateOf<String?>(null) }
    var projectDetailFor by remember { mutableStateOf<String?>(null) }
    var projectRenameFor by remember { mutableStateOf<String?>(null) }
    var projectUnbindConfirmFor by remember { mutableStateOf<String?>(null) }
    var projectDeleteConfirmFor by remember { mutableStateOf<String?>(null) }

    // ── 会话行三点菜单 / 弹窗状态 ──
    var sessionMenuFor by remember { mutableStateOf<String?>(null) }
    var sessionRenameFor by remember { mutableStateOf<String?>(null) }
    var sessionDeleteConfirmFor by remember { mutableStateOf<String?>(null) }

    // ── 已解绑项目组（2026-09-03 新增）：files/Projects/ 下不在项目列表中的目录 ──
    var unbindQuery by remember { mutableStateOf("") }
    val selectedUnbound = remember { mutableStateListOf<String>() } // 目录绝对路径
    var unboundMenuFor by remember { mutableStateOf<String?>(null) }
    var unboundRenameFor by remember { mutableStateOf<String?>(null) }
    var unboundDeleteConfirmFor by remember { mutableStateOf<String?>(null) }
    var unbindTick by remember { mutableIntStateOf(0) } // 绑定/重命名/删除后刷新目录列表
    // 已解绑组批量操作弹窗（2026-09-03 追加）
    var unboundActionDialogOpen by remember { mutableStateOf(false) }
    var unboundActionChoice by remember { mutableStateOf<Int?>(null) } // 0 = 重新绑定；1 = 删除

    // 项目记录列表（按名称过滤）
    val projects = chatState.projects.filter {
        it.name.contains(projectQuery.trim(), ignoreCase = true)
    }
    // 会话记录列表（按标题过滤；选中项目记录时仅显示该项目会话，未选中显示全部）
    val allSessions = chatState.sessions.values.flatten()
    val sessions = allSessions.filter {
        (selectedProject == null || it.project == selectedProject) &&
            it.title.contains(sessionQuery.trim(), ignoreCase = true)
    }

    // 已解绑项目（2026-09-03 新增）：files/Projects/ 下不在项目列表中的目录。
    // 已绑定判定 = 本地项目（uri=null）path 与目录绝对路径精确匹配；SAF 项目不落此目录。
    val projectsDir = File(context.filesDir, "Projects")
    val unboundDirs = (projectsDir.listFiles()?.filter { it.isDirectory } ?: emptyList())
        .filter { d -> chatState.projects.none { p -> p.uri == null && p.path == d.absolutePath } }
        .sortedBy { it.name }
        .filter { it.name.contains(unbindQuery.trim(), ignoreCase = true) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ── 顶栏 ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Icon(
                    Icons.Outlined.ArrowBack, "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = { nav.popBackStack() }),
                )
                Text(
                    "项目管理设置",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
            ) {
                // ── 项目记录 ──
                SectionHeader("项目记录", icon = Icons.Outlined.Folder)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                ) {
                    // 搜索框
                    RecordSearchField(
                        value = projectQuery,
                        onValueChange = { projectQuery = it },
                        placeholder = "搜索项目名称",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                    // 项目列表（2026-09-03：搜索框下方分隔线已移除；
                    // 2026-09-03 高度上限 280dp 防记录增多无限延伸，超出后卡片内滚动）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (projects.isEmpty()) {
                            Text(
                                "无匹配项目",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            )
                        } else {
                            projects.forEachIndexed { i, p ->
                                ProjectRecordRow(
                                    name = p.name,
                                    path = p.path,
                                    selected = p.name == selectedProject,
                                    menuExpanded = projectMenuFor == p.name,
                                    canRemove = chatState.projects.isNotEmpty(),
                                    onClick = {
                                        selectedProject = if (selectedProject == p.name) null else p.name
                                    },
                                    onMenuToggle = {
                                        projectMenuFor = if (projectMenuFor == p.name) null else p.name
                                    },
                                    onMenuDismiss = { projectMenuFor = null },
                                    onMenuDetail = {
                                        projectMenuFor = null
                                        projectDetailFor = p.name
                                    },
                                    onMenuRename = {
                                        projectMenuFor = null
                                        projectRenameFor = p.name
                                    },
                                    onMenuUnbind = {
                                        projectMenuFor = null
                                        projectUnbindConfirmFor = p.name
                                    },
                                    onMenuDelete = {
                                        projectMenuFor = null
                                        projectDeleteConfirmFor = p.name
                                    },
                                )
                                if (i != projects.lastIndex) DividerLine()
                            }
                        }
                    }
                }

                Spacer(Modifier.heightIn(min = 8.dp))

                // ── 会话记录 ──
                SectionHeader("会话记录", icon = Icons.Outlined.Chat)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                ) {
                    // 搜索框
                    RecordSearchField(
                        value = sessionQuery,
                        onValueChange = { sessionQuery = it },
                        placeholder = "搜索会话标题",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                    // 「已选择 0/N 条」+ 全选 / 取消（取消仅在已选择后亮蓝，2026-09-03；
                    // 搜索框下方与已选择行下方两条分隔线均已移除）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "已选择 ${selectedSessions.size}/${sessions.size} 条",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "全选",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (sessions.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable(enabled = sessions.isNotEmpty()) {
                                    selectedSessions.clear()
                                    sessions.forEach { selectedSessions.add(it.id) }
                                }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "取消",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selectedSessions.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable(enabled = selectedSessions.isNotEmpty()) {
                                    selectedSessions.clear()
                                }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                    // 会话列表（2026-09-03：「已选择」行下方分隔线已移除；
                    // 2026-09-03 高度上限 340dp 防记录增多无限延伸，超出后卡片内滚动）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (sessions.isEmpty()) {
                            Text(
                                "无匹配会话",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            )
                        } else {
                            sessions.forEachIndexed { i, s ->
                                SessionRecordRow(
                                    session = s,
                                    selected = s.id in selectedSessions,
                                    menuExpanded = sessionMenuFor == s.id,
                                    onClick = {
                                        if (s.id in selectedSessions) selectedSessions.remove(s.id)
                                        else selectedSessions.add(s.id)
                                    },
                                    onMenuToggle = {
                                        sessionMenuFor = if (sessionMenuFor == s.id) null else s.id
                                    },
                                    onMenuDismiss = { sessionMenuFor = null },
                                    onMenuRename = {
                                        sessionMenuFor = null
                                        sessionRenameFor = s.id
                                    },
                                    onMenuDelete = {
                                        sessionMenuFor = null
                                        sessionDeleteConfirmFor = s.id
                                    },
                                )
                                if (i != sessions.lastIndex) DividerLine()
                            }
                        }
                    }
                    // 选中后底部出现操作按键
                    AnimatedVisibility(visible = selectedSessions.isNotEmpty()) {
                        PientButton(
                            "操作已选会话（${selectedSessions.size}）",
                            onClick = {
                                actionChoice = null
                                actionDialogOpen = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        )
                    }
                }

                Spacer(Modifier.heightIn(min = 8.dp))

                // ── 已解绑项目（2026-09-03 新增：files/Projects/ 下已移出项目列表的目录） ──
                SectionHeader("已解绑项目", icon = Icons.Outlined.LinkOff)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                ) {
                    // 搜索框
                    RecordSearchField(
                        value = unbindQuery,
                        onValueChange = { unbindQuery = it },
                        placeholder = "搜索已解绑项目",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                    // 「已选择 0/N 条」+ 全选 / 取消（取消仅在已选择后亮蓝）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "已选择 ${selectedUnbound.size}/${unboundDirs.size} 条",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "全选",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (unboundDirs.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable(enabled = unboundDirs.isNotEmpty()) {
                                    selectedUnbound.clear()
                                    unboundDirs.forEach { selectedUnbound.add(it.absolutePath) }
                                }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "取消",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selectedUnbound.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable(enabled = selectedUnbound.isNotEmpty()) {
                                    selectedUnbound.clear()
                                }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                    // 已解绑项目列表（2026-09-03 高度上限 340dp 防记录增多无限延伸，超出后卡片内滚动）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (unboundDirs.isEmpty()) {
                            Text(
                                "无已解绑项目",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            )
                        } else {
                            unboundDirs.forEachIndexed { i, d ->
                                UnboundProjectRow(
                                    name = d.name,
                                    selected = d.absolutePath in selectedUnbound,
                                    menuExpanded = unboundMenuFor == d.absolutePath,
                                    onClick = {
                                        if (d.absolutePath in selectedUnbound) selectedUnbound.remove(d.absolutePath)
                                        else selectedUnbound.add(d.absolutePath)
                                    },
                                    onMenuToggle = {
                                        unboundMenuFor = if (unboundMenuFor == d.absolutePath) null else d.absolutePath
                                    },
                                    onMenuDismiss = { unboundMenuFor = null },
                                    onRebind = {
                                        unboundMenuFor = null
                                        if (chatState.addProject(d.name, d.absolutePath)) {
                                            Toast.makeText(context, "已重新绑定项目「${d.name}」", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "已存在同名项目", Toast.LENGTH_SHORT).show()
                                        }
                                        selectedUnbound.remove(d.absolutePath)
                                        unbindTick++
                                    },
                                    onRename = {
                                        unboundMenuFor = null
                                        unboundRenameFor = d.absolutePath
                                    },
                                    onDelete = {
                                        unboundMenuFor = null
                                        unboundDeleteConfirmFor = d.absolutePath
                                    },
                                )
                                if (i != unboundDirs.lastIndex) DividerLine()
                            }
                        }
                    }
                    // 选中后底部出现操作按键（2026-09-03 追加）
                    AnimatedVisibility(visible = selectedUnbound.isNotEmpty()) {
                        PientButton(
                            "操作已选项目（${selectedUnbound.size}）",
                            onClick = {
                                unboundActionChoice = null
                                unboundActionDialogOpen = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }

        // ── 操作弹窗：导出 / 删除 选择项 + 取消 / 确定 ──
        if (actionDialogOpen) {
            PientDialog(
                title = "操作已选会话",
                onDismiss = { actionDialogOpen = false },
                confirmText = "确定",
                confirmEnabled = actionChoice != null,
                showClose = false,
                onConfirm = {
                    actionDialogOpen = false
                    when (actionChoice) {
                        0 -> Toast.makeText(
                            context,
                            "已导出 ${selectedSessions.size} 个会话（原型占位）",
                            Toast.LENGTH_SHORT,
                        ).show()
                        1 -> {
                            selectedSessions.toList().forEach { chatState.deleteSessionById(it) }
                            Toast.makeText(
                                context,
                                "已删除 ${selectedSessions.size} 个会话",
                                Toast.LENGTH_SHORT,
                            ).show()
                            selectedSessions.clear()
                        }
                    }
                    actionChoice = null
                },
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionChoiceRow(
                        icon = Icons.Outlined.FileDownload,
                        label = "导出会话",
                        selected = actionChoice == 0,
                        onClick = { actionChoice = 0 },
                    )
                    ActionChoiceRow(
                        icon = Icons.Outlined.Delete,
                        label = "删除会话",
                        selected = actionChoice == 1,
                        danger = true,
                        onClick = { actionChoice = 1 },
                    )
                }
            }
        }

        // ── 项目行三点菜单 / 会话行三点菜单：锚定在各自行内三点 Box 中渲染（见行组件） ──

        // ── 项目详细信息弹窗 ──
        projectDetailFor?.let { name ->
            val project = chatState.projects.firstOrNull { it.name == name } ?: return@let
            val info = remember(project.path, project.uri) { computeProjectInfo(context, project) }
            Box(Modifier.fillMaxSize()) {
                PientDialog(
                    title = "详细信息",
                    onDismiss = { projectDetailFor = null },
                    onConfirm = { projectDetailFor = null },
                    showClose = false,
                    showCancel = false,
                ) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        DetailRow("位置", project.path)
                        DetailRow("大小", info.size)
                        DetailRow("修改时间", info.modified)
                    }
                }
            }
        }

        // ── 项目重命名弹窗 ──
        projectRenameFor?.let { name ->
            var newName by remember(name) { mutableStateOf(name) }
            Box(Modifier.fillMaxSize()) {
                PientDialog(
                    title = "重命名项目",
                    onDismiss = { projectRenameFor = null },
                    confirmText = "保存",
                    showClose = false,
                    confirmEnabled = newName.isNotBlank() &&
                        (newName.trim() == name || chatState.projects.none { it.name == newName.trim() }),
                    onConfirm = {
                        chatState.renameProject(name, newName)
                        projectRenameFor = null
                    },
                ) {
                    BasicTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                    )
                }
            }
        }

        // ── 项目解绑确认弹窗（移出列表 + 删除会话记录，保留文件夹及文件） ──
        projectUnbindConfirmFor?.let { name ->
            Box(Modifier.fillMaxSize()) {
                PientDialog(
                    title = "解绑项目",
                    onDismiss = { projectUnbindConfirmFor = null },
                    confirmText = "解绑",
                    showClose = false,
                    onConfirm = {
                        chatState.removeProject(name)
                        projectUnbindConfirmFor = null
                        if (selectedProject == name) selectedProject = null
                    },
                ) {
                    Text(
                        "确定要解绑项目「$name」吗？其下 ${chatState.sessionsFor(name).size} 个会话记录将一并删除，项目文件夹及其中文件不受影响。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }

        // ── 项目删除确认弹窗（删除文件夹及其中所有文件 + 会话记录） ──
        projectDeleteConfirmFor?.let { name ->
            val project = chatState.projects.firstOrNull { it.name == name }
            Box(Modifier.fillMaxSize()) {
                PientDialog(
                    title = "删除项目",
                    onDismiss = { projectDeleteConfirmFor = null },
                    confirmText = "删除",
                    showClose = false,
                    onConfirm = {
                        val folderRemoved = project == null || ProjectFiles.deleteProjectRoot(context, project)
                        if (folderRemoved) {
                            chatState.removeProject(name)
                            projectDeleteConfirmFor = null
                            if (selectedProject == name) selectedProject = null
                        } else {
                            Toast.makeText(context, "项目文件夹删除失败", Toast.LENGTH_SHORT).show()
                            projectDeleteConfirmFor = null
                        }
                    },
                ) {
                    Text(
                        "确定要删除项目「$name」吗？项目文件夹及其中所有文件、其下 ${chatState.sessionsFor(name).size} 个会话记录将一并删除。此操作不可撤销。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }

        // ── 会话重命名弹窗 ──
        sessionRenameFor?.let { id ->
            val session = allSessions.firstOrNull { it.id == id } ?: return@let
            var newName by remember(id) { mutableStateOf(session.title) }
            Box(Modifier.fillMaxSize()) {
                PientDialog(
                    title = "重命名会话",
                    onDismiss = { sessionRenameFor = null },
                    confirmText = "保存",
                    showClose = false,
                    confirmEnabled = newName.isNotBlank(),
                    onConfirm = {
                        chatState.renameSession(id, newName)
                        sessionRenameFor = null
                    },
                ) {
                    BasicTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                    )
                }
            }
        }

        // ── 会话删除确认弹窗 ──
        sessionDeleteConfirmFor?.let { id ->
            val session = allSessions.firstOrNull { it.id == id } ?: return@let
            Box(Modifier.fillMaxSize()) {
                PientDialog(
                    title = "删除会话",
                    onDismiss = { sessionDeleteConfirmFor = null },
                    confirmText = "删除",
                    showClose = false,
                    onConfirm = {
                        chatState.deleteSessionById(id)
                        selectedSessions.remove(id)
                        sessionDeleteConfirmFor = null
                    },
                ) {
                    Text(
                        "确定要删除会话「${session.title}」吗？此操作不可撤销。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }

        // ── 已解绑项目批量操作弹窗（2026-09-03 追加）：重新绑定 / 删除 选择项 + 取消 / 确定 ──
        if (unboundActionDialogOpen) {
            PientDialog(
                title = "操作已选项目",
                onDismiss = { unboundActionDialogOpen = false },
                confirmText = "确定",
                confirmEnabled = unboundActionChoice != null,
                showClose = false,
                onConfirm = {
                    unboundActionDialogOpen = false
                    val targets = selectedUnbound.toList()
                    when (unboundActionChoice) {
                        0 -> {
                            // 批量重新绑定：重名跳过（addProject 返回 false）
                            var ok = 0
                            targets.forEach { path ->
                                val d = File(path)
                                if (chatState.addProject(d.name, d.absolutePath)) ok++
                            }
                            Toast.makeText(
                                context,
                                "已重新绑定 $ok/${targets.size} 个项目",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        1 -> {
                            // 批量彻底删除：文件夹及其中所有文件
                            var ok = 0
                            targets.forEach { path ->
                                val d = File(path)
                                if (!d.exists() || d.deleteRecursively()) ok++
                            }
                            Toast.makeText(
                                context,
                                "已删除 $ok/${targets.size} 个项目",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    selectedUnbound.clear()
                    unboundActionChoice = null
                    unbindTick++
                },
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionChoiceRow(
                        icon = Icons.Outlined.Link,
                        label = "重新绑定",
                        selected = unboundActionChoice == 0,
                        onClick = { unboundActionChoice = 0 },
                    )
                    ActionChoiceRow(
                        icon = Icons.Outlined.Delete,
                        label = "删除",
                        selected = unboundActionChoice == 1,
                        danger = true,
                        onClick = { unboundActionChoice = 1 },
                    )
                }
            }
        }

        // ── 已解绑项目重命名弹窗 ──
        unboundRenameFor?.let { path ->
            val dir = File(path)
            var newName by remember(path) { mutableStateOf(dir.name) }
            val nameTaken = newName.trim() != dir.name && (
                File(projectsDir, newName.trim()).exists() ||
                    chatState.projects.any { it.name == newName.trim() }
                )
            Box(Modifier.fillMaxSize()) {
                PientDialog(
                    title = "重命名项目",
                    onDismiss = { unboundRenameFor = null },
                    confirmText = "保存",
                    showClose = false,
                    confirmEnabled = newName.isNotBlank() && !newName.contains('/') && !nameTaken,
                    onConfirm = {
                        val target = File(projectsDir, newName.trim())
                        if (dir.renameTo(target)) {
                            selectedUnbound.remove(path)
                            unboundRenameFor = null
                            unbindTick++
                        } else {
                            Toast.makeText(context, "重命名失败", Toast.LENGTH_SHORT).show()
                            unboundRenameFor = null
                        }
                    },
                ) {
                    BasicTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                    )
                }
            }
        }

        // ── 已解绑项目删除确认弹窗（彻底删除文件夹及其中所有文件） ──
        unboundDeleteConfirmFor?.let { path ->
            val dir = File(path)
            Box(Modifier.fillMaxSize()) {
                PientDialog(
                    title = "删除项目",
                    onDismiss = { unboundDeleteConfirmFor = null },
                    confirmText = "删除",
                    showClose = false,
                    onConfirm = {
                        if (!dir.exists() || dir.deleteRecursively()) {
                            selectedUnbound.remove(path)
                            unboundDeleteConfirmFor = null
                            unbindTick++
                        } else {
                            Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                            unboundDeleteConfirmFor = null
                        }
                    },
                ) {
                    Text(
                        "确定要删除项目「${dir.name}」吗？其文件夹及其中所有文件将被彻底删除。此操作不可撤销。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

/** 项目行三点菜单（选项与聊天页侧边栏项目菜单一致） */
@Composable
private fun ProjectRowMenu(
    canRemove: Boolean,
    onDetail: () -> Unit,
    onRename: () -> Unit,
    onUnbind: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text("详细信息") },
        leadingIcon = {
            Icon(
                Icons.Outlined.Info, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = onDetail,
    )
    DropdownMenuItem(
        text = { Text("重命名") },
        leadingIcon = {
            Icon(
                Icons.Outlined.Edit, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = onRename,
    )
    DropdownMenuItem(
        text = { Text("解绑") },
        leadingIcon = {
            Icon(
                Icons.Outlined.LinkOff, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
        enabled = canRemove,
        onClick = onUnbind,
    )
    DropdownMenuItem(
        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
        leadingIcon = {
            Icon(
                Icons.Outlined.Delete, null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        },
        enabled = canRemove,
        onClick = onDelete,
    )
}

/** 会话行三点菜单（重命名 / 删除） */
@Composable
private fun SessionRowMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text("重命名") },
        leadingIcon = {
            Icon(
                Icons.Outlined.Edit, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = onRename,
    )
    DropdownMenuItem(
        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
        leadingIcon = {
            Icon(
                Icons.Outlined.Delete, null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = onDelete,
    )
}

/** 记录卡内搜索框（surfaceContainerHigh 底 + 圆角 10 + Search 图案 + placeholder + 有输入时 × 清空） */
@Composable
private fun RecordSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(
            Icons.Outlined.Search, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    innerTextField()
                }
            },
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        if (value.isNotEmpty()) {
            Icon(
                Icons.Outlined.Close, "清空",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = { onValueChange("") }),
            )
        }
    }
}

/** 项目记录行：名称 + 路径小字；点击单选高亮（再点取消）；行尾竖直三点菜单 */
@Composable
private fun ProjectRecordRow(
    name: String,
    path: String,
    selected: Boolean,
    menuExpanded: Boolean,
    canRemove: Boolean,
    onClick: () -> Unit,
    onMenuToggle: () -> Unit,
    onMenuDismiss: () -> Unit,
    onMenuDetail: () -> Unit,
    onMenuRename: () -> Unit,
    onMenuUnbind: () -> Unit,
    onMenuDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clickable(onClick = onMenuToggle)
                .padding(start = 6.dp),
        ) {
            Icon(
                Icons.Outlined.MoreVert, "项目操作",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onMenuDismiss,
            ) {
                ProjectRowMenu(
                    canRemove = canRemove,
                    onDetail = onMenuDetail,
                    onRename = onMenuRename,
                    onUnbind = onMenuUnbind,
                    onDelete = onMenuDelete,
                )
            }
        }
    }
}

/** 会话记录行：圆形选择框（选中圆内 √）+ 标题 + 项目名小字；整行点击切换选择；行尾竖直三点菜单 */
@Composable
private fun SessionRecordRow(
    session: Session,
    selected: Boolean,
    menuExpanded: Boolean,
    onClick: () -> Unit,
    onMenuToggle: () -> Unit,
    onMenuDismiss: () -> Unit,
    onMenuRename: () -> Unit,
    onMenuDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        // 圆形选择框：未选 = 空心圆描边；选中 = primary 实底 + 白 √
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    CircleShape,
                )
                .border(
                    1.5.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape,
                ),
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check, null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                session.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                session.project,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clickable(onClick = onMenuToggle)
                .padding(start = 6.dp),
        ) {
            Icon(
                Icons.Outlined.MoreVert, "会话操作",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onMenuDismiss,
            ) {
                SessionRowMenu(
                    onRename = onMenuRename,
                    onDelete = onMenuDelete,
                )
            }
        }
    }
}

/** 已解绑项目行：圆形选择框 + 名称；整行点击切换选择；行尾竖直三点菜单（重新绑定 / 重命名 / 删除） */
@Composable
private fun UnboundProjectRow(
    name: String,
    selected: Boolean,
    menuExpanded: Boolean,
    onClick: () -> Unit,
    onMenuToggle: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRebind: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        // 圆形选择框：未选 = 空心圆描边；选中 = primary 实底 + 白 √
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    CircleShape,
                )
                .border(
                    1.5.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape,
                ),
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check, null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clickable(onClick = onMenuToggle)
                .padding(start = 6.dp),
        ) {
            Icon(
                Icons.Outlined.MoreVert, "项目操作",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onMenuDismiss,
            ) {
                DropdownMenuItem(
                    text = { Text("重新绑定") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Link, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = onRebind,
                )
                DropdownMenuItem(
                    text = { Text("重命名") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Edit, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = onRename,
                )
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Delete, null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = onDelete,
                )
            }
        }
    }
}

/** 弹窗操作选择行：图标 + 文字；选中 = accent 0.10 底 + 描边 + 右侧 √ */
@Composable
private fun ActionChoiceRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val accent = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(10.dp),
            )
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Icon(
            icon, null,
            tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        if (selected) {
            Icon(
                Icons.Outlined.Check, null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
