package com.mechanicel.tomsdarts.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Tests fuer [SettingsRepository] (ADR-0040) gegen einen echten, datei-basierten
 * Preferences-DataStore in einem temporaeren Verzeichnis (reines JVM, kein
 * Robolectric noetig).
 *
 * Bewusst ueber eine [OkioStorage] mit dem [PreferencesSerializer] (gleiches
 * Preferences-Dateiformat wie in der App): Die `File`-basierte Storage der App
 * ersetzt die Datei per `File.renameTo`, das auf Windows-Hosts scheitert, sobald
 * die Zieldatei existiert (jeder zweite Schreibvorgang). Auf Android tritt das
 * nicht auf; die Okio-Storage verschiebt atomar mit Ueberschreiben und laeuft auf
 * allen Hosts.
 *
 * Coroutine-Hygiene: jeder DataStore laeuft in einem Kind-Scope von
 * `backgroundScope` und wird damit spaetestens am Ende von `runTest` beendet;
 * soll eine zweite Instanz dieselbe Datei oeffnen, wird die erste vorher
 * explizit gecancelt (DataStore erlaubt pro Datei nur eine aktive Instanz).
 */
class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun settingsFile(): File = File(tmp.root, "t.preferences_pb")

    /**
     * Kind-Scope von `backgroundScope` mit eigenem Job: wird am Testende
     * automatisch mit beendet, kann aber auch gezielt vorher gecancelt werden.
     */
    private fun TestScope.childScope(): Pair<CoroutineScope, Job> {
        val job = Job(parent = backgroundScope.coroutineContext[Job])
        return CoroutineScope(backgroundScope.coroutineContext + job) to job
    }

    private fun TestScope.newStore(
        handler: ReplaceFileCorruptionHandler<Preferences>? = null,
    ): Pair<DataStore<Preferences>, Job> {
        val (scope, job) = childScope()
        val store = PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) {
                settingsFile().toOkioPath()
            },
            corruptionHandler = handler,
            scope = scope,
        )
        return store to job
    }

    @Test
    fun defaultIstFeiernAn() = runTest {
        val (store, _) = newStore()
        val repo = SettingsRepository(store)

        assertEquals(AppSettings.DEFAULT, repo.settings.first())
        assertTrue(repo.settings.first().delightEnabled)
    }

    @Test
    fun ausschaltenKommtImFlowAn() = runTest {
        val (store, _) = newStore()
        val repo = SettingsRepository(store)

        repo.setDelightEnabled(false)

        assertFalse(repo.settings.first().delightEnabled)
    }

    @Test
    fun neueInstanzAufDerselbenDateiLiestGespeichertenWert() = runTest {
        val (first, firstJob) = newStore()
        SettingsRepository(first).setDelightEnabled(false)
        // Erste Instanz sauber beenden, bevor eine zweite die Datei oeffnet.
        firstJob.cancel()
        firstJob.join()

        val (second, _) = newStore()
        val repo = SettingsRepository(second)

        assertFalse(repo.settings.first().delightEnabled)
    }

    @Test
    fun zurueckAufAnSchalten() = runTest {
        val (store, _) = newStore()
        val repo = SettingsRepository(store)

        repo.setDelightEnabled(false)
        repo.setDelightEnabled(true)

        assertTrue(repo.settings.first().delightEnabled)
    }

    @Test
    fun korrupteDateiLiefertDefaultsOhneAbsturz() = runTest {
        // Feld 1, length-delimited, mit unmoeglicher Laenge -> kein gueltiges Protobuf.
        settingsFile().writeBytes(byteArrayOf(0x0A, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F))
        val (store, _) = newStore()
        val repo = SettingsRepository(store)

        assertEquals(AppSettings.DEFAULT, repo.settings.first())
    }

    @Test
    fun korrupteDateiMitProduktivHandler_defaultsUndWiederBeschreibbar() = runTest {
        settingsFile().writeBytes(byteArrayOf(0x0A, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F))
        val (store, _) = newStore(handler = settingsCorruptionHandler)
        val repo = SettingsRepository(store)

        assertEquals(AppSettings.DEFAULT, repo.settings.first())
        repo.setDelightEnabled(false)
        assertFalse(repo.settings.first().delightEnabled)
    }

    @Test
    fun ioExceptionBeimLesenLiefertDefaults() = runTest {
        val repo = SettingsRepository(FailingDataStore(IOException("Platte weg")))

        assertEquals(listOf(AppSettings.DEFAULT), repo.settings.toList())
    }

    @Test
    fun andereFehlerWerdenNichtVerschluckt() = runTest {
        val repo = SettingsRepository(FailingDataStore(IllegalStateException("Bug")))

        try {
            repo.settings.first()
            fail("IllegalStateException erwartet")
        } catch (expected: IllegalStateException) {
            assertEquals("Bug", expected.message)
        }
    }

    @Test
    fun gleicherWertEmittiertNichtErneut() = runTest {
        val repo = SettingsRepository(
            ListDataStore(listOf(emptyPreferences(), emptyPreferences())),
        )

        assertEquals(listOf(AppSettings.DEFAULT), repo.settings.toList())
    }

    /** DataStore, dessen Lesestrom sofort mit [error] fehlschlaegt. */
    private class FailingDataStore(private val error: Throwable) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw error }
        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = throw error
    }

    /** DataStore, der die festen [values] nacheinander emittiert und endet. */
    private class ListDataStore(private val values: List<Preferences>) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { values.forEach { emit(it) } }
        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = throw UnsupportedOperationException()
    }
}
