package com.chessforge.data.db

import com.chessforge.data.model.Classification
import com.chessforge.data.model.Motif
import com.chessforge.data.model.Phase

data class GamePoint(
    val playedAt: Long,
    val accuracy: Double,
    val acpl: Double,
    val opponentAccuracy: Double,
    val outcome: String,
    val timeClass: String?,
    val userRating: Int?,
)

data class OpeningRow(
    val family: String,
    val games: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val avgAcpl: Double,
    val avgAccuracy: Double,
    val asWhite: Int,
) {
    /** Score au sens echiquéen : 1 pour un gain, 0,5 pour un nul. */
    val score: Double get() = if (games == 0) 0.0 else (wins + draws * 0.5) / games
}

data class TimeClassRow(
    val timeClass: String,
    val games: Int,
    val score: Double,
    val avgAccuracy: Double,
    val blundersPerGame: Double,
)

data class PhaseRow(val phase: Phase, val moves: Int, val avgCpLoss: Double, val errors: Int)

data class TimePressureRow(
    val bucketLabel: String,
    val moves: Int,
    val errorRate: Double,
    val avgCpLoss: Double,
)

data class GlobalSummary(
    val games: Int,
    val analyzedGames: Int,
    val moves: Int,
    val avgAccuracy: Double,
    val avgAcpl: Double,
    val blundersPer100: Double,
    val mistakesPer100: Double,
    val inaccuraciesPer100: Double,
    val bestMoveRate: Double,
    val winRate: Double,
    val lastPlayedAt: Long?,
)

class StatsDao(private val helper: ForgeDb) {

    fun summary(): GlobalSummary {
        val db = helper.readableDatabase
        val games = db.queryLong("SELECT COUNT(*) FROM games").toInt()
        val analyzed = db.queryLong("SELECT COUNT(*) FROM games WHERE analyzedAt IS NOT NULL").toInt()
        val moves = db.queryLong("SELECT COUNT(*) FROM moves WHERE byUser = 1").toInt()
        val avgAccuracy = db.queryDouble("SELECT AVG(accuracy) FROM games WHERE accuracy IS NOT NULL") ?: 0.0
        val avgAcpl = db.queryDouble("SELECT AVG(acpl) FROM games WHERE acpl IS NOT NULL") ?: 0.0
        val blunders = db.queryLong("SELECT COUNT(*) FROM moves WHERE byUser = 1 AND classification = 'BLUNDER'")
        val mistakes = db.queryLong("SELECT COUNT(*) FROM moves WHERE byUser = 1 AND classification = 'MISTAKE'")
        val inaccuracies =
            db.queryLong("SELECT COUNT(*) FROM moves WHERE byUser = 1 AND classification = 'INACCURACY'")
        val best = db.queryLong("SELECT COUNT(*) FROM moves WHERE byUser = 1 AND classification IN ('BEST','BRILLIANT','GREAT')")
        val wins = db.queryLong("SELECT COUNT(*) FROM games WHERE userOutcome = 'win'")
        val last = db.queryLong("SELECT MAX(playedAt) FROM games").takeIf { it > 0 }
        val per100 = { n: Long -> if (moves == 0) 0.0 else n * 100.0 / moves }
        return GlobalSummary(
            games = games,
            analyzedGames = analyzed,
            moves = moves,
            avgAccuracy = avgAccuracy,
            avgAcpl = avgAcpl,
            blundersPer100 = per100(blunders),
            mistakesPer100 = per100(mistakes),
            inaccuraciesPer100 = per100(inaccuracies),
            bestMoveRate = if (moves == 0) 0.0 else best.toDouble() / moves,
            winRate = if (games == 0) 0.0 else wins.toDouble() / games,
            lastPlayedAt = last,
        )
    }

    fun accuracyTrend(limit: Int = 60): List<GamePoint> = helper.readableDatabase.mapQuery(
        """
        SELECT playedAt, accuracy, acpl, opponentAccuracy, userOutcome, timeClass, userRating
        FROM games WHERE analyzedAt IS NOT NULL
        ORDER BY playedAt DESC LIMIT $limit
        """.trimIndent()
    ) {
        GamePoint(
            playedAt = it.getLong(0),
            accuracy = if (it.isNull(1)) 0.0 else it.getDouble(1),
            acpl = if (it.isNull(2)) 0.0 else it.getDouble(2),
            opponentAccuracy = if (it.isNull(3)) 0.0 else it.getDouble(3),
            outcome = it.getString(4) ?: "",
            timeClass = it.getString(5),
            userRating = if (it.isNull(6)) null else it.getInt(6),
        )
    }.reversed()

