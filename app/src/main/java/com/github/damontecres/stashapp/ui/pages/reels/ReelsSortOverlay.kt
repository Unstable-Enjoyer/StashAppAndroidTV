package com.github.damontecres.stashapp.ui.pages.reels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.damontecres.stashapp.api.type.SortDirectionEnum
import com.github.damontecres.stashapp.data.SortAndDirection
import com.github.damontecres.stashapp.data.SortOption
import com.github.damontecres.stashapp.data.flip

private val OverlayBg = Color.Black.copy(alpha = 0.92f)
private val PrimaryColor = Color(0xFF9ACBFA)
private val FocusBg = Color(0xFF9ACBFA).copy(alpha = 0.08f)
private val TextColor = Color.White.copy(alpha = 0.7f)
private val DirColor = Color.White.copy(alpha = 0.3f)

private data class SortItem(
    val option: SortOption,
    val defaultDirection: SortDirectionEnum,
    val directionLabel: String,
)

private val SORT_ITEMS = listOf(
    SortItem(SortOption.Random, SortDirectionEnum.ASC, "--"),
    SortItem(SortOption.CreatedAt, SortDirectionEnum.DESC, "DESC"),
    SortItem(SortOption.UpdatedAt, SortDirectionEnum.DESC, "DESC"),
    SortItem(SortOption.Title, SortDirectionEnum.ASC, "A-Z"),
    SortItem(SortOption.Rating, SortDirectionEnum.DESC, "DESC"),
    SortItem(SortOption.Duration, SortDirectionEnum.DESC, "DESC"),
    SortItem(SortOption.FileSize, SortDirectionEnum.DESC, "DESC"),
    SortItem(SortOption.Bitrate, SortDirectionEnum.DESC, "DESC"),
    SortItem(SortOption.Path, SortDirectionEnum.ASC, "A-Z"),
    SortItem(SortOption.PlayCount, SortDirectionEnum.DESC, "DESC"),
    SortItem(SortOption.Date, SortDirectionEnum.DESC, "DESC"),
    SortItem(SortOption.LastPlayedAt, SortDirectionEnum.DESC, "DESC"),
)

@Composable
fun ReelsSortOverlay(
    currentSort: SortAndDirection,
    onSelectSort: (SortAndDirection) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

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
                text = "Sort By",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            SORT_ITEMS.forEach { sortItem ->
                val isSelected = currentSort.sort == sortItem.option
                var isFocused by remember { mutableStateOf(false) }

                val dirLabel = if (isSelected) {
                    when {
                        sortItem.option == SortOption.Random -> "--"
                        currentSort.direction == SortDirectionEnum.ASC ->
                            if (sortItem.option == SortOption.Title || sortItem.option == SortOption.Path) "A-Z" else "ASC"
                        else ->
                            if (sortItem.option == SortOption.Title || sortItem.option == SortOption.Path) "Z-A" else "DESC"
                    }
                } else {
                    sortItem.directionLabel
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isFocused) FocusBg else Color.Transparent,
                            RoundedCornerShape(6.dp),
                        )
                        .then(
                            if (isFocused) Modifier.border(1.5.dp, PrimaryColor, RoundedCornerShape(6.dp))
                            else Modifier.border(1.5.dp, Color.Transparent, RoundedCornerShape(6.dp))
                        )
                        .clickable {
                            if (isSelected && sortItem.option != SortOption.Random) {
                                // Toggle direction
                                onSelectSort(currentSort.copy(direction = currentSort.direction.flip()))
                            } else {
                                onSelectSort(SortAndDirection(sortItem.option, sortItem.defaultDirection))
                            }
                        }
                        .focusable()
                        .onFocusChanged { isFocused = it.isFocused }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = sortItem.option.getString(context),
                        color = if (isSelected) PrimaryColor else TextColor,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text(
                        text = dirLabel,
                        color = if (isSelected) PrimaryColor else DirColor,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
