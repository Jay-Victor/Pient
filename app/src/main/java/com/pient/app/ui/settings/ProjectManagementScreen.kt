package com.pient.app.ui.settings

import com.pient.app.data.i18n.L
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
import androidx.compose.runtime.LaunchedEffect
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
import com.pient.app.ui.components.PientChoiceRow
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.components.PientSearchField
import com.pient.app.ui.components.ProjectInfo
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
 *   导出与删除都是真实执行（导出 → 系统「下载/Pient/」的 Markdown，2026-09-16 真实化）。
 *   2026-09-16 另修两处：① 项目重命名现在会连磁盘目录一起改；② 「详细信息」统计下放 IO 线程。
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
                    Icons.Outlined.ArrowBack, L.common.back,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = { nav.popBackStack() }),
                )
                Text(
                    L.settings.projectManagement,
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
                SectionHeader(L.project.sectionProjects, icon = Icons.Outlined.Folder)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                ) {
                    // 搜索框
                    PientSearchField(
                        value = projectQuery,
                        onValueChange = { projectQuery = it },
                        placeholder = L.project.searchProjects,
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
                                L.project.noMatchingProjects,
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
                SectionHeader(L.project.sectionSessions, icon = Icons.Outlined.Chat)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                ) {
                    // 搜索框
                    PientSearchField(
                        value = sessionQuery,
                        onValueChange = { sessionQuery = it },
                        placeholder = L.session.searchSessions,
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
                            L.project.selectedSessionsCount(selectedSessions.size, sessions.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            L.common.selectAll,
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
                            L.common.cancel,
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
                                L.session.noMatchingSessions,
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
                            L.project.selectedSessionsAction(selectedSessions.size),
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
                SectionHeader(L.project.sectionUnbound, icon = Icons.Outlined.LinkOff)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                ) {
                    // 搜索框
                    PientSearchField(
                        value = unbindQuery,
                        onValueChange = { unbindQuery = it },
                        placeholder = L.project.searchUnbound,
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
                            L.project.selectedUnboundCount(selectedUnbound.size, unboundDirs.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            L.common.selectAll,
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
                            L.common.cancel,
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
                                L.project.noUnbound,
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
                                            Toast.makeText(context, L.project.reboundToast(d.name), Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, L.project.projectExists, Toast.LENGTH_SHORT).show()
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
                            L.project.selectedProjectsAction(selectedUnbound.size),
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
                title = L.project.selectedSessionsTitle,
                onDismiss = { actionDialogOpen = false },
                confirmText = L.common.confirm,
                confirmEnabled = actionChoice != null,
                showClose = false,
                onConfirm = {
                    actionDialogOpen = false
                    when (actionChoice) {
                        0 -> {
                            // 真实导出（2026-09-16；此前是原型占位 Toast）：
                            // Markdown 落到系统「下载/Pient/」，全部会话合成一个文档。
                            val picks = allSessions.filter { selectedSessions.contains(it.id) }
                            val appCtx = context.applicationContext
                            val count = picks.size
                            Thread {
                                val md = com.pient.app.data.SessionExport.markdown(
                                    chatState, picks, L.project.exportDocTitle,
                                )
                                val where = com.pient.app.data.SessionExport.writeToDownloads(
                                    appCtx, com.pient.app.data.SessionExport.fileName(count), md,
                                )
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    Toast.makeText(
                                        appCtx,
                                        if (where != null) {
                                            L.project.exportDone(count, where)
                                        } else {
                                            L.project.exportFailed
                                        },
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }.start()
                            selectedSessions.clear()
                        }
                        1 -> {
                            selectedSessions.toList().forEach { chatState.deleteSessionById(it) }
                            Toast.makeText(
                                context,
                                L.project.deletedSessionsToast(selectedSessions.size),
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
                    PientChoiceRow(
                        icon = Icons.Outlined.FileDownload,
                        label = L.project.exportSessions,
                        selected = actionChoice == 0,
                        onClick = { actionChoice = 0 },
                    )
                    PientChoiceRow(
                        icon = Icons.Outlined.Delete,
                        label = L.session.deleteSession,
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
            // 统计放 IO 线程（2026-09-16 修）：SAF 大目录几千文件，组合期直接扫会卡死/ANR
            var info by remember(project.path, project.uri) { mutableStateOf<ProjectInfo?>(null) }
            LaunchedEffect(project.path, project.uri) { info = computeProjectInfo(context, project) }
            Box(Modifier.fillMaxSize()) {
                PientDialog(
                    title = L.common.details,
                    onDismiss = { projectDetailFor = null },
                    onConfirm = { projectDetailFor = null },
                    showClose = false,
                    showCancel = false,
                ) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        DetailRow(L.common.location, project.path)
                        DetailRow(L.common.size, info?.size ?: L.common.counting)
                        DetailRow(L.common.modifiedAt, info?.modified ?: L.common.counting)
                    }
                }
            }
        }

        // ── 项目重命名弹窗 ──
        projectRenameFor?.let { name ->
            var newName by remember(name) { mutableStateOf(name) }
            Box(Modifier.fillMaxSize()) {
                PientDialog(
                    title = L.project.renameProject,
                    onDismiss = { projectRenameFor = null },
                    confirmText = L.common.save,
                    showClose = false,
                    confirmEnabled = newName.isNotBlank() &&
                        (newName.trim() == name || chatState.projects.none { it.name == newName.trim() }),
                    onConfirm = {
                        // 失败（目录被占用 / 同名目录）如实提示（2026-09-16 起 renameProject 会连目录一起改）
                        chatState.renameProject(name, newName)?.let { err ->
                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                        }
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
                    title = L.project.unbindProject,
                    onDismiss = { projectUnbindConfirmFor = null },
                    confirmText = L.common.unbind,
                    showClose = false,
                    onConfirm = {
                        chatState.removeProject(name)
                        projectUnbindConfirmFor = null
                        if (selectedProject == name) selectedProject = null
                    },
                ) {
                    Text(
                        L.project.unbindConfirm(name, chatState.sessionsFor(name).size),
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
                    title = L.project.deleteProject,
                    onDismiss = { projectDeleteConfirmFor = null },
                    confirmText = L.common.delete,
                    showClose = false,
                    onConfirm = {
                        val folderRemoved = project == null || ProjectFiles.deleteProjectRoot(context, project)
                        if (folderRemoved) {
                            chatState.removeProject(name)
                            projectDeleteConfirmFor = null
                            if (selectedProject == name) selectedProject = null
                        } else {
                            Toast.makeText(context, L.project.folderDeleteFailed, Toast.LENGTH_SHORT).show()
                            projectDeleteConfirmFor = null
                        }
                    },
                ) {
                    Text(
                        L.project.deleteConfirm(name, chatState.sessionsFor(name).size),
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
                    title = L.session.renameSession,
                    onDismiss = { sessionRenameFor = null },
                    confirmText = L.common.save,
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
                    title = L.session.deleteSession,
                    onDismiss = { sessionDeleteConfirmFor = null },
                    confirmText = L.common.delete,
                    showClose = false,
                    onConfirm = {
                        chatState.deleteSessionById(id)
                        selectedSessions.remove(id)
                        sessionDeleteConfirmFor = null
                    },
                ) {
                    Text(
                        L.session.deleteConfirm(session.title),
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
                title = L.project.selectedProjectsTitle,
                onDismiss = { unboundActionDialogOpen = false },
                confirmText = L.common.confirm,
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
                                L.project.reboundCountToast(ok, targets.size),
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
                                L.project.deletedCountToast(ok, targets.size),
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
                    PientChoiceRow(
                        icon = Icons.Outlined.Link,
                        label = L.project.rebind,
                        selected = unboundActionChoice == 0,
                        onClick = { unboundActionChoice = 0 },
                    )
                    PientChoiceRow(
                        icon = Icons.Outlined.Delete,
                        label = L.common.delete,
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
                    title = L.project.renameProject,
                    onDismiss = { unboundRenameFor = null },
                    confirmText = L.common.save,
                    showClose = false,
                    confirmEnabled = newName.isNotBlank() && !newName.contains('/') && !nameTaken,
                    onConfirm = {
                        val target = File(projectsDir, newName.trim())
                        if (dir.renameTo(target)) {
                            selectedUnbound.remove(path)
                            unboundRenameFor = null
                            unbindTick++
                        } else {
                            Toast.makeText(context, L.common.renameFailed, Toast.LENGTH_SHORT).show()
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
                    title = L.project.deleteProject,
                    onDismiss = { unboundDeleteConfirmFor = null },
                    confirmText = L.common.delete,
                    showClose = false,
                    onConfirm = {
                        if (!dir.exists() || dir.deleteRecursively()) {
                            selectedUnbound.remove(path)
                            unboundDeleteConfirmFor = null
                            unbindTick++
                        } else {
                            Toast.makeText(context, L.common.deleteFailed, Toast.LENGTH_SHORT).show()
                            unboundDeleteConfirmFor = null
                        }
                    },
                ) {
                    Text(
                        L.project.deleteDirConfirm(dir.name),
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
        text = { Text(L.common.details) },
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
        text = { Text(L.common.rename) },
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
        text = { Text(L.common.unbind) },
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
        text = { Text(L.common.delete, color = MaterialTheme.colorScheme.error) },
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
        text = { Text(L.common.rename) },
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
        text = { Text(L.common.delete, color = MaterialTheme.colorScheme.error) },
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
                Icons.Outlined.MoreVert, L.project.projectActions,
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
                Icons.Outlined.MoreVert, L.project.sessionActions,
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
                Icons.Outlined.MoreVert, L.project.projectActions,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onMenuDismiss,
            ) {
                DropdownMenuItem(
                    text = { Text(L.project.rebind) },
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
                    text = { Text(L.common.rename) },
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
                    text = { Text(L.common.delete, color = MaterialTheme.colorScheme.error) },
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
