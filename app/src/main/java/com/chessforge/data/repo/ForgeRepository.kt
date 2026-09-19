package com.chessforge.data.repo

import android.util.Log
import com.chessforge.analysis.AnalysisProgress
import com.chessforge.analysis.GameAnalyzer
import com.chessforge.analysis.Openings
import com.chessforge.analysis.PuzzleGenerator
import com.chessforge.analysis.TrainingPlan
import com.chessforge.analysis.TrainingPlanBuilder
import com.chessforge.analysis.Weakness
import com.chessforge.analysis.WeaknessEngine
import com.chessforge.analysis.WeaknessInput
import com.chessforge.chess.Piece
import com.chessforge.chess.pgn.PgnParser
import com.chessforge.chess.pgn.TimeControl
import com.chessforge.data.db.GameDao
import com.chessforge.data.db.GameFilter
import com.chessforge.data.db.GamePoint
import com.chessforge.data.db.GlobalSummary
import com.chessforge.data.db.OpeningRow
import com.chessforge.data.db.PhaseRow
import com.chessforge.data.db.PuzzleDao
import com.chessforge.data.db.PuzzleProgress
import com.chessforge.data.db.PuzzleQuery
import com.chessforge.data.db.StatsDao
import com.chessforge.data.db.TimeClassRow
import com.chessforge.data.db.TimePressureRow
import com.chessforge.data.model.Classification
import com.chessforge.data.model.GameRecord
import com.chessforge.data.model.Motif
import com.chessforge.data.model.MoveRecord
import com.chessforge.data.model.Puzzle
import com.chessforge.data.model.PuzzleAttempt
import com.chessforge.data.model.PuzzleKind
import com.chessforge.data.model.RatingPoint
import com.chessforge.data.model.SrsState
import com.chessforge.data.prefs.Settings
import com.chessforge.data.remote.ChessComClient
import com.chessforge.data.remote.ChessComException
import com.chessforge.data.remote.ChessComGame
import com.chessforge.engine.EngineProvider
import com.chessforge.srs.Srs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SyncOutcome(val imported: Int, val skipped: Int, val error: String? = null)

data class AnalyzeOutcome(
    val analyzedGames: Int,
    val puzzlesCreated: Int,
    val error: String? = null,
)

data class DashboardData(
    val summary: GlobalSummary,
    val trend: List<GamePoint>,
    val classifications: Map<Classification, Int>,
    val phases: List<PhaseRow>,
    val motifErrors: Map<Motif, Int>,
    val openings: List<OpeningRow>,
    val timeClasses: List<TimeClassRow>,
    val timePressure: List<TimePressureRow>,
    val puzzleProgress: PuzzleProgress,
    val weaknesses: List<Weakness>,
    val heatmap: IntArray,
    val ratings: List<RatingPoint>,
    val plan: TrainingPlan,
    val pendingAnalysis: Int,
    val engineName: String,
)

/**
 * Point d'entree unique de la logique metier : import chess.com, analyse, generation
 * de puzzles, agregats du tableau de bord.
 */
