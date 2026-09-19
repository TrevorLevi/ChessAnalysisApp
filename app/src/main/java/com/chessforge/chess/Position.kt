package com.chessforge.chess

/**
 * Position d'echecs mutable avec generation de coups, make/unmake et cle Zobrist incrementale.
 *
 * Concue pour etre reutilisee dans la boucle de recherche : aucune allocation dans
 * [makeMove] / [unmakeMove] / [generateMoves].
 */
class Position {

    val board = IntArray(64)
    var side = Piece.WHITE
        private set
    var castling = 0
        private set
    var epSquare = Square.NONE
        private set
    var halfmoveClock = 0
        private set
    var fullmoveNumber = 1
        private set
    var key = 0L
        private set

    /** Case du roi par couleur, maintenue a jour pour eviter une recherche a chaque test. */
    val kingSquare = intArrayOf(Square.NONE, Square.NONE)

    // --- Pile d'annulation ---------------------------------------------------

    private val undoMove = IntArray(MAX_PLY)
    private val undoCaptured = IntArray(MAX_PLY)
    private val undoCapturedSq = IntArray(MAX_PLY)
    private val undoCastling = IntArray(MAX_PLY)
    private val undoEp = IntArray(MAX_PLY)
    private val undoHalfmove = IntArray(MAX_PLY)
    private val undoKey = LongArray(MAX_PLY)
    private var undoTop = 0

    /** Nombre de coups joues depuis la creation de l'objet (utile pour la detection de repetition). */
    val plyPlayed: Int get() = undoTop

    // --- Construction -------------------------------------------------------

    fun clear() {
        board.fill(Piece.NONE)
        side = Piece.WHITE
        castling = 0
        epSquare = Square.NONE
        halfmoveClock = 0
        fullmoveNumber = 1
        key = 0L
        kingSquare[0] = Square.NONE
        kingSquare[1] = Square.NONE
        undoTop = 0
    }

    fun copy(): Position {
        val p = Position()
        board.copyInto(p.board)
        p.side = side
        p.castling = castling
        p.epSquare = epSquare
        p.halfmoveClock = halfmoveClock
        p.fullmoveNumber = fullmoveNumber
        p.key = key
        p.kingSquare[0] = kingSquare[0]
        p.kingSquare[1] = kingSquare[1]
        return p
    }

    /** Utilise par le parseur FEN ; recalcule la cle a la fin via [rebuildKey]. */
    fun setup(
        board: IntArray,
        side: Int,
        castling: Int,
        epSquare: Int,
        halfmoveClock: Int,
        fullmoveNumber: Int,
    ) {
        clear()
        board.copyInto(this.board)
        this.side = side
        this.castling = castling
        this.epSquare = epSquare
        this.halfmoveClock = halfmoveClock
        this.fullmoveNumber = fullmoveNumber
        for (sq in 0..63) {
            val p = this.board[sq]
            if (p != Piece.NONE && Piece.typeOf(p) == Piece.KING) kingSquare[Piece.colorOf(p)] = sq
        }
        rebuildKey()
    }

    private fun rebuildKey() {
        var k = 0L
        for (sq in 0..63) {
            val p = board[sq]
            if (p != Piece.NONE) k = k xor Zobrist.pieces[p][sq]
        }
        k = k xor Zobrist.castling[castling]
        if (epSquare != Square.NONE) k = k xor Zobrist.epFile[Square.file(epSquare)]
        if (side == Piece.BLACK) k = k xor Zobrist.sideToMove
        key = k
    }

    // --- Interrogation ------------------------------------------------------

    fun pieceAt(sq: Int): Int = board[sq]

    fun isEmpty(sq: Int): Boolean = board[sq] == Piece.NONE

    fun colorAt(sq: Int): Int {
        val p = board[sq]
        return if (p == Piece.NONE) -1 else Piece.colorOf(p)
    }

