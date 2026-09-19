package com.chessforge.engine

/** Limites de reflexion. Si [movetimeMs] > 0 il primera sur la profondeur. */
data class EngineLimits(
    val depth: Int = 14,
    val movetimeMs: Long = 0L,
    val multiPv: Int = 1,
    val threads: Int = 1,
)

/**
 * Score du point de vue du camp au trait.
 * [cp] en centipions, ou [mate] en nombre de coups (positif = je mate, negatif = je suis mate).
 */
data class EngineScore(val cp: Int? = null, val mate: Int? = null) {

    /** Valeur bornee en centipions, utilisable pour les graphiques et les comparaisons. */
    fun toCp(cap: Int = 1500): Int = when {
        mate != null && mate > 0 -> cap
        mate != null -> -cap
        else -> (cp ?: 0).coerceIn(-cap, cap)
    }

    fun negate(): EngineScore = EngineScore(cp?.let { -it }, mate?.let { -it })

    fun format(): String = when {
        mate != null -> if (mate > 0) "#$mate" else "#-${-mate}"
        else -> {
            val v = (cp ?: 0) / 100.0
            (if (v > 0) "+" else "") + String.format("%.2f", v)
        }
    }

    companion object {
        val EVEN = EngineScore(cp = 0)
    }
}

/** Une variante proposee par le moteur. */
data class EngineLine(
    val score: EngineScore,
    /** Coups en notation UCI, le premier etant le coup recommande. */
    val pv: List<String>,
    val depth: Int,
    val nodes: Long = 0L,
) {
    val bestMove: String? get() = pv.firstOrNull()
}

interface ChessEngine {
    val id: String
    val displayName: String

    /** Force approximative affichee a l'utilisateur. */
    val strengthLabel: String

    suspend fun analyze(fen: String, limits: EngineLimits): List<EngineLine>

    /** Interrompt l'analyse en cours au plus vite. */
    fun stop()

    fun close()
}

class EngineUnavailableException(message: String) : Exception(message)
