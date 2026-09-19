package com.chessforge.engine.forge

import com.chessforge.chess.Fen
import com.chessforge.chess.Move
import com.chessforge.chess.MoveList
import com.chessforge.chess.Piece
import com.chessforge.chess.Position
import com.chessforge.engine.ChessEngine
import com.chessforge.engine.EngineLimits
import com.chessforge.engine.EngineLine
import com.chessforge.engine.EngineScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Moteur d'echecs integre, 100 % Kotlin : aucune dependance, fonctionne hors ligne.
 *
 * Recherche alpha-beta avec approfondissement iteratif, table de transposition,
 * recherche de quiescence, elagage du coup nul, reductions de coups tardifs et
 * heuristiques de tri (coup de la table, MVV-LVA, killers, historique).
 *
 * Suffisant pour reperer les gaffes et proposer des puzzles ; Stockfish reste
 * plus precis sur les positions calmes (voir `tools/fetch_stockfish.ps1`).
 */
class ForgeEngine(ttSizeMb: Int = 12) : ChessEngine {

    override val id = "forge"
    override val displayName = "ForgeEngine (integre)"
    override val strengthLabel = "environ 2000 Elo"

    private val tt = TranspositionTable(ttSizeMb)
    private val mutex = Mutex()
    private val searcher = Searcher(tt)

    override suspend fun analyze(fen: String, limits: EngineLimits): List<EngineLine> =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                searcher.reset()
                val position = Fen.parse(fen)
                val excluded = HashSet<Int>()
                val lines = ArrayList<EngineLine>(limits.multiPv)
                repeat(limits.multiPv) {
                    val line = searcher.search(position.copy(), limits, excluded)
                        ?: return@repeat
                    lines.add(line)
                    val first = line.pv.firstOrNull() ?: return@repeat
                    excluded.add(com.chessforge.chess.San.fromUci(position, first))
                }
                if (lines.isEmpty()) listOf(EngineLine(EngineScore.EVEN, emptyList(), 0)) else lines
            }
        }

    override fun stop() = searcher.requestStop()

    override fun close() = tt.clear()
}

private const val INFINITY = 1_000_000
private const val MATE_VALUE = 32_000
private const val MATE_BOUND = MATE_VALUE - 1000
private const val MAX_DEPTH = 96

/** Bornes stockees dans la table de transposition. */
private const val FLAG_EXACT = 0
private const val FLAG_LOWER = 1
private const val FLAG_UPPER = 2

private class TranspositionTable(sizeMb: Int) {
    private val count: Int = run {
        val bytesPerEntry = 18
        var n = 1
        val target = (sizeMb.coerceIn(1, 64) * 1024L * 1024L) / bytesPerEntry
        while (n * 2L <= target) n *= 2
        n.coerceAtLeast(1 shl 12)
    }
    private val mask = count - 1
    private val keys = LongArray(count)
    private val moves = IntArray(count)
    private val scores = IntArray(count)
    private val depths = ByteArray(count)
    private val flags = ByteArray(count)

    var probedMove = Move.NONE
        private set
    var probedScore = 0
        private set
    var probedDepth = 0
        private set
    var probedFlag = -1
        private set

    fun clear() {
        keys.fill(0L)
        moves.fill(0)
        depths.fill(0)
        flags.fill(0)
    }

    /** Vrai si la position est dans la table ; les champs `probed*` sont alors renseignes. */
    fun probe(key: Long): Boolean {
        val i = (key.toInt() and mask)
        if (keys[i] != key) {
            probedFlag = -1
            probedMove = Move.NONE
            return false
        }
        probedMove = moves[i]
        probedScore = scores[i]
        probedDepth = depths[i].toInt()
        probedFlag = flags[i].toInt()
        return true
    }

    fun store(key: Long, move: Int, score: Int, depth: Int, flag: Int) {
        val i = (key.toInt() and mask)
        // Remplacement : on garde l'entree la plus profonde, sauf si la cle change.
        if (keys[i] == key && depths[i] > depth && flags[i].toInt() == FLAG_EXACT) return
        keys[i] = key
        moves[i] = move
        scores[i] = score
        depths[i] = depth.coerceIn(-128, 127).toByte()
        flags[i] = flag.toByte()
    }
}

