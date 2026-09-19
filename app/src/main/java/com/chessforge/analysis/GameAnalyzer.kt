package com.chessforge.analysis

import com.chessforge.chess.Fen
import com.chessforge.chess.Move
import com.chessforge.chess.Piece
import com.chessforge.chess.San
import com.chessforge.chess.pgn.PgnParser
import com.chessforge.data.model.Classification
import com.chessforge.data.model.GameAnalysisSummary
import com.chessforge.data.model.GameRecord
import com.chessforge.data.model.Motif
import com.chessforge.data.model.MoveRecord
import com.chessforge.data.model.Phase
import com.chessforge.engine.ChessEngine
import com.chessforge.engine.EngineLimits
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class AnalyzedGame(
    val summary: GameAnalysisSummary,
    val moves: List<MoveRecord>,
)

data class AnalysisProgress(val gameId: String, val done: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/**
 * Analyse complete d'une partie : une passe du moteur sur chaque position, puis
 * classification de chaque coup et etiquetage des motifs.
 *
 * Astuce de cout : l'evaluation de la position *apres* un coup est celle de la position
 * *avant* le coup suivant. Une seule passe suffit donc pour obtenir les deux bornes
 * de chaque coup, au lieu de deux appels moteur par coup.
 */
class GameAnalyzer(private val engine: ChessEngine) {

    suspend fun analyze(
        game: GameRecord,
        limits: EngineLimits,
        onProgress: (AnalysisProgress) -> Unit = {},
    ): AnalyzedGame? {
        val pgn = PgnParser.parse(game.pgn) ?: return null
        val played = PgnParser.replay(pgn)
        if (played.isEmpty()) return null

        val positions = played.map { it.fenBefore } + played.last().fenAfter
        val total = positions.size
        val evals = arrayOfNulls<Eval>(total)
        val bestMoves = arrayOfNulls<String>(total)
        val bestPvs = arrayOfNulls<List<String>>(total)

        for ((index, fen) in positions.withIndex()) {
            currentCoroutineContext().ensureActive()
            val position = Fen.parse(fen)
            // Position terminale : pas de coup a chercher, l'evaluation suffit.
            val isTerminal = index == total - 1 && !position.hasLegalMove()
            val movetime = openingAwareMovetime(limits.movetimeMs, index)
            val line = engine.analyze(fen, limits.copy(movetimeMs = movetime, multiPv = 1)).firstOrNull()
            evals[index] = when {
                line == null -> Eval(0)
                else -> Eval.from(line.score, position.side)
            }
            if (!isTerminal) {
                bestMoves[index] = line?.bestMove
                bestPvs[index] = line?.pv
            }
            onProgress(AnalysisProgress(game.id, index + 1, total))
        }

        val records = ArrayList<MoveRecord>(played.size)
        val sanHistory = ArrayList<String>(played.size)
        val userAccuracies = ArrayList<Double>()
        val userLosses = ArrayList<Int>()
        val opponentAccuracies = ArrayList<Double>()
        val opponentLosses = ArrayList<Int>()
        val phaseLosses = mutableMapOf(
            Phase.OPENING to ArrayList<Int>(),
            Phase.MIDDLEGAME to ArrayList<Int>(),
            Phase.ENDGAME to ArrayList<Int>(),
        )
        var blunders = 0
        var mistakes = 0
        var inaccuracies = 0
        var misses = 0
        var best = 0

        for ((index, pm) in played.withIndex()) {
            currentCoroutineContext().ensureActive()
            val position = Fen.parse(pm.fenBefore)
            val evalBefore = evals[index] ?: Eval(0)
            val evalAfter = evals[index + 1] ?: Eval(0)
            val bestUci = bestMoves[index]
            val byUser = pm.sideToMove == game.userColor

            sanHistory.add(pm.san)
            val isBook = Openings.isBookMove(sanHistory)
            val legalCount = position.legalMoves().size
            val playedIsBest = bestUci != null && bestUci == pm.uci

            val phase = Phases.of(position, pm.ply)
            val verdict = MoveClassifier.classify(
                positionBefore = position,
                move = pm.move,
                evalBefore = evalBefore,
                evalAfter = evalAfter,
                playedIsBest = playedIsBest,
                legalMoveCount = legalCount,
                isBookMove = isBook,
            )

            val bestMove = bestUci?.let { San.fromUci(position, it) } ?: Move.NONE
            val bestSan = if (bestMove != Move.NONE) San.of(position, bestMove) else null
            val bestPvSan = bestPvs[index]
                ?.takeIf { it.isNotEmpty() }
                ?.let { San.lineToSan(position, it.take(6)) }
                ?.joinToString(" ")

            // Les motifs ne sont calcules que la ou ils servent : erreurs et coups forts.
            val motifs: List<Motif> = when {
                verdict.classification.isError && bestMove != Move.NONE ->
                    MotifDetector.ofMistake(position, pm.move, bestMove, phase, evalAfter)
                verdict.classification == Classification.BRILLIANT ||
                    verdict.classification == Classification.GREAT ->
                    MotifDetector.ofSolution(position, pm.move, phase, evalAfter)
                else -> emptyList()
            }

            records.add(
                MoveRecord(
                    gameId = game.id,
                    ply = pm.ply,
                    san = pm.san,
                    uci = pm.uci,
                    fenBefore = pm.fenBefore,
                    sideToMove = pm.sideToMove,
                    byUser = byUser,
                    evalBefore = evalBefore.cp,
                    evalAfter = evalAfter.cp,
                    mateBefore = evalBefore.mate,
                    mateAfter = evalAfter.mate,
                    cpLoss = verdict.cpLoss,
                    winDrop = verdict.winDrop,
                    classification = verdict.classification,
                    bestUci = bestUci,
                    bestSan = bestSan,
                    bestPvSan = bestPvSan,
                    phase = phase,
                    motifs = motifs,
                    clockSeconds = pm.clockSeconds,
                    secondsSpent = pm.secondsSpent,
                )
            )

            if (byUser) {
                userAccuracies.add(verdict.accuracy)
                userLosses.add(verdict.cpLoss)
                phaseLosses.getValue(phase).add(verdict.cpLoss)
                when (verdict.classification) {
                    Classification.BLUNDER -> blunders++
                    Classification.MISTAKE -> mistakes++
                    Classification.INACCURACY -> inaccuracies++
                    Classification.MISS -> misses++
                    Classification.BEST, Classification.BRILLIANT, Classification.GREAT -> best++
                    else -> {}
                }
            } else {
                opponentAccuracies.add(verdict.accuracy)
                opponentLosses.add(verdict.cpLoss)
            }
        }

        val summary = GameAnalysisSummary(
            analyzedAt = System.currentTimeMillis() / 1000,
            engineId = engine.id,
            depth = limits.depth,
            accuracy = MoveClassifier.gameAccuracy(userAccuracies),
            acpl = MoveClassifier.averageCentipawnLoss(userLosses),
            opponentAccuracy = MoveClassifier.gameAccuracy(opponentAccuracies),
            opponentAcpl = MoveClassifier.averageCentipawnLoss(opponentLosses),
            blunders = blunders,
            mistakes = mistakes,
            inaccuracies = inaccuracies,
            misses = misses,
            bestMoves = best,
            openingAcpl = MoveClassifier.averageCentipawnLoss(phaseLosses.getValue(Phase.OPENING)),
            middlegameAcpl = MoveClassifier.averageCentipawnLoss(phaseLosses.getValue(Phase.MIDDLEGAME)),
            endgameAcpl = MoveClassifier.averageCentipawnLoss(phaseLosses.getValue(Phase.ENDGAME)),
        )
        return AnalyzedGame(summary, records)
    }

    /** Les premiers coups sont connus : on y consacre moins de temps moteur. */
    private fun openingAwareMovetime(base: Long, ply: Int): Long =
        if (ply < 8) (base / 2).coerceAtLeast(120L) else base

    companion object {
        /** Reglages proposes dans l'interface, du plus rapide au plus precis. */
        val PRESETS = listOf(
            AnalysisPreset("Rapide", "environ 15 s par partie", EngineLimits(depth = 10, movetimeMs = 180)),
            AnalysisPreset("Equilibre", "environ 40 s par partie", EngineLimits(depth = 14, movetimeMs = 450)),
            AnalysisPreset("Approfondi", "2 min et plus par partie", EngineLimits(depth = 18, movetimeMs = 1_200)),
        )
    }
}

data class AnalysisPreset(val label: String, val hint: String, val limits: EngineLimits)

/** Camp adverse, pour la lisibilite des appels. */
fun opposite(color: Int): Int = if (color == Piece.WHITE) Piece.BLACK else Piece.WHITE
