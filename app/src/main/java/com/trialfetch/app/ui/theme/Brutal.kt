package com.trialfetch.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ketebalan garis tinta: 2dp ala mockup minimalis yang disetujui. */
val InkWidth = 2.dp

/** Radius sudut membulat, mengikuti nilai px di web. */
private val CardShape = RoundedCornerShape(14.dp)
private val ButtonShape = RoundedCornerShape(12.dp)
private val PillShape = RoundedCornerShape(999.dp)

/**
 * Modifier yang menggambar bayangan keras tanpa blur, seperti
 * `box-shadow: 3px 3px 0 var(--shadow)` di web.
 *
 * Bayangan digambar sendiri memakai drawBehind, bukan Modifier.shadow(),
 * karena Modifier.shadow() selalu memakai gaussian blur sehingga hasilnya
 * terlihat seperti Material, bukan seperti web.
 */
fun Modifier.hardShadow(
    color: Color,
    offset: Dp = 4.dp,
    shape: Shape = CardShape
): Modifier = this.drawBehind {
    val dx = offset.toPx()
    val dy = offset.toPx()
    translate(dx, dy) {
        // Gambar mengikuti outline shape (pill/rounded), BUKAN drawRect:
        // rect di belakang sudut membulat mencongol keluar dan terlihat
        // seperti kotak rusak, terutama di elemen kecil. Lewat Path agar
        // kompatibel semua versi Compose (tanpa drawOutline).
        val w = (size.width - dx).coerceAtLeast(0f)
        val h = (size.height - dy).coerceAtLeast(0f)
        val path = when (val o = shape.createOutline(Size(w, h), layoutDirection, this)) {
            is Outline.Generic -> o.path
            is Outline.Rounded -> Path().apply { addRoundRect(o.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(o.rect) }
        }
        drawPath(path, color)
    }
}

/**
 * Latar aplikasi dengan titik polkadot, meniru
 * `radial-gradient(circle, var(--bg-dot) 1.5px, transparent 1.5px)`.
 */
@Composable
fun PolkaDotBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val extra = LocalExtraColors.current
    val bg = MaterialTheme.colorScheme.background
    val dot = extra.bgDot

    Box(
        modifier = modifier
            .background(bg)
            .drawBehind {
                val step = 22.dp.toPx()
                val radius = 1.5.dp.toPx()
                var y = step / 2f
                var row = 0
                while (y < size.height) {
                    var x = if (row % 2 == 0) step / 2f else step
                    while (x < size.width) {
                        drawCircle(color = dot, radius = radius, center = Offset(x, y))
                        x += step
                    }
                    y += step
                    row++
                }
            }
    ) {
        content()
    }
}

/**
 * Judul dengan text-shadow offset, mengikuti
 * `text-shadow: 3px 3px 0 var(--title-shadow)`.
 */
@Composable
fun BrutalTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = MaterialTheme.colorScheme.onBackground
) {
    val shadow = LocalExtraColors.current.titleShadow
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(
            shadow = Shadow(
                color = shadow,
                offset = Offset(3f, 3f),
                blurRadius = 0f
            )
        ),
        color = color
    )
}

/**
 * Kartu ala web: garis tinta, bayangan keras, sudut membulat.
 * Dipakai menggantikan Card Material yang tampil datar.
 */
@Composable
fun BrutalCard(
    modifier: Modifier = Modifier,
    background: Color = LocalExtraColors.current.cardLight,
    shape: Shape = CardShape,
    borderColor: Color = MaterialTheme.colorScheme.onBackground,
    borderWidth: Dp = InkWidth,
    shadowOffset: Dp = 2.dp,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shadowColor = LocalExtraColors.current.shadow
    val base = modifier
        .hardShadow(shadowColor, shadowOffset, shape)
        .background(background, shape)
        .border(borderWidth, borderColor, shape)

    Column(
        modifier = (if (onClick != null) base.clickable { onClick() } else base)
            .padding(contentPadding),
        content = content
    )
}

/** Tombol utama ala web: warna aksen, garis tinta, bayangan keras. */
@Composable
fun BrutalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = LocalExtraColors.current.yellow,
    contentColor: Color = LocalExtraColors.current.onAccent,
    enabled: Boolean = true
) {
    val shadowColor = LocalExtraColors.current.shadow
    val borderColor = MaterialTheme.colorScheme.onBackground

    Box(
        modifier = modifier
            .hardShadow(shadowColor, 2.dp, ButtonShape)
            .background(if (enabled) background else background.copy(alpha = 0.5f), ButtonShape)
            .border(InkWidth, borderColor, ButtonShape)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.5f)
        )
    }
}

/** Deretan tombol pilihan aktif/nonaktif, gaya pil di web. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrutalChoiceRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LocalExtraColors.current.blue
) {
    val shadowColor = LocalExtraColors.current.shadow
    val borderColor = MaterialTheme.colorScheme.onBackground
    val onAccent = LocalExtraColors.current.onAccent

    // FlowRow agar opsi banyak/tulisan panjang tidak terpotong di layar sempit.
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .hardShadow(shadowColor, 2.dp, PillShape)
                    .background(if (selected) accent else Color.Transparent, PillShape)
                    .border(InkWidth, borderColor, PillShape)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) onAccent else MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

/** Kotak kosong bergaris putus-putus, untuk placeholder dan status kosong. */
@Composable
fun BrutalDashedBox(
    modifier: Modifier = Modifier,
    text: String = "",
    borderColor: Color = LocalExtraColors.current.outline
) {
    Box(
        modifier = modifier
            .border(InkWidth, borderColor, RoundedCornerShape(20.dp))
            .padding(16.dp),
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

/** Lingkaran Avatar ala web untuk inisial. */
@Composable
fun BrutalAvatar(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    background: Color = LocalExtraColors.current.purple
) {
    val shadowColor = LocalExtraColors.current.shadow
    val borderColor = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .size(size)
            .hardShadow(shadowColor, 3.dp, RoundedCornerShape(50))
            .background(background, RoundedCornerShape(50))
            .border(InkWidth, borderColor, RoundedCornerShape(50)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = LocalExtraColors.current.onAccent
        )
    }
}
