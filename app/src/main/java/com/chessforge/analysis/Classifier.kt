package com.chessforge.analysis

import com.chessforge.chess.Move
import com.chessforge.chess.Piece
import com.chessforge.chess.Position
import com.chessforge.data.model.Classification
import com.chessforge.data.model.Phase
import com.chessforge.engine.EngineScore
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/** Evaluation d'une position, toujours stockee du point de vue des blancs. */
data class Eval(val cp: Int, val mate: Int? = null) {

    fun forColor(color: Int): Eval =
        if (color == Piece.WHITE) this else Eval(-cp, mate?.let { -it })

    /** Probabilite de gain (0-100) pour [color], selon la courbe logistique de Lichess. */
    fun winPercentFor(color: Int): Double {
        val v = forColor(color)
        v.mate?.let { return if (it > 0) 100.0 else 0.0 }
        return WinPercent.of(v.cp)
    }

    fun cpFor(color: Int): Int = forColor(color).let { it.mate?.let { m -> if (m > 0) MATE_CP else -MATE_CP } ?: it.cp }

    fun format(): String = EngineScore(cp = cp, mate = mate).format()

    companion object {
        const val MATE_CP = 2000

        fun from(score: EngineScore, sideToMove: Int): Eval {
            val fromMover = Eval(score.cp ?: 0, score.mate)
            return if (sideToMove == Piece.WHITE) fromMover else Eval(-fromMover.cp, fromMover.mate?.let { -it })
        }
    }
}

object WinPercent {
    /** Courbe reliant centipions et probabilite de gain (constante issue des stats Lichess). */
    fun of(cp: Int): Double {
        val clamped = cp.coerceIn(-2000, 2000)
        return 50.0 + 50.0 * (2.0 / (1.0 + exp(-0.00368208 * clamped)) - 1.0)
    }

    /**
     * Precision d'un coup (0-100) a partir de la perte de probabilite de gain.
     * Meme formule que Lichess, pour que les chiffres soient comparables a ceux
     * que vous voyez ailleurs.
     */
    fun accuracyOf(winBefore: Double, winAfter: Double): Double {
        val drop = max(0.0, winBefore - winAfter)
        return (103.1668 * exp(-0.04354 * drop) - 3.1669).coerceIn(0.0, 100.0)
    }
}

object Phases {
    /** Phase deduite du materiel restant, avec un garde-fou sur le numero de coup. */
    fun of(position: Position, ply: Int): Phase {
        val material = position.materialCount(Piece.WHITE) + position.materialCount(Piece.BLACK)
        val pieces = position.nonPawnPieceCount()
        return when {
            material <= 2600 || pieces <= 4 -> Phase.ENDGAME
            ply < 16 && pieces >= 10 -> Phase.OPENING
            else -> Phase.MIDDLEGAME
        }
    }
}

/** Resultat de la classification d'un coup joue. */
data class MoveVerdict(
    val classification: Classification,
    val cpLoss: Int,
    val winDrop: Double,
    val accuracy: Double,
)

object MoveClassifier {

    private const val BLUNDER_DROP = 20.0
    private const val MISTAKE_DROP = 10.0
    private const val INACCURACY_DROP = 5.0
    private const val EXCELLENT_DROP = 2.0
    private const val WINNING_WIN_PCT = 85.0

    /**
     * Classe le coup joue en comparant la position avant et apres.
     *
     * @param evalBefore evaluation de la position avant le coup (meilleure suite du moteur)
     * @param evalAfter evaluation de la position obtenue apres le coup joue
     * @param secondBestDrop perte qu'aurait entrainee la deuxieme meilleure option,
     *        si elle est connue : permet de distinguer un coup simplement correct
     *        d'un coup *indispensable*.
     */
    fun classify(
        positionBefore: Position,
        move: Int,
        evalBefore: Eval,
        evalAfter: Eval,
        playedIsBest: Boolean,
        legalMoveCount: Int,
        isBookMove: Boolean,
        secondBestDrop: Double? = null,
    ): MoveVerdict {
        val mover = positionBefore.side
        val winBefore = evalBefore.winPercentFor(mover)
        val winAfter = evalAfter.winPercentFor(mover)
        val drop = max(0.0, winBefore - winAfter)
        val cpLoss = max(0, evalBefore.cpFor(mover) - evalAfter.cpFor(mover))
        val accuracy = WinPercent.accuracyOf(winBefore, winAfter)

        val classification = when {
            legalMoveCount <= 1 -> Classification.FORCED
            isBookMove && drop < MISTAKE_DROP -> Classification.BOOK

            // Un mat force abandonne, ou une position gagnante laissee filer.
            missedForcedWin(evalBefore, evalAfter, mover, drop, winBefore) -> Classification.MISS

            drop >= BLUNDER_DROP -> Classification.BLUNDER
            drop >= MISTAKE_DROP -> Classification.MISTAKE
            drop >= INACCURACY_DROP -> Classification.INACCURACY

            playedIsBest && isSoundSacrifice(positionBefore, move, winAfter) -> Classification.BRILLIANT
            playedIsBest && secondBestDrop != null && secondBestDrop >= MISTAKE_DROP -> Classification.GREAT
            playedIsBest -> Classification.BEST
            drop < EXCELLENT_DROP -> Classification.EXCELLENT
            else -> Classification.GOOD
        }

        return MoveVerdict(classification, cpLoss, drop, accuracy)
    }

    private fun missedForcedWin(
        before: Eval,
        after: Eval,
        mover: Int,
        drop: Double,
        winBefore: Double,
    ): Boolean {
        val mateBefore = before.forColor(mover).mate
        val mateAfter = after.forColor(mover).mate
        val lostForcedMate = mateBefore != null && mateBefore > 0 && (mateAfter == null || mateAfter < 0)
        if (lostForcedMate && drop >= INACCURACY_DROP) return true
        return winBefore >= WINNING_WIN_PCT && drop >= MISTAKE_DROP
    }

    /**
     * Sacrifice sain : le coup perd du materiel a l'echange statique mais reste le
     * meilleur coup et conserve une position au moins egale.
     */
    private fun isSoundSacrifice(position: Position, move: Int, winAfter: Double): Boolean {
        if (winAfter < 45.0) return false
        val movedType = Piece.typeOf(position.board[Move.from(move)])
        if (movedType == Piece.PAWN) return false
        return Tactics.see(position, move) <= -150
    }

    /** Precision d'une partie : moyenne des precisions coup par coup. */
    fun gameAccuracy(accuracies: List<Double>): Double =
        if (accuracies.isEmpty()) 0.0 else accuracies.average()

    /** Perte moyenne en centipions, plafonnee pour qu'une position perdue n'ecrase pas tout. */
    fun averageCentipawnLoss(losses: List<Int>, cap: Int = 1000): Double =
        if (losses.isEmpty()) 0.0 else losses.map { abs(it).coerceAtMost(cap) }.average()
}
