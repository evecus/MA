package com.ma.manager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.ma.manager.ui.theme.MAMotion
import com.ma.manager.ui.theme.pressClickable
import com.ma.manager.ui.theme.pressScale
import com.ma.manager.ui.theme.MAAlertDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ma.manager.util.CodeLanguage
import com.ma.manager.util.EditorThemes
import com.ma.manager.util.SyntaxHighlighter
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.launch

/**
 * MT 管理器风格的代码/文本编辑器（对齐 2.26.11 截图）：
 *  - 顶部工具栏图标均匀分布；图钉倾斜；撤销/重做按历史栈置灰
 *  - 信息行：文件名 · 行:列 · 字符编码（深底浅字，与工具栏间有细分隔线）
 *  - 行号槽：浅灰底 + 右侧竖线 + 连续行号（不做代码折叠）
 *  - 当前行浅黄高亮、自动换行箭头、缩进参考线
 *  - 语法配色：深藏青关键字 / 绿色字符串 / 灰色斜体注释
 *  - 底部符号栏：→ / + - * = <
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    path: String,
    initialContent: String,
    readOnly: Boolean = false,
    wrap: Boolean = false,
    charset: Charset = StandardCharsets.UTF_8,
    fileBytes: ByteArray? = null,
    onSave: (String, Charset) -> Unit,
    onClose: () -> Unit
) {
    val fileName = File(path).name
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val palette = if (isDark) EditorThemes.Dark else EditorThemes.Light

    var textValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialContent,
                selection = if (readOnly) TextRangeZero else TextRange(initialContent.length)
            )
        )
    }
    var dirty by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(!readOnly) }
    var currentCharset by remember { mutableStateOf(charset) }
    var lang by remember { mutableStateOf(CodeLanguage.detect(fileName)) }
    var langAuto by remember { mutableStateOf(true) }
    var wrapEnabled by remember { mutableStateOf(wrap) }
    var fontSize by remember { mutableStateOf(13f) }
    // MT 风格显示开关（均有真实状态支撑，不做无效菜单项）
    var showLineNumbers by remember { mutableStateOf(true) }
    var syntaxEnabled by remember { mutableStateOf(true) }
    var wrapArrowEnabled by remember { mutableStateOf(true) }
    var indentGuideEnabled by remember { mutableStateOf(true) }
    var monoFontEnabled by remember { mutableStateOf(true) }

    var showFind by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var matchIndex by remember { mutableStateOf(0) }
    var showGoto by remember { mutableStateOf(false) }
    var gotoText by remember { mutableStateOf("") }
    var showCharset by remember { mutableStateOf(false) }
    var showLang by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showCloseConfirm by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val undoStack = remember { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember { mutableStateListOf<TextFieldValue>() }
    var suppressPush by remember { mutableStateOf(false) }

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var editorHeightPx by remember { mutableStateOf(0) }
    val gutterMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()

    val lineHeightSp = fontSize * 1.4f
    val lineHeightPx = with(density) { lineHeightSp.sp.toPx() }
    val monoStyle = TextStyle(
        fontFamily = if (monoFontEnabled) FontFamily.Monospace else FontFamily.Default,
        fontSize = fontSize.sp,
        lineHeight = lineHeightSp.sp,
        color = palette.plain
    )
    // MT：当前行浅黄高亮（深色主题给对应低明度色）
    val currentLineBg = if (isDark) Color(0xFF3A3620) else Color(0xFFFDFAE5)

    val text = textValue.text
    val lines = remember(text) { text.split('\n') }
    val highlighted = remember(text, lang, palette, syntaxEnabled) {
        if (syntaxEnabled) SyntaxHighlighter.highlightLines(text, lang, palette) else null
    }
    val lineStarts = remember(lines) {
        val arr = IntArray(lines.size + 1)
        var acc = 0
        for (i in lines.indices) { arr[i] = acc; acc += lines[i].length + 1 }
        arr[lines.size] = acc
        arr
    }
    // MT 编辑器不做代码折叠：每个逻辑行都有连续行号。
    val visibleLines = remember(lines) { lines.indices.toList() }

    /** 原文本 offset 落在哪个逻辑行（用于缩进参考线与软换行标记）。 */
    fun logicalLineOf(offset: Int): Int {
        var lo = 0
        var hi = lines.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (lineStarts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
    }
    val padTopPx = with(density) { 4.dp.toPx() }

    // Exact top offset (content px) of the vi-th visible line, taken from the real
    // text layout so gutter numbers never drift from their lines; uniform-grid
    // fallback before the first layout pass.
    fun gutterLineTop(vi: Int): Float {
        val l = layoutResult
        return if (l != null && vi in 0 until l.lineCount) padTopPx + l.getLineTop(vi)
        else padTopPx + vi * lineHeightPx
    }

    // Inverse of gutterLineTop: content y -> original line index.
    fun gutterLineAt(y: Float): Int {
        val l = layoutResult
        val vi = if (l != null && l.lineCount > 0) {
            var lo = 0
            var hi = l.lineCount - 1
            var ans = 0
            val target = y - padTopPx
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (l.getLineTop(mid) <= target) { ans = mid; lo = mid + 1 } else hi = mid - 1
            }
            ans
        } else ((y - padTopPx) / lineHeightPx).toInt().coerceAtLeast(0)
        return visibleLines.getOrElse(vi) { lines.size - 1 }
    }

    val transformation = remember(highlighted, lines) {
        EditorTransformation(highlighted)
    }

    // Cursor position (line:col) for the info row.
    val cursorOffset = textValue.selection.start.coerceIn(0, text.length)
    val cursorLine = remember(text, cursorOffset) { text.substring(0, cursorOffset).count { it == '\n' } + 1 }
    val cursorCol = remember(text, cursorOffset) {
        cursorOffset - text.lastIndexOf('\n', (cursorOffset - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 } + 1
    }

    val matches = remember(findText, text) {
        if (findText.isEmpty()) emptyList()
        else {
            val list = mutableListOf<Int>()
            var idx = text.indexOf(findText)
            while (idx >= 0) { list.add(idx); idx = text.indexOf(findText, idx + findText.length) }
            list
        }
    }

    fun pushHistory(v: TextFieldValue) {
        if (suppressPush) return
        undoStack.add(v)
        if (undoStack.size > 100) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun onEdit(nv: TextFieldValue) {
        val old = textValue
        var result = nv
        // Auto-indent: typing a newline carries over the current line's indentation.
        if (nv.text.length == old.text.length + 1) {
            val p = commonPrefixLength(old.text, nv.text)
            if (p < nv.text.length && nv.text[p] == '\n') {
                val lineStart = old.text.lastIndexOf('\n', p - 1) + 1
                val indent = old.text.substring(lineStart).takeWhile { it == ' ' || it == '\t' }
                if (indent.isNotEmpty()) {
                    val newText = StringBuilder(nv.text).insert(p + 1, indent).toString()
                    result = TextFieldValue(newText, TextRange(p + 1 + indent.length))
                }
            }
        }
        pushHistory(old)
        textValue = result
        dirty = true
    }

    fun insertSymbol(sym: String) {
        val sel = textValue.selection
        val newText = text.replaceRange(sel.start, sel.end, sym)
        pushHistory(textValue)
        textValue = TextFieldValue(newText, TextRange(sel.start + sym.length))
        dirty = true
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        suppressPush = true
        redoStack.add(textValue)
        textValue = prev
        suppressPush = false
        dirty = true
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        suppressPush = true
        undoStack.add(textValue)
        textValue = next
        suppressPush = false
        dirty = true
    }

    fun jumpToLine(line: Int) {
        val idx = (line - 1).coerceIn(0, lines.size - 1)
        val offset = lineStarts[idx]
        suppressPush = true
        textValue = textValue.copy(selection = TextRange(offset, offset))
        suppressPush = false
        scope.launch {
            layoutResult?.let { layout ->
                val rect = layout.getCursorRect(offset)
                vScroll.scrollTo(rect.top.toInt())
                if (!wrapEnabled) hScroll.scrollTo(rect.left.toInt())
            }
        }
    }

    // Keep the cursor visible while typing / navigating.
    LaunchedEffect(textValue.selection, text, viewport) {
        val layout = layoutResult ?: return@LaunchedEffect
        if (viewport.height == 0) return@LaunchedEffect
        val rect = layout.getCursorRect(cursorOffset)
        val top = vScroll.value.toFloat()
        val bottom = top + viewport.height
        if (rect.top < top) vScroll.scrollTo(rect.top.toInt())
        else if (rect.bottom > bottom) vScroll.scrollTo((rect.bottom - viewport.height).toInt().coerceAtLeast(0))
        if (!wrapEnabled && viewport.width > 0) {
            val left = hScroll.value.toFloat()
            val right = left + viewport.width
            if (rect.left < left) hScroll.scrollTo(rect.left.toInt())
            else if (rect.right > right) hScroll.scrollTo((rect.right - viewport.width).toInt().coerceAtLeast(0))
        }
    }

    fun requestClose() {
        if (dirty) showCloseConfirm = true else onClose()
    }

    // System back: collapse the find/replace bar first, otherwise behave like the close button
    // (prompts to save when there are unsaved changes).
    BackHandler {
        if (showFind) showFind = false else requestClose()
    }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            Surface(
                color = com.ma.manager.ui.theme.MAStyle.ToolbarBg,
                contentColor = androidx.compose.ui.graphics.Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 4.dp
            ) {
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                    // Row 1: MA-style icon toolbar
                    Row(
                        Modifier.fillMaxWidth().height(44.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            EditorBarIcon(Icons.Default.Menu, "菜单") { showMenu = true }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (dirty) "保存 *" else "保存") },
                                    onClick = { showMenu = false; onSave(text, currentCharset); dirty = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("保存并退出") },
                                    onClick = {
                                        showMenu = false
                                        onSave(text, currentCharset)
                                        dirty = false
                                        onClose()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("查找替换") },
                                    onClick = { showMenu = false; showFind = !showFind }
                                )
                                DropdownMenuItem(
                                    text = { Text("转到指定行") },
                                    onClick = { showMenu = false; showGoto = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("指定编码重新加载") },
                                    onClick = { showMenu = false; showCharset = true }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (syntaxEnabled) "✓ 语法高亮" else "语法高亮") },
                                    onClick = { showMenu = false; syntaxEnabled = !syntaxEnabled }
                                )
                                DropdownMenuItem(
                                    text = { Text("语法语言") },
                                    onClick = { showMenu = false; showLang = true }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (editMode) "只读模式" else "关闭只读模式") },
                                    onClick = { showMenu = false; editMode = !editMode }
                                )
                                DropdownMenuItem(
                                    text = { Text("重新加载文件") },
                                    onClick = {
                                        showMenu = false
                                        val bytes = fileBytes
                                        if (bytes != null) {
                                            val decoded = runCatching { String(bytes, currentCharset) }.getOrNull()
                                            if (decoded != null) {
                                                undoStack.clear(); redoStack.clear()
                                                textValue = TextFieldValue(decoded, TextRange(0))
                                                dirty = false
                                                statusMsg = "已重新加载文件"
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("关闭文件") },
                                    onClick = { showMenu = false; requestClose() }
                                )
                            }
                        }
                        EditorBarIcon(
                            icon = Icons.Default.PushPin,
                            desc = "转到指定行",
                            onClick = { showGoto = true },
                            rotation = -45f
                        )
                        EditorBarIcon(
                            icon = Icons.AutoMirrored.Filled.Undo,
                            desc = "撤销",
                            onClick = { undo() },
                            enabled = undoStack.isNotEmpty()
                        )
                        EditorBarIcon(
                            icon = Icons.AutoMirrored.Filled.Redo,
                            desc = "重做",
                            onClick = { redo() },
                            enabled = redoStack.isNotEmpty()
                        )
                        EditorBarIcon(Icons.Default.Save, "保存") {
                            onSave(text, currentCharset)
                            dirty = false
                        }
                        EditorBarIcon(
                            Icons.Default.Edit,
                            if (editMode) "只读模式" else "关闭只读模式"
                        ) { editMode = !editMode }
                        Box {
                            EditorBarIcon(Icons.Default.MoreVert, "更多") { showMore = true }
                            DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            DropdownMenuItem(
                                text = { Text(if (wrapEnabled) "✓ 自动换行" else "自动换行") },
                                leadingIcon = { Icon(Icons.Default.WrapText, null) },
                                onClick = { showMore = false; wrapEnabled = !wrapEnabled }
                            )
                            DropdownMenuItem(
                                text = { Text(if (wrapArrowEnabled) "✓ 显示自动换行箭头" else "显示自动换行箭头") },
                                onClick = { showMore = false; wrapArrowEnabled = !wrapArrowEnabled }
                            )
                            DropdownMenuItem(
                                text = { Text(if (showLineNumbers) "✓ 显示行号信息" else "显示行号信息") },
                                onClick = { showMore = false; showLineNumbers = !showLineNumbers }
                            )
                            DropdownMenuItem(
                                text = { Text(if (indentGuideEnabled) "✓ 显示缩进参考线" else "显示缩进参考线") },
                                onClick = { showMore = false; indentGuideEnabled = !indentGuideEnabled }
                            )
                            DropdownMenuItem(
                                text = { Text(if (monoFontEnabled) "✓ 等宽字体" else "等宽字体") },
                                onClick = { showMore = false; monoFontEnabled = !monoFontEnabled }
                            )
                            DropdownMenuItem(
                                text = { Text("增大字号") },
                                leadingIcon = { Icon(Icons.Default.FormatSize, null) },
                                onClick = {
                                    showMore = false
                                    fontSize = (fontSize + 1f).coerceIn(9f, 24f)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("减小字号") },
                                leadingIcon = { Icon(Icons.Default.FormatSize, null) },
                                onClick = {
                                    showMore = false
                                    fontSize = (fontSize - 1f).coerceIn(9f, 24f)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (editMode) "只读模式" else "关闭只读模式") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { showMore = false; editMode = !editMode }
                            )
                            DropdownMenuItem(
                                text = { Text("全选") },
                                onClick = {
                                    showMore = false
                                    suppressPush = true
                                    textValue = textValue.copy(selection = TextRange(0, text.length))
                                    suppressPush = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("关闭文件") },
                                onClick = { showMenu = false; showMore = false; requestClose() }
                            )
                            }
                        }
                    }
                    // MT：图标行与信息行之间一条细分隔线
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF4A4A4A))
                    )
                    // Row 2: file name · line:col · charset
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            fileName + if (dirty) " •" else "",
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "$cursorLine:$cursorCol",
                            fontSize = 11.sp,
                            color = Color(0xFFCCCCCC),
                            modifier = Modifier.clickable { showGoto = true }.padding(horizontal = 6.dp)
                        )
                        Text(
                            charsetName(currentCharset),
                            fontSize = 11.sp,
                            color = Color(0xFFCCCCCC),
                            modifier = Modifier.clickable { showCharset = true }.padding(horizontal = 4.dp)
                        )
                    }
                    AnimatedVisibility(
                        visible = showFind,
                        enter = expandVertically(tween(MAMotion.Medium, easing = MAMotion.Standard)) + fadeIn(tween(MAMotion.Short)),
                        exit = shrinkVertically(tween(MAMotion.Short, easing = MAMotion.Standard)) + fadeOut(tween(MAMotion.Fast))
                    ) {
                        FindReplaceBar(
                            findText = findText,
                            onFindChange = { findText = it; matchIndex = 0 },
                            replaceText = replaceText,
                            onReplaceChange = { replaceText = it },
                            matchCount = matches.size,
                            matchIndex = matchIndex,
                            onPrev = {
                                if (matches.isNotEmpty()) {
                                    matchIndex = (matchIndex - 1 + matches.size) % matches.size
                                    textValue = selectMatch(text, matches[matchIndex], findText.length)
                                }
                            },
                            onNext = {
                                if (matches.isNotEmpty()) {
                                    matchIndex = (matchIndex + 1) % matches.size
                                    textValue = selectMatch(text, matches[matchIndex], findText.length)
                                }
                            },
                            onReplaceOne = {
                                val m = matches.getOrNull(matchIndex)
                                if (m != null) {
                                    val newText = text.replaceRange(m, m + findText.length, replaceText)
                                    pushHistory(textValue)
                                    textValue = TextFieldValue(newText, TextRange(m + replaceText.length))
                                    dirty = true
                                }
                            },
                            onReplaceAll = {
                                if (findText.isNotEmpty()) {
                                    val count = matches.size
                                    val newText = text.replace(findText, replaceText)
                                    pushHistory(textValue)
                                    textValue = TextFieldValue(newText, TextRange(0))
                                    dirty = true
                                    statusMsg = "已替换 $count 处"
                                }
                            },
                            onClose = { showFind = false }
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().height(46.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("→", "/", "+", "-", "*", "=", "<").forEach { sym ->
                        Text(
                            sym,
                            style = monoStyle.copy(fontSize = 17.sp),
                            modifier = Modifier
                                .pressClickable(pressedScale = 0.78f) { insertSymbol(sym) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(palette.background)
                .onGloballyPositioned { viewport = it.size }
        ) {
            // One vertical scroll container shared by the gutter and the text field,
            // so line numbers stay locked to their lines at every scroll offset.
            Column(Modifier.fillMaxWidth().verticalScroll(vScroll)) {
            Row(Modifier.fillMaxWidth()) {
                // Line-number + fold gutter (only meaningful without soft wrap)
                if (!wrapEnabled && showLineNumbers) {
                    // MT：行号槽约 42dp，浅灰底 + 右侧细竖线，只有连续行号。
                    val gutterWidth = 42.dp
                    val contentHeight = with(density) {
                        if (editorHeightPx > 0) editorHeightPx.toDp()
                        else ((lines.size + 1) * lineHeightPx).toDp()
                    }
                    val gutterStyle = monoStyle.copy(
                        fontSize = (fontSize * 0.82f).sp,
                        lineHeight = lineHeightSp.sp
                    )
                    Canvas(
                        Modifier
                            .width(gutterWidth)
                            .height(contentHeight)
                            .background(if (isDark) Color(0xFF1B1B1B) else Color(0xFFF8F8F8))
                            .pointerInput(lines.size, lineHeightPx) {
                                detectTapGestures { pos ->
                                    val line = gutterLineAt(pos.y)
                                    val start = lineStarts[line]
                                    val end = (start + lines[line].length).coerceAtMost(text.length)
                                    suppressPush = true
                                    textValue = textValue.copy(selection = TextRange(start, end))
                                    suppressPush = false
                                }
                            }
                    ) {
                        val first = ((vScroll.value - padTopPx) / lineHeightPx).toInt().coerceAtLeast(0)
                        val count = (viewport.height / lineHeightPx).toInt() + 3
                        val rightPad = with(density) { 10.dp.toPx() }
                        // 右侧竖直分隔线
                        drawLine(
                            color = if (isDark) Color(0xFF333333) else Color(0xFFE6E6E6),
                            start = Offset(size.width - 1f, 0f),
                            end = Offset(size.width - 1f, size.height),
                            strokeWidth = with(density) { 1.dp.toPx() }
                        )
                        for (vi in first until minOf(visibleLines.size, first + count)) {
                            val i = visibleLines[vi]
                            val y = gutterLineTop(vi)
                            val label = "${i + 1}"
                            val w = gutterMeasurer.measure(label, gutterStyle).size.width
                            drawText(
                                gutterMeasurer, label,
                                topLeft = Offset(size.width - rightPad - w.toFloat(), y),
                                style = gutterStyle.copy(
                                    color = if (i == cursorLine - 1) palette.gutterActive else palette.gutter
                                )
                            )
                        }
                    }
                }

                // Editor surface
                Box(
                    Modifier
                        .weight(1f)
                        .then(if (wrapEnabled) Modifier.fillMaxWidth() else Modifier.horizontalScroll(hScroll))
                        .onGloballyPositioned { editorHeightPx = it.size.height }
                ) {
                    if (text.isEmpty()) {
                        Text(
                            if (readOnly) "只读文件" else "空文件 — 开始输入",
                            style = monoStyle.copy(color = palette.gutter),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    BasicTextField(
                        value = textValue,
                        onValueChange = { nv ->
                            if (nv.text != textValue.text) onEdit(nv) else textValue = nv
                        },
                        readOnly = !editMode,
                        textStyle = monoStyle,
                        cursorBrush = SolidColor(palette.cursor),
                        visualTransformation = transformation,
                        onTextLayout = { layoutResult = it },
                        modifier = Modifier
                            .then(
                                if (wrapEnabled) Modifier.fillMaxWidth()
                                else Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .drawBehind {
                                val layout = layoutResult ?: return@drawBehind
                                val count = layout.lineCount
                                if (count == 0) return@drawBehind

                                // 1) 当前行浅黄高亮
                                val cur = (cursorLine - 1).coerceIn(0, count - 1)
                                val top = layout.getLineTop(cur)
                                val bottom = layout.getLineBottom(cur)
                                drawRect(
                                    color = currentLineBg,
                                    topLeft = Offset(0f, top),
                                    size = Size(size.width, bottom - top)
                                )

                                // 2) 缩进参考线
                                if (indentGuideEnabled) {
                                    val charW = with(density) { (fontSize * 0.6f).sp.toPx() }
                                    val guideColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFE6E6E6)
                                    val first = ((vScroll.value - padTopPx) / lineHeightPx).toInt().coerceAtLeast(0)
                                    val last = (first + viewport.height / lineHeightPx + 3).toInt()
                                    for (li in first until minOf(count, last)) {
                                        val offset = layout.getLineStart(li).coerceIn(0, text.length)
                                        val srcLine = lines.getOrNull(logicalLineOf(offset)) ?: continue
                                        val spaces = srcLine.takeWhile { it == ' ' }.length
                                        if (spaces <= 0) continue
                                        val x = spaces.toFloat() * charW - charW * 0.5f
                                        drawLine(
                                            guideColor,
                                            Offset(x, layout.getLineTop(li)),
                                            Offset(x, layout.getLineBottom(li)),
                                            with(density) { 1.dp.toPx() }
                                        )
                                    }
                                }

                                // 3) 自动换行箭头 ↵ + 续行折角 ⌞
                                if (wrapEnabled && wrapArrowEnabled) {
                                    val arrowStyle = TextStyle(
                                        color = if (isDark) Color(0xFF666666) else Color(0xFFB0B0B0),
                                        fontSize = (fontSize * 0.8f).sp
                                    )
                                    for (li in 0 until count - 1) {
                                        val end = layout.getLineEnd(li)
                                        val soft = end < text.length && text[end] != '\n'
                                        if (!soft) continue
                                        drawText(
                                            gutterMeasurer, "↵",
                                            topLeft = Offset(
                                                size.width - with(density) { 16.dp.toPx() },
                                                layout.getLineTop(li)
                                            ),
                                            style = arrowStyle
                                        )
                                        drawText(
                                            gutterMeasurer, "⌞",
                                            topLeft = Offset(0f, layout.getLineTop(li + 1)),
                                            style = arrowStyle
                                        )
                                    }
                                }
                            }
                    )
                }
            }
            }

            // 状态提示：从底部滑入、淡入；消失时完整滑出（保留最后一条文字）
            val shownMsg = statusMsg
            val lastStatus = remember { arrayOf("") }
            if (shownMsg != null) lastStatus[0] = shownMsg
            AnimatedVisibility(
                visible = shownMsg != null,
                enter = slideInVertically(tween(MAMotion.Medium, easing = MAMotion.EmphasizedDecelerate)) { it } +
                    fadeIn(tween(MAMotion.Short)),
                exit = slideOutVertically(tween(MAMotion.Short, easing = MAMotion.EmphasizedAccelerate)) { it } +
                    fadeOut(tween(MAMotion.Short)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        shownMsg ?: lastStatus[0],
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------- dialogs

    if (showGoto) {
        MAAlertDialog(
            onDismissRequest = { showGoto = false },
            title = { Text("转到指定行") },
            text = {
                OutlinedTextField(
                    value = gotoText,
                    onValueChange = { gotoText = it.filter { ch -> ch.isDigit() } },
                    singleLine = true,
                    label = { Text("行号 (1 - ${lines.size})") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val line = gotoText.toIntOrNull()
                    if (line != null) jumpToLine(line)
                    showGoto = false
                }) { Text("跳转") }
            },
            dismissButton = { TextButton(onClick = { showGoto = false }) { Text("取消") } }
        )
    }

    if (showCharset) {
        val options = listOf(
            "UTF-8" to StandardCharsets.UTF_8,
            "UTF-16 LE" to Charset.forName("UTF-16LE"),
            "UTF-16 BE" to Charset.forName("UTF-16BE"),
            "GBK" to Charset.forName("GBK"),
            "GB18030" to Charset.forName("GB18030"),
            "Big5" to Charset.forName("Big5"),
            "ISO-8859-1" to StandardCharsets.ISO_8859_1,
            "Windows-1252" to Charset.forName("windows-1252"),
            "US-ASCII" to StandardCharsets.US_ASCII
        )
        MAAlertDialog(
            onDismissRequest = { showCharset = false },
            title = { Text("字符编码") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { (name, cs) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCharset = false
                                    if (cs == currentCharset) return@clickable
                                    currentCharset = cs
                                    val bytes = fileBytes
                                    if (bytes != null) {
                                        val decoded = runCatching { String(bytes, cs) }.getOrNull()
                                        if (decoded != null) {
                                            undoStack.clear(); redoStack.clear()
                                            textValue = TextFieldValue(decoded, TextRange(0))
                                            dirty = true
                                            statusMsg = "已按 $name 重新解码"
                                        } else {
                                            statusMsg = "无法按 $name 解码，仅更改保存编码"
                                        }
                                    } else {
                                        statusMsg = "保存编码已设为 $name"
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = cs == currentCharset, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCharset = false }) { Text("关闭") } }
        )
    }

    if (showLang) {
        MAAlertDialog(
            onDismissRequest = { showLang = false },
            title = { Text("语法高亮") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            showLang = false
                            langAuto = true
                            lang = CodeLanguage.detect(fileName)
                        }.padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = langAuto, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text("自动检测（${CodeLanguage.detect(fileName).displayName}）")
                    }
                    CodeLanguage.entries.forEach { cl ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                showLang = false
                                langAuto = false
                                lang = cl
                            }.padding(vertical = 6.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = !langAuto && lang == cl, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(cl.displayName, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLang = false }) { Text("关闭") } }
        )
    }

    if (showCloseConfirm) {
        MAAlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text("文件已修改") },
            text = { Text("是否保存对 $fileName 的更改？") },
            confirmButton = {
                TextButton(onClick = {
                    showCloseConfirm = false
                    onSave(text, currentCharset)
                    dirty = false
                    onClose()
                }) { Text("保存并退出") }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false; onClose() }) { Text("放弃更改") }
            }
        )
    }

    LaunchedEffect(statusMsg) {
        if (statusMsg != null) {
            kotlinx.coroutines.delay(2500)
            statusMsg = null
        }
    }
}

private val TextRangeZero = androidx.compose.ui.text.TextRange(0)

@Composable
private fun EditorBarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    enabled: Boolean = true,
    rotation: Float = 0f,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    // 启用 / 禁用（撤销、重做等）时图标颜色渐变；按下时缩放回弹
    val tint by animateColorAsState(
        if (enabled) Color.White else Color(0xFF757575),
        tween(MAMotion.Short),
        label = "barIconTint"
    )
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        modifier = Modifier.size(40.dp).pressScale(source, 0.8f)
    ) {
        Icon(
            icon,
            contentDescription = desc,
            tint = tint,
            modifier = Modifier
                .size(20.dp)
                .then(if (rotation != 0f) Modifier.rotate(rotation) else Modifier)
        )
    }
}

@Composable
private fun FindReplaceBar(
    findText: String,
    onFindChange: (String) -> Unit,
    replaceText: String,
    onReplaceChange: (String) -> Unit,
    matchCount: Int,
    matchIndex: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReplaceOne: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
    Column(Modifier.padding(horizontal = 8.dp).padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = findText,
                onValueChange = onFindChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("查找内容", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(16.dp)) },
                trailingIcon = {
                    Text(
                        if (matchCount == 0) "0/0" else "${matchIndex + 1}/$matchCount",
                        fontSize = 11.sp
                    )
                }
            )
            IconButton(onClick = onPrev) { Text("↑", fontSize = 14.sp) }
            IconButton(onClick = onNext) { Text("↓", fontSize = 14.sp) }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "关闭查找", Modifier.size(18.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = replaceText,
                onValueChange = onReplaceChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("替换为", fontSize = 12.sp) }
            )
            Spacer(Modifier.width(6.dp))
            FilledTonalButton(onClick = onReplaceOne, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("替换", fontSize = 12.sp)
            }
            Spacer(Modifier.width(4.dp))
            FilledTonalButton(onClick = onReplaceAll, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("全部", fontSize = 12.sp)
            }
        }
    }
    }
}

private fun charsetName(cs: Charset): String = when (cs.name()) {
    "UTF-8" -> "UTF-8"
    "UTF-16LE" -> "UTF-16 LE"
    "UTF-16BE" -> "UTF-16 BE"
    else -> cs.name().uppercase()
}

private fun commonPrefixLength(a: String, b: String): Int {
    val n = minOf(a.length, b.length)
    var i = 0
    while (i < n && a[i] == b[i]) i++
    return i
}

private fun selectMatch(text: String, start: Int, length: Int): TextFieldValue =
    TextFieldValue(text, TextRange(start, start + length))

/**
 * MT 风格：仅做语法高亮，不做代码折叠（行号连续）。
 */
private class EditorTransformation(
    private val highlighted: List<AnnotatedString>?
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder()
        if (highlighted != null) {
            for (i in highlighted.indices) {
                if (i > 0) builder.append('\n')
                builder.append(highlighted[i])
            }
        } else {
            builder.append(text.text)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
