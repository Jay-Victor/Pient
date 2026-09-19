package com.pient.app.ui.chat

import com.pient.app.data.i18n.L
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.pient.app.R
import com.pient.app.data.ChatState
import com.pient.app.data.PanelMaterial
import com.pient.app.data.Project
import com.pient.app.data.ProjectFiles
import com.pient.app.data.ProjectTemplates
import com.pient.app.data.ProjectType
import com.pient.app.data.Session
import com.pient.app.data.SessionGroup
import com.pient.app.data.SettingsStore
import com.pient.app.data.SidebarStyle
import com.pient.app.data.groupSessionsByRecency
import com.pient.app.data.relativeTimeLabel
import com.pient.app.ui.components.ArcSpinner
import com.pient.app.ui.components.DetailRow
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.components.ProjectInfo
import com.pient.app.ui.components.computeProjectInfo
import com.pient.app.ui.theme.MonoFont
import com.pient.app.ui.theme.PientGlassSurface
import com.kyant.backdrop.Backdrop
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 悬浮侧边栏的四周留白（左/上/下 12dp，与「悬浮输入框」同口径 28dp 圆角配套）。
 *
 * ★ 这份留白也算「侧栏右缘」的一部分：面板右缘 = 展开位移量 296dp + 本值 = 308dp。
 *   凡是按「侧栏宽」算的数值——**推动展开的主内容位移、抽屉自身滑入起始位、平板压缩的
 *   宽度/位移、点外关闭的 x 阈值**——都必须用 `296dp + SidebarFloatingInset`，
 *   只算 296dp 会让侧栏右缘（含右描边）压在聊天页上 12dp。贴边样式本值不参与（= 0）。
 *   本常量与 [SessionDrawer] 里的 `padding(start=…)` 是同一份实现，改一处即可。
 */
internal val SidebarFloatingInset = 12.dp

/**
 * 会话侧栏：
 * 品牌 + 搜索 / 批量管理 / 新建会话 / 项目选择器（切换 + 新建；项目行三点菜单重命名/删除）/
 * 会话列表（按项目过滤、时间分组；关键字搜索；行尾三点菜单）
 * 底部技能·插件·设置。宽度 296dp。
 *
 * 外观由「主题与外观 → 侧边栏设置」决定：
 * - 侧边栏样式：贴边（默认，贴屏幕左缘、仅右侧两角 16dp 圆角）/ 悬浮（四周留白 + 四角全圆角 28dp）；
 * - 侧边栏材质：简约（纯色面板，可调透明度）/ 磨砂玻璃 / 液态玻璃（见 [PientGlassSurface]）。
 *
 * @param backdrop 聊天页面板内容层的 backdrop（玻璃材质采样「侧栏背后透出的内容」；
 *   该层与抽屉同级、不含抽屉自身，不会造成渲染树自引用）
 */