private class Searcher(private val tt: TranspositionTable) {

    @Volatile
    private var stopRequested = false
    private var stopped = false
    private var deadlineNanos = Long.MAX_VALUE
    private var nodes = 0L

    private lateinit var position: Position
    private val moveLists = Array(MAX_DEPTH) { MoveList() }
    private val killers = Array(MAX_DEPTH) { IntArray(2) }
    private val history = Array(2) { IntArray(64 * 64) }
    private val pv = Array(MAX_DEPTH) { IntArray(MAX_DEPTH) }
    private val pvLength = IntArray(MAX_DEPTH)
    private val scoresBuffer = Array(MAX_DEPTH) { IntArray(256) }

    fun requestStop() {
        stopRequested = true
    }

    fun reset() {
        stopRequested = false
        stopped = false
        nodes = 0L
        for (k in killers) k.fill(0)
        for (h in history) h.fill(0)
    }

    /**
     * Approfondissement iteratif depuis [root]. [excluded] permet le multi-PV :
     * les coups deja retenus sont ecartes a la racine.
     */
    fun search(root: Position, limits: EngineLimits, excluded: Set<Int>): EngineLine? {
        position = root
        stopped = false
        val start = System.nanoTime()
        deadlineNanos = if (limits.movetimeMs > 0) {
            start + limits.movetimeMs * 1_000_000L
        } else {
            start + DEFAULT_BUDGET_MS * 1_000_000L
        }

        val rootMoves = MoveList()
        position.generateMoves(rootMoves)
        val legal = ArrayList<Int>(rootMoves.size)
        for (i in 0 until rootMoves.size) {
            val m = rootMoves[i]
            if (m in excluded) continue
            if (position.makeMove(m)) {
                position.unmakeMove()
                legal.add(m)
            }
        }
        if (legal.isEmpty()) return null

        var bestMove = legal.first()
        var bestScore = -INFINITY
        var bestPv = listOf(Move.toUci(bestMove))
        var reachedDepth = 0

        val maxDepth = limits.depth.coerceIn(1, MAX_DEPTH - 8)
        for (depth in 1..maxDepth) {
            var alpha = -INFINITY
            val beta = INFINITY
            var iterBest = Move.NONE
            var iterScore = -INFINITY
            var iterPv: List<String> = emptyList()

            for ((index, move) in legal.withIndex()) {
                if (!position.makeMove(move)) continue
                val score = if (index == 0) {
                    -negamax(depth - 1, -beta, -alpha, 1, true)
                } else {
                    var s = -negamax(depth - 1, -alpha - 1, -alpha, 1, true)
                    if (s > alpha) s = -negamax(depth - 1, -beta, -alpha, 1, true)
                    s
                }
                position.unmakeMove()
                if (stopped) break
                if (score > iterScore) {
                    iterScore = score
                    iterBest = move
                    if (score > alpha) {
                        alpha = score
                        iterPv = extractPv(move)
                    }
                }
            }

            if (iterBest != Move.NONE && (!stopped || reachedDepth == 0)) {
                bestMove = iterBest
                bestScore = iterScore
                if (iterPv.isNotEmpty()) bestPv = iterPv
                reachedDepth = depth
            }
            if (stopped) break
            // Mat trouve : inutile de chercher plus profond.
            if (kotlin.math.abs(bestScore) > MATE_BOUND) break
            if (System.nanoTime() >= deadlineNanos) break
        }

        if (bestPv.isEmpty()) bestPv = listOf(Move.toUci(bestMove))
        // Arret avant la fin de la premiere iteration : on rend au moins l'evaluation statique.
        if (bestScore <= -INFINITY + 1) bestScore = Evaluation.evaluate(position)
        return EngineLine(toScore(bestScore), bestPv, reachedDepth.coerceAtLeast(1), nodes)
    }

    private fun extractPv(rootMove: Int): List<String> {
        val out = ArrayList<String>(pvLength.getOrElse(1) { 0 } + 1)
        out.add(Move.toUci(rootMove))
        for (i in 1 until pvLength[1].coerceAtMost(MAX_DEPTH)) out.add(Move.toUci(pv[1][i]))
        return out
    }

