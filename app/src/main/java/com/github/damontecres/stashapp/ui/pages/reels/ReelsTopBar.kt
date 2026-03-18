package com.github.damontecres.stashapp.ui.pages.reels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ButtonBg = Color.White.copy(alpha = 0.18f)
private val PrimaryColor = Color(0xFF9ACBFA)
private val TextColor = Color.White.copy(alpha = 0.85f)
private val IconTint = Color.White.copy(alpha = 0.7f)

@Composable
fun ReelsTopBar(
    visible: Boolean,
    filterName: String,
    sortName: String,
    isTV: Boolean,
    onFilterClick: () -> Unit,
    onSortClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // On phones, pad below the status bar; on TV there is none
    val statusBarPadding = if (!isTV) {
        WindowInsets.statusBars.asPaddingValues()
    } else {
        null
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (statusBarPadding != null) {
                        Modifier.padding(
                            top = statusBarPadding.calculateTopPadding() + 8.dp,
                            start = 14.dp,
                            end = 14.dp,
                            bottom = 8.dp,
                        )
                    } else {
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    },
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Filter button (left)
            TopBarButton(
                icon = Icons.Filled.Menu,
                label = "Filter: $filterName",
                onClick = onFilterClick,
            )

            // Right side: Sort + Info
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopBarButton(
                    icon = Icons.Filled.PlayArrow,
                    label = sortName,
                    onClick = onSortClick,
                )
                TopBarIconButton(
                    icon = Icons.Filled.Info,
                    contentDescription = "Info",
                    onClick = onInfoClick,
                )
            }
        }
    }
}

@Composable
private fun TopBarButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .background(ButtonBg, RoundedCornerShape(6.dp))
            .then(
                if (isFocused) Modifier.border(1.5.dp, PrimaryColor, RoundedCornerShape(6.dp))
                else Modifier.border(1.5.dp, Color.Transparent, RoundedCornerShape(6.dp))
            )
            .clickable(onClick = onClick)
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = IconTint,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            color = TextColor,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(34.dp)
            .background(ButtonBg, CircleShape)
            .then(
                if (isFocused) Modifier.border(1.5.dp, PrimaryColor, CircleShape)
                else Modifier.border(1.5.dp, Color.Transparent, CircleShape)
            )
            .clickable(onClick = onClick)
            .focusable()
            .onFocusChanged { isFocused = it.isFocused },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = IconTint,
            modifier = Modifier.size(18.dp),
        )
    }
}
