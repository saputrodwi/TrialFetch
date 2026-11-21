package com.trialfetch.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Komponen bersama. Nama lama dipertahankan supaya semua layar kompil,
 * tetapi tampilannya kini mengikuti bahasa visual Mihon: datar, satu
 * aksen, garis tipis, tanpa bayangan dekoratif.
 */
private val CardShape = RoundedCornerShape(8.dp)
private val PillShape = RoundedCornerShape(8.dp)

fun Modifier.hardShadow(
    color: Color,
    offset: Dp = 4.dp,
    shape: Shape = CardShape
): Modifier = this

@Composable
fun PolkaDotBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier.background(MaterialTheme.colorScheme.background)) { content() }
}

@Composable
fun BrutalTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = MaterialTheme.colorScheme.onBackground,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow
    )
}

@Composable
fun BrutalCard(
    modifier: Modifier = Modifier,
    background: Color = LocalExtraColors.current.cardLight,
    shape: Shape = CardShape,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    borderWidth: Dp = 1.dp,
    shadowOffset: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(Space.lg),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier
        .background(MaterialTheme.colorScheme.surfaceVariant, shape)
        .border(borderWidth, borderColor, shape)
    Surface(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
        color = Color.Transparent,
        shape = shape
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun BrutalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = LocalExtraColors.current.yellow,
    contentColor: Color = LocalExtraColors.current.onAccent,
    enabled: Boolean = true
) {
    val primary = background == LocalExtraColors.current.yellow
    val buttonModifier = modifier.heightIn(min = Space.touch)
    if (primary) {
        Button(onClick = onClick, modifier = buttonModifier, enabled = enabled, shape = CardShape) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = buttonModifier, enabled = enabled, shape = CardShape) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun BrutalChoiceRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LocalExtraColors.current.yellow
) {
    androidx.compose.foundation.layout.Row(modifier) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val choiceModifier = Modifier
                .heightIn(min = Space.touch)
                .weight(1f, fill = false)
            if (selected) {
                Button(
                    onClick = { onSelect(index) },
                    modifier = choiceModifier,
                    shape = PillShape
                ) { Text(option, style = MaterialTheme.typography.labelMedium) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(index) },
                    modifier = choiceModifier,
                    shape = PillShape
                ) { Text(option, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
fun BrutalDashedBox(
    modifier: Modifier = Modifier,
    text: String = "",
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, CardShape)
            .border(1.dp, borderColor, CardShape)
            .padding(Space.lg),
        contentAlignment = Alignment.Center
    ) {
        if (text.isNotEmpty()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BrutalAvatar(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    background: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceVariant, CardShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
