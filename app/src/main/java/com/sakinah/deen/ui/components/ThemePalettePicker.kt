package com.sakinah.deen.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sakinah.deen.R
import com.sakinah.deen.ui.theme.SakinahPalette

/**
 * Responsive palette picker for the full theme registry.
 *
 * Rendered as a two-column [FlowRow] of two-tone swatch cards rather than a single horizontal
 * row, so every palette stays legible and tappable on narrow screens without horizontal
 * scrolling or clipped labels.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePalettePicker(
    selected: SakinahPalette,
    onSelect: (SakinahPalette) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2
) {
    val columnCount = columns.coerceAtLeast(1)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val spacing = 8.dp
        val itemWidth = (maxWidth - spacing * (columnCount - 1)) / columnCount
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            SakinahPalette.entries.forEach { palette ->
                PaletteSwatchCard(
                    palette = palette,
                    selected = palette == selected,
                    onClick = { onSelect(palette) },
                    modifier = Modifier.width(itemWidth)
                )
            }
        }
    }
}

@Composable
private fun PaletteSwatchCard(
    palette: SakinahPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PaletteSwatch(palette = palette, modifier = Modifier.size(26.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(palette.titleRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // The description is shown only for the active palette so 18 cards stay
                // compact instead of turning the picker into a wall of small text.
                if (selected) {
                    Text(
                        text = stringResource(palette.subtitleRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.theme_palette_selected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** Two-tone circle: the palette's primary fill with its accent overlapping the bottom corner. */
@Composable
private fun PaletteSwatch(palette: SakinahPalette, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(palette.lightPreviewPrimary)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(12.dp)
                .clip(CircleShape)
                .background(palette.lightPreviewAccent)
        )
    }
}
