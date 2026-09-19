package com.chessforge.srs

import com.chessforge.data.model.SrsState
import kotlin.math.roundToLong

/**
 * Repetition espacee (variante de SM-2 adaptee aux puzzles).
 *
 * Un puzzle rate revient tres vite ; un puzzle resolu d'un trait s'espace. La note
 * tient compte du temps de resolution et des indices utilises : resoudre en 40 secondes
 * avec un indice n'est pas la meme chose que trouver immediatement.
 */
object Srs {

    private const val MIN_EASE = 1.3
    private const val MAX_EASE = 2.8
    private const val RELEARN_SECONDS = 600L
    private const val RETIRE_INTERVAL_DAYS = 240.0
    private const val RETIRE_MIN_REPS = 6

    fun next(
        state: SrsState,
        success: Boolean,
        elapsedMillis: Long,
        hintsUsed: Int,
        difficulty: Int,
        now: Long = System.currentTimeMillis() / 1000,
    ): SrsState {
        val grade = grade(success, elapsedMillis, hintsUsed, difficulty)

        if (grade < 3) {
            return state.copy(
                dueAt = now + RELEARN_SECONDS,
                intervalDays = 0.0,
                ease = (state.ease - 0.20).coerceAtLeast(MIN_EASE),
                reps = 0,
                lapses = state.lapses + 1,
                retired = false,
            )
        }

        val reps = state.reps + 1
        val ease = (state.ease + (0.1 - (5 - grade) * (0.08 + (5 - grade) * 0.02)))
            .coerceIn(MIN_EASE, MAX_EASE)
        val interval = when (reps) {
            1 -> 1.0
            2 -> 3.0
            else -> (state.intervalDays * ease).coerceAtLeast(3.0)
        }
        val retired = interval >= RETIRE_INTERVAL_DAYS && reps >= RETIRE_MIN_REPS
        return state.copy(
            dueAt = now + (interval * 86_400).roundToLong(),
            intervalDays = interval,
            ease = ease,
            reps = reps,
            retired = retired,
        )
    }

    /**
     * Note de 1 a 5. Le seuil de rapidite depend de la difficulte du puzzle :
     * 15 secondes sur un mat en un n'est pas une reussite franche, sur un coup calme si.
     */
    private fun grade(success: Boolean, elapsedMillis: Long, hintsUsed: Int, difficulty: Int): Int {
        if (!success) return 1
        val seconds = elapsedMillis / 1000.0
        val quickThreshold = 6.0 + difficulty * 6.0
        var grade = when {
            seconds <= quickThreshold * 0.5 -> 5
            seconds <= quickThreshold -> 4
            else -> 3
        }
        grade -= hintsUsed.coerceAtMost(2)
        return grade.coerceIn(2, 5)
    }

    /** Libelle lisible de la prochaine echeance. */
    fun dueLabel(dueAt: Long, now: Long = System.currentTimeMillis() / 1000): String {
        val delta = dueAt - now
        return when {
            delta <= 0 -> "a revoir"
            delta < 3_600 -> "dans ${delta / 60} min"
            delta < 86_400 -> "dans ${delta / 3_600} h"
            delta < 86_400 * 30 -> "dans ${delta / 86_400} j"
            else -> "dans ${delta / (86_400 * 30)} mois"
        }
    }
}
