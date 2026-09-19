package com.chessforge.engine.sf

import android.util.Log

/**
 * Pont JNI vers Stockfish compile dans l'APK.
 *
 * Stockfish tourne *dans* le processus (pas comme un binaire externe : Android
 * interdit d'executer un fichier depuis le repertoire de donnees de l'appli).
 * Sa boucle UCI lit et ecrit sur deux tubes rediriges vers stdin/stdout.
 *
 * Si la bibliotheque n'a pas ete compilee, [available] vaut false et l'appli
 * retombe sur son moteur Kotlin.
 */
object NativeBridge {

    val available: Boolean by lazy {
        try {
            System.loadLibrary("chessforge_sf")
            true
        } catch (e: Throwable) {
            Log.i(TAG, "Stockfish natif absent, moteur integre utilise (${e.message})")
            false
        }
    }

    @JvmStatic
    external fun nativeStart(): Boolean

    @JvmStatic
    external fun nativeWrite(command: String)

    /** Lit une ligne de la sortie du moteur ; bloque jusqu'a ce qu'une ligne arrive. */
    @JvmStatic
    external fun nativeReadLine(): String?

    @JvmStatic
    external fun nativeStop()

    private const val TAG = "NativeBridge"
}
