package com.chessforge.chess.pgn

import com.chessforge.chess.Fen
import com.chessforge.chess.Move
import com.chessforge.chess.Position
import com.chessforge.chess.San

data class PgnMove(
    val san: String,
    /** Temps restant a la pendule apres le coup, en secondes (balise `[%clk ...]`). */
    val clockSeconds: Double? = null,
    val comment: String? = null,
    val nags: List<Int> = emptyList(),
)

data class PgnGame(
    val headers: Map<String, String>,
    val moves: List<PgnMove>,
    val result: String,
) {
    operator fun get(key: String): String? = headers[key]

    val startFen: String get() = headers["FEN"] ?: Fen.START
}

/** Un coup rejoue, avec la position avant/apres et le temps reellement consomme. */
data class PlayedMove(
    val ply: Int,
    val san: String,
    val uci: String,
    val move: Int,
    val fenBefore: String,
    val fenAfter: String,
    val sideToMove: Int,
    val clockSeconds: Double?,
    val secondsSpent: Double?,
    val isCapture: Boolean,
    val isCheck: Boolean,
)

object PgnParser {

    private val HEADER = Regex("""\[\s*(\w+)\s+"((?:[^"\\]|\\.)*)"\s*]""")
    private val CLOCK = Regex("""\[%clk\s+(\d+):(\d+):([\d.]+)]""")

    /** Decoupe un fichier PGN pouvant contenir plusieurs parties. */
    fun parseAll(text: String): List<PgnGame> {
        val games = ArrayList<PgnGame>()
        val lines = text.lines()
        val buffer = StringBuilder()
        var sawMovetext = false
        for (line in lines) {
            val trimmed = line.trim()
            val isHeader = trimmed.startsWith("[") && trimmed.endsWith("]")
            if (isHeader && sawMovetext) {
                parse(buffer.toString())?.let { games.add(it) }
                buffer.clear()
                sawMovetext = false
            }
            if (!isHeader && trimmed.isNotEmpty()) sawMovetext = true
            buffer.append(line).append('\n')
        }
        parse(buffer.toString())?.let { games.add(it) }
        return games
    }

    fun parse(pgn: String): PgnGame? {
        if (pgn.isBlank()) return null
        val headers = LinkedHashMap<String, String>()
        for (m in HEADER.findAll(pgn)) {
            headers[m.groupValues[1]] = m.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\")
        }
        // Le bloc d'en-tetes s'arrete a la premiere ligne non vide qui n'est pas une balise.
        // On ne peut pas chercher le dernier ']' : les commentaires `[%clk ...]` en contiennent.
        val lines = pgn.lines()
        var firstMoveLine = 0
        while (firstMoveLine < lines.size) {
            val t = lines[firstMoveLine].trim()
            if (t.isEmpty() || (t.startsWith("[") && t.endsWith("]"))) firstMoveLine++ else break
        }
        val moves = parseMovetext(lines.drop(firstMoveLine).joinToString("\n"))
        if (headers.isEmpty() && moves.isEmpty()) return null
        return PgnGame(headers, moves, headers["Result"] ?: "*")
    }