    /** Vrai si [sq] est attaquee par au moins une piece de couleur [byColor]. */
    fun isAttacked(sq: Int, byColor: Int): Boolean {
        // Pions : les cases d'ou un pion de byColor attaque sq sont les attaques
        // d'un pion de la couleur opposee depuis sq.
        val pawn = Piece.of(byColor, Piece.PAWN)
        for (s in Tables.pawnAttacks[1 - byColor][sq]) if (board[s] == pawn) return true

        val knight = Piece.of(byColor, Piece.KNIGHT)
        for (s in Tables.knightAttacks[sq]) if (board[s] == knight) return true

        val king = Piece.of(byColor, Piece.KING)
        for (s in Tables.kingAttacks[sq]) if (board[s] == king) return true

        val queen = Piece.of(byColor, Piece.QUEEN)
        val rook = Piece.of(byColor, Piece.ROOK)
        for (dir in Tables.ROOK_DIRS) {
            val ray = Tables.rays[sq][dir]
            for (s in ray) {
                val p = board[s]
                if (p != Piece.NONE) {
                    if (p == rook || p == queen) return true
                    break
                }
            }
        }
        val bishop = Piece.of(byColor, Piece.BISHOP)
        for (dir in Tables.BISHOP_DIRS) {
            val ray = Tables.rays[sq][dir]
            for (s in ray) {
                val p = board[s]
                if (p != Piece.NONE) {
                    if (p == bishop || p == queen) return true
                    break
                }
            }
        }
        return false
    }

    fun isInCheck(color: Int = side): Boolean {
        val ks = kingSquare[color]
        return ks != Square.NONE && isAttacked(ks, 1 - color)
    }

    /** Liste des cases occupees par une piece de type/couleur donnes. */
    fun squaresOf(color: Int, type: Int): List<Int> {
        val target = Piece.of(color, type)
        return (0..63).filter { board[it] == target }
    }

    fun materialCount(color: Int): Int {
        var total = 0
        for (sq in 0..63) {
            val p = board[sq]
            if (p != Piece.NONE && Piece.colorOf(p) == color) {
                val t = Piece.typeOf(p)
                if (t != Piece.KING) total += Piece.value(t)
            }
        }
        return total
    }

    /** Nombre de pieces hors rois et pions : sert a detecter la finale. */
    fun nonPawnPieceCount(): Int {
        var n = 0
        for (sq in 0..63) {
            val p = board[sq]
            if (p != Piece.NONE) {
                val t = Piece.typeOf(p)
                if (t != Piece.KING && t != Piece.PAWN) n++
            }
        }
        return n
    }

    // --- Generation de coups ------------------------------------------------

    /** Genere les coups pseudo-legaux (le roi peut rester en prise). */
    fun generateMoves(list: MoveList, capturesOnly: Boolean = false) {
        list.clear()
        val us = side
        val them = 1 - us
        for (from in 0..63) {
            val piece = board[from]
            if (piece == Piece.NONE || Piece.colorOf(piece) != us) continue
            when (Piece.typeOf(piece)) {
                Piece.PAWN -> genPawn(list, from, us, them, capturesOnly)
                Piece.KNIGHT -> genStep(list, from, Tables.knightAttacks[from], us, capturesOnly)
                Piece.BISHOP -> genSlide(list, from, Tables.BISHOP_DIRS, us, capturesOnly)
                Piece.ROOK -> genSlide(list, from, Tables.ROOK_DIRS, us, capturesOnly)
                Piece.QUEEN -> {
                    genSlide(list, from, Tables.ROOK_DIRS, us, capturesOnly)
                    genSlide(list, from, Tables.BISHOP_DIRS, us, capturesOnly)
                }
                Piece.KING -> {
                    genStep(list, from, Tables.kingAttacks[from], us, capturesOnly)
                    if (!capturesOnly) genCastles(list, us)
                }
            }
        }
    }

    private fun genStep(list: MoveList, from: Int, targets: IntArray, us: Int, capturesOnly: Boolean) {
        for (to in targets) {
            val p = board[to]
            if (p == Piece.NONE) {
                if (!capturesOnly) list.add(Move.make(from, to))
            } else if (Piece.colorOf(p) != us) {
                list.add(Move.make(from, to))
            }
        }
    }