    private fun toScore(score: Int): EngineScore = when {
        score > MATE_BOUND -> EngineScore(mate = (MATE_VALUE - score + 1) / 2)
        score < -MATE_BOUND -> EngineScore(mate = -((MATE_VALUE + score + 1) / 2))
        else -> EngineScore(cp = score)
    }

    private fun checkTime() {
        if (stopRequested) {
            stopped = true
            return
        }
        if (nodes and 2047L == 0L && System.nanoTime() >= deadlineNanos) stopped = true
    }

    private fun negamax(depthIn: Int, alphaIn: Int, beta: Int, ply: Int, canNull: Boolean): Int {
        var alpha = alphaIn
        pvLength[ply] = ply

        if (stopped) return 0
        nodes++
        checkTime()
        if (stopped) return 0

        if (ply > 0) {
            if (position.halfmoveClock >= 100 || position.isRepetition() ||
                position.isInsufficientMaterial()
            ) return 0
        }
        if (ply >= MAX_DEPTH - 2) return Evaluation.evaluate(position)

        val inCheck = position.isInCheck()
        var depth = depthIn
        if (inCheck && depth < 1) depth = 1 // ne jamais entrer en quiescence sous echec
        if (depth <= 0) return quiescence(alpha, beta, ply)

        var ttMove = Move.NONE
        if (tt.probe(position.key)) {
            ttMove = tt.probedMove
            if (ply > 0 && tt.probedDepth >= depth) {
                val s = fromTtScore(tt.probedScore, ply)
                when (tt.probedFlag) {
                    FLAG_EXACT -> return s
                    FLAG_LOWER -> if (s >= beta) return s
                    FLAG_UPPER -> if (s <= alpha) return s
                }
            }
        }

        // Elagage du coup nul : si passer son tour suffit a depasser beta, la position est gagnee.
        if (!inCheck && canNull && ply > 0 && depth >= 3 &&
            beta < MATE_BOUND && position.nonPawnPieceCount() > 0
        ) {
            val reduction = 2 + depth / 6
            position.makeNullMove()
            val score = -negamax(depth - 1 - reduction, -beta, -beta + 1, ply + 1, false)
            position.unmakeNullMove()
            if (stopped) return 0
            if (score >= beta) return if (score > MATE_BOUND) beta else score
        }

        val list = moveLists[ply]
        position.generateMoves(list)
        orderMoves(list, ply, ttMove)

        var bestScore = -INFINITY
        var bestMove = Move.NONE
        var legalCount = 0

        for (i in 0 until list.size) {
            val move = pickNext(list, i, ply)
            val isCapture = position.board[Move.to(move)] != Piece.NONE ||
                Move.flag(move) == Move.FLAG_EN_PASSANT
            val isPromo = Move.promo(move) != 0

            if (!position.makeMove(move)) continue
            legalCount++
            val givesCheck = position.isInCheck()
            val newDepth = depth - 1 + if (givesCheck && depth <= 4) 1 else 0

            var score: Int
            if (legalCount == 1) {
                score = -negamax(newDepth, -beta, -alpha, ply + 1, true)
            } else {
                var reduction = 0
                if (depth >= 3 && legalCount > 3 && !isCapture && !isPromo && !inCheck && !givesCheck) {
                    reduction = 1 + (if (legalCount > 8) 1 else 0) + (depth / 8)
                    reduction = reduction.coerceAtMost(newDepth - 1).coerceAtLeast(0)
                }
                score = -negamax(newDepth - reduction, -alpha - 1, -alpha, ply + 1, true)
                if (score > alpha && reduction > 0) {
                    score = -negamax(newDepth, -alpha - 1, -alpha, ply + 1, true)
                }
                if (score > alpha && score < beta) {
                    score = -negamax(newDepth, -beta, -alpha, ply + 1, true)
                }
            }
            position.unmakeMove()
            if (stopped) return 0

            if (score > bestScore) {
                bestScore = score
                bestMove = move
                if (score > alpha) {
                    alpha = score
                    updatePv(ply, move)
                    if (score >= beta) {
                        if (!isCapture) {
                            if (killers[ply][0] != move) {
                                killers[ply][1] = killers[ply][0]
                                killers[ply][0] = move
                            }
                            val color = position.side
                            history[color][Move.from(move) * 64 + Move.to(move)] += depth * depth
                        }
                        tt.store(position.key, move, toTtScore(score, ply), depth, FLAG_LOWER)
                        return score
                    }
                }
            }
        }

        if (legalCount == 0) return if (inCheck) -MATE_VALUE + ply else 0

        val flag = if (bestScore > alphaIn) FLAG_EXACT else FLAG_UPPER
        tt.store(position.key, bestMove, toTtScore(bestScore, ply), depth, flag)
        return bestScore
    }

