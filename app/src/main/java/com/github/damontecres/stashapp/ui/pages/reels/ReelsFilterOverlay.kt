package com.github.damontecres.stashapp.ui.pages.reels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.github.damontecres.stashapp.api.fragment.SavedFilter
import com.github.damontecres.stashapp.suppliers.FilterArgs
import com.github.damontecres.stashapp.util.StashServer

private val OverlayBg = Color.Black.copy(alpha = 0.92f)
private val PrimaryColor = Color(0xFF9ACBFA)
private val FocusBg = Color(0xFF9ACBFA).copy(alpha = 0.08f)
private val TextColor = Color.White.copy(alpha = 0.7f)
private val SectionLabelColor = Color.White.copy(alpha = 0.3f)
private val DividerColor = Color.White.copy(alpha = 0.08f)

@Composable
fun ReelsFilterOverlay(
    currentFilter: FilterArgs,
    savedFilters: List<SavedFilter>,
    loop: Boolean,
    autoAdvance: Boolean,
    server: StashServer,
    onSelectSavedFilter: (SavedFilter) -> Unit,
    onToggleLoop: () -> Unit,
    onToggleAutoAdvance: () -> Unit,
    onDismiss: () -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 50.dp, bottom = 30.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Filter",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Saved Filters
            if (savedFilters.isNotEmpty()) {
                SectionLabel("Saved Filters")
                savedFilters.forEach { filter ->
                    QuickFilterItem(
                        name = filter.name,
                        isSelected = currentFilter.name == filter.name,
                        onClick = { onSelectSavedFilter(filter) },
                    )
                }
            }

            // Playback toggles
            Spacer(Modifier.height(10.dp))
            Divider()
            SectionLabel("Playback")
            ToggleRow(
                label = "Loop",
                isOn = loop,
                onClick = onToggleLoop,
            )
            ToggleRow(
                label = "Auto-advance",
                isOn = autoAdvance,
                onClick = onToggleAutoAdvance,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = SectionLabelColor,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DividerColor),
    )
}

@Composable
private fun QuickFilterItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    Text(
        text = name,
        color = if (isSelected) PrimaryColor else TextColor,
        fontSize = 14.sp,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun ToggleRow(
    label: String,
    isOn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = TextColor,
            fontSize = 13.sp,
        )
        // Toggle track
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(18.dp)
                .background(
                    if (isOn) PrimaryColor else Color.White.copy(alpha = 0.15f),
                    RoundedCornerShape(9.dp),
                ),
        ) {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .align(if (isOn) Alignment.CenterEnd else Alignment.CenterStart)
                    .background(Color.White, RoundedCornerShape(50))
                    .width(14.dp)
                    .height(14.dp),
            )
        }
    }
}