    private fun genSlide(list: MoveList, from: Int, dirs: IntArray, us: Int, capturesOnly: Boolean) {
        for (dir in dirs) {
            for (to in Tables.rays[from][dir]) {
                val p = board[to]
                if (p == Piece.NONE) {
                    if (!capturesOnly) list.add(Move.make(from, to))
                } else {
                    if (Piece.colorOf(p) != us) list.add(Move.make(from, to))
                    break
                }
            }
        }
    }

    private fun genPawn(list: MoveList, from: Int, us: Int, them: Int, capturesOnly: Boolean) {
        val up = if (us == Piece.WHITE) 8 else -8
        val startRank = if (us == Piece.WHITE) 1 else 6
        val promoRank = if (us == Piece.WHITE) 7 else 0
        val rank = Square.rank(from)

        val one = from + up
        if (one in 0..63 && board[one] == Piece.NONE) {
            if (Square.rank(one) == promoRank) {
                // Une promotion change le materiel : on la genere meme en mode "captures".
                addPromotions(list, from, one)
            } else if (!capturesOnly) {
                list.add(Move.make(from, one))
                if (rank == startRank) {
                    val two = from + 2 * up
                    if (board[two] == Piece.NONE) list.add(Move.make(from, two, 0, Move.FLAG_DOUBLE_PUSH))
                }
            }
        }

        for (to in Tables.pawnAttacks[us][from]) {
            val p = board[to]
            if (p != Piece.NONE && Piece.colorOf(p) == them) {
                if (Square.rank(to) == promoRank) addPromotions(list, from, to)
                else list.add(Move.make(from, to))
            } else if (p == Piece.NONE && to == epSquare) {
                list.add(Move.make(from, to, 0, Move.FLAG_EN_PASSANT))
            }
        }
    }

    private fun addPromotions(list: MoveList, from: Int, to: Int) {
        list.add(Move.make(from, to, Piece.QUEEN))
        list.add(Move.make(from, to, Piece.KNIGHT))
        list.add(Move.make(from, to, Piece.ROOK))
        list.add(Move.make(from, to, Piece.BISHOP))
    }

    private fun genCastles(list: MoveList, us: Int) {
        if (isInCheck(us)) return
        val them = 1 - us
        if (us == Piece.WHITE) {
            if (castling and Castling.WK != 0 && board[5] == Piece.NONE && board[6] == Piece.NONE &&
                board[7] == Piece.WR && !isAttacked(5, them) && !isAttacked(6, them)
            ) list.add(Move.make(4, 6, 0, Move.FLAG_CASTLE_KING))
            if (castling and Castling.WQ != 0 && board[3] == Piece.NONE && board[2] == Piece.NONE &&
                board[1] == Piece.NONE && board[0] == Piece.WR &&
                !isAttacked(3, them) && !isAttacked(2, them)
            ) list.add(Move.make(4, 2, 0, Move.FLAG_CASTLE_QUEEN))
        } else {
            if (castling and Castling.BK != 0 && board[61] == Piece.NONE && board[62] == Piece.NONE &&
                board[63] == Piece.BR && !isAttacked(61, them) && !isAttacked(62, them)
            ) list.add(Move.make(60, 62, 0, Move.FLAG_CASTLE_KING))
            if (castling and Castling.BQ != 0 && board[59] == Piece.NONE && board[58] == Piece.NONE &&
                board[57] == Piece.NONE && board[56] == Piece.BR &&
                !isAttacked(59, them) && !isAttacked(58, them)
            ) list.add(Move.make(60, 58, 0, Move.FLAG_CASTLE_QUEEN))
        }
    }

