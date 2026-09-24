package com.chessforge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chessforge.analysis.Eval
import com.chessforge.chess.Fen
import com.chessforge.chess.Move
import com.chessforge.chess.Piece
import com.chessforge.chess.Position
import com.chessforge.chess.San
import com.chessforge.chess.pgn.PgnParser
import com.chessforge.chess.pgn.PlayedMove
import com.chessforge.data.model.Classification
import com.chessforge.data.model.GameRecord
import com.chessforge.data.model.MoveRecord
import com.chessforge.data.model.PuzzleKind
import com.chessforge.di.AppContainer
import com.chessforge.engine.EngineLimits
import com.chessforge.engine.EngineLine
import com.chessforge.ui.Format
import com.chessforge.ui.charts.ChartCard
import com.chessforge.ui.charts.Datum
import com.chessforge.ui.charts.LineChart
import com.chessforge.ui.components.BoardArrow
import com.chessforge.ui.components.ChessBoard
import com.chessforge.ui.components.ClassificationBadge
import com.chessforge.ui.components.EvalBar
import com.chessforge.ui.theme.BoardPalette
import com.chessforge.ui.theme.LocalViz
import com.chessforge.ui.theme.glyph
import com.chessforge.ui.theme.statusColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReviewViewModel(private val container: AppContainer, private val gameId: String) : ViewModel() {

    data class Sandbox(val position: Position, val history: List<String>, val thinking: Boolean)

    data class State(
        val game: GameRecord? = null,
        val played: List<PlayedMove> = emptyList(),
        val records: Map<Int, MoveRecord> = emptyMap(),
        val ply: Int = 0,
        val flipped: Boolean = false,
        val engineLine: EngineLine? = null,
        val engineBusy: Boolean = false,
        val sandbox: Sandbox? = null,
        val message: String? = null,
    ) {
        val currentFen: String
            get() = when {
                played.isEmpty() -> Fen.START
                ply < played.size -> played[ply].fenBefore
                else -> played.last().fenAfter
            }

        val lastMove: Int? get() = if (ply > 0 && ply <= played.size) played[ply - 1].move else null
        val currentRecord: MoveRecord? get() = records[ply]
        val previousRecord: MoveRecord? get() = records[ply - 1]
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val tasks = container.tasks.state
    val settings = container.settings.state

    init {
        load()
        viewModelScope.launch { container.tasks.revision.collect { load() } }
    }

    fun load() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val game = container.repository.game(gameId)
                val played = game?.let { PgnParser.parse(it.pgn) }?.let { PgnParser.replay(it) } ?: emptyList()
                val records = container.repository.moves(gameId).associateBy { it.ply }
                Triple(game, played, records)
            }
            _state.value = _state.value.copy(
                game = loaded.first,
                played = loaded.second,
                records = loaded.third,
                flipped = loaded.first?.userColor == Piece.BLACK,
            )
        }
    }

    fun goTo(ply: Int) {
        val max = _state.value.played.size
        _state.value = _state.value.copy(ply = ply.coerceIn(0, max), engineLine = null, sandbox = null)
    }

    fun next() = goTo(_state.value.ply + 1)
    fun previous() = goTo(_state.value.ply - 1)
    fun first() = goTo(0)
    fun last() = goTo(_state.value.played.size)
    fun flip() { _state.value = _state.value.copy(flipped = !_state.value.flipped) }
    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    /** Analyse a la demande de la position affichee, avec deux variantes. */
    fun analyzePosition() {
        if (_state.value.engineBusy) return
        val fen = _state.value.sandbox?.let { Fen.of(it.position) } ?: _state.value.currentFen
        _state.value = _state.value.copy(engineBusy = true)
        viewModelScope.launch {
            val engine = container.engines.engine()
            val lines = runCatching {
                engine.analyze(fen, EngineLimits(depth = 18, movetimeMs = 2_500, multiPv = 2))
            }.getOrDefault(emptyList())
            _state.value = _state.value.copy(engineBusy = false, engineLine = lines.firstOrNull())
        }
    }

    fun analyzeGame() = container.tasks.analyzeGame(gameId)

    fun makePuzzleHere() {
        val state = _state.value
        val game = state.game ?: return
        val fen = state.currentFen
        val playedSan = state.currentRecord?.san
        _state.value = state.copy(engineBusy = true)
        viewModelScope.launch {
            val puzzle = runCatching {
                container.repository.createPuzzleFromPosition(
                    gameId = game.id,
                    fen = fen,
                    ply = state.ply,
                    playedSan = playedSan,
                    kind = PuzzleKind.BLUNDER_FIX,
                )
            }.getOrNull()
            _state.value = _state.value.copy(
                engineBusy = false,
                message = if (puzzle == null) {
                    "Cette position n'a pas de solution assez nette pour devenir un puzzle."
                } else {
                    "Puzzle ajoute a votre file de revision."
                },
            )
        }
    }

    // --- Mode "rejouer depuis ici" ------------------------------------------

    fun startSandbox() {
        val position = Fen.parse(_state.value.currentFen)
        _state.value = _state.value.copy(sandbox = Sandbox(position, emptyList(), false))
    }

    fun exitSandbox() {
        _state.value = _state.value.copy(sandbox = null)
    }

    fun sandboxMove(from: Int, to: Int, promo: Int) {
        val sandbox = _state.value.sandbox ?: return
        if (sandbox.thinking) return
        val position = sandbox.position.copy()
        val move = position.legalMoves().firstOrNull {
            Move.from(it) == from && Move.to(it) == to && Move.promo(it) == promo
        } ?: return
        val san = San.of(position, move)
        if (!position.makeMove(move)) return
        _state.value = _state.value.copy(
            sandbox = Sandbox(position, sandbox.history + san, thinking = position.hasLegalMove()),
        )
        if (!position.hasLegalMove()) return

        viewModelScope.launch {
            val engine = container.engines.engine()
            val fen = Fen.of(position)
            val line = runCatching {
                engine.analyze(fen, EngineLimits(depth = 14, movetimeMs = 900)).firstOrNull()
            }.getOrNull()
            val reply = line?.bestMove?.let { San.fromUci(position, it) }
            val current = _state.value.sandbox ?: return@launch
            if (reply == null || reply == Move.NONE) {
                _state.value = _state.value.copy(sandbox = current.copy(thinking = false))
                return@launch
            }
            val replyPosition = current.position.copy()
            val replySan = San.of(replyPosition, reply)
            replyPosition.makeMove(reply)
            _state.value = _state.value.copy(
                sandbox = Sandbox(replyPosition, current.history + replySan, thinking = false),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameReviewScreen(
    viewModel: ReviewViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val task by viewModel.tasks.collectAsStateWithLifecycle()
    val game = state.game ?: return

    val palette = BoardPalette.byKey(settings.boardTheme)
    val position = remember(state.sandbox, state.ply, state.played.size) {
        state.sandbox?.position ?: Fen.parse(state.currentFen)
    }

    // Evaluation courante, du point de vue des blancs, pour la barre laterale.
    val evalRecord = state.currentRecord ?: state.previousRecord
    val whiteWin = evalRecord?.let {
        Eval(it.evalBefore, it.mateBefore).winPercentFor(Piece.WHITE)
    } ?: 50.0

    val bestArrow = state.currentRecord?.bestUci
        ?.takeIf { settings.showBestMoveArrow && state.sandbox == null }
        ?.let { uci ->
            val move = San.fromUci(position, uci)
            if (move == Move.NONE) null
            else BoardArrow(Move.from(move), Move.to(move), LocalViz.current.series(0).copy(alpha = 0.75f))
        }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 640.dp

        val boardBlock: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth()) {
                EvalBar(
                    whiteWinPercent = whiteWin,
                    flipped = state.flipped,
                    modifier = Modifier
                        .width(10.dp)
                        .height(if (twoPane) 420.dp else 340.dp),
                )
                Spacer(Modifier.width(8.dp))
                ChessBoard(
                    position = position,
                    modifier = Modifier.weight(1f),
                    palette = palette,
                    flipped = state.flipped,
                    lastMove = if (state.sandbox == null) state.lastMove else null,
                    arrows = listOfNotNull(bestArrow),
                    interactive = state.sandbox != null,
                    onMove = viewModel::sandboxMove,
                )
            }
        }

        val detailsBlock: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReviewHeader(game, state.ply, state.played.size)
                ReviewControls(viewModel, state)

                state.message?.let { message ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = viewModel::clearMessage) { Text("OK") }
                        }
                    }
                }

                if (state.sandbox != null) {
                    SandboxPanel(state, viewModel)
                } else {
                    MoveDetailCard(state, viewModel)
                }

                if (state.records.isNotEmpty()) {
                    EvalGraphCard(state, viewModel)
                }

                MoveListCard(state, viewModel)

                if (game.analysis == null) {
                    Button(
                        onClick = viewModel::analyzeGame,
                        enabled = !task.running,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Analyser cette partie") }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        if (twoPane) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) { boardBlock() }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) { detailsBlock() }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                boardBlock()
                detailsBlock()
            }
        }
    }
}

