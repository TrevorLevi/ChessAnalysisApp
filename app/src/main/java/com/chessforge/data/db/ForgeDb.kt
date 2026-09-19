package com.chessforge.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Base locale unique de l'application. Tout reste sur le telephone : aucune donnee
 * n'est envoyee ailleurs que vers l'API publique de chess.com pour telecharger vos parties.
 *
 * SQLite est pilote directement (pas de Room) pour garder le projet sans traitement
 * d'annotations : compilation plus rapide et migrations explicites.
 */
class ForgeDb(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE games (
                id TEXT PRIMARY KEY,
                url TEXT,
                pgn TEXT NOT NULL,
                white TEXT NOT NULL,
                black TEXT NOT NULL,
                whiteElo INTEGER,
                blackElo INTEGER,
                result TEXT NOT NULL,
                userColor INTEGER NOT NULL,
                userOutcome TEXT NOT NULL,
                termination TEXT,
                timeControl TEXT,
                timeClass TEXT,
                eco TEXT,
                openingName TEXT,
                openingFamily TEXT,
                playedAt INTEGER NOT NULL,
                rated INTEGER NOT NULL DEFAULT 1,
                moveCount INTEGER NOT NULL DEFAULT 0,
                userRating INTEGER,
                opponentRating INTEGER,
                analyzedAt INTEGER,
                engineId TEXT,
                analysisDepth INTEGER,
                accuracy REAL,
                acpl REAL,
                opponentAccuracy REAL,
                opponentAcpl REAL,
                blunders INTEGER,
                mistakes INTEGER,
                inaccuracies INTEGER,
                misses INTEGER,
                bestMoves INTEGER,
                openingAcpl REAL,
                middlegameAcpl REAL,
                endgameAcpl REAL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_games_playedAt ON games(playedAt DESC)")
        db.execSQL("CREATE INDEX idx_games_analyzed ON games(analyzedAt)")
        db.execSQL("CREATE INDEX idx_games_family ON games(openingFamily)")

        db.execSQL(
            """
            CREATE TABLE moves (
                gameId TEXT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
                ply INTEGER NOT NULL,
                san TEXT NOT NULL,
                uci TEXT NOT NULL,
                fenBefore TEXT NOT NULL,
                sideToMove INTEGER NOT NULL,
                byUser INTEGER NOT NULL,
                evalBefore INTEGER NOT NULL,
                evalAfter INTEGER NOT NULL,
                mateBefore INTEGER,
                mateAfter INTEGER,
                cpLoss INTEGER NOT NULL,
                winDrop REAL NOT NULL,
                classification TEXT NOT NULL,
                bestUci TEXT,
                bestSan TEXT,
                bestPvSan TEXT,
                phase TEXT NOT NULL,
                motifs TEXT,
                clockSeconds REAL,
                secondsSpent REAL,
                PRIMARY KEY (gameId, ply)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_moves_class ON moves(byUser, classification)")
        db.execSQL("CREATE INDEX idx_moves_phase ON moves(byUser, phase)")

        db.execSQL(
            """
            CREATE TABLE puzzles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                gameId TEXT REFERENCES games(id) ON DELETE CASCADE,
                ply INTEGER NOT NULL,
                fen TEXT NOT NULL,
                solutionUci TEXT NOT NULL,
                solutionSan TEXT NOT NULL,
                kind TEXT NOT NULL,
                motifs TEXT,
                phase TEXT NOT NULL,
                difficulty INTEGER NOT NULL,
                swingCp INTEGER NOT NULL,
                playedSan TEXT,
                userColor INTEGER NOT NULL,
                opponentName TEXT,
                playedAt INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                dueAt INTEGER NOT NULL,
                intervalDays REAL NOT NULL DEFAULT 0,
                ease REAL NOT NULL DEFAULT 2.5,
                reps INTEGER NOT NULL DEFAULT 0,
                lapses INTEGER NOT NULL DEFAULT 0,
                retired INTEGER NOT NULL DEFAULT 0,
                UNIQUE (fen, solutionUci)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_puzzles_due ON puzzles(retired, dueAt)")

        db.execSQL(
            """
            CREATE TABLE puzzle_attempts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                puzzleId INTEGER NOT NULL REFERENCES puzzles(id) ON DELETE CASCADE,
                at INTEGER NOT NULL,
                success INTEGER NOT NULL,
                millis INTEGER NOT NULL,
                firstMoveUci TEXT,
                hintsUsed INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_attempts_at ON puzzle_attempts(at DESC)")

        db.execSQL(
            """
            CREATE TABLE ratings (
                at INTEGER NOT NULL,
                timeClass TEXT NOT NULL,
                rating INTEGER NOT NULL,
                PRIMARY KEY (at, timeClass)
            )
            """.trimIndent()
        )

        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 : rien a migrer pour l'instant. Les futures migrations viendront ici.
    }

    companion object {
        const val NAME = "chessforge.db"
        const val VERSION = 1
    }
}

// --- Petits utilitaires de lecture ------------------------------------------

inline fun <T> SQLiteDatabase.mapQuery(
    sql: String,
    args: Array<String> = emptyArray(),
    map: (Cursor) -> T,
): List<T> {
    val out = ArrayList<T>()
    rawQuery(sql, args).use { c ->
        while (c.moveToNext()) out.add(map(c))
    }
    return out
}

inline fun <T> SQLiteDatabase.firstOrNull(
    sql: String,
    args: Array<String> = emptyArray(),
    map: (Cursor) -> T,
): T? = rawQuery(sql, args).use { c -> if (c.moveToFirst()) map(c) else null }

fun SQLiteDatabase.queryLong(sql: String, args: Array<String> = emptyArray()): Long =
    firstOrNull(sql, args) { if (it.isNull(0)) 0L else it.getLong(0) } ?: 0L

fun SQLiteDatabase.queryDouble(sql: String, args: Array<String> = emptyArray()): Double? =
    firstOrNull(sql, args) { if (it.isNull(0)) null else it.getDouble(0) }

fun Cursor.intOrNull(column: String): Int? {
    val i = getColumnIndexOrThrow(column)
    return if (isNull(i)) null else getInt(i)
}

fun Cursor.doubleOrNull(column: String): Double? {
    val i = getColumnIndexOrThrow(column)
    return if (isNull(i)) null else getDouble(i)
}

fun Cursor.stringOrNull(column: String): String? {
    val i = getColumnIndexOrThrow(column)
    return if (isNull(i)) null else getString(i)
}

fun Cursor.str(column: String): String = getString(getColumnIndexOrThrow(column)) ?: ""
fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
fun Cursor.double(column: String): Double = getDouble(getColumnIndexOrThrow(column))
fun Cursor.bool(column: String): Boolean = getInt(getColumnIndexOrThrow(column)) != 0

fun contentValues(block: ContentValues.() -> Unit): ContentValues = ContentValues().apply(block)