    /** Coups legaux (allocation d'une nouvelle liste : usage interface, pas recherche). */
    fun legalMoves(): IntArray {
        val list = MoveList()
        generateMoves(list)
        val out = ArrayList<Int>(list.size)
        for (i in 0 until list.size) {
            val m = list[i]
            if (makeMove(m)) {
                out.add(m)
                unmakeMove()
            }
        }
        return out.toIntArray()
    }

    fun hasLegalMove(): Boolean {
        val list = MoveList()
        generateMoves(list)
        for (i in 0 until list.size) {
            if (makeMove(list[i])) {
                unmakeMove()
                return true
            }
        }
        return false
    }

    // --- Jouer / annuler ----------------------------------------------------

    /**
     * Joue [move] et renvoie false si le coup laisse le roi en prise (dans ce cas la position
     * est deja restauree : rien a annuler).
     */
    fun makeMove(move: Int): Boolean {
        val from = Move.from(move)
        val to = Move.to(move)
        val flag = Move.flag(move)
        val piece = board[from]
        val us = side
        val them = 1 - us

        var capturedSq = to
        if (flag == Move.FLAG_EN_PASSANT) capturedSq = if (us == Piece.WHITE) to - 8 else to + 8
        val captured = board[capturedSq]

        undoMove[undoTop] = move
        undoCaptured[undoTop] = captured
        undoCapturedSq[undoTop] = capturedSq
        undoCastling[undoTop] = castling
        undoEp[undoTop] = epSquare
        undoHalfmove[undoTop] = halfmoveClock
        undoKey[undoTop] = key
        undoTop++

        // Retirer l'ancien en-passant de la cle
        if (epSquare != Square.NONE) key = key xor Zobrist.epFile[Square.file(epSquare)]
        key = key xor Zobrist.castling[castling]

        if (captured != Piece.NONE) {
            board[capturedSq] = Piece.NONE
            key = key xor Zobrist.pieces[captured][capturedSq]
        }

        board[from] = Piece.NONE
        key = key xor Zobrist.pieces[piece][from]

        val promo = Move.promo(move)
        val placed = if (promo != 0) Piece.of(us, promo) else piece
        board[to] = placed
        key = key xor Zobrist.pieces[placed][to]

        if (Piece.typeOf(piece) == Piece.KING) kingSquare[us] = to

        when (flag) {
            Move.FLAG_CASTLE_KING -> {
                val rookFrom = if (us == Piece.WHITE) 7 else 63
                val rookTo = if (us == Piece.WHITE) 5 else 61
                val rook = board[rookFrom]
                board[rookFrom] = Piece.NONE
                board[rookTo] = rook
                key = key xor Zobrist.pieces[rook][rookFrom] xor Zobrist.pieces[rook][rookTo]
            }
            Move.FLAG_CASTLE_QUEEN -> {
                val rookFrom = if (us == Piece.WHITE) 0 else 56
                val rookTo = if (us == Piece.WHITE) 3 else 59
                val rook = board[rookFrom]
                board[rookFrom] = Piece.NONE
                board[rookTo] = rook
                key = key xor Zobrist.pieces[rook][rookFrom] xor Zobrist.pieces[rook][rookTo]
            }
        }

        // Droits de roque
        castling = castling and CASTLE_MASK[from] and CASTLE_MASK[to]
        key = key xor Zobrist.castling[castling]

        epSquare = if (flag == Move.FLAG_DOUBLE_PUSH) {
            val ep = if (us == Piece.WHITE) from + 8 else from - 8
            key = key xor Zobrist.epFile[Square.file(ep)]
            ep
        } else {
            Square.NONE
        }

        halfmoveClock =
            if (Piece.typeOf(piece) == Piece.PAWN || captured != Piece.NONE) 0 else halfmoveClock + 1
        if (us == Piece.BLACK) fullmoveNumber++

        side = them
        key = key xor Zobrist.sideToMove

        if (isAttacked(kingSquare[us], them)) {
            unmakeMove()
            return false
        }
        return true
    }

