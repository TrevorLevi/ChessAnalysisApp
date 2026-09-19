package com.chessforge.analysis

import com.chessforge.chess.Move
import com.chessforge.chess.MoveList
import com.chessforge.chess.Piece
import com.chessforge.chess.Position
import com.chessforge.chess.Square
import com.chessforge.chess.Tables
import com.chessforge.data.model.Motif
import com.chessforge.data.model.Phase

/**
 * Detection heuristique des motifs tactiques.
 *
 * Aucune de ces regles n'est parfaite ; elles sont volontairement conservatrices,
 * car un motif faux egare l'entrainement plus qu'une absence de motif.
 */
object MotifDetector {

    /** Motifs portes par un coup fort : ce que le puzzle demandera de trouver. */
    fun ofSolution(position: Position, move: Int, phase: Phase, evalAfter: Eval): List<Motif> {
        val motifs = LinkedHashSet<Motif>()
        val mover = position.side
        val from = Move.from(move)
        val to = Move.to(move)
        val movedType = Piece.typeOf(position.board[from])
        val capturedPiece = position.board[to]
        val isCapture = capturedPiece != Piece.NONE || Move.flag(move) == Move.FLAG_EN_PASSANT

        val after = position.copy()
        if (!after.makeMove(move)) return emptyList()
        val givesCheck = after.isInCheck()

        // Mat force
        val mate = evalAfter.forColor(mover).mate
        if (mate != null && mate > 0) {
            motifs.add(Motif.MATE)
            if (Tactics.isBackRankWeak(after, 1 - mover)) motifs.add(Motif.BACK_RANK)
        }

        // Fourchette / double attaque depuis la case d'arrivee
        val targets = Tactics.valuableTargets(after, to)
        if (targets.size >= 2) {
            motifs.add(if (movedType == Piece.KNIGHT || movedType == Piece.PAWN) Motif.FORK else Motif.DOUBLE_ATTACK)
        }

        // Clouage / enfilade cree par le coup
        Tactics.lineTactics(after, to)?.let { motifs.add(if (it.pin) Motif.PIN else Motif.SKEWER) }

        // Attaque a la decouverte : une piece amie prend le relais sur la ligne liberee
        if (isDiscovered(after, from, mover)) motifs.add(Motif.DISCOVERED)

        // Prise d'une piece non defendue
        if (isCapture && Tactics.isHanging(position, to)) motifs.add(Motif.HANGING)

        // Sacrifice
        if (Tactics.see(position, move) <= -150) motifs.add(Motif.SACRIFICE)

        // Promotion et pions passes
        if (Move.promo(move) != 0) motifs.add(Motif.PROMOTION)
        if (movedType == Piece.PAWN) {
            val relRank = if (mover == Piece.WHITE) Square.rank(to) else 7 - Square.rank(to)
            if (relRank >= 5) motifs.add(Motif.PASSED_PAWN)
        }

        // Piece adverse piegee apres le coup
        for (sq in 0..63) {
            val p = after.board[sq]
            if (p != Piece.NONE && Piece.colorOf(p) != mover && Tactics.isTrapped(after, sq)) {
                motifs.add(Motif.TRAPPED)
                break
            }
        }

        // Defenseur surcharge : on prend / attaque une piece qui gardait autre chose
        if (isCapture && wasOverloadedDefender(position, to, mover)) motifs.add(Motif.OVERLOAD)

        // Un coup calme est souvent le plus dur a trouver : il merite son etiquette.
        if (!isCapture && !givesCheck && Move.promo(move) == 0 && motifs.isEmpty()) motifs.add(Motif.QUIET)

        when (phase) {
            Phase.ENDGAME -> motifs.add(Motif.ENDGAME)
            Phase.OPENING -> if (motifs.isEmpty()) motifs.add(Motif.DEVELOPMENT)
            Phase.MIDDLEGAME -> {}
        }

        return motifs.toList()
    }

