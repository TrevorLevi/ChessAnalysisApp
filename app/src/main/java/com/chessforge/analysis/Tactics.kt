package com.chessforge.analysis

import com.chessforge.chess.Move
import com.chessforge.chess.MoveList
import com.chessforge.chess.Piece
import com.chessforge.chess.Position
import com.chessforge.chess.Square
import com.chessforge.chess.Tables

/**
 * Outils tactiques statiques partages par le classificateur de coups, le detecteur de
 * motifs et le generateur de puzzles.
 */
object Tactics {

    /** Cases occupees par les pieces de [byColor] qui attaquent [sq] (attaques directes). */
    fun attackers(position: Position, sq: Int, byColor: Int): List<Int> {
        val out = ArrayList<Int>(4)
        val pawn = Piece.of(byColor, Piece.PAWN)
        for (s in Tables.pawnAttacks[1 - byColor][sq]) if (position.board[s] == pawn) out.add(s)

        val knight = Piece.of(byColor, Piece.KNIGHT)
        for (s in Tables.knightAttacks[sq]) if (position.board[s] == knight) out.add(s)

        val king = Piece.of(byColor, Piece.KING)
        for (s in Tables.kingAttacks[sq]) if (position.board[s] == king) out.add(s)

        val queen = Piece.of(byColor, Piece.QUEEN)
        val rook = Piece.of(byColor, Piece.ROOK)
        for (dir in Tables.ROOK_DIRS) {
            for (s in Tables.rays[sq][dir]) {
                val p = position.board[s]
                if (p != Piece.NONE) {
                    if (p == rook || p == queen) out.add(s)
                    break
                }
            }
        }
        val bishop = Piece.of(byColor, Piece.BISHOP)
        for (dir in Tables.BISHOP_DIRS) {
            for (s in Tables.rays[sq][dir]) {
                val p = position.board[s]
                if (p != Piece.NONE) {
                    if (p == bishop || p == queen) out.add(s)
                    break
                }
            }
        }
        return out
    }

    /** Cases attaquees par la piece posee sur [from] (sans tenir compte de la legalite). */
    fun attacksFrom(position: Position, from: Int): List<Int> {
        val piece = position.board[from]
        if (piece == Piece.NONE) return emptyList()
        val color = Piece.colorOf(piece)
        return when (Piece.typeOf(piece)) {
            Piece.PAWN -> Tables.pawnAttacks[color][from].toList()
            Piece.KNIGHT -> Tables.knightAttacks[from].toList()
            Piece.KING -> Tables.kingAttacks[from].toList()
            Piece.BISHOP -> slideTargets(position, from, Tables.BISHOP_DIRS)
            Piece.ROOK -> slideTargets(position, from, Tables.ROOK_DIRS)
            Piece.QUEEN -> slideTargets(position, from, Tables.BISHOP_DIRS) +
                slideTargets(position, from, Tables.ROOK_DIRS)
            else -> emptyList()
        }
    }

    private fun slideTargets(position: Position, from: Int, dirs: IntArray): List<Int> {
        val out = ArrayList<Int>(14)
        for (dir in dirs) {
            for (to in Tables.rays[from][dir]) {
                out.add(to)
                if (position.board[to] != Piece.NONE) break
            }
        }
        return out
    }

    /** Une piece est "en l'air" si elle est attaquee et qu'aucun defenseur ne la couvre. */
    fun isHanging(position: Position, sq: Int): Boolean {
        val piece = position.board[sq]
        if (piece == Piece.NONE) return false
        val color = Piece.colorOf(piece)
        if (attackers(position, sq, 1 - color).isEmpty()) return false
        return attackers(position, sq, color).isEmpty()
    }

    /**
     * Evaluation statique d'echange (SEE) du coup, en centipions.
     * Contrairement au SEE classique, l'echange est joue reellement sur l'echiquier :
     * les clouages et les echecs sont donc pris en compte correctement.
     */
    fun see(position: Position, move: Int): Int {
        val to = Move.to(move)
        val captured = position.board[to]
        var gain = if (captured != Piece.NONE) Piece.value(Piece.typeOf(captured)) else 0
        if (Move.flag(move) == Move.FLAG_EN_PASSANT) gain = Piece.value(Piece.PAWN)
        val promo = Move.promo(move)
        if (promo != 0) gain += Piece.value(promo) - Piece.value(Piece.PAWN)

        val work = position.copy()
        if (!work.makeMove(move)) return 0
        return gain - bestCaptureValue(work, to, 0)
    }

    /** Meilleur gain net pour le camp au trait en prenant sur [target] (0 si rien a gagner). */
    private fun bestCaptureValue(position: Position, target: Int, depth: Int): Int {
        if (depth > 12) return 0
        val victim = position.board[target]
        if (victim == Piece.NONE) return 0
        val victimValue = Piece.value(Piece.typeOf(victim))

        val list = MoveList()
        position.generateMoves(list, capturesOnly = true)
        var bestGain = 0
        var bestAttackerValue = Int.MAX_VALUE
        var bestMove = Move.NONE
        for (i in 0 until list.size) {
            val m = list[i]
            if (Move.to(m) != target) continue
            val attackerValue = Piece.value(Piece.typeOf(position.board[Move.from(m)]))
            if (attackerValue < bestAttackerValue) {
                bestAttackerValue = attackerValue
                bestMove = m
            }
        }
        if (bestMove == Move.NONE) return 0
        if (!position.makeMove(bestMove)) return 0
        val gain = victimValue - bestCaptureValue(position, target, depth + 1)
        position.unmakeMove()
        bestGain = maxOf(0, gain)
        return bestGain
    }

