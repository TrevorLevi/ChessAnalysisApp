package com.chessforge.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessforge.ui.theme.LocalViz
import com.chessforge.ui.theme.VizPalette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Specifications communes a tous les graphiques de l'appli :
 *  - marques fines : lignes 2 dp, barres 24 dp maximum, extremite arrondie 4 dp
 *    du cote de la valeur et carree sur la ligne de base ;
 *  - separation par du vide : 2 dp de surface entre deux marques jointives,
 *    anneau de 2 dp autour des points qui se croisent ;
 *  - grille et axes en filet de 1 dp, pleins, en retrait ;
 *  - etiquettes selectives (extremite et extremum), jamais une valeur par point ;
 *  - le texte porte toujours une couleur d'encre, jamais la couleur de la serie ;
 *  - chaque graphique a son equivalent en tableau, accessible depuis la carte.
 */

private val MARK_RADIUS = 4.dp
private val LINE_WIDTH = 2.dp
private val SPACER = 2.dp
private val MAX_BAR = 24.dp
private val DOT_RADIUS = 4.5.dp

data class Datum(
    val label: String,
    val value: Float,
    val valueLabel: String = formatNumber(value),
)

data class Slice(val label: String, val value: Float, val color: Color, val valueLabel: String)

fun formatNumber(value: Float): String =
    if (abs(value) >= 100f || value == value.roundToInt().toFloat()) value.roundToInt().toString()
    else String.format("%.1f", value)

/**
 * Carte d'accueil d'un graphique : titre, sous-titre, et bascule vers le tableau de
 * valeurs. Le tableau n'est pas un repli degrade, c'est le jumeau accessible du
 * graphique : toute valeur lisible sur la courbe est lisible ici.
 */
@Composable
fun ChartCard(
    title: String,
    subtitle: String? = null,
    tableRows: List<Pair<String, String>> = emptyList(),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var showTable by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (tableRows.isNotEmpty()) {
                    IconButton(onClick = { showTable = !showTable }) {
                        Icon(
                            imageVector = if (showTable) Icons.Filled.ShowChart else Icons.Filled.List,
                            contentDescription = if (showTable) "Afficher le graphique" else "Afficher les valeurs",
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (showTable) ValueTable(tableRows) else content()
        }
    }
}

@Composable
private fun ValueTable(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((label, value) in rows) {
            Row {
                Text(
                    label,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                    ),
                )
            }
        }
    }
}

/**
 * Courbe d'evolution a une seule serie (donc sans legende : le titre nomme la donnee).
 * Un appui ou un glissement affiche un reticule et la valeur du point vise.
 */
