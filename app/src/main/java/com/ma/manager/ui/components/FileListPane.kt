package com.ma.manager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import com.ma.manager.ui.theme.MAMotion
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.ma.manager.ui.theme.MAStyle
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ma.manager.data.FileItem
import com.ma.manager.data.FileKind
import com.ma.manager.data.PaneState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListPane(
    state: PaneState,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
    onSwipeSelect: (FileItem) -> Unit = {},
    longPressItem: FileItem? = null,
    menuContent: @Composable (FileItem) -> Unit = {}
) {
    val visible = state.visibleItems
    val listState = rememberLazyListState()

    // Scroll a jumped-to search result into view.
    LaunchedEffect(state.highlightPath) {
        val idx = visible.indexOfFirst { it.path == state.highlightPath }
        if (idx >= 0) runCatching { listState.animateScrollToItem(idx) }
    }

    // ---- 加载态判定 -------------------------------------------------------------
    // 「同一目录刷新」时保留旧列表（不闪烁）；只有真的切换了目录才进入加载态。
    val loadedPath = remember { arrayOfNulls<String>(1) }
    SideEffect { if (!state.isLoading) loadedPath[0] = state.currentPath }
    val navigating = state.isLoading && loadedPath[0] != state.currentPath
    val showEmpty = !navigating && visible.isEmpty()
    val showList = !navigating && visible.isNotEmpty()

    // 加载转圈延迟 150ms 才出现，本地目录秒开时完全不会闪。
    var spinnerReady by remember { mutableStateOf(false) }
    LaunchedEffect(navigating) {
        spinnerReady = false
        if (navigating) {
            kotlinx.coroutines.delay(150)
            spinnerReady = true
        }
    }

    // ---- 目录切换：进入子目录从右滑入，返回上级从左滑入，其它情况淡入 ----------------
    val path = state.currentPath
    val prevPath = remember { arrayOf(path) }
    val direction = remember(path) {
        val old = prevPath[0]
        when {
            old == path -> 0
            path.trimEnd('/').startsWith(old.trimEnd('/') + "/") || old == "/" -> 1
            old.trimEnd('/').startsWith(path.trimEnd('/') + "/") || path == "/" -> -1
            else -> 0
        }
    }
    SideEffect { prevPath[0] = path }
    val enter = remember(path) { Animatable(0f) }
    LaunchedEffect(path, showList, showEmpty) {
        if (showList || showEmpty) {
            enter.animateTo(1f, tween(MAMotion.Medium, easing = MAMotion.EmphasizedDecelerate))
        }
    }
    val slidePx = with(LocalDensity.current) { 36.dp.toPx() }

    // 搜索 / 过滤条退出动画期间，query 已变为 null，用这里保存的最后一次文字继续显示
    val lastQuery = remember { arrayOf("", "") }
    state.searchQuery?.let { lastQuery[0] = it }
    state.filterQuery?.let { lastQuery[1] = it }

    Column(modifier = modifier.fillMaxHeight().background(MAStyle.listBg())) {
        AnimatedVisibility(
            visible = state.searchQuery != null,
            enter = expandVertically(tween(MAMotion.Short)) + fadeIn(tween(MAMotion.Short)),
            exit = shrinkVertically(tween(MAMotion.Short)) + fadeOut(tween(MAMotion.Fast))
        ) {
            val q = state.searchQuery ?: lastQuery[0]
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "搜索: $q (${visible.size} 项)",
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.filterQuery != null,
            enter = expandVertically(tween(MAMotion.Short)) + fadeIn(tween(MAMotion.Short)),
            exit = shrinkVertically(tween(MAMotion.Short)) + fadeOut(tween(MAMotion.Fast))
        ) {
            val q = state.filterQuery ?: lastQuery[1]
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "过滤: $q (${visible.size}/${state.items.size})",
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            // ---- 加载中 ----
            BoxFade(
                visible = navigating && spinnerReady,
                enter = fadeIn(tween(MAMotion.Short)),
                exit = fadeOut(tween(MAMotion.Fast)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                androidx.compose.material3.CircularProgressIndicator()
            }

            // ---- 空目录 / 无结果 ----
            BoxFade(
                visible = showEmpty,
                enter = fadeIn(tween(MAMotion.Medium)) + scaleIn(tween(MAMotion.Medium), initialScale = 0.9f),
                exit = fadeOut(tween(MAMotion.Fast)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    if (state.searchQuery != null) "无匹配结果"
                    else if (state.filterQuery != null) "无匹配文件"
                    else "空文件夹",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }

            // ---- 列表 ----
            if (showList) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = enter.value
                            alpha = 0.25f + 0.75f * p
                            translationX = direction * slidePx * (1f - p)
                        },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    items(visible, key = { it.path }) { item ->
                        FileListItem(
                            item = item,
                            isSelected = item.path in state.selectedItems,
                            isHighlighted = item.path == state.highlightPath,
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongClick(item) },
                            onSwipeSelect = { onSwipeSelect(item) },
                            // 删除 / 重命名 / 排序后，条目平滑移动到新位置，新增条目淡入
                            modifier = Modifier.animateItem(),
                            menu = if (item == longPressItem) {
                                { menuContent(item) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    onSwipeSelect: () -> Unit = {},
    menu: (@Composable () -> Unit)? = null
) {
    // MT 风格：多选只用整行纯色高亮表达选中态，不叠加勾选图标。
    val bgTarget = when {
        isSelected -> MAStyle.selectedRowBg()
        isHighlighted -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
        else -> Color.Transparent
    }
    // 选中 / 取消选中 / 高亮：整行颜色渐变，而不是瞬间换色
    val bg by animateColorAsState(bgTarget, tween(MAMotion.Short), label = "rowBg")
    val thresholdPx = with(LocalDensity.current) { 88.dp.toPx() }

    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MAStyle.RowHeight)
                .background(bg)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .pointerInput(item.path) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = { total = 0f },
                        onDragCancel = { total = 0f },
                        onHorizontalDrag = { _, drag ->
                            total += drag
                            if (total > thresholdPx) {
                                onSwipeSelect()
                                total = 0f
                            }
                        }
                    )
                }
                .padding(start = 5.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileIconTile(item)
            Spacer(Modifier.width(4.dp))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    text = item.name,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MAStyle.nameColor(),
                    modifier = Modifier.paddingFromBaseline(top = 18.7.dp)
                )
                if (item.name != "..") {
                    Text(
                        text = buildString {
                            append(item.displayDate)
                            if (item.displaySize.isNotEmpty()) {
                                append("  ")
                                append(item.displaySize)
                            }
                            if (item.permission.isNotEmpty()) {
                                append("  ")
                                append(item.permission)
                            }
                        },
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        letterSpacing = 0.sp,
                        color = MAStyle.dateColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.paddingFromBaseline(top = 33.dp)
                    )
                }
            }
            // 多选不画勾选图标：选中态已由整行高亮表达（对齐 MT）。
        }
        menu?.invoke()
    }
}

