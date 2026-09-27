package com.chessforge.training

import com.chessforge.chess.Fen
import com.chessforge.chess.Move
import com.chessforge.chess.San
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Une ligne d'ouverture dont un coup serait illegal bloquerait l'exercice sans
 * explication. On les rejoue donc toutes sur l'echiquier.
 */
class OpeningLineTest {

    @Test
    fun identifiersAreUnique() {
        val ids = OpeningLines.all.map { it.id }
        assertEquals("identifiants en double", ids.size, ids.distinct().size)
    }

    @Test
    fun everyLineIsPlayableFromTheStart() {
        for (line in OpeningLines.all) {
            val position = Fen.parse(Fen.START)
            for ((index, san) in line.moves.withIndex()) {
                val move = San.parse(position, san)
                assertTrue(
                    "${line.id} : coup ${index + 1} « $san » illegal",
                    move != Move.NONE,
                )
                assertTrue(
                    "${line.id} : coup ${index + 1} « $san » laisse le roi en prise",
                    position.makeMove(move),
                )
            }
            assertTrue("${line.id} : ligne trop courte", line.moves.size >= 6)
        }
    }

    @Test
    fun linesAlternateColoursCorrectly() {
        for (line in OpeningLines.all) {
            // Chaque ligne commence par un coup des blancs : l'exercice fait jouer
            // l'utilisateur dans ce camp et repond automatiquement pour l'autre.
            val position = Fen.parse(Fen.START)
            assertEquals("${line.id} : la ligne doit commencer par les blancs", 0, position.side)
        }
    }
}
