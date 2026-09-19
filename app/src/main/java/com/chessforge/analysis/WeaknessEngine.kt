package com.chessforge.analysis

import com.chessforge.data.db.GlobalSummary
import com.chessforge.data.db.MotifScore
import com.chessforge.data.db.OpeningRow
import com.chessforge.data.db.PhaseRow
import com.chessforge.data.db.TimeClassRow
import com.chessforge.data.db.TimePressureRow
import com.chessforge.data.model.Motif
import com.chessforge.data.model.Phase
import kotlin.math.roundToInt

/** Ce sur quoi on peut cliquer pour travailler une faiblesse. */
sealed interface WeaknessAction {
    data class TrainMotif(val motif: Motif) : WeaknessAction
    data class TrainPhase(val phase: Phase) : WeaknessAction
    data class ReviewOpening(val family: String) : WeaknessAction
    data object TrainAll : WeaknessAction
    data object SlowDown : WeaknessAction
    data object None : WeaknessAction
}

data class Weakness(
    val id: String,
    val title: String,
    /** Constat chiffre, formule pour etre lu sans jargon. */
    val detail: String,
    val advice: String,
    /** 0-100 : combien cela vous coute par rapport au reste. */
    val severity: Int,
    val metricValue: String,
    val metricLabel: String,
    val action: WeaknessAction,
)

data class WeaknessInput(
    val summary: GlobalSummary,
    val phases: List<PhaseRow>,
    val motifErrors: Map<Motif, Int>,
    val motifScores: List<MotifScore>,
    val openings: List<OpeningRow>,
    val timeClasses: List<TimeClassRow>,
    val timePressure: List<TimePressureRow>,
    val errorsByMoveNumber: List<Pair<String, Double>>,
    val colorSplit: Pair<OpeningRow, OpeningRow>,
)

/**
 * Transforme les statistiques brutes en liste de faiblesses classees.
 *
 * Le principe : une faiblesse n'est interessante que si elle est *frequente* et
 * *couteuse*. Chaque regle produit donc une severite calculee sur ces deux axes,
 * pas sur une impression.
 */
object WeaknessEngine {

