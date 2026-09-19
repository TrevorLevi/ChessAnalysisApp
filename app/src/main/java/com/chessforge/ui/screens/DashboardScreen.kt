package com.chessforge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chessforge.analysis.Weakness
import com.chessforge.analysis.WeaknessAction
import com.chessforge.data.model.Classification
import com.chessforge.data.model.Motif
import com.chessforge.data.model.Phase
import com.chessforge.data.repo.DashboardData
import com.chessforge.di.AppContainer
import com.chessforge.ui.Format
import com.chessforge.ui.charts.BarChart
import com.chessforge.ui.charts.ChartCard
import com.chessforge.ui.charts.Datum
import com.chessforge.ui.charts.DonutChart
import com.chessforge.ui.charts.HeatmapBoard
import com.chessforge.ui.charts.LineChart
import com.chessforge.ui.charts.Slice
import com.chessforge.ui.components.EmptyState
import com.chessforge.ui.components.HeroFigure
import com.chessforge.ui.components.SectionTitle
import com.chessforge.ui.components.StatTile
import com.chessforge.ui.components.TaskBanner
import com.chessforge.ui.components.WeaknessCard
import com.chessforge.ui.theme.LocalViz
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class DashboardViewModel(private val container: AppContainer) : ViewModel() {

    private val _data = MutableStateFlow<DashboardData?>(null)
    val data: StateFlow<DashboardData?> = _data.asStateFlow()

    val tasks = container.tasks.state
    val settings = container.settings.state

    init {
        load()
        viewModelScope.launch {
            container.tasks.revision.collect { load() }
        }
    }

    fun load() {
        viewModelScope.launch { _data.value = container.repository.dashboard() }
    }

    fun sync() = container.tasks.syncThenAnalyze(container.settings.current.autoAnalyzeAfterSync)
    fun analyzePending() = container.tasks.analyzePending(10)
    fun cancel() = container.tasks.cancel()
    fun clearMessage() = container.tasks.clearMessage()
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenPuzzles: (Motif?) -> Unit,
    onOpenGames: () -> Unit,
    onOpenOpening: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val task by viewModel.tasks.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 640.dp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            TaskBanner(
                running = task.running,
                label = task.label,
                detail = task.detail,
                progress = task.progress,
                message = task.result ?: task.error,
                isError = task.error != null,
                onCancel = viewModel::cancel,
                onDismiss = viewModel::clearMessage,
            )

            if (!settings.isConfigured) {
                EmptyState(
                    title = "Reliez votre compte chess.com",
                    message = "Indiquez votre pseudo dans les reglages : l'appli telecharge vos parties " +
                        "publiques, les analyse hors ligne et en tire vos puzzles personnels.",
                    actionLabel = "Ouvrir les reglages",
                    onAction = onOpenSettings,
                )
                return@Column
            }

            val current = data
            if (current == null) {
                Text(
                    "Chargement...",
                    Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            if (current.summary.games == 0) {
                EmptyState(
                    title = "Aucune partie importee",
                    message = "Lancez un import : les ${settings.syncMonths} derniers mois de " +
                        "${settings.username} seront telecharges puis analyses.",
                    actionLabel = "Importer mes parties",
                    onAction = viewModel::sync,
                )
                return@Column
            }

            val blocks = dashboardBlocks(
                data = current,
                onOpenPuzzles = onOpenPuzzles,
                onOpenGames = onOpenGames,
                onOpenOpening = onOpenOpening,
                onSync = viewModel::sync,
                onAnalyze = viewModel::analyzePending,
                busy = task.running,
            )

            if (twoPane) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        blocks.filterIndexed { index, _ -> index % 2 == 0 }.forEach { it() }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        blocks.filterIndexed { index, _ -> index % 2 == 1 }.forEach { it() }
                    }
                }
            } else {
                blocks.forEach { it() }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Le tableau de bord est decrit comme une liste de blocs : en ecran deplie, ils se
 * repartissent sur deux colonnes sans dupliquer le code de mise en page.
 */
@Composable
private fun dashboardBlocks(
    data: DashboardData,
    onOpenPuzzles: (Motif?) -> Unit,
    onOpenGames: () -> Unit,
    onOpenOpening: (String) -> Unit,
    onSync: () -> Unit,
    onAnalyze: () -> Unit,
    busy: Boolean,
): List<@Composable () -> Unit> {
    val viz = LocalViz.current
    val summary = data.summary
    val trend = data.trend

    return buildList {
        add {
            HeroFigure(
                label = "Precision moyenne",
                value = Format.oneDecimal(summary.avgAccuracy),
                subtitle = "sur ${summary.analyzedGames} partie(s) analysee(s) · moteur ${data.engineName}",
                trend = trend.map { it.accuracy.toFloat() }.takeLast(12),
            )
        }

        add {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = "Perte moyenne par coup",
                    value = "${summary.avgAcpl.roundToInt()} cp",
                    modifier = Modifier.weight(1f),
                    trend = trend.map { it.acpl.toFloat() }.takeLast(12),
                    seriesIndex = 1,
                )
                StatTile(
                    label = "Gaffes / 100 coups",
                    value = Format.oneDecimal(summary.blundersPer100),
                    modifier = Modifier.weight(1f),
                    delta = "${(summary.bestMoveRate * 100).roundToInt()} % de meilleurs coups",
                )
            }
        }

        add {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = "Puzzles a revoir",
                    value = data.puzzleProgress.due.toString(),
                    modifier = Modifier.weight(1f),
                    delta = "${data.puzzleProgress.total} en reserve",
                )
                StatTile(
                    label = "Serie de jours",
                    value = data.puzzleProgress.streakDays.toString(),
                    modifier = Modifier.weight(1f),
                    delta = "${data.puzzleProgress.solvedToday} resolus aujourd'hui",
                )
            }
        }

        add { TrainingPlanCard(data, onOpenPuzzles) }

        add {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Mise a jour", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (data.pendingAnalysis > 0) {
                            "${data.pendingAnalysis} partie(s) importee(s) attendent l'analyse."
                        } else {
                            "Toutes les parties importees sont analysees."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onSync, enabled = !busy) {
                            Icon(Icons.Filled.CloudDownload, null, Modifier.height(18.dp))
                            Text("  Importer")
                        }
                        if (data.pendingAnalysis > 0) {
                            OutlinedButton(onClick = onAnalyze, enabled = !busy) {
                                Icon(Icons.Filled.Insights, null, Modifier.height(18.dp))
                                Text("  Analyser")
                            }
                        }
                    }
                }
            }
        }

        if (data.weaknesses.isNotEmpty()) {
            add { SectionTitle("Vos points a travailler") }
            for (weakness in data.weaknesses.take(5)) {
                add { WeaknessBlock(weakness, onOpenPuzzles, onOpenOpening) }
            }
        }

        add {
            ChartCard(
                title = "Precision partie par partie",
                subtitle = "${trend.size} dernieres parties analysees · appuyez pour lire une valeur",
                tableRows = trend.takeLast(12).map { Format.dateShort(it.playedAt) to Format.oneDecimal(it.accuracy) },
            ) {
                LineChart(
                    data = trend.map { Datum(Format.dateShort(it.playedAt), it.accuracy.toFloat()) },
                    valueSuffix = "",
                    yMin = 0f,
                    yMax = 100f,
                )
            }
        }

        if (data.ratings.size >= 2) {
            add {
                val byClass = data.ratings.groupBy { it.timeClass }
                val main = byClass.maxByOrNull { it.value.size }
                ChartCard(
                    title = "Classement ${Format.timeClass(main?.key)}",
                    subtitle = "Evolution de votre elo chess.com",
                    tableRows = main?.value?.takeLast(12)
                        ?.map { Format.dateShort(it.at) to it.rating.toString() } ?: emptyList(),
                ) {
                    LineChart(
                        data = main?.value?.map { Datum(Format.dateShort(it.at), it.rating.toFloat()) }
                            ?: emptyList(),
                        seriesIndex = 2,
                    )
                }
            }
        }

        add {
            val counts = data.classifications
            fun sum(vararg keys: Classification) = keys.sumOf { counts[it] ?: 0 }
            val solid = sum(
                Classification.BRILLIANT, Classification.GREAT, Classification.BEST,
                Classification.EXCELLENT, Classification.GOOD, Classification.BOOK, Classification.FORCED,
            )
            val inaccuracies = sum(Classification.INACCURACY)
            val mistakes = sum(Classification.MISTAKE, Classification.MISS)
            val blunders = sum(Classification.BLUNDER)
            val total = (solid + inaccuracies + mistakes + blunders).coerceAtLeast(1)
            val slices = listOf(
                Slice("Coups solides", solid.toFloat(), viz.good, "${solid * 100 / total} %"),
                Slice("Imprecisions", inaccuracies.toFloat(), viz.warning, "${inaccuracies * 100 / total} %"),
                Slice("Erreurs", mistakes.toFloat(), viz.serious, "${mistakes * 100 / total} %"),
                Slice("Gaffes", blunders.toFloat(), viz.critical, "${blunders * 100 / total} %"),
            )
            ChartCard(
                title = "Repartition de vos coups",
                subtitle = "$total coups joues",
                tableRows = slices.map { it.label to "${it.value.toInt()} (${it.valueLabel})" },
            ) {
                DonutChart(
                    slices = slices,
                    centerValue = "${solid * 100 / total} %",
                    centerLabel = "de coups solides",
                )
            }
        }

        add {
            val phases = data.phases.filter { it.moves > 0 }
            ChartCard(
                title = "Ou vous perdez le plus",
                subtitle = "Centipions perdus par coup, selon la phase",
                tableRows = phases.map { it.phase.label to "${it.avgCpLoss.roundToInt()} cp" },
            ) {
                BarChart(
                    data = phases.map {
                        Datum(it.phase.label.take(11), it.avgCpLoss.toFloat(), "${it.avgCpLoss.roundToInt()}")
                    },
                )
            }
        }

        if (data.motifErrors.isNotEmpty()) {
            add {
                val top = data.motifErrors.entries.take(6)
                ChartCard(
                    title = "Motifs de vos erreurs",
                    subtitle = "Nombre de fautes portant ce theme",
                    tableRows = top.map { it.key.label to it.value.toString() },
                ) {
                    BarChart(
                        data = top.map { Datum(it.key.label, it.value.toFloat()) },
                        horizontal = true,
                        height = (top.size * 34).dp,
                        seriesIndex = 1,
                    )
                }
            }
        }

        if (data.timePressure.isNotEmpty()) {
            add {
                ChartCard(
                    title = "Erreurs et temps de reflexion",
                    subtitle = "Part de fautes selon le temps passe sur le coup",
                    tableRows = data.timePressure.map {
                        it.bucketLabel to "${(it.errorRate * 100).roundToInt()} % (${it.moves} coups)"
                    },
                ) {
                    BarChart(
                        data = data.timePressure.map {
                            Datum(it.bucketLabel, (it.errorRate * 100).toFloat(), "${(it.errorRate * 100).roundToInt()} %")
                        },
                        seriesIndex = 3,
                    )
                }
            }
        }

        if (data.heatmap.any { it > 0 }) {
            add {
                ChartCard(
                    title = "Ou vos gaffes atterrissent",
                    subtitle = "Case d'arrivee de vos erreurs graves, vue des blancs",
                    tableRows = data.heatmap.withIndex()
                        .filter { it.value > 0 }
                        .sortedByDescending { it.value }
                        .take(8)
                        .map { com.chessforge.chess.Square.name(it.index) to it.value.toString() },
                ) {
                    HeatmapBoard(values = data.heatmap)
                }
            }
        }

        if (data.timeClasses.size >= 2) {
            add {
                ChartCard(
                    title = "Score par cadence",
                    subtitle = "Points marques par partie (1 = victoire)",
                    tableRows = data.timeClasses.map {
                        Format.timeClass(it.timeClass) to "${(it.score * 100).roundToInt()} % (${it.games} parties)"
                    },
                ) {
                    BarChart(
                        data = data.timeClasses.map {
                            Datum(
                                Format.timeClass(it.timeClass),
                                (it.score * 100).toFloat(),
                                "${(it.score * 100).roundToInt()} %",
                            )
                        },
                        seriesIndex = 2,
                    )
                }
            }
        }

        add {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Vos parties", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${summary.games} partie(s) · ${Format.ratio(summary.winRate)} de victoires" +
                            (summary.lastPlayedAt?.let { " · derniere ${Format.relative(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onOpenGames) { Text("Parcourir et rejouer") }
                }
            }
        }
    }
}