    /** Bilan materiel du camp [color] en centipions (positif = avantage). */
    fun materialBalance(position: Position, color: Int): Int =
        position.materialCount(color) - position.materialCount(1 - color)

    /** Vrai si la piece en [sq] n'a aucune case de fuite sure : elle est piegee. */
    fun isTrapped(position: Position, sq: Int): Boolean {
        val piece = position.board[sq]
        if (piece == Piece.NONE) return false
        val type = Piece.typeOf(piece)
        if (type == Piece.KING || type == Piece.PAWN) return false
        val color = Piece.colorOf(piece)
        if (position.side != color) return false
        val attackers = attackers(position, sq, 1 - color)
        if (attackers.isEmpty()) return false
        // Attaquee par une piece de moindre valeur, ou non defendue : il faut fuir.
        val minAttacker = attackers.minOf { Piece.value(Piece.typeOf(position.board[it])) }
        if (minAttacker >= Piece.value(type) && attackers(position, sq, color).isNotEmpty()) return false

        val list = MoveList()
        position.generateMoves(list)
        for (i in 0 until list.size) {
            val m = list[i]
            if (Move.from(m) != sq) continue
            if (see(position, m) >= 0) {
                val work = position.copy()
                if (work.makeMove(m)) return false
            }
        }
        return true
    }

    /**
     * Cibles de valeur attaquees depuis [from] apres le coup : sert a detecter
     * fourchettes et doubles attaques.
     */
    fun valuableTargets(position: Position, from: Int, minValue: Int = 300): List<Int> {
        val piece = position.board[from]
        if (piece == Piece.NONE) return emptyList()
        val color = Piece.colorOf(piece)
        val attackerValue = Piece.value(Piece.typeOf(piece))
        return attacksFrom(position, from).filter { sq ->
            val target = position.board[sq]
            if (target == Piece.NONE || Piece.colorOf(target) == color) return@filter false
            val type = Piece.typeOf(target)
            if (type == Piece.KING) return@filter true
            val value = Piece.value(type)
            // Une cible ne compte que si la prendre serait profitable.
            value >= minValue && (value > attackerValue || attackers(position, sq, 1 - color).isEmpty())
        }
    }

    /**
     * Detecte clouage et enfilade : une piece adverse est seule entre notre piece a
     * longue portee (en [from]) et une piece de plus grande valeur derriere elle.
     */
    fun lineTactics(position: Position, from: Int): LineTactic? {
        val piece = position.board[from]
        if (piece == Piece.NONE) return null
        val color = Piece.colorOf(piece)
        val type = Piece.typeOf(piece)
        val dirs = when (type) {
            Piece.BISHOP -> Tables.BISHOP_DIRS
            Piece.ROOK -> Tables.ROOK_DIRS
            Piece.QUEEN -> Tables.ROOK_DIRS + Tables.BISHOP_DIRS
            else -> return null
        }
        for (dir in dirs) {
            var first = Square.NONE
            for (sq in Tables.rays[from][dir]) {
                val p = position.board[sq]
                if (p == Piece.NONE) continue
                if (Piece.colorOf(p) == color) break
                if (first == Square.NONE) {
                    first = sq
                    continue
                }
                // Deuxieme piece adverse sur la ligne
                val frontValue = Piece.value(Piece.typeOf(position.board[first]))
                val backType = Piece.typeOf(p)
                val backValue = Piece.value(backType)
                return when {
                    backType == Piece.KING -> LineTactic(first, sq, pin = true)
                    backValue > frontValue -> LineTactic(first, sq, pin = true)
                    frontValue > backValue -> LineTactic(first, sq, pin = false)
                    else -> null
                }
            }
        }
        return null
    }

    data class LineTactic(val frontSquare: Int, val backSquare: Int, val pin: Boolean)

    /** Vrai si le roi de [color] est enferme sur sa rangee de depart par ses propres pions. */
    fun isBackRankWeak(position: Position, color: Int): Boolean {
        val king = position.kingSquare[color]
        if (king == Square.NONE) return false
        val homeRank = if (color == Piece.WHITE) 0 else 7
        if (Square.rank(king) != homeRank) return false
        val dir = if (color == Piece.WHITE) 1 else -1
        var escapes = 0
        for (df in -1..1) {
            val f = Square.file(king) + df
            if (f !in 0..7) continue
            val sq = Square.of(f, Square.rank(king) + dir)
            if (position.board[sq] == Piece.NONE && !position.isAttacked(sq, 1 - color)) escapes++
        }
        return escapes == 0
    }

    /** Pion passe le plus avance de [color], ou null. */
    fun mostAdvancedPassedPawn(position: Position, color: Int): Int? {
        val pawns = position.squaresOf(color, Piece.PAWN)
        val foePawn = Piece.of(1 - color, Piece.PAWN)
        return pawns.filter { sq ->
            val file = Square.file(sq)
            val rank = Square.rank(sq)
            val range = if (color == Piece.WHITE) (rank + 1)..7 else 0..(rank - 1)
            (file - 1..file + 1).none { f ->
                f in 0..7 && range.any { r -> position.board[Square.of(f, r)] == foePawn }
            }
        }.maxByOrNull { if (color == Piece.WHITE) Square.rank(it) else 7 - Square.rank(it) }
    }
}
