package com.pient.app.ui.chat

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.documentfile.provider.DocumentFile
import com.pient.app.R
import com.pient.app.data.ChatState
import com.pient.app.data.Project
import com.pient.app.data.ProjectFiles
import com.pient.app.data.Session
import com.pient.app.data.SessionGroup
import com.pient.app.data.groupSessionsByRecency
import com.pient.app.data.relativeTimeLabel
import com.pient.app.ui.components.DetailRow
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.components.ProjectInfo
import com.pient.app.ui.components.computeProjectInfo
import com.pient.app.ui.theme.MonoFont
import com.pient.app.ui.theme.PientPanel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 会话侧栏（P2，设计计划 3.2）：
 * 品牌 + 搜索 / 批量管理 / 新建会话 / 项目选择器（切换 + 新建；项目行三点菜单重命名/删除）/
 * 会话列表（按项目过滤、时间分组；关键字搜索；行尾三点菜单）/
 * 底部技能·插件·设置。宽度 296dp。
 */
@Composable
fun SessionDrawer(
    chatState: ChatState,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var projectPickerOpen by remember { mutableStateOf(false) }
    var newProjectDialogOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var projectMenuFor by remember { mutableStateOf<String?>(null) }
    var projectDetailFor by remember { mutableStateOf<String?>(null) }
    var projectRenameFor by remember { mutableStateOf<String?>(null) }
    var projectDeleteConfirmFor by remember { mutableStateOf<String?>(null) }
    var projectUnbindConfirmFor by remember { mutableStateOf<String?>(null) }
    var renameFor by remember { mutableStateOf<String?>(null) }
    var deleteConfirmFor by remember { mutableStateOf<String?>(null) }
    var batchMode by remember { mutableStateOf(false) }
    var batchDeleteConfirm by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    val context = LocalContext.current

    // 选择本地文件夹（SAF，2026-09-02 实现）：系统目录选择器 → tree URI 持久化授权 → 新项目。
    // 结果校验必须是目录（系统选择器 UI 仍会显示文件条目，文件不可作为项目根）；
    // 主存储映射为真实路径 /storage/emulated/0/...，其他 provider 回退 content URI。
    val pickFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult // 用户取消
        val doc = DocumentFile.fromTreeUri(context, uri)
        if (doc == null || !doc.isDirectory) {
            Toast.makeText(context, "请选择文件夹，不支持文件", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val name = doc.name ?: uri.lastPathSegment ?: "本地文件夹"
        // primary:xxx → /storage/emulated/0/xxx；SD 卡/云盘 provider 无法映射时保留 content URI
        val realPath = try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val parts = docId.split(':')
            if (uri.authority == "com.android.externalstorage.documents" &&
                parts.size == 2 && parts[0] == "primary"
            ) {
                Environment.getExternalStorageDirectory().absolutePath + "/" + parts[1]
            } else null
        } catch (e: Exception) {
            null
        }
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            // 个别 provider 不支持持久化授权，按本次会话临时授权继续
        }
        if (!chatState.addProject(name, realPath ?: uri.toString(), uri.toString())) {
            Toast.makeText(context, "已存在同名项目「$name」", Toast.LENGTH_SHORT).show()
        } else {
            projectPickerOpen = false
        }
    }

    val projectSessions = chatState.sessionsFor(chatState.currentProject ?: "")
    val allSelected = projectSessions.isNotEmpty() && selectedIds.containsAll(projectSessions.map { it.id })
    val searching = searchOpen && searchQuery.isNotBlank()
    val visibleSessions = if (searching) {
        projectSessions.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }
    } else projectSessions

    Box {
        PientPanel(
            modifier = modifier
                .width(296.dp)
                .statusBarsPadding() // Operit 同款：padding(top=inset) 在 fillMaxHeight 之前，容器全高
                .fillMaxHeight(),
            // Operit drawerShape 同款：右侧 16dp 圆角（shapes.medium.copy），左侧贴屏缘直角
            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
        // ── 上方一栏：品牌 + 搜索 / 批量管理 + 新建会话 ──
        if (batchMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            ) {
                Text(
                    "已选 ${selectedIds.size} 个会话",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
                        .clickable(onClick = {
                            batchMode = false
                            selectedIds.clear()
                        })
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        "取消",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                        .clickable(onClick = {
                            batchMode = false
                            selectedIds.clear()
                        })
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        "完成",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.pient_logo),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Pient",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                // 搜索图案按键（批量按键左侧）
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clickable(onClick = {
                            searchOpen = !searchOpen
                            if (!searchOpen) searchQuery = ""
                        })
                        .padding(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.Search, "搜索",
                        tint = if (searchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                // 批量管理图案按键（新建会话左侧）
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clickable(onClick = {
                            batchMode = true
                            selectedIds.clear()
                            searchOpen = false
                            searchQuery = ""
                        })
                        .padding(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.Checklist, "批量管理",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                        .clickable(onClick = {
                            // 未绑定项目时引导先创建项目（2026-09-08：无 mock 项目）
                            if (chatState.currentProject == null) {
                                Toast.makeText(context, "请先创建项目", Toast.LENGTH_SHORT).show()
                            } else {
                                chatState.newSession()
                            }
                        })
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(
                        Icons.Outlined.Add, null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "新建会话",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        // ── 项目选择器 ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .clickable(onClick = { projectPickerOpen = !projectPickerOpen })
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "项目：${chatState.currentProject ?: "未创建项目"}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (projectPickerOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            val path = chatState.projects.firstOrNull { it.name == chatState.currentProject }?.path ?: ""
            Text(
                path,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFont, fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 项目选择层（项目行限高滚动；新建项目行固定在最底部不被挤走）
            AnimatedVisibility(visible = projectPickerOpen) {
                Column(Modifier.padding(top = 10.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        chatState.projects.forEach { p ->
                        val sel = p.name == chatState.currentProject
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = {
                                    chatState.setProject(p.name)
                                    projectPickerOpen = false
                                })
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                p.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            // 项目行三点：重命名 / 删除
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clickable(onClick = { projectMenuFor = p.name })
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.MoreVert, "项目操作",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                DropdownMenu(
                                    expanded = projectMenuFor == p.name,
                                    onDismissRequest = { projectMenuFor = null },
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
                                        onClick = {
                                            projectMenuFor = null
                                            projectDetailFor = p.name
                                        },
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
                                        onClick = {
                                            projectMenuFor = null
                                            projectRenameFor = p.name
                                        },
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
                                        enabled = chatState.projects.isNotEmpty(),
                                        onClick = {
                                            projectMenuFor = null
                                            projectUnbindConfirmFor = p.name
                                        },
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
                                        enabled = chatState.projects.isNotEmpty(),
                                        onClick = {
                                            projectMenuFor = null
                                            projectDeleteConfirmFor = p.name
                                        },
                                    )
                                }
                            }
                        }
                    }
                        }
                    // ── 底部分隔线（与上方项目列表隔开）──
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    // ── 新建项目 / 选择本地文件夹：同一行，中间竖分隔线 ──
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .offset(x = (-10).dp) // 2026-09-02 用户要求：整体稍左移
                                .clickable(onClick = {
                                    // 2026-09-02 实现：弹窗输入名称 → 真实创建文件夹
                                    newProjectDialogOpen = true
                                }),
                        ) {
                            Icon(
                                Icons.Outlined.CreateNewFolder, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                "新建项目",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = {
                                    // 2026-09-02 实现：SAF 系统目录选择器 + tree URI 持久化授权
                                    pickFolderLauncher.launch(null)
                                }),
                        ) {
                            Icon(
                                Icons.Outlined.FolderOpen, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                "选择本地文件夹",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        // ── 搜索框（项目选择器下方；自动聚焦） ──
        if (searchOpen) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.Outlined.Search, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                        .focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        Box {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "搜索会话标题",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                if (searchQuery.isNotEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clickable(onClick = { searchQuery = "" })
                            .padding(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Close, "清空搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        // ── 批量操作条（全选 / 删除） ──
        if (batchMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = {
                        if (allSelected) {
                            selectedIds.clear()
                        } else {
                            selectedIds.clear()
                            selectedIds.addAll(projectSessions.map { it.id })
                        }
                    }),
                ) {
                    Icon(
                        if (allSelected) Icons.Outlined.CheckCircle else Icons.Outlined.Circle,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "全选",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(enabled = selectedIds.isNotEmpty(), onClick = {
                        batchDeleteConfirm = true
                    }),
                ) {
                    Icon(
                        Icons.Outlined.Delete, null,
                        tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "删除",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }

        // ── 会话列表（当前项目；搜索模式平铺，普通模式时间分组） ──
        LazyColumn(
            modifier = Modifier.weight(1f).padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (searching) {
                if (visibleSessions.isEmpty()) {
                    item {
                        Text(
                            "无匹配会话",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                items(visibleSessions, key = { it.id }) { s ->
                    SessionRow(
                        session = s,
                        active = s.id == chatState.currentSessionId,
                        batchMode = false,
                        selected = false,
                        onClick = {
                            chatState.selectSession(s.id)
                            onClose()
                        },
                        onToggleSelect = {},
                        onTogglePin = { chatState.togglePin(s.id) },
                        onRename = { renameFor = s.id },
                        onDeleteRequest = { deleteConfirmFor = s.id },
                    )
                }
            } else {
                val pinned = projectSessions.filter { it.pinned }
                val unpinned = projectSessions.filter { !it.pinned }
                val groups = groupSessionsByRecency(unpinned)
                if (pinned.isEmpty() && groups.isEmpty()) {
                    item {
                        Text(
                            if (chatState.currentProject == null) "请先创建项目"
                            else "暂无会话 · 点右上角 + 新建会话",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                if (pinned.isNotEmpty()) {
                    // 置顶分组头：与时间分组头同款（文字 + 折叠箭头 + 横线，2026-09-09 用户要求
                    // 统一视觉；折叠键 = "pinned" 进 collapsedTimeGroups，与其他日历桶同机制）
                    item(key = "g-pinned") {
                        TimeGroupHeader(
                            label = "置顶",
                            collapsed = "pinned" in chatState.collapsedTimeGroups,
                            onToggle = { chatState.toggleTimeGroup("pinned") },
                        )
                    }
                    if ("pinned" !in chatState.collapsedTimeGroups) {
                        items(pinned, key = { it.id }) { s ->
                            SessionRow(
                                session = s,
                                active = s.id == chatState.currentSessionId,
                                batchMode = batchMode,
                                selected = s.id in selectedIds,
                                onClick = {
                                    chatState.selectSession(s.id)
                                    onClose()
                                },
                                onToggleSelect = {
                                    if (s.id in selectedIds) selectedIds.remove(s.id) else selectedIds.add(s.id)
                                },
                                onTogglePin = { chatState.togglePin(s.id) },
                                onRename = { renameFor = s.id },
                                onDeleteRequest = { deleteConfirmFor = s.id },
                            )
                        }
                        // 分隔线：置顶会话与下方时间分组列表隔开（2026-09-09 用户要求——
                        // 置顶段与未置顶会话的视觉分界；仅两侧都有会话且置顶段展开时渲染，
                        // 全置顶或置顶折叠时无内容可区分，不渲染悬线）
                        if (groups.isNotEmpty()) {
                            item(key = "g-pinned-divider") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                            }
                        }
                    }
                }
                // ── 渐进揭示（2026-09-09 用户要求）──
                // 默认仅显示最近分组（展开）+ 次新分组（折叠，内容空）；折叠组下方右侧
                // 横向三点按键，每点揭示 5 个会话；组完全揭示后下一更老分组解锁接替；
                // 全部组揭示完三点消失。组头点击仍可手动全展开/折叠（展开 = 全揭示）。
                val rendered = mutableListOf<Pair<SessionGroup, Int>>() // (组, 可见会话数)
                for ((i, g) in groups.withIndex()) {
                    val collapsed = g.key in chatState.collapsedTimeGroups
                    val visible = when {
                        collapsed -> 0
                        i == 0 -> g.sessions.size // 最近组恒全显示
                        else -> minOf(chatState.timeGroupRevealed[g.key] ?: 0, g.sessions.size)
                    }
                    if (i >= 2) {
                        // 更老组：前一渲染组完全揭示才解锁
                        val prev = rendered.last().first
                        val prevCollapsed = prev.key in chatState.collapsedTimeGroups
                        val prevRevealed = chatState.timeGroupRevealed[prev.key] ?: 0
                        val prevComplete = !prevCollapsed && prevRevealed >= prev.sessions.size
                        if (!prevComplete) break
                    }
                    rendered += g to visible
                }
                // 三点只服务「渐进未揭示」：手动折叠（collapsed）的组不挂三点——
                // 其组头箭头即可恢复，全部揭示后折叠不会让三点诈尸
                val moreGroup = rendered.lastOrNull()?.let { last ->
                    val lastCollapsed = last.first.key in chatState.collapsedTimeGroups
                    if (rendered.size >= 2 && !lastCollapsed && last.second < last.first.sessions.size) {
                        last.first
                    } else null
                }

                rendered.forEach { (group, visible) ->
                    // 内容隐藏 = 手动折叠或渐进未揭示（visible=0）；箭头与点击行为按此统一
                    val hidden = group.key in chatState.collapsedTimeGroups || visible == 0
                    item(key = "g-${group.key}") {
                        TimeGroupHeader(
                            label = group.label,
                            collapsed = hidden,
                            onToggle = {
                                if (hidden) {
                                    // 展开 = 全揭示（渐进三点与组头点击互不打架）
                                    chatState.collapsedTimeGroups.remove(group.key)
                                    chatState.timeGroupRevealed[group.key] = group.sessions.size
                                } else {
                                    chatState.collapsedTimeGroups.add(group.key)
                                }
                            },
                        )
                    }
                    if (visible > 0) {
                        items(group.sessions.take(visible), key = { it.id }) { s ->
                            SessionRow(
                                session = s,
                                active = s.id == chatState.currentSessionId,
                                batchMode = batchMode,
                                selected = s.id in selectedIds,
                                onClick = {
                                    chatState.selectSession(s.id)
                                    onClose()
                                },
                                onToggleSelect = {
                                    if (s.id in selectedIds) selectedIds.remove(s.id) else selectedIds.add(s.id)
                                },
                                onTogglePin = { chatState.togglePin(s.id) },
                                onRename = { renameFor = s.id },
                                onDeleteRequest = { deleteConfirmFor = s.id },
                            )
                        }
                    }
                }
                // 横向三点「显示更多」按键（挂在最后一个折叠渐进组下方靠右；整行可点）
                moreGroup?.let { mg ->
                    item(key = "g-more-${mg.key}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { chatState.revealMoreTimeGroup(mg.key, mg.sessions.size) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Icon(
                                Icons.Outlined.MoreHoriz, "显示更多会话",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(horizontal = 20.dp, vertical = 6.dp)
                                    .size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        // ── 分隔线（底部导航区上方；两端与侧栏边框留间隔） ──
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )

        // ── 下方一栏：技能 / 插件 / 设置 ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DrawerEntry(Icons.Outlined.Code, "技能") { onNavigate("skills") }
            DrawerEntry(Icons.Outlined.Extension, "插件") { onNavigate("plugins") }
            DrawerEntry(Icons.Outlined.Settings, "设置") { onNavigate("settings") }
        }
        }
    }

    // 项目详细信息弹窗（2026-09-02 新增：位置 / 大小 / 修改时间；单「确定」按钮）
    projectDetailFor?.let { name ->
        val project = chatState.projects.firstOrNull { it.name == name } ?: return@let
        val info = remember(project.path, project.uri) { computeProjectInfo(context, project) }
        Box(Modifier.fillMaxSize()) {
            PientDialog(
                title = "详细信息",
                onDismiss = { projectDetailFor = null },
                onConfirm = { projectDetailFor = null },
                showClose = false,   // 2026-09-02 用户：详细信息卡片右上角 × 多余（点外/确定可关）
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

    // 新建项目（新建文件夹）弹窗（2026-09-02 实现：确定后在应用私有目录 Projects/ 下真实创建）
    if (newProjectDialogOpen) {
        var name by remember { mutableStateOf("") }
        val confirmEnabled = name.isNotBlank() && !name.contains('/') &&
            chatState.projects.none { it.name == name.trim() }
        Box(Modifier.fillMaxSize()) {
            PientDialog(
                title = "新建项目",
                onDismiss = { newProjectDialogOpen = false },
                confirmText = "创建",
                showClose = false,
                confirmEnabled = confirmEnabled,
                onConfirm = {
                    val dir = File(context.filesDir, "Projects/${name.trim()}")
                    if (dir.exists() || dir.mkdirs()) {
                        chatState.addProject(name.trim(), dir.absolutePath)
                        newProjectDialogOpen = false
                        projectPickerOpen = false
                    } else {
                        Toast.makeText(context, "文件夹创建失败", Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
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
                Text(
                    "将在应用私有目录 Projects/ 下创建该文件夹",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    // 项目重命名弹窗
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

    // 项目删除确认弹窗（2026-09-03 语义更新：删除项目文件夹及其中所有文件 + 会话记录）
    projectDeleteConfirmFor?.let { name ->
        val project = chatState.projects.firstOrNull { it.name == name }
        Box(Modifier.fillMaxSize()) {
            PientDialog(
                title = "删除项目",
                onDismiss = { projectDeleteConfirmFor = null },
                confirmText = "删除",
                showClose = false,
                onConfirm = {
                    // 先删真实文件夹（成功/目录不存在视为成功），再移出列表与记录
                    val folderRemoved = project == null || ProjectFiles.deleteProjectRoot(context, project)
                    if (folderRemoved) {
                        chatState.removeProject(name)
                        projectDeleteConfirmFor = null
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

    // 项目解绑确认弹窗（2026-09-03 新增：移出列表 + 删除会话记录，保留项目文件夹及文件）
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

    // 删除二次确认弹窗（单个会话）
    deleteConfirmFor?.let { id ->
        val session = projectSessions.firstOrNull { it.id == id }
        if (session != null) {
            Box(Modifier.fillMaxSize()) {
                PientDialog(
                    title = "删除会话",
                    onDismiss = { deleteConfirmFor = null },
                    confirmText = "删除",
                    showClose = false,
                    onConfirm = {
                        chatState.deleteSession(id)
                        deleteConfirmFor = null
                        selectedIds.remove(id)
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
    }

    // 批量删除确认弹窗
    if (batchDeleteConfirm) {
        Box(Modifier.fillMaxSize()) {
            PientDialog(
                title = "批量删除会话",
                onDismiss = { batchDeleteConfirm = false },
                confirmText = "删除",
                showClose = false,
                onConfirm = {
                    selectedIds.toList().forEach { chatState.deleteSession(it) }
                    selectedIds.clear()
                    batchDeleteConfirm = false
                    // 2026-09-09：删除最后一个会话后 ChatState 自动新建，批量模式一律退出
                    batchMode = false
                },
            ) {
                Text(
                    "确定要删除选中的 ${selectedIds.size} 个会话吗？此操作不可撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }

    // 重命名会话弹窗
    renameFor?.let { id ->
        var newName by remember(id) { mutableStateOf(projectSessions.firstOrNull { it.id == id }?.title ?: "") }
        Box(Modifier.fillMaxSize()) {
            PientDialog(
                title = "重命名会话",
                onDismiss = { renameFor = null },
                confirmText = "保存",
                showClose = false,
                confirmEnabled = newName.isNotBlank(),
                onConfirm = {
                    chatState.renameSession(id, newName)
                    renameFor = null
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
    }
}

/**
 * 时间/置顶分组头：分组文字 + 折叠箭头 + 右侧横线（2026-08-30 样式）。
 * 2026-09-09 加折叠（Hermes SidebarDateDivider 同款）：整行可点切换折叠，
 * 箭头右=折叠 / 下=展开（chevron-right rotate-90 语义），折叠仅隐藏组内会话、
 * 组头保留；无标签组不渲染头（不可折叠）。置顶分组复用本组件（键 "pinned"）。
 */
@Composable
private fun TimeGroupHeader(label: String, collapsed: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            if (collapsed) Icons.Outlined.KeyboardArrowRight else Icons.Outlined.KeyboardArrowDown,
            contentDescription = if (collapsed) "展开" else "收起",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        // 分组标题旁的横线（贯穿至行右缘）
        Box(
            Modifier
                .weight(1f)
                .padding(start = 8.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/**
 * 会话运行中指示（pi-web SessionSidebar RunningSessionIndicator 逐值对齐）：
 * 14×14 容器、accent 色 305.1° 大弧（viewBox 24 内半径 9、stroke 2.8 圆帽、
 * 0.9s/圈绕中心无限旋转）——path M21 12a9 9 0 1 1-3.8-7.4 换算：半径 0.75×、
 * 弧圆 topLeft 0.125×、stroke 2.8/24×14=1.63dp。
 */
@Composable
private fun RunningArcIndicator() {
    val angle by rememberInfiniteTransition(label = "runningArc").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "runningArcAngle",
    )
    val arcColor = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .size(14.dp)
            .rotate(angle),
    ) {
        drawArc(
            color = arcColor,
            startAngle = 0f,
            sweepAngle = 305.1f,
            useCenter = false,
            topLeft = Offset(size.width * 0.125f, size.height * 0.125f),
            size = Size(size.width * 0.75f, size.height * 0.75f),
            style = Stroke(width = 1.63.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * 会话行：名称 + 最后聊天时间（参考 Hermes）+ 竖直三点（菜单：置顶/重命名/删除）。
 * 批量模式：前导圆形勾选 + 名称，点击行切换选择，三点菜单禁用。
 * 会话运行中（session.running）时行首显示旋转圆弧指示。
 */
@Composable
private fun SessionRow(
    session: Session,
    active: Boolean,
    batchMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)

    if (batchMode) {
        // ── 批量模式：勾选 + 名称 ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
                )
                .clickable(onClick = onToggleSelect)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Icon(
                if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.Circle,
                null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(18.dp),
            )
            if (session.running) {
                Spacer(Modifier.width(6.dp))
                RunningArcIndicator()
            }
            Text(
                session.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
        }
        return
    }

    // ── 普通模式：名称 + 竖直三点 ──
    var menuOpen by remember(session.id) { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        if (session.running) {
            RunningArcIndicator()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            session.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // 最后活动相对时间（Hermes 会话行口径：刚刚/N分/N时/N天，渲染时由 updatedAt 派生）
        Text(
            relativeTimeLabel(session.updatedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp),
        )
        // 竖直三点：点击弹出置顶/重命名/删除菜单
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clickable(onClick = { menuOpen = true })
                .padding(6.dp),
        ) {
            Icon(
                Icons.Outlined.MoreVert, "更多操作",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (session.pinned) "取消置顶" else "置顶",
                            color = if (session.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.PushPin, null,
                            tint = if (session.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onTogglePin()
                    },
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
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
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
                    onClick = {
                        menuOpen = false
                        onDeleteRequest()
                    },
                )
            }
        }
    }
}

@Composable
private fun DrawerEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 项目详细信息（2026-09-02）：位置 / 大小 / 修改时间
// 实现已移至公共组件 ui/components/Common.kt（ProjectInfo / computeProjectInfo / DetailRow，
// 2026-09-03 项目管理页复用）；此处直接使用。
// ─────────────────────────────────────────────────────────────
