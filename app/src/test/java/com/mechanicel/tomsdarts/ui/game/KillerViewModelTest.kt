package com.mechanicel.tomsdarts.ui.game

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.repository.MatchRepository
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.delight.DelightRegistry
import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.KillerMode
import com.mechanicel.tomsdarts.game.KillerState
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * ViewModel-Ebenen-Tests fuer den Killer-Modus, ergaenzend zu
 * [GameModeInfrastructureTest] (Board-Smoke) und den reinen Engine-Integrationstests
 * ([com.mechanicel.tomsdarts.game.engine.KillerMatchIntegrationTest]). Schwerpunkt:
 * das Zusammenspiel von Killer mit der Kontroll-Pause (`scored`-Semantik in der
 * Zug-Summe: wirksame statt roher Darts), dem Sieg-Ruecknahme-Pfad
 * ([GameViewModel.onUndoWin]), dem Leg-Wechsel ([GameViewModel.onNewLeg]) und der
 * Eliminierungs-Rotation - end-to-end durch den echten [GameViewModel], nicht nur
 * isoliert in Modus/Adapter.
 *
 * Laeuft host-seitig unter Robolectric (SDK 34 gepinnt), analog zu
 * [GameViewModelTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KillerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: TomsDartsDatabase
    private lateinit var matchRepository: MatchRepository
    private lateinit var playerRepository: PlayerRepository

    @Before
    fun setUp() {
        val directExecutor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TomsDartsDatabase::class.java,
        )
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .allowMainThreadQueries()
            .build()
        matchRepository = MatchRepository(
            matchDao = db.matchDao(),
            legDao = db.legDao(),
            turnDao = db.turnDao(),
            throwDao = db.throwDao(),
            matchPlayerDao = db.matchPlayerDao(),
        )
        playerRepository = PlayerRepository(db.playerDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun newPlayer(name: String): Long =
        db.playerDao().insert(Player(name = name, createdAt = 1L))

    private fun viewModel(
        playerIds: List<Long>,
        legsToWin: Int = 2,
    ) = GameViewModel(
        matchRepository,
        playerRepository,
        playerIds,
        GameConfig(legsToWin = legsToWin, setsToWin = 1, killerSeed = SEED),
        KillerMode(),
        KillerUiAdapter(),
        DelightRegistry.EMPTY,
    )

    /** Zielzahl des Sitzplatzes [index] fuer den festen [SEED] - dieselbe Quelle wie der Modus selbst. */
    private fun numberOf(index: Int): Int = KillerState.numberFor(SEED, index)

    private suspend fun GameViewModel<*>.awaitPlaying(): GameUiState.Playing =
        uiState.first { it is GameUiState.Playing } as GameUiState.Playing

    private val GameUiState.Playing.currentName: String
        get() = players.first { it.isCurrent }.name

    private fun GameUiState.Playing.player(name: String): PlayerScoreUi =
        players.first { it.name == name }

    private fun PlayerScoreUi.killerBoard(): PlayerBoardUi.Killer = board as PlayerBoardUi.Killer

    // --- Kontroll-Pause: Summe = Anzahl WIRKSAMER Darts, nicht die Rohsumme -----

    @Test
    fun regulaeresAufnahmeEnde_beiKiller_turnSumZeigtNurWirksameDarts() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            val n0 = numberOf(0)

            // dart1: Double(eigene Zahl) -> wirksam (wird Killer, scored 1).
            // dart2: Single(eigene Zahl) -> wirkungslos (kein Double).
            // dart3: Double(eigene Zahl) ALS Killer -> ebenfalls wirkungslos
            // (eigenes Double ist als Killer nie ein Selbst-Treffer).
            vm.onToggleDouble(); vm.onNumber(n0)
            vm.onNumber(n0)
            vm.onToggleDouble(); vm.onNumber(n0)

            val playing = vm.uiState.value as GameUiState.Playing
            val review = playing.turnReview
            assertTrue("Kontroll-Pause laeuft nach dem 3. Dart", review != null)
            assertEquals("Tom", review!!.throwerName)
            assertEquals(
                listOf(Dart.double(n0), Dart.single(n0), Dart.double(n0)),
                review.darts,
            )
            assertEquals("Summe = 1 wirksamer Dart, NICHT die Roh-Anzahl 3", 1, review.turnSum)
            assertEquals("Anna", review.nextPlayerName)
        }

    // --- legWon: keine Kontroll-Pause, egal an welcher Dart-Position ------------

    @Test
    fun legWon_alsErsterDartDerAufnahme_loestKeineKontrollPauseAus() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            val n0 = numberOf(0)
            val n1 = numberOf(1)

            // Turn 1 (Tom): wird Killer, trifft Anna zweimal -> regulaeres
            // Aufnahme-Ende MIT Pause (noch kein Sieg, Anna hat 1 Leben).
            vm.onToggleDouble(); vm.onNumber(n0)
            vm.onToggleDouble(); vm.onNumber(n1)
            vm.onToggleDouble(); vm.onNumber(n1)
            assertTrue((vm.uiState.value as GameUiState.Playing).turnReview != null)
            vm.onContinue()

            // Turn 2 (Anna): harmlos, ebenfalls mit Pause.
            vm.onOut(); vm.onOut(); vm.onOut()
            assertTrue((vm.uiState.value as GameUiState.Playing).turnReview != null)
            vm.onContinue()

            // Turn 3 (Tom): der ERSTE Dart ist der dritte Treffer auf Anna -> als
            // einziger Gegner sofortiger Sieg, OHNE dass je eine Kontroll-Pause
            // aufblitzt.
            vm.onToggleDouble(); vm.onNumber(n1)

            val legWon = vm.uiState.first { it is GameUiState.LegWon } as GameUiState.LegWon
            assertEquals("Tom", legWon.legWinnerName)
            // dartsUsed zaehlt ALLE im Gewinn-Leg geworfenen Darts des Gewinners
            // (nicht nur die der Sieg-Aufnahme): 3 aus Turn 1 + der eine Sieg-Dart.
            assertEquals(4, legWon.dartsUsed)
        }

    @Test
    fun legWon_alsDritterDartEinerVollenAufnahme_ueberspringtDieKontrollPauseTrotzdem() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            val n0 = numberOf(0)
            val n1 = numberOf(1)

            // Turn 1 (Tom): wird Killer, ein Treffer, dritter Dart verfehlt.
            vm.onToggleDouble(); vm.onNumber(n0)
            vm.onToggleDouble(); vm.onNumber(n1)
            vm.onOut()
            assertTrue((vm.uiState.value as GameUiState.Playing).turnReview != null)
            vm.onContinue()

            // Turn 2 (Anna): harmlos.
            vm.onOut(); vm.onOut(); vm.onOut()
            assertTrue((vm.uiState.value as GameUiState.Playing).turnReview != null)
            vm.onContinue()

            // Turn 3 (Tom): zweiter Treffer, ein Fehlwurf, dann der DRITTE
            // (letzte) Dart der vollen Aufnahme ist der entscheidende dritte
            // Treffer -> trotz voller 3-Dart-Aufnahme KEINE Kontroll-Pause, weil
            // der legWon-Zweig im ViewModel strukturell VOR turnReview greift.
            vm.onToggleDouble(); vm.onNumber(n1)
            vm.onOut()
            vm.onToggleDouble(); vm.onNumber(n1)

            val state = vm.uiState.value
            assertTrue("Sieg statt Kontroll-Pause trotz 3. Dart", state is GameUiState.LegWon)
        }

    // --- onUndoWin: danach weiterspielbar ----------------------------------------

    @Test
    fun onUndoWin_ausKillerLegWon_oeffnetAufnahmeWieder_undErneuterSiegDartFuehrtWiederZuLegWon() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            val n0 = numberOf(0)
            val n1 = numberOf(1)

            vm.onToggleDouble(); vm.onNumber(n0)
            vm.onToggleDouble(); vm.onNumber(n1)
            vm.onToggleDouble(); vm.onNumber(n1)
            vm.onContinue()
            vm.onOut(); vm.onOut(); vm.onOut()
            vm.onContinue()
            vm.onToggleDouble(); vm.onNumber(n1) // Turn 3: sofortiger Sieg.
            vm.uiState.first { it is GameUiState.LegWon }

            vm.onUndoWin()

            val playing = vm.awaitPlaying()
            assertEquals("Tom", playing.currentName)
            // Der einzige (Sieg-)Dart der Aufnahme wurde entfernt -> Aufnahme leer.
            assertTrue(playing.input.darts.isEmpty())
            assertTrue("Anna lebt wieder", playing.player("Anna").killerBoard().lives > 0)
            assertTrue(playing.canUndo)
            assertNull("keine Kontroll-Pause nach dem Ruecknehmen", playing.turnReview)

            // Weiterspielbar: derselbe Treffer erneut -> wieder Leg gewonnen.
            vm.onToggleDouble(); vm.onNumber(n1)
            val legWon = vm.uiState.first { it is GameUiState.LegWon } as GameUiState.LegWon
            assertEquals("Tom", legWon.legWinnerName)
        }

    // --- Leg-Wechsel: Zahlen bleiben stabil, Leben/Killer-Status zuruecksetzen --

    @Test
    fun onNewLeg_nachKillerLegSieg_zahlenBleibenGleich_lebenUndKillerStatusZuruecksetzen() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            val start = vm.awaitPlaying()
            val numbersBefore = start.players.associate { it.name to it.killerBoard().number }

            val n0 = numberOf(0)
            val n1 = numberOf(1)

            vm.onToggleDouble(); vm.onNumber(n0)
            vm.onToggleDouble(); vm.onNumber(n1)
            vm.onToggleDouble(); vm.onNumber(n1)
            vm.onContinue()
            vm.onOut(); vm.onOut(); vm.onOut()
            vm.onContinue()
            vm.onToggleDouble(); vm.onNumber(n1) // Turn 3: sofortiger Sieg.
            val legWon = vm.uiState.first { it is GameUiState.LegWon } as GameUiState.LegWon
            assertEquals("Anna", legWon.nextStarterName)

            vm.onNewLeg()

            val nextLeg = vm.awaitPlaying()
            assertEquals("Anna", nextLeg.currentName)
            val numbersAfter = nextLeg.players.associate { it.name to it.killerBoard().number }
            assertEquals(
                "Zahlen bleiben ueber den Leg-Wechsel stabil (Seed konstant)",
                numbersBefore,
                numbersAfter,
            )
            assertTrue(
                "frisches Leg: volle Leben, kein Killer-Status",
                nextLeg.players.all { !it.killerBoard().isKiller && it.killerBoard().lives == KillerState.LIVES },
            )
        }

    // --- Eliminierungs-Rotation end-to-end durch die echte VM -------------------

    @Test
    fun dreiSpieler_eliminierterSpielerWirdInDerRotationDerVmUebersprungen() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val bjoern = newPlayer("Bjoern")
            val vm = viewModel(listOf(tom, anna, bjoern))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            val n0 = numberOf(0)
            val n1 = numberOf(1)

            // Turn 1 (Tom): wird Killer, trifft Anna zweimal.
            vm.onToggleDouble(); vm.onNumber(n0)
            vm.onToggleDouble(); vm.onNumber(n1)
            vm.onToggleDouble(); vm.onNumber(n1)
            vm.onContinue()
            // Turn 2 (Anna) und Turn 3 (Bjoern): harmlos.
            vm.onOut(); vm.onOut(); vm.onOut(); vm.onContinue()
            vm.onOut(); vm.onOut(); vm.onOut(); vm.onContinue()
            assertEquals("Tom", (vm.uiState.value as GameUiState.Playing).currentName)

            // Turn 4 (Tom): dritter Treffer eliminiert Anna, Rest verfehlt.
            vm.onToggleDouble(); vm.onNumber(n1)
            vm.onOut(); vm.onOut()
            assertTrue((vm.uiState.value as GameUiState.Playing).turnReview != null)
            vm.onContinue()

            // Die Rotation ueberspringt Anna (eliminiert) und geht direkt zu Bjoern.
            val afterSkip = vm.uiState.value as GameUiState.Playing
            assertEquals("Bjoern", afterSkip.currentName)
            assertTrue("Annas Karte zeigt 0 Leben", afterSkip.player("Anna").killerBoard().eliminated)
        }

    private companion object {
        const val SEED: Long = 24_680L
    }
}
