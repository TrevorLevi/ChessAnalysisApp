package com.chessforge.engine.sf

import android.content.Context
import android.util.Log
import com.chessforge.engine.ChessEngine
import com.chessforge.engine.EngineLimits
import com.chessforge.engine.EngineLine
import com.chessforge.engine.EngineScore
import com.chessforge.engine.EngineUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties

/**
 * Moteur Stockfish pilote en UCI a travers [NativeBridge].
 *
 * Les reseaux NNUE sont livres dans les assets par `tools/fetch_stockfish.ps1` puis
 * copies une fois dans le stockage prive de l'appli, car le code natif ne sait pas
 * lire directement dans un APK.
 */
class StockfishEngine(private val context: Context) : ChessEngine {

    override val id = "stockfish"
    override val displayName = "Stockfish (natif)"
    override val strengthLabel = "niveau grand maitre"

    private val mutex = Mutex()
    private var booted = false

    suspend fun boot(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (booted) return@withLock true
            if (!NativeBridge.available) return@withLock false
            if (!NativeBridge.nativeStart()) return@withLock false

            send("uci")
            awaitToken("uciok")

            installNets()?.let { nets ->
                nets.big?.let { send("setoption name EvalFile value ${it.absolutePath}") }
                nets.small?.let { send("setoption name EvalFileSmall value ${it.absolutePath}") }
            }
            send("setoption name Threads value ${recommendedThreads()}")
            send("setoption name Hash value 128")
            send("setoption name UCI_ShowWDL value false")
            send("isready")
            awaitToken("readyok")
            booted = true
            true
        }
    }

    override suspend fun analyze(fen: String, limits: EngineLimits): List<EngineLine> =
        withContext(Dispatchers.IO) {
            if (!booted && !boot()) throw EngineUnavailableException("Stockfish natif indisponible.")
            mutex.withLock {
                send("setoption name MultiPV value ${limits.multiPv.coerceAtLeast(1)}")
                send("position fen $fen")
                val go = buildString {
                    append("go")
                    if (limits.depth > 0) append(" depth ${limits.depth}")
                    if (limits.movetimeMs > 0) append(" movetime ${limits.movetimeMs}")
                }
                send(go)
                readSearch(limits.multiPv)
            }
        }

    override fun stop() {
        if (booted) runCatching { NativeBridge.nativeWrite("stop") }
    }

    /**
     * Interrompt la recherche en cours mais garde le moteur vivant : la boucle UCI de
     * Stockfish ne peut etre demarree qu'une fois par processus.
     */
    override fun close() {
        if (booted) runCatching { NativeBridge.nativeStop() }
    }

    // --- Protocole UCI -------------------------------------------------------

    private fun send(command: String) = NativeBridge.nativeWrite(command)

    private fun awaitToken(token: String, maxLines: Int = 400) {
        repeat(maxLines) {
            val line = NativeBridge.nativeReadLine() ?: return
            if (line.trim() == token) return
        }
    }

    /** Collecte les lignes `info` jusqu'a `bestmove`, en gardant la plus profonde par multipv. */
    private fun readSearch(multiPv: Int): List<EngineLine> {
        val bestByPv = HashMap<Int, EngineLine>()
        var fallbackBestMove: String? = null
        var guard = 0
        while (guard++ < MAX_LINES) {
            val line = NativeBridge.nativeReadLine() ?: break
            when {
                line.startsWith("bestmove") -> {
                    fallbackBestMove = line.split(" ").getOrNull(1)
                    break
                }
                line.startsWith("info ") && line.contains(" pv ") -> {
                    parseInfo(line)?.let { (pvIndex, engineLine) ->
                        val existing = bestByPv[pvIndex]
                        if (existing == null || engineLine.depth >= existing.depth) {
                            bestByPv[pvIndex] = engineLine
                        }
                    }
                }
            }
        }
        val ordered = (1..multiPv.coerceAtLeast(1)).mapNotNull { bestByPv[it] }
        if (ordered.isNotEmpty()) return ordered
        val move = fallbackBestMove?.takeIf { it != "(none)" } ?: return emptyList()
        return listOf(EngineLine(EngineScore.EVEN, listOf(move), 0))
    }

    private fun parseInfo(line: String): Pair<Int, EngineLine>? {
        val tokens = line.split(" ")
        var depth = 0
        var multiPv = 1
        var cp: Int? = null
        var mate: Int? = null
        var nodes = 0L
        var pv: List<String> = emptyList()
        var i = 0
        while (i < tokens.size) {
            when (tokens[i]) {
                "depth" -> depth = tokens.getOrNull(++i)?.toIntOrNull() ?: depth
                "multipv" -> multiPv = tokens.getOrNull(++i)?.toIntOrNull() ?: multiPv
                "nodes" -> nodes = tokens.getOrNull(++i)?.toLongOrNull() ?: nodes
                "score" -> {
                    when (tokens.getOrNull(i + 1)) {
                        "cp" -> cp = tokens.getOrNull(i + 2)?.toIntOrNull()
                        "mate" -> mate = tokens.getOrNull(i + 2)?.toIntOrNull()
                    }
                    i += 2
                }
                "pv" -> {
                    pv = tokens.drop(i + 1).filter { it.isNotBlank() }
                    i = tokens.size
                }
            }
            i++
        }
        if (pv.isEmpty()) return null
        return multiPv to EngineLine(EngineScore(cp = cp, mate = mate), pv, depth, nodes)
    }

    // --- Reseaux NNUE --------------------------------------------------------

    private data class Nets(val big: File?, val small: File?)

    private fun installNets(): Nets? {
        val manifest = runCatching {
            context.assets.open("$ASSET_DIR/$MANIFEST").use { input ->
                Properties().apply { load(input) }
            }
        }.getOrNull() ?: run {
            Log.i(TAG, "Aucun reseau NNUE dans les assets : Stockfish utilisera son evaluation par defaut.")
            return null
        }

        val targetDir = File(context.filesDir, "nnue").apply { mkdirs() }
        fun install(key: String): File? {
            val name = manifest.getProperty(key)?.takeIf { it.isNotBlank() } ?: return null
            val target = File(targetDir, name)
            if (target.exists() && target.length() > 1024) return target
            return runCatching {
                context.assets.open("$ASSET_DIR/$name").use { input ->
                    target.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
                }
                target
            }.getOrElse {
                Log.w(TAG, "Copie du reseau $name impossible", it)
                null
            }
        }
        return Nets(install("big"), install("small"))
    }

    private fun recommendedThreads(): Int =
        (Runtime.getRuntime().availableProcessors() - 2).coerceIn(1, 4)

    companion object {
        private const val TAG = "StockfishEngine"
        private const val ASSET_DIR = "nnue"
        private const val MANIFEST = "manifest.properties"
        private const val MAX_LINES = 20_000
    }
}
