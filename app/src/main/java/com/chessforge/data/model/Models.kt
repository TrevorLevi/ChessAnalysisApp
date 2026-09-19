package com.chessforge.data.model

import com.chessforge.chess.Piece

/** Qualite d'un coup, dans le vocabulaire des joueurs. */
enum class Classification(val label: String, val short: String, val weight: Int) {
    BRILLIANT("Brillant", "!!", 0),
    GREAT("Tres fort", "!", 0),
    BEST("Meilleur coup", "=", 0),
    EXCELLENT("Excellent", "", 0),
    GOOD("Bon", "", 0),
    BOOK("Theorie", "", 0),
    FORCED("Force", "", 0),
    INACCURACY("Imprecision", "?!", 1),
    MISTAKE("Erreur", "?", 3),
    MISS("Occasion manquee", "x", 4),
    BLUNDER("Gaffe", "??", 6);

    val isError: Boolean get() = weight > 0

    companion object {
        fun fromName(name: String?): Classification =
            entries.firstOrNull { it.name == name } ?: GOOD
    }
}

/** Phase de la partie, deduite du materiel et du numero de coup. */
enum class Phase(val label: String) {
    OPENING("Ouverture"),
    MIDDLEGAME("Milieu de partie"),
    ENDGAME("Finale");

    companion object {
        fun fromName(name: String?): Phase = entries.firstOrNull { it.name == name } ?: MIDDLEGAME
    }
}

/**
 * Motif tactique ou thematique. Sert a la fois d'etiquette de puzzle et d'axe
 * d'agregation dans le tableau de bord.
 */
enum class Motif(val label: String, val advice: String) {
    MATE("Mat force", "Calculez les echecs jusqu'au bout avant de jouer autre chose."),
    BACK_RANK("Mat du couloir", "Verifiez la case de fuite de votre roi apres chaque echange."),
    FORK("Fourchette", "Reperez les cases d'ou une piece attaque deux cibles."),
    PIN("Clouage", "Avant de bouger, verifiez si la piece est clouee."),
    SKEWER("Enfilade", "Alignez roi et piece lourde adverse sur une meme ligne."),
    DISCOVERED("Attaque a la decouverte", "Cherchez les pieces qui masquent une ligne."),
    DOUBLE_ATTACK("Double attaque", "Un coup, deux menaces : cherchez-les systematiquement."),
    HANGING("Piece en l'air", "Faites l'inventaire des pieces non defendues a chaque coup."),
    TRAPPED("Piece piegee", "Comptez les cases de fuite de vos pieces avancees."),
    PROMOTION("Promotion", "Un pion sur la 7e vaut souvent une piece."),
    PASSED_PAWN("Pion passe", "Poussez ou bloquez : un passe decide les finales."),
    DEFLECTION("Deviation", "Detournez le defenseur de la case cle."),
    OVERLOAD("Defenseur surcharge", "Une piece qui garde deux choses n'en garde aucune."),
    SACRIFICE("Sacrifice", "Le materiel se rend si la suite est forcee."),
    COUNTING("Compte d'echanges", "Recomptez attaquants et defenseurs avant de prendre."),
    KING_SAFETY("Securite du roi", "Ne laissez pas ouvrir les lignes vers votre roi."),
    ENDGAME("Technique de finale", "Roi actif, pions passes, tours derriere les pions."),
    DEVELOPMENT("Developpement", "Sortez les pieces avant d'attaquer."),
    QUIET("Coup calme", "Le meilleur coup n'est pas toujours un echec ou une prise.");

    companion object {
        fun parseList(raw: String?): List<Motif> =
            raw?.split(",")?.mapNotNull { key -> entries.firstOrNull { it.name == key.trim() } }
                ?: emptyList()

        fun encode(motifs: List<Motif>): String = motifs.joinToString(",") { it.name }
    }
}