    /**
     * Motifs expliquant une erreur : ce que l'utilisateur a laisse passer.
     * [playedMove] est le coup reellement joue, [bestMove] celui du moteur.
     */
    fun ofMistake(
        position: Position,
        playedMove: Int,
        bestMove: Int,
        phase: Phase,
        evalAfterPlayed: Eval,
    ): List<Motif> {
        val motifs = LinkedHashSet<Motif>()
        val mover = position.side
        val after = position.copy()
        if (!after.makeMove(playedMove)) return emptyList()

        // 1. Une prise mal comptee
        if (position.board[Move.to(playedMove)] != Piece.NONE &&
            Tactics.see(position, playedMove) <= -100
        ) motifs.add(Motif.COUNTING)

        // 2. Une piece laissee en l'air apres le coup (y compris celle qui vient de bouger)
        val hanging = (0..63).firstOrNull { sq ->
            val p = after.board[sq]
            p != Piece.NONE && Piece.colorOf(p) == mover &&
                Piece.typeOf(p) != Piece.KING && Tactics.isHanging(after, sq)
        }
        if (hanging != null) motifs.add(Motif.HANGING)

        // 3. La riposte adverse la plus forte revele le motif subi
        val reply = strongestReply(after)
        if (reply != Move.NONE) {
            val replyFrom = Move.from(reply)
            val afterReply = after.copy()
            if (afterReply.makeMove(reply)) {
                if (Tactics.valuableTargets(afterReply, Move.to(reply)).size >= 2) {
                    motifs.add(
                        if (Piece.typeOf(after.board[replyFrom]) == Piece.KNIGHT) Motif.FORK
                        else Motif.DOUBLE_ATTACK
                    )
                }
                Tactics.lineTactics(afterReply, Move.to(reply))?.let {
                    motifs.add(if (it.pin) Motif.PIN else Motif.SKEWER)
                }
                if (afterReply.isInCheck() && !afterReply.hasLegalMove()) {
                    motifs.add(Motif.MATE)
                    if (Tactics.isBackRankWeak(after, mover)) motifs.add(Motif.BACK_RANK)
                }
            }
        }

        // 4. Mat annonce contre nous
        val mate = evalAfterPlayed.forColor(mover).mate
        if (mate != null && mate < 0) {
            motifs.add(Motif.MATE)
            if (Tactics.isBackRankWeak(after, mover)) motifs.add(Motif.BACK_RANK)
            motifs.add(Motif.KING_SAFETY)
        }

        // 5. Ce que le bon coup aurait realise reste la lecon principale
        motifs.addAll(ofSolution(position, bestMove, phase, Eval(0)).filter { it != Motif.QUIET })

        if (phase == Phase.OPENING && motifs.isEmpty()) motifs.add(Motif.DEVELOPMENT)
        if (phase == Phase.ENDGAME) motifs.add(Motif.ENDGAME)
        return motifs.toList()
    }

    /** La ligne liberee par le depart de [vacated] donne-t-elle une attaque a une piece amie ? */
    private fun isDiscovered(after: Position, vacated: Int, mover: Int): Boolean {
        val foeKing = after.kingSquare[1 - mover]
        if (foeKing == Square.NONE) return false
        for (sq in 0..63) {
            val p = after.board[sq]
            if (p == Piece.NONE || Piece.colorOf(p) != mover) continue
            when (Piece.typeOf(p)) {
                Piece.BISHOP, Piece.ROOK, Piece.QUEEN -> {
                    val dir = Tables.direction[sq][foeKing]
                    if (dir < 0) continue
                    // La case liberee doit se trouver sur la ligne entre la piece et le roi.
                    if (Tables.direction[sq][vacated] != dir) continue
                    if (Tables.distance[sq][vacated] >= Tables.distance[sq][foeKing]) continue
                    if (Tactics.attacksFrom(after, sq).contains(foeKing)) return true
                    Tactics.lineTactics(after, sq)?.let { if (it.backSquare == foeKing) return true }
                }
                else -> {}
            }
        }
        return false
    }

    /** La piece prise en [square] defendait-elle une autre piece elle-meme attaquee ? */
    private fun wasOverloadedDefender(position: Position, square: Int, mover: Int): Boolean {
        val defended = Tactics.attacksFrom(position, square)
        for (sq in defended) {
            val p = position.board[sq]
            if (p == Piece.NONE || Piece.colorOf(p) == mover) continue
            if (Tactics.attackers(position, sq, mover).isNotEmpty() &&
                Tactics.attackers(position, sq, 1 - mover).size <= 1
            ) return true
        }
        return false
    }

    /** Meilleure prise ou echec adverse au sens statique : sert a nommer le motif subi. */
    private fun strongestReply(position: Position): Int {
        val list = MoveList()
        position.generateMoves(list)
        var best = Move.NONE
        var bestGain = 0
        for (i in 0 until list.size) {
            val m = list[i]
            val gain = Tactics.see(position, m)
            if (gain > bestGain) {
                val work = position.copy()
                if (work.makeMove(m)) {
                    bestGain = gain
                    best = m
                }
            }
        }
        return best
    }
}