    fun unmakeMove() {
        if (undoTop == 0) return
        undoTop--
        val move = undoMove[undoTop]
        val captured = undoCaptured[undoTop]
        val capturedSq = undoCapturedSq[undoTop]

        val from = Move.from(move)
        val to = Move.to(move)
        val flag = Move.flag(move)

        side = 1 - side
        val us = side
        if (us == Piece.BLACK) fullmoveNumber--

        val placed = board[to]
        board[to] = Piece.NONE
        val original = if (Move.promo(move) != 0) Piece.of(us, Piece.PAWN) else placed
        board[from] = original

        if (Piece.typeOf(original) == Piece.KING) kingSquare[us] = from
        if (captured != Piece.NONE) board[capturedSq] = captured

        when (flag) {
            Move.FLAG_CASTLE_KING -> {
                val rookFrom = if (us == Piece.WHITE) 7 else 63
                val rookTo = if (us == Piece.WHITE) 5 else 61
                board[rookFrom] = board[rookTo]
                board[rookTo] = Piece.NONE
            }
            Move.FLAG_CASTLE_QUEEN -> {
                val rookFrom = if (us == Piece.WHITE) 0 else 56
                val rookTo = if (us == Piece.WHITE) 3 else 59
                board[rookFrom] = board[rookTo]
                board[rookTo] = Piece.NONE
            }
        }

        castling = undoCastling[undoTop]
        epSquare = undoEp[undoTop]
        halfmoveClock = undoHalfmove[undoTop]
        key = undoKey[undoTop]
    }

    /** Coup nul : utile pour l'elagage "null move" de la recherche. */
    fun makeNullMove() {
        undoMove[undoTop] = Move.NONE
        undoCaptured[undoTop] = Piece.NONE
        undoCapturedSq[undoTop] = Square.NONE
        undoCastling[undoTop] = castling
        undoEp[undoTop] = epSquare
        undoHalfmove[undoTop] = halfmoveClock
        undoKey[undoTop] = key
        undoTop++

        if (epSquare != Square.NONE) key = key xor Zobrist.epFile[Square.file(epSquare)]
        epSquare = Square.NONE
        side = 1 - side
        key = key xor Zobrist.sideToMove
        halfmoveClock++
    }

    fun unmakeNullMove() {
        undoTop--
        side = 1 - side
        castling = undoCastling[undoTop]
        epSquare = undoEp[undoTop]
        halfmoveClock = undoHalfmove[undoTop]
        key = undoKey[undoTop]
    }

    /** Vrai si la position courante s'est deja produite dans la partie en cours. */
    fun isRepetition(): Boolean {
        var count = 0
        var i = undoTop - 2
        var limit = halfmoveClock
        while (i >= 0 && limit > 0) {
            if (undoKey[i] == key) {
                count++
                if (count >= 1) return true
            }
            i -= 2
            limit -= 2
        }
        return false
    }

    /** Materiel insuffisant pour mater (cas simples uniquement). */
    fun isInsufficientMaterial(): Boolean {
        var minors = 0
        for (sq in 0..63) {
            val p = board[sq]
            if (p == Piece.NONE) continue
            when (Piece.typeOf(p)) {
                Piece.PAWN, Piece.ROOK, Piece.QUEEN -> return false
                Piece.KNIGHT, Piece.BISHOP -> minors++
            }
        }
        return minors <= 1
    }

    companion object {
        const val MAX_PLY = 1024

        /** Masque appliquant la perte de droits de roque quand une case est quittee ou prise. */
        private val CASTLE_MASK = IntArray(64) { Castling.ALL }.also { m ->
            m[4] = Castling.ALL and (Castling.WK or Castling.WQ).inv()
            m[60] = Castling.ALL and (Castling.BK or Castling.BQ).inv()
            m[0] = Castling.ALL and Castling.WQ.inv()
            m[7] = Castling.ALL and Castling.WK.inv()
            m[56] = Castling.ALL and Castling.BQ.inv()
            m[63] = Castling.ALL and Castling.BK.inv()
        }

        fun startPosition(): Position = Fen.parse(Fen.START)
    }
}
