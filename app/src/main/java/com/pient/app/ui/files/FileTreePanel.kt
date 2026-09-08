package com.pient.app.ui.files

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.UnfoldLess
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.pient.app.data.ChatState
import com.pient.app.data.FileNode
import com.pient.app.data.ProjectFiles
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.components.PientSegmented
import com.pient.app.ui.theme.MonoFont
import com.pient.app.ui.theme.PientPanel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 文件树排序模式 */
private enum class FileSort(val label: String) {
    NAME("按名称"),
    SIZE("按大小"),
    TIME("按修改时间"),
}

/**
 * 右侧文件树面板（设计计划 3.5，pi-web FileExplorer 参考）：
 * 顶部栏 = 项目根名 + 四个图案按键（从左到右：排序 / 新建 / 刷新 / 折叠全部）；
 * 文件夹分层级折叠/展开；文件点击在预览区打开；长按操作菜单。
 * 宽 296dp，同窗口浮层。关闭：点面板外遮罩。
 */
@Composable
fun FileTreePanel(
    chatState: ChatState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuFor by remember { mutableStateOf<FileNode?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(FileSort.NAME) }
    var createOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }
    var deleteTarget by remember { mutableStateOf<FileNode?>(null) }
    var detailTarget by remember { mutableStateOf<FileNode?>(null) }
    var importMenuOpen by remember { mutableStateOf(false) }
    var exportMenuOpen by remember { mutableStateOf(false) }
    // 批量导出选择模式（2026-09-02）：树行尾勾选；存 source（树刷新后仍稳定）
    var exportSelectMode by remember { mutableStateOf(false) }
    val exportSelectedSources = remember { mutableStateListOf<String>() }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    // 真实文件树（2026-09-02）：当前项目目录；null = 未绑定项目或目录不存在（2026-09-08 起无 mock 回退）
    val root = chatState.fileTreeRoot

    // 面板打开/项目切换时加载真实树
    LaunchedEffect(chatState.currentProject) {
        chatState.refreshFileTree(context)
    }

    val project = chatState.projects.firstOrNull { it.name == chatState.currentProject }

    // 导入文件：SAF 多选（不限类型）→ 递归拷贝到项目根
    val importFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        if (uris.isEmpty() || project == null) return@rememberLauncherForActivityResult
        val ok = ProjectFiles.importFiles(context, project, uris)
        Toast.makeText(context, "已导入 $ok/${uris.size} 个文件", Toast.LENGTH_SHORT).show()
        if (ok > 0) chatState.refreshFileTree(context)
    }

    // 导入文件夹：SAF 选目录 → 整目录递归拷入项目根
    val importFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null || project == null) return@rememberLauncherForActivityResult
        val ok = ProjectFiles.importFolder(context, project, uri)
        Toast.makeText(
            context,
            if (ok) "已导入文件夹" else "导入失败",
            Toast.LENGTH_SHORT,
        ).show()
        if (ok) chatState.refreshFileTree(context)
    }

    // 导出目标选择（SAF 目录）：批量导出按选择集合、项目导出按 zip 打包
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null || project == null) return@rememberLauncherForActivityResult
        val ok = if (exportSelectMode) {
            val currentRoot = chatState.fileTreeRoot
            if (currentRoot == null) {
                false // 无文件树无法按选择导出
            } else {
                val nodes = collectNodes(currentRoot).filter { it.source in exportSelectedSources }
                ProjectFiles.exportSelected(context, project, nodes, uri)
            }
        } else {
            ProjectFiles.exportProject(context, project, uri)
        }
        Toast.makeText(
            context,
            if (ok) "已导出到所选目录" else "导出失败",
            Toast.LENGTH_SHORT,
        ).show()
        if (ok) {
            exportSelectMode = false
            exportSelectedSources.clear()
        }
    }

    val searching = searchOpen && searchQuery.isNotBlank()
    val matchedFiles = if (searching) {
        val out = mutableListOf<FileNode>()
        root?.let { collectFiles(it.children, out) }
        out.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    } else emptyList()

    // 根 Box：弹窗 fillMaxSize 必须挂在 Box 内，否则会作为父 Row 的第三子项被
    // 压缩到极窄宽度（文字竖排）——与 SessionDrawer 同构
    Box(modifier) {
        PientPanel(
            modifier = Modifier
                .width(296.dp)
                .fillMaxHeight()
                // 面板必须自身 align(CenterEnd)：重命名/删除弹窗（fillMaxSize）会把
                // 根 Box 撑到全屏，若面板无 align（TopStart）会随根 Box 变宽而跳到
                // 屏幕左侧（2026-09-02 用户报「弹窗后文件树侧边栏变到左侧」）。
                .align(Alignment.CenterEnd),
            // 左侧 16dp 圆角、右侧贴屏缘直角（聊天页侧栏右侧圆角的镜像，2026-09-02 用户要求）
            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 注意：面板嵌在顶栏下方区域（FilesPanel 内），顶栏已含状态栏避让，
                // 此处再 statusBarsPadding 会双重避让产生 ~24dp 顶部留白（2026-08-27 修复）
                .padding(bottom = 8.dp),
        ) {
        // 头部：项目根 + 图案按键（排序 / 新建 / 刷新 / 折叠全部）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                root?.name ?: (chatState.currentProject ?: "未绑定项目"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 搜索
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clickable(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) searchQuery = ""
                    })
                    .padding(5.dp),
            ) {
                Icon(
                    Icons.Outlined.Search, "搜索",
                    tint = if (searchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            // 排序
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clickable(onClick = { sortMenuOpen = true })
                    .padding(5.dp),
            ) {
                Icon(
                    Icons.Outlined.Sort, "排序",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false },
                ) {
                    FileSort.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        mode.label,
                                        color = if (sortMode == mode) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (sortMode == mode) {
                                        Icon(
                                            Icons.Outlined.Check, null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            },
                            onClick = {
                                sortMode = mode
                                sortMenuOpen = false
                            },
                        )
                    }
                }
            }
            // 导入（文件多选 / 文件夹）
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clickable(onClick = { importMenuOpen = true })
                    .padding(5.dp),
            ) {
                Icon(
                    Icons.Outlined.FileDownload, "导入",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                DropdownMenu(
                    expanded = importMenuOpen,
                    onDismissRequest = { importMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("导入文件") },
                        onClick = {
                            importMenuOpen = false
                            importFilesLauncher.launch(arrayOf("*/*"))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("导入文件夹") },
                        onClick = {
                            importMenuOpen = false
                            importFolderLauncher.launch(null)
                        },
                    )
                }
            }
            // 导出（项目整目录到所选位置）
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clickable(onClick = { exportMenuOpen = true })
                    .padding(5.dp),
            ) {
                Icon(
                    Icons.Outlined.FileUpload, "导出",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                DropdownMenu(
                    expanded = exportMenuOpen,
                    onDismissRequest = { exportMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("批量导出") },
                        onClick = {
                            exportMenuOpen = false
                            // 进入批量选择模式：树行尾出现勾选，底部操作条确认导出
                            exportSelectMode = true
                            exportSelectedSources.clear()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("导出项目") },
                        onClick = {
                            exportMenuOpen = false
                            exportSelectMode = false
                            exportSelectedSources.clear()
                            exportLauncher.launch(null)
                        },
                    )
                }
            }
            // 新建
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clickable(onClick = { createOpen = true })
                    .padding(5.dp),
            ) {
                Icon(
                    Icons.Outlined.Add, "新建",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            // 刷新
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clickable(onClick = { chatState.refreshFileTree(context) })
                    .padding(5.dp),
            ) {
                Icon(
                    Icons.Outlined.Refresh, "刷新",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            // 折叠全部
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clickable(onClick = { chatState.expandedDirs.clear() })
                    .padding(5.dp),
            ) {
                Icon(
                    Icons.Outlined.UnfoldLess, "折叠全部",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // 搜索输入框（点击搜索按键后出现；自动聚焦，样式同 SessionDrawer）
        if (searchOpen) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 4.dp)
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

        // 树（普通递归 Composable：避免 LazyListScope 递归接收者陷阱）；
        // 搜索中则显示扁平匹配结果列表
        // weight(1f) 默认 fill=true：占满剩余空间，批量导出操作条恒贴面板最底部（2026-09-02）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 60.dp),
        ) {
            if (searching) {
                if (matchedFiles.isEmpty()) {
                    Text(
                        "未找到匹配文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    matchedFiles.forEach { node ->
                        SearchResultRow(
                            node = node,
                            onClick = { chatState.openFile(node) },
                        )
                    }
                }
            } else if (root == null) {
                // 未绑定项目/目录不存在（2026-09-08 起无 mock 回退）：提示而非空白
                Text(
                    "绑定项目后显示文件树",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                sortedChildren(root.children, sortMode).forEach { child ->
                    TreeRow(
                        node = child,
                        depth = 0,
                        path = "/" + root.name,
                        chatState = chatState,
                        sortMode = sortMode,
                        selectMode = exportSelectMode,
                        selectedSources = exportSelectedSources,
                        onMenu = { menuFor = it },
                    )
                }
            }
        }

        // 批量导出操作条（选择模式下固定面板底部）
        if (exportSelectMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    "已选 ${exportSelectedSources.size} 项",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
                        .clickable(onClick = {
                            exportSelectMode = false
                            exportSelectedSources.clear()
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
                        .background(
                            if (exportSelectedSources.isEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable(enabled = exportSelectedSources.isNotEmpty()) {
                            exportLauncher.launch(null)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        "导出",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        }
    }

    // 新建文件/文件夹弹窗（Popup 定位窗口左上：无平台 dim，只有内容内单层 scrim——
    // 之前用 Dialog 会叠加平台默认遮罩使阴影更浓，2026-08-27 修复）
    if (createOpen) {
        val density = LocalDensity.current
        val config = LocalConfiguration.current
        Popup(
            onDismissRequest = { createOpen = false },
            popupPositionProvider = object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset = IntOffset.Zero
            },
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        ) {
            Box(
                Modifier.size(
                    with(density) { config.screenWidthDp.dp },
                    with(density) { config.screenHeightDp.dp },
                ),
            ) {
                CreateEntryDialog(
                    onDismiss = { createOpen = false },
                    onCreate = { type, name ->
                        createOpen = false
                        // 真实创建（2026-09-02）：项目根目录下新建文件/文件夹
                        val ok = project != null &&
                            ProjectFiles.createEntry(context, project, name.trim(), type == 0)
                        Toast.makeText(
                            context,
                            if (ok) "已创建${if (type == 0) "文件" else "文件夹"} $name"
                            else "创建失败（名称无效或目录不可写）",
                            Toast.LENGTH_SHORT,
                        ).show()
                        if (ok) chatState.refreshFileTree(context)
                    },
                )
            }
        }
    }

    // 长按操作菜单
    menuFor?.let { node ->
        Box {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { menuFor = null },
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
                        detailTarget = node
                        menuFor = null
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
                        renameTarget = node
                        menuFor = null
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
                        deleteTarget = node
                        menuFor = null
                    },
                )
                DropdownMenuItem(
                    text = { Text("@ 提及插入输入框") },
                    onClick = {
                        chatState.mentionInsertRequest = node.name
                        Toast.makeText(context, "@${node.name} 已插入输入框", Toast.LENGTH_SHORT).show()
                        menuFor = null
                    },
                )
            }
        }
    }

    // 详细信息弹窗（2026-09-02 新增：位置 / 大小 / 修改时间；单「确定」按钮）
    detailTarget?.let { node ->
        val (size, modified) = remember(node.source, node.size, node.modifiedAt) {
            ProjectFiles.nodeStat(context, node)
        }
        Box(Modifier.fillMaxSize()) {
            PientDialog(
                title = "详细信息",
                onDismiss = { detailTarget = null },
                onConfirm = { detailTarget = null },
                showClose = false,   // 2026-09-02 用户：右上角 × 多余（与聊天页侧边栏同款）
                showCancel = false,
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DetailRow("位置", node.source ?: "—")
                    DetailRow("大小", formatSize(size))
                    DetailRow(
                        "修改时间",
                        if (modified > 0) SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(modified))
                        else "—",
                    )
                }
            }
        }
    }

    // 重命名弹窗（真实操作，2026-09-02）
    renameTarget?.let { node ->
        var newName by remember(node.name) { mutableStateOf(node.name) }
        Box(Modifier.fillMaxSize()) {
            PientDialog(
                title = "重命名",
                onDismiss = { renameTarget = null },
                confirmText = "保存",
                showClose = false,
                confirmEnabled = newName.isNotBlank() && newName != node.name &&
                    !newName.contains('/') && !newName.contains('\\'),
                onConfirm = {
                    val ok = ProjectFiles.renameEntry(context, node, newName.trim())
                    Toast.makeText(
                        context,
                        if (ok) "已重命名" else "重命名失败",
                        Toast.LENGTH_SHORT,
                    ).show()
                    renameTarget = null
                    if (ok) chatState.refreshFileTree(context)
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

    // 删除确认弹窗（真实操作，2026-09-02）
    deleteTarget?.let { node ->
        Box(Modifier.fillMaxSize()) {
            PientDialog(
                title = "删除",
                onDismiss = { deleteTarget = null },
                confirmText = "删除",
                showClose = false,
                onConfirm = {
                    val ok = ProjectFiles.deleteEntry(context, node)
                    Toast.makeText(
                        context,
                        if (ok) "已删除 ${node.name}" else "删除失败",
                        Toast.LENGTH_SHORT,
                    ).show()
                    deleteTarget = null
                    if (ok) {
                        // 已打开的标签页若指向该文件则关闭
                        val i = chatState.openTabs.indexOfFirst { it.source == node.source && it.source != null }
                        if (i >= 0) chatState.closeTab(i)
                        chatState.refreshFileTree(context)
                    }
                },
            ) {
                Text(
                    "确定要删除「${node.name}」吗？${if (node.isDir) "其下所有内容将一并删除。" else ""}此操作不可撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
    }
}

/** 排序：文件夹恒在文件前（文件夹按名称），文件按所选模式（大小/时间降序，未知值排后） */
private fun sortedChildren(children: List<FileNode>, mode: FileSort): List<FileNode> {
    val dirs = children.filter { it.isDir }.sortedBy { it.name.lowercase() }
    val files = when (mode) {
        FileSort.NAME -> children.filter { !it.isDir }.sortedBy { it.name.lowercase() }
        FileSort.SIZE -> children.filter { !it.isDir }.sortedWith(
            compareByDescending<FileNode> { it.size }.thenBy { it.name.lowercase() },
        )
        FileSort.TIME -> children.filter { !it.isDir }.sortedWith(
            compareByDescending<FileNode> { it.modifiedAt }.thenBy { it.name.lowercase() },
        )
    }
    return dirs + files
}

/** 扁平化收集文件树中的全部文件节点（递归；搜索结果源） */
private fun collectFiles(nodes: List<FileNode>, out: MutableList<FileNode>) {
    nodes.forEach { node ->
        if (node.isDir) collectFiles(node.children, out) else out.add(node)
    }
}

/** 搜索匹配文件行：图标 + 文件名，点击在预览区打开 */
@Composable
private fun SearchResultRow(
    node: FileNode,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Icon(
            fileIcon(node.ext),
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            node.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
    }
}

/** 文件类型选项（新建文件弹窗；null = 不指定，需手输完整文件名） */
private val fileTypes = listOf(
    null to "不指定（手动输入后缀）",
    ".txt" to "文本文件 .txt",
    ".md" to "Markdown .md",
    ".json" to "JSON .json",
    ".xml" to "XML .xml",
    ".kt" to "Kotlin .kt",
    ".java" to "Java .java",
    ".py" to "Python .py",
    ".html" to "HTML .html",
    ".png" to "图片 .png",
    ".jpg" to "图片 .jpg",
    ".mp4" to "视频 .mp4",
    ".mp3" to "音频 .mp3",
    ".zip" to "压缩包 .zip",
    ".apk" to "安装包 .apk",
)

/** 新建弹窗：分段（文件/文件夹）+ 文件类型选择器（仅文件）+ 名称输入 + 取消/创建 */
@Composable
private fun CreateEntryDialog(
    onDismiss: () -> Unit,
    onCreate: (type: Int, name: String) -> Unit,
) {
    var type by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    // 文件类型（null = 不指定）：选中后创建时自动补后缀
    var fileTypeExt by remember { mutableStateOf<String?>(null) }
    var typeMenuOpen by remember { mutableStateOf(false) }
    // 校验：文件夹直接可用；文件需「已选类型」或「名称自带后缀」
    val trimmed = name.trim()
    val nameOk = trimmed.isNotBlank() && !trimmed.contains('/') && !trimmed.contains('\\')
    val suffixOk = fileTypeExt != null || (trimmed.contains('.') && !trimmed.endsWith("."))
    val canCreate = nameOk && (type == 1 || suffixOk)

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
                Text(
                    "新建",
                    style = MaterialTheme.typography.titleMedium,
                )
                PientSegmented(
                    labels = listOf("文件", "文件夹"),
                    selected = type,
                    onSelect = { type = it },
                    modifier = Modifier.padding(top = 12.dp),
                )
                // 文件类型选择器（仅文件分段显示，2026-09-02）：右侧 v 箭头；
                // 选择器行与展开列表同一卡片区域（2026-09-02 用户：不要分成两个卡片）；
                // 列表自绘内嵌（嵌套 Popup 中 DropdownMenu 定位会飘到屏幕顶部，实测）
                if (type == 0) {
                    val typeLabel = fileTypes.firstOrNull { it.first == fileTypeExt }?.second
                        ?: "不指定（手动输入后缀）"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                    ) {
                        // 选择器行
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = { typeMenuOpen = !typeMenuOpen })
                                .padding(12.dp),
                        ) {
                            Text(
                                "文件类型",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                typeLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (fileTypeExt != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(start = 10.dp),
                            )
                            Icon(
                                if (typeMenuOpen) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        // 类型列表（同一卡片内；紧凑行高；限高滚动）
                        if (typeMenuOpen) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 4.dp),
                            ) {
                                fileTypes.forEach { (ext, label) ->
                                    val sel = ext == fileTypeExt
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(onClick = {
                                                fileTypeExt = ext
                                                typeMenuOpen = false
                                            })
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                    ) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (sel) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (sel) {
                                            Icon(
                                                Icons.Outlined.Check, null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (name.isEmpty()) {
                            Text(
                                if (type == 0 && fileTypeExt != null) "输入文件名（自动补 ${fileTypeExt}）"
                                else if (type == 0) "输入文件名（含后缀）"
                                else "输入文件夹名称",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        inner()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    PientButton("取消", onClick = onDismiss, primary = false, modifier = Modifier.weight(1f))
                    PientButton(
                        "创建",
                        onClick = {
                            // 已选类型且名称未带此后缀时自动补全（大小写不敏感）
                            val ext = fileTypeExt
                            var finalName = name.trim()
                            if (type == 0 && ext != null &&
                                !finalName.endsWith(ext, ignoreCase = true)
                            ) {
                                finalName += ext
                            }
                            onCreate(type, finalName)
                        },
                        enabled = canCreate,
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TreeRow(
    node: FileNode,
    depth: Int,
    path: String,
    chatState: ChatState,
    sortMode: FileSort,
    selectMode: Boolean = false,
    selectedSources: SnapshotStateList<String>? = null,
    onMenu: (FileNode) -> Unit,
) {
    val thisPath = "$path/${node.name}"
    val expanded = thisPath in chatState.expandedDirs
    val selected = selectMode && node.source != null &&
        selectedSources != null && node.source in selectedSources

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (node.isDir) chatState.toggleDir(thisPath)
                    else chatState.openFile(node)
                },
                onLongClick = { onMenu(node) },
            )
            .padding(start = (depth * 14 + 8).dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Icon(
            when {
                node.isDir && expanded -> Icons.Outlined.FolderOpen
                node.isDir -> Icons.Outlined.Folder
                else -> fileIcon(node.ext)
            },
            null,
            tint = if (node.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            node.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        if (node.isDir) {
            Icon(
                if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        // 批量导出选择模式：行尾勾选（独立点击区，不影响文件夹展开/文件打开）
        if (selectMode && selectedSources != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clickable(onClick = { toggleExportSelect(node, selectedSources) })
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Icon(
                    if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.Circle,
                    null,
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    if (node.isDir && expanded) {
        sortedChildren(node.children, sortMode).forEach { child ->
            TreeRow(child, depth + 1, thisPath, chatState, sortMode, selectMode, selectedSources, onMenu)
        }
    }
}

/** 切换批量导出勾选（按 source 唯一标识） */
private fun toggleExportSelect(node: FileNode, selectedSources: SnapshotStateList<String>) {
    val src = node.source ?: return
    if (src in selectedSources) selectedSources.remove(src) else selectedSources.add(src)
}

/** 扁平化收集树中全部节点（含目录；批量导出解析选中项用） */
private fun collectNodes(node: FileNode): List<FileNode> {
    val out = mutableListOf(node)
    node.children.forEach { out.addAll(collectNodes(it)) }
    return out
}

/** 大小格式化（详细信息；B/KB/MB/GB） */
private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
