package com.chessforge.chess

import com.chessforge.chess.pgn.PgnParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Perft : le seul test qui prouve qu'un generateur de coups est juste.
 * Les valeurs de reference viennent des positions standard du Chess Programming Wiki.
 */
class PerftTest {

    private fun perft(position: Position, depth: Int): Long {
        if (depth == 0) return 1L
        val list = MoveList()
        position.generateMoves(list)
        var nodes = 0L
        for (i in 0 until list.size) {
            val m = list[i]
            if (position.makeMove(m)) {
                nodes += if (depth == 1) 1L else perft(position, depth - 1)
                position.unmakeMove()
            }
        }
        return nodes
    }

    private fun check(fen: String, expected: LongArray) {
        val position = Fen.parse(fen)
        for ((index, want) in expected.withIndex()) {
            assertEquals("perft(${index + 1}) sur $fen", want, perft(position, index + 1))
        }
    }

    @Test
    fun startPosition() = check(Fen.START, longArrayOf(20, 400, 8902, 197281, 4865609))

    @Test
    fun kiwipete() = check(
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
        longArrayOf(48, 2039, 97862, 4085603),
    )

    @Test
    fun enPassantAndPromotionEdgeCases() = check(
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
        longArrayOf(14, 191, 2812, 43238, 674624),
    )

    @Test
    fun castlingUnderFire() = check(
        "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
        longArrayOf(6, 264, 9467, 422333),
    )

    @Test
    fun promotionHeavy() = check(
        "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
        longArrayOf(44, 1486, 62379, 2103487),
    )

    @Test
    fun fenRoundTrip() {
        val fens = listOf(
            Fen.START,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 b - - 3 17",
        )
        for (fen in fens) assertEquals(fen, Fen.of(Fen.parse(fen)))
    }

    @Test
    fun sanRoundTripOnEveryLegalMove() {
        val position = Fen.parse("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        for (move in position.legalMoves()) {
            val san = San.of(position, move)
            assertEquals("aller-retour SAN pour $san", move, San.parse(position, san))
        }
    }

    @Test
    fun zobristKeyIsRestoredAfterUnmake() {
        val position = Fen.parse(Fen.START)
        val before = position.key
        for (move in position.legalMoves()) {
            position.makeMove(move)
            position.unmakeMove()
            assertEquals(before, position.key)
        }
    }

    @Test
    fun parsesChessComStylePgn() {
        val pgn = """
            [Event "Live Chess"]
            [Site "Chess.com"]
            [White "alice"]
            [Black "bob"]
            [Result "1-0"]
            [ECO "C50"]
            [ECOUrl "https://www.chess.com/openings/Italian-Game-Two-Knights-Defense"]
            [TimeControl "600+5"]

            1. e4 {[%clk 0:09:58.3]} 1... e5 {[%clk 0:09:55.1]} 2. Nf3 {[%clk 0:09:56]} 2... Nc6
            {[%clk 0:09:50]} 3. Bc4 {[%clk 0:09:54]} 3... Nf6 {[%clk 0:09:40]} 1-0
        """.trimIndent()

        val game = PgnParser.parse(pgn)!!
        assertEquals("alice", game["White"])
        assertEquals(6, game.moves.size)
        assertEquals("e4", game.moves[0].san)
        assertEquals(598.3, game.moves[0].clockSeconds!!, 0.01)

        val played = PgnParser.replay(game)
        assertEquals(6, played.size)
        assertEquals("Nf6", played.last().san)
        assertTrue("le temps consomme doit etre calcule", played[1].secondsSpent!! > 0)

        val tc = TimeControlOf(game)
        assertEquals("Rapide", tc)
    }

    private fun TimeControlOf(game: com.chessforge.chess.pgn.PgnGame): String =
        com.chessforge.chess.pgn.TimeControl.parse(game["TimeControl"])!!.category

    @Test
    fun detectsCheckmateAndStalemate() {
        val mate = Fen.parse("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3")
        assertTrue(mate.isInCheck())
        assertTrue("mat du berger inverse : aucun coup legal", !mate.hasLegalMove())

        val stalemate = Fen.parse("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertTrue(!stalemate.isInCheck())
        assertTrue(!stalemate.hasLegalMove())
    }
}
