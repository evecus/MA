package com.ma.manager.ui.screens

import androidx.activity.compose.BackHandler
import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import com.ma.manager.ui.theme.MAAlertDialog
import com.ma.manager.ui.theme.MAMotion
import com.ma.manager.ui.theme.OverlayHost
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.ma.manager.ui.theme.DualPaneShadows
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ma.manager.data.FileItem
import com.ma.manager.data.FileManagerViewModel
import com.ma.manager.data.FileKind
import com.ma.manager.data.OpenChoice
import com.ma.manager.data.PaneSide
import com.ma.manager.ui.components.*
import com.ma.manager.util.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: FileManagerViewModel = viewModel()
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var hiddenMenuExpanded by remember { mutableStateOf(false) }
    var currentTool by remember { mutableStateOf<ToolAction?>(null) }
    var snackMsg by remember { mutableStateOf<String?>(null) }
    var showExtractTo by remember { mutableStateOf<FileItem?>(null) }
    var fullApkPath by remember { mutableStateOf<String?>(null) }

    val left = viewModel.leftPane
    val right = viewModel.rightPane
    val active = viewModel.activePane
    val activeState = if (active == PaneSide.LEFT) left else right
    val context = LocalContext.current
    val settings = remember { com.ma.manager.data.SettingsStore.get(context) }

    viewModel.toastMessage?.let { msg ->
        LaunchedEffect(msg) {
            snackMsg = msg
            viewModel.clearToast()
        }
    }

    // ---- system back key / gesture on the file-manager screen ----
    // Full-screen pages (settings, editor, viewers, ...) register their own BackHandler,
    // so this one is only active while the dual-pane list is what the user sees.
    var lastBackAt by remember { mutableStateOf(0L) }
    val overlayShown = currentTool != null ||
        viewModel.imageViewerPath != null ||
        viewModel.hexViewerPath != null ||
        viewModel.mediaPlaylist != null ||
        viewModel.showTextEditor
    BackHandler(enabled = !overlayShown) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            viewModel.longPressItem != null -> viewModel.dismissLongPressMenu()
            left.inSelectionMode || right.inSelectionMode -> {
                if (left.inSelectionMode) viewModel.clearSelection(PaneSide.LEFT)
                if (right.inSelectionMode) viewModel.clearSelection(PaneSide.RIGHT)
            }
            activeState.searchQuery != null -> viewModel.clearSearch(active)
            canGoUp(activeState.currentPath) -> viewModel.goUp(active)
            else -> {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastBackAt < 2000L) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackAt = now
                    viewModel.toastMessage = "再按一次返回键退出"
                }
            }
        }
    }

    // ---- 占位类工具：用对话框提示 ----
    when (currentTool) {
        ToolAction.PLUGINS,
        ToolAction.REMOTE,
        ToolAction.COLOR_PICKER,
        ToolAction.FTP,
        ToolAction.ACTIVITY_LOG -> {
            MAAlertDialog(
                onDismissRequest = { currentTool = null },
                title = { Text("功能开发中") },
                text = {
                    Text(
                        when (currentTool) {
                            ToolAction.PLUGINS -> "插件管理：后续支持自定义脚本与翻译引擎。"
                            ToolAction.REMOTE -> "远程管理：HTTP/FTP 服务即将加入。"
                            ToolAction.COLOR_PICKER -> "屏幕取色：需要悬浮窗权限，后续实现。"
                            ToolAction.FTP -> "FTP 客户端：连接远程服务器浏览文件。"
                            ToolAction.ACTIVITY_LOG -> "Activity 记录：查看最近打开的路径与操作。"
                            else -> "敬请期待"
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = { currentTool = null }) { Text("确定") }
                }
            )
        }
        else -> {}
    }

    // ---- 全屏页面：不再用 return 硬切，而是作为覆盖层滑入 / 滑出 ----
    // 优先级与原来的 early-return 顺序一致：工具页 > 图片 > Hex > 播放器 > 文本编辑器
    val toolPage = currentTool?.takeIf { it !in DIALOG_TOOLS }
    val imagePath = viewModel.imageViewerPath
    val hexPath = viewModel.hexViewerPath
    val playlist = viewModel.mediaPlaylist
    val overlay: Overlay? = when {
        toolPage != null -> Overlay.Tool(toolPage)
        imagePath != null -> Overlay.Image(imagePath)
        hexPath != null -> Overlay.Hex(hexPath)
        playlist != null -> Overlay.Media(playlist)
        viewModel.showTextEditor -> Overlay.Editor
        else -> null
    }
    // 覆盖层出现时，下面的主界面轻微左移并压暗（视差），退出时恢复
    val overlayProgress by animateFloatAsState(
        targetValue = if (overlay != null) 1f else 0f,
        animationSpec = tween(MAMotion.Long - 30, easing = MAMotion.Standard),
        label = "overlayProgress"
    )

    // Long-press menu content shared by both panes (single vs batch).
    val paneMenu: @Composable (PaneSide, FileItem) -> Unit = { side, item ->
        val paneState = if (side == PaneSide.LEFT) left else right
        val batch = item.path in paneState.selectedItems && paneState.selectedItems.size > 1
        val targets = if (batch) {
            paneState.items.filter { it.path in paneState.selectedItems && it.name != ".." }
        } else {
            listOf(item)
        }
        LongPressMenu(
            expanded = true,
            item = item,
            targets = targets,
            batchMode = batch,
            otherPaneIsZip = viewModel.isOtherPaneArchive(side),
            sourceSide = side,
            onDismiss = { viewModel.dismissLongPressMenu() },
            onCopy = { viewModel.addOrCopyToOtherPane(side, targets) },
            onMove = { viewModel.moveToOtherPane(side, targets) },
            onDelete = { viewModel.requestDelete(targets) },
            onRename = {
                if (batch) {
                    viewModel.batchFind = ""
                    viewModel.batchReplace = ""
                    viewModel.showBatchRenameDialog = true
                } else {
                    viewModel.showRename(item)
                }
            },
            onCompress = { viewModel.compressItems(targets) },
            onProperties = {
                if (batch) viewModel.showMultiProperties(targets)
                else viewModel.showProperties(item)
            },
            onShare = {
                if (batch) viewModel.shareItems(targets) else viewModel.shareItem(item)
            },
            onExtractTo = { showExtractTo = item },
            onOpenWith = { viewModel.showOpenWith(item) },
            onAddBookmark = { viewModel.showBookmarkAddDialog = true },
            onSignatureInfo = { viewModel.openApkDialog(item) },
            // MT：长按「安装」不直接安装，先弹二次确认。
            onInstall = { viewModel.requestInstall(item) },
            onExtractToOtherPane = { viewModel.extractEntryToOtherPane(item) },
            onEditZipEntry = { viewModel.openZipEntryText(item) },
            onBatchRename = {
                viewModel.batchFind = ""
                viewModel.batchReplace = ""
                viewModel.showBatchRenameDialog = true
            },
            onChmod = { viewModel.showChmodDialog = true },
            onHash = { viewModel.hashItem(item) },
            onHex = { viewModel.openHex(item) },
            onAddToZip = { viewModel.addToZipTargets(side, targets) },
            onTestZip = {
                targets.firstOrNull { it.fileKind == FileKind.ARCHIVE && !it.isZipEntry }
                    ?.let { viewModel.testZipTarget(it.path) }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = -size.width * 0.16f * overlayProgress }
    ) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = androidx.compose.ui.graphics.RectangleShape,
                drawerContainerColor = com.ma.manager.ui.theme.MAStyle.listBg(),
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                SideDrawerContent(
                    bookmarks = viewModel.bookmarkList,
                    onNavigate = { viewModel.navigateTo(active, it) },
                    onTool = { currentTool = it },
                    onBookmarkRemove = { viewModel.removeBookmark(it) },
                    onBookmarkAdd = { viewModel.showBookmarkAddDialog = true },
                    onClose = { scope.launch { drawerState.close() } },
                    isOpen = drawerState.targetValue == DrawerValue.Open,
                    onRequestRoot = {
                        scope.launch {
                            val isRoot = withContext(Dispatchers.IO) {
                                com.topjohnwu.superuser.Shell.getShell().isRoot
                            }
                            viewModel.toastMessage =
                                if (isRoot) "Root 已授权，可访问所有目录" else "未获得 Root 权限，仅能部分访问"
                            viewModel.refreshPane(active)
                            viewModel.refreshPane(
                                if (active == PaneSide.LEFT) PaneSide.RIGHT else PaneSide.LEFT
                            )
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                val visibleNow = activeState.visibleItems
                val folderCount = visibleNow.count { it.isDirectory && it.name != ".." }
                val fileCount = visibleNow.count { !it.isDirectory }
                val (usedB, totalB) = FileHelper.getStorageInfo("/storage/emulated/0")
                MATopBar(
                    title = activeState.currentPath.let { if (it.endsWith("/")) it else "$it/" },
                    subtitle = "文件夹: $folderCount  文件: $fileCount  储存: " +
                            "${FileItem.formatStorage(usedB)}/${FileItem.formatStorage(totalB)}",
                    onNavigationClick = { scope.launch { drawerState.open() } },
                    onTitleClick = { viewModel.showJumpDialog = true },
                    actions = {
                        // 进入 / 退出多选：计数与关闭按钮横向展开 + 淡入；数字变化时上下滚动
                        val selCount = activeState.selectedItems.size
                        val lastSel = remember { arrayOf(0) }
                        if (selCount > 0) lastSel[0] = selCount
                        AnimatedVisibility(
                            visible = activeState.inSelectionMode,
                            enter = fadeIn(tween(MAMotion.Short)) +
                                expandHorizontally(tween(MAMotion.Short, easing = MAMotion.Standard)),
                            exit = fadeOut(tween(MAMotion.Fast)) +
                                shrinkHorizontally(tween(MAMotion.Short, easing = MAMotion.Standard))
                        ) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                AnimatedContent(
                                    targetState = if (selCount > 0) selCount else lastSel[0],
                                    transitionSpec = {
                                        if (targetState > initialState) {
                                            (slideInVertically(tween(MAMotion.Short)) { it / 2 } + fadeIn(tween(MAMotion.Short))) togetherWith
                                                (slideOutVertically(tween(MAMotion.Short)) { -it / 2 } + fadeOut(tween(MAMotion.Fast)))
                                        } else {
                                            (slideInVertically(tween(MAMotion.Short)) { -it / 2 } + fadeIn(tween(MAMotion.Short))) togetherWith
                                                (slideOutVertically(tween(MAMotion.Short)) { it / 2 } + fadeOut(tween(MAMotion.Fast)))
                                        }
                                    },
                                    label = "selCount"
                                ) { n ->
                                    Text(
                                        "已选 $n",
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                IconButton(onClick = { viewModel.clearSelection(active) }) {
                                    Icon(Icons.Default.Close, contentDescription = "取消选择")
                                }
                            }
                        }
                        Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.width(196.dp),
                            offset = androidx.compose.ui.unit.DpOffset(0.dp, (-48).dp),
                            shadowElevation = 12.dp
                        ) {
                            MAMenuItem("刷新", Icons.Default.Refresh, {
                                viewModel.refreshPane(active); menuExpanded = false
                            })
                            MAMenuItem("搜索", Icons.Default.Search, {
                                viewModel.showSearchDialog = true; menuExpanded = false
                            })
                            if (activeState.searchQuery != null) {
                                MAMenuItem("搜索结果", Icons.Default.ListAlt, {
                                    viewModel.toastMessage = "当前显示搜索 \"${activeState.searchQuery}\" 的结果"
                                    menuExpanded = false
                                })
                            }
                            MAMenuItem("全选", Icons.Default.SelectAll, {
                                viewModel.selectAll(active); menuExpanded = false
                            })
                            MAMenuItem("过滤", Icons.Outlined.FilterAlt, {
                                viewModel.showFilterDialog = true; menuExpanded = false
                            })
                            MAMenuItem("排序方式", Icons.AutoMirrored.Filled.Sort, {
                                viewModel.showSortDialog = true; menuExpanded = false
                            })
                            MAMenuItem("打开终端", Icons.Outlined.Terminal, {
                                currentTool = ToolAction.TERMINAL; menuExpanded = false
                            })
                            // 隐藏文件▶ with nested submenu
                            Box {
                                MAMenuItem(
                                    "隐藏文件", Icons.Default.Visibility,
                                    { hiddenMenuExpanded = true },
                                    trailing = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                                )
                                DropdownMenu(
                                    expanded = hiddenMenuExpanded,
                                    onDismissRequest = { hiddenMenuExpanded = false },
                                    shadowElevation = 12.dp
                                ) {
                                    MAMenuItem(
                                        if (activeState.showHidden) "不显示隐藏文件" else "显示隐藏文件",
                                        Icons.Default.VisibilityOff,
                                        {
                                            viewModel.toggleShowHidden()
                                            hiddenMenuExpanded = false
                                            menuExpanded = false
                                        }
                                    )
                                }
                            }
                            MAMenuItem("添加书签", Icons.Default.CollectionsBookmark, {
                                viewModel.showBookmarkAddDialog = true; menuExpanded = false
                            })
                            MAMenuItem("设为首页", Icons.Default.Home, {
                                viewModel.setAsHome(); menuExpanded = false
                            })
                            MAMenuItem("交换窗口", Icons.AutoMirrored.Filled.Login, {
                                viewModel.swapPanes(); menuExpanded = false
                            })
                            MAMenuItem("设置", Icons.Default.Settings, {
                                currentTool = ToolAction.SETTINGS; menuExpanded = false
                            })
                            MAMenuItem("退出", Icons.AutoMirrored.Filled.ExitToApp, {
                                menuExpanded = false
                                (context as? Activity)?.finish()
                            })
                        }
                        }
                    }
                )
            },
            bottomBar = {
                BottomActionBar(
                    canGoBack = activeState.historyIndex > 0,
                    canGoForward = activeState.historyIndex < activeState.history.lastIndex,
                    onBack = { viewModel.goBack(active) },
                    onForward = { viewModel.goForward(active) },
                    onNew = { viewModel.showNewDialog = true },
                    onSync = { viewModel.syncPanes(active) },
                    onUp = { viewModel.goUp(active) }
                )
            },
            snackbarHost = {
                // 退出动画期间 snackMsg 已为 null，保留最后一条文字让它完整滑出
                val shown = snackMsg
                val lastMsg = remember { arrayOf("") }
                if (shown != null) lastMsg[0] = shown
                AnimatedVisibility(
                    visible = shown != null,
                    enter = slideInVertically(
                        tween(MAMotion.Medium, easing = MAMotion.EmphasizedDecelerate)
                    ) { it } + fadeIn(tween(MAMotion.Short)),
                    exit = slideOutVertically(
                        tween(MAMotion.Short, easing = MAMotion.EmphasizedAccelerate)
                    ) { it } + fadeOut(tween(MAMotion.Short))
                ) {
                    Snackbar(
                        action = {
                            TextButton(onClick = { snackMsg = null }) { Text("关闭") }
                        }
                    ) { Text(shown ?: lastMsg[0]) }
                }
                LaunchedEffect(shown) {
                    if (shown != null) {
                        kotlinx.coroutines.delay(2500)
                        snackMsg = null
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Row(Modifier.fillMaxSize()) {
                    FileListPane(
                        state = left,
                        onItemClick = {
                            viewModel.switchActivePane(PaneSide.LEFT)
                            viewModel.onItemClick(PaneSide.LEFT, it)
                        },
                        onItemLongClick = {
                            viewModel.switchActivePane(PaneSide.LEFT)
                            viewModel.onItemLongClick(PaneSide.LEFT, it)
                        },
                        onSwipeSelect = { viewModel.toggleSelect(PaneSide.LEFT, it.path) },
                        longPressItem = if (viewModel.longPressSide == PaneSide.LEFT) viewModel.longPressItem else null,
                        menuContent = { item -> paneMenu(PaneSide.LEFT, item) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .activateOnTouch { viewModel.switchActivePane(PaneSide.LEFT) }
                    )

                    FileListPane(
                        state = right,
                        onItemClick = {
                            viewModel.switchActivePane(PaneSide.RIGHT)
                            viewModel.onItemClick(PaneSide.RIGHT, it)
                        },
                        onItemLongClick = {
                            viewModel.switchActivePane(PaneSide.RIGHT)
                            viewModel.onItemLongClick(PaneSide.RIGHT, it)
                        },
                        onSwipeSelect = { viewModel.toggleSelect(PaneSide.RIGHT, it.path) },
                        longPressItem = if (viewModel.longPressSide == PaneSide.RIGHT) viewModel.longPressItem else null,
                        menuContent = { item -> paneMenu(PaneSide.RIGHT, item) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .activateOnTouch { viewModel.switchActivePane(PaneSide.RIGHT) }
                    )
                }

                // MT 风格：三条边阴影始终投在「非活动视窗」一侧，
                // 切换活动窗口时整体镜像。覆盖层不消费触摸事件。
                DualPaneShadows(
                    activeSide = active,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    }

    // 压暗层：不拦截触摸（没有 pointerInput），触摸由上面的覆盖层页面自己消费
    if (overlayProgress > 0.001f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f * overlayProgress))
        )
    }

    OverlayHost(overlay) { ov ->
        when (ov) {
            is Overlay.Tool -> ToolPage(
                action = ov.action,
                viewModel = viewModel,
                activeItems = activeState.items,
                activeSelected = activeState.selectedItems,
                activePath = activeState.currentPath,
                fullApkPath = fullApkPath,
                onClose = { currentTool = null }
            )
            is Overlay.Image -> ImageViewerScreen(
                initialPath = ov.path,
                onClose = { viewModel.dismissImageViewer() }
            )
            is Overlay.Hex -> HexViewerScreen(
                initialPath = ov.path,
                onClose = { viewModel.dismissHexViewer() }
            )
            is Overlay.Media -> MediaPlayerScreen(
                paths = ov.paths,
                startIndex = viewModel.mediaStartIndex,
                onClose = { viewModel.dismissMediaPlayer() },
                onOpenSystem = { path ->
                    viewModel.dismissMediaPlayer()
                    viewModel.openWithSystem(FileItem.fromFile(java.io.File(path)))
                }
            )
            Overlay.Editor -> TextEditorScreen(
                path = viewModel.textEditorPath,
                initialContent = viewModel.textEditorContent,
                readOnly = viewModel.textEditorReadOnly,
                wrap = false,
                charset = viewModel.textEditorCharset,
                fileBytes = viewModel.textEditorBytes,
                onSave = { content, cs -> viewModel.saveTextEditor(content, cs) },
                onClose = { viewModel.showTextEditor = false }
            )
        }
    }
    }

    // ------------------------------------------------------------- dialogs
    if (viewModel.showJumpDialog) {
        JumpPathDialog(
            currentPath = activeState.currentPath,
            onDismiss = { viewModel.showJumpDialog = false },
            onConfirm = {
                viewModel.navigateTo(active, it)
                viewModel.showJumpDialog = false
            }
        )
    }

    if (viewModel.showNewDialog) {
        NewFileDialog(
            onDismiss = { viewModel.showNewDialog = false },
            onCreate = { name, isDir -> viewModel.createNew(name, isDir) }
        )
    }

    if (viewModel.showSortDialog) {
        SortDialog(
            currentSort = activeState.sortBy,
            ascending = activeState.sortAscending,
            onDismiss = { viewModel.showSortDialog = false },
            onConfirm = { sort, asc -> viewModel.setSort(sort, asc) }
        )
    }

    if (viewModel.showSearchDialog) {
        SearchDialog(
            initialQuery = viewModel.searchQuery,
            initialContent = viewModel.searchContent,
            onDismiss = { viewModel.showSearchDialog = false },
            onConfirm = { query, content ->
                viewModel.searchQuery = query
                viewModel.searchContent = content
                viewModel.runSearch(active)
            }
        )
    }

    if (viewModel.showFilterDialog) {
        FilterDialog(
            initialQuery = activeState.filterQuery,
            onDismiss = { viewModel.showFilterDialog = false },
            onApply = { q ->
                viewModel.setFilter(active, q)
                viewModel.showFilterDialog = false
            }
        )
    }

    viewModel.renameTarget?.let { target ->
        if (viewModel.showRenameDialog) {
            RenameDialog(
                item = target,
                onDismiss = { viewModel.showRenameDialog = false },
                onConfirm = { viewModel.confirmRename(it) }
            )
        }
    }

    if (viewModel.showPropertiesDialog) {
        viewModel.propertiesItem?.let { item ->
            PropertiesDialog(
                item = item,
                apkInfo = viewModel.apkInfo,
                onDismiss = {
                    viewModel.showPropertiesDialog = false
                    viewModel.apkInfo = null
                },
                onOpenApkInfo = {
                    viewModel.showPropertiesDialog = false
                    fullApkPath = item.path
                    currentTool = ToolAction.APK_INFO
                }
            )
        }
    }

    if (viewModel.showConflictDialog) {
        ConflictDialog(
            name = viewModel.conflictName,
            onResolve = { viewModel.resolveConflict(it) }
        )
    }

    if (viewModel.showZipCreateDialog) {
        ZipCreateDialog(
            currentPath = activeState.currentPath,
            count = activeState.selectedItems.size.takeIf { it > 0 } ?: viewModel.zipSingleCount(),
            onDismiss = { viewModel.showZipCreateDialog = false },
            onConfirm = { name, pwd, format -> viewModel.createArchiveFromSelection(active, name, pwd, format) }
        )
    }

    if (viewModel.showBatchRenameDialog) {
        BatchRenameDialog(
            onDismiss = { viewModel.showBatchRenameDialog = false },
            onConfirm = { find, replace ->
                viewModel.batchFind = find
                viewModel.batchReplace = replace
                viewModel.batchRenameSelected(active)
            }
        )
    }

    if (viewModel.showChmodDialog) {
        ChmodDialog(
            onDismiss = { viewModel.showChmodDialog = false },
            onConfirm = { viewModel.chmodSelected(it) }
        )
    }

    if (viewModel.showBookmarkAddDialog) {
        BookmarkAddDialog(
            currentPath = activeState.currentPath,
            onDismiss = { viewModel.showBookmarkAddDialog = false },
            onConfirm = { name, path -> viewModel.addBookmark(name, path) }
        )
    }

    if (viewModel.showDeleteConfirm) {
        val pending = viewModel.pendingDeleteList().ifEmpty {
            activeState.items.filter { it.path in activeState.selectedItems }
        }
        ConfirmDeleteDialog(
            count = pending.size,
            useRecycleBin = settings.useRecycleBin,
            onDismiss = {
                viewModel.showDeleteConfirm = false
                viewModel.clearPendingDeleteItems()
            },
            onConfirm = {
                viewModel.confirmDelete()
            }
        )
    }

    // ---- 打开方式 (图三) ----
    viewModel.openWithItem?.let { item ->
        if (viewModel.showOpenWithDialog) {
            OpenWithDialog(
                item = item,
                onDismiss = { viewModel.dismissOpenWith() },
                onChoice = { choice ->
                    viewModel.handleOpenChoice(item, choice)
                    viewModel.dismissOpenWith()
                }
            )
        }
    }

    // ---- APK 信息 (图四) ----
    viewModel.apkDialogItem?.let { item ->
        ApkInfoDialog(
            item = item,
            info = viewModel.apkDialogInfo,
            onDismiss = { viewModel.dismissApkDialog() },
            onInstall = { viewModel.installApk(item) },
            onShare = { viewModel.shareItem(item) },
            onBrowse = { viewModel.browseApkContents(item) },
            onFullInfo = {
                viewModel.dismissApkDialog()
                viewModel.showPropertiesDialog = false
                fullApkPath = item.path
                currentTool = ToolAction.APK_INFO
            }
        )
    }

    // ---- MT 风格：安装 APK 前二次确认 ----
    viewModel.installConfirmItem?.let { item ->
        InstallConfirmDialog(
            item = item,
            info = viewModel.installConfirmInfo,
            onDismiss = { viewModel.dismissInstallConfirm() },
            onConfirm = {
                viewModel.dismissInstallConfirm()
                viewModel.installApk(item)
            }
        )
    }

    // ---- 解压到... ----
    showExtractTo?.let { item ->
        // Default destination: a real writable directory. The other pane may be
        // browsing inside an archive (a zip:// virtual path) or a directory the
        // user cannot write to, so fall back through: other pane (if real) →
        // the archive's own folder → the primary storage root.
        val otherPath = if (active == PaneSide.LEFT) right.currentPath else left.currentPath
        val defaultDest = if (!otherPath.startsWith("zip://") && otherPath != "/") {
            otherPath
        } else {
            val archiveParent = java.io.File(item.path).parent
            if (!archiveParent.isNullOrBlank() && archiveParent != "/") archiveParent
            else "/storage/emulated/0"
        }
        ExtractToDialog(
            archiveName = item.name,
            initialDest = defaultDest,
            needsPassword = FileHelper.zipNeedsPassword(item.path),
            initialPassword = viewModel.rememberedZipPassword(item.path),
            onDismiss = { showExtractTo = null },
            onConfirm = { dest, pwd ->
                if (pwd.isNotBlank()) viewModel.rememberZipPassword(item.path, pwd)
                viewModel.extractZipAt(active, item.path, dest, pwd)
                showExtractTo = null
            }
        )
    }

    // ---- 多选属性汇总 ----
    if (viewModel.showMultiPropsDialog) {
        MultiPropertiesDialog(
            items = viewModel.multiPropsItems,
            onDismiss = { viewModel.dismissMultiProperties() }
        )
    }

    // ---- 哈希结果 ----
    viewModel.hashResult?.let { (name, digest) ->
        HashResultDialog(
            name = name,
            digest = digest,
            onDismiss = { viewModel.dismissHashResult() }
        )
    }

    if (viewModel.opProgress.active || viewModel.opProgress.finished) {
        OpProgressDialog(
            progress = viewModel.opProgress,
            onCancel = { viewModel.cancelOperation() },
            onDismiss = { viewModel.dismissProgress() }
        )
    }
}


/** True when "back" should still walk up the directory tree (stops at / and the storage root). */
private fun canGoUp(path: String): Boolean {
    if (path.startsWith("zip://")) return true
    val p = path.trimEnd('/').ifEmpty { "/" }
    if (p == "/" || p == "/storage/emulated/0" || p == "/sdcard") return false
    return java.io.File(p).parent != null
}

/**
 * Activates a pane as soon as a finger touches it (tap, scroll or swipe).
 * Observed in the Initial pass and never consumed, so child gestures
 * (list scrolling, swipe-select, long press) keep working.
 */
private fun Modifier.activateOnTouch(onTouch: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any { it.pressed && !it.previousPressed }) onTouch()
            }
        }
    }


