package com.chessforge.chess

/** Conversion entre coups internes et notations SAN / UCI. */
object San {

    /** Notation algebrique abregee du coup, decorations de mat incluses. */
    fun of(position: Position, move: Int): String {
        val from = Move.from(move)
        val to = Move.to(move)
        val piece = position.board[from]
        val type = Piece.typeOf(piece)
        val flag = Move.flag(move)

        val core = when {
            flag == Move.FLAG_CASTLE_KING -> "O-O"
            flag == Move.FLAG_CASTLE_QUEEN -> "O-O-O"
            else -> buildString {
                val isCapture = position.board[to] != Piece.NONE || flag == Move.FLAG_EN_PASSANT
                if (type == Piece.PAWN) {
                    if (isCapture) {
                        append('a' + Square.file(from))
                        append('x')
                    }
                    append(Square.name(to))
                    val promo = Move.promo(move)
                    if (promo != 0) {
                        append('=')
                        append(Piece.sanLetter(promo))
                    }
                } else {
                    append(Piece.sanLetter(type))
                    append(disambiguation(position, move, type, from, to))
                    if (isCapture) append('x')
                    append(Square.name(to))
                }
            }
        }

        // Suffixe +/# : on doit jouer le coup pour connaitre l'etat du camp adverse.
        val work = position.copy()
        if (!work.makeMove(move)) return core
        val suffix = when {
            work.isInCheck() && !work.hasLegalMove() -> "#"
            work.isInCheck() -> "+"
            else -> ""
        }
        return core + suffix
    }

    private fun disambiguation(position: Position, move: Int, type: Int, from: Int, to: Int): String {
        val list = MoveList()
        position.generateMoves(list)
        val rivals = ArrayList<Int>(4)
        val work = position.copy()
        for (i in 0 until list.size) {
            val m = list[i]
            if (m == move) continue
            if (Move.to(m) != to) continue
            val f = Move.from(m)
            if (Piece.typeOf(position.board[f]) != type) continue
            if (work.makeMove(m)) {
                work.unmakeMove()
                rivals.add(m)
            }
        }
        if (rivals.isEmpty()) return ""
        val sameFile = rivals.any { Square.file(Move.from(it)) == Square.file(from) }
        val sameRank = rivals.any { Square.rank(Move.from(it)) == Square.rank(from) }
        return when {
            !sameFile -> ('a' + Square.file(from)).toString()
            !sameRank -> (Square.rank(from) + 1).toString()
            else -> Square.name(from)
        }
    }

    /**
     * Retrouve le coup legal correspondant a une notation SAN (tolerante : 0-0, e8Q,
     * "Nbd7", suffixes !?+#, ou notation UCI en secours).
     */
    fun parse(position: Position, text: String): Int {
        val wanted = normalize(text)
        if (wanted.isEmpty()) return Move.NONE

        val list = MoveList()
        position.generateMoves(list)
        val work = position.copy()
        var uciFallback = Move.NONE
        for (i in 0 until list.size) {
            val m = list[i]
            if (!work.makeMove(m)) continue
            work.unmakeMove()
            if (normalize(of(position, m)) == wanted) return m
            if (Move.toUci(m) == text.lowercase().trim()) uciFallback = m
        }
        return uciFallback
    }

    private fun normalize(san: String): String {
        var s = san.trim()
        s = s.replace("0-0-0", "O-O-O").replace("0-0", "O-O")
        s = s.replace("e.p.", "")
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '+', '#', '!', '?', '=', ' ', '–' -> {}
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** Retrouve un coup legal a partir de sa notation UCI ("e2e4", "a7a8q"). */
    fun fromUci(position: Position, uci: String): Int {
        if (uci.length < 4) return Move.NONE
        val from = Square.fromName(uci.substring(0, 2))
        val to = Square.fromName(uci.substring(2, 4))
        if (from == Square.NONE || to == Square.NONE) return Move.NONE
        val promo = if (uci.length >= 5) Piece.typeOf(Piece.fromChar(uci[4])) else 0
        val list = MoveList()
        position.generateMoves(list)
        val work = position.copy()
        for (i in 0 until list.size) {
            val m = list[i]
            if (Move.from(m) != from || Move.to(m) != to) continue
            if (Move.promo(m) != promo) continue
            if (work.makeMove(m)) {
                work.unmakeMove()
                return m
            }
        }
        return Move.NONE
    }

    /** Traduit une suite de coups UCI en SAN lisible, en partant de [position]. */
    fun lineToSan(position: Position, uciMoves: List<String>): List<String> {
        val work = position.copy()
        val out = ArrayList<String>(uciMoves.size)
        for (uci in uciMoves) {
            val m = fromUci(work, uci)
            if (m == Move.NONE) break
            out.add(of(work, m))
            if (!work.makeMove(m)) break
        }
        return out
    }
}
