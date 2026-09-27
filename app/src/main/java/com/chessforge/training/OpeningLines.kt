package com.chessforge.training

/**
 * Lignes d'ouverture servant a l'entrainement au repertoire.
 *
 * Elles sont volontairement courtes : l'objectif est de retenir les premiers coups
 * et l'idee generale, pas de reciter une variante jusqu'au 20e coup. Les noms et les
 * suites sont verifies par OpeningLineTest, qui rejoue chaque ligne sur l'echiquier.
 */
data class OpeningLine(
    val id: String,
    val name: String,
    /** Coups en notation algebrique abregee, dans l'ordre. */
    val moves: List<String>,
    /** Ce que la ligne cherche a obtenir, affiche a la fin de l'exercice. */
    val idea: String,
) {
    /** Couleur que l'utilisateur joue : celle qui commence la ligne. */
    val plies: Int get() = moves.size
}

object OpeningLines {

    private fun line(id: String, name: String, moves: String, idea: String) =
        OpeningLine(id, name, moves.split(" "), idea)

    val all: List<OpeningLine> = listOf(
        // --- Ouvertures du pion roi ---------------------------------------
        line(
            "ruy-lopez", "Espagnole, variante Morphy",
            "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6",
            "Le fou presse le cavalier qui defend e5. Les blancs jouent pour un centre " +
                "durable et une pression lente sur l'aile roi.",
        ),
        line(
            "italian", "Partie italienne",
            "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6",
            "Developpement rapide vers f7, puis d4 pour prendre le centre. La plus " +
                "naturelle des ouvertures ouvertes.",
        ),
        line(
            "two-knights", "Defense des deux cavaliers",
            "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5",
            "Les noirs acceptent une attaque immediate sur f7 et rendent un pion pour " +
                "prendre l'initiative.",
        ),
        line(
            "petrov", "Defense russe",
            "e4 e5 Nf3 Nf6 Nxe5 d6 Nf3 Nxe4",
            "Les noirs copient les blancs et cherchent la symetrie : une arme solide " +
                "contre les joueurs agressifs.",
        ),
        line(
            "kings-gambit", "Gambit du roi",
            "e4 e5 f4 exf4 Nf3 g5 h4 g4",
            "Un pion offert pour ouvrir la colonne f et attaquer tout de suite. " +
                "Tranchant des le troisieme coup.",
        ),
        line(
            "najdorf", "Sicilienne Najdorf",
            "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6",
            "a6 prepare b5 et prive les blancs de la case b5. La variante la plus " +
                "analysee de toute la theorie.",
        ),
        line(
            "taimanov", "Sicilienne Taimanov",
            "e4 c5 Nf3 e6 d4 cxd4 Nxd4 Nc6 Nc3 Qc7",
            "Un developpement souple qui garde le choix de la structure de pions.",
        ),
        line(
            "alapin", "Sicilienne Alapin",
            "e4 c5 c3 d5 exd5 Qxd5 d4 Nf6",
            "Les blancs evitent toute la theorie sicilienne en construisant un centre " +
                "de pions classique.",
        ),
        line(
            "french-steinitz", "Francaise, variante Steinitz",
            "e4 e6 d4 d5 Nc3 Nf6 e5 Nfd7",
            "Le centre se ferme. Les noirs attaqueront la chaine de pions par c5 et f6.",
        ),
        line(
            "french-tarrasch", "Francaise, variante Tarrasch",
            "e4 e6 d4 d5 Nd2 Nf6 e5 Nfd7",
            "Nd2 evite le clouage en b4 au prix d'un developpement plus modeste.",
        ),
        line(
            "caro-kann", "Caro-Kann, variante classique",
            "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5",
            "Les noirs sortent leur fou de cases claires avant de jouer e6 : c'est tout " +
                "l'interet du Caro-Kann sur la Francaise.",
        ),
        line(
            "scandinavian", "Defense scandinave",
            "e4 d5 exd5 Qxd5 Nc3 Qa5 d4 Nf6",
            "Un contre immediat au centre. La dame sort tot mais trouve une case sure en a5.",
        ),
        line(
            "alekhine", "Defense Alekhine",
            "e4 Nf6 e5 Nd5 d4 d6 Nf3 dxe5",
            "Les noirs invitent les pions blancs a avancer pour les attaquer ensuite.",
        ),
        line(
            "pirc", "Defense Pirc",
            "e4 d6 d4 Nf6 Nc3 g6 Nf3 Bg7",
            "Les noirs cedent le centre pour le contre-attaquer depuis le fianchetto.",
        ),

        // --- Ouvertures du pion dame --------------------------------------
        line(
            "qgd", "Gambit dame refuse",
            "d4 d5 c4 e6 Nc3 Nf6 Nf3 Be7",
            "Les noirs tiennent d5 avec e6 et acceptent un fou de cases claires passif " +
                "en echange d'une solidite a toute epreuve.",
        ),
        line(
            "slav", "Defense slave",
            "d4 d5 c4 c6 Nf3 Nf6 Nc3 e6",
            "c6 soutient d5 sans enfermer le fou de cases claires : le principal " +
                "reproche fait au Gambit dame refuse.",
        ),
        line(
            "qga", "Gambit dame accepte",
            "d4 d5 c4 dxc4 Nf3 Nf6 e3 e6",
            "Les noirs prennent le pion sans esperer le garder, et jouent pour c5.",
        ),
        line(
            "nimzo", "Nimzo-indienne, variante Rubinstein",
            "d4 Nf6 c4 e6 Nc3 Bb4 e3 O-O",
            "Le clouage du cavalier c3 controle e4. Les noirs echangent souvent le fou " +
                "contre le cavalier pour abimer la structure blanche.",
        ),
        line(
            "kings-indian", "Est-indienne classique",
            "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3 O-O",
            "Les noirs laissent les blancs occuper le centre, puis frappent par e5 ou c5 " +
                "en lancant une attaque sur l'aile roi.",
        ),
        line(
            "grunfeld", "Grunfeld, variante d'echange",
            "d4 Nf6 c4 g6 Nc3 d5 cxd5 Nxd5",
            "Les noirs rendent le centre pour le bombarder depuis le fianchetto. " +
                "Le contraire exact de l'Est-indienne.",
        ),

        // --- Ouvertures de flanc ------------------------------------------
        line(
            "english", "Anglaise, sicilienne inversee",
            "c4 e5 Nc3 Nf6 Nf3 Nc6 g3 d5",
            "Une sicilienne avec un tempo de plus. Jeu de position sur les cases claires.",
        ),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