class ForgeRepository(
    private val gameDao: GameDao,
    private val puzzleDao: PuzzleDao,
    private val statsDao: StatsDao,
    private val client: ChessComClient,
    private val settings: Settings,
    private val engines: EngineProvider,
) {

    // --- Import -------------------------------------------------------------

    suspend fun verifyUsername(username: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching { client.profile(username).username }
    }

    /**
     * Telecharge les parties des [SettingsData.syncMonths] derniers mois et ignore
     * celles deja presentes. Les archives chess.com sont mensuelles : on part de la
     * plus recente pour que la premiere synchro donne tout de suite du contenu.
     */
    suspend fun syncGames(onProgress: (String) -> Unit = {}): SyncOutcome = withContext(Dispatchers.IO) {
        val config = settings.current
        val username = config.username.trim()
        if (username.isBlank()) return@withContext SyncOutcome(0, 0, "Aucun pseudo chess.com configure.")

        try {
            onProgress("Lecture des archives de $username...")
            val archives = client.archives(username)
            if (archives.isEmpty()) return@withContext SyncOutcome(0, 0, "Aucune archive trouvee pour ce pseudo.")

            val selected = archives.takeLast(config.syncMonths.coerceIn(1, 60))
            val known = gameDao.existingIds()
            var imported = 0
            var skipped = 0

            for ((index, archive) in selected.withIndex().reversed()) {
                val month = archive.takeLast(7).replace('/', '-')
                onProgress("Mois $month (${index + 1}/${selected.size})...")
                val games = client.games(archive)
                for (raw in games) {
                    if (raw.rules != null && raw.rules != "chess") {
                        skipped++
                        continue
                    }
                    if (!config.includeUnrated && !raw.rated) {
                        skipped++
                        continue
                    }
                    val id = raw.url.ifBlank { "${raw.endTime}-${raw.whiteUsername}-${raw.blackUsername}" }
                    if (id in known) {
                        skipped++
                        continue
                    }
                    val record = toRecord(username, id, raw)
                    if (record == null) {
                        skipped++
                        continue
                    }
                    gameDao.upsert(record)
                    record.userRating?.let { rating ->
                        gameDao.putRating(RatingPoint(record.playedAt, record.timeClass ?: "inconnu", rating))
                    }
                    imported++
                }
            }

            runCatching { client.currentRatings(username) }.getOrNull()?.forEach { (timeClass, rating) ->
                gameDao.putRating(RatingPoint(System.currentTimeMillis() / 1000, timeClass, rating))
            }

            settings.update { it.copy(lastSyncAt = System.currentTimeMillis() / 1000) }
            SyncOutcome(imported, skipped)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ChessComException) {
            SyncOutcome(0, 0, e.message)
        } catch (e: Exception) {
            Log.e(TAG, "Echec de synchronisation", e)
            SyncOutcome(0, 0, "Erreur inattendue : ${e.message}")
        }
    }

    private fun toRecord(username: String, id: String, raw: ChessComGame): GameRecord? {
        val pgn = PgnParser.parse(raw.pgn) ?: return null
        val userIsWhite = raw.whiteUsername.equals(username, ignoreCase = true)
        val userIsBlack = raw.blackUsername.equals(username, ignoreCase = true)
        if (!userIsWhite && !userIsBlack) return null
        val userColor = if (userIsWhite) Piece.WHITE else Piece.BLACK

        val result = when {
            raw.whiteResult == "win" -> "1-0"
            raw.blackResult == "win" -> "0-1"
            else -> "1/2-1/2"
        }
        val userOutcome = when {
            result == "1/2-1/2" -> "draw"
            (result == "1-0") == userIsWhite -> "win"
            else -> "loss"
        }

        val openingName = Openings.nameFromEcoUrl(pgn["ECOUrl"]) ?: pgn["Opening"]
        val family = Openings.resolveFamily(pgn["ECOUrl"], raw.eco ?: pgn["ECO"])
        val timeClass = raw.timeClass
            ?: TimeControl.parse(raw.timeControl ?: pgn["TimeControl"])?.category?.lowercase()

        return GameRecord(
            id = id,
            url = raw.url.ifBlank { null },
            pgn = raw.pgn,
            white = raw.whiteUsername.ifBlank { pgn["White"] ?: "?" },
            black = raw.blackUsername.ifBlank { pgn["Black"] ?: "?" },
            whiteElo = raw.whiteRating ?: pgn["WhiteElo"]?.toIntOrNull(),
            blackElo = raw.blackRating ?: pgn["BlackElo"]?.toIntOrNull(),
            result = result,
            userColor = userColor,
            userOutcome = userOutcome,
            termination = pgn["Termination"],
            timeControl = raw.timeControl ?: pgn["TimeControl"],
            timeClass = timeClass,
            eco = raw.eco ?: pgn["ECO"],
            openingName = openingName,
            openingFamily = family,
            playedAt = raw.endTime.takeIf { it > 0 } ?: (System.currentTimeMillis() / 1000),
            rated = raw.rated,
            moveCount = (pgn.moves.size + 1) / 2,
            userRating = if (userIsWhite) raw.whiteRating else raw.blackRating,
            opponentRating = if (userIsWhite) raw.blackRating else raw.whiteRating,
        )
    }

    // --- Analyse ------------------------------------------------------------

    suspend fun analyzeGame(
        gameId: String,
        onProgress: (AnalysisProgress) -> Unit = {},
    ): AnalyzeOutcome {
        val game = gameDao.get(gameId) ?: return AnalyzeOutcome(0, 0, "Partie introuvable.")
        return analyzeOne(game, onProgress)
    }

    suspend fun analyzePending(
        limit: Int,
        onProgress: (AnalysisProgress) -> Unit = {},
    ): AnalyzeOutcome {
        val pending = gameDao.pendingAnalysis(limit)
        if (pending.isEmpty()) return AnalyzeOutcome(0, 0)
        var games = 0
        var puzzles = 0
        var error: String? = null
        for (game in pending) {
            val outcome = analyzeOne(game, onProgress)
            games += outcome.analyzedGames
            puzzles += outcome.puzzlesCreated
            if (outcome.error != null) {
                error = outcome.error
                break
            }
        }
        return AnalyzeOutcome(games, puzzles, error)
    }

    private suspend fun analyzeOne(
        game: GameRecord,
        onProgress: (AnalysisProgress) -> Unit,
    ): AnalyzeOutcome = try {
        val engine = engines.engine()
        val limits = GameAnalyzer.PRESETS[settings.current.analysisPresetIndex.coerceIn(0, 2)].limits
        val analyzed = GameAnalyzer(engine).analyze(game, limits, onProgress)
            ?: return AnalyzeOutcome(0, 0, "PGN illisible pour cette partie.")
        gameDao.saveAnalysis(game.id, analyzed.summary, analyzed.moves)

        val puzzles = PuzzleGenerator(engine).generate(
            game = game,
            moves = analyzed.moves,
            maxPerGame = settings.current.maxPuzzlesPerGame,
        )
        var created = 0
        for (puzzle in puzzles) if (puzzleDao.insert(puzzle) > 0) created++
        AnalyzeOutcome(1, created)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Echec d'analyse de ${game.id}", e)
        AnalyzeOutcome(0, 0, "Analyse interrompue : ${e.message}")
    }

    /**
     * Cree un puzzle a partir d'une position choisie dans la revue de partie.
     * Renvoie null si la position n'a pas de meilleur coup assez net pour etre un exercice.
     */
    suspend fun createPuzzleFromPosition(
        gameId: String,
        fen: String,
        ply: Int,
        playedSan: String?,
        kind: PuzzleKind = PuzzleKind.BLUNDER_FIX,
    ): Puzzle? {
        val game = gameDao.get(gameId) ?: return null
        val engine = engines.engine()
        val puzzle = PuzzleGenerator(engine).fromPosition(game, fen, ply, playedSan, kind) ?: return null
        return if (puzzleDao.insert(puzzle) > 0) puzzle else puzzle
    }

    /** Regenere les puzzles d'une partie deja analysee (utile apres un changement de moteur). */
    suspend fun regeneratePuzzles(gameId: String): Int {
        val game = gameDao.get(gameId) ?: return 0
        val moves = gameDao.moves(gameId)
        if (moves.isEmpty()) return 0
        val engine = engines.engine()
        val puzzles = PuzzleGenerator(engine).generate(game, moves, maxPerGame = settings.current.maxPuzzlesPerGame)
        return puzzles.count { puzzleDao.insert(it) > 0 }
    }

    // --- Lectures -----------------------------------------------------------

    fun games(filter: GameFilter = GameFilter()): List<GameRecord> = gameDao.list(filter)
    fun game(id: String): GameRecord? = gameDao.get(id)
    fun moves(gameId: String): List<MoveRecord> = gameDao.moves(gameId)
    fun openingFamilies(): List<String> = statsDao.openingRows(minGames = 1, limit = 60).map { it.family }
    fun openings(minGames: Int = 1): List<OpeningRow> = statsDao.openingRows(minGames, limit = 40)
    fun colorSplit(): Pair<OpeningRow, OpeningRow> = statsDao.colorSplit()

    fun puzzles(query: PuzzleQuery): List<Puzzle> = puzzleDao.query(query)
    fun puzzle(id: Long): Puzzle? = puzzleDao.get(id)
    fun puzzleCountsByMotif(): Map<Motif, Int> = puzzleDao.countByMotif()
    fun puzzleProgress(): PuzzleProgress = puzzleDao.progress()

    suspend fun dashboard(): DashboardData = withContext(Dispatchers.IO) {
        val summary = statsDao.summary()
        val phases = statsDao.phaseRows()
        val motifErrors = statsDao.motifErrorCounts()
        val openings = statsDao.openingRows()
        val timeClasses = statsDao.timeClassRows()
        val timePressure = statsDao.timePressureRows()
        val progress = puzzleDao.progress()
        val weaknesses = WeaknessEngine.analyze(
            WeaknessInput(
                summary = summary,
                phases = phases,
                motifErrors = motifErrors,
                motifScores = puzzleDao.motifScores(),
                openings = openings,
                timeClasses = timeClasses,
                timePressure = timePressure,
                errorsByMoveNumber = statsDao.errorsByMoveNumber(),
                colorSplit = statsDao.colorSplit(),
            )
        )
        DashboardData(
            summary = summary,
            trend = statsDao.accuracyTrend(),
            classifications = statsDao.classificationCounts(),
            phases = phases,
            motifErrors = motifErrors,
            openings = openings,
            timeClasses = timeClasses,
            timePressure = timePressure,
            puzzleProgress = progress,
            weaknesses = weaknesses,
            heatmap = statsDao.blunderHeatmap(),
            ratings = gameDao.ratings(),
            plan = TrainingPlanBuilder.build(weaknesses, progress, settings.current.dailyPuzzleGoal),
            pendingAnalysis = gameDao.countPendingAnalysis(),
            engineName = engines.engine().displayName,
        )
    }

    // --- Entrainement -------------------------------------------------------

    fun recordPuzzleResult(
        puzzle: Puzzle,
        success: Boolean,
        elapsedMillis: Long,
        hintsUsed: Int,
        firstMoveUci: String?,
    ): SrsState {
        val now = System.currentTimeMillis() / 1000
        val next = Srs.next(puzzle.srs, success, elapsedMillis, hintsUsed, puzzle.difficulty, now)
        puzzleDao.recordAttempt(
            PuzzleAttempt(puzzle.id, now, success, elapsedMillis, firstMoveUci, hintsUsed),
            next,
        )
        return next
    }

    fun retirePuzzle(id: Long) = puzzleDao.retire(id)

    fun resetEverything() {
        puzzleDao.deleteAll()
        gameDao.deleteAllGames()
    }

    companion object {
        private const val TAG = "ForgeRepository"
    }
}
