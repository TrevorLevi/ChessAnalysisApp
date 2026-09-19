package com.chessforge.analysis

import com.chessforge.data.db.PuzzleProgress
import com.chessforge.data.model.Motif
import com.chessforge.data.model.Phase

data class PlanItem(
    val title: String,
    val subtitle: String,
    val action: WeaknessAction,
    val targetCount: Int,
    val minutes: Int,
)

data class TrainingPlan(
    val items: List<PlanItem>,
    val dailyGoal: Int,
    val solvedToday: Int,
    val dueCount: Int,
) {
    val goalReached: Boolean get() = solvedToday >= dailyGoal
    val totalMinutes: Int get() = items.sumOf { it.minutes }
}

/**
 * Construit une seance du jour a partir des faiblesses mesurees.
 *
 * Regle de conception : une seance doit tenir en 20 a 30 minutes et ne jamais melanger
 * plus de trois themes, sinon rien ne se fixe.
 */
object TrainingPlanBuilder {

    fun build(
        weaknesses: List<Weakness>,
        progress: PuzzleProgress,
        dailyGoal: Int,
    ): TrainingPlan {
        val items = ArrayList<PlanItem>(4)

        if (progress.due > 0) {
            val count = minOf(progress.due, dailyGoal)
            items.add(
                PlanItem(
                    title = "Revoir les puzzles du jour",
                    subtitle = "$count position${if (count > 1) "s" else ""} a revoir, issues de vos parties",
                    action = WeaknessAction.TrainAll,
                    targetCount = count,
                    minutes = (count * 1.2).toInt().coerceAtLeast(3),
                )
            )
        }

        val topMotif = weaknesses.firstOrNull { it.action is WeaknessAction.TrainMotif }
        if (topMotif != null) {
            val motif = (topMotif.action as WeaknessAction.TrainMotif).motif
            items.add(
                PlanItem(
                    title = "Serie ciblee : ${motif.label.lowercase()}",
                    subtitle = topMotif.detail,
                    action = topMotif.action,
                    targetCount = 8,
                    minutes = 10,
                )
            )
        }

        val phaseWeakness = weaknesses.firstOrNull { it.action is WeaknessAction.TrainPhase }
        if (phaseWeakness != null) {
            val phase = (phaseWeakness.action as WeaknessAction.TrainPhase).phase
            items.add(
                PlanItem(
                    title = when (phase) {
                        Phase.ENDGAME -> "Exercices de finale"
                        Phase.OPENING -> "Revue de vos debuts de partie"
                        Phase.MIDDLEGAME -> "Positions de milieu de partie"
                    },
                    subtitle = phaseWeakness.detail,
                    action = phaseWeakness.action,
                    targetCount = 6,
                    minutes = 8,
                )
            )
        }

        val opening = weaknesses.firstOrNull { it.action is WeaknessAction.ReviewOpening }
        if (opening != null) {
            items.add(
                PlanItem(
                    title = "Reparer une ouverture",
                    subtitle = opening.detail,
                    action = opening.action,
                    targetCount = 2,
                    minutes = 7,
                )
            )
        }

        if (items.isEmpty()) {
            items.add(
                PlanItem(
                    title = "Importer et analyser vos parties",
                    subtitle = "Le plan se construit tout seul des que quelques parties sont analysees.",
                    action = WeaknessAction.None,
                    targetCount = 0,
                    minutes = 2,
                )
            )
        }

        return TrainingPlan(items.take(4), dailyGoal, progress.solvedToday, progress.due)
    }

    /** Motifs a travailler en priorite, pour alimenter une serie de puzzles. */
    fun priorityMotifs(weaknesses: List<Weakness>, limit: Int = 3): List<Motif> =
        weaknesses.mapNotNull { (it.action as? WeaknessAction.TrainMotif)?.motif }.take(limit)
}
