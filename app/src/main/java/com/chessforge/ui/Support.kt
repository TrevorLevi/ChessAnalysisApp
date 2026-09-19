package com.chessforge.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chessforge.di.AppContainer
import com.chessforge.di.container
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Cree une ViewModel en lui passant le conteneur de dependances de l'application. */
@Composable
inline fun <reified VM : ViewModel> forgeViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalContext.current.container
    return viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = create(container) as T
        },
    )
}

private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)
private val DATE_SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)
private val DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy 'a' HH:mm", Locale.FRENCH)

object Format {

    fun date(epochSeconds: Long): String =
        DATE.format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()))

    fun dateShort(epochSeconds: Long): String =
        DATE_SHORT.format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()))

    fun dateTime(epochSeconds: Long): String =
        DATE_TIME.format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()))

    fun relative(epochSeconds: Long, now: Long = System.currentTimeMillis() / 1000): String {
        val delta = now - epochSeconds
        return when {
            delta < 60 -> "a l'instant"
            delta < 3_600 -> "il y a ${delta / 60} min"
            delta < 86_400 -> "il y a ${delta / 3_600} h"
            delta < 86_400 * 7 -> "il y a ${delta / 86_400} j"
            else -> date(epochSeconds)
        }
    }

    fun percent(value: Double, decimals: Int = 0): String =
        String.format(Locale.FRENCH, "%.${decimals}f %%", value)

    /** Ratio 0-1 exprime en pourcentage. */
    fun ratio(value: Double, decimals: Int = 0): String = percent(value * 100, decimals)

    fun oneDecimal(value: Double): String = String.format(Locale.FRENCH, "%.1f", value)

    fun duration(seconds: Double): String = when {
        seconds < 60 -> "${seconds.toInt()} s"
        seconds < 3_600 -> "${(seconds / 60).toInt()} min"
        else -> "${(seconds / 3_600).toInt()} h"
    }

    fun outcome(userOutcome: String): String = when (userOutcome) {
        "win" -> "Victoire"
        "loss" -> "Defaite"
        else -> "Nulle"
    }

    fun timeClass(raw: String?): String = when (raw) {
        "bullet" -> "Bullet"
        "blitz" -> "Blitz"
        "rapid" -> "Rapide"
        "daily" -> "Quotidien"
        null -> "Inconnu"
        else -> raw.replaceFirstChar { it.uppercase() }
    }
}
