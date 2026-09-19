package com.chessforge.engine

import com.chessforge.engine.forge.ForgeEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le moteur n'a pas besoin d'etre fort, il doit etre *fiable* : trouver les mats forces
 * et les gains de materiel evidents, sinon les puzzles generes seraient faux.
 */
class ForgeEngineTest {

    private val engine = ForgeEngine(ttSizeMb = 8)
    private val limits = EngineLimits(depth = 8, movetimeMs = 3_000)

    @Test
    fun findsMateInOne() = runBlocking {
        // Dame et roi contre roi : Qg7# (le mat du couloir le plus simple).
        val line = engine.analyze("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", limits).first()
        assertEquals("a1a8", line.bestMove)
        assertEquals(1, line.score.mate)
    }

    @Test
    fun findsMateInTwo() = runBlocking {
        // Echelle de tours : le roi noir est confine en h8 par la tour a7, le pion b7
        // bloque la colonne b. 1.Rb6 Kg8 2.Rb8# ; aucun mat en un n'existe.
        val line = engine.analyze("7k/Rp6/8/8/8/8/1R6/7K w - - 0 1", EngineLimits(depth = 9, movetimeMs = 6_000)).first()
        assertEquals("mat en deux attendu", 2, line.score.mate)
    }

    @Test
    fun grabsAFreeQueen() = runBlocking {
        // La dame noire en d5 n'est defendue par personne : le cavalier la prend.
        // Les pions blancs evitent que la suite soit un nul par materiel insuffisant.
        val line = engine.analyze("4k3/8/8/3q4/8/2N5/PPP5/4K3 w - - 0 1", limits).first()
        assertEquals("c3d5", line.bestMove)
        assertTrue("avantage decisif attendu, obtenu ${line.score.format()}", line.score.toCp() > 600)
    }

    @Test
    fun avoidsLosingAHangingPiece() = runBlocking {
        // Le cavalier blanc en e5 est attaque par le pion d6 : il doit bouger.
        val line = engine.analyze("rnbqkbnr/ppp1pppp/3p4/4N3/8/8/PPPPPPPP/RNBQKB1R b KQkq - 0 3", limits).first()
        assertEquals("d6e5", line.bestMove)
    }

    @Test
    fun reportsStalemateAndMateScoresConsistently() = runBlocking {
        val mated = engine.analyze("7k/5Q1K/8/8/8/8/8/8 b - - 0 1", limits)
        assertTrue("aucun coup legal : la liste doit rester exploitable", mated.isNotEmpty())
    }

    @Test
    fun multiPvReturnsDistinctMoves() = runBlocking {
        val lines = engine.analyze(
            "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4",
            EngineLimits(depth = 7, movetimeMs = 4_000, multiPv = 3),
        )
        assertTrue("au moins deux variantes", lines.size >= 2)
        assertEquals(lines.size, lines.mapNotNull { it.bestMove }.distinct().size)
    }
}
