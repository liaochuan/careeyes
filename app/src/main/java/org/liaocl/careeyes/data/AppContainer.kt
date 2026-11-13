package org.liaocl.careeyes.data

import android.content.Context
import org.liaocl.careeyes.engine.EyeCareEngine

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val settingsRepository = SettingsRepository(appContext)
    val statsRepository = StatsRepository(appContext)
    val engine = EyeCareEngine()
}
