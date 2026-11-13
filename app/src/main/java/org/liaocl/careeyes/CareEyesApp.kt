package org.liaocl.careeyes

import android.app.Application
import android.content.Context
import org.liaocl.careeyes.data.AppContainer

class CareEyesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as CareEyesApp).container
