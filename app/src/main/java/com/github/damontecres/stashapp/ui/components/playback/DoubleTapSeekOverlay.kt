package com.github.damontecres.stashapp.ui.components.playback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.damontecres.stashapp.R

/**
 * Oval shape with a straight left edge and bezier-curved right edge.
 * Adapted from mpvKt OvalBox.kt (MIT license).
 */
private val LeftSideOvalShape =
    object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline {
            val path =
                Path().apply {
                    // Start at top-left
                    moveTo(0f, 0f)
                    // Straight top edge to about 70% width
                    lineTo(size.width * 0.7f, 0f)
                    // Bezier curve outward (convex) on right side
                    cubicTo(
                        size.width,
                        size.height * 0.25f,
                        size.width,
                        size.height * 0.75f,
                        size.width * 0.7f,
                        size.height,
                    )
                    // Straight bottom edge back to left
                    lineTo(0f, size.height)
                    close()
                }
            return Outline.Generic(path)
        }
    }

/**
 * Oval shape with a straight right edge and bezier-curved left edge.
 * Adapted from mpvKt OvalBox.kt (MIT license).
 */
private val RightSideOvalShape =
    object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline {
            val path =
                Path().apply {
                    // Start at top-right
                    moveTo(size.width, 0f)
                    // Straight top edge to about 30% width
                    lineTo(size.width * 0.3f, 0f)
                    // Bezier curve outward (convex) on left side
                    cubicTo(
                        0f,
                        size.height * 0.25f,
                        0f,
                        size.height * 0.75f,
                        size.width * 0.3f,
                        size.height,
                    )
                    // Straight bottom edge back to right
                    lineTo(size.width, size.height)
                    close()
                }
            return Outline.Generic(path)
        }
    }

/**
 * Three cascading animated arrows for the seek overlay.
 * Adapted from mpvKt DoubleTapSeekSecondsView.kt (MIT license).
 */
@Composable
private fun DoubleTapSeekTriangles(isForward: Boolean) {
    val alpha1 = remember { Animatable(0f) }
    val alpha2 = remember { Animatable(0f) }
    val alpha3 = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val duration = 125
        val spec = tween<Float>(durationMillis = duration, easing = LinearEasing)
        while (true) {
            // Fade in sequentially
            alpha1.animateTo(1f, spec)
            alpha2.animateTo(1f, spec)
            alpha3.animateTo(1f, spec)
            // Fade out sequentially
            alpha1.animateTo(0f, spec)
            alpha2.animateTo(0f, spec)
            alpha3.animateTo(0f, spec)
        }
    }

    Row(
        modifier = if (!isForward) Modifier.rotate(180f) else Modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val alphas = listOf(alpha1, alpha2, alpha3)
        alphas.forEach { anim ->
            Image(
                painter = painterResource(R.drawable.ic_seek_triangle),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(width = 16.dp, height = 20.dp)
                        .alpha(anim.value),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
    }
}

/**
 * mpvKt-style animated double-tap seek overlay with bezier-curved oval background
 * and cascading triple-arrow animation.
 */
@Composable
fun DoubleTapSeekOverlay(
    isVisible: Boolean,
    isForward: Boolean,
    seconds: Long,
    onHide: () -> Unit,
) {
    if (!isVisible) return

    LaunchedEffect(seconds) {
        kotlinx.coroutines.delay(800L)
        onHide()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.4f)
                    .fillMaxHeight()
                    .align(if (isForward) Alignment.CenterEnd else Alignment.CenterStart)
                    .clip(if (isForward) RightSideOvalShape else LeftSideOvalShape)
                    .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DoubleTapSeekTriangles(isForward = isForward)
                Text(
                    text = "$seconds seconds",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