@Composable
private fun ReviewHeader(game: GameRecord, ply: Int, total: Int) {
    Column {
        Text(
            "${game.white} — ${game.black}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            buildString {
                append(Format.outcome(game.userOutcome))
                append(" · ")
                append(Format.timeClass(game.timeClass))
                append(" · ")
                append(Format.date(game.playedAt))
                game.openingName?.let { append(" · $it") }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        game.analysis?.let { analysis ->
            Text(
                "Precision ${analysis.accuracy.toInt()} vs ${analysis.opponentAccuracy.toInt()} · " +
                    "${analysis.blunders} gaffe(s), ${analysis.mistakes} erreur(s), " +
                    "${analysis.inaccuracies} imprecision(s)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Coup $ply / $total",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReviewControls(viewModel: ReviewViewModel, state: ReviewViewModel.State) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = viewModel::first) { Icon(Icons.Filled.FirstPage, "Debut") }
        FilledIconButton(onClick = viewModel::previous) { Icon(Icons.Filled.NavigateBefore, "Precedent") }
        FilledIconButton(onClick = viewModel::next) { Icon(Icons.Filled.NavigateNext, "Suivant") }
        IconButton(onClick = viewModel::last) { Icon(Icons.Filled.LastPage, "Fin") }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = viewModel::flip) { Icon(Icons.Filled.SwapVert, "Retourner le plateau") }
        IconButton(
            onClick = { if (state.sandbox == null) viewModel.startSandbox() else viewModel.exitSandbox() },
        ) {
            Icon(Icons.Filled.SportsEsports, "Jouer depuis cette position")
        }
        IconButton(onClick = viewModel::makePuzzleHere, enabled = !state.engineBusy) {
            Icon(Icons.Filled.Extension, "En faire un puzzle")
        }
    }
}

@Composable
private fun MoveDetailCard(state: ReviewViewModel.State, viewModel: ReviewViewModel) {
    val record = state.currentRecord
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (record == null) {
                Text("Position courante", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Cette partie n'est pas encore analysee, ou vous etes en fin de partie. " +
                        "Vous pouvez demander l'avis du moteur sur la position.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${record.notation} ${record.san}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(10.dp))
                    ClassificationBadge(record.classification)
                }
                Text(
                    "Evaluation ${Eval(record.evalBefore, record.mateBefore).format()} → " +
                        Eval(record.evalAfter, record.mateAfter).format() +
                        " · ${record.cpLoss} cp perdus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (record.classification.isError && record.bestSan != null) {
                    Text(
                        "Le moteur jouait ${record.bestSan}" +
                            (record.bestPvSan?.let { " (suite : $it)" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (record.motifs.isNotEmpty()) {
                    FlowRowChips(record.motifs.map { it.label })
                    Text(
                        record.motifs.first().advice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                record.secondsSpent?.let {
                    Text(
                        "Temps passe sur ce coup : ${Format.duration(it)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.engineLine?.let { line ->
                val position = Fen.parse(state.currentFen)
                Text(
                    "Moteur : ${line.score.format()} (profondeur ${line.depth}) · " +
                        San.lineToSan(position, line.pv.take(6)).joinToString(" "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = viewModel::analyzePosition, enabled = !state.engineBusy) {
                Text(if (state.engineBusy) "Calcul..." else "Demander au moteur")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(labels: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (label in labels) {
            AssistChip(onClick = {}, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
        }
    }
}

@Composable
private fun EvalGraphCard(state: ReviewViewModel.State, viewModel: ReviewViewModel) {
    val ordered = state.records.values.sortedBy { it.ply }
    if (ordered.isEmpty()) return
    ChartCard(
        title = "Courbe d'evaluation",
        subtitle = "Avantage des blancs, en centipions · appuyez pour aller au coup",
        tableRows = ordered.filter { it.classification.isError }
            .map { "${it.notation} ${it.san}" to "${it.classification.label} (${it.cpLoss} cp)" },
    ) {
        LineChart(
            data = ordered.map { Datum("${it.moveNumber}", it.evalAfter.coerceIn(-800, 800).toFloat()) },
            onSelect = { index -> viewModel.goTo(ordered[index].ply) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveListCard(state: ReviewViewModel.State, viewModel: ReviewViewModel) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Coups", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                state.played.forEachIndexed { index, played ->
                    val record = state.records[index]
                    val selected = state.ply == index
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Row(
                            Modifier
                                .plainClickable { viewModel.goTo(index) }
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (played.sideToMove == Piece.WHITE) {
                                Text(
                                    "${index / 2 + 1}.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(3.dp))
                            }
                            Text(played.san, style = MaterialTheme.typography.bodySmall)
                            if (record != null && record.classification.isError) {
                                Spacer(Modifier.width(3.dp))
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = record.classification.statusColor(),
                                ) {
                                    Text(
                                        record.classification.glyph,
                                        Modifier.padding(horizontal = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = androidx.compose.ui.graphics.Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SandboxPanel(state: ReviewViewModel.State, viewModel: ReviewViewModel) {
    val sandbox = state.sandbox ?: return
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Vous rejouez la position", style = MaterialTheme.typography.titleSmall)
            Text(
                "Jouez votre coup sur le plateau : le moteur repond. C'est le meilleur moyen de " +
                    "verifier que vous avez compris l'erreur, pas seulement memorise la solution.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sandbox.history.isNotEmpty()) {
                Text(sandbox.history.joinToString(" "), style = MaterialTheme.typography.bodySmall)
            }
            if (sandbox.thinking) {
                Text(
                    "Le moteur reflechit...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = viewModel::exitSandbox) { Text("Revenir a la partie reelle") }
        }
    }
}

/** Clic simple sans effet d'ondulation, pour les pastilles denses de la liste de coups. */
private fun Modifier.plainClickable(onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = null, indication = null, onClick = onClick)
