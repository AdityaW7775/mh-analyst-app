package com.thepagination.mhanalyst.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thepagination.mhanalyst.util.Fmt

/** Colours shared with the MH Analyst website. */
data class MhColors(
    val bg: Color, val panel: Color, val panel2: Color, val line: Color, val ink: Color, val muted: Color,
    val accent: Color, val green: Color, val greenBg: Color, val orange: Color, val orangeBg: Color, val barMuted: Color,
    val darkCard: Color, val blueCard: Color,
)

private val Light = MhColors(
    bg = Color(0xFFF2F3F5), panel = Color.White, panel2 = Color(0xFFF7F8FA), line = Color(0xFFE4E6EA), ink = Color(0xFF14171C),
    muted = Color(0xFF5A6068), accent = Color(0xFF1F5FD1), green = Color(0xFF20B15A), greenBg = Color(0xFFE3F1E8),
    orange = Color(0xFFC2620A), orangeBg = Color(0xFFFBEEE1), barMuted = Color(0xFFD9DDE3), darkCard = Color(0xFF14171C), blueCard = Color(0xFF2563EB),
)
private val Dark = MhColors(
    bg = Color(0xFF0F1216), panel = Color(0xFF171B21), panel2 = Color(0xFF1C2128), line = Color(0xFF2A3039), ink = Color(0xFFE8EAED),
    muted = Color(0xFFA2A9B3), accent = Color(0xFF5B8DEF), green = Color(0xFF34C77B), greenBg = Color(0xFF15302A),
    orange = Color(0xFFF0A35B), orangeBg = Color(0xFF3A2716), barMuted = Color(0xFF333A44), darkCard = Color(0xFF0B0D10), blueCard = Color(0xFF1D4ED8),
)

val LocalMh = staticCompositionLocalOf { Light }
val mh: MhColors @Composable get() = LocalMh.current

@Composable
fun MhTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) Dark else Light
    val scheme = if (dark) darkColorScheme(primary = c.accent, background = c.bg, surface = c.panel, onSurface = c.ink, onBackground = c.ink)
    else lightColorScheme(primary = c.accent, background = c.bg, surface = c.panel, onSurface = c.ink, onBackground = c.ink)
    CompositionLocalProvider(LocalMh provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** White rounded card (or dark / blue highlight card). */
@Composable
fun DCard(
    modifier: Modifier = Modifier,
    background: Color = mh.panel,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
fun CardTitle(text: String, color: Color = mh.muted, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** ₹16,73,157.96 with the paise shown lighter, like the reference design. */
@Composable
fun BigAmount(n: Double, color: Color = mh.ink, faded: Color = mh.muted, size: Int = 32, chip: Int? = null) {
    val (whole, dec) = Fmt.split(n)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = (size * 0.62).sp, fontWeight = FontWeight.SemiBold)) { append("₹") }
                withStyle(SpanStyle(fontSize = size.sp, fontWeight = FontWeight.Bold)) { append(whole) }
                withStyle(SpanStyle(fontSize = size.sp, fontWeight = FontWeight.SemiBold, color = faded)) { append(dec) }
            },
            color = color, letterSpacing = (-0.5).sp, maxLines = 1,
        )
        if (chip != null) {
            Spacer(Modifier.width(10.dp))
            TrendChip(chip)
        }
    }
}

@Composable
fun TrendChip(pct: Int) {
    val up = pct >= 0
    Text(
        (if (up) "+" else "") + pct + "%",
        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (up) Color(0xFF20B15A) else Color(0xFFE5484D)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun Sub(text: String, color: Color = mh.muted) = Text(text, color = color, fontSize = 13.sp)

/** Pill-shaped option switch (This month · 6 months · 12 months). */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(50)).background(mh.panel).padding(4.dp)) {
        options.forEach { (key, label) ->
            val on = key == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) mh.ink else Color.Transparent).clickable { onSelect(key) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) mh.bg else mh.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Small selectable pill, for filters. */