    fun analyze(input: WeaknessInput): List<Weakness> {
        val out = ArrayList<Weakness>()
        val totalErrors = input.motifErrors.values.sum().coerceAtLeast(1)

        // 1. Motifs tactiques qui reviennent dans vos erreurs
        for ((motif, count) in input.motifErrors.entries.take(6)) {
            if (count < 3) continue
            val share = count.toDouble() / totalErrors
            val puzzleScore = input.motifScores.firstOrNull { it.motif == motif }
            val failRate = puzzleScore?.let { if (it.attempts >= 4) 1.0 - it.successRate else null }
            val severity = (share * 70 + (failRate ?: 0.35) * 30).scaleTo100()
            out.add(
                Weakness(
                    id = "motif:${motif.name}",
                    title = motif.label,
                    detail = buildString {
                        append("$count erreurs liees a ce motif")
                        append(" (${(share * 100).roundToInt()} % de vos fautes)")
                        if (failRate != null) {
                            append(", et ${((1 - failRate) * 100).roundToInt()} % de reussite sur ces puzzles")
                        }
                    },
                    advice = motif.advice,
                    severity = severity,
                    metricValue = count.toString(),
                    metricLabel = "erreurs",
                    action = WeaknessAction.TrainMotif(motif),
                )
            )
        }

        // 2. Phase de jeu la plus couteuse
        val phasesWithData = input.phases.filter { it.moves >= 20 }
        if (phasesWithData.size >= 2) {
            val worst = phasesWithData.maxBy { it.avgCpLoss }
            val others = phasesWithData.filter { it.phase != worst.phase }
            val reference = others.map { it.avgCpLoss }.average()
            if (worst.avgCpLoss > reference * 1.25) {
                val excess = (worst.avgCpLoss - reference) / reference
                out.add(
                    Weakness(
                        id = "phase:${worst.phase.name}",
                        title = "Phase faible : ${worst.phase.label.lowercase()}",
                        detail = "Vous perdez ${worst.avgCpLoss.roundToInt()} centipions par coup en " +
                            "${worst.phase.label.lowercase()}, contre ${reference.roundToInt()} ailleurs.",
                        advice = when (worst.phase) {
                            Phase.OPENING -> "Revoyez les 10 premiers coups de vos ouvertures les plus jouees."
                            Phase.MIDDLEGAME -> "Travaillez les plans typiques et la securite du roi."
                            Phase.ENDGAME -> "Entrainez les finales de base : roi actif, pions passes, tours."
                        },
                        severity = (excess * 60 + 30).scaleTo100(),
                        metricValue = worst.avgCpLoss.roundToInt().toString(),
                        metricLabel = "cp perdus / coup",
                        action = WeaknessAction.TrainPhase(worst.phase),
                    )
                )
            }
        }

        // 3. Fuites d'ouverture : familles ou votre score s'effondre
        for (opening in input.openings.filter { it.games >= 3 }.sortedBy { it.score }.take(2)) {
            if (opening.score > 0.42) continue
            out.add(
                Weakness(
                    id = "opening:${opening.family}",
                    title = "Ouverture en souffrance : ${opening.family}",
                    detail = "${(opening.score * 100).roundToInt()} % de score sur ${opening.games} parties " +
                        "(${opening.wins}V / ${opening.draws}N / ${opening.losses}D).",
                    advice = "Choisissez une ligne, notez les 8 premiers coups, et rejouez vos defaites pour " +
                        "trouver le coup ou vous sortez du plan.",
                    severity = ((0.5 - opening.score) * 120 + opening.games * 2).scaleTo100(),
                    metricValue = "${(opening.score * 100).roundToInt()} %",
                    metricLabel = "score",
                    action = WeaknessAction.ReviewOpening(opening.family),
                )
            )
        }

        // 4. Gestion du temps : les coups joues trop vite
        val fast = input.timePressure.filter { it.bucketLabel == "< 2 s" || it.bucketLabel == "2-5 s" }
        val slow = input.timePressure.filter { it.bucketLabel !in setOf("< 2 s", "2-5 s") }
        if (fast.isNotEmpty() && slow.isNotEmpty()) {
            val fastRate = fast.sumOf { it.errorRate * it.moves } / fast.sumOf { it.moves }
            val slowRate = slow.sumOf { it.errorRate * it.moves } / slow.sumOf { it.moves }
            if (fastRate > slowRate * 1.4 && fastRate > 0.06) {
                out.add(
                    Weakness(
                        id = "time:fast",
                        title = "Coups joues trop vite",
                        detail = "${(fastRate * 100).roundToInt()} % d'erreurs sur les coups joues en moins de " +
                            "5 secondes, contre ${(slowRate * 100).roundToInt()} % sinon.",
                        advice = "Avant de jouer, posez-vous une seule question : qu'est-ce que son coup menace ?",
                        severity = ((fastRate - slowRate) * 260).scaleTo100(),
                        metricValue = "${(fastRate * 100).roundToInt()} %",
                        metricLabel = "erreurs en < 5 s",
                        action = WeaknessAction.SlowDown,
                    )
                )
            }
        }

        // 5. Cadence la plus defavorable
        val cadences = input.timeClasses.filter { it.games >= 4 }
        if (cadences.size >= 2) {
            val worst = cadences.minBy { it.score }
            val bestCadence = cadences.maxBy { it.score }
            if (bestCadence.score - worst.score > 0.15) {
                out.add(
                    Weakness(
                        id = "cadence:${worst.timeClass}",
                        title = "Cadence a risque : ${worst.timeClass}",
                        detail = "${(worst.score * 100).roundToInt()} % de score en ${worst.timeClass} " +
                            "contre ${(bestCadence.score * 100).roundToInt()} % en ${bestCadence.timeClass}, " +
                            "avec ${"%.1f".format(worst.blundersPerGame)} gaffes par partie.",
                        advice = "Si vous voulez progresser, jouez la cadence longue pour apprendre et " +
                            "la courte pour vous amuser, pas l'inverse.",
                        severity = ((bestCadence.score - worst.score) * 200).scaleTo100(),
                        metricValue = "${(worst.score * 100).roundToInt()} %",
                        metricLabel = "score",
                        action = WeaknessAction.None,
                    )
                )
            }
        }

        // 6. Taux de gaffes global
        if (input.summary.blundersPer100 > 2.5) {
            out.add(
                Weakness(
                    id = "global:blunders",
                    title = "Frequence des gaffes",
                    detail = "${"%.1f".format(input.summary.blundersPer100)} gaffes pour 100 coups joues.",
                    advice = "Un controle en deux temps avant chaque coup : mes pieces en l'air, ses menaces.",
                    severity = (input.summary.blundersPer100 * 18).scaleTo100(),
                    metricValue = "%.1f".format(input.summary.blundersPer100),
                    metricLabel = "gaffes / 100 coups",
                    action = WeaknessAction.TrainAll,
                )
            )
        }

        // 7. Ecart entre les deux couleurs
        val (white, black) = input.colorSplit
        if (white.games >= 5 && black.games >= 5) {
            val delta = white.score - black.score
            if (kotlin.math.abs(delta) > 0.15) {
                val weak = if (delta > 0) black else white
                out.add(
                    Weakness(
                        id = "color:${weak.family}",
                        title = "Desequilibre avec les ${weak.family.lowercase()}",
                        detail = "${(weak.score * 100).roundToInt()} % de score avec les " +
                            "${weak.family.lowercase()} sur ${weak.games} parties.",
                        advice = "Preparez un repertoire simple et unique pour cette couleur.",
                        severity = (kotlin.math.abs(delta) * 150).scaleTo100(),
                        metricValue = "${(weak.score * 100).roundToInt()} %",
                        metricLabel = "score",
                        action = WeaknessAction.None,
                    )
                )
            }
        }

        // 8. Effondrement en fin de partie
        val buckets = input.errorsByMoveNumber
        if (buckets.size >= 4) {
            val firstHalf = buckets.take(buckets.size / 2).map { it.second }.average()
            val secondHalf = buckets.drop(buckets.size / 2).map { it.second }.average()
            if (secondHalf > firstHalf * 1.5 && secondHalf > 0.08) {
                out.add(
                    Weakness(
                        id = "stamina:late",
                        title = "Baisse de vigilance en fin de partie",
                        detail = "${(secondHalf * 100).roundToInt()} % d'erreurs apres le coup " +
                            "${buckets[buckets.size / 2].first.substringBefore('-')} contre " +
                            "${(firstHalf * 100).roundToInt()} % avant.",
                        advice = "Gardez du temps pour la fin : votre probleme n'est pas l'ouverture.",
                        severity = ((secondHalf - firstHalf) * 240).scaleTo100(),
                        metricValue = "${(secondHalf * 100).roundToInt()} %",
                        metricLabel = "erreurs tardives",
                        action = WeaknessAction.TrainPhase(Phase.ENDGAME),
                    )
                )
            }
        }

        return out.sortedByDescending { it.severity }
    }

    /** Chaque regle produit deja une valeur sur une echelle de 100 : on borne seulement. */
    private fun Double.scaleTo100(): Int = roundToInt().coerceIn(5, 100)
}
