package com.mechanicel.tomsdarts

import android.app.Application
import com.mechanicel.tomsdarts.data.AppContainer

/**
 * Application-Einstiegspunkt. Haelt den prozessweiten [AppContainer], der die
 * lokale Datenbank und die Repositories bereitstellt.
 *
 * Bleibt rein lokal (offline-first, keine Cloud/Backend/Tracking).
 */
class TomsDartsApp : Application() {

    /** Manueller DI-Container, lazy beim ersten Zugriff aufgebaut. */
    val container: AppContainer by lazy { AppContainer(this) }
}
