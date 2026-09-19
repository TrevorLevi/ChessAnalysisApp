package com.chessforge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.chessforge.data.db.PuzzleProgress
import com.chessforge.data.db.PuzzleQuery
import com.chessforge.data.model.Motif
import com.chessforge.data.model.Puzzle
import com.chessforge.di.AppContainer
import com.chessforge.engine.EngineLimits
import com.chessforge.srs.Srs
import com.chessforge.ui.Format
import com.chessforge.ui.components.ChessBoard
import com.chessforge.ui.components.EmptyState
import com.chessforge.ui.components.StatTile
import com.chessforge.ui.theme.BoardPalette
import com.chessforge.ui.theme.LocalViz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// --- Liste des puzzles ------------------------------------------------------

class PuzzleListViewModel(private val container: AppContainer) : ViewModel() {

    data class State(
        val progress: PuzzleProgress? = null,
        val motifCounts: Map<Motif, Int> = emptyMap(),
        val puzzles: List<Puzzle> = emptyList(),
        val motif: Motif? = null,
        val dueOnly: Boolean = true,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch { container.tasks.revision.collect { load() } }
        load()
    }

    fun load() {
        viewModelScope.launch {
            val snapshot = _state.value
            val loaded = withContext(Dispatchers.IO) {
                val puzzles = container.repository.puzzles(
                    PuzzleQuery(dueOnly = snapshot.dueOnly, motif = snapshot.motif, limit = 60)
                )
                Triple(
                    container.repository.puzzleProgress(),
                    container.repository.puzzleCountsByMotif(),
                    puzzles,
                )
            }
            _state.value = snapshot.copy(
                progress = loaded.first,
                motifCounts = loaded.second,
                puzzles = loaded.third,
            )
        }
    }

    fun setMotif(motif: Motif?) {
        _state.value = _state.value.copy(motif = motif)
        load()
    }

    fun toggleDueOnly() {
        _state.value = _state.value.copy(dueOnly = !_state.value.dueOnly)
        load()
    }
}

