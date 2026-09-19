package com.chessforge.di

import com.chessforge.analysis.AnalysisProgress
import com.chessforge.data.repo.ForgeRepository
import com.chessforge.engine.EngineProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Centralise les travaux longs (import chess.com, analyse moteur) pour qu'un seul
 * puisse tourner a la fois et que tous les ecrans voient la meme progression.
 *
 * [revision] s'incremente a chaque fin de tache : les ecrans l'observent pour se
 * recharger sans avoir besoin d'un bus d'evenements.
 */
class TaskCenter(
    private val repository: ForgeRepository,
    private val engines: EngineProvider,
) {

    data class State(
        val running: Boolean = false,
        val kind: Kind = Kind.NONE,
        val label: String = "",
        val detail: String = "",
        /** null quand la progression n'est pas mesurable. */
        val progress: Float? = null,
        val result: String? = null,
        val error: String? = null,
    )

    enum class Kind { NONE, SYNC, ANALYZE }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private var job: Job? = null

    val busy: Boolean get() = _state.value.running

    fun syncThenAnalyze(autoAnalyze: Boolean, analyzeLimit: Int = 10) {
        if (busy) return
        job = scope.launch {
            _state.value = State(running = true, kind = Kind.SYNC, label = "Import des parties")
            val sync = repository.syncGames { message ->
                _state.value = _state.value.copy(detail = message)
            }
            if (sync.error != null) {
                _state.value = State(error = sync.error)
                _revision.value++
                return@launch
            }
            val importedLabel = when (sync.imported) {
                0 -> "Aucune nouvelle partie"
                1 -> "1 nouvelle partie importee"
                else -> "${sync.imported} nouvelles parties importees"
            }
            if (!autoAnalyze || sync.imported == 0) {
                _state.value = State(result = importedLabel)
                _revision.value++
                return@launch
            }
            // Les parties sont deja en base : on previent les ecrans avant de lancer
            // l'analyse, qui dure plusieurs minutes.
            _revision.value++
            runAnalysis(analyzeLimit, prefix = importedLabel)
        }
    }

    fun analyzePending(limit: Int) {
        if (busy) return
        job = scope.launch { runAnalysis(limit, prefix = null) }
    }

    fun analyzeGame(gameId: String) {
        if (busy) return
        job = scope.launch {
            _state.value = State(running = true, kind = Kind.ANALYZE, label = "Analyse de la partie")
            val outcome = repository.analyzeGame(gameId) { progress -> publish(progress, 1, 1) }
            _state.value = if (outcome.error != null) {
                State(error = outcome.error)
            } else {
                State(result = "Analyse terminee, ${outcome.puzzlesCreated} puzzle(s) cree(s)")
            }
            _revision.value++
        }
    }

    private suspend fun runAnalysis(limit: Int, prefix: String?) {
        _state.value = State(running = true, kind = Kind.ANALYZE, label = "Analyse des parties")
        var index = 0
        val outcome = repository.analyzePending(limit) { progress ->
            publish(progress, index, limit)
            if (progress.done == progress.total) {
                index++
                // Une partie de plus est analysee : le tableau de bord peut se mettre a jour.
                _revision.value++
            }
        }
        val summary = buildString {
            if (prefix != null) append("$prefix · ")
            append("${outcome.analyzedGames} partie(s) analysee(s)")
            if (outcome.puzzlesCreated > 0) append(", ${outcome.puzzlesCreated} puzzle(s)")
        }
        _state.value = if (outcome.error != null) State(error = outcome.error) else State(result = summary)
        _revision.value++
    }

    private fun publish(progress: AnalysisProgress, gameIndex: Int, totalGames: Int) {
        val within = progress.fraction
        val overall = if (totalGames <= 1) within else ((gameIndex + within) / totalGames).coerceIn(0f, 1f)
        _state.value = _state.value.copy(
            running = true,
            kind = Kind.ANALYZE,
            detail = "Coup ${progress.done}/${progress.total}",
            progress = overall,
        )
    }

    fun cancel() {
        engines.stopCurrent()
        scope.launch {
            job?.cancelAndJoin()
            _state.value = State(result = "Tache interrompue")
            _revision.value++
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(result = null, error = null)
    }
}
