package com.chessforge.chess

import kotlin.random.Random

/** Cles de hachage Zobrist, graine fixe pour que les cles soient stables entre sessions. */
object Zobrist {
    private val rng = Random(0x5EED_C4E5_5L)

    val pieces: Array<LongArray> = Array(15) { LongArray(64) { rng.nextLong() } }
    val castling: LongArray = LongArray(16) { rng.nextLong() }
    val epFile: LongArray = LongArray(8) { rng.nextLong() }
    val sideToMove: Long = rng.nextLong()
}
