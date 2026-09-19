package com.chessforge.di

import android.content.Context
import com.chessforge.ChessForgeApp
import com.chessforge.data.db.ForgeDb
import com.chessforge.data.db.GameDao
import com.chessforge.data.db.PuzzleDao
import com.chessforge.data.db.StatsDao
import com.chessforge.data.prefs.Settings
import com.chessforge.data.remote.ChessComClient
import com.chessforge.data.repo.ForgeRepository
import com.chessforge.engine.EngineProvider

/**
 * Assemblage manuel des dependances. A cette taille, un conteneur explicite est plus
 * lisible qu'un framework d'injection, et ne coute rien a la compilation.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settings = Settings(appContext)
    private val db = ForgeDb(appContext)

    val gameDao = GameDao(db)
    val puzzleDao = PuzzleDao(db)
    val statsDao = StatsDao(db)

    val engines = EngineProvider(appContext, settings)

    val repository = ForgeRepository(
        gameDao = gameDao,
        puzzleDao = puzzleDao,
        statsDao = statsDao,
        client = ChessComClient(),
        settings = settings,
        engines = engines,
    )

    val tasks = TaskCenter(repository, engines)
}

/** Raccourci d'acces depuis un composable ou une ViewModel. */
val Context.container: AppContainer
    get() = (applicationContext as ChessForgeApp).container
