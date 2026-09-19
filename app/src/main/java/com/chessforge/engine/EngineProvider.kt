package com.chessforge.engine

import android.content.Context
import com.chessforge.data.prefs.EnginePreference
import com.chessforge.data.prefs.Settings
import com.chessforge.engine.forge.ForgeEngine
import com.chessforge.engine.sf.NativeBridge
import com.chessforge.engine.sf.StockfishEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Choisit le moteur a utiliser et le garde en cache.
 *
 * Stockfish est prefere quand il est disponible ; sinon le moteur Kotlin integre prend
 * le relais sans que l'utilisateur ait quoi que ce soit a faire.
 */
class EngineProvider(private val context: Context, private val settings: Settings) {

    private val mutex = Mutex()
    private var cached: ChessEngine? = null
    private var cachedFor: EnginePreference? = null

    val nativeAvailable: Boolean get() = NativeBridge.available

    suspend fun engine(): ChessEngine = mutex.withLock {
        val preference = settings.current.enginePreference
        cached?.let { if (cachedFor == preference) return@withLock it }
        cached?.close()

        val engine = when (preference) {
            EnginePreference.FORGE -> ForgeEngine()
            EnginePreference.STOCKFISH, EnginePreference.AUTO -> bootStockfish() ?: ForgeEngine()
        }
        cached = engine
        cachedFor = preference
        engine
    }

    private suspend fun bootStockfish(): ChessEngine? {
        if (!NativeBridge.available) return null
        val engine = StockfishEngine(context)
        return if (runCatching { engine.boot() }.getOrDefault(false)) engine else null
    }

    fun stopCurrent() {
        cached?.stop()
    }

    fun shutdown() {
        cached?.close()
        cached = null
        cachedFor = null
    }
}
