package com.ma.manager.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ma.manager.data.PaneSide

/**
 * Colors / metrics measured from the official MA Manager file browser
 * (screenshot, density 3.21875 px/dp).
 */
object MAStyle {
    val ToolbarBg = Color(0xFF303030)
    val ToolbarHeight: Dp = 56.dp
    val RowHeight: Dp = 40.dp
    val TileSize: Dp = 28.dp
    val TileRadius: Dp = 4.5.dp
    val ShadowSize: Dp = 5.6.dp

    @Composable
    fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

    @Composable fun listBg() = if (isDark()) Color(0xFF121212) else Color(0xFFFAFAFA)
    @Composable fun barBg() = if (isDark()) Color(0xFF1E1E1E) else Color(0xFFFAFAFA)
    @Composable fun tile() = if (isDark()) Color(0xFF3A3A3A) else Color(0xFF2B2B2B)
    @Composable fun nameColor() = if (isDark()) Color(0xFFEEEEEE) else Color(0xFF121212)
    @Composable fun dateColor() = if (isDark()) Color(0xFF9E9E9E) else Color(0xFF646464)
    @Composable fun barIcon() = if (isDark()) Color(0xFFBDBDBD) else Color(0xFF555555)
    @Composable fun barIconLight() = if (isDark()) Color(0xFF616161) else Color(0xFFB4B4B4)

    /** Shadow falloff measured from the screenshot (alpha of black, 0 = at the edge). */
    val shadowStops: Array<Pair<Float, Color>> = arrayOf(
        0.00f to Color.Black.copy(alpha = 0.416f),
        0.11f to Color.Black.copy(alpha = 0.31f),
        0.22f to Color.Black.copy(alpha = 0.216f),
        0.33f to Color.Black.copy(alpha = 0.16f),
        0.44f to Color.Black.copy(alpha = 0.13f),
        0.55f to Color.Black.copy(alpha = 0.10f),
        0.67f to Color.Black.copy(alpha = 0.064f),
        0.78f to Color.Black.copy(alpha = 0.032f),
        0.89f to Color.Black.copy(alpha = 0.008f),
        1.00f to Color.Transparent
    )

    /** Mirrored stops: transparent at 0, darkest at 1. Used for shadows whose
     *  source edge sits at the END of the axis (right edge / bottom edge). */
    val shadowStopsReversed: Array<Pair<Float, Color>> = shadowStops
        .map { (1f - it.first) to it.second }
        .reversed()
        .toTypedArray()
}

/** Vertical shadow cast downward from the toolbar. */
@Composable
fun ShadowBelow(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(MAStyle.ShadowSize)
            .background(Brush.verticalGradient(colorStops = MAStyle.shadowStops))
    )
}

/** Vertical shadow cast upward from the bottom bar (darkest at the bottom edge). */
@Composable
fun ShadowAbove(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(MAStyle.ShadowSize)
            .background(Brush.verticalGradient(colorStops = MAStyle.shadowStopsReversed))
    )
}

/** Horizontal shadow fading out to the left; darkest at its RIGHT edge.
 *  Placed at the right edge of the left pane — i.e. the shadow is cast INTO
 *  the left (inactive) pane. */
@Composable
fun ShadowToLeft(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(MAStyle.ShadowSize)
            .fillMaxHeight()
            .background(Brush.horizontalGradient(colorStops = MAStyle.shadowStopsReversed))
    )
}

/** Horizontal shadow fading out to the right; darkest at its LEFT edge.
 *  Placed at the left edge of the right pane — i.e. the shadow is cast INTO
 *  the right (inactive) pane. */
@Composable
fun ShadowToRight(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(MAStyle.ShadowSize)
            .fillMaxHeight()
            .background(Brush.horizontalGradient(colorStops = MAStyle.shadowStops))
    )
}

/**
 * MT-style dual-pane shadows.
 *
 * The active pane is "raised": the toolbar, the bottom bar and the centre
 * divider all cast their shadow onto the INACTIVE pane only, while the active
 * pane stays clean white. Switching the active pane mirrors all three shadows.
 *
 * Drawn as a transparent overlay above both lists. It registers no pointer
 * input, so taps / long-presses / swipes still reach the lists underneath.
 */
@Composable
fun DualPaneShadows(activeSide: PaneSide, modifier: Modifier = Modifier) {
    // The inactive pane is the one opposite to the active pane.
    val inactiveIsLeft = activeSide == PaneSide.RIGHT

    Box(modifier.fillMaxSize()) {
        // ---- centre divider: shadow into the inactive pane ----
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (inactiveIsLeft) {
                    ShadowToLeft(Modifier.align(Alignment.CenterEnd))
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (!inactiveIsLeft) {
                    ShadowToRight(Modifier.align(Alignment.CenterStart))
                }
            }
        }

        // ---- toolbar bottom edge: only above the inactive pane ----
        Row(
            Modifier
                .fillMaxWidth()
                .height(MAStyle.ShadowSize)
                .align(Alignment.TopStart)
        ) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (inactiveIsLeft) ShadowBelow()
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (!inactiveIsLeft) ShadowBelow()
            }
        }

        // ---- bottom bar top edge: only above the inactive pane ----
        Row(
            Modifier
                .fillMaxWidth()
                .height(MAStyle.ShadowSize)
                .align(Alignment.BottomStart)
        ) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (inactiveIsLeft) ShadowAbove()
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (!inactiveIsLeft) ShadowAbove()
            }
        }
    }
}
