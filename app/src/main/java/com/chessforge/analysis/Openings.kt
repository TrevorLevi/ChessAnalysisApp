package com.chessforge.analysis

/**
 * Nommage des ouvertures et petit livre de theorie.
 *
 * chess.com fournit deja `ECO` et `ECOUrl` dans le PGN : on s'en sert en priorite,
 * c'est plus fiable qu'une table maison. Le livre ne sert qu'a ne pas compter les
 * premiers coups connus comme des "erreurs".
 */
object Openings {

    private val CUT_TOKENS = setOf(
        "Defense", "Defence", "Gambit", "Game", "Opening", "Attack",
        "System", "Variation", "Tarrasch", "Reversed",
    )

    /** Nom lisible complet, extrait de l'URL d'ouverture chess.com. */
    fun nameFromEcoUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val slug = url.trimEnd('/').substringAfterLast('/')
        if (slug.isBlank()) return null
        val words = slug.split('-')
            .takeWhile { token -> token.none { it.isDigit() } }
            .filter { it.isNotBlank() }
        if (words.isEmpty()) return null
        return words.joinToString(" ")
    }

    /**
     * Famille d'ouverture : on coupe apres le premier mot structurant pour regrouper
     * toutes les variantes d'une meme ouverture sous une seule etiquette.
     */
    fun familyFromName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val words = name.split(' ').filter { it.isNotBlank() }
        val out = ArrayList<String>(3)
        for (word in words) {
            out.add(word)
            if (word in CUT_TOKENS) break
            if (out.size >= 4) break
        }
        return out.joinToString(" ")
    }

    /** Famille approchee a partir du seul code ECO, quand l'URL manque. */
    fun familyFromEco(eco: String?): String? {
        if (eco.isNullOrBlank() || eco.length < 3) return null
        val letter = eco[0].uppercaseChar()
        val number = eco.substring(1).toIntOrNull() ?: return null
        return when (letter) {
            'A' -> when (number) {
                in 0..9 -> "Ouvertures irregulieres"
                in 10..39 -> "Anglaise"
                in 40..44 -> "Debuts de pion dame"
                in 45..49 -> "Indienne de dame"
                in 50..79 -> "Debuts indiens"
                in 80..99 -> "Hollandaise"
                else -> "Debut de flanc"
            }
            'B' -> when (number) {
                in 0..9 -> "Defenses semi-ouvertes"
                in 10..19 -> "Caro-Kann"
                in 20..99 -> "Sicilienne"
                else -> "Defense semi-ouverte"
            }
            'C' -> when (number) {
                in 0..19 -> "Francaise"
                in 20..99 -> "Debuts du pion roi"
                else -> "Debut du pion roi"
            }
            'D' -> when (number) {
                in 0..69 -> "Gambit dame"
                in 70..99 -> "Grunfeld"
                else -> "Debut du pion dame"
            }
            'E' -> when (number) {
                in 0..59 -> "Nimzo-indienne / Ouest-indienne"
                in 60..99 -> "Indienne du roi"
                else -> "Debut indien"
            }
            else -> null
        }
    }

    fun resolveFamily(ecoUrl: String?, eco: String?): String {
        val fromUrl = familyFromName(nameFromEcoUrl(ecoUrl))
        if (!fromUrl.isNullOrBlank()) return fromUrl
        return familyFromEco(eco) ?: "Inconnue"
    }

    /**
     * Lignes principales servant a marquer les coups "de theorie". Volontairement
     * courtes : au-dela, une erreur est une vraie erreur, meme dans une ouverture connue.
     */
    private val BOOK_LINES: List<String> = listOf(
        // Ouvertures 1.e4
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6",
        "e4 e5 Nf3 Nc6 Bb5 Nf6 O-O Nxe4",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6",
        "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6",
        "e4 e5 Nf3 Nf6 Nxe5 d6 Nf3 Nxe4",
        "e4 e5 Nc3 Nf6 f4 d5 fxe5 Nxe4",
        "e4 e5 Bc4 Nf6 d3 c6 Nf3 d5",
        "e4 e5 f4 exf4 Nf3 g5 h4 g4",
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6",
        "e4 c5 Nf3 Nc6 d4 cxd4 Nxd4 Nf6 Nc3 e5",
        "e4 c5 Nf3 e6 d4 cxd4 Nxd4 Nc6 Nc3 Qc7",
        "e4 c5 Nc3 Nc6 g3 g6 Bg2 Bg7",
        "e4 c5 c3 d5 exd5 Qxd5 d4 Nf6",
        "e4 c5 Bb5 Nc6 O-O",
        "e4 e6 d4 d5 Nc3 Nf6 e5 Nfd7",
        "e4 e6 d4 d5 Nc3 Bb4 e5 c5",
        "e4 e6 d4 d5 Nd2 Nf6 e5 Nfd7",
        "e4 e6 d4 d5 exd5 exd5 Nf3 Nf6",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5",
        "e4 c6 d4 d5 e5 Bf5 Nf3 e6",
        "e4 c6 d4 d5 exd5 cxd5 c4 Nf6",
        "e4 d5 exd5 Qxd5 Nc3 Qa5 d4 Nf6",
        "e4 Nf6 e5 Nd5 d4 d6 Nf3 dxe5",
        "e4 d6 d4 Nf6 Nc3 g6 Nf3 Bg7",
        "e4 g6 d4 Bg7 Nc3 d6 Nf3 Nf6",
        // Ouvertures 1.d4
        "d4 d5 c4 e6 Nc3 Nf6 Nf3 Be7",
        "d4 d5 c4 c6 Nf3 Nf6 Nc3 e6",
        "d4 d5 c4 dxc4 Nf3 Nf6 e3 e6",
        "d4 d5 c4 e5 dxe5 d4 Nf3 Nc6",
        "d4 d5 Nf3 Nf6 c4 e6 Nc3 Be7",
        "d4 Nf6 c4 e6 Nc3 Bb4 e3 O-O",
        "d4 Nf6 c4 e6 Nf3 b6 g3 Bb7",
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3 O-O",
        "d4 Nf6 c4 g6 Nc3 d5 cxd5 Nxd5",
        "d4 Nf6 Nf3 g6 c4 Bg7 Nc3 d5",
        "d4 f5 g3 Nf6 Bg2 e6 Nf3 d5",
        "d4 e6 c4 Nf6 Nc3 Bb4",
        "d4 c5 d5 Nf6 Nc3 Qa5",
        "d4 d6 Nf3 Nf6 c4 g6 Nc3 Bg7",
        // Flancs
        "c4 e5 Nc3 Nf6 Nf3 Nc6 g3 d5",
        "c4 Nf6 Nc3 e6 Nf3 d5 d4 Be7",
        "c4 c5 Nf3 Nf6 Nc3 Nc6 g3 g6",
        "Nf3 d5 d4 Nf6 c4 e6 Nc3 Be7",
        "Nf3 Nf6 g3 g6 Bg2 Bg7 O-O O-O",
        "g3 d5 Nf3 Nf6 Bg2 e6 O-O Be7",
        "b3 e5 Bb2 Nc6 e3 d5",
        "f4 d5 Nf3 Nf6 e3 g6",
        "e3 e5 c4 Nf6 Nc3 d5",
    )

    /** Ensemble de tous les prefixes de coups connus, pour un test en O(1). */
    private val BOOK_PREFIXES: Set<String> = buildSet {
        for (line in BOOK_LINES) {
            val moves = line.split(' ')
            for (i in 1..moves.size) add(moves.take(i).joinToString(" "))
        }
    }

    /** Vrai si la suite de coups jouee jusqu'ici (incluse) appartient a une ligne connue. */
    fun isBookMove(sanHistory: List<String>): Boolean {
        if (sanHistory.isEmpty() || sanHistory.size > MAX_BOOK_PLIES) return false
        return sanHistory.joinToString(" ") in BOOK_PREFIXES
    }

    /** Nombre de demi-coups au-dela duquel on ne parle plus de theorie. */
    const val MAX_BOOK_PLIES = 12
}
