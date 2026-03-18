package com.github.damontecres.stashapp.ui.pages.reels

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.damontecres.stashapp.ui.compat.isNotTvDevice

private val SeekBarColor = Color(0xFF5DB2E0)
private val MarkerColor = Color(0xFF9ACBFA)
private val TrackBg = Color.White.copy(alpha = 0.12f)

@Composable
fun ReelsProgressBar(
    currentPosition: Long,
    totalDuration: Long,
    markerPositions: List<Long>,
    isPaused: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isScrubbing: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    var isInteracting by remember { mutableStateOf(false) }
    val isTouchDevice = isNotTvDevice
    val showExpanded = isFocused || isPaused || isInteracting || isScrubbing

    val barHeight by animateDpAsState(
        targetValue = if (showExpanded) {
            if (isInteracting || isScrubbing) 24.dp else 18.dp
        } else {
            9.dp
        },
        label = "barHeight",
    )
    val scrubberSize by animateDpAsState(
        targetValue = if (isInteracting) 14.dp else 12.dp,
        label = "scrubberSize",
    )

    val progress = if (totalDuration > 0) {
        (currentPosition.toFloat() / totalDuration).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusable()
            .onFocusChanged { isFocused = it.isFocused },
    ) {
        // Time display (visible when focused, paused, or interacting)
        if (showExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTime(currentPosition),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                )
                Text(
                    text = formatTime(totalDuration),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                )
            }
        }

        // Touch target wrapper (48dp on touch devices, wraps content on TV)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .let { mod ->
                    if (isTouchDevice) {
                        mod.height(48.dp)
                            .pointerInput(totalDuration) {
                                detectHorizontalDragGestures(
                                    onDragStart = { isInteracting = true },
                                    onDragEnd = { isInteracting = false },
                                    onDragCancel = { isInteracting = false },
                                    onHorizontalDrag = { change, _ ->
                                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                        val position = (fraction * totalDuration).toLong()
                                        onSeek(position)
                                    },
                                )
                            }
                    } else {
                        mod
                    }
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .background(TrackBg),
            ) {
                // Fill
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(barHeight)
                        .background(SeekBarColor),
                ) {
                    // Scrubber dot (visible when focused, paused, or interacting)
                    if (showExpanded) {
                        Box(
                            modifier = Modifier
                                .size(scrubberSize)
                                .align(Alignment.CenterEnd)
                                .offset(x = scrubberSize / 2)
                                .background(Color.White, CircleShape),
                        )
                    }
                }

                // Marker dots
                if (totalDuration > 0) {
                    markerPositions.forEach { markerPos ->
                        val markerFraction = (markerPos.toFloat() / totalDuration).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(markerFraction)
                                .height(barHeight)
                                .align(Alignment.CenterStart),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(barHeight)
                                    .align(Alignment.CenterEnd)
                                    .background(MarkerColor.copy(alpha = 0.6f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val mins = minutes % 60
        "%d:%02d:%02d".format(hours, mins, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