    fun classificationCounts(): Map<Classification, Int> {
        val rows = helper.readableDatabase.mapQuery(
            "SELECT classification, COUNT(*) FROM moves WHERE byUser = 1 GROUP BY classification"
        ) { it.getString(0) to it.getInt(1) }
        return rows.associate { Classification.fromName(it.first) to it.second }
    }

    fun phaseRows(): List<PhaseRow> = Phase.entries.map { phase ->
        val db = helper.readableDatabase
        val args = arrayOf(phase.name)
        val moves = db.queryLong("SELECT COUNT(*) FROM moves WHERE byUser = 1 AND phase = ?", args).toInt()
        val avg = db.queryDouble("SELECT AVG(cpLoss) FROM moves WHERE byUser = 1 AND phase = ?", args) ?: 0.0
        val errors = db.queryLong(
            """
            SELECT COUNT(*) FROM moves
            WHERE byUser = 1 AND phase = ? AND classification IN ('INACCURACY','MISTAKE','BLUNDER','MISS')
            """.trimIndent(), args
        ).toInt()
        PhaseRow(phase, moves, avg, errors)
    }

    /** Motifs presents dans les coups fautifs : ce qui vous coute reellement des points. */
    fun motifErrorCounts(): Map<Motif, Int> {
        val db = helper.readableDatabase
        val out = LinkedHashMap<Motif, Int>()
        for (motif in Motif.entries) {
            val n = db.queryLong(
                """
                SELECT COUNT(*) FROM moves
                WHERE byUser = 1 AND motifs LIKE ?
                  AND classification IN ('MISTAKE','BLUNDER','MISS')
                """.trimIndent(),
                arrayOf("%${motif.name}%"),
            ).toInt()
            if (n > 0) out[motif] = n
        }
        return out.entries.sortedByDescending { it.value }.associate { it.key to it.value }
    }

    fun openingRows(minGames: Int = 2, limit: Int = 24): List<OpeningRow> =
        helper.readableDatabase.mapQuery(
            """
            SELECT COALESCE(openingFamily, 'Inconnue') AS fam,
                   COUNT(*) AS n,
                   SUM(CASE WHEN userOutcome = 'win' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN userOutcome = 'draw' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN userOutcome = 'loss' THEN 1 ELSE 0 END),
                   AVG(acpl), AVG(accuracy),
                   SUM(CASE WHEN userColor = 0 THEN 1 ELSE 0 END)
            FROM games
            GROUP BY fam
            HAVING n >= $minGames
            ORDER BY n DESC
            LIMIT $limit
            """.trimIndent()
        ) {
            OpeningRow(
                family = it.getString(0),
                games = it.getInt(1),
                wins = it.getInt(2),
                draws = it.getInt(3),
                losses = it.getInt(4),
                avgAcpl = if (it.isNull(5)) 0.0 else it.getDouble(5),
                avgAccuracy = if (it.isNull(6)) 0.0 else it.getDouble(6),
                asWhite = it.getInt(7),
            )
        }

    fun timeClassRows(): List<TimeClassRow> = helper.readableDatabase.mapQuery(
        """
        SELECT COALESCE(timeClass, 'inconnu') AS tc,
               COUNT(*),
               AVG(CASE userOutcome WHEN 'win' THEN 1.0 WHEN 'draw' THEN 0.5 ELSE 0.0 END),
               AVG(accuracy),
               AVG(COALESCE(blunders, 0))
        FROM games GROUP BY tc ORDER BY COUNT(*) DESC
        """.trimIndent()
    ) {
        TimeClassRow(
            timeClass = it.getString(0),
            games = it.getInt(1),
            score = if (it.isNull(2)) 0.0 else it.getDouble(2),
            avgAccuracy = if (it.isNull(3)) 0.0 else it.getDouble(3),
            blundersPerGame = if (it.isNull(4)) 0.0 else it.getDouble(4),
        )
    }