/** Origine du puzzle : ce qui explique pourquoi il a ete genere. */
enum class PuzzleKind(val label: String, val prompt: String) {
    BLUNDER_FIX("Gaffe a corriger", "Vous avez joue un coup perdant ici. Trouvez le bon."),
    MISSED_WIN("Gain manque", "Il y avait un gain net dans cette position. Trouvez-le."),
    PUNISH("Punir l'adversaire", "Votre adversaire vient de se tromper. Punissez."),
    DEFENSE("Defense a trouver", "La position est difficile. Trouvez la meilleure defense."),
    ENDGAME_DRILL("Exercice de finale", "Technique de finale : jouez le coup precis.");

    companion object {
        fun fromName(name: String?): PuzzleKind = entries.firstOrNull { it.name == name } ?: BLUNDER_FIX
    }
}

data class GameRecord(
    val id: String,
    val url: String?,
    val pgn: String,
    val white: String,
    val black: String,
    val whiteElo: Int?,
    val blackElo: Int?,
    val result: String,
    val userColor: Int,
    val userOutcome: String,
    val termination: String?,
    val timeControl: String?,
    val timeClass: String?,
    val eco: String?,
    val openingName: String?,
    val openingFamily: String?,
    val playedAt: Long,
    val rated: Boolean,
    val moveCount: Int,
    val userRating: Int?,
    val opponentRating: Int?,
    val analysis: GameAnalysisSummary? = null,
) {
    val opponent: String get() = if (userColor == Piece.WHITE) black else white
    val userColorLabel: String get() = if (userColor == Piece.WHITE) "Blancs" else "Noirs"
    val isAnalyzed: Boolean get() = analysis != null
}

data class GameAnalysisSummary(
    val analyzedAt: Long,
    val engineId: String,
    val depth: Int,
    val accuracy: Double,
    val acpl: Double,
    val opponentAccuracy: Double,
    val opponentAcpl: Double,
    val blunders: Int,
    val mistakes: Int,
    val inaccuracies: Int,
    val misses: Int,
    val bestMoves: Int,
    val openingAcpl: Double,
    val middlegameAcpl: Double,
    val endgameAcpl: Double,
)

data class MoveRecord(
    val gameId: String,
    val ply: Int,
    val san: String,
    val uci: String,
    val fenBefore: String,
    val sideToMove: Int,
    val byUser: Boolean,
    /** Evaluations du point de vue des blancs, en centipions bornes. */
    val evalBefore: Int,
    val evalAfter: Int,
    val mateBefore: Int?,
    val mateAfter: Int?,
    val cpLoss: Int,
    val winDrop: Double,
    val classification: Classification,
    val bestUci: String?,
    val bestSan: String?,
    val bestPvSan: String?,
    val phase: Phase,
    val motifs: List<Motif>,
    val clockSeconds: Double?,
    val secondsSpent: Double?,
) {
    val moveNumber: Int get() = ply / 2 + 1
    val notation: String get() = if (sideToMove == Piece.WHITE) "$moveNumber." else "$moveNumber..."
}

data class Puzzle(
    val id: Long,
    val gameId: String?,
    val ply: Int,
    val fen: String,
    /** Coups de la solution en UCI : l'utilisateur joue les indices pairs. */
    val solutionUci: List<String>,
    val solutionSan: List<String>,
    val kind: PuzzleKind,
    val motifs: List<Motif>,
    val phase: Phase,
    val difficulty: Int,
    val swingCp: Int,
    val playedSan: String?,
    val userColor: Int,
    val opponentName: String?,
    val playedAt: Long,
    val createdAt: Long,
    val srs: SrsState,
) {
    /** Camp qui doit jouer dans le puzzle. */
    val solverColor: Int get() = userColor
}

data class SrsState(
    val dueAt: Long,
    val intervalDays: Double,
    val ease: Double,
    val reps: Int,
    val lapses: Int,
    val retired: Boolean,
) {
    companion object {
        fun fresh(now: Long) = SrsState(now, 0.0, 2.5, 0, 0, false)
    }
}

data class PuzzleAttempt(
    val puzzleId: Long,
    val at: Long,
    val success: Boolean,
    val millis: Long,
    val firstMoveUci: String?,
    val hintsUsed: Int,
)

/** Point de notation chess.com, pour la courbe d'evolution. */
data class RatingPoint(val at: Long, val timeClass: String, val rating: Int)