    private fun parseMovetext(text: String): List<PgnMove> {
        val moves = ArrayList<PgnMove>()
        var i = 0
        var pendingComment: String? = null
        var pendingClock: Double? = null
        val pendingNags = ArrayList<Int>()

        fun attachTo(last: Int) {
            if (last < 0) return
            moves[last] = moves[last].copy(
                clockSeconds = pendingClock ?: moves[last].clockSeconds,
                comment = pendingComment ?: moves[last].comment,
                nags = if (pendingNags.isEmpty()) moves[last].nags else moves[last].nags + pendingNags,
            )
            pendingComment = null
            pendingClock = null
            pendingNags.clear()
        }

        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++

                c == '{' -> {
                    val end = text.indexOf('}', i).let { if (it < 0) text.length else it }
                    val body = text.substring(i + 1, end)
                    CLOCK.find(body)?.let { m ->
                        val h = m.groupValues[1].toDouble()
                        val min = m.groupValues[2].toDouble()
                        val s = m.groupValues[3].toDouble()
                        pendingClock = h * 3600 + min * 60 + s
                    }
                    val clean = CLOCK.replace(body, "").trim()
                    if (clean.isNotEmpty()) pendingComment = clean
                    attachTo(moves.size - 1)
                    i = end + 1
                }

                c == '(' -> {
                    // Variante : ignoree (on n'analyse que la partie principale).
                    var depth = 1
                    i++
                    while (i < text.length && depth > 0) {
                        if (text[i] == '(') depth++
                        if (text[i] == ')') depth--
                        i++
                    }
                }

                c == ';' -> {
                    val end = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                    i = end
                }

                c == '$' -> {
                    var j = i + 1
                    while (j < text.length && text[j].isDigit()) j++
                    text.substring(i + 1, j).toIntOrNull()?.let { pendingNags.add(it) }
                    attachTo(moves.size - 1)
                    i = j
                }

                c.isDigit() -> {
                    // Numero de coup ("12." / "12...") ou resultat ("1-0", "1/2-1/2").
                    var j = i
                    while (j < text.length && !text[j].isWhitespace()) j++
                    val token = text.substring(i, j)
                    if (token.contains('-') || token.contains('/')) {
                        // resultat : fin de la partie
                    }
                    i = j
                }

                c == '*' -> i++

                else -> {
                    var j = i
                    while (j < text.length && !text[j].isWhitespace() &&
                        text[j] != '{' && text[j] != '(' && text[j] != ';'
                    ) j++
                    val token = text.substring(i, j).trim('.', ',')
                    if (token.isNotEmpty() && token != "--" && !token.startsWith("$")) {
                        moves.add(PgnMove(token))
                    }
                    i = j
                }
            }
        }
        return moves
    }

    /**
     * Rejoue la partie coup par coup. Les coups illisibles arretent la relecture :
     * on garde ce qui a ete valide plutot que d'echouer sur toute la partie.
     */
    fun replay(game: PgnGame): List<PlayedMove> {
        val position = Fen.parse(game.startFen)
        val out = ArrayList<PlayedMove>(game.moves.size)
        // Pendules initiales deduites du controle de temps pour calculer le 1er temps passe.
        val tc = TimeControl.parse(game["TimeControl"])
        val lastClock = doubleArrayOf(
            tc?.baseSeconds?.toDouble() ?: Double.NaN,
            tc?.baseSeconds?.toDouble() ?: Double.NaN,
        )

        for ((index, pgnMove) in game.moves.withIndex()) {
            val mover = position.side
            val move = San.parse(position, pgnMove.san)
            if (move == Move.NONE) break
            val fenBefore = Fen.of(position)
            val isCapture = position.board[Move.to(move)] != com.chessforge.chess.Piece.NONE ||
                Move.flag(move) == Move.FLAG_EN_PASSANT
            val san = San.of(position, move)
            val uci = Move.toUci(move)
            if (!position.makeMove(move)) break
            val fenAfter = Fen.of(position)

            val clock = pgnMove.clockSeconds
            val spent = if (clock != null && !lastClock[mover].isNaN()) {
                (lastClock[mover] - clock + (tc?.incrementSeconds ?: 0)).coerceAtLeast(0.0)
            } else {
                null
            }
            if (clock != null) lastClock[mover] = clock

            out.add(
                PlayedMove(
                    ply = index,
                    san = san,
                    uci = uci,
                    move = move,
                    fenBefore = fenBefore,
                    fenAfter = fenAfter,
                    sideToMove = mover,
                    clockSeconds = clock,
                    secondsSpent = spent,
                    isCapture = isCapture,
                    isCheck = position.isInCheck(),
                )
            )
        }
        return out
    }
}

/** Controle de temps PGN : "600", "300+5", "1/86400" (par correspondance). */
data class TimeControl(val baseSeconds: Int, val incrementSeconds: Int, val isCorrespondence: Boolean) {

    val category: String
        get() = when {
            isCorrespondence -> "Correspondance"
            estimated < 180 -> "Bullet"
            estimated < 600 -> "Blitz"
            estimated < 1800 -> "Rapide"
            else -> "Classique"
        }

    /** Duree estimee d'une partie selon la convention usuelle base + 40 x increment. */
    val estimated: Int get() = baseSeconds + 40 * incrementSeconds

    fun label(): String = when {
        isCorrespondence -> "1 jour/coup"
        incrementSeconds > 0 -> "${baseSeconds / 60}+$incrementSeconds"
        baseSeconds % 60 == 0 -> "${baseSeconds / 60} min"
        else -> "${baseSeconds}s"
    }

    companion object {
        fun parse(raw: String?): TimeControl? {
            if (raw.isNullOrBlank() || raw == "-") return null
            if (raw.startsWith("1/")) {
                val perMove = raw.removePrefix("1/").toIntOrNull() ?: return null
                return TimeControl(perMove, 0, isCorrespondence = true)
            }
            val parts = raw.split("+")
            val base = parts[0].toIntOrNull() ?: return null
            val inc = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return TimeControl(base, inc, isCorrespondence = false)
        }
    }
}