@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) mh.bg else mh.ink, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) mh.ink else mh.panel2)
            .border(1.dp, if (selected) mh.ink else mh.line, RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
fun StatusTag(text: String, bg: Color, fg: Color) = Text(
    text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold,
    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
)

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, modifier = modifier.fillMaxWidth(),
        placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = mh.panel, unfocusedContainerColor = mh.panel, unfocusedBorderColor = mh.line),
    )
}

@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp, track: Color = mh.line) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
fun EmptyNote(text: String) = Text(text, color = mh.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))

// ---------- charts ----------

/** Smooth line with a soft gradient, the peak marked with a dot. */
@Composable
fun LineChart(values: List<Double>, labels: List<String>, line: Color, axis: Color, grid: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            if (values.isEmpty()) return@Canvas
            val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
            val w = size.width
            val h = size.height - 8f
            for (i in 0..3) {
                val y = h - h * i / 3f + 4f
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            val step = if (values.size > 1) w / (values.size - 1) else w
            val pts = values.mapIndexed { i, v -> Offset(i * step, (h - (v / max * h).toFloat()) + 4f) }
            val path = Path()
            pts.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(p.x, p.y) else {
                    val prev = pts[i - 1]
                    val mid = (prev.x + p.x) / 2f
                    path.cubicTo(mid, prev.y, mid, p.y, p.x, p.y)
                }
            }
            val fill = Path().apply { addPath(path); lineTo(pts.last().x, h + 4f); lineTo(pts.first().x, h + 4f); close() }
            drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.22f), line.copy(alpha = 0f))))
            drawPath(path, line, style = Stroke(width = 5f))
            val peak = values.indexOf(values.max())
            if (peak >= 0 && values[peak] > 0) {
                drawCircle(line, 9f, pts[peak])
                drawCircle(Color.White, 4f, pts[peak])
            }
        }
        AxisLabels(labels, axis)
    }
}

/** Vertical bars, the best one highlighted. */
@Composable
fun BarChart(values: List<Double>, labels: List<String>, highlight: Color, rest: Color, axis: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            if (values.isEmpty()) return@Canvas
            val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
            val slot = size.width / values.size
            val bw = (slot * 0.62f).coerceAtMost(48f)
            values.forEachIndexed { i, v ->
                val bh = (v / max * size.height).toFloat().coerceAtLeast(if (v > 0) 6f else 3f)
                drawRoundRect(
                    if (v == values.maxOrNull() && v > 0) highlight else rest,
                    topLeft = Offset(i * slot + (slot - bw) / 2f, size.height - bh), size = Size(bw, bh), cornerRadius = CornerRadius(12f, 12f),
                )
            }
        }
        AxisLabels(labels.mapIndexed { i, s -> if (i % 2 == 0) s else "" }, axis)
    }
}

@Composable
private fun AxisLabels(labels: List<String>, color: Color) {
    if (labels.isEmpty()) return
    val shown = if (labels.size > 7) labels.mapIndexed { i, s -> if (i % ((labels.size + 6) / 7) == 0) s else "" } else labels
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        shown.forEach { Text(it, color = color, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f)) }
    }
}

/** Doughnut for category shares. */
@Composable
fun Donut(values: List<Double>, colors: List<Color>, gap: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(170.dp)) {
        val total = values.sum().takeIf { it > 0 } ?: return@Canvas
        var start = -90f
        val stroke = size.minDimension * 0.2f
        val inset = stroke / 2f
        values.forEachIndexed { i, v ->
            val sweep = (v / total * 360f).toFloat()
            drawArc(colors[i % colors.size], start, (sweep - 2f).coerceAtLeast(0.5f), false,
                topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke), style = Stroke(width = stroke))
            start += sweep
        }
        drawCircle(gap, size.minDimension / 2f - stroke - 2f, center)
    }
}

@Composable
fun LegendDot(color: Color) = Box(Modifier.size(9.dp).clip(CircleShape).background(color))
