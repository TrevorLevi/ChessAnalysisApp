package com.chessforge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chessforge.data.db.OpeningRow
import com.chessforge.di.AppContainer
import com.chessforge.ui.charts.BarChart
import com.chessforge.ui.charts.ChartCard
import com.chessforge.ui.charts.Datum
import com.chessforge.ui.charts.SeverityMeter
import com.chessforge.ui.components.EmptyState
import com.chessforge.ui.theme.LocalViz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class OpeningsViewModel(private val container: AppContainer) : ViewModel() {

    data class State(
        val rows: List<OpeningRow> = emptyList(),
        val white: OpeningRow? = null,
        val black: OpeningRow? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch { container.tasks.revision.collect { load() } }
        load()
    }

    fun load() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val rows = container.repository.openings(minGames = 1)
                val split = container.repository.colorSplit()
                Triple(rows, split.first, split.second)
            }
            _state.value = State(loaded.first, loaded.second, loaded.third)
        }
    }
}

@Composable
fun OpeningsScreen(
    viewModel: OpeningsViewModel,
    onOpenFamily: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.rows.isEmpty()) {
        EmptyState(
            title = "Pas encore de repertoire",
            message = "Vos ouvertures apparaissent ici des que des parties sont importees.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val white = state.white
            val black = state.black
            if (white != null && black != null && white.games + black.games > 0) {
                ChartCard(
                    title = "Score par couleur",
                    subtitle = "Points marques par partie",
                    tableRows = listOf(
                        "Blancs" to "${(white.score * 100).roundToInt()} % (${white.games} parties)",
                        "Noirs" to "${(black.score * 100).roundToInt()} % (${black.games} parties)",
                    ),
                ) {
                    BarChart(
                        data = listOf(
                            Datum("Blancs", (white.score * 100).toFloat(), "${(white.score * 100).roundToInt()} %"),
                            Datum("Noirs", (black.score * 100).toFloat(), "${(black.score * 100).roundToInt()} %"),
                        ),
                        height = 150.dp,
                    )
                }
            }
        }

        item {
            val top = state.rows.filter { it.games >= 2 }.take(8)
            if (top.isNotEmpty()) {
                ChartCard(
                    title = "Vos ouvertures les plus jouees",
                    subtitle = "Score en pourcentage des points possibles",
                    tableRows = top.map {
                        it.family to "${(it.score * 100).roundToInt()} % sur ${it.games} parties"
                    },
                ) {
                    BarChart(
                        data = top.map {
                            Datum(it.family, (it.score * 100).toFloat(), "${(it.score * 100).roundToInt()} %")
                        },
                        horizontal = true,
                        height = (top.size * 34).dp,
                    )
                }
            }
        }

        item {
            Text(
                "Detail par famille",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        items(state.rows, key = { it.family }) { row ->
            OpeningCard(row) { onOpenFamily(row.family) }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun OpeningCard(row: OpeningRow, onClick: () -> Unit) {
    val viz = LocalViz.current
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.family, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${(row.score * 100).roundToInt()} %",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                "${row.games} partie(s) · ${row.wins}V ${row.draws}N ${row.losses}D · " +
                    "${row.avgAcpl.roundToInt()} cp perdus par coup",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            // La jauge reprend la convention de severite : sous 40 % de score, c'est une fuite.
            SeverityMeter(
                fraction = row.score.toFloat(),
                modifier = Modifier.fillMaxWidth(),
                color = when {
                    row.score < 0.40 -> viz.critical
                    row.score < 0.50 -> viz.warning
                    else -> viz.good
                },
            )
        }
    }
}