@Composable
private fun WeaknessBlock(
    weakness: Weakness,
    onOpenPuzzles: (Motif?) -> Unit,
    onOpenOpening: (String) -> Unit,
) {
    val action = weakness.action
    var label: String? = null
    var callback: (() -> Unit)? = null
    when (action) {
        is WeaknessAction.TrainMotif -> {
            label = "Entrainer ce motif"
            callback = { onOpenPuzzles(action.motif) }
        }
        is WeaknessAction.TrainPhase -> {
            label = "Voir les puzzles de cette phase"
            callback = { onOpenPuzzles(null) }
        }
        is WeaknessAction.ReviewOpening -> {
            label = "Revoir cette ouverture"
            callback = { onOpenOpening(action.family) }
        }
        WeaknessAction.TrainAll -> {
            label = "Lancer une serie"
            callback = { onOpenPuzzles(null) }
        }
        WeaknessAction.SlowDown, WeaknessAction.None -> {}
    }
    WeaknessCard(
        title = weakness.title,
        detail = weakness.detail,
        advice = weakness.advice,
        severity = weakness.severity,
        metricValue = weakness.metricValue,
        metricLabel = weakness.metricLabel,
        actionLabel = label,
        onAction = callback,
    )
}

@Composable
private fun TrainingPlanCard(data: DashboardData, onOpenPuzzles: (Motif?) -> Unit) {
    val plan = data.plan
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Seance du jour", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${plan.totalMinutes} min · objectif ${plan.solvedToday}/${plan.dailyGoal} puzzles",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (plan.goalReached) {
                    Text("Objectif atteint", style = MaterialTheme.typography.labelLarge)
                }
            }
            for (item in plan.items) {
                Column {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        item.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = {
                    val motif = plan.items.firstNotNullOfOrNull {
                        (it.action as? WeaknessAction.TrainMotif)?.motif
                    }
                    onOpenPuzzles(motif)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (plan.dueCount > 0) "Commencer (${plan.dueCount} a revoir)" else "Commencer la seance")
            }
        }
    }
}

/** Phase la plus faible, utilisee par les raccourcis de navigation. */
fun DashboardData.weakestPhase(): Phase? =
    phases.filter { it.moves >= 20 }.maxByOrNull { it.avgCpLoss }?.phase