/** 以对话框形式提示「开发中」的占位工具，不是全屏页。 */
private val DIALOG_TOOLS = setOf(
    ToolAction.PLUGINS,
    ToolAction.REMOTE,
    ToolAction.COLOR_PICKER,
    ToolAction.FTP,
    ToolAction.ACTIVITY_LOG
)

/** 当前显示在主界面之上的全屏页（用作转场的 key，相同 key 不会重复播放转场）。 */
private sealed interface Overlay {
    data class Tool(val action: ToolAction) : Overlay
    data class Image(val path: String) : Overlay
    data class Hex(val path: String) : Overlay
    data class Media(val paths: List<String>) : Overlay
    data object Editor : Overlay
}

@Composable
private fun ToolPage(
    action: ToolAction,
    viewModel: FileManagerViewModel,
    activeItems: List<FileItem>,
    activeSelected: Set<String>,
    activePath: String,
    fullApkPath: String?,
    onClose: () -> Unit
) {
    val firstSelectedFile = activeItems
        .firstOrNull { it.path in activeSelected && !it.isDirectory }?.path
    when (action) {
        ToolAction.TERMINAL -> TerminalScreen(onClose = onClose)
        ToolAction.SETTINGS -> SettingsScreen(onClose = onClose)
        ToolAction.BOOKMARKS -> BookmarksScreen(
            bookmarks = viewModel.bookmarkList,
            onOpen = { viewModel.navigateTo(viewModel.activePane, it) },
            onRemove = { viewModel.removeBookmark(it) },
            onClose = onClose
        )
        ToolAction.TRASH -> TrashScreen(
            onClose = onClose,
            onEmpty = { viewModel.emptyTrash() }
        )
        ToolAction.TEXT_EDITOR -> TextEditorScreen(
            path = "/storage/emulated/0/MA-note.txt",
            initialContent = "",
            onSave = { content, _ ->
                viewModel.textEditorPath = "/storage/emulated/0/MA-note.txt"
                viewModel.saveTextEditor(content)
                onClose()
            },
            onClose = onClose
        )
        ToolAction.APK_EXTRACT -> ApkExtractorScreen(onClose = onClose)
        ToolAction.APK_INFO -> {
            val p = fullApkPath
                ?: viewModel.propertiesItem?.path
                ?: firstSelectedFile
                ?: activePath
            ApkInfoScreen(apkPath = p, onClose = onClose)
        }
        ToolAction.IMAGE_VIEWER -> ImageViewerScreen(initialPath = firstSelectedFile, onClose = onClose)
        ToolAction.HEX_VIEWER -> HexViewerScreen(initialPath = firstSelectedFile, onClose = onClose)
        else -> {}
    }
}