@Composable
fun LineChart(
    data: List<Datum>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    seriesIndex: Int = 0,
    valueSuffix: String = "",
    yMin: Float? = null,
    yMax: Float? = null,
    /** Appele quand l'utilisateur pointe un point : permet de piloter un autre element. */
    onSelect: ((Int) -> Unit)? = null,
) {
    if (data.isEmpty()) {
        EmptyPlot(modifier, height)
        return
    }
    val viz = LocalViz.current
    val color = viz.series(seriesIndex)
    val measurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface
    var selected by remember(data.size) { mutableStateOf<Int?>(null) }

    // Des bornes explicites sont des bornes dures : une precision ne depasse pas 100,
    // inutile de reserver de la place pour des valeurs impossibles.
    val padded = if (yMin != null && yMax != null) {
        yMin to yMax
    } else {
        val lo = yMin ?: data.minOf { it.value }
        val hi = yMax ?: data.maxOf { it.value }
        val span = (hi - lo).takeIf { it > 0.001f } ?: 1f
        niceBounds(lo - span * 0.12f, hi + span * 0.12f)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(data.size) {
                detectTapGestures { offset ->
                    val index = indexAt(offset.x, size.width.toFloat(), data.size)
                    selected = index
                    onSelect?.invoke(index)
                }
            }
            .pointerInput(data.size) {
                // Le glissement fait office de survol : la zone de saisie couvre toute la hauteur.
                detectHorizontalDragGestures { change, _ ->
                    val index = indexAt(change.position.x, size.width.toFloat(), data.size)
                    selected = index
                    onSelect?.invoke(index)
                }
            }
    ) {
        val axisSpace = 34.dp.toPx()
        val bottomSpace = 18.dp.toPx()
        val plotLeft = axisSpace
        val plotRight = size.width - 6.dp.toPx()
        val plotTop = 8.dp.toPx()
        val plotBottom = size.height - bottomSpace
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop

        // Grille : filets pleins, en retrait, plus les graduations arrondies de l'axe.
        val ticks = 4
        for (i in 0..ticks) {
            val t = i.toFloat() / ticks
            val y = plotBottom - t * plotHeight
            drawLine(viz.grid, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1.dp.toPx())
            val value = padded.first + (padded.second - padded.first) * t
            drawTickLabel(measurer, formatNumber(value), Offset(plotLeft - 6.dp.toPx(), y), viz, right = true)
        }
        drawLine(viz.axis, Offset(plotLeft, plotBottom), Offset(plotRight, plotBottom), strokeWidth = 1.dp.toPx())

        fun xAt(index: Int): Float =
            if (data.size == 1) plotLeft + plotWidth / 2f
            else plotLeft + plotWidth * index / (data.size - 1).toFloat()

        fun yAt(value: Float): Float {
            val t = (value - padded.first) / (padded.second - padded.first)
            return plotBottom - t.coerceIn(0f, 1f) * plotHeight
        }

        // Aire : un lavis a 10 %, jamais un bloc sature.
        val area = Path().apply {
            moveTo(xAt(0), plotBottom)
            data.forEachIndexed { index, datum -> lineTo(xAt(index), yAt(datum.value)) }
            lineTo(xAt(data.lastIndex), plotBottom)
            close()
        }
        drawPath(area, color.copy(alpha = 0.10f))

        val line = Path().apply {
            data.forEachIndexed { index, datum ->
                val x = xAt(index)
                val y = yAt(datum.value)
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(line, color, style = Stroke(width = LINE_WIDTH.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))

        // Etiquetage selectif : extremite, minimum et maximum seulement.
        val extremes = setOfNotNull(
            data.lastIndex,
            data.indices.maxByOrNull { data[it].value },
            data.indices.minByOrNull { data[it].value },
        )
        for (index in extremes) {
            val x = xAt(index)
            val y = yAt(data[index].value)
            drawCircle(viz.surface, radius = DOT_RADIUS.toPx() + SPACER.toPx(), center = Offset(x, y))
            drawCircle(color, radius = DOT_RADIUS.toPx(), center = Offset(x, y))
        }
        val last = data.lastIndex
        drawValueLabel(
            measurer,
            data[last].valueLabel + valueSuffix,
            Offset(xAt(last), yAt(data[last].value) - 14.dp.toPx()),
            onSurface,
            anchorRight = true,
        )

        // Premiere et derniere abscisse : deux reperes suffisent a situer la periode.
        drawTickLabel(measurer, data.first().label, Offset(plotLeft, plotBottom + 4.dp.toPx()), viz)
        drawTickLabel(
            measurer, data.last().label,
            Offset(plotRight, plotBottom + 4.dp.toPx()), viz, right = true,
        )

        // Reticule d'inspection
        selected?.coerceIn(0, data.lastIndex)?.let { index ->
            val x = xAt(index)
            val y = yAt(data[index].value)
            drawLine(viz.axis, Offset(x, plotTop), Offset(x, plotBottom), strokeWidth = 1.dp.toPx())
            drawCircle(viz.surface, radius = DOT_RADIUS.toPx() + SPACER.toPx(), center = Offset(x, y))
            drawCircle(color, radius = DOT_RADIUS.toPx(), center = Offset(x, y))
            val text = "${data[index].label} · ${data[index].valueLabel}$valueSuffix"
            drawCallout(measurer, text, Offset(x, plotTop), viz, onSurface, plotLeft, plotRight)
        }
    }
}

/** Barres verticales ou horizontales, une seule serie : une couleur pour toutes les barres. */
@Composable
fun BarChart(
    data: List<Datum>,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    horizontal: Boolean = false,
    seriesIndex: Int = 0,
    colorOverride: List<Color>? = null,
) {
    if (data.isEmpty()) {
        EmptyPlot(modifier, height)
        return
    }
    val viz = LocalViz.current
    val measurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val maxValue = max(data.maxOf { it.value }, 0.0001f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
    ) {
        if (horizontal) {
            val labelWidth = 92.dp.toPx()
            val valueWidth = 46.dp.toPx()
            val slot = size.height / data.size
            val thickness = minOf(slot - SPACER.toPx() * 2, MAX_BAR.toPx())
            val trackLeft = labelWidth
            val trackRight = size.width - valueWidth
            data.forEachIndexed { index, datum ->
                val color = colorOverride?.getOrNull(index) ?: viz.series(seriesIndex)
                val centerY = slot * index + slot / 2f
                val top = centerY - thickness / 2f
                val length = (trackRight - trackLeft) * (datum.value / maxValue)
                drawHorizontalBar(trackLeft, top, length.coerceAtLeast(2.dp.toPx()), thickness, color)
                drawTickLabel(
                    measurer, datum.label,
                    Offset(labelWidth - 8.dp.toPx(), centerY - 7.dp.toPx()), viz, right = true,
                )
                drawValueLabel(
                    measurer, datum.valueLabel,
                    Offset(trackLeft + length + 6.dp.toPx(), centerY - 7.dp.toPx()), onSurface,
                )
            }
        } else {
            val bottomSpace = 20.dp.toPx()
            val topSpace = 18.dp.toPx()
            val plotBottom = size.height - bottomSpace
            val plotHeight = plotBottom - topSpace
            val slot = size.width / data.size
            val thickness = minOf(slot - SPACER.toPx() * 2, MAX_BAR.toPx())
            drawLine(
                viz.axis, Offset(0f, plotBottom), Offset(size.width, plotBottom),
                strokeWidth = 1.dp.toPx(),
            )
            data.forEachIndexed { index, datum ->
                val color = colorOverride?.getOrNull(index) ?: viz.series(seriesIndex)
                val centerX = slot * index + slot / 2f
                val barHeight = (plotHeight * (datum.value / maxValue)).coerceAtLeast(2.dp.toPx())
                drawVerticalBar(centerX - thickness / 2f, plotBottom - barHeight, thickness, barHeight, color)
                drawCenteredLabel(
                    measurer, datum.valueLabel, centerX,
                    plotBottom - barHeight - 16.dp.toPx(), onSurface, bold = true,
                )
                drawCenteredLabel(measurer, datum.label, centerX, plotBottom + 4.dp.toPx(), viz.muted)
            }
        }
    }
}

/**
 * Anneau part-au-tout, 6 segments au maximum et toujours accompagne d'une legende :
 * l'identite ne repose jamais sur la seule couleur.
 */
@Composable
fun DonutChart(
    slices: List<Slice>,
    centerValue: String,
    centerLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 156.dp,
) {
    val viz = LocalViz.current
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    if (total <= 0f) {
        EmptyPlot(modifier, size)
        return
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val thickness = 22.dp.toPx()
                val inset = thickness / 2f
                val diameter = kotlin.math.min(this.size.width, this.size.height) - thickness
                val radius = diameter / 2f
                val gapDegrees = (SPACER.toPx() / radius) * (180f / Math.PI.toFloat())
                var start = -90f
                for (slice in slices) {
                    val sweep = 360f * (slice.value / total)
                    if (sweep <= 0f) continue
                    val drawSweep = (sweep - gapDegrees).coerceAtLeast(0.6f)
                    drawArc(
                        color = slice.color,
                        startAngle = start + gapDegrees / 2f,
                        sweepAngle = drawSweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset + (this.size.height - this.size.width) / 2f),
                        size = Size(diameter, diameter),
                        style = Stroke(width = thickness),
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    centerValue,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    centerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (slice in slices) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(slice.color, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        slice.label,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        slice.valueLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * Carte de chaleur 8x8 aux coordonnees de l'echiquier, rampe a teinte unique.
 * Sur fond sombre, la valeur nulle se confond volontairement avec la surface.
 */
@Composable
fun HeatmapBoard(
    values: IntArray,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    flipped: Boolean = false,
) {
    val viz = LocalViz.current
    val measurer = rememberTextMeasurer()
    val maxValue = values.maxOrNull() ?: 0
    Column(modifier) {
        Canvas(
            Modifier
                .size(size)
        ) {
            val gap = SPACER.toPx()
            val cell = (this.size.minDimension - gap * 7) / 8f
            for (rank in 0..7) {
                for (file in 0..7) {
                    val square = if (flipped) (7 - rank) * 8 + (7 - file) else rank * 8 + file
                    val count = values.getOrElse(square) { 0 }
                    val color = if (count == 0 || maxValue == 0) {
                        viz.surface
                    } else {
                        val step = ((count.toFloat() / maxValue) * (viz.sequential.size - 1)).roundToInt()
                        viz.sequential[step.coerceIn(0, viz.sequential.lastIndex)]
                    }
                    val x = file * (cell + gap)
                    val y = (7 - rank) * (cell + gap)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(cell, cell),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                    )
                    if (count == 0) {
                        drawRoundRect(
                            color = viz.grid,
                            topLeft = Offset(x, y),
                            size = Size(cell, cell),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                }
            }
            // Coordonnees discretes sous la grille
            for (file in 0..7) {
                val letter = ('a' + if (flipped) 7 - file else file).toString()
                drawCenteredLabel(
                    measurer, letter,
                    file * (cell + gap) + cell / 2f,
                    8 * (cell + gap) - gap + 2.dp.toPx(),
                    viz.muted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        ScaleLegend(maxValue)
    }
}

@Composable
private fun ScaleLegend(maxValue: Int) {
    val viz = LocalViz.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (color in viz.sequential) {
                Box(
                    Modifier
                        .size(width = 14.dp, height = 8.dp)
                        .background(color, RoundedCornerShape(1.dp))
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            maxValue.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Mini-courbe de 12 points environ, destinee aux tuiles de statistiques. */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    seriesIndex: Int = 0,
) {
    if (values.size < 2) {
        Spacer(modifier)
        return
    }
    val viz = LocalViz.current
    val color = viz.series(seriesIndex)
    Canvas(modifier) {
        val lo = values.min()
        val hi = values.max()
        val span = (hi - lo).takeIf { it > 0.0001f } ?: 1f
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.size - 1).toFloat()
            val y = size.height - ((value - lo) / span) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            color.copy(alpha = 0.55f),
            style = Stroke(width = 1.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
        val lastX = size.width
        val lastY = size.height - ((values.last() - lo) / span) * size.height
        drawCircle(viz.surface, radius = 3.5.dp.toPx(), center = Offset(lastX - 2.dp.toPx(), lastY))
        drawCircle(color, radius = 2.dp.toPx(), center = Offset(lastX - 2.dp.toPx(), lastY))
    }
}

/** Jauge de severite : la piste vide est un pas plus clair de la meme rampe. */
@Composable
fun SeverityMeter(fraction: Float, modifier: Modifier = Modifier, color: Color? = null) {
    val viz = LocalViz.current
    val fill = color ?: when {
        fraction >= 0.66f -> viz.critical
        fraction >= 0.33f -> viz.warning
        else -> viz.good
    }
    Canvas(modifier.height(6.dp)) {
        val radius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
        drawRoundRect(fill.copy(alpha = 0.18f), size = size, cornerRadius = radius)
        drawRoundRect(
            fill,
            size = Size(size.width * fraction.coerceIn(0f, 1f), size.height),
            cornerRadius = radius,
        )
    }
}

@Composable
private fun EmptyPlot(modifier: Modifier, height: Dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Pas encore de donnees",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Primitives de dessin ---------------------------------------------------

/** Barre verticale : extremite haute arrondie, base carree sur la ligne de zero. */
private fun DrawScope.drawVerticalBar(left: Float, top: Float, width: Float, height: Float, color: Color) {
    val radius = MARK_RADIUS.toPx().coerceAtMost(height / 2f)
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
    )
    drawRect(color, topLeft = Offset(left, top + height - radius), size = Size(width, radius))
}

/** Barre horizontale : extremite droite arrondie, depart carre sur l'axe. */
private fun DrawScope.drawHorizontalBar(left: Float, top: Float, length: Float, thickness: Float, color: Color) {
    val radius = MARK_RADIUS.toPx().coerceAtMost(length / 2f)
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(length, thickness),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
    )
    drawRect(color, topLeft = Offset(left, top), size = Size(radius, thickness))
}

private fun DrawScope.layout(measurer: TextMeasurer, text: String, color: Color, bold: Boolean = false):
    TextLayoutResult = measurer.measure(
    AnnotatedString(text),
    style = TextStyle(
        fontSize = 10.sp,
        color = color,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        fontFeatureSettings = "tnum",
    ),
)

private fun DrawScope.drawTickLabel(
    measurer: TextMeasurer,
    text: String,
    at: Offset,
    viz: VizPalette,
    right: Boolean = false,
) {
    val result = layout(measurer, text, viz.muted)
    val x = if (right) at.x - result.size.width else at.x
    drawText(result, topLeft = Offset(x, at.y))
}

private fun DrawScope.drawValueLabel(
    measurer: TextMeasurer,
    text: String,
    at: Offset,
    color: Color,
    anchorRight: Boolean = false,
) {
    val result = layout(measurer, text, color, bold = true)
    val x = (if (anchorRight) at.x - result.size.width else at.x)
        .coerceIn(0f, (size.width - result.size.width).coerceAtLeast(0f))
    drawText(result, topLeft = Offset(x, at.y))
}

private fun DrawScope.drawCenteredLabel(
    measurer: TextMeasurer,
    text: String,
    centerX: Float,
    y: Float,
    color: Color,
    bold: Boolean = false,
) {
    val result = layout(measurer, text, color, bold)
    val x = (centerX - result.size.width / 2f)
        .coerceIn(0f, (size.width - result.size.width).coerceAtLeast(0f))
    drawText(result, topLeft = Offset(x, y))
}

/** Bulle d'inspection : elle complete la lecture, elle ne la conditionne pas. */
private fun DrawScope.drawCallout(
    measurer: TextMeasurer,
    text: String,
    at: Offset,
    viz: VizPalette,
    ink: Color,
    plotLeft: Float,
    plotRight: Float,
) {
    val result = layout(measurer, text, ink, bold = true)
    val paddingX = 8.dp.toPx()
    val paddingY = 5.dp.toPx()
    val boxWidth = result.size.width + paddingX * 2
    val boxHeight = result.size.height + paddingY * 2
    val x = (at.x - boxWidth / 2f).coerceIn(plotLeft, (plotRight - boxWidth).coerceAtLeast(plotLeft))
    drawRoundRect(
        viz.surface,
        topLeft = Offset(x, at.y),
        size = Size(boxWidth, boxHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
    )
    drawRoundRect(
        viz.axis,
        topLeft = Offset(x, at.y),
        size = Size(boxWidth, boxHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
        style = Stroke(width = 1.dp.toPx()),
    )
    drawText(result, topLeft = Offset(x + paddingX, at.y + paddingY))
}

private fun indexAt(x: Float, width: Float, count: Int): Int {
    if (count <= 1) return 0
    val step = width / (count - 1)
    return (x / step).roundToInt().coerceIn(0, count - 1)
}

/** Bornes arrondies pour que les graduations tombent sur des nombres lisibles. */
private fun niceBounds(lo: Float, hi: Float): Pair<Float, Float> {
    val span = hi - lo
    if (span <= 0f) return lo to lo + 1f
    val magnitude = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(span.toDouble()))).toFloat()
    val step = when {
        span / magnitude < 2f -> magnitude / 2f
        span / magnitude < 5f -> magnitude
        else -> magnitude * 2f
    }
    val niceLo = kotlin.math.floor(lo / step) * step
    val niceHi = kotlin.math.ceil(hi / step) * step
    return niceLo to niceHi
}
