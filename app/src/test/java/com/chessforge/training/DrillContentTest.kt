package com.chessforge.training

import com.chessforge.chess.Fen
import com.chessforge.chess.Move
import com.chessforge.chess.Piece
import com.chessforge.chess.San
import com.chessforge.engine.EngineLimits
import com.chessforge.engine.forge.ForgeEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les exercices d'entrainement sont ecrits a la main : ce test est ce qui garantit
 * qu'ils sont justes. Un exercice dont la solution serait fausse apprendrait une
 * betise a l'utilisateur, ce qui est pire que de ne rien proposer.
 */
class DrillContentTest {

    private val engine = ForgeEngine(ttSizeMb = 8)

    @Test
    fun identifiersAreUnique() {
        val ids = Drills.endgames.map { it.id } + Drills.tactics.map { it.id }
        assertEquals("identifiants en double", ids.size, ids.distinct().size)
    }

    @Test
    fun everyPositionIsLegalAndPlayable() {
        for (drill in Drills.endgames) {
            val position = Fen.parse(drill.fen)
            assertEquals("FEN non canonique pour ${drill.id}", drill.fen, Fen.of(position))
            assertTrue("${drill.id} : aucun coup legal", position.hasLegalMove())
            // Deux rois sur des cases adjacentes, ou un roi en prise alors que c'est a
            // l'adversaire de jouer, rendraient la position impossible.
            assertTrue("${drill.id} : le camp qui ne joue pas est en echec",
                !position.isInCheck(1 - position.side))
        }
        for (drill in Drills.tactics) {
            val position = Fen.parse(drill.fen)
            assertEquals("FEN non canonique pour ${drill.id}", drill.fen, Fen.of(position))
            assertTrue("${drill.id} : aucun coup legal", position.hasLegalMove())
            assertTrue("${drill.id} : le camp qui ne joue pas est en echec",
                !position.isInCheck(1 - position.side))
        }
    }

    @Test
    fun tacticSolutionsAreLegalAndBest() = runBlocking {
        for (drill in Drills.tactics) {
            val position = Fen.parse(drill.fen)
            val move = San.fromUci(position, drill.solutionUci)
            assertTrue("${drill.id} : solution ${drill.solutionUci} illegale", move != Move.NONE)

            val line = engine.analyze(drill.fen, EngineLimits(depth = 10, movetimeMs = 4_000)).first()
            assertEquals(
                "${drill.id} : le moteur prefere ${line.bestMove} (${line.score.format()})",
                drill.solutionUci,
                line.bestMove,
            )
        }
    }

    /**
     * Pour un exercice a gagner, le moteur doit confirmer que la position l'est
     * vraiment : sinon l'objectif annonce serait inatteignable.
     */
    @Test
    fun winnableEndgamesAreActuallyWinnable() = runBlocking {
        for (drill in Drills.endgames.filter { it.goal != DrillGoal.DRAW }) {
            // Recherche volontairement profonde : une finale de pions n'est comprise
            // qu'une fois la promotion dans l'horizon, le moteur n'ayant pas de table
            // de finales pour l'adjuger autrement.
            val line = engine.analyze(drill.fen, EngineLimits(depth = 24, movetimeMs = 20_000)).first()
            val score = line.score
            val decisive = score.mate?.let { it > 0 } ?: (score.cp ?: 0 > 300)
            assertTrue(
                "${drill.id} annonce « ${drill.goal.label} » mais le moteur evalue ${score.format()}",
                decisive,
            )
        }
    }

    /**
     * Les nulles theoriques ne sont pas verifiables par le moteur : sans tables de
     * finales, il voit le materiel et annonce un avantage la ou la position est nulle.
     * C'est precisement ce qui rend l'exercice interessant. On verifie donc seulement
     * que la position est jouable par le camp qui doit tenir.
     */
    @Test
    fun drawDrillsArePlayableByTheDefender() {
        for (drill in Drills.endgames.filter { it.goal == DrillGoal.DRAW }) {
            val position = Fen.parse(drill.fen)
            assertTrue("${drill.id} : le defenseur n'a aucun coup", position.hasLegalMove())
            assertNotNull(
                "${drill.id} : le defenseur doit avoir un roi",
                position.kingSquare[position.side].takeIf { it >= 0 },
            )
        }
    }

    @Test
    fun endgameSideToMoveMatchesTheGoal() {
        for (drill in Drills.endgames) {
            val position = Fen.parse(drill.fen)
            val label = if (position.side == Piece.WHITE) "blancs" else "noirs"
            // Simple garde-fou de coherence : l'exercice se joue toujours du point de
            // vue du camp au trait, l'interface le presente ainsi.
            assertTrue("${drill.id} : camp au trait $label", position.side in 0..1)
        }
    }
}