// ------------------------------------------------------------------ file icons

/**
 * Tile colours per file kind. Only folders use the dark tile; every file kind
 * (including video and unknown files) gets its own non-dark colour so files
 * can never be mistaken for folders.
 */
private fun kindTileColor(kind: FileKind, dark: Boolean): Color? = when (kind) {
    FileKind.CODE -> Color(0xFF3B66C4)
    FileKind.TEXT -> Color(0xFF4F7FA8)
    FileKind.ARCHIVE -> Color(0xFF7B5548)
    FileKind.APK -> Color(0xFF4C9A52)
    FileKind.IMAGE -> Color(0xFF3E8E7E)
    FileKind.AUDIO -> Color(0xFF8E5BB5)
    FileKind.VIDEO -> Color(0xFFC25B4E)
    FileKind.OTHER -> Color(0xFF8C939D) // unrecognised files: one uniform grey
    FileKind.FOLDER -> null // only folders keep the dark tile
}

/** Small glyph drawn on top of / instead of the document icon. */
private fun badgeFor(kind: FileKind): ImageVector? = when (kind) {
    FileKind.CODE -> Icons.Default.Code
    FileKind.TEXT -> Icons.Default.Article
    FileKind.IMAGE -> Icons.Default.Image
    FileKind.VIDEO -> Icons.Default.Videocam
    FileKind.AUDIO -> Icons.Default.MusicNote
    else -> null
}

/**
 * Official-style 28dp rounded tile:
 *  - folder: dark tile + white folder
 *  - other files: per-type colour, white document with a type badge
 *  - image / video / apk: real thumbnail or app icon when it can be loaded
 */
@Composable
private fun FileIconTile(item: FileItem) {
    val shape = RoundedCornerShape(MAStyle.TileRadius)
    val kind = item.fileKind
    val dark = MAStyle.isDark()
    val tileColor = kindTileColor(kind, dark) ?: MAStyle.tile()
    val thumb = rememberThumb(item)

    Box(
        Modifier
            .size(MAStyle.TileSize)
            .shadow(1.5.dp, shape, clip = false)
            .background(tileColor, shape)
    ) {
        when {
            item.isDirectory -> FolderGlyph(Modifier.padding(start = 6.8.dp, top = 8.2.dp))
            thumb != null -> androidx.compose.foundation.Image(
                bitmap = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape)
            )
            kind == FileKind.ARCHIVE -> Icon(
                Icons.Default.Archive, null, tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(18.dp)
            )
            kind == FileKind.APK -> Icon(
                Icons.Default.Android, null, tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(18.dp)
            )
            else -> {
                // white document with a small badge cut into it
                Icon(
                    Icons.Default.InsertDriveFile, null, tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(18.dp)
                )
                val badge = badgeFor(kind)
                if (badge != null) {
                    Icon(
                        badge, null, tint = tileColor,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = 2.dp)
                            .size(9.dp)
                    )
                }
            }
        }
    }
}

