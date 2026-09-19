package com.chessforge.engine.forge

import com.chessforge.chess.Piece
import com.chessforge.chess.Position
import com.chessforge.chess.Square
import com.chessforge.chess.Tables

/**
 * Evaluation statique du moteur integre : materiel + tables positionnelles interpolees
 * entre milieu de partie et finale, structure de pions, securite du roi, mobilite.
 *
 * Le score est rendu du point de vue du camp au trait.
 */
object Evaluation {

    // Valeurs materielles distinctes en milieu de partie / finale.
    private val MG_VALUE = intArrayOf(0, 82, 337, 365, 477, 1025, 0)
    private val EG_VALUE = intArrayOf(0, 94, 281, 297, 512, 936, 0)

    // Tables ecrites comme un echiquier vu des blancs : index 0 = a8, index 63 = h1.
    private val MG_PAWN = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        98, 134, 61, 95, 68, 126, 34, -11,
        -6, 7, 26, 31, 65, 56, 25, -20,
        -14, 13, 6, 21, 23, 12, 17, -23,
        -27, -2, -5, 12, 17, 6, 10, -25,
        -26, -4, -4, -10, 3, 3, 33, -12,
        -35, -1, -20, -23, -15, 24, 38, -22,
        0, 0, 0, 0, 0, 0, 0, 0,
    )
    private val EG_PAWN = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        178, 173, 158, 134, 147, 132, 165, 187,
        94, 100, 85, 67, 56, 53, 82, 84,
        32, 24, 13, 5, -2, 4, 17, 17,
        13, 9, -3, -7, -7, -8, 3, -1,
        4, 7, -6, 1, 0, -5, -1, -8,
        13, 8, 8, 10, 13, 0, 2, -7,
        0, 0, 0, 0, 0, 0, 0, 0,
    )
    private val MG_KNIGHT = intArrayOf(
        -167, -89, -34, -49, 61, -97, -15, -107,
        -73, -41, 72, 36, 23, 62, 7, -17,
        -47, 60, 37, 65, 84, 129, 73, 44,
        -9, 17, 19, 53, 37, 69, 18, 22,
        -13, 4, 16, 13, 28, 19, 21, -8,
        -23, -9, 12, 10, 19, 17, 25, -16,
        -29, -53, -12, -3, -1, 18, -14, -19,
        -105, -21, -58, -33, -17, -28, -19, -23,
    )
    private val EG_KNIGHT = intArrayOf(
        -58, -38, -13, -28, -31, -27, -63, -99,
        -25, -8, -25, -2, -9, -25, -24, -52,
        -24, -20, 10, 9, -1, -9, -19, -41,
        -17, 3, 22, 22, 22, 11, 8, -18,
        -18, -6, 16, 25, 16, 17, 4, -18,
        -23, -3, -1, 15, 10, -3, -20, -22,
        -42, -20, -10, -5, -2, -20, -23, -44,
        -29, -51, -23, -15, -22, -18, -50, -64,
    )
    private val MG_BISHOP = intArrayOf(
        -29, 4, -82, -37, -25, -42, 7, -8,
        -26, 16, -18, -13, 30, 59, 18, -47,
        -16, 37, 43, 40, 35, 50, 37, -2,
        -4, 5, 19, 50, 37, 37, 7, -2,
        -6, 13, 13, 26, 34, 12, 10, 4,
        0, 15, 15, 15, 14, 27, 18, 10,
        4, 15, 16, 0, 7, 21, 33, 1,
        -33, -3, -14, -21, -13, -12, -39, -21,
    )
    private val EG_BISHOP = intArrayOf(
        -14, -21, -11, -8, -7, -9, -17, -24,
        -8, -4, 7, -12, -3, -13, -4, -14,
        2, -8, 0, -1, -2, 6, 0, 4,
        -3, 9, 12, 9, 14, 10, 3, 2,
        -6, 3, 13, 19, 7, 10, -3, -9,
        -12, -3, 8, 10, 13, 3, -7, -15,
        -14, -18, -7, -1, 4, -9, -15, -27,
        -23, -9, -23, -5, -9, -16, -5, -17,
    )
    private val MG_ROOK = intArrayOf(
        32, 42, 32, 51, 63, 9, 31, 43,
        27, 32, 58, 62, 80, 67, 26, 44,
        -5, 19, 26, 36, 17, 45, 61, 16,
        -24, -11, 7, 26, 24, 35, -8, -20,
        -36, -26, -12, -1, 9, -7, 6, -23,
        -45, -25, -16, -17, 3, 0, -5, -33,
        -44, -16, -20, -9, -1, 11, -6, -71,
        -19, -13, 1, 17, 16, 7, -37, -26,
    )
    private val EG_ROOK = intArrayOf(
        13, 10, 18, 15, 12, 12, 8, 5,
        11, 13, 13, 11, -3, 3, 8, 3,
        7, 7, 7, 5, 4, -3, -5, -3,
        4, 3, 13, 1, 2, 1, -1, 2,
        3, 5, 8, 4, -5, -6, -8, -11,
        -4, 0, -5, -1, -7, -12, -8, -16,
        -6, -6, 0, 2, -9, -9, -11, -3,
        -9, 2, 3, -1, -5, -13, 4, -20,
    )
    private val MG_QUEEN = intArrayOf(
        -28, 0, 29, 12, 59, 44, 43, 45,
        -24, -39, -5, 1, -16, 57, 28, 54,
        -13, -17, 7, 8, 29, 56, 47, 57,
        -27, -27, -16, -16, -1, 17, -2, 1,
        -9, -26, -9, -10, -2, -4, 3, -3,
        -14, 2, -11, -2, -5, 2, 14, 5,
        -35, -8, 11, 2, 8, 15, -3, 1,
        -1, -18, -9, 10, -15, -25, -31, -50,
    )
    private val EG_QUEEN = intArrayOf(
        -9, 22, 22, 27, 27, 19, 10, 20,
        -17, 20, 32, 41, 58, 25, 30, 0,
        -20, 6, 9, 49, 47, 35, 19, 9,
        3, 22, 24, 45, 57, 40, 57, 36,
        -18, 28, 19, 47, 31, 34, 39, 23,
        -16, -27, 15, 6, 9, 17, 10, 5,
        -22, -23, -30, -16, -16, -23, -36, -32,
        -33, -28, -22, -43, -5, -32, -20, -41,
    )
    private val MG_KING = intArrayOf(
        -65, 23, 16, -15, -56, -34, 2, 13,
        29, -1, -20, -7, -8, -4, -38, -29,
        -9, 24, 2, -16, -20, 6, 22, -22,
        -17, -20, -12, -27, -30, -25, -14, -36,
        -49, -1, -27, -39, -46, -44, -33, -51,
        -14, -14, -22, -46, -44, -30, -15, -27,
        1, 7, -8, -64, -43, -16, 9, 8,
        -15, 36, 12, -54, 8, -28, 24, 14,
    )
    private val EG_KING = intArrayOf(
        -74, -35, -18, -18, -11, 15, 4, -17,
        -12, 17, 14, 17, 17, 38, 23, 11,
        10, 17, 23, 15, 20, 45, 44, 13,
        -8, 22, 24, 27, 26, 33, 26, 3,
        -18, -4, 21, 24, 27, 23, 9, -11,
        -19, -3, 11, 21, 23, 16, 7, -9,
        -27, -11, 4, 13, 14, 4, -5, -17,
        -53, -34, -21, -11, -28, -14, -24, -43,
    )

    private val MG_TABLES = arrayOf(IntArray(64), MG_PAWN, MG_KNIGHT, MG_BISHOP, MG_ROOK, MG_QUEEN, MG_KING)
    private val EG_TABLES = arrayOf(IntArray(64), EG_PAWN, EG_KNIGHT, EG_BISHOP, EG_ROOK, EG_QUEEN, EG_KING)

    /** Poids de phase par type de piece (total 24 en position initiale). */
    private val PHASE_WEIGHT = intArrayOf(0, 0, 1, 1, 2, 4, 0)

    private const val BISHOP_PAIR_MG = 25
    private const val BISHOP_PAIR_EG = 45
    private val PASSED_PAWN_MG = intArrayOf(0, 5, 10, 20, 35, 60, 100, 0)
    private val PASSED_PAWN_EG = intArrayOf(0, 10, 20, 35, 60, 100, 160, 0)
    private const val DOUBLED_PAWN = -12
    private const val ISOLATED_PAWN = -14
    private const val ROOK_OPEN_FILE = 26
    private const val ROOK_SEMI_OPEN_FILE = 12
    private const val KING_SHIELD_MISSING = -18
    private const val TEMPO = 12

    /** Index de table pour une case vue du camp donne. */
    private fun tableIndex(color: Int, sq: Int): Int = if (color == Piece.WHITE) sq xor 56 else sq

    fun evaluate(position: Position): Int {
        var mg = 0
        var eg = 0
        var phase = 0

        val pawnsByFile = Array(2) { IntArray(8) }
        val pawnSquares = Array(2) { ArrayList<Int>(8) }
        val bishops = intArrayOf(0, 0)

        for (sq in 0..63) {
            val piece = position.board[sq]
            if (piece == Piece.NONE) continue
            val color = Piece.colorOf(piece)
            val type = Piece.typeOf(piece)
            val sign = if (color == Piece.WHITE) 1 else -1
            val idx = tableIndex(color, sq)

            mg += sign * (MG_VALUE[type] + MG_TABLES[type][idx])
            eg += sign * (EG_VALUE[type] + EG_TABLES[type][idx])
            phase += PHASE_WEIGHT[type]

            when (type) {
                Piece.PAWN -> {
                    pawnsByFile[color][Square.file(sq)]++
                    pawnSquares[color].add(sq)
                }
                Piece.BISHOP -> bishops[color]++
                else -> {}
            }
        }

        for (color in 0..1) {
            val sign = if (color == Piece.WHITE) 1 else -1
            if (bishops[color] >= 2) {
                mg += sign * BISHOP_PAIR_MG
                eg += sign * BISHOP_PAIR_EG
            }
            // Structure de pions
            for (file in 0..7) {
                val count = pawnsByFile[color][file]
                if (count > 1) {
                    mg += sign * DOUBLED_PAWN * (count - 1)
                    eg += sign * DOUBLED_PAWN * (count - 1)
                }
                if (count > 0) {
                    val leftEmpty = file == 0 || pawnsByFile[color][file - 1] == 0
                    val rightEmpty = file == 7 || pawnsByFile[color][file + 1] == 0
                    if (leftEmpty && rightEmpty) {
                        mg += sign * ISOLATED_PAWN * count
                        eg += sign * ISOLATED_PAWN * count
                    }
                }
            }
            // Pions passes
            for (sq in pawnSquares[color]) {
                if (isPassed(position, color, sq)) {
                    val relRank = if (color == Piece.WHITE) Square.rank(sq) else 7 - Square.rank(sq)
                    mg += sign * PASSED_PAWN_MG[relRank]
                    eg += sign * PASSED_PAWN_EG[relRank]
                }
            }
            // Tours sur colonnes ouvertes
            for (sq in position.squaresOf(color, Piece.ROOK)) {
                val file = Square.file(sq)
                val own = pawnsByFile[color][file]
                val foe = pawnsByFile[1 - color][file]
                if (own == 0 && foe == 0) mg += sign * ROOK_OPEN_FILE
                else if (own == 0) mg += sign * ROOK_SEMI_OPEN_FILE
            }
            // Bouclier du roi (uniquement pertinent en milieu de partie)
            val ks = position.kingSquare[color]
            if (ks != Square.NONE) {
                var missing = 0
                val dir = if (color == Piece.WHITE) 1 else -1
                val kf = Square.file(ks)
                val kr = Square.rank(ks)
                for (df in -1..1) {
                    val f = kf + df
                    if (f !in 0..7) continue
                    val r = kr + dir
                    if (r !in 0..7) continue
                    if (position.board[Square.of(f, r)] != Piece.of(color, Piece.PAWN)) missing++
                }
                mg += sign * KING_SHIELD_MISSING * missing
            }
            mg += sign * mobility(position, color)
        }

        val phaseClamped = phase.coerceAtMost(24)
        val score = (mg * phaseClamped + eg * (24 - phaseClamped)) / 24
        val whitePov = score + if (position.side == Piece.WHITE) TEMPO else -TEMPO
        return if (position.side == Piece.WHITE) whitePov else -whitePov
    }

    private fun isPassed(position: Position, color: Int, sq: Int): Boolean {
        val foePawn = Piece.of(1 - color, Piece.PAWN)
        val file = Square.file(sq)
        val rank = Square.rank(sq)
        val range = if (color == Piece.WHITE) (rank + 1)..7 else 0..(rank - 1)
        for (f in (file - 1)..(file + 1)) {
            if (f !in 0..7) continue
            for (r in range) if (position.board[Square.of(f, r)] == foePawn) return false
        }
        return true
    }

    /** Mobilite legere : nombre de cases atteintes par les pieces mineures et majeures. */
    private fun mobility(position: Position, color: Int): Int {
        var count = 0
        for (sq in 0..63) {
            val piece = position.board[sq]
            if (piece == Piece.NONE || Piece.colorOf(piece) != color) continue
            when (Piece.typeOf(piece)) {
                Piece.KNIGHT -> for (t in Tables.knightAttacks[sq]) if (position.colorAt(t) != color) count++
                Piece.BISHOP -> count += slideCount(position, sq, Tables.BISHOP_DIRS, color)
                Piece.ROOK -> count += slideCount(position, sq, Tables.ROOK_DIRS, color)
                Piece.QUEEN -> {
                    count += slideCount(position, sq, Tables.BISHOP_DIRS, color) / 2
                    count += slideCount(position, sq, Tables.ROOK_DIRS, color) / 2
                }
            }
        }
        return count * 2
    }

    private fun slideCount(position: Position, from: Int, dirs: IntArray, color: Int): Int {
        var count = 0
        for (dir in dirs) {
            for (to in Tables.rays[from][dir]) {
                val p = position.board[to]
                if (p == Piece.NONE) {
                    count++
                } else {
                    if (Piece.colorOf(p) != color) count++
                    break
                }
            }
        }
        return count
    }
}
