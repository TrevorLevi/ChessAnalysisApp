package com.chessforge.training

import com.chessforge.data.model.Motif

/**
 * Exercices d'entrainement generiques, independants des parties de l'utilisateur.
 *
 * Contrairement aux puzzles personnels, ces positions sont ecrites a la main. Elles
 * sont donc toutes verifiees par DrillContentTest, qui demande au moteur de confirmer
 * la solution annoncee ou l'issue attendue : un exercice dont la reponse serait fausse
 * egarerait plus qu'il n'apprendrait.
 */

/** Ce que le joueur doit accomplir dans un exercice de finale. */
enum class DrillGoal(val label: String, val instruction: String) {
    MATE("Mater", "Menez la position au mat."),
    PROMOTE("Promouvoir", "Amenez un pion a promotion."),
    DRAW("Tenir la nulle", "Obtenez la nulle : votre adversaire cherche a gagner."),
}

data class EndgameDrill(
    val id: String,
    val title: String,
    val fen: String,
    val goal: DrillGoal,
    /** Le principe a retenir, affiche avant de commencer. */
    val lesson: String,
    val difficulty: Int,
)

data class TacticDrill(
    val id: String,
    val fen: String,
    /** Coup attendu, en notation UCI. Verifie par le moteur dans les tests. */
    val solutionUci: String,
    val motif: Motif,
    val hint: String,
    val difficulty: Int,
)

object Drills {

    /**
     * Finales classiques. Elles se jouent contre le moteur : c'est la seule facon
     * d'apprendre une technique, la connaitre de memoire ne suffit pas.
     */
    val endgames: List<EndgameDrill> = listOf(
        EndgameDrill(
            id = "eg-queen-mate",
            title = "Dame contre roi",
            fen = "4k3/8/8/8/8/8/8/3QK3 w - - 0 1",
            goal = DrillGoal.MATE,
            lesson = "Reduisez la case du roi adverse avec la dame, puis approchez votre roi. " +
                "Attention au pat : laissez toujours une case libre tant que votre roi n'est pas la.",
            difficulty = 1,
        ),
        EndgameDrill(
            id = "eg-rook-mate",
            title = "Tour contre roi",
            fen = "4k3/8/8/8/8/8/8/R3K3 w - - 0 1",
            goal = DrillGoal.MATE,
            lesson = "La tour coupe le roi adverse sur une ligne, le votre le pousse. " +
                "L'opposition des rois est l'outil central.",
            difficulty = 2,
        ),
        EndgameDrill(
            id = "eg-ladder-mate",
            title = "L'echelle, deux tours",
            fen = "4k3/8/8/8/8/8/8/R5RK w - - 0 1",
            goal = DrillGoal.MATE,
            lesson = "Chaque tour prend une rangee et pousse le roi vers le bord. " +
                "Quand le roi approche d'une tour, faites-la glisser a l'autre bout.",
            difficulty = 1,
        ),
        EndgameDrill(
            id = "eg-opposition",
            title = "Roi devant le pion",
            fen = "8/8/3k4/8/3K4/8/3P4/8 w - - 0 1",
            goal = DrillGoal.PROMOTE,
            lesson = "Le roi precede le pion, jamais l'inverse. Le pion garde un tempo en " +
                "reserve : c'est lui qui vous donnera l'opposition au bon moment.",
            difficulty = 3,
        ),
        EndgameDrill(
            id = "eg-square-rule",
            title = "La regle du carre",
            fen = "7k/8/8/8/8/8/P7/6K1 w - - 0 1",
            goal = DrillGoal.PROMOTE,
            lesson = "Tracez le carre dont le pion occupe un coin : si le roi adverse ne peut " +
                "y entrer, le pion passe seul. Poussez sans hesiter.",
            difficulty = 1,
        ),
        EndgameDrill(
            id = "eg-queen-vs-pawn",
            title = "Dame contre pion sur la 7e",
            fen = "8/8/8/7Q/8/3k4/3p4/7K w - - 0 1",
            goal = DrillGoal.MATE,
            lesson = "Donnez des echecs qui forcent le roi devant son pion : chaque fois qu'il " +
                "le bloque, vous gagnez un tempo pour rapprocher votre roi.",
            difficulty = 4,
        ),
        EndgameDrill(
            id = "eg-lucena",
            title = "Lucena : construire le pont",
            fen = "1K6/1P1k4/8/8/8/8/r7/2R5 w - - 0 1",
            goal = DrillGoal.PROMOTE,
            lesson = "Placez la tour sur la 4e rangee, sortez le roi, puis interposez la tour " +
                "contre les echecs. La position de gain la plus utile des finales de tours.",
            difficulty = 5,
        ),
        EndgameDrill(
            id = "eg-wrong-bishop",
            title = "Fou de mauvaise couleur",
            fen = "7k/8/8/8/8/8/5B1P/6K1 b - - 0 1",
            goal = DrillGoal.DRAW,
            lesson = "Le fou ne controle pas la case de promotion : le roi qui reste dans le " +
                "coin ne peut pas etre chasse. Ne sortez jamais de h8 et g8.",
            difficulty = 3,
        ),
    )

