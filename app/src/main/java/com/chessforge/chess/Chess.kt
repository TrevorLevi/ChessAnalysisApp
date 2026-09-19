package com.chessforge.chess

/**
 * Constantes et tables precalculees du coeur echiquéen.
 *
 * Representation : plateau "mailbox" de 64 cases, a1 = 0, h1 = 7, a8 = 56, h8 = 63.
 * Une piece tient dans un Int : (couleur shl 3) or type, donc 1..6 pour les blancs
 * et 9..14 pour les noirs. 0 = case vide.
 */
object Piece {
    const val NONE = 0
    const val PAWN = 1
    const val KNIGHT = 2
    const val BISHOP = 3
    const val ROOK = 4
    const val QUEEN = 5
    const val KING = 6

    const val WHITE = 0
    const val BLACK = 1

    const val WP = 1
    const val WN = 2
    const val WB = 3
    const val WR = 4
    const val WQ = 5
    const val WK = 6
    const val BP = 9
    const val BN = 10
    const val BB = 11
    const val BR = 12
    const val BQ = 13
    const val BK = 14

    fun of(color: Int, type: Int): Int = (color shl 3) or type
    fun typeOf(piece: Int): Int = piece and 7
    fun colorOf(piece: Int): Int = piece shr 3
    fun isWhite(piece: Int): Boolean = piece in WP..WK
    fun isBlack(piece: Int): Boolean = piece in BP..BK

    /** Lettre FEN d'une piece ('P', 'n', ...). */
    fun toChar(piece: Int): Char {
        val c = when (typeOf(piece)) {
            PAWN -> 'p'; KNIGHT -> 'n'; BISHOP -> 'b'
            ROOK -> 'r'; QUEEN -> 'q'; KING -> 'k'
            else -> ' '
        }
        return if (colorOf(piece) == WHITE) c.uppercaseChar() else c
    }

    fun fromChar(c: Char): Int {
        val type = when (c.lowercaseChar()) {
            'p' -> PAWN; 'n' -> KNIGHT; 'b' -> BISHOP
            'r' -> ROOK; 'q' -> QUEEN; 'k' -> KING
            else -> return NONE
        }
        return of(if (c.isUpperCase()) WHITE else BLACK, type)
    }

    /** Lettre de notation algebrique (SAN) du type de piece, vide pour un pion. */
    fun sanLetter(type: Int): String = when (type) {
        KNIGHT -> "N"; BISHOP -> "B"; ROOK -> "R"; QUEEN -> "Q"; KING -> "K"
        else -> ""
    }

    /** Valeur materielle en centipions, utilisee par le tri des coups et le bilan materiel. */
    fun value(type: Int): Int = when (type) {
        PAWN -> 100; KNIGHT -> 320; BISHOP -> 330; ROOK -> 500; QUEEN -> 900; KING -> 20000
        else -> 0
    }
}

object Square {
    const val NONE = -1

    fun of(file: Int, rank: Int): Int = (rank shl 3) or file
    fun file(sq: Int): Int = sq and 7
    fun rank(sq: Int): Int = sq shr 3

    fun name(sq: Int): String =
        if (sq < 0 || sq > 63) "-" else "${('a' + file(sq))}${rank(sq) + 1}"

    fun fromName(name: String): Int {
        if (name.length < 2) return NONE
        val f = name[0].lowercaseChar() - 'a'
        val r = name[1] - '1'
        return if (f in 0..7 && r in 0..7) of(f, r) else NONE
    }
}

object Castling {
    const val WK = 1
    const val WQ = 2
    const val BK = 4
    const val BQ = 8
    const val ALL = 15
}

/**
 * Un coup tient dans un Int :
 *  bits 0-5   case de depart
 *  bits 6-11  case d'arrivee
 *  bits 12-14 type de promotion (0 = aucune)
 *  bits 15-17 drapeau special
 */
object Move {
    const val NONE = 0

    const val FLAG_NORMAL = 0
    const val FLAG_DOUBLE_PUSH = 1
    const val FLAG_EN_PASSANT = 2
    const val FLAG_CASTLE_KING = 3
    const val FLAG_CASTLE_QUEEN = 4

    fun make(from: Int, to: Int, promo: Int = 0, flag: Int = FLAG_NORMAL): Int =
        from or (to shl 6) or (promo shl 12) or (flag shl 15)