    /**
     * Gestion du temps : taux d'erreur selon le temps reellement passe sur le coup.
     * Les deux extremes sont revelateurs — trop vite (reflexe) et trop lent (doute).
     */
    fun timePressureRows(): List<TimePressureRow> {
        val buckets = listOf(
            Triple("< 2 s", 0.0, 2.0),
            Triple("2-5 s", 2.0, 5.0),
            Triple("5-15 s", 5.0, 15.0),
            Triple("15-40 s", 15.0, 40.0),
            Triple("> 40 s", 40.0, 100_000.0),
        )
        val db = helper.readableDatabase
        return buckets.mapNotNull { (label, lo, hi) ->
            val args = arrayOf(lo.toString(), hi.toString())
            val moves = db.queryLong(
                "SELECT COUNT(*) FROM moves WHERE byUser = 1 AND secondsSpent >= ? AND secondsSpent < ?", args
            ).toInt()
            if (moves < 5) return@mapNotNull null
            val errors = db.queryLong(
                """
                SELECT COUNT(*) FROM moves
                WHERE byUser = 1 AND secondsSpent >= ? AND secondsSpent < ?
                  AND classification IN ('MISTAKE','BLUNDER','MISS')
                """.trimIndent(), args
            ).toInt()
            val avg = db.queryDouble(
                "SELECT AVG(cpLoss) FROM moves WHERE byUser = 1 AND secondsSpent >= ? AND secondsSpent < ?", args
            ) ?: 0.0
            TimePressureRow(label, moves, errors.toDouble() / moves, avg)
        }
    }

    /** Erreurs par tranche de numero de coup : montre a quel moment la partie derape. */
    fun errorsByMoveNumber(bucketSize: Int = 5, buckets: Int = 12): List<Pair<String, Double>> {
        val db = helper.readableDatabase
        return (0 until buckets).mapNotNull { b ->
            val loPly = b * bucketSize * 2
            val hiPly = (b + 1) * bucketSize * 2
            val args = arrayOf(loPly.toString(), hiPly.toString())
            val moves = db.queryLong(
                "SELECT COUNT(*) FROM moves WHERE byUser = 1 AND ply >= ? AND ply < ?", args
            ).toInt()
            if (moves < 5) return@mapNotNull null
            val errors = db.queryLong(
                """
                SELECT COUNT(*) FROM moves WHERE byUser = 1 AND ply >= ? AND ply < ?
                  AND classification IN ('MISTAKE','BLUNDER','MISS')
                """.trimIndent(), args
            ).toInt()
            "${b * bucketSize + 1}-${(b + 1) * bucketSize}" to errors.toDouble() / moves
        }
    }

    /**
     * Carte de chaleur des cases d'arrivee de vos gaffes : revele les zones du plateau
     * ou votre attention retombe (souvent l'aile ou vous ne regardez pas).
     */
    fun blunderHeatmap(): IntArray {
        val heat = IntArray(64)
        helper.readableDatabase.mapQuery(
            """
            SELECT uci FROM moves
            WHERE byUser = 1 AND classification IN ('BLUNDER','MISTAKE')
            """.trimIndent()
        ) { it.getString(0) }.forEach { uci ->
            if (uci.length >= 4) {
                val sq = com.chessforge.chess.Square.fromName(uci.substring(2, 4))
                if (sq >= 0) heat[sq]++
            }
        }
        return heat
    }

    fun colorSplit(): Pair<OpeningRow, OpeningRow> {
        fun row(color: Int, label: String): OpeningRow {
            val db = helper.readableDatabase
            val args = arrayOf(color.toString())
            val games = db.queryLong("SELECT COUNT(*) FROM games WHERE userColor = ?", args).toInt()
            val wins = db.queryLong("SELECT COUNT(*) FROM games WHERE userColor = ? AND userOutcome = 'win'", args).toInt()
            val draws = db.queryLong("SELECT COUNT(*) FROM games WHERE userColor = ? AND userOutcome = 'draw'", args).toInt()
            val losses = db.queryLong("SELECT COUNT(*) FROM games WHERE userColor = ? AND userOutcome = 'loss'", args).toInt()
            val acpl = db.queryDouble("SELECT AVG(acpl) FROM games WHERE userColor = ?", args) ?: 0.0
            val acc = db.queryDouble("SELECT AVG(accuracy) FROM games WHERE userColor = ?", args) ?: 0.0
            return OpeningRow(label, games, wins, draws, losses, acpl, acc, if (color == 0) games else 0)
        }
        return row(0, "Blancs") to row(1, "Noirs")
    }
}
