package com.chessforge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chessforge.chess.Piece
import com.chessforge.data.db.GameFilter
import com.chessforge.data.model.GameRecord
import com.chessforge.di.AppContainer
import com.chessforge.ui.Format
import com.chessforge.ui.components.EmptyState
import com.chessforge.ui.theme.LocalViz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class GamesViewModel(
    private val container: AppContainer,
    initialFamily: String? = null,
) : ViewModel() {

    private val _filter = MutableStateFlow(GameFilter(openingFamily = initialFamily))
    val filter: StateFlow<GameFilter> = _filter.asStateFlow()

    private val _games = MutableStateFlow<List<GameRecord>>(emptyList())
    val games: StateFlow<List<GameRecord>> = _games.asStateFlow()

    val tasks = container.tasks.state

    init {
        load()
        viewModelScope.launch { container.tasks.revision.collect { load() } }
    }

    fun load() {
        viewModelScope.launch {
            _games.value = withContext(Dispatchers.IO) { container.repository.games(_filter.value) }
        }
    }

    fun setFilter(update: (GameFilter) -> GameFilter) {
        _filter.value = update(_filter.value)
        load()
    }

    fun analyze(gameId: String) = container.tasks.analyzeGame(gameId)
}

@Composable
fun GamesScreen(
    viewModel: GamesViewModel,
    onOpenGame: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val games by viewModel.games.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        // Une seule barre de filtres, au-dessus de tout ce qu'elle concerne.
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter.analyzedOnly,
                onClick = { viewModel.setFilter { it.copy(analyzedOnly = !it.analyzedOnly) } },
                label = { Text("Analysees") },
            )
            for (outcome in listOf("win" to "Victoires", "loss" to "Defaites", "draw" to "Nulles")) {
                FilterChip(
                    selected = filter.outcome == outcome.first,
                    onClick = {
                        viewModel.setFilter {
                            it.copy(outcome = if (it.outcome == outcome.first) null else outcome.first)
                        }
                    },
                    label = { Text(outcome.second) },
                )
            }
            for (color in listOf(Piece.WHITE to "Blancs", Piece.BLACK to "Noirs")) {
                FilterChip(
                    selected = filter.color == color.first,
                    onClick = {
                        viewModel.setFilter {
                            it.copy(color = if (it.color == color.first) null else color.first)
                        }
                    },
                    label = { Text(color.second) },
                )
            }
            for (tc in listOf("bullet", "blitz", "rapid", "daily")) {
                FilterChip(
                    selected = filter.timeClass == tc,
                    onClick = {
                        viewModel.setFilter { it.copy(timeClass = if (it.timeClass == tc) null else tc) }
                    },
                    label = { Text(Format.timeClass(tc)) },
                )
            }
        }

        if (games.isEmpty()) {
            EmptyState(
                title = "Aucune partie ne correspond",
                message = "Relachez un filtre, ou lancez un import depuis le tableau de bord.",
            )
            return@Column
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(games, key = { it.id }) { game ->
                GameRow(game, onClick = { onOpenGame(game.id) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun GameRow(game: GameRecord, onClick: () -> Unit) {
    val viz = LocalViz.current
    val outcomeColor = when (game.userOutcome) {
        "win" -> viz.good
        "loss" -> viz.critical
        else -> viz.neutral
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // Le resultat est porte par une pastille *et* par un mot : jamais la couleur seule.
            Column(
                Modifier.width(52.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = outcomeColor,
                ) {
                    Text(
                        when (game.userOutcome) {
                            "win" -> "V"
                            "loss" -> "D"
                            else -> "N"
                        },
                        Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    game.userColorLabel.take(5),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    game.opponent,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    buildString {
                        game.opponentRating?.let { append("$it · ") }
                        append(Format.timeClass(game.timeClass))
                        append(" · ")
                        append(Format.dateShort(game.playedAt))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                game.openingName?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                val analysis = game.analysis
                if (analysis == null) {
                    Text(
                        "non analysee",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "${analysis.accuracy.roundToInt()}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "precision",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (analysis.blunders > 0) {
                        Text(
                            "${analysis.blunders} gaffe(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