@Composable
fun SessionDrawer(
    chatState: ChatState,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
    backdrop: Backdrop? = null,
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
    // 重置工作区
    var projectResetConfirmFor by remember { mutableStateOf<String?>(null) }
    var renameFor by remember { mutableStateOf<String?>(null) }
    var deleteConfirmFor by remember { mutableStateOf<String?>(null) }
    var batchMode by remember { mutableStateOf(false) }
    var batchDeleteConfirm by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    val context = LocalContext.current

    val projectSessions = chatState.sessionsFor(chatState.currentProject ?: "")
    // 时间分组（普通模式列表用；批量模式进入时也要用它把列表一次性全展开，故提到这里）
    val pinnedSessions = projectSessions.filter { it.pinned }
    val sessionGroups = groupSessionsByRecency(projectSessions.filter { !it.pinned })
    val allSelected = projectSessions.isNotEmpty() && selectedIds.containsAll(projectSessions.map { it.id })
    val searching = searchOpen && searchQuery.isNotBlank()
    // 内容检索：标题之外再搜消息正文 —— 命中片段显示在会话行下方。
    // 放 LaunchedEffect（按 query 触发）+ IO 线程：搜索要遍历会话消息，不能在组合里做。
    var contentHits by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(searchQuery, searching) {
        contentHits = if (!searching) {
            emptyMap()
        } else {
            withContext(Dispatchers.IO) { chatState.searchSessionContents(searchQuery) }
        }
    }
    val visibleSessions = if (searching) {
        projectSessions.filter {
            it.title.contains(searchQuery.trim(), ignoreCase = true) || contentHits.containsKey(it.id)
        }
    } else projectSessions

    // ── 侧边栏外观（侧边栏设置）──
    // 贴边 = 贴屏幕左缘、仅右侧两角 16dp；
    // 悬浮 = 四周留白（左/上/下 12dp）+ 四角全圆角 28dp（与「悬浮输入框」同口径），
    //        像卡片一样浮在页面上（上下留白叠在状态栏/导航栏 inset 之外）。
    val sidebarFloating = SettingsStore.sidebarStyle == SidebarStyle.FLOATING
    val drawerShape = if (sidebarFloating) {
        RoundedCornerShape(28.dp)
    } else {
        RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
    }

    Box {
        PientGlassSurface(
            material = SettingsStore.sidebarMaterial,
            shape = drawerShape,
            floating = sidebarFloating,
            transparency = SettingsStore.sidebarTransparency,
            frostIntensity = SettingsStore.sidebarFrostIntensity,
            extraBackdrop = backdrop,
            modifier = modifier
                .then(
                    if (sidebarFloating) {
                        Modifier.padding(
                            start = SidebarFloatingInset,
                            top = SidebarFloatingInset,
                            bottom = SidebarFloatingInset,
                        )
                    } else {
                        Modifier
                    },
                )
                .width(296.dp)
                .statusBarsPadding() // padding(top=inset) 在 fillMaxHeight 之前，容器全高
                .fillMaxHeight()
                .clip(drawerShape), // 悬浮时四角全圆角：内容（列表/底栏）随形状裁切
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
                    L.session.selectedCount(selectedIds.size),
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
                        L.common.cancel,
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
                        L.common.done,
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
                        Icons.Outlined.Search, L.common.search,
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
                            // 批量模式：列表一次性全部展开——折叠组与渐进未揭示的会话
                            // 也要可见可选（否则全选/勾选前得先手动展开）
                            chatState.expandAllTimeGroups(sessionGroups)
                        })
                        .padding(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.Checklist, L.session.batchMode,
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
                            // 未绑定项目时引导先创建项目（无 mock 项目）
                            if (chatState.currentProject == null) {
                                Toast.makeText(context, L.session.createProjectFirst, Toast.LENGTH_SHORT).show()
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
                        L.session.newSession,
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
                    L.session.projectLabel(chatState.currentProject ?: L.session.noProject),
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
            val proj = chatState.projects.firstOrNull { it.name == chatState.currentProject }
            val path = proj?.path ?: ""
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
                                    Icons.Outlined.MoreVert, L.project.projectActions,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                DropdownMenu(
                                    expanded = projectMenuFor == p.name,
                                    onDismissRequest = { projectMenuFor = null },
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
                                        onClick = {
                                            projectMenuFor = null
                                            projectDetailFor = p.name
                                        },
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
                                        onClick = {
                                            projectMenuFor = null
                                            projectRenameFor = p.name
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(L.session.resetWorkspace, color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.RestartAlt, null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                        onClick = {
                                            projectMenuFor = null
                                            projectResetConfirmFor = p.name
                                        },
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
                                        enabled = chatState.projects.isNotEmpty(),
                                        onClick = {
                                            projectMenuFor = null
                                            projectUnbindConfirmFor = p.name
                                        },
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
                    // ── 新建项目 ──
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = {
                                // 弹窗输入名称 → 真实创建文件夹（可选项目类型模板）
                                newProjectDialogOpen = true
                            })
                            .padding(vertical = 8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.CreateNewFolder, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            L.session.newProject,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp),
                        )
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
                                    L.session.searchSessions,
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
                            Icons.Outlined.Close, L.common.clearSearch,
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
                        L.common.selectAll,
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
                        L.common.delete,
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
                            L.session.noMatchingSessions,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                items(visibleSessions, key = { it.id }) { s ->
                    Column {
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
                        // 内容命中片段（标题没匹配上是靠正文命中的；给用户一句「为什么它在这」）
                        contentHits[s.id]?.let { snip ->
                            Text(
                                snip,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 14.dp, end = 10.dp, bottom = 6.dp),
                            )
                        }
                    }
                }
            } else {
                val pinned = pinnedSessions
                val groups = sessionGroups
                if (pinned.isEmpty() && groups.isEmpty()) {
                    item {
                        Text(
                            if (chatState.currentProject == null) L.session.createProjectFirst
                            else L.session.emptySessions,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                if (pinned.isNotEmpty()) {
                    // 置顶分组头：与时间分组头同款（文字 + 折叠箭头 + 横线，
                    // 统一视觉；折叠键 = "pinned" 进 collapsedTimeGroups，与其他日历桶同机制）
                    item(key = "g-pinned") {
                        TimeGroupHeader(
                            label = L.session.pin,
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
                    }
                }
                // ── 渐进揭示 ──
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
                                Icons.Outlined.MoreHoriz, L.session.showMoreSessions,
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
            DrawerEntry(Icons.Outlined.Code, L.common.skill) { onNavigate("skills") }
            DrawerEntry(Icons.Outlined.Extension, L.session.plugins) { onNavigate("plugins") }
            DrawerEntry(Icons.Outlined.Settings, L.common.settings) { onNavigate("settings") }
        }
        }
    }

    // 项目详细信息弹窗（位置 / 大小 / 修改时间；单「确定」按钮）
    projectDetailFor?.let { name ->
        val project = chatState.projects.firstOrNull { it.name == name } ?: return@let
        // 统计放 IO 线程（与项目管理页同一口径）
        var info by remember(project.path, project.uri) { mutableStateOf<ProjectInfo?>(null) }
        LaunchedEffect(project.path, project.uri) { info = computeProjectInfo(context, project) }
        Box(Modifier.fillMaxSize()) {
            PientDialog(
                title = L.common.details,
                onDismiss = { projectDetailFor = null },
                onConfirm = { projectDetailFor = null },
                showClose = false,   // 详细信息卡片不带右上角 ×（点外/确定可关）
                showCancel = false,
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DetailRow(L.common.location, ProjectFiles.readablePath(project.path))
                    DetailRow(L.common.size, info?.size ?: L.common.counting)
                    DetailRow(L.common.modifiedAt, info?.modified ?: L.common.counting)
                }
            }
        }
    }


    // 重置工作区确认（清空项目根目录内容、保留根目录本身；破坏性 → 红字确认）
    projectResetConfirmFor?.let { rn ->
        val rp = chatState.projects.firstOrNull { it.name == rn }
        if (rp != null) {
            PientDialog(
                title = L.session.resetWorkspace,
                onDismiss = { projectResetConfirmFor = null },
                confirmText = L.session.resetConfirm,
                showClose = false,
                onConfirm = {
                    projectResetConfirmFor = null
                    val done = ProjectFiles.resetProjectRoot(context, rp)
                    Toast.makeText(
                        context,
                        if (done) L.session.workspaceResetToast(rp.name) else L.session.resetFailed,
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            ) {
                Text(
                    L.session.clearConfirm(rn) +
                        L.session.clearConfirmSuffix,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    // 新建项目弹窗（项目类型模板）
    if (newProjectDialogOpen) {
        var name by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(ProjectType.BLANK) }
        val confirmEnabled = name.isNotBlank() && !name.contains('/') &&
            chatState.projects.none { it.name == name.trim() }
        Box(Modifier.fillMaxSize()) {
            PientDialog(
                title = L.session.newProject,
                onDismiss = { newProjectDialogOpen = false },
                confirmText = L.common.create,
                showClose = false,
                confirmEnabled = confirmEnabled,
                onConfirm = {
                    val n = name.trim()
                    val dir = File(context.filesDir, "Projects/$n")
                    if (dir.exists() || dir.mkdirs()) {
                        // 模板物化（小文件，量级 KB；已存在的文件不覆盖）
                        val written = ProjectTemplates.create(dir, type, n)
                        chatState.addProject(n, dir.absolutePath)
                        if (type != ProjectType.BLANK) {
                            Toast.makeText(
                                context,
                                L.session.templateCreatedToast(type.title, written),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        newProjectDialogOpen = false
                        projectPickerOpen = false
                    } else {
                        Toast.makeText(context, L.session.folderCreateFailed, Toast.LENGTH_SHORT).show()
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
                    L.session.projectTypeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    ProjectType.entries.forEach { t ->
                        val on = type == t
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                )
                                .border(
                                    1.dp,
                                    if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { type = t }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                t.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
                Text(
                    L.session.projectTypeHint(type.desc),
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
                title = L.project.renameProject,
                onDismiss = { projectRenameFor = null },
                confirmText = L.common.save,
                showClose = false,
                confirmEnabled = newName.isNotBlank() &&
                    (newName.trim() == name || chatState.projects.none { it.name == newName.trim() }),
                onConfirm = {
                    // 失败（目录被占用 / 同名目录）如实提示
                    chatState.renameProject(name, newName)?.let { err ->
                        android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_SHORT).show()
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

    // 项目删除确认弹窗（删除项目文件夹及其中所有文件 + 会话记录）
    projectDeleteConfirmFor?.let { name ->
        val project = chatState.projects.firstOrNull { it.name == name }
        Box(Modifier.fillMaxSize()) {
            PientDialog(
                title = L.project.deleteProject,
                onDismiss = { projectDeleteConfirmFor = null },
                confirmText = L.common.delete,
                showClose = false,
                onConfirm = {
                    // 先删真实文件夹（成功/目录不存在视为成功），再移出列表与记录
                    val folderRemoved = project == null || ProjectFiles.deleteProjectRoot(context, project)
                    if (folderRemoved) {
                        chatState.removeProject(name)
                        projectDeleteConfirmFor = null
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

    // 项目解绑确认弹窗（移出列表 + 删除会话记录，保留项目文件夹及文件）
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

    // 删除二次确认弹窗（单个会话）
    deleteConfirmFor?.let { id ->
        val session = projectSessions.firstOrNull { it.id == id }
        if (session != null) {
            Box(Modifier.fillMaxSize()) {
                PientDialog(
                    title = L.session.deleteSession,
                    onDismiss = { deleteConfirmFor = null },
                    confirmText = L.common.delete,
                    showClose = false,
                    onConfirm = {
                        chatState.deleteSession(id)
                        deleteConfirmFor = null
                        selectedIds.remove(id)
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
    }

    // 批量删除确认弹窗
    if (batchDeleteConfirm) {
        Box(Modifier.fillMaxSize()) {
            PientDialog(
                title = L.session.batchDeleteTitle,
                onDismiss = { batchDeleteConfirm = false },
                confirmText = L.common.delete,
                showClose = false,
                onConfirm = {
                    selectedIds.toList().forEach { chatState.deleteSession(it) }
                    selectedIds.clear()
                    batchDeleteConfirm = false
                    // 删除最后一个会话后 ChatState 自动新建，批量模式一律退出
                    batchMode = false
                },
            ) {
                Text(
                    L.session.batchDeleteConfirm(selectedIds.size),
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
                title = L.session.renameSession,
                onDismiss = { renameFor = null },
                confirmText = L.common.save,
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
 * 时间/置顶分组头：分组文字 + 折叠箭头 + 右侧横线。
 * 整行可点切换折叠，
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
            contentDescription = if (collapsed) L.common.expand else L.common.collapse,
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
 * 会话行：名称 + 最后聊天时间 + 竖直三点（菜单：置顶/重命名/删除）。
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
                ArcSpinner()
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
            ArcSpinner()
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
        // 最后活动相对时间（刚刚/N分/N时/N天，渲染时由 updatedAt 派生）
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
                Icons.Outlined.MoreVert, L.session.moreActions,
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
                            if (session.pinned) L.session.unpin else L.session.pin,
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
                    text = { Text(L.common.rename) },
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
                    text = { Text(L.common.delete, color = MaterialTheme.colorScheme.error) },
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
// 项目详细信息：位置 / 大小 / 修改时间
// 实现位于公共组件 ui/components/Common.kt（ProjectInfo / computeProjectInfo / DetailRow，
// 项目管理页复用）；此处直接使用。
// ─────────────────────────────────────────────────────────────
