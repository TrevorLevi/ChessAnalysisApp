package com.chessforge

import android.app.Application
import com.chessforge.di.AppContainer

class ChessForgeApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onTerminate() {
        super.onTerminate()
        container.engines.shutdown()
    }
}
