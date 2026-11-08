package com.ma.manager.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

/**
 * 全应用统一动画规格。
 *
 * 只做「简单动画」：短时长 + 淡入淡出 + 轻微位移，不改变任何交互时序，
 * 也不给列表滚动 / Canvas 绘制加动画（会掉帧或破坏 offset 映射）。
 * 时长集中在此：调一处即可全局生效；要彻底关闭动画，把两个常量都置 0 即可。
 */
object MAAnim {

    /** 选中色、开关滑块、进度条等微反馈 */
    const val ShortMs = 160

    /** 页面切换、弹层进出 */
    const val MediumMs = 220

    /** 主文件列表页的 screenKey，用来判断前进 / 后退方向 */
    const val SCREEN_LIST = "list"

    /** Snackbar / 顶部提示：从下方滑入 */
    val ToastEnter: EnterTransition =
        fadeIn(tween(ShortMs)) + slideInVertically(tween(ShortMs)) { it / 2 }

    val ToastExit: ExitTransition =
        fadeOut(tween(ShortMs)) + slideOutVertically(tween(ShortMs)) { it / 2 }

    /**
     * 页面切换：进入全屏页时新页从右侧滑入、旧列表轻微左移淡出（视差）；
     * 返回列表时方向反过来。两个全屏页之间切换也按「前进」处理。
     */
    fun screenTransform(forward: Boolean): ContentTransform {
        val enter: EnterTransition
        val exit: ExitTransition
        if (forward) {
            enter = fadeIn(tween(MediumMs)) + slideInHorizontally(tween(MediumMs)) { it / 8 }
            exit = fadeOut(tween(ShortMs)) + slideOutHorizontally(tween(ShortMs)) { -it / 16 }
        } else {
            enter = fadeIn(tween(MediumMs)) + slideInHorizontally(tween(MediumMs)) { -it / 16 }
            exit = fadeOut(tween(ShortMs)) + slideOutHorizontally(tween(ShortMs)) { it / 8 }
        }
        return enter.togetherWith(exit)
    }
}
