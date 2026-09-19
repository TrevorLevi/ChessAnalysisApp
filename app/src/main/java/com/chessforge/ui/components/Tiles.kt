package com.chessforge.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessforge.data.model.Classification
import com.chessforge.ui.charts.SeverityMeter
import com.chessforge.ui.charts.Sparkline
import com.chessforge.ui.theme.LocalViz
import com.chessforge.ui.theme.glyph
import com.chessforge.ui.theme.statusColor

/**
 * Chiffre phare de la vue. Un seul par ecran : c'est la reponse a la question
 * "est-ce que je progresse ?".
 */
@Composable
fun HeroFigure(
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trend: List<Float> = emptyList(),
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    value,
                    // Chiffres proportionnels : un grand nombre en chasse fixe paraitrait lache.
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (trend.size >= 2) {
                Sparkline(
                    trend,
                    Modifier
                        .width(96.dp)
                        .height(44.dp),
                )
            }
        }
    }
}

/** Tuile de statistique : libelle, valeur, variation facultative, mini-courbe facultative. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
    deltaIsGood: Boolean? = null,
    trend: List<Float> = emptyList(),
    seriesIndex: Int = 0,
) {
    val viz = LocalViz.current
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (delta != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val color = when (deltaIsGood) {
                        true -> viz.good
                        false -> viz.critical
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    if (deltaIsGood != null) {
                        Icon(
                            imageVector = if (deltaIsGood) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                            contentDescription = if (deltaIsGood) "en hausse" else "en baisse",
                            tint = color,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Text(
                        delta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trend.size >= 2) {
                Spacer(Modifier.height(8.dp))
                Sparkline(
                    trend,
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    seriesIndex = seriesIndex,
                )
            }
        }
    }
}

/**
 * Pastille de qualite d'un coup. La couleur exprime l'etat, le symbole et le libelle
 * le disent : l'information ne repose jamais sur la couleur seule.
 */
@Composable
fun ClassificationBadge(
    classification: Classification,
    modifier: Modifier = Modifier,
    withLabel: Boolean = true,
) {
    val color = classification.statusColor()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(18.dp)
                .background(color, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                classification.glyph,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
        if (withLabel) {
            Spacer(Modifier.width(6.dp))
            Text(
                classification.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        action?.invoke()
    }
}

/** Carte de faiblesse : constat chiffre, conseil, severite, action. */
@Composable
fun WeaknessCard(
    title: String,
    detail: String,
    advice: String,
    severity: Int,
    metricValue: String,
    metricLabel: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(metricValue, style = MaterialTheme.typography.titleMedium)
                    Text(
                        metricLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            SeverityMeter(severity / 100f, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text(
                advice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onAction, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/** Banniere de progression des taches de fond (import, analyse). */
@Composable
fun TaskBanner(
    running: Boolean,
    label: String,
    detail: String,
    progress: Float?,
    message: String?,
    isError: Boolean,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viz = LocalViz.current
    AnimatedVisibility(visible = running || message != null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = if (isError) viz.critical.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (running) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        message ?: label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    if (running && detail.isNotBlank()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (running && progress != null) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (running) {
                    TextButton(onClick = onCancel) { Text("Arreter") }
                } else {
                    TextButton(onClick = onDismiss) { Text("OK") }
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
