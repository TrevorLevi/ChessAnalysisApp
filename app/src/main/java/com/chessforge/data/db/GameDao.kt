package com.chessforge.data.db

import android.database.Cursor
import com.chessforge.data.model.Classification
import com.chessforge.data.model.GameAnalysisSummary
import com.chessforge.data.model.GameRecord
import com.chessforge.data.model.Motif
import com.chessforge.data.model.MoveRecord
import com.chessforge.data.model.Phase
import com.chessforge.data.model.RatingPoint

/** Filtre de la liste des parties. */
data class GameFilter(
    val timeClass: String? = null,
    val color: Int? = null,
    val outcome: String? = null,
    val openingFamily: String? = null,
    val analyzedOnly: Boolean = false,
    val search: String? = null,
    val limit: Int = 200,
)

class GameDao(private val helper: ForgeDb) {

    fun upsert(game: GameRecord) {
        val db = helper.writableDatabase
        db.insertWithOnConflict(
            "games", null,
            contentValues {
                put("id", game.id)
                put("url", game.url)
                put("pgn", game.pgn)
                put("white", game.white)
                put("black", game.black)
                put("whiteElo", game.whiteElo)
                put("blackElo", game.blackElo)
                put("result", game.result)
                put("userColor", game.userColor)
                put("userOutcome", game.userOutcome)
                put("termination", game.termination)
                put("timeControl", game.timeControl)
                put("timeClass", game.timeClass)
                put("eco", game.eco)
                put("openingName", game.openingName)
                put("openingFamily", game.openingFamily)
                put("playedAt", game.playedAt)
                put("rated", if (game.rated) 1 else 0)
                put("moveCount", game.moveCount)
                put("userRating", game.userRating)
                put("opponentRating", game.opponentRating)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    fun existingIds(): Set<String> =
        helper.readableDatabase.mapQuery("SELECT id FROM games") { it.getString(0) }.toSet()

    fun count(): Int = helper.readableDatabase.queryLong("SELECT COUNT(*) FROM games").toInt()

    fun countAnalyzed(): Int =
        helper.readableDatabase.queryLong("SELECT COUNT(*) FROM games WHERE analyzedAt IS NOT NULL").toInt()

    fun get(id: String): GameRecord? = helper.readableDatabase.firstOrNull(
        "SELECT * FROM games WHERE id = ?", arrayOf(id)
    ) { readGame(it) }

    fun list(filter: GameFilter = GameFilter()): List<GameRecord> {
        val where = ArrayList<String>()
        val args = ArrayList<String>()
        filter.timeClass?.let { where.add("timeClass = ?"); args.add(it) }
        filter.color?.let { where.add("userColor = ?"); args.add(it.toString()) }
        filter.outcome?.let { where.add("userOutcome = ?"); args.add(it) }
        filter.openingFamily?.let { where.add("openingFamily = ?"); args.add(it) }
        if (filter.analyzedOnly) where.add("analyzedAt IS NOT NULL")
        filter.search?.takeIf { it.isNotBlank() }?.let {
            where.add("(white LIKE ? OR black LIKE ? OR openingName LIKE ?)")
            val like = "%$it%"
            args.add(like); args.add(like); args.add(like)
        }
        val clause = if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")
        return helper.readableDatabase.mapQuery(
            "SELECT * FROM games $clause ORDER BY playedAt DESC LIMIT ${filter.limit}",
            args.toTypedArray(),
        ) { readGame(it) }
    }

    /** Parties telechargees mais pas encore analysees, les plus recentes d'abord. */
    fun pendingAnalysis(limit: Int): List<GameRecord> = helper.readableDatabase.mapQuery(
        "SELECT * FROM games WHERE analyzedAt IS NULL ORDER BY playedAt DESC LIMIT $limit"
    ) { readGame(it) }

    fun countPendingAnalysis(): Int =
        helper.readableDatabase.queryLong("SELECT COUNT(*) FROM games WHERE analyzedAt IS NULL").toInt()

    fun saveAnalysis(gameId: String, summary: GameAnalysisSummary, moves: List<MoveRecord>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("moves", "gameId = ?", arrayOf(gameId))
            for (m in moves) {
                db.insert(
                    "moves", null,
                    contentValues {
                        put("gameId", m.gameId)
                        put("ply", m.ply)
                        put("san", m.san)
                        put("uci", m.uci)
                        put("fenBefore", m.fenBefore)
                        put("sideToMove", m.sideToMove)
                        put("byUser", if (m.byUser) 1 else 0)
                        put("evalBefore", m.evalBefore)
                        put("evalAfter", m.evalAfter)
                        put("mateBefore", m.mateBefore)
                        put("mateAfter", m.mateAfter)
                        put("cpLoss", m.cpLoss)
                        put("winDrop", m.winDrop)
                        put("classification", m.classification.name)
                        put("bestUci", m.bestUci)
                        put("bestSan", m.bestSan)
                        put("bestPvSan", m.bestPvSan)
                        put("phase", m.phase.name)
                        put("motifs", Motif.encode(m.motifs))
                        put("clockSeconds", m.clockSeconds)
                        put("secondsSpent", m.secondsSpent)
                    },
                )
            }
            db.update(
                "games",
                contentValues {
                    put("analyzedAt", summary.analyzedAt)
                    put("engineId", summary.engineId)
                    put("analysisDepth", summary.depth)
                    put("accuracy", summary.accuracy)
                    put("acpl", summary.acpl)
                    put("opponentAccuracy", summary.opponentAccuracy)
                    put("opponentAcpl", summary.opponentAcpl)
                    put("blunders", summary.blunders)
                    put("mistakes", summary.mistakes)
                    put("inaccuracies", summary.inaccuracies)
                    put("misses", summary.misses)
                    put("bestMoves", summary.bestMoves)
                    put("openingAcpl", summary.openingAcpl)
                    put("middlegameAcpl", summary.middlegameAcpl)
                    put("endgameAcpl", summary.endgameAcpl)
                },
                "id = ?", arrayOf(gameId),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun moves(gameId: String): List<MoveRecord> = helper.readableDatabase.mapQuery(
        "SELECT * FROM moves WHERE gameId = ? ORDER BY ply", arrayOf(gameId)
    ) { readMove(it) }

    /** Coups fautifs de l'utilisateur, candidats a la generation de puzzles. */
    fun userErrorMoves(limit: Int = 500, minWinDrop: Double = 10.0): List<MoveRecord> =
        helper.readableDatabase.mapQuery(
            """
            SELECT m.* FROM moves m
            JOIN games g ON g.id = m.gameId
            WHERE m.byUser = 1 AND m.winDrop >= ? AND m.bestUci IS NOT NULL
            ORDER BY g.playedAt DESC, m.winDrop DESC
            LIMIT $limit
            """.trimIndent(),
            arrayOf(minWinDrop.toString()),
        ) { readMove(it) }

    fun deleteAllGames() {
        val db = helper.writableDatabase
        db.delete("moves", null, null)
        db.delete("games", null, null)
    }

    // --- Notations chess.com -------------------------------------------------

    fun putRating(point: RatingPoint) {
        helper.writableDatabase.insertWithOnConflict(
            "ratings", null,
            contentValues {
                put("at", point.at)
                put("timeClass", point.timeClass)
                put("rating", point.rating)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun ratings(timeClass: String? = null, limit: Int = 400): List<RatingPoint> {
        val clause = if (timeClass == null) "" else "WHERE timeClass = ?"
        val args = if (timeClass == null) emptyArray() else arrayOf(timeClass)
        return helper.readableDatabase.mapQuery(
            "SELECT at, timeClass, rating FROM ratings $clause ORDER BY at DESC LIMIT $limit", args
        ) { RatingPoint(it.getLong(0), it.getString(1), it.getInt(2)) }.reversed()
    }

    // --- Cle/valeur ---------------------------------------------------------

    fun meta(key: String): String? = helper.readableDatabase.firstOrNull(
        "SELECT value FROM meta WHERE key = ?", arrayOf(key)
    ) { it.getString(0) }

    fun putMeta(key: String, value: String) {
        helper.writableDatabase.insertWithOnConflict(
            "meta", null,
            contentValues { put("key", key); put("value", value) },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    // --- Lecture de curseurs ------------------------------------------------

    private fun readGame(c: Cursor): GameRecord {
        val analyzedAt = c.long2("analyzedAt")
        return GameRecord(
            id = c.str("id"),
            url = c.stringOrNull("url"),
            pgn = c.str("pgn"),
            white = c.str("white"),
            black = c.str("black"),
            whiteElo = c.intOrNull("whiteElo"),
            blackElo = c.intOrNull("blackElo"),
            result = c.str("result"),
            userColor = c.int("userColor"),
            userOutcome = c.str("userOutcome"),
            termination = c.stringOrNull("termination"),
            timeControl = c.stringOrNull("timeControl"),
            timeClass = c.stringOrNull("timeClass"),
            eco = c.stringOrNull("eco"),
            openingName = c.stringOrNull("openingName"),
            openingFamily = c.stringOrNull("openingFamily"),
            playedAt = c.long("playedAt"),
            rated = c.bool("rated"),
            moveCount = c.int("moveCount"),
            userRating = c.intOrNull("userRating"),
            opponentRating = c.intOrNull("opponentRating"),
            analysis = if (analyzedAt == null) null else GameAnalysisSummary(
                analyzedAt = analyzedAt,
                engineId = c.stringOrNull("engineId") ?: "?",
                depth = c.intOrNull("analysisDepth") ?: 0,
                accuracy = c.doubleOrNull("accuracy") ?: 0.0,
                acpl = c.doubleOrNull("acpl") ?: 0.0,
                opponentAccuracy = c.doubleOrNull("opponentAccuracy") ?: 0.0,
                opponentAcpl = c.doubleOrNull("opponentAcpl") ?: 0.0,
                blunders = c.intOrNull("blunders") ?: 0,
                mistakes = c.intOrNull("mistakes") ?: 0,
                inaccuracies = c.intOrNull("inaccuracies") ?: 0,
                misses = c.intOrNull("misses") ?: 0,
                bestMoves = c.intOrNull("bestMoves") ?: 0,
                openingAcpl = c.doubleOrNull("openingAcpl") ?: 0.0,
                middlegameAcpl = c.doubleOrNull("middlegameAcpl") ?: 0.0,
                endgameAcpl = c.doubleOrNull("endgameAcpl") ?: 0.0,
            ),
        )
    }

    private fun readMove(c: Cursor) = MoveRecord(
        gameId = c.str("gameId"),
        ply = c.int("ply"),
        san = c.str("san"),
        uci = c.str("uci"),
        fenBefore = c.str("fenBefore"),
        sideToMove = c.int("sideToMove"),
        byUser = c.bool("byUser"),
        evalBefore = c.int("evalBefore"),
        evalAfter = c.int("evalAfter"),
        mateBefore = c.intOrNull("mateBefore"),
        mateAfter = c.intOrNull("mateAfter"),
        cpLoss = c.int("cpLoss"),
        winDrop = c.double("winDrop"),
        classification = Classification.fromName(c.str("classification")),
        bestUci = c.stringOrNull("bestUci"),
        bestSan = c.stringOrNull("bestSan"),
        bestPvSan = c.stringOrNull("bestPvSan"),
        phase = Phase.fromName(c.str("phase")),
        motifs = Motif.parseList(c.stringOrNull("motifs")),
        clockSeconds = c.doubleOrNull("clockSeconds"),
        secondsSpent = c.doubleOrNull("secondsSpent"),
    )
}

private fun Cursor.long2(column: String): Long? {
    val i = getColumnIndexOrThrow(column)
    return if (isNull(i)) null else getLong(i)
}