    /**
     * Motifs tactiques elementaires. Chaque solution est confrontee au moteur dans les
     * tests : seules les positions ou le meilleur coup est net figurent ici.
     */
    val tactics: List<TacticDrill> = listOf(
        TacticDrill(
            id = "tc-backrank-rook",
            fen = "6k1/5ppp/8/8/8/8/8/4R1K1 w - - 0 1",
            solutionUci = "e1e8",
            motif = Motif.BACK_RANK,
            hint = "Les pions du roque n'ont jamais bouge.",
            difficulty = 1,
        ),
        TacticDrill(
            id = "tc-backrank-queen",
            fen = "6k1/5ppp/8/8/8/8/8/3Q2K1 w - - 0 1",
            solutionUci = "d1d8",
            motif = Motif.BACK_RANK,
            hint = "Trouvez la case de la derniere rangee que le roi ne peut pas atteindre.",
            difficulty = 1,
        ),
        TacticDrill(
            id = "tc-knight-fork",
            fen = "3q3k/8/8/4N3/8/8/8/6K1 w - - 0 1",
            solutionUci = "e5f7",
            motif = Motif.FORK,
            hint = "Une seule case attaque le roi et la dame en meme temps.",
            difficulty = 2,
        ),
        TacticDrill(
            id = "tc-pawn-fork",
            fen = "4k3/8/2n1b3/8/2PP4/8/8/4K3 w - - 0 1",
            solutionUci = "d4d5",
            motif = Motif.FORK,
            hint = "Un pion peut attaquer deux pieces, a condition d'etre lui-meme defendu.",
            difficulty = 2,
        ),
        TacticDrill(
            id = "tc-hanging-queen",
            fen = "4k3/8/8/3q4/8/2N5/PPP5/4K3 w - - 0 1",
            solutionUci = "c3d5",
            motif = Motif.HANGING,
            hint = "Faites l'inventaire des pieces adverses non defendues.",
            difficulty = 1,
        ),
        TacticDrill(
            id = "tc-free-rook",
            fen = "4k3/8/8/8/7r/8/5B2/4K3 w - - 0 1",
            solutionUci = "f2h4",
            motif = Motif.HANGING,
            hint = "Suivez la diagonale du fou jusqu'au bout.",
            difficulty = 1,
        ),
        TacticDrill(
            id = "tc-promotion",
            fen = "8/P6k/8/8/8/8/8/K7 w - - 0 1",
            solutionUci = "a7a8q",
            motif = Motif.PROMOTION,
            hint = "Le roi noir est trop loin pour intervenir.",
            difficulty = 1,
        ),
    )

    fun endgame(id: String) = endgames.firstOrNull { it.id == id }
    fun tactic(id: String) = tactics.firstOrNull { it.id == id }
}
