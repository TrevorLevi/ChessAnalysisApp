package com.chessforge.data.db

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.chessforge.data.model.Motif
import com.chessforge.data.model.Phase
import com.chessforge.data.model.Puzzle
import com.chessforge.data.model.PuzzleAttempt
import com.chessforge.data.model.PuzzleKind
import com.chessforge.data.model.SrsState

data class PuzzleQuery(
    val dueOnly: Boolean = false,
    val motif: Motif? = null,
    val kind: PuzzleKind? = null,
    val phase: Phase? = null,
    val neverAttempted: Boolean = false,
    val now: Long = System.currentTimeMillis() / 1000,
    val limit: Int = 30,
)

data class PuzzleProgress(
    val total: Int,
    val due: Int,
    val attempted: Int,
    val solvedToday: Int,
    val successRate: Double,
    val streakDays: Int,
    val medianSeconds: Double,
)

data class MotifScore(
    val motif: Motif,
    val attempts: Int,
    val successes: Int,
    val occurrencesInGames: Int,
) {
    val successRate: Double get() = if (attempts == 0) 0.0 else successes.toDouble() / attempts
}

class PuzzleDao(private val helper: ForgeDb) {

    /** Renvoie l'identifiant insere, ou -1 si le puzzle existe deja (contrainte FEN + solution). */
    fun insert(puzzle: Puzzle): Long = helper.writableDatabase.insertWithOnConflict(
        "puzzles", null,
        contentValues {
            put("gameId", puzzle.gameId)
            put("ply", puzzle.ply)
            put("fen", puzzle.fen)
            put("solutionUci", puzzle.solutionUci.joinToString(" "))
            put("solutionSan", puzzle.solutionSan.joinToString(" "))
            put("kind", puzzle.kind.name)
            put("motifs", Motif.encode(puzzle.motifs))
            put("phase", puzzle.phase.name)
            put("difficulty", puzzle.difficulty)
            put("swingCp", puzzle.swingCp)
            put("playedSan", puzzle.playedSan)
            put("userColor", puzzle.userColor)
            put("opponentName", puzzle.opponentName)
            put("playedAt", puzzle.playedAt)
            put("createdAt", puzzle.createdAt)
            put("dueAt", puzzle.srs.dueAt)
            put("intervalDays", puzzle.srs.intervalDays)
            put("ease", puzzle.srs.ease)
            put("reps", puzzle.srs.reps)
            put("lapses", puzzle.srs.lapses)
            put("retired", if (puzzle.srs.retired) 1 else 0)
        },
        SQLiteDatabase.CONFLICT_IGNORE,
    )

    fun get(id: Long): Puzzle? = helper.readableDatabase.firstOrNull(
        "SELECT * FROM puzzles WHERE id = ?", arrayOf(id.toString())
    ) { readPuzzle(it) }

    fun query(q: PuzzleQuery): List<Puzzle> {
        val where = arrayListOf("retired = 0")
        val args = ArrayList<String>()
        if (q.dueOnly) {
            where.add("dueAt <= ?")
            args.add(q.now.toString())
        }
        if (q.neverAttempted) where.add("reps = 0")
        q.motif?.let {
            where.add("motifs LIKE ?")
            args.add("%${it.name}%")
        }
        q.kind?.let {
            where.add("kind = ?")
            args.add(it.name)
        }
        q.phase?.let {
            where.add("phase = ?")
            args.add(it.name)
        }
        // Ordre : ce qui est du en premier, puis les plus gros ecarts d'evaluation
        // (les erreurs les plus couteuses sont les plus rentables a corriger).
        return helper.readableDatabase.mapQuery(
            """
            SELECT * FROM puzzles
            WHERE ${where.joinToString(" AND ")}
            ORDER BY dueAt ASC, swingCp DESC
            LIMIT ${q.limit}
            """.trimIndent(),
            args.toTypedArray(),
        ) { readPuzzle(it) }
    }

    fun count(): Int = helper.readableDatabase.queryLong("SELECT COUNT(*) FROM puzzles WHERE retired = 0").toInt()

    fun countDue(now: Long = System.currentTimeMillis() / 1000): Int = helper.readableDatabase
        .queryLong("SELECT COUNT(*) FROM puzzles WHERE retired = 0 AND dueAt <= ?", arrayOf(now.toString()))
        .toInt()

    fun countByMotif(): Map<Motif, Int> {
        val out = LinkedHashMap<Motif, Int>()
        for (motif in Motif.entries) {
            val n = helper.readableDatabase.queryLong(
                "SELECT COUNT(*) FROM puzzles WHERE retired = 0 AND motifs LIKE ?",
                arrayOf("%${motif.name}%"),
            ).toInt()
            if (n > 0) out[motif] = n
        }
        return out
    }

