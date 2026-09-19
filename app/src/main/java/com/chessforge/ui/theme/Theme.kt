package com.chessforge.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.chessforge.data.model.Classification
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkScheme = darkColorScheme(
    primary = Azure,
    onPrimary = Color(0xFF06203F),
    primaryContainer = AzureDeep,
    onPrimaryContainer = Mist050,
    secondary = Gold,
    onSecondary = Color(0xFF2B1D05),
    secondaryContainer = GoldDeep,
    onSecondaryContainer = Mist050,
    tertiary = Jade,
    onTertiary = Color(0xFF05271A),
    background = Ink900,
    onBackground = Mist200,
    surface = Ink800,
    onSurface = Mist200,
    surfaceVariant = Ink700,
    onSurfaceVariant = Mist400,
    surfaceContainer = Ink700,
    surfaceContainerHigh = Ink600,
    surfaceContainerHighest = Ink500,
    outline = Ink500,
    outlineVariant = Ink600,
    error = Crimson,
    onError = Color(0xFF3A0B07),
)

private val LightScheme = lightColorScheme(
    primary = AzureDeep,
    onPrimary = Color.White,
    secondary = GoldDeep,
    onSecondary = Color.White,
    tertiary = Color(0xFF1F7A56),
    background = Mist050,
    onBackground = Ink900,
    surface = Color.White,
    onSurface = Ink900,
    surfaceVariant = Color(0xFFE6EBF2),
    onSurfaceVariant = Color(0xFF4A5462),
    outline = Color(0xFFB4BECB),
    error = Color(0xFFB3261E),
)

@Composable
fun ChessForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalViz provides if (darkTheme) VizPalette.Dark else VizPalette.Light) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ForgeTypography,
            shapes = ForgeShapes,
            content = content,
        )
    }
}

/** Parametres de visualisation du theme courant. */
val LocalViz = staticCompositionLocalOf { VizPalette.Dark }

/**
 * Couleur de statut d'une classification de coup.
 *
 * Ces couleurs *signifient* un etat (correct / imprecis / grave / critique), elles ne
 * sont jamais reutilisees comme teintes de serie. Elles ne portent jamais le sens
 * seules : chaque usage est accompagne du symbole et du libelle de la classification.
 */
@Composable
fun Classification.statusColor(): Color {
    val viz = LocalViz.current
    return when (this) {
        Classification.BRILLIANT, Classification.GREAT, Classification.BEST,
        Classification.EXCELLENT, Classification.GOOD -> viz.good
        Classification.BOOK, Classification.FORCED -> viz.neutral
        Classification.INACCURACY -> viz.warning
        Classification.MISTAKE, Classification.MISS -> viz.serious
        Classification.BLUNDER -> viz.critical
    }
}

/** Symbole court affiche dans la pastille, pour ne jamais dependre de la couleur seule. */
val Classification.glyph: String
    get() = when (this) {
        Classification.BRILLIANT -> "!!"
        Classification.GREAT -> "!"
        Classification.BEST -> "★"
        Classification.EXCELLENT -> "✓"
        Classification.GOOD -> "·"
        Classification.BOOK -> "■"
        Classification.FORCED -> "→"
        Classification.INACCURACY -> "?!"
        Classification.MISTAKE -> "?"
        Classification.MISS -> "✕"
        Classification.BLUNDER -> "??"
    }
