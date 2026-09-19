package com.chessforge.chess

object Fen {
    const val START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    fun parse(fen: String): Position {
        val parts = fen.trim().split(Regex("\\s+"))
        require(parts.size >= 2) { "FEN invalide : $fen" }

        val board = IntArray(64) { Piece.NONE }
        var rank = 7
        var file = 0
        for (c in parts[0]) {
            when {
                c == '/' -> {
                    rank--
                    file = 0
                }
                c.isDigit() -> file += c - '0'
                else -> {
                    if (rank in 0..7 && file in 0..7) board[Square.of(file, rank)] = Piece.fromChar(c)
                    file++
                }
            }
        }

        val side = if (parts[1].startsWith("b")) Piece.BLACK else Piece.WHITE

        var castling = 0
        val castleField = parts.getOrElse(2) { "-" }
        if (castleField != "-") for (c in castleField) {
            when (c) {
                'K' -> castling = castling or Castling.WK
                'Q' -> castling = castling or Castling.WQ
                'k' -> castling = castling or Castling.BK
                'q' -> castling = castling or Castling.BQ
            }
        }

        val epField = parts.getOrElse(3) { "-" }
        val ep = if (epField == "-") Square.NONE else Square.fromName(epField)
        val halfmove = parts.getOrElse(4) { "0" }.toIntOrNull() ?: 0
        val fullmove = parts.getOrElse(5) { "1" }.toIntOrNull() ?: 1

        return Position().apply { setup(board, side, castling, ep, halfmove, fullmove) }
    }

    fun of(position: Position): String = buildString {
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val p = position.board[Square.of(file, rank)]
                if (p == Piece.NONE) {
                    empty++
                } else {
                    if (empty > 0) {
                        append(empty)
                        empty = 0
                    }
                    append(Piece.toChar(p))
                }
            }
            if (empty > 0) append(empty)
            if (rank > 0) append('/')
        }
        append(' ')
        append(if (position.side == Piece.WHITE) 'w' else 'b')
        append(' ')
        val c = position.castling
        if (c == 0) {
            append('-')
        } else {
            if (c and Castling.WK != 0) append('K')
            if (c and Castling.WQ != 0) append('Q')
            if (c and Castling.BK != 0) append('k')
            if (c and Castling.BQ != 0) append('q')
        }
        append(' ')
        append(if (position.epSquare == Square.NONE) "-" else Square.name(position.epSquare))
        append(' ')
        append(position.halfmoveClock)
        append(' ')
        append(position.fullmoveNumber)
    }

    /** FEN sans les compteurs : identifiant stable d'une position pour la deduplication. */
    fun positionId(position: Position): String = of(position).split(" ").take(4).joinToString(" ")
}
