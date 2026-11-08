package com.ma.manager.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.ma.manager.R

/**
 * 全应用统一的动效参数。所有动画都从这里取时长与缓动，
 * 这样整个 App 的「手感」一致，也方便以后整体调快 / 调慢。
 */
object MAMotion {
    const val Fast = 120
    const val Short = 200
    const val Medium = 300
    const val Long = 360

    /** 进入：快速起步、柔和落定（Material 3 emphasized decelerate）。 */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** 退出：缓慢起步、快速离开（Material 3 emphasized accelerate）。 */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** 通用的标准缓动。 */
    val Standard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** 带一点回弹的 spring，用于按压反馈、开关滑块等小元素。 */
    fun <T> bouncy() = spring<T>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)

    /** 无回弹的 spring，用于尺寸 / 位置变化。 */
    fun <T> smooth() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}

// ---------------------------------------------------------------- 按压反馈

/** 按下时缩小、松开时回弹。[interactionSource] 需与 clickable 共用同一个。 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.9f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "pressScale"
    )
    graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

/** clickable + 按压缩放，保留系统水波纹。 */
fun Modifier.pressClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.9f,
    onClick: () -> Unit
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    this
        .pressScale(source, pressedScale)
        .clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )
}

// ---------------------------------------------------------------- 入场

/** 首次出现时上浮 + 淡入，[index] 越大越晚出现，用来做「依次浮现」的错落效果。 */
fun Modifier.staggerIn(index: Int, stepMillis: Int = 45): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * stepMillis).toLong())
        p.animateTo(1f, tween(MAMotion.Long, easing = MAMotion.EmphasizedDecelerate))
    }
    graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 24.dp.toPx()
    }
}

// ---------------------------------------------------------------- 对话框

/** 对话框内容弹出：从 0.88 放大到 1 并淡入。 */
fun Modifier.dialogPopIn(): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(MAMotion.Medium, easing = MAMotion.EmphasizedDecelerate))
    }
    graphicsLayer {
        val v = p.value
        alpha = (v * 1.6f).coerceAtMost(1f)
        val s = 0.88f + 0.12f * v
        scaleX = s
        scaleY = s
    }
}

/**
 * Compose 的 Dialog 是独立 Window，没有系统转场。这里给它的 Window 套上淡入 / 淡出动画，
 * 这样「关闭」时也有退出动画（遮罩会一起淡出，而不是瞬间消失）。
 */
@Composable
fun DialogWindowAnimation() {
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window
            ?.setWindowAnimations(R.style.MADialogWindowAnim)
    }
}

/** 带进出场动画的 AlertDialog，参数与 Material3 的 AlertDialog 一致，可直接替换。 */
@Composable
fun MAAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            DialogWindowAnimation()
            confirmButton()
        },
        modifier = modifier.dialogPopIn(),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        properties = properties
    )
}

/** 带进出场动画的通用 Dialog（长按菜单等自绘内容的弹窗用）。 */
@Composable
fun MADialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        DialogWindowAnimation()
        Box(Modifier.dialogPopIn()) { content() }
    }
}

// ---------------------------------------------------------------- 全屏页转场

/**
 * 全屏页面宿主：[target] 非空时，页面从右侧滑入；变回 null 时向右滑出。
 * 页面离开期间会保留最后一次的内容，所以退出动画是完整的。
 * 页面底板不透明且会吃掉触摸，下层界面不会被误触。
 */
@Composable
fun <T : Any> OverlayHost(
    target: T?,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    AnimatedContent(
        targetState = target,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            when {
                initialState == null ->
                    (slideInHorizontally(tween(MAMotion.Long, easing = MAMotion.EmphasizedDecelerate)) { it } +
                        fadeIn(tween(MAMotion.Short))) togetherWith ExitTransition.None
                targetState == null ->
                    EnterTransition.None togetherWith
                        (slideOutHorizontally(tween(MAMotion.Medium, easing = MAMotion.EmphasizedAccelerate)) { it } +
                            fadeOut(tween(MAMotion.Medium, delayMillis = 40)))
                else ->
                    fadeIn(tween(MAMotion.Medium)) togetherWith fadeOut(tween(MAMotion.Short))
            }
        },
        label = "overlayHost"
    ) { t ->
        if (t != null) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) { content(t) }
        } else {
            Spacer(Modifier.fillMaxSize())
        }
    }
}