    fun recordAttempt(attempt: PuzzleAttempt, next: SrsState) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.insert(
                "puzzle_attempts", null,
                contentValues {
                    put("puzzleId", attempt.puzzleId)
                    put("at", attempt.at)
                    put("success", if (attempt.success) 1 else 0)
                    put("millis", attempt.millis)
                    put("firstMoveUci", attempt.firstMoveUci)
                    put("hintsUsed", attempt.hintsUsed)
                },
            )
            db.update(
                "puzzles",
                contentValues {
                    put("dueAt", next.dueAt)
                    put("intervalDays", next.intervalDays)
                    put("ease", next.ease)
                    put("reps", next.reps)
                    put("lapses", next.lapses)
                    put("retired", if (next.retired) 1 else 0)
                },
                "id = ?", arrayOf(attempt.puzzleId.toString()),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun progress(now: Long = System.currentTimeMillis() / 1000): PuzzleProgress {
        val db = helper.readableDatabase
        val total = count()
        val due = countDue(now)
        val attempted = db.queryLong("SELECT COUNT(*) FROM puzzles WHERE reps > 0").toInt()
        val dayStart = now - (now % 86_400)
        val solvedToday = db.queryLong(
            "SELECT COUNT(*) FROM puzzle_attempts WHERE at >= ?", arrayOf(dayStart.toString())
        ).toInt()
        val successRate = db.queryDouble(
            "SELECT AVG(success) FROM (SELECT success FROM puzzle_attempts ORDER BY at DESC LIMIT 50)"
        ) ?: 0.0
        val median = db.queryDouble(
            """
            SELECT AVG(millis) / 1000.0 FROM (
              SELECT millis FROM puzzle_attempts ORDER BY at DESC LIMIT 30
            )
            """.trimIndent()
        ) ?: 0.0
        return PuzzleProgress(total, due, attempted, solvedToday, successRate, streakDays(now), median)
    }

    /** Nombre de jours consecutifs (jusqu'a aujourd'hui) avec au moins une tentative. */
    private fun streakDays(now: Long): Int {
        val days = helper.readableDatabase.mapQuery(
            "SELECT DISTINCT at / 86400 FROM puzzle_attempts ORDER BY 1 DESC LIMIT 400"
        ) { it.getLong(0) }.toSet()
        if (days.isEmpty()) return 0
        var day = now / 86_400
        // Une seance non encore faite aujourd'hui ne casse pas la serie de la veille.
        if (day !in days) day -= 1
        var streak = 0
        while (day in days) {
            streak++
            day--
        }
        return streak
    }

    fun motifScores(): List<MotifScore> {
        val db = helper.readableDatabase
        return Motif.entries.mapNotNull { motif ->
            val like = arrayOf("%${motif.name}%")
            val attempts = db.queryLong(
                """
                SELECT COUNT(*) FROM puzzle_attempts a
                JOIN puzzles p ON p.id = a.puzzleId
                WHERE p.motifs LIKE ?
                """.trimIndent(), like
            ).toInt()
            val successes = db.queryLong(
                """
                SELECT COUNT(*) FROM puzzle_attempts a
                JOIN puzzles p ON p.id = a.puzzleId
                WHERE p.motifs LIKE ? AND a.success = 1
                """.trimIndent(), like
            ).toInt()
            val inGames = db.queryLong(
                "SELECT COUNT(*) FROM moves WHERE byUser = 1 AND motifs LIKE ?", like
            ).toInt()
            if (attempts == 0 && inGames == 0) null
            else MotifScore(motif, attempts, successes, inGames)
        }
    }

    fun retire(id: Long) {
        helper.writableDatabase.update(
            "puzzles", contentValues { put("retired", 1) }, "id = ?", arrayOf(id.toString())
        )
    }

    fun attemptHistory(limit: Int = 200): List<PuzzleAttempt> = helper.readableDatabase.mapQuery(
        "SELECT * FROM puzzle_attempts ORDER BY at DESC LIMIT $limit"
    ) {
        PuzzleAttempt(
            puzzleId = it.long("puzzleId"),
            at = it.long("at"),
            success = it.bool("success"),
            millis = it.long("millis"),
            firstMoveUci = it.stringOrNull("firstMoveUci"),
            hintsUsed = it.int("hintsUsed"),
        )
    }

    fun deleteAll() {
        val db = helper.writableDatabase
        db.delete("puzzle_attempts", null, null)
        db.delete("puzzles", null, null)
    }

    private fun readPuzzle(c: Cursor) = Puzzle(
        id = c.long("id"),
        gameId = c.stringOrNull("gameId"),
        ply = c.int("ply"),
        fen = c.str("fen"),
        solutionUci = c.str("solutionUci").split(" ").filter { it.isNotBlank() },
        solutionSan = c.str("solutionSan").split(" ").filter { it.isNotBlank() },
        kind = PuzzleKind.fromName(c.str("kind")),
        motifs = Motif.parseList(c.stringOrNull("motifs")),
        phase = Phase.fromName(c.str("phase")),
        difficulty = c.int("difficulty"),
        swingCp = c.int("swingCp"),
        playedSan = c.stringOrNull("playedSan"),
        userColor = c.int("userColor"),
        opponentName = c.stringOrNull("opponentName"),
        playedAt = c.long("playedAt"),
        createdAt = c.long("createdAt"),
        srs = SrsState(
            dueAt = c.long("dueAt"),
            intervalDays = c.double("intervalDays"),
            ease = c.double("ease"),
            reps = c.int("reps"),
            lapses = c.int("lapses"),
            retired = c.bool("retired"),
        ),
    )
}
