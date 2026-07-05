package com.mechanicel.tomsdarts.ui.setup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Happy-Path-Test fuer [SetupViewModel]: In-Memory-Room-DB ueber das echte
 * [PlayerRepository]. Prueft die Namensaufloesung zu den uebergebenen
 * playerIds (inkl. Erhalt der Eingangsreihenfolge) sowie die Verkabelung der
 * Reorder-/Remove-Aktionen an die geordnete Teilnehmerliste. Edge-Cases deckt
 * das Test-Gate ab.
 *
 * Laeuft host-seitig unter Robolectric (SDK 34 gepinnt).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SetupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: TomsDartsDatabase
    private lateinit var repository: PlayerRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TomsDartsDatabase::class.java,
        ).build()
        repository = PlayerRepository(db.playerDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun aufloesung_liefertNamenInEingangsReihenfolge() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tomId = repository.addPlayer("Tom")
            val annaId = repository.addPlayer("Anna")
            val bjoernId = repository.addPlayer("Bjoern")

            // Bewusst eine andere Reihenfolge als die Einfuege-Reihenfolge, um
            // zu pruefen, dass die uebergebene Eingangsreihenfolge erhalten bleibt.
            val viewModel = SetupViewModel(repository, listOf(bjoernId, tomId, annaId))

            val resolved = viewModel.participants.first { it.isNotEmpty() }
            assertEquals(
                listOf(
                    SetupPlayer(bjoernId, "Bjoern"),
                    SetupPlayer(tomId, "Tom"),
                    SetupPlayer(annaId, "Anna"),
                ),
                resolved,
            )
            assertEquals(listOf(bjoernId, tomId, annaId), viewModel.orderedPlayerIds())
        }

    @Test
    fun movePlayerUp_aktualisiertGeordneteIdListe() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tomId = repository.addPlayer("Tom")
            val annaId = repository.addPlayer("Anna")
            val bjoernId = repository.addPlayer("Bjoern")
            val viewModel = SetupViewModel(repository, listOf(tomId, annaId, bjoernId))
            viewModel.participants.first { it.isNotEmpty() }

            viewModel.movePlayerUp(1)

            assertEquals(listOf(annaId, tomId, bjoernId), viewModel.orderedPlayerIds())
        }

    @Test
    fun removePlayer_entferntUndReduziertIdListe() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tomId = repository.addPlayer("Tom")
            val annaId = repository.addPlayer("Anna")
            val bjoernId = repository.addPlayer("Bjoern")
            val viewModel = SetupViewModel(repository, listOf(tomId, annaId, bjoernId))
            viewModel.participants.first { it.isNotEmpty() }

            viewModel.removePlayer(1)

            assertEquals(listOf(tomId, bjoernId), viewModel.orderedPlayerIds())
        }

    @Test
    fun removePlayer_beiMinimumBleibtUnveraendert() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tomId = repository.addPlayer("Tom")
            val annaId = repository.addPlayer("Anna")
            val viewModel = SetupViewModel(repository, listOf(tomId, annaId))
            viewModel.participants.first { it.isNotEmpty() }

            viewModel.removePlayer(0)

            assertEquals(listOf(tomId, annaId), viewModel.orderedPlayerIds())
        }

    @Test
    fun movePlayerDown_aktualisiertGeordneteIdListe() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tomId = repository.addPlayer("Tom")
            val annaId = repository.addPlayer("Anna")
            val bjoernId = repository.addPlayer("Bjoern")
            val viewModel = SetupViewModel(repository, listOf(tomId, annaId, bjoernId))
            viewModel.participants.first { it.isNotEmpty() }

            viewModel.movePlayerDown(0)

            assertEquals(listOf(annaId, tomId, bjoernId), viewModel.orderedPlayerIds())
        }

    @Test
    fun movePlayerUp_amErstenIndexAendertNichtsUndBleibtBedienbar() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Grenzfall: der Aufruf am ersten Index darf weder werfen noch die
            // Liste veraendern (Reducer ist defensiv), und weitere Aktionen
            // muessen danach weiterhin funktionieren.
            val tomId = repository.addPlayer("Tom")
            val annaId = repository.addPlayer("Anna")
            val viewModel = SetupViewModel(repository, listOf(tomId, annaId))
            viewModel.participants.first { it.isNotEmpty() }

            viewModel.movePlayerUp(0)
            assertEquals(listOf(tomId, annaId), viewModel.orderedPlayerIds())

            viewModel.movePlayerDown(0)
            assertEquals(listOf(annaId, tomId), viewModel.orderedPlayerIds())
        }

    @Test
    fun reorderUndRemove_verliertKeineTeilnehmerUndHaeltReihenfolgeKonsistent() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tomId = repository.addPlayer("Tom")
            val annaId = repository.addPlayer("Anna")
            val bjoernId = repository.addPlayer("Bjoern")
            val chrisId = repository.addPlayer("Chris")
            val viewModel = SetupViewModel(
                repository,
                listOf(tomId, annaId, bjoernId, chrisId),
            )
            viewModel.participants.first { it.isNotEmpty() }

            viewModel.movePlayerDown(0) // [Anna, Tom, Bjoern, Chris]
            viewModel.movePlayerUp(3) // [Anna, Tom, Chris, Bjoern]
            viewModel.removePlayer(1) // [Anna, Chris, Bjoern]

            assertEquals(listOf(annaId, chrisId, bjoernId), viewModel.orderedPlayerIds())
        }

    @Test
    fun aufloesung_ignoriertNichtVorhandeneSpielerIdStillschweigend() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Finding: eine nicht (mehr) existierende Spieler-ID wird beim
            // Aufloesen kommentarlos herausgefiltert (mapNotNull in init{}).
            // Werden dadurch weniger als MIN_MATCH_PLAYERS Teilnehmer aufgeloest,
            // gibt es auf dieser Ebene KEINE Absicherung mehr - anders als bei
            // removePlayer() erzwingt hier nichts die Untergrenze. In der
            // heutigen App ist dieser Pfad nur ueber inkonsistente/geloeschte
            // Spieler-IDs erreichbar (ProfileScreen erzwingt >= 2 VOR dem
            // Wechsel ins Setup), aber SetupViewModel selbst haelt die
            // Invariante nicht unabhaengig durch. Dieser Test dokumentiert das
            // tatsaechliche (nicht abgesicherte) Verhalten als Beleg fuer das
            // Finding.
            val tomId = repository.addPlayer("Tom")
            val unknownId = tomId + 999_999L

            val viewModel = SetupViewModel(repository, listOf(tomId, unknownId))

            val resolved = viewModel.participants.first { it.isNotEmpty() }
            assertEquals(listOf(SetupPlayer(tomId, "Tom")), resolved)
            // Nur noch 1 Teilnehmer aufgeloest - unterhalb MIN_MATCH_PLAYERS,
            // ohne dass SetupViewModel das an dieser Stelle verhindert.
            assertEquals(1, viewModel.orderedPlayerIds().size)
        }

    @Test
    fun aufloesung_laesstUnbekannteIdAnJederPositionHerausfallen() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Variante von oben mit der unbekannten ID an erster Stelle (statt
            // zuletzt): mapNotNull erhaelt trotzdem die Reihenfolge der
            // verbleibenden, aufgeloesten Teilnehmer.
            val unknownId = 999_999L
            val tomId = repository.addPlayer("Tom")
            val annaId = repository.addPlayer("Anna")

            val viewModel = SetupViewModel(repository, listOf(unknownId, tomId, annaId))

            val resolved = viewModel.participants.first { it.isNotEmpty() }
            assertEquals(
                listOf(SetupPlayer(tomId, "Tom"), SetupPlayer(annaId, "Anna")),
                resolved,
            )
            assertEquals(listOf(tomId, annaId), viewModel.orderedPlayerIds())
        }
}