private object ThumbCache {
    val cache = android.util.LruCache<String, ImageBitmap>(300)
    val failed: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())
    val gate = Semaphore(3)
}

@Composable
private fun rememberThumb(item: FileItem): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val kind = item.fileKind
    val wanted = !item.isDirectory && !item.isZipEntry &&
        (kind == FileKind.IMAGE || kind == FileKind.VIDEO || kind == FileKind.APK)
    val key = "${item.path}|${item.lastModified}|${item.size}"
    val state = produceState<ImageBitmap?>(
        initialValue = if (wanted) ThumbCache.cache.get(key) else null,
        key1 = key, key2 = wanted
    ) {
        if (!wanted) return@produceState
        ThumbCache.cache.get(key)?.let { value = it; return@produceState }
        if (key in ThumbCache.failed) return@produceState
        val bmp = ThumbCache.gate.withPermit {
            withContext(Dispatchers.IO) { loadThumb(context, item.path, kind) }
        }
        if (bmp != null) {
            ThumbCache.cache.put(key, bmp)
            value = bmp
        } else {
            ThumbCache.failed.add(key)
        }
    }
    return state.value
}

private const val THUMB_PX = 96

private fun loadThumb(context: android.content.Context, path: String, kind: FileKind): ImageBitmap? {
    return try {
        val file = java.io.File(path)
        if (!file.isFile || !file.canRead()) return null
        val bitmap: android.graphics.Bitmap? = when (kind) {
            FileKind.IMAGE -> {
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(path, opts)
                if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
                var sample = 1
                while (opts.outWidth / (sample * 2) >= THUMB_PX && opts.outHeight / (sample * 2) >= THUMB_PX) sample *= 2
                val real = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                android.graphics.BitmapFactory.decodeFile(path, real)
            }
            FileKind.VIDEO -> {
                val r = android.media.MediaMetadataRetriever()
                try {
                    r.setDataSource(path)
                    r.getFrameAtTime(-1, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    runCatching { r.release() }
                }
            }
            FileKind.APK -> {
                val d = com.ma.manager.util.ApkInfoUtil.loadIcon(context, path) ?: return null
                val w = if (d.intrinsicWidth > 0) d.intrinsicWidth.coerceAtMost(THUMB_PX * 2) else THUMB_PX
                val h = if (d.intrinsicHeight > 0) d.intrinsicHeight.coerceAtMost(THUMB_PX * 2) else THUMB_PX
                val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(b)
                d.setBounds(0, 0, w, h)
                d.draw(c)
                b
            }
            else -> null
        }
        bitmap?.asImageBitmap()
    } catch (e: Throwable) {
        null
    }
}

@Composable
private fun FolderGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier.size(13.8.dp, 10.9.dp)) {
        val d = density
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(5.7f * d, 0f)
            lineTo(6.85f * d, 1.25f * d)
            lineTo(13.8f * d, 1.25f * d)
            lineTo(13.8f * d, 10.9f * d)
            lineTo(0f, 10.9f * d)
            close()
        }
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                color = Color.White
                isAntiAlias = true
                pathEffect = PathEffect.cornerPathEffect(1.1f * d)
            }
            canvas.drawPath(path, paint)
        }
    }
}

private fun iconFor(item: FileItem): ImageVector = when (item.fileKind) {
    FileKind.FOLDER -> Icons.Default.Folder
    FileKind.ARCHIVE -> Icons.Default.FolderZip
    FileKind.APK -> Icons.Default.InstallMobile
    FileKind.IMAGE -> Icons.Default.Image
    FileKind.VIDEO -> Icons.Default.VideoFile
    FileKind.AUDIO -> Icons.Default.AudioFile
    FileKind.TEXT -> Icons.Default.Article
    FileKind.CODE -> Icons.Default.Code
    FileKind.OTHER -> Icons.Default.InsertDriveFile
}

private fun tintFor(item: FileItem): Color = when (item.fileKind) {
    FileKind.FOLDER -> Color(0xFF42A5F5)
    FileKind.ARCHIVE -> Color(0xFFAB47BC)
    FileKind.APK -> Color(0xFF66BB6A)
    FileKind.IMAGE -> Color(0xFFEF5350)
    FileKind.VIDEO -> Color(0xFFEF5350)
    FileKind.AUDIO -> Color(0xFFFFCA28)
    FileKind.TEXT -> Color(0xFF78909C)
    FileKind.CODE -> Color(0xFF26C6DA)
    FileKind.OTHER -> Color(0xFF78909C)
}


/** 不带 ColumnScope 接收者的 AnimatedVisibility 封装（嵌套在 Column 里的 Box 中直接调用会被作用域限制拒绝）。 */
@Composable
private fun BoxFade(
    visible: Boolean,
    enter: androidx.compose.animation.EnterTransition,
    exit: androidx.compose.animation.ExitTransition,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = enter, exit = exit) { content() }
}
