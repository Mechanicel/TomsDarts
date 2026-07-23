package com.mechanicel.tomsdarts.ui.game

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.repository.MatchRepository
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.X01Mode
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Happy-Path-Basistests fuer das Mehrspieler-[GameViewModel]: In-Memory-Room-DB
 * ueber die echten Repositories. Prueft Match-/Leg-/MatchPlayer-Anlage, den
 * automatischen Spielerwechsel, Leg-/Match-Gewinn (Legs/Sets) sowie die
 * throw-level-Persistenz pro Spieler.
 *
 * Die DB nutzt einen synchronen (direkten) Executor, damit die im
 * [GameViewModel] feuernde Persistenz unter
 * [kotlinx.coroutines.test.UnconfinedTestDispatcher] deterministisch
 * abgeschlossen ist, bevor die Assertions lesen. Edge-Cases deckt das Test-Gate
 * ab. Laeuft host-seitig unter Robolectric (SDK 34 gepinnt).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameViewModelTest {

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

    // --- Helfer ---------------------------------------------------------------

    private suspend fun newPlayer(name: String): Long =
        db.playerDao().insert(Player(name = name, createdAt = 1L))

    private fun viewModel(
        playerIds: List<Long>,
        config: GameConfig = GameConfig(startScore = 501, doubleOut = true, legsToWin = 2, setsToWin = 1),
    ) = GameViewModel(matchRepository, playerRepository, playerIds, config, X01Mode(), X01UiAdapter())

    private suspend fun GameViewModel<*>.awaitPlaying(): GameUiState.Playing =
        uiState.first { it is GameUiState.Playing } as GameUiState.Playing

    private val GameUiState.Playing.currentName: String
        get() = players.first { it.isCurrent }.name

    /** Karte eines Spielers ueber seinen Namen (fuer die pro-Spieler-Aufnahme). */
    private fun GameUiState.Playing.player(name: String): PlayerScoreUi =
        players.first { it.name == name }

    private suspend fun singleLegId(): Long =
        matchRepository.getLegs(matchRepository.getMatches().first().id).first().id

    /** Spielt eine volle Checkout-Aufnahme: Double (startScore/2) in einem Dart. */
    private fun GameViewModel<*>.checkout(half: Int) {
        onToggleDouble()
        onNumber(half)
    }

    // --- Tests ----------------------------------------------------------------

    @Test
    fun init_legtMatchAlleSpielerUndErstesLegAnUndIstPlaying() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(listOf(tom, anna))

            val playing = vm.awaitPlaying()
            assertEquals(2, playing.players.size)
            assertEquals("Tom", playing.currentName)
            assertEquals(501, playing.startScore)
            assertTrue(playing.players.all { it.remaining == 501 })
            assertEquals(1, playing.currentLegNumber)
            assertEquals(1, playing.currentSetNumber)
            assertEquals(2, playing.legsToWin)

            val match = matchRepository.getMatches().single()
            assertEquals("X01", match.modeType)
            assertEquals(501, match.startScore)

            val matchPlayers = matchRepository.getMatchPlayers(match.id)
            assertEquals(2, matchPlayers.size)
            assertEquals(listOf(tom, anna), matchPlayers.sortedBy { it.position }.map { it.playerId })
            assertEquals(listOf(0, 1), matchPlayers.sortedBy { it.position }.map { it.position })

            val legs = matchRepository.getLegs(match.id)
            assertEquals(1, legs.size)
            assertEquals(1, legs.first().legNumber)
        }

    @Test
    fun aufnahmeEnde_wechseltZumNaechstenSpieler() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Toms Aufnahme: 3x Single 20.
            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)

            val playing = vm.uiState.value as GameUiState.Playing
            assertEquals("Anna", playing.currentName)
            // Toms Rest reduziert, Anna unveraendert.
            assertEquals(441, playing.players.first { it.playerId == tom }.remaining)
            assertEquals(501, playing.players.first { it.playerId == anna }.remaining)
            // Annas Aufnahme ist leer.
            assertTrue(playing.input.darts.isEmpty())

            // Persistierter Turn traegt Toms playerId und das aktuelle Leg.
            val legId = singleLegId()
            val turns = matchRepository.getTurns(legId)
            assertEquals(1, turns.size)
            assertEquals(tom, turns.first().playerId)
            assertEquals(60, turns.first().totalScored)
        }

    @Test
    fun legGewinn_fuehrtZuLegWonUndSchliesstLegMitWinnerAb() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Tom checkt Leg 1 mit Double 20 aus.
            vm.checkout(20)

            val legWon = vm.uiState.first { it is GameUiState.LegWon } as GameUiState.LegWon
            assertEquals("Tom", legWon.legWinnerName)
            // Engine hat rotiert: Anna beginnt das naechste Leg.
            assertEquals("Anna", legWon.nextStarterName)
            assertEquals(2, legWon.nextLegNumber)
            assertEquals(1, legWon.dartsUsed)

            // Erstes Leg ist mit Toms winnerId abgeschlossen, Match laeuft weiter.
            val match = matchRepository.getMatches().single()
            assertEquals(null, match.winnerId)
            val leg = matchRepository.getLegs(match.id).first { it.legNumber == 1 }
            assertNotNull("Leg endedAt gesetzt", leg.endedAt)
            assertEquals(tom, leg.winnerId)
        }

    @Test
    fun onNewLeg_ausLegWon_legtZweitesLegAnUndIstPlaying() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.checkout(20) // Tom gewinnt Leg 1
            vm.uiState.first { it is GameUiState.LegWon }

            val matchId = matchRepository.getMatches().single().id
            vm.onNewLeg()

            val playing = vm.awaitPlaying()
            assertEquals(2, playing.currentLegNumber)
            // Anna beginnt das neue Leg (Rotation).
            assertEquals("Anna", playing.currentName)
            assertTrue(playing.players.all { it.remaining == 40 })

            val legs = matchRepository.getLegs(matchId)
            assertEquals(2, legs.size)
            assertEquals(listOf(1, 2), legs.map { it.legNumber }.sorted())
        }

    @Test
    fun matchGewinn_nachZweiLegs_fuehrtZuMatchWonUndSchliesstMatchAb() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Leg 1: Tom gewinnt (Tom beginnt).
            vm.checkout(20)
            vm.uiState.first { it is GameUiState.LegWon }
            vm.onNewLeg()
            vm.awaitPlaying()

            // Leg 2: Anna beginnt nach Rotation und verfehlt (3 Misses) ->
            // Wechsel zu Tom, der auscheckt und das Match gewinnt.
            vm.onOut(); vm.onOut(); vm.onOut()
            vm.checkout(20)

            val matchWon = vm.uiState.first { it is GameUiState.MatchWon } as GameUiState.MatchWon
            assertEquals("Tom", matchWon.matchWinnerName)

            val match = matchRepository.getMatches().single()
            assertNotNull("Match endedAt gesetzt", match.endedAt)
            assertEquals(tom, match.winnerId)

            // Beide Legs sind abgeschlossen und gehoeren Tom.
            val legs = matchRepository.getLegs(match.id)
            assertEquals(2, legs.size)
            assertTrue(legs.all { it.winnerId == tom && it.endedAt != null })
        }

    @Test
    fun throwLevelPersistenz_ordnetTurnsKorrektenSpielernUndLegsZu() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Tom: 3x Single 20. Anna: 3x Single 19.
            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
            vm.onNumber(19); vm.onNumber(19); vm.onNumber(19)

            val legId = singleLegId()
            val turns = matchRepository.getTurns(legId)
            assertEquals(2, turns.size)
            val tomTurn = turns.single { it.playerId == tom }
            val annaTurn = turns.single { it.playerId == anna }
            assertEquals(60, tomTurn.totalScored)
            assertEquals(57, annaTurn.totalScored)
            assertFalse(tomTurn.bust)
            assertEquals(listOf(0, 1), turns.map { it.turnIndex }.sorted())

            assertEquals(3, matchRepository.getThrows(tomTurn.id).size)
            assertEquals(listOf(19, 19, 19), matchRepository.getThrows(annaTurn.id).map { it.value })
        }

    @Test
    fun startScore_wirdInSpielzustandUndMatchUebernommen() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")

            // Der im Setup gewaehlte Startpunkt muss bis in Spielzustand und
            // persistiertes Match durchlaufen (301/501/701).
            listOf(301, 501, 701).forEach { startScore ->
                val vm = viewModel(
                    listOf(tom, anna),
                    GameConfig(startScore = startScore, doubleOut = true, legsToWin = 2, setsToWin = 1),
                )

                val playing = vm.awaitPlaying()
                assertEquals(startScore, playing.startScore)
                assertTrue(playing.players.all { it.remaining == startScore })

                val match = matchRepository.getMatches().last()
                assertEquals(startScore, match.startScore)
            }
        }

    @Test
    fun letzteAufnahme_enthaeltDartsDesAbgeschlossenenZugsBeimEigenenSpieler() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            val initial = vm.awaitPlaying()
            // Zu Leg-Beginn hat noch kein Spieler eine letzte Aufnahme.
            assertTrue(initial.players.all { it.lastTurnDarts.isEmpty() && !it.lastTurnBust })

            // Toms Aufnahme: T-20, 5, 20.
            vm.onToggleTriple(); vm.onNumber(20)
            vm.onNumber(5)
            vm.onNumber(20)

            val playing = vm.uiState.value as GameUiState.Playing
            // Die Aufnahme haengt an Toms Karte, nicht an Annas.
            assertEquals(
                listOf(Dart.triple(20), Dart.single(5), Dart.single(20)),
                playing.player("Tom").lastTurnDarts,
            )
            assertFalse(playing.player("Tom").lastTurnBust)
            assertTrue(playing.player("Anna").lastTurnDarts.isEmpty())
        }

    @Test
    fun letzteAufnahme_laufenderZugAendertSieNicht() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Nur zwei Darts geworfen -> Aufnahme noch nicht beendet.
            vm.onNumber(20)
            vm.onNumber(20)

            val playing = vm.uiState.value as GameUiState.Playing
            assertTrue(playing.players.all { it.lastTurnDarts.isEmpty() })
        }

    @Test
    fun letzteAufnahme_bustSetztFlagBeimWerfendenSpieler() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            // Startscore 40, Double-Out: Single 20 auf 0 ist kein gueltiger
            // Checkout (kein Doppel) -> Bust, Aufnahme endet.
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onNumber(20); vm.onNumber(20)

            val playing = vm.uiState.value as GameUiState.Playing
            // Bust haengt an Toms Karte.
            assertTrue(playing.player("Tom").lastTurnBust)
            assertTrue(playing.player("Tom").lastTurnDarts.isNotEmpty())
            assertFalse(playing.player("Anna").lastTurnBust)
            // Nach dem Bust ist der naechste Spieler am Zug (Anna).
            assertEquals("Anna", playing.currentName)
        }

    @Test
    fun letzteAufnahme_wirdBeiNeuemLegFuerAlleZurueckgesetzt() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.checkout(20) // Tom gewinnt Leg 1
            vm.uiState.first { it is GameUiState.LegWon }
            vm.onNewLeg()

            val playing = vm.awaitPlaying()
            // Die letzte Aufnahme darf bei keinem Spieler ins neue Leg bluten.
            assertTrue(playing.players.all { it.lastTurnDarts.isEmpty() && !it.lastTurnBust })
        }

    @Test
    fun checkout_wirdFuerAktuellenWerferGesetzt() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            // Startscore 170: der Werfer hat sofort einen 3-Dart-Checkout.
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 170, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )

            val playing = vm.awaitPlaying()
            assertEquals(
                listOf(Dart.triple(20), Dart.triple(20), Dart.doubleBull()),
                playing.checkout,
            )
        }

    @Test
    fun checkout_bleibtOhneAuscheckbarenRestNull() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            // 501 liegt ueber 170 -> kein Vorschlag.
            val vm = viewModel(listOf(tom, anna))

            val playing = vm.awaitPlaying()
            assertEquals(null, playing.checkout)
        }

    @Test
    fun checkout_folgtDemSinkendenRest() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 100, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            val start = vm.awaitPlaying()
            // Rest 100 -> T-20, D-20.
            assertEquals(listOf(Dart.triple(20), Dart.double(20)), start.checkout)

            // Tom wirft T-20 (Rest 40, Aufnahme laeuft weiter) -> Vorschlag D-20.
            vm.onToggleTriple(); vm.onNumber(20)

            val after = vm.uiState.value as GameUiState.Playing
            assertEquals(listOf(Dart.double(20)), after.checkout)
        }

    @Test
    fun checkout_bleibtBeiSingleOutImmerNull() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            // Rest 170 waere bei Double-Out sofort auscheckbar (siehe
            // checkout_wirdFuerAktuellenWerferGesetzt) -- bei Single-Out darf
            // trotzdem nie ein Vorschlag erscheinen.
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 170, doubleOut = false, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }

            val start = vm.awaitPlaying()
            assertEquals(null, start.checkout)

            // Auch nach einem Wurf (Rest sinkt auf 110, waere bei Double-Out
            // auscheckbar) bleibt der Vorschlag bei Single-Out aus.
            vm.onToggleTriple(); vm.onNumber(20)

            val after = vm.uiState.value as GameUiState.Playing
            assertEquals(null, after.checkout)
        }

    @Test
    fun undo_ueberSpielerwechsel_loeschtTurnUndSpieltDuplikatfreiWeiter() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Tom wirft eine volle Aufnahme (3x 20) -> Wechsel zu Anna, Turn in DB.
            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
            var playing = vm.uiState.value as GameUiState.Playing
            assertEquals("Anna", playing.currentName)
            val legId = singleLegId()
            assertEquals(1, matchRepository.getTurns(legId).size)

            // Undo spult ueber die Aufnahme-Grenze zurueck: Tom wieder am Zug mit
            // zwei Darts, der abgeschlossene Turn ist aus der DB entfernt.
            vm.onUndo()
            playing = vm.uiState.value as GameUiState.Playing
            assertEquals("Tom", playing.currentName)
            assertEquals(2, playing.input.darts.size)
            assertTrue(matchRepository.getTurns(legId).isEmpty())

            // Weiterspielen: dritter Dart erneut -> genau EIN Turn (kein Duplikat).
            vm.onNumber(20)
            playing = vm.uiState.value as GameUiState.Playing
            assertEquals("Anna", playing.currentName)
            val turns = matchRepository.getTurns(legId)
            assertEquals(1, turns.size)
            assertEquals(0, turns.first().turnIndex)
            assertEquals(tom, turns.first().playerId)
            assertEquals(60, turns.first().totalScored)
            assertEquals(3, matchRepository.getThrows(turns.first().id).size)
        }

    @Test
    fun undo_innerhalbAufnahme_nimmtNurLetztenDartZurueck() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Zwei Darts (noch kein Aufnahme-Ende), dann Undo des letzten.
            vm.onNumber(20); vm.onNumber(5)
            vm.onUndo()

            val playing = vm.uiState.value as GameUiState.Playing
            assertEquals("Tom", playing.currentName)
            assertEquals(listOf(Dart.single(20)), playing.input.darts)
            assertEquals(481, playing.players.first { it.playerId == tom }.remaining)
            // Noch nichts persistiert (Aufnahme laeuft).
            assertTrue(matchRepository.getTurns(singleLegId()).isEmpty())
        }

    @Test
    fun canUndo_falseZuLegBeginn_trueNachDartUndNachSpielerwechsel() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }

            val initial = vm.awaitPlaying()
            assertFalse("Zu Leg-Beginn kein Undo", initial.canUndo)

            // Nach einem Dart kann zurueckgenommen werden.
            vm.onNumber(20)
            assertTrue((vm.uiState.value as GameUiState.Playing).canUndo)

            // Nach voller Aufnahme + Spielerwechsel bleibt Undo moeglich (Cross-Turn).
            vm.onNumber(20); vm.onNumber(20)
            val afterSwitch = vm.uiState.value as GameUiState.Playing
            assertEquals("Anna", afterSwitch.currentName)
            assertTrue("Direkt nach Spielerwechsel Undo moeglich", afterSwitch.canUndo)
        }

    @Test
    fun zuWenigeGueltigeSpieler_fuehrtZuNoPlayer() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            // Nur ein gueltiger Spieler (zweite ID existiert nicht).
            val vm = viewModel(listOf(tom, 9999L))

            val state = vm.uiState.first { it !is GameUiState.Loading }
            assertTrue(state is GameUiState.NoPlayer)
            assertTrue(matchRepository.getMatches().isEmpty())
        }
}
