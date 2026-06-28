package com.mechanicel.tomsdarts.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Leichter Integrationstest fuer [AppContainer]: prueft, dass der manuelle
 * DI-Container die Repositories nicht-null bereitstellt und eine Mini-Operation
 * end-to-end ueber das echte [TomsDartsDatabase.getInstance]-Singleton
 * funktioniert.
 *
 * Bewusst **eine einzige** Testmethode: [AppContainer] nutzt die datei-basierte
 * Singleton-DB (`tomsdarts.db`). Das prozessweite [TomsDartsDatabase] INSTANCE
 * ueberlebt einzelne Robolectric-Testmethoden, dessen SQLite-Connection wird
 * aber zwischen den Methoden vom Robolectric-Shadow invalidiert ("Illegal
 * connection pointer"). Daher wird der Container hier nur innerhalb genau einer
 * Sandbox konstruiert und benutzt. Der Test bleibt zudem **idempotent**: er
 * prueft additiv (eigener Marker-Datensatz) und raeumt ihn am Ende wieder auf.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppContainerTest {

    @Test
    fun providesRepositoriesAndWorksEndToEnd() = runBlocking {
        val container = AppContainer(ApplicationProvider.getApplicationContext<Context>())

        assertNotNull("playerRepository darf nicht null sein", container.playerRepository)
        assertNotNull("matchRepository darf nicht null sein", container.matchRepository)

        val repo = container.playerRepository
        // Eindeutiger Marker-Name, damit der Test nicht von evtl. vorhandenen
        // Datensaetzen des datei-basierten Singletons abhaengt.
        val uniqueName = "AppContainerProbe-${System.nanoTime()}"
        val id = repo.addPlayer(uniqueName)
        assertTrue("addPlayer muss positive ID liefern", id > 0)

        try {
            val found = repo.observePlayers().first().firstOrNull { it.id == id }
            assertNotNull("Angelegter Spieler muss ueber den Container lesbar sein", found)
            assertEquals(uniqueName, found!!.name)
        } finally {
            // Idempotenz: angelegten Datensatz wieder entfernen.
            repo.getPlayer(id)?.let { repo.deletePlayer(it) }
        }
    }
}
