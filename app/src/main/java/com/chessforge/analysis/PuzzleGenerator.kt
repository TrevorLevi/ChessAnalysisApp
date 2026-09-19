package com.chessforge.analysis

import com.chessforge.chess.Fen
import com.chessforge.chess.Move
import com.chessforge.chess.Piece
import com.chessforge.chess.San
import com.chessforge.data.model.Classification
import com.chessforge.data.model.GameRecord
import com.chessforge.data.model.Motif
import com.chessforge.data.model.MoveRecord
import com.chessforge.data.model.Puzzle
import com.chessforge.data.model.PuzzleKind
import com.chessforge.data.model.SrsState
import com.chessforge.engine.ChessEngine
import com.chessforge.engine.EngineLimits
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs

/**
 * Fabrique des puzzles a partir de *vos* parties.
 *
 * Un bon puzzle personnel respecte trois conditions :
 *  1. la position vient d'une de vos parties et d'un moment ou vous avez devie ;
 *  2. il existe un coup nettement meilleur que tous les autres (sinon la reponse est arbitraire) ;
 *  3. l'ecart d'evaluation est assez grand pour que la lecon soit reelle.
 *
 * La condition 2 est verifiee en demandant au moteur les deux meilleures variantes.
 */
class PuzzleGenerator(private val engine: ChessEngine) {

    suspend fun generate(
        game: GameRecord,
        moves: List<MoveRecord>,
        limits: EngineLimits = VALIDATION_LIMITS,
        maxPerGame: Int = 4,
    ): List<Puzzle> {
        val candidates = collectCandidates(game, moves)
        val out = ArrayList<Puzzle>(maxPerGame)
        for (candidate in candidates.sortedByDescending { it.priority }) {
            if (out.size >= maxPerGame) break
            currentCoroutineContext().ensureActive()
            val puzzle = validate(game, candidate, limits) ?: continue
            out.add(puzzle)
        }
        return out
    }

    /**
     * Cree un puzzle depuis une position precise, a la demande de l'utilisateur
     * (bouton "en faire un puzzle" dans la revue de partie). Renvoie null si la
     * position n'a pas de solution suffisamment unique.
     */
    suspend fun fromPosition(
        game: GameRecord,
        fen: String,
        ply: Int,
        playedSan: String?,
        kind: PuzzleKind,
        limits: EngineLimits = VALIDATION_LIMITS,
    ): Puzzle? {
        val position = Fen.parse(fen)
        return validate(
            game,
            Candidate(fen, ply, kind, playedSan, position.side, priority = 0.0),
            limits,
        )
    }

    private data class Candidate(
        val fen: String,
        val ply: Int,
        val kind: PuzzleKind,
        val playedSan: String?,
        val solverColor: Int,
        val priority: Double,
    )

    private fun collectCandidates(game: GameRecord, moves: List<MoveRecord>): List<Candidate> {
        val out = ArrayList<Candidate>()
        for ((index, move) in moves.withIndex()) {
            if (move.bestUci == null) continue

            if (move.byUser && move.classification.isError) {
                val kind = when {
                    move.classification == Classification.MISS -> PuzzleKind.MISSED_WIN
                    move.phase == com.chessforge.data.model.Phase.ENDGAME -> PuzzleKind.ENDGAME_DRILL
                    // Position deja difficile : l'exercice porte sur la meilleure defense.
                    move.evalBefore.let { if (move.sideToMove == Piece.WHITE) it else -it } < -150 ->
                        PuzzleKind.DEFENSE
                    else -> PuzzleKind.BLUNDER_FIX
                }
                out.add(
                    Candidate(
                        fen = move.fenBefore,
                        ply = move.ply,
                        kind = kind,
                        playedSan = move.san,
                        solverColor = move.sideToMove,
                        priority = move.winDrop + if (move.classification == Classification.BLUNDER) 10.0 else 0.0,
                    )
                )
            }

            // Erreur adverse que vous n'avez pas punie : la position juste apres est un puzzle.
            if (!move.byUser && move.classification.isError) {
                val next = moves.getOrNull(index + 1) ?: continue
                if (!next.byUser || next.bestUci == null) continue
                val punished = next.classification in PUNISHED
                if (punished) continue
                out.add(
                    Candidate(
                        fen = next.fenBefore,
                        ply = next.ply,
                        kind = PuzzleKind.PUNISH,
                        playedSan = next.san,
                        solverColor = next.sideToMove,
                        priority = move.winDrop * 0.8 + next.winDrop,
                    )
                )
            }
        }
        return out.distinctBy { it.fen }
    }