@Composable
fun PuzzleListScreen(
    viewModel: PuzzleListViewModel,
    onTrain: (Motif?, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress = state.progress

    Column(modifier.fillMaxSize()) {
        if (progress != null) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatTile("A revoir", progress.due.toString(), Modifier.weight(1f))
                StatTile("Reussite", "${(progress.successRate * 100).roundToInt()} %", Modifier.weight(1f))
                StatTile("Serie", "${progress.streakDays} j", Modifier.weight(1f))
            }
        }

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.dueOnly,
                onClick = viewModel::toggleDueOnly,
                label = { Text("A revoir aujourd'hui") },
            )
            FilterChip(
                selected = state.motif == null,
                onClick = { viewModel.setMotif(null) },
                label = { Text("Tous les motifs") },
            )
            for ((motif, count) in state.motifCounts) {
                FilterChip(
                    selected = state.motif == motif,
                    onClick = { viewModel.setMotif(if (state.motif == motif) null else motif) },
                    label = { Text("${motif.label} ($count)") },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onTrain(state.motif, state.dueOnly) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            enabled = state.puzzles.isNotEmpty(),
        ) {
            Text(
                if (state.puzzles.isEmpty()) "Rien a entrainer pour l'instant"
                else "Demarrer la seance (${state.puzzles.size} puzzles)",
            )
        }

        if (state.puzzles.isEmpty()) {
            EmptyState(
                title = "File vide",
                message = "Les puzzles naissent de vos erreurs : importez et analysez des parties, " +
                    "ou relachez le filtre \"a revoir aujourd'hui\".",
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.puzzles, key = { it.id }) { puzzle ->
                PuzzleRow(puzzle)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PuzzleRow(puzzle: Puzzle) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(puzzle.kind.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(
                    "difficulte ${puzzle.difficulty}/5",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                buildString {
                    append(if (puzzle.userColor == Piece.WHITE) "Blancs" else "Noirs")
                    append(" · coup ${puzzle.ply / 2 + 1}")
                    puzzle.opponentName?.let { append(" · contre $it") }
                    append(" · ${Format.dateShort(puzzle.playedAt)}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (puzzle.motifs.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (motif in puzzle.motifs.take(4)) {
                        AssistChip(
                            onClick = {},
                            label = { Text(motif.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
            Text(
                "Revision ${Srs.dueLabel(puzzle.srs.dueAt)} · ${puzzle.srs.reps} passage(s)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- Entraineur -------------------------------------------------------------

class TrainerViewModel(
    private val container: AppContainer,
    private val motif: Motif?,
    private val dueOnly: Boolean,
) : ViewModel() {

    enum class Status { LOADING, SOLVING, CHECKING, WRONG, SOLVED, FINISHED }

    data class State(
        val queue: List<Puzzle> = emptyList(),
        val index: Int = 0,
        val position: Position = Position.startPosition(),
        val step: Int = 0,
        val status: Status = Status.LOADING,
        val hints: Int = 0,
        val startedAt: Long = 0L,
        val wrongMove: String? = null,
        val alternativeAccepted: Boolean = false,
        val revealed: Boolean = false,
        val solvedCount: Int = 0,
        val failedCount: Int = 0,
    ) {
        val puzzle: Puzzle? get() = queue.getOrNull(index)
        /** Coups de la solution deja joues, en notation lisible. */
        fun playedSoFar(): List<String> = puzzle?.solutionSan?.take(step) ?: emptyList()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    val settings = container.settings.state

    init {
        viewModelScope.launch {
            val queue = withContext(Dispatchers.IO) {
                container.repository.puzzles(PuzzleQuery(dueOnly = dueOnly, motif = motif, limit = 30))
            }
            _state.value = State(queue = queue, status = if (queue.isEmpty()) Status.FINISHED else Status.LOADING)
            if (queue.isNotEmpty()) startPuzzle(0)
        }
    }

    private fun startPuzzle(index: Int) {
        val queue = _state.value.queue
        if (index >= queue.size) {
            _state.value = _state.value.copy(status = Status.FINISHED)
            return
        }
        val puzzle = queue[index]
        _state.value = _state.value.copy(
            index = index,
            position = Fen.parse(puzzle.fen),
            step = 0,
            status = Status.SOLVING,
            hints = 0,
            startedAt = System.currentTimeMillis(),
            wrongMove = null,
            alternativeAccepted = false,
            revealed = false,
        )
    }

    fun next() = startPuzzle(_state.value.index + 1)

    fun onMove(from: Int, to: Int, promo: Int) {
        val state = _state.value
        val puzzle = state.puzzle ?: return
        if (state.status != Status.SOLVING) return
        val position = state.position
        val move = position.legalMoves().firstOrNull {
            Move.from(it) == from && Move.to(it) == to && Move.promo(it) == promo
        } ?: return
        val uci = Move.toUci(move)
        val expected = puzzle.solutionUci.getOrNull(state.step)

        if (uci == expected) {
            advance(move)
            return
        }
        // Un autre coup peut etre aussi bon : on demande au moteur avant de sanctionner.
        _state.value = state.copy(status = Status.CHECKING)
        viewModelScope.launch { checkAlternative(puzzle, position, move, uci) }
    }

    private suspend fun checkAlternative(puzzle: Puzzle, position: Position, move: Int, uci: String) {
        val solver = position.side
        val engine = container.engines.engine()
        val limits = EngineLimits(depth = 14, movetimeMs = 700)

        val afterUser = position.copy()
        if (!afterUser.makeMove(move)) {
            _state.value = _state.value.copy(status = Status.SOLVING)
            return
        }
        val userEval = runCatching { engine.analyze(Fen.of(afterUser), limits).firstOrNull() }.getOrNull()
        val expectedUci = puzzle.solutionUci.getOrNull(_state.value.step)
        val expectedMove = expectedUci?.let { San.fromUci(position, it) } ?: Move.NONE
        val afterBest = position.copy()
        if (expectedMove == Move.NONE || !afterBest.makeMove(expectedMove)) {
            markWrong(uci)
            return
        }
        val bestEval = runCatching { engine.analyze(Fen.of(afterBest), limits).firstOrNull() }.getOrNull()

        val userWin = userEval?.let { Eval.from(it.score, afterUser.side).winPercentFor(solver) } ?: 0.0
        val bestWin = bestEval?.let { Eval.from(it.score, afterBest.side).winPercentFor(solver) } ?: 100.0

        if (userWin >= bestWin - ALTERNATIVE_TOLERANCE) {
            // Coup different mais equivalent : on valide et on cloture le puzzle.
            _state.value = _state.value.copy(position = afterUser, alternativeAccepted = true)
            finish(success = true, firstMoveUci = uci)
        } else {
            markWrong(uci)
        }
    }

    private fun markWrong(uci: String) {
        val state = _state.value
        _state.value = state.copy(status = Status.WRONG, wrongMove = uci, failedCount = state.failedCount + 1)
        record(success = false, firstMoveUci = uci)
    }

    /** Joue le coup de la solution, puis la reponse adverse imposee par la variante. */
    private fun advance(move: Int) {
        val state = _state.value
        val puzzle = state.puzzle ?: return
        val position = state.position.copy()
        if (!position.makeMove(move)) return
        var step = state.step + 1

        if (step >= puzzle.solutionUci.size || !position.hasLegalMove()) {
            _state.value = state.copy(position = position, step = step)
            finish(success = true, firstMoveUci = puzzle.solutionUci.firstOrNull())
            return
        }

        // Reponse adverse de la variante principale
        val replyUci = puzzle.solutionUci[step]
        val reply = San.fromUci(position, replyUci)
        if (reply != Move.NONE && position.makeMove(reply)) step++

        if (step >= puzzle.solutionUci.size) {
            _state.value = state.copy(position = position, step = step)
            finish(success = true, firstMoveUci = puzzle.solutionUci.firstOrNull())
        } else {
            _state.value = state.copy(position = position, step = step, status = Status.SOLVING)
        }
    }

    private fun finish(success: Boolean, firstMoveUci: String?) {
        val state = _state.value
        _state.value = state.copy(
            status = Status.SOLVED,
            solvedCount = state.solvedCount + if (success) 1 else 0,
        )
        record(success, firstMoveUci)
    }

    private fun record(success: Boolean, firstMoveUci: String?) {
        val state = _state.value
        val puzzle = state.puzzle ?: return
        val elapsed = System.currentTimeMillis() - state.startedAt
        viewModelScope.launch(Dispatchers.IO) {
            container.repository.recordPuzzleResult(puzzle, success, elapsed, state.hints, firstMoveUci)
        }
    }

    fun useHint() {
        val state = _state.value
        if (state.status != Status.SOLVING) return
        _state.value = state.copy(hints = state.hints + 1)
    }

    fun revealSolution() {
        val state = _state.value
        if (state.status == Status.SOLVING) {
            _state.value = state.copy(revealed = true)
            markWrong("abandon")
        } else {
            _state.value = state.copy(revealed = true)
        }
    }

    fun retry() {
        val puzzle = _state.value.puzzle ?: return
        _state.value = _state.value.copy(
            position = Fen.parse(puzzle.fen),
            step = 0,
            status = Status.SOLVING,
            wrongMove = null,
            revealed = false,
            startedAt = System.currentTimeMillis(),
        )
    }

    companion object {
        /** Tolerance en points de probabilite de gain pour accepter une autre solution. */
        private const val ALTERNATIVE_TOLERANCE = 6.0
    }
}

@Composable
fun PuzzleTrainerScreen(
    viewModel: TrainerViewModel,
    onOpenGame: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val viz = LocalViz.current
    val puzzle = state.puzzle

    if (state.status == TrainerViewModel.Status.FINISHED || puzzle == null) {
        EmptyState(
            title = "Seance terminee",
            message = "${state.solvedCount} reussite(s), ${state.failedCount} echec(s). " +
                "Les puzzles rates reviendront dans quelques minutes, les autres dans quelques jours.",
            actionLabel = "Revenir a la liste",
            onAction = onDone,
            modifier = modifier,
        )
        return
    }

    // Le solveur est toujours en bas du plateau : on raisonne comme dans la partie.
    val flipped = puzzle.solverColor == Piece.BLACK
    val solving = state.status == TrainerViewModel.Status.SOLVING

    BoxWithConstraints(modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 640.dp

        val board: @Composable () -> Unit = {
            ChessBoard(
                position = state.position,
                palette = BoardPalette.byKey(settings.boardTheme),
                flipped = flipped,
                showCoordinates = settings.showCoordinates,
                interactive = solving,
                onMove = viewModel::onMove,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val panel: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row {
                            Text(
                                "Puzzle ${state.index + 1}/${state.queue.size}",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                "difficulte ${puzzle.difficulty}/5",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(puzzle.kind.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (state.status) {
                                TrainerViewModel.Status.CHECKING ->
                                    "Verification de votre coup avec le moteur..."
                                TrainerViewModel.Status.WRONG ->
                                    "Ce n'est pas le meilleur coup. Regardez la solution, puis rejouez la position."
                                TrainerViewModel.Status.SOLVED ->
                                    if (state.alternativeAccepted) {
                                        "Trouve ! Votre coup differe de la variante principale mais vaut autant."
                                    } else {
                                        "Trouve. C'est exactement le coup que le moteur recommande."
                                    }
                                else -> "${puzzle.kind.prompt} Les ${if (flipped) "noirs" else "blancs"} jouent."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (state.hints > 0 && solving) {
                            val hintSquare = puzzle.solutionUci.firstOrNull()?.take(2)
                            Text(
                                "Indice : la piece a jouer part de $hintSquare.",
                                style = MaterialTheme.typography.bodySmall,
                                color = viz.warning,
                            )
                        }
                        if (state.playedSoFar().isNotEmpty()) {
                            Text(
                                "Variante : ${state.playedSoFar().joinToString(" ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (solving) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::useHint) { Text("Indice") }
                        TextButton(onClick = viewModel::revealSolution) { Text("Voir la solution") }
                    }
                } else {
                    ResultPanel(state, puzzle, viewModel, onOpenGame)
                }
            }
        }

        if (twoPane) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) { board() }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) { panel() }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                board()
                panel()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultPanel(
    state: TrainerViewModel.State,
    puzzle: Puzzle,
    viewModel: TrainerViewModel,
    onOpenGame: (String) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Solution", style = MaterialTheme.typography.titleSmall)
            Text(
                puzzle.solutionSan.joinToString(" "),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            puzzle.playedSan?.let {
                Text(
                    "Dans la partie, vous avez joue $it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (puzzle.motifs.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (motif in puzzle.motifs) {
                        AssistChip(
                            onClick = {},
                            label = { Text(motif.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                Text(puzzle.motifs.first().advice, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::next) { Text("Puzzle suivant") }
                OutlinedButton(onClick = viewModel::retry) { Text("Rejouer") }
            }
            puzzle.gameId?.let { gameId ->
                TextButton(
                    onClick = { onOpenGame(gameId) },
                    contentPadding = PaddingValues(0.dp),
                ) { Text("Voir la partie d'origine") }
            }
        }
    }
}