    private fun quiescence(alphaIn: Int, beta: Int, ply: Int): Int {
        var alpha = alphaIn
        nodes++
        checkTime()
        if (stopped) return 0
        if (ply >= MAX_DEPTH - 2) return Evaluation.evaluate(position)

        val standPat = Evaluation.evaluate(position)
        if (standPat >= beta) return standPat
        if (standPat > alpha) alpha = standPat

        val list = moveLists[ply]
        position.generateMoves(list, capturesOnly = true)
        orderMoves(list, ply, Move.NONE)

        for (i in 0 until list.size) {
            val move = pickNext(list, i, ply)
            val victim = position.board[Move.to(move)]
            val gain = if (victim != Piece.NONE) Piece.value(Piece.typeOf(victim)) else 100
            // Elagage delta : une prise qui ne peut pas rattraper le retard est ignoree.
            if (Move.promo(move) == 0 && standPat + gain + 180 < alpha) continue

            if (!position.makeMove(move)) continue
            val score = -quiescence(-beta, -alpha, ply + 1)
            position.unmakeMove()
            if (stopped) return 0
            if (score >= beta) return score
            if (score > alpha) alpha = score
        }
        return alpha
    }

    private fun updatePv(ply: Int, move: Int) {
        pv[ply][ply] = move
        var i = ply + 1
        while (i < pvLength[ply + 1]) {
            pv[ply][i] = pv[ply + 1][i]
            i++
        }
        pvLength[ply] = pvLength[ply + 1]
        if (pvLength[ply] <= ply) pvLength[ply] = ply + 1
    }

    private fun toTtScore(score: Int, ply: Int): Int = when {
        score > MATE_BOUND -> score + ply
        score < -MATE_BOUND -> score - ply
        else -> score
    }

    private fun fromTtScore(score: Int, ply: Int): Int = when {
        score > MATE_BOUND -> score - ply
        score < -MATE_BOUND -> score + ply
        else -> score
    }

    /** Calcule les scores de tri une fois, puis [pickNext] fait un tri par selection. */
    private fun orderMoves(list: MoveList, ply: Int, ttMove: Int) {
        val scores = scoresBuffer[ply]
        val color = position.side
        for (i in 0 until list.size) {
            val move = list[i]
            val to = Move.to(move)
            val victim = position.board[to]
            val attacker = position.board[Move.from(move)]
            scores[i] = when {
                move == ttMove -> 2_000_000
                victim != Piece.NONE ->
                    1_000_000 + 10 * Piece.value(Piece.typeOf(victim)) - Piece.value(Piece.typeOf(attacker))
                Move.promo(move) != 0 -> 900_000 + Piece.value(Move.promo(move))
                Move.flag(move) == Move.FLAG_EN_PASSANT -> 1_000_000
                move == killers[ply][0] -> 800_000
                move == killers[ply][1] -> 790_000
                else -> history[color][Move.from(move) * 64 + to]
            }
        }
    }

    private fun pickNext(list: MoveList, start: Int, ply: Int): Int {
        val scores = scoresBuffer[ply]
        var best = start
        for (i in start + 1 until list.size) if (scores[i] > scores[best]) best = i
        if (best != start) {
            val tmpMove = list[start]
            list.set(start, list[best])
            list.set(best, tmpMove)
            val tmpScore = scores[start]
            scores[start] = scores[best]
            scores[best] = tmpScore
        }
        return list[start]
    }

    companion object {
        /** Budget par defaut quand l'appelant ne fixe que la profondeur. */
        private const val DEFAULT_BUDGET_MS = 8_000L
    }
}