    fun from(move: Int): Int = move and 63
    fun to(move: Int): Int = (move shr 6) and 63
    fun promo(move: Int): Int = (move shr 12) and 7
    fun flag(move: Int): Int = (move shr 15) and 7
    fun isCastle(move: Int): Boolean = flag(move) == FLAG_CASTLE_KING || flag(move) == FLAG_CASTLE_QUEEN

    /** Notation UCI : "e2e4", "e7e8q". */
    fun toUci(move: Int): String {
        if (move == NONE) return "0000"
        val promo = promo(move)
        val suffix = if (promo == 0) "" else Piece.toChar(Piece.of(Piece.BLACK, promo)).toString()
        return Square.name(from(move)) + Square.name(to(move)) + suffix
    }
}

/** Tables d'attaque precalculees. */
object Tables {
    /** Index des directions : N, NE, E, SE, S, SW, W, NW. */
    val DIR_FILE = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
    val DIR_RANK = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
    val ROOK_DIRS = intArrayOf(0, 2, 4, 6)
    val BISHOP_DIRS = intArrayOf(1, 3, 5, 7)

    /** rays[case][direction] = cases alignees, de la plus proche a la plus lointaine. */
    val rays: Array<Array<IntArray>> = Array(64) { sq ->
        Array(8) { dir ->
            val out = ArrayList<Int>(7)
            var f = Square.file(sq) + DIR_FILE[dir]
            var r = Square.rank(sq) + DIR_RANK[dir]
            while (f in 0..7 && r in 0..7) {
                out.add(Square.of(f, r))
                f += DIR_FILE[dir]
                r += DIR_RANK[dir]
            }
            out.toIntArray()
        }
    }

    val knightAttacks: Array<IntArray> = Array(64) { sq ->
        val f = Square.file(sq)
        val r = Square.rank(sq)
        val deltas = arrayOf(
            1 to 2, 2 to 1, 2 to -1, 1 to -2,
            -1 to -2, -2 to -1, -2 to 1, -1 to 2,
        )
        deltas.mapNotNull { (df, dr) ->
            val nf = f + df
            val nr = r + dr
            if (nf in 0..7 && nr in 0..7) Square.of(nf, nr) else null
        }.toIntArray()
    }

    val kingAttacks: Array<IntArray> = Array(64) { sq ->
        val f = Square.file(sq)
        val r = Square.rank(sq)
        (0..7).mapNotNull { dir ->
            val nf = f + DIR_FILE[dir]
            val nr = r + DIR_RANK[dir]
            if (nf in 0..7 && nr in 0..7) Square.of(nf, nr) else null
        }.toIntArray()
    }

    /** pawnAttacks[couleur][case] = cases attaquees par un pion de cette couleur. */
    val pawnAttacks: Array<Array<IntArray>> = Array(2) { color ->
        Array(64) { sq ->
            val dr = if (color == Piece.WHITE) 1 else -1
            val f = Square.file(sq)
            val r = Square.rank(sq)
            listOf(-1, 1).mapNotNull { df ->
                val nf = f + df
                val nr = r + dr
                if (nf in 0..7 && nr in 0..7) Square.of(nf, nr) else null
            }.toIntArray()
        }
    }

    /** Distance de Chebyshev entre deux cases. */
    val distance: Array<IntArray> = Array(64) { a ->
        IntArray(64) { b ->
            maxOf(
                kotlin.math.abs(Square.file(a) - Square.file(b)),
                kotlin.math.abs(Square.rank(a) - Square.rank(b)),
            )
        }
    }

    /** direction[a][b] = index de direction de a vers b si alignes, -1 sinon. */
    val direction: Array<IntArray> = Array(64) { a ->
        IntArray(64) { -1 }.also { row ->
            for (dir in 0..7) for (sq in rays[a][dir]) row[sq] = dir
        }
    }
}

/** Liste de coups a allocation unique, utilisee dans les boucles de recherche. */
class MoveList(capacity: Int = 256) {
    val moves = IntArray(capacity)
    var size = 0
        private set

    fun add(move: Int) {
        moves[size++] = move
    }

    fun clear() {
        size = 0
    }

    operator fun get(index: Int): Int = moves[index]

    fun set(index: Int, move: Int) {
        moves[index] = move
    }

    fun toIntArray(): IntArray = moves.copyOf(size)
    fun asList(): List<Int> = (0 until size).map { moves[it] }
    fun contains(move: Int): Boolean = (0 until size).any { moves[it] == move }
}
