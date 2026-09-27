package com.chessforge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chessforge.chess.Fen
import com.chessforge.chess.Move
import com.chessforge.chess.Piece
import com.chessforge.chess.Position
import com.chessforge.chess.San
import com.chessforge.di.AppContainer
import com.chessforge.engine.EngineLimits
import com.chessforge.training.DrillGoal
import com.chessforge.training.Drills
import com.chessforge.training.EndgameDrill
import com.chessforge.training.OpeningLine
import com.chessforge.training.OpeningLines
import com.chessforge.training.TacticDrill
import com.chessforge.ui.components.BoardBadge
import com.chessforge.ui.components.ChessBoard
import com.chessforge.ui.theme.BoardPalette
import com.chessforge.ui.theme.LocalViz
import com.chessforge.ui.theme.PieceStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Accueil de la section
// ---------------------------------------------------------------------------

private data class TrainMode(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onOpen: () -> Unit,
)

@Composable
fun TrainHubScreen(
    onOpenPuzzles: () -> Unit,
    onOpenEndgames: () -> Unit,
    onOpenTactics: () -> Unit,
    onOpenRepertoire: () -> Unit,
    duePuzzles: Int,
    modifier: Modifier = Modifier,
) {
    val modes = listOf(
        TrainMode(
            "Mes erreurs",
            if (duePuzzles > 0) "$duePuzzles position(s) a revoir, tirees de vos parties"
            else "Les positions ou vous avez devie, en revision espacee",
            Icons.Filled.Extension,
            onOpenPuzzles,
        ),
        TrainMode(
            "Finales",
            "${Drills.endgames.size} techniques a jouer contre le moteur, du mat elementaire " +
                "au pont de Lucena",
            Icons.Filled.Flag,
            onOpenEndgames,
        ),
        TrainMode(
            "Tactique",
            "${Drills.tactics.size} motifs classiques : couloir, fourchette, piece en l'air",
            Icons.Filled.Bolt,
            onOpenTactics,
        ),
        TrainMode(
            "Repertoire",
            "${OpeningLines.all.size} ouvertures a rejouer de memoire, avec leur idee directrice",
            Icons.Filled.MenuBook,
            onOpenRepertoire,
        ),
    )

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Vos erreurs d'abord : ce sont elles qui vous coutent des points. Les trois " +
                "autres modes ne dependent pas de vos parties et restent disponibles quand " +
                "la file de revision est vide.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (mode in modes) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = mode.onOpen),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(mode.icon, null, Modifier.size(28.dp), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mode.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            mode.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------
// Finales : se jouent contre le moteur
// ---------------------------------------------------------------------------

@Composable
fun EndgameListScreen(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Une technique ne s'apprend pas de memoire : jouez-la contre le moteur, " +
                    "qui defendra du mieux qu'il peut.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(Drills.endgames, key = { it.id }) { drill ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(drill.id) },
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(drill.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text(
                            "difficulte ${drill.difficulty}/5",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        drill.goal.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        drill.lesson,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

class EndgameDrillViewModel(
    private val container: AppContainer,
    private val drillId: String,
) : ViewModel() {

    enum class Outcome { PLAYING, SUCCESS, FAILED, UNFINISHED }

    data class State(
        val drill: EndgameDrill? = null,
        val position: Position = Position.startPosition(),
        val solverColor: Int = Piece.WHITE,
        val history: List<String> = emptyList(),
        val lastMove: Int? = null,
        val thinking: Boolean = false,
        val outcome: Outcome = Outcome.PLAYING,
        val verdict: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    val settings = container.settings.state

    private var solverPromoted = false
    private var opponentPromoted = false

    init {
        restart()
    }

    fun restart() {
        val drill = Drills.endgame(drillId) ?: return
        solverPromoted = false
        opponentPromoted = false
        _state.value = State(
            drill = drill,
            position = Fen.parse(drill.fen),
            solverColor = Fen.parse(drill.fen).side,
        )
    }

    fun onMove(from: Int, to: Int, promo: Int) {
        val state = _state.value
        val drill = state.drill ?: return
        if (state.outcome != Outcome.PLAYING || state.thinking) return

        val position = state.position.copy()
        val move = position.legalMoves().firstOrNull {
            Move.from(it) == from && Move.to(it) == to && Move.promo(it) == promo
        } ?: return
        val san = San.of(position, move)
        if (promo != 0) solverPromoted = true
        position.makeMove(move)

        val afterUser = state.copy(
            position = position,
            history = state.history + san,
            lastMove = move,
        )
        val verdict = judge(position, drill, state.solverColor)
        if (verdict != Outcome.PLAYING) {
            _state.value = afterUser.copy(outcome = verdict, verdict = describe(verdict, drill))
            return
        }
        _state.value = afterUser.copy(thinking = true)
        viewModelScope.launch { engineReply(drill) }
    }

    private suspend fun engineReply(drill: EndgameDrill) {
        val current = _state.value
        val position = current.position.copy()
        val engine = container.engines.engine()
        val line = runCatching {
            engine.analyze(Fen.of(position), EngineLimits(depth = 16, movetimeMs = 900)).firstOrNull()
        }.getOrNull()
        val reply = line?.bestMove?.let { San.fromUci(position, it) }
        if (reply == null || reply == Move.NONE) {
            _state.value = current.copy(thinking = false)
            return
        }
        val san = San.of(position, reply)
        if (Move.promo(reply) != 0) opponentPromoted = true
        position.makeMove(reply)

        val verdict = judge(position, drill, current.solverColor)
        _state.value = current.copy(
            position = position,
            history = current.history + san,
            lastMove = reply,
            thinking = false,
            outcome = verdict,
            verdict = if (verdict == Outcome.PLAYING) null else describe(verdict, drill),
        )
    }

    /**
     * Traduit l'etat de la partie en reussite ou en echec selon l'objectif annonce.
     * La limite de coups evite qu'un exercice tourne indefiniment sans conclusion.
     */
    private fun judge(position: Position, drill: EndgameDrill, solver: Int): Outcome {
        val moves = _state.value.history.size
        val over = !position.hasLegalMove()
        val mated = over && position.isInCheck()
        val stalemate = over && !position.isInCheck()
        val drawn = stalemate || position.isInsufficientMaterial() || position.halfmoveClock >= 100

        return when (drill.goal) {
            DrillGoal.MATE -> when {
                mated && position.side != solver -> Outcome.SUCCESS
                mated -> Outcome.FAILED
                drawn -> Outcome.FAILED
                moves > MAX_PLIES -> Outcome.UNFINISHED
                else -> Outcome.PLAYING
            }
            DrillGoal.PROMOTE -> when {
                mated && position.side != solver -> Outcome.SUCCESS
                solverPromoted -> Outcome.SUCCESS
                mated -> Outcome.FAILED
                drawn -> Outcome.FAILED
                moves > MAX_PLIES -> Outcome.UNFINISHED
                else -> Outcome.PLAYING
            }
            DrillGoal.DRAW -> when {
                drawn -> Outcome.SUCCESS
                mated && position.side == solver -> Outcome.FAILED
                opponentPromoted -> Outcome.FAILED
                // Tenir assez longtemps vaut reussite : la position est theoriquement nulle.
                moves > MAX_PLIES -> Outcome.SUCCESS
                else -> Outcome.PLAYING
            }
        }
    }

    private fun describe(outcome: Outcome, drill: EndgameDrill): String = when (outcome) {
        Outcome.SUCCESS -> when (drill.goal) {
            DrillGoal.MATE -> "Mat. La technique est passee."
            DrillGoal.PROMOTE -> "Pion promu : la finale est gagnee."
            DrillGoal.DRAW -> "Nulle tenue. C'etait tout l'enjeu."
        }
        Outcome.FAILED -> when (drill.goal) {
            DrillGoal.DRAW -> "La position est perdue. Reprenez : le roi doit rester dans le coin."
            else -> "Objectif manque. Relancez et appliquez le principe enonce."
        }
        Outcome.UNFINISHED -> "Trop de coups sans conclusion. Reprenez depuis le debut."
        Outcome.PLAYING -> ""
    }

    private companion object {
        const val MAX_PLIES = 80
    }
}

@Composable
fun EndgameDrillScreen(viewModel: EndgameDrillViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val viz = LocalViz.current
    val drill = state.drill ?: return
    val playing = state.outcome == EndgameDrillViewModel.Outcome.PLAYING && !state.thinking

    DrillLayout(
        modifier = modifier,
        board = {
            ChessBoard(
                position = state.position,
                palette = BoardPalette.byKey(settings.boardTheme),
                pieceStyle = PieceStyle.byKey(settings.pieceStyle),
                flipped = state.solverColor == Piece.BLACK,
                lastMove = state.lastMove,
                interactive = playing,
                onMove = viewModel::onMove,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        panel = {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(drill.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        drill.goal.instruction,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        drill.lesson,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.thinking) {
                        Text(
                            "Le moteur reflechit...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            state.verdict?.let { verdict ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = when (state.outcome) {
                        EndgameDrillViewModel.Outcome.SUCCESS -> viz.good.copy(alpha = 0.16f)
                        EndgameDrillViewModel.Outcome.FAILED -> viz.critical.copy(alpha = 0.16f)
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(verdict, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = viewModel::restart) { Text("Recommencer") }
                    }
                }
            }

            if (state.history.isNotEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Coups joues", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(state.history.joinToString(" "), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            OutlinedButton(onClick = viewModel::restart) { Text("Reprendre la position") }
        },
    )
}

// ---------------------------------------------------------------------------
// Tactique : trouver le coup
// ---------------------------------------------------------------------------

class TacticDrillViewModel(private val container: AppContainer) : ViewModel() {

    enum class Status { SOLVING, WRONG, SOLVED, FINISHED }

    data class State(
        val queue: List<TacticDrill> = emptyList(),
        val index: Int = 0,
        val position: Position = Position.startPosition(),
        val status: Status = Status.SOLVING,
        val markedSquare: Int? = null,
        val hintShown: Boolean = false,
        val solved: Int = 0,
        val failed: Int = 0,
    ) {
        val drill: TacticDrill? get() = queue.getOrNull(index)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    val settings = container.settings.state

    init {
        val queue = Drills.tactics.shuffled()
        _state.value = State(queue = queue, position = Fen.parse(queue.first().fen))
    }

    fun onMove(from: Int, to: Int, promo: Int) {
        val state = _state.value
        val drill = state.drill ?: return
        if (state.status != Status.SOLVING) return
        val position = state.position.copy()
        val move = position.legalMoves().firstOrNull {
            Move.from(it) == from && Move.to(it) == to && Move.promo(it) == promo
        } ?: return
        position.makeMove(move)
        val correct = Move.toUci(move) == drill.solutionUci
        _state.value = state.copy(
            position = position,
            markedSquare = to,
            status = if (correct) Status.SOLVED else Status.WRONG,
            solved = state.solved + if (correct) 1 else 0,
            failed = state.failed + if (correct) 0 else 1,
        )
    }

    fun retry() {
        val drill = _state.value.drill ?: return
        _state.value = _state.value.copy(
            position = Fen.parse(drill.fen),
            status = Status.SOLVING,
            markedSquare = null,
        )
    }

    fun showHint() {
        _state.value = _state.value.copy(hintShown = true)
    }

    fun next() {
        val state = _state.value
        val index = state.index + 1
        if (index >= state.queue.size) {
            _state.value = state.copy(status = Status.FINISHED)
            return
        }
        _state.value = state.copy(
            index = index,
            position = Fen.parse(state.queue[index].fen),
            status = Status.SOLVING,
            markedSquare = null,
            hintShown = false,
        )
    }
}

@Composable
fun TacticDrillScreen(
    viewModel: TacticDrillViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val drill = state.drill

    if (state.status == TacticDrillViewModel.Status.FINISHED || drill == null) {
        com.chessforge.ui.components.EmptyState(
            title = "Serie terminee",
            message = "${state.solved} reussite(s) sur ${state.queue.size}. " +
                "Les motifs rates meritent un second passage.",
            actionLabel = "Revenir",
            onAction = onDone,
            modifier = modifier,
        )
        return
    }

    val solverColor = Fen.parse(drill.fen).side
    val badges = state.markedSquare?.let { square ->
        when (state.status) {
            TacticDrillViewModel.Status.WRONG -> listOf(BoardBadge(square, BoardBadge.Kind.WRONG))
            TacticDrillViewModel.Status.SOLVED -> listOf(BoardBadge(square, BoardBadge.Kind.CORRECT))
            else -> null
        }
    } ?: emptyList()

    DrillLayout(
        modifier = modifier,
        board = {
            ChessBoard(
                position = state.position,
                palette = BoardPalette.byKey(settings.boardTheme),
                pieceStyle = PieceStyle.byKey(settings.pieceStyle),
                flipped = solverColor == Piece.BLACK,
                interactive = state.status == TacticDrillViewModel.Status.SOLVING,
                onMove = viewModel::onMove,
                badges = badges,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        panel = {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Text(
                            "Exercice ${state.index + 1}/${state.queue.size}",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            "difficulte ${drill.difficulty}/5",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(drill.motif.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (state.status) {
                            TacticDrillViewModel.Status.WRONG ->
                                "Ce n'est pas le coup. Reprenez la position."
                            TacticDrillViewModel.Status.SOLVED -> "Trouve."
                            else -> "Les ${if (solverColor == Piece.WHITE) "blancs" else "noirs"} jouent " +
                                "et gagnent du materiel ou matent."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (state.hintShown) {
                        Text(
                            drill.hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (state.status == TacticDrillViewModel.Status.SOLVED) {
                        Text(
                            drill.motif.advice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state.status) {
                    TacticDrillViewModel.Status.SOLVING ->
                        if (!state.hintShown) {
                            OutlinedButton(onClick = viewModel::showHint) { Text("Indice") }
                        }
                    TacticDrillViewModel.Status.WRONG -> {
                        Button(onClick = viewModel::retry) { Text("Reessayer") }
                        TextButton(onClick = viewModel::next) { Text("Passer") }
                    }
                    else -> Button(onClick = viewModel::next) { Text("Suivant") }
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Repertoire : rejouer une ouverture de memoire
// ---------------------------------------------------------------------------

@Composable
fun RepertoireListScreen(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Vous jouez les blancs, l'application repond pour les noirs. Le but n'est pas " +
                    "de reciter : c'est de reconnaitre la position et de retrouver le coup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(OpeningLines.all, key = { it.id }) { line ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(line.id) },
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(line.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${(line.plies + 1) / 2} coups",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

class RepertoireDrillViewModel(
    private val container: AppContainer,
    private val lineId: String,
) : ViewModel() {

    enum class Status { PLAYING, WRONG, DONE }

    data class State(
        val line: OpeningLine? = null,
        val position: Position = Position.startPosition(),
        val ply: Int = 0,
        val status: Status = Status.PLAYING,
        val lastMove: Int? = null,
        val expected: String? = null,
        val mistakes: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    val settings = container.settings.state

    init {
        restart()
    }

    fun restart() {
        val line = OpeningLines.byId(lineId) ?: return
        _state.value = State(line = line, position = Fen.parse(Fen.START))
    }

    fun onMove(from: Int, to: Int, promo: Int) {
        val state = _state.value
        val line = state.line ?: return
        if (state.status != Status.PLAYING) return
        val expectedSan = line.moves.getOrNull(state.ply) ?: return

        val position = state.position.copy()
        val move = position.legalMoves().firstOrNull {
            Move.from(it) == from && Move.to(it) == to && Move.promo(it) == promo
        } ?: return
        val expectedMove = San.parse(position, expectedSan)

        if (move != expectedMove) {
            _state.value = state.copy(
                status = Status.WRONG,
                expected = expectedSan,
                mistakes = state.mistakes + 1,
            )
            return
        }

        position.makeMove(move)
        var ply = state.ply + 1

        // Reponse automatique de l'adversaire, tiree de la ligne elle-meme.
        var reply: Int? = null
        line.moves.getOrNull(ply)?.let { replySan ->
            val parsed = San.parse(position, replySan)
            if (parsed != Move.NONE && position.makeMove(parsed)) {
                reply = parsed
                ply++
            }
        }

        _state.value = state.copy(
            position = position,
            ply = ply,
            lastMove = reply ?: move,
            status = if (ply >= line.moves.size) Status.DONE else Status.PLAYING,
            expected = null,
        )
    }

    /** Rejoue la position avant l'erreur pour reessayer le meme coup. */
    fun retry() {
        _state.value = _state.value.copy(status = Status.PLAYING, expected = null)
    }

    /** Joue le coup attendu a la place de l'utilisateur, quand il seche. */
    fun reveal() {
        val state = _state.value
        val line = state.line ?: return
        val san = line.moves.getOrNull(state.ply) ?: return
        val move = San.parse(state.position, san)
        if (move == Move.NONE) return
        onMove(Move.from(move), Move.to(move), Move.promo(move))
    }
}

@Composable
fun RepertoireDrillScreen(viewModel: RepertoireDrillViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val viz = LocalViz.current
    val line = state.line ?: return
    val done = state.status == RepertoireDrillViewModel.Status.DONE

    DrillLayout(
        modifier = modifier,
        board = {
            ChessBoard(
                position = state.position,
                palette = BoardPalette.byKey(settings.boardTheme),
                pieceStyle = PieceStyle.byKey(settings.pieceStyle),
                lastMove = state.lastMove,
                interactive = state.status == RepertoireDrillViewModel.Status.PLAYING,
                onMove = viewModel::onMove,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        panel = {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (done) viz.good.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(line.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            done -> "Ligne complete. ${state.mistakes} erreur(s) en chemin."
                            state.status == RepertoireDrillViewModel.Status.WRONG ->
                                "Ce n'est pas le coup de la ligne. Le coup attendu etait " +
                                    "${state.expected}."
                            else -> "Coup ${state.ply / 2 + 1} : a vous de jouer."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (done) {
                        Text(
                            line.idea,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        line.moves.take(state.ply).chunkedSan(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state.status) {
                    RepertoireDrillViewModel.Status.WRONG -> {
                        Button(onClick = viewModel::retry) { Text("Reessayer") }
                        TextButton(onClick = viewModel::reveal) { Text("Montrer le coup") }
                    }
                    RepertoireDrillViewModel.Status.DONE ->
                        Button(onClick = viewModel::restart) { Text("Rejouer la ligne") }
                    else -> {
                        OutlinedButton(onClick = viewModel::reveal) { Text("Je seche") }
                        TextButton(onClick = viewModel::restart) { Text("Recommencer") }
                    }
                }
            }
        },
    )
}

/** Met les coups en forme "1. e4 e5 2. Nf3 Nc6" plutot qu'une suite brute. */
private fun List<String>.chunkedSan(): String = buildString {
    this@chunkedSan.forEachIndexed { index, san ->
        if (index % 2 == 0) {
            if (index > 0) append(' ')
            append("${index / 2 + 1}. ")
        } else {
            append(' ')
        }
        append(san)
    }
}

// ---------------------------------------------------------------------------
// Mise en page commune : plateau et panneau, cote a cote sur ecran large
// ---------------------------------------------------------------------------

@Composable
private fun DrillLayout(
    board: @Composable () -> Unit,
    panel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 640.dp
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
