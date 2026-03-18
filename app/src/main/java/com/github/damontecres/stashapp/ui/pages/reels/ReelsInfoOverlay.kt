package com.github.damontecres.stashapp.ui.pages.reels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.damontecres.stashapp.api.fragment.FullSceneData
import com.github.damontecres.stashapp.data.DataType
import com.github.damontecres.stashapp.navigation.Destination
import com.github.damontecres.stashapp.util.titleOrFilename

private val OverlayBg = Color.Black.copy(alpha = 0.92f)
private val PrimaryColor = Color(0xFF9ACBFA)
private val SeekBarColor = Color(0xFF5DB2E0)
private val GoldColor = Color(0xFFFFD700)
private val SectionLabelColor = Color.White.copy(alpha = 0.3f)
private val TextColor = Color.White.copy(alpha = 0.8f)
private val SubtextColor = Color.White.copy(alpha = 0.4f)
private val FocusBg = Color(0xFF9ACBFA).copy(alpha = 0.08f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReelsInfoOverlay(
    sceneData: FullSceneData?,
    onDismiss: () -> Unit,
    onNavigate: (Destination) -> Unit,
    onSeekToMarker: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OverlayBg)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Back || event.key == Key.Escape)
                ) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
    ) {
        if (sceneData == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Loading...", color = Color.White.copy(alpha = 0.5f))
            }
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 50.dp, bottom = 30.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Title
            Text(
                text = sceneData.titleOrFilename ?: "",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))

            // Subtitle info
            val fileData = sceneData.files.firstOrNull()?.videoFile
            val duration = fileData?.duration?.let { formatDuration(it) } ?: ""
            val resolution = fileData?.height?.let { "${it}p" } ?: ""
            val playCount = sceneData.play_count?.let { "$it plays" } ?: ""
            val subtitle = listOf(duration, resolution, playCount)
                .filter { it.isNotEmpty() }
                .joinToString("  --  ")
            Text(
                text = subtitle,
                color = SubtextColor,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))

            // Rating
            val rating100 = sceneData.rating100
            if (rating100 != null && rating100 > 0) {
                SectionLabel("Rating")
                val stars = (rating100 / 20.0).toInt()
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(5) { i ->
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = if (i < stars) GoldColor else Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$stars/5",
                        color = SubtextColor,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Performers
            if (sceneData.performers.isNotEmpty()) {
                SectionLabel("Performers")
                sceneData.performers.forEach { performer ->
                    FocusableRow(
                        onClick = {
                            onNavigate(Destination.Item(DataType.PERFORMER, performer.id))
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = performer.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "View",
                            color = Color.White.copy(alpha = 0.2f),
                            fontSize = 11.sp,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Studio
            sceneData.studio?.let { studioWrapper ->
                val studio = studioWrapper.studioData
                SectionLabel("Studio")
                FocusableRow(
                    onClick = {
                        onNavigate(Destination.Item(DataType.STUDIO, studio.id))
                    },
                ) {
                    Text(
                        text = studio.name,
                        color = TextColor,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "View",
                        color = Color.White.copy(alpha = 0.2f),
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Tags
            if (sceneData.tags.isNotEmpty()) {
                SectionLabel("Tags")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    sceneData.tags.forEach { tag ->
                        FocusableTag(
                            text = tag.tagData.name,
                            onClick = {
                                onNavigate(Destination.Item(DataType.TAG, tag.tagData.id))
                            },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Markers
            if (sceneData.scene_markers.isNotEmpty()) {
                SectionLabel("Markers")
                sceneData.scene_markers.forEach { marker ->
                    FocusableRow(
                        onClick = {
                            onSeekToMarker(marker.seconds)
                        },
                    ) {
                        Text(
                            text = formatDuration(marker.seconds),
                            color = SeekBarColor,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = marker.title.ifBlank { marker.primary_tag.tagData.name },
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = SectionLabelColor,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun FocusableRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isFocused) FocusBg else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .then(
                if (isFocused) Modifier.border(1.5.dp, PrimaryColor, RoundedCornerShape(6.dp))
                else Modifier.border(1.5.dp, Color.Transparent, RoundedCornerShape(6.dp))
            )
            .clickable(onClick = onClick)
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun FocusableTag(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 12.sp,
        modifier = modifier
            .background(
                if (isFocused) FocusBg else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(4.dp),
            )
            .then(
                if (isFocused) Modifier.border(1.5.dp, PrimaryColor, RoundedCornerShape(4.dp))
                else Modifier.border(1.5.dp, Color.Transparent, RoundedCornerShape(4.dp))
            )
            .clickable(onClick = onClick)
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private fun formatDuration(seconds: Double): String {
    val totalSeconds = seconds.toLong()
    val minutes = totalSeconds / 60
    val secs = totalSeconds % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val mins = minutes % 60
        "%d:%02d:%02d".format(hours, mins, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}
