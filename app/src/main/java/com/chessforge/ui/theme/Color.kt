package com.chessforge.ui.theme

import androidx.compose.ui.graphics.Color

// --- Socle neutre (sombre par defaut : l'appli sert surtout le soir) ---
val Ink900 = Color(0xFF0F1115)
val Ink800 = Color(0xFF161A21)
val Ink700 = Color(0xFF1E242E)
val Ink600 = Color(0xFF2A323F)
val Ink500 = Color(0xFF3A4453)
val Mist400 = Color(0xFF8A93A3)
val Mist200 = Color(0xFFC9D1DE)
val Mist050 = Color(0xFFF2F5FA)

// --- Accents de marque ---
val Azure = Color(0xFF7FB2FF)
val AzureDeep = Color(0xFF2F6BD8)
val Gold = Color(0xFFE9B96B)
val GoldDeep = Color(0xFFB07F2E)
val Jade = Color(0xFF6FD3A4)
val Crimson = Color(0xFFEF5F52)

/**
 * Parametres de visualisation.
 *
 * Les teintes categorielles et les couleurs de statut viennent d'une palette deja
 * validee (bande de clarte, plancher de chroma, separation sous daltonisme, contraste
 * sur la surface) : l'ordre des emplacements *est* le mecanisme de securite, il ne
 * doit pas etre reordonne ni etendu au-dela de 8. Au-dela, on regroupe en "Autres".
 *
 * Le gris de chrome est decline sur l'axe froid du theme pour rester coherent avec
 * l'interface, a clarte equivalente.
 */
data class VizPalette(
    val surface: Color,
    val series: List<Color>,
    val good: Color,
    val warning: Color,
    val serious: Color,
    val critical: Color,
    val neutral: Color,
    /** Rampe sequentielle a teinte unique, du plus proche de la surface au plus contraste. */
    val sequential: List<Color>,
    val grid: Color,
    val axis: Color,
    val muted: Color,
) {
    /** Emplacement categoriel n (0 = premier). Jamais cycle : on borne a la taille. */
    fun series(index: Int): Color = series[index.coerceIn(0, series.lastIndex)]

    companion object {
        val Dark = VizPalette(
            surface = Ink800,
            series = listOf(
                Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500),
                Color(0xFFD55181), Color(0xFF008300), Color(0xFF9085E9), Color(0xFFE66767),
            ),
            good = Color(0xFF0CA30C),
            warning = Color(0xFFFAB219),
            serious = Color(0xFFEC835A),
            critical = Color(0xFFD03B3B),
            neutral = Color(0xFF6B7482),
            sequential = listOf(
                Color(0xFF0D366B), Color(0xFF104281), Color(0xFF184F95), Color(0xFF1C5CAB),
                Color(0xFF256ABF), Color(0xFF2A78D6), Color(0xFF3987E5), Color(0xFF5598E7),
                Color(0xFF6DA7EC), Color(0xFF86B6EF),
            ),
            grid = Color(0xFF242A33),
            axis = Color(0xFF333B47),
            muted = Mist400,
        )

        val Light = VizPalette(
            surface = Color.White,
            series = listOf(
                Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100),
                Color(0xFFE87BA4), Color(0xFF008300), Color(0xFF4A3AA7), Color(0xFFE34948),
            ),
            good = Color(0xFF0CA30C),
            warning = Color(0xFFFAB219),
            serious = Color(0xFFEC835A),
            critical = Color(0xFFD03B3B),
            neutral = Color(0xFF9AA3B0),
            sequential = listOf(
                Color(0xFFCDE2FB), Color(0xFFB7D3F6), Color(0xFF9EC5F4), Color(0xFF86B6EF),
                Color(0xFF6DA7EC), Color(0xFF5598E7), Color(0xFF3987E5), Color(0xFF2A78D6),
                Color(0xFF256ABF), Color(0xFF1C5CAB),
            ),
            grid = Color(0xFFE3E7ED),
            axis = Color(0xFFC3C8D1),
            muted = Color(0xFF6E7787),
        )
    }
}

/**
 * Themes de plateau.
 *
 * Les conventions visuelles reprennent celles des plateaux en ligne courants, dont
 * chess.com : case jouee surlignee en jaune translucide, cases accessibles marquees
 * d'un disque sombre translucide (et d'un anneau sur une prise), roi en echec cercle
 * de rouge. Ce sont des codes d'usage, pas des elements graphiques proprietaires.
 */
data class BoardPalette(
    val key: String,
    val label: String,
    val light: Color,
    val dark: Color,
    /** Case selectionnee et case du dernier coup : meme jaune, comme en ligne. */
    val highlight: Color,
    val lastMove: Color,
    /** Disque/anneau indiquant une destination legale. */
    val hint: Color,
    val check: Color,
) {
    companion object {
        private val HINT = Color(0x26000000)
        private val YELLOW = Color(0x8CF7F769)
        private val CHECK = Color(0x99E03131)

        val Green = BoardPalette(
            "forest", "Vert",
            light = Color(0xFFEBECD0), dark = Color(0xFF739552),
            highlight = YELLOW, lastMove = YELLOW, hint = HINT, check = CHECK,
        )
        val Blue = BoardPalette(
            "slate", "Bleu",
            light = Color(0xFFDEE3E6), dark = Color(0xFF8CA2AD),
            highlight = YELLOW, lastMove = YELLOW, hint = HINT, check = CHECK,
        )
        val Walnut = BoardPalette(
            "walnut", "Bois",
            light = Color(0xFFF0D9B5), dark = Color(0xFFB58863),
            highlight = YELLOW, lastMove = YELLOW, hint = HINT, check = CHECK,
        )
        val all = listOf(Green, Blue, Walnut)

        // Les cles historiques sont conservees pour ne pas perdre le choix deja
        // enregistre dans les preferences.
        fun byKey(key: String) = all.firstOrNull { it.key == key } ?: Green
    }
}