    /** Verifie l'unicite de la solution et construit le puzzle, ou renvoie null. */
    private suspend fun validate(game: GameRecord, candidate: Candidate, limits: EngineLimits): Puzzle? {
        val position = Fen.parse(candidate.fen)
        if (position.side != candidate.solverColor) return null
        val legal = position.legalMoves()
        if (legal.size < 2) return null

        val lines = engine.analyze(candidate.fen, limits.copy(multiPv = 2))
        val best = lines.firstOrNull() ?: return null
        val bestUci = best.bestMove ?: return null
        val bestMove = San.fromUci(position, bestUci)
        if (bestMove == Move.NONE) return null

        val bestWin = Eval.from(best.score, position.side).winPercentFor(candidate.solverColor)
        val second = lines.getOrNull(1)
        val secondWin = second
            ?.let { Eval.from(it.score, position.side).winPercentFor(candidate.solverColor) }
            ?: 0.0
        val gap = bestWin - secondWin

        val bestMate = best.score.mate
        val secondMate = second?.score?.mate
        val uniqueByMate = bestMate != null && bestMate > 0 && (secondMate == null || secondMate <= 0)

        if (!uniqueByMate && gap < MIN_UNIQUENESS_GAP) return null
        // Une position deja perdue sans ressource n'apprend rien.
        if (bestWin < 8.0 && candidate.kind != PuzzleKind.DEFENSE) return null

        val afterBest = position.copy()
        if (!afterBest.makeMove(bestMove)) return null
        val evalAfter = Eval.from(best.score, position.side)
        val phase = Phases.of(position, candidate.ply)
        val motifs = MotifDetector.ofSolution(position, bestMove, phase, evalAfter)

        val solutionUci = best.pv.take(MAX_SOLUTION_PLIES)
        val solutionSan = San.lineToSan(position, solutionUci)
        if (solutionSan.isEmpty()) return null

        val swing = abs(bestWin - secondWin).toInt() * 10
        val now = System.currentTimeMillis() / 1000
        return Puzzle(
            id = 0L,
            gameId = game.id,
            ply = candidate.ply,
            fen = candidate.fen,
            solutionUci = solutionUci,
            solutionSan = solutionSan,
            kind = candidate.kind,
            motifs = motifs,
            phase = phase,
            difficulty = difficultyOf(position, bestMove, gap, motifs, uniqueByMate),
            swingCp = swing,
            playedSan = candidate.playedSan,
            userColor = candidate.solverColor,
            opponentName = game.opponent,
            playedAt = game.playedAt,
            createdAt = now,
            srs = SrsState.fresh(now),
        )
    }

    /**
     * Difficulte de 1 a 5. Les coups calmes et les solutions longues sont plus dures ;
     * une prise evidente avec un ecart enorme est facile.
     */
    private fun difficultyOf(
        position: com.chessforge.chess.Position,
        bestMove: Int,
        gap: Double,
        motifs: List<Motif>,
        forcedMate: Boolean,
    ): Int {
        var score = 3.0
        val isCapture = position.board[Move.to(bestMove)] != Piece.NONE
        val after = position.copy().also { it.makeMove(bestMove) }
        val givesCheck = after.isInCheck()

        if (isCapture) score -= 0.6
        if (givesCheck) score -= 0.4
        if (Motif.QUIET in motifs) score += 1.2
        if (Motif.SACRIFICE in motifs) score += 0.8
        if (Motif.HANGING in motifs) score -= 0.5
        if (forcedMate) score -= 0.3
        if (gap > 60) score -= 0.5
        if (gap < 20) score += 0.7
        if (position.nonPawnPieceCount() >= 10) score += 0.3
        return score.coerceIn(1.0, 5.0).toInt().coerceIn(1, 5)
    }

    companion object {
        private val PUNISHED = setOf(
            Classification.BEST, Classification.BRILLIANT, Classification.GREAT, Classification.EXCELLENT,
        )

        /** Ecart minimal de probabilite de gain entre le meilleur coup et le deuxieme. */
        private const val MIN_UNIQUENESS_GAP = 14.0
        private const val MAX_SOLUTION_PLIES = 5

        /** Validation plus profonde que l'analyse de masse : un puzzle faux est pire que pas de puzzle. */
        val VALIDATION_LIMITS = EngineLimits(depth = 16, movetimeMs = 900, multiPv = 2)
    }
}
