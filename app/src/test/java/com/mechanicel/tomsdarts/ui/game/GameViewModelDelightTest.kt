package com.mechanicel.tomsdarts.ui.game

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.repository.MatchRepository
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.delight.DelightAnimation
import com.mechanicel.tomsdarts.delight.DelightPresentation
import com.mechanicel.tomsdarts.delight.DelightRegistry
import com.mechanicel.tomsdarts.delight.DelightTextKeys
import com.mechanicel.tomsdarts.delight.DelightTrigger
import com.mechanicel.tomsdarts.delight.DelightVisit
import com.mechanicel.tomsdarts.delight.ProductDelightTriggers
import com.mechanicel.tomsdarts.game.CountUpMode
import com.mechanicel.tomsdarts.game.CountUpState
import com.mechanicel.tomsdarts.game.CricketMode
import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.GameModeCatalog
import com.mechanicel.tomsdarts.game.X01Mode
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import com.mechanicel.tomsdarts.ui.delight.planDelightIntake
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * Anbindung des Delight-Trigger-Systems (ADR-0038) an das [GameViewModel]:
 * - Auswertung beim Abschluss JEDER Aufnahme (regulaer schon beim dritten Dart,
 *   Bust, Leg-/Match-Gewinn) in X01 und einem Nicht-X01-Modus,
 * - [GameViewModel.delightEvents] mit streng monotoner [com.mechanicel.tomsdarts.delight.DelightEvent.id],
 * - Undo loest kein Event erneut aus, erneutes Werfen darf neu ausloesen,
 * - Pausen-Timer der Kontroll-Pause wartet auf [GameViewModel.onDelightDismissed]
 *   bzw. das Sicherheitsnetz [GameViewModel.DELIGHT_MAX_HOLD_MILLIS].
 *
 * Setup wie die uebrigen Game-Tests: In-Memory-Room mit direktem Executor,
 * [MainDispatcherRule] mit gemeinsamem Scheduler (virtuelle Zeit fuer die Timer).
 * Laeuft host-seitig unter Robolectric (SDK 34 gepinnt), rein lokal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameViewModelDelightTest {

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

    private suspend fun twoPlayers(): Pair<Long, Long> =
        db.playerDao().insert(Player(name = "Tom", createdAt = 1L)) to
            db.playerDao().insert(Player(name = "Anna", createdAt = 1L))

    private fun trigger(id: String, priority: Int = 0, condition: (DelightVisit) -> Boolean) =
        DelightTrigger(
            id = id,
            priority = priority,
            condition = condition,
            presentation = DelightPresentation(DelightAnimation.GENERIC, "text_$id"),
        )

    /** Trigger auf "drei Mal Single 20" (rohe Summe 60, kein Bust). */
    private val sixty = trigger("sixty") { !it.bust && it.dartSum == 60 && it.darts.size == 3 }

    private fun x01(
        playerIds: List<Long>,
        triggers: List<DelightTrigger> = listOf(sixty),
        config: GameConfig = GameConfig(startScore = 501, doubleOut = true, legsToWin = 2, setsToWin = 1),
    ) = GameViewModel(
        matchRepository, playerRepository, playerIds, config, X01Mode(), X01UiAdapter(),
        DelightRegistry(triggers),
    )

    private fun countUp(playerIds: List<Long>, triggers: List<DelightTrigger>) =
        GameViewModel(
            matchRepository, playerRepository, playerIds, GameConfig(),
            CountUpMode(), CountUpUiAdapter(),
            DelightRegistry(triggers),
        )

    private suspend fun TestScope.start(vm: GameViewModel<*>): GameUiState.Playing {
        backgroundScope.launch { vm.uiState.collect {} }
        return vm.uiState.first { it is GameUiState.Playing } as GameUiState.Playing
    }

    private fun GameViewModel<*>.threeSingle20() {
        onNumber(20); onNumber(20); onNumber(20)
    }

    private val GameViewModel<*>.playing: GameUiState.Playing
        get() = uiState.value as GameUiState.Playing

    private val GameUiState.Playing.currentName: String
        get() = players.first { it.isCurrent }.name

    // --- Ausloesung -----------------------------------------------------------

    @Test
    fun passendeAufnahme_eventSchonBeimDrittenDart_mitVisitDaten() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = x01(listOf(tom, anna))
            start(vm)
            assertNull(vm.delightEvents.value)

            vm.threeSingle20()

            val event = vm.delightEvents.value!!
            assertEquals(1L, event.id)
            assertEquals("sixty", event.triggerId)
            assertEquals(DelightPresentation(DelightAnimation.GENERIC, "text_sixty"), event.presentation)
            assertEquals(List(3) { Dart.single(20) }, event.visit.darts)
            assertEquals(GameModeCatalog.X01, event.visit.modeKey)
            assertEquals(60, event.visit.scored)
            assertFalse(event.visit.bust)
            assertFalse(event.visit.checkout)
            assertFalse(event.visit.legEnded)
            assertEquals(tom, event.playerId)
            // Feier faellt in die Kontroll-Pause; der Timer ist angehalten.
            val review = vm.playing.turnReview!!
            assertTrue(review.heldForDelight)
        }

    @Test
    fun nichtPassendeAufnahme_keinEvent_timerLaeuftNormal() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = x01(listOf(tom, anna))
            start(vm)

            vm.onNumber(20); vm.onNumber(20); vm.onNumber(19)

            assertNull(vm.delightEvents.value)
            assertFalse(vm.playing.turnReview!!.heldForDelight)
            advanceTimeBy(GameViewModel.TURN_REVIEW_MILLIS + 1)
            runCurrent()
            assertEquals("Anna", vm.playing.currentName)
            assertNull(vm.playing.turnReview)
        }

    @Test
    fun keineEventsBeiTeilaufnahme() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            var evaluated = 0
            val vm = x01(listOf(tom, anna), listOf(trigger("count") { evaluated++; true }))
            start(vm)

            vm.onNumber(20); vm.onNumber(20)
            assertEquals(0, evaluated)
            assertNull(vm.delightEvents.value)

            vm.onNumber(20)
            assertEquals(1, evaluated)
        }

    @Test
    fun hoeherPriorisierterTriggerGewinnt() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = x01(
                listOf(tom, anna),
                listOf(sixty, trigger("high", priority = 5) { it.dartSum >= 60 }),
            )
            start(vm)

            vm.threeSingle20()

            assertEquals("high", vm.delightEvents.value?.triggerId)
        }

    // --- Produkt-Registry per Default (ADR-0041) --------------------------------

    /** X01-ViewModel mit der Default-Registry ([DelightRegistry.DEFAULT]). */
    private fun x01Default(playerIds: List<Long>, startScore: Int = 501) = GameViewModel(
        matchRepository, playerRepository, playerIds,
        GameConfig(startScore = startScore, doubleOut = true, legsToWin = 1, setsToWin = 1),
        X01Mode(), X01UiAdapter(),
    )

    @Test
    fun produktRegistryPerDefault_180InX01_konfetti() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = x01Default(listOf(tom, anna))
            start(vm)

            vm.onToggleTriple(); vm.onNumber(20)
            vm.onToggleTriple(); vm.onNumber(20)
            vm.onToggleTriple(); vm.onNumber(20)

            val event = vm.delightEvents.value!!
            assertEquals(ProductDelightTriggers.ID_ONE_EIGHTY, event.triggerId)
            assertEquals(DelightAnimation.CONFETTI, event.presentation.animation)
            assertEquals(DelightTextKeys.ONE_EIGHTY, event.presentation.textKey)
            assertTrue(vm.playing.turnReview!!.heldForDelight)
        }

    @Test
    fun produktRegistryPerDefault_waschmaschineInCountUp_spin() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = GameViewModel(
                matchRepository, playerRepository, listOf(tom, anna), GameConfig(),
                CountUpMode(), CountUpUiAdapter(),
            )
            start(vm)

            vm.onNumber(20); vm.onNumber(5); vm.onNumber(1)

            val event = vm.delightEvents.value!!
            assertEquals(ProductDelightTriggers.ID_WASHING_MACHINE, event.triggerId)
            assertEquals(DelightAnimation.SPIN, event.presentation.animation)
            assertEquals(GameModeCatalog.COUNT_UP, event.visit.modeKey)
        }

    @Test
    fun produktRegistryPerDefault_bustMitWaschmaschinenMuster_keinEvent() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            // Rest 27: 20 -> 7, 5 -> 2, 1 -> Rest 1 = Bust beim dritten Dart.
            val vm = x01Default(listOf(tom, anna), startScore = 27)
            start(vm)

            vm.onNumber(20); vm.onNumber(5); vm.onNumber(1)

            assertNull(vm.delightEvents.value)
            assertEquals("Anna", vm.playing.currentName)
        }

    @Test
    fun produktRegistryPerDefault_gewoehnlicheAufnahme_keinEvent() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = x01Default(listOf(tom, anna))
            start(vm)

            vm.onNumber(20); vm.onNumber(20); vm.onNumber(19)

            assertNull(vm.delightEvents.value)
            assertFalse(vm.playing.turnReview!!.heldForDelight)
        }

    // --- Pausen-Timer wartet auf die Feier ------------------------------------

    @Test
    fun pausenTimerWartetAufDismiss_undStartetDanachMitVollerDauer() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = x01(listOf(tom, anna))
            start(vm)

            vm.threeSingle20()
            val id = vm.delightEvents.value!!.id

            // Ohne Dismiss wechselt der Spieler nach der normalen Pausen-Dauer NICHT.
            advanceTimeBy(GameViewModel.TURN_REVIEW_MILLIS * 2)
            runCurrent()
            assertEquals("Tom", vm.playing.currentName)
            assertTrue(vm.playing.turnReview!!.heldForDelight)

            vm.onDelightDismissed(id)
            assertNull("Quittiertes Event ist nicht mehr ausstehend", vm.delightEvents.value)
            assertFalse(vm.playing.turnReview!!.heldForDelight)

            advanceTimeBy(GameViewModel.TURN_REVIEW_MILLIS - 1)
            runCurrent()
            assertNotNull("Timer laeuft ab Dismiss mit voller Dauer", vm.playing.turnReview)

            advanceTimeBy(2)
            runCurrent()
            assertNull(vm.playing.turnReview)
            assertEquals("Anna", vm.playing.currentName)
        }

    @Test
    fun feiernAbgeschaltet_sofortQuittiert_pauseLaeuftOhneVerzoegerung() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Einstellung "Feier-Animationen" aus (ADR-0040): GameScreen quittiert
            // laut planDelightIntake sofort; die Kontrollpause darf dann nicht
            // laenger dauern als ohne Feier.
            val (tom, anna) = twoPlayers()
            val vm = x01(listOf(tom, anna))
            start(vm)

            vm.threeSingle20()
            val event = vm.delightEvents.value!!
            val plan = planDelightIntake(
                eventId = event.id,
                enabled = false,
                lastShownId = vm.lastShownDelightId,
                activeId = null,
            )
            assertFalse(plan.show)
            assertEquals(listOf(event.id), plan.acknowledgeIds)
            plan.acknowledgeIds.forEach(vm::onDelightDismissed)

            assertNull(vm.delightEvents.value)
            assertFalse(vm.playing.turnReview!!.heldForDelight)
            advanceTimeBy(GameViewModel.TURN_REVIEW_MILLIS + 1)
            runCurrent()
            assertNull(vm.playing.turnReview)
            assertEquals("Anna", vm.playing.currentName)
        }

    @Test
    fun ohneDismiss_greiftSicherheitsnetz() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = x01(listOf(tom, anna))
            start(vm)

            vm.threeSingle20()

            advanceTimeBy(GameViewModel.DELIGHT_MAX_HOLD_MILLIS - 1)
            runCurrent()
            assertTrue(vm.playing.turnReview!!.heldForDelight)
            assertNotNull(vm.delightEvents.value)

            advanceTimeBy(2)
            runCurrent()
            // Sicherheitsnetz wirkt wie ein Dismiss: Feier verworfen, Timer laeuft.
            assertNull(vm.delightEvents.value)
            assertFalse(vm.playing.turnReview!!.heldForDelight)
            assertEquals("Tom", vm.playing.currentName)

            advanceTimeBy(GameViewModel.TURN_REVIEW_MILLIS)
            runCurrent()
            assertNull(vm.playing.turnReview)
            assertEquals("Anna", vm.playing.currentName)
        }

    @Test
    fun weiterWaehrendFeier_wechseltSofort_spaeteresDismissIstWirkungslos() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = x01(listOf(tom, anna))
            start(vm)

            vm.threeSingle20()
            val id = vm.delightEvents.value!!.id
            vm.onContinue()
            assertEquals("Anna", vm.playing.currentName)

            vm.onDelightDismissed(id)
            vm.onDelightDismissed(id)
            assertNull(vm.delightEvents.value)
            advanceTimeBy(GameViewModel.DELIGHT_MAX_HOLD_MILLIS + GameViewModel.TURN_REVIEW_MILLIS)
            runCurrent()
            assertEquals("Anna", vm.playing.currentName)
            assertNull(vm.playing.turnReview)
            assertTrue(vm.playing.players.first { it.name == "Anna" }.isCurrent)
        }

    // --- Robustheit -----------------------------------------------------------

    @Test
    fun werfendeBedingung_brichtSpielablaufNicht_keinEventKeineIdVerbraucht() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val boom = trigger("boom", priority = 10) { v ->
                if (v.dartSum == 60) error("kaputte Bedingung") else false
            }
            val fiftyNine = trigger("59") { it.dartSum == 59 }
            val vm = x01(listOf(tom, anna), listOf(boom, fiftyNine))
            start(vm)

            vm.threeSingle20()

            assertNull(vm.delightEvents.value)
            val review = vm.playing.turnReview!!
            assertFalse(review.heldForDelight)
            // Aufnahme ist trotzdem persistiert, der Pausen-Timer laeuft normal.
            val legId = matchRepository.getLegs(matchRepository.getMatches().single().id).single().id
            assertEquals(1, matchRepository.getTurns(legId).size)
            advanceTimeBy(GameViewModel.TURN_REVIEW_MILLIS + 1)
            runCurrent()
            assertEquals("Anna", vm.playing.currentName)

            // Folgende Aufnahme feiert regulaer - mit ID 1 (keine ID verbraucht).
            vm.onNumber(20); vm.onNumber(20); vm.onNumber(19)
            assertEquals(1L, vm.delightEvents.value!!.id)
            assertEquals("59", vm.delightEvents.value!!.triggerId)
        }

    // --- Undo -----------------------------------------------------------------

    @Test
    fun korrigierenWaehrendGehaltenemTimer_verwirftTimer_keinErneutesEvent_neuerWurfLoestNeuAus() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = x01(listOf(tom, anna))
            start(vm)

            vm.threeSingle20()
            val first = vm.delightEvents.value!!

            vm.onUndo()
            assertNull(vm.playing.turnReview)
            assertEquals("Tom", vm.playing.currentName)
            assertEquals(first, vm.delightEvents.value)

            // Weder Sicherheitsnetz noch Pausen-Timer laufen nach.
            advanceTimeBy(GameViewModel.DELIGHT_MAX_HOLD_MILLIS + GameViewModel.TURN_REVIEW_MILLIS * 2)
            runCurrent()
            assertEquals("Tom", vm.playing.currentName)
            assertNull(vm.playing.turnReview)
            assertEquals(first, vm.delightEvents.value)

            // Veraltetes Dismiss der ersten Feier: Event weg, aber kein Timer-Effekt.
            vm.onDelightDismissed(first.id)
            assertNull(vm.delightEvents.value)

            // Erneuter dritter Dart: neue Feier mit neuer ID, Timer wieder gehalten.
            vm.onNumber(20)
            val second = vm.delightEvents.value!!
            assertEquals(first.id + 1, second.id)
            assertTrue(vm.playing.turnReview!!.heldForDelight)

            // Dismiss der ALTEN ID beeinflusst die neue Pause nicht.
            vm.onDelightDismissed(first.id)
            assertEquals(second, vm.delightEvents.value)
            assertTrue(vm.playing.turnReview!!.heldForDelight)

            vm.onDelightDismissed(second.id)
            advanceTimeBy(GameViewModel.TURN_REVIEW_MILLIS + 1)
            runCurrent()
            assertEquals("Anna", vm.playing.currentName)
        }

    @Test
    fun crossTurnUndo_loestKeinEventErneutAus() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = x01(listOf(tom, anna))
            start(vm)

            vm.threeSingle20()
            val first = vm.delightEvents.value!!
            vm.onDelightDismissed(first.id)
            vm.onContinue()
            assertEquals("Anna", vm.playing.currentName)

            // Annas leere Aufnahme -> Toms abgeschlossene Aufnahme wieder oeffnen.
            vm.onUndo()
            assertEquals("Tom", vm.playing.currentName)
            assertNull(vm.delightEvents.value)
            vm.onUndo()
            assertNull(vm.delightEvents.value)

            vm.onNumber(20); vm.onNumber(20)
            assertEquals(first.id + 1, vm.delightEvents.value!!.id)
        }

    // --- Bust / Sieg ----------------------------------------------------------

    @Test
    fun bustAufnahme_bedingungEntscheidet_keineKontrollPause() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = x01(
                listOf(tom, anna),
                listOf(sixty, trigger("bust") { it.bust }),
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            start(vm)

            // Rest 40: Single 20 -> 20, Single 20 -> 0 ohne Double -> Bust beim 2. Dart.
            vm.onNumber(20); vm.onNumber(20)

            val event = vm.delightEvents.value!!
            assertEquals("bust", event.triggerId)
            assertTrue(event.visit.bust)
            assertEquals(0, event.visit.scored)
            assertEquals(40, event.visit.dartSum)
            assertEquals(2, event.visit.darts.size)
            assertNull(vm.playing.turnReview)
            assertEquals("Anna", vm.playing.currentName)
        }

    @Test
    fun legGewinn_eventMitCheckout_ohneWarten() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = x01(
                listOf(tom, anna),
                listOf(trigger("checkout") { it.checkout }),
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            start(vm)

            vm.onToggleDouble(); vm.onNumber(20)

            vm.uiState.first { it is GameUiState.LegWon }
            val event = vm.delightEvents.value!!
            assertEquals("checkout", event.triggerId)
            assertTrue(event.visit.checkout)
            assertTrue(event.visit.legEnded)
            assertEquals(listOf(Dart.double(20)), event.visit.darts)
            assertEquals(tom, event.playerId)
        }

    @Test
    fun matchGewinn_eventUndSiegZuruecknehmen_keinErneutesEvent_erneuterSiegLoestNeuAus() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = x01(
                listOf(tom, anna),
                listOf(trigger("checkout") { it.checkout }),
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 1, setsToWin = 1),
            )
            start(vm)

            vm.onToggleDouble(); vm.onNumber(20)
            vm.uiState.first { it is GameUiState.MatchWon }
            val first = vm.delightEvents.value!!
            assertEquals(1L, first.id)

            vm.onUndoWin()
            vm.uiState.first { it is GameUiState.Playing }
            assertEquals("Undo-Sieg loest nichts erneut aus", first, vm.delightEvents.value)

            vm.onToggleDouble(); vm.onNumber(20)
            vm.uiState.first { it is GameUiState.MatchWon }
            assertEquals(2L, vm.delightEvents.value!!.id)
        }

    // --- Nicht-X01-Modus ------------------------------------------------------

    @Test
    fun countUp_eventMitModusKennung() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val countUpOnly = trigger("countup") { it.modeKey == GameModeCatalog.COUNT_UP && it.dartSum == 60 }
            val vm = countUp(listOf(tom, anna), listOf(countUpOnly))
            start(vm)

            vm.threeSingle20()

            val event = vm.delightEvents.value!!
            assertEquals("countup", event.triggerId)
            assertEquals(GameModeCatalog.COUNT_UP, event.visit.modeKey)
            assertEquals(tom, event.playerId)
        }

    @Test
    fun x01_modusGebundenerTriggerFuerAnderenModus_loestNichtAus() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val countUpOnly = trigger("countup") { it.modeKey == GameModeCatalog.COUNT_UP }
            val vm = x01(listOf(tom, anna), listOf(countUpOnly))
            start(vm)

            vm.threeSingle20()

            assertNull(vm.delightEvents.value)
        }

    @Test
    fun countUp_rundenbasiertesLegEnde_legEndedOhneCheckout() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val legEnd = trigger("legEnd") { it.legEnded && !it.checkout }
            val vm = countUp(listOf(tom, anna), listOf(legEnd))
            start(vm)

            // Tom wirft je Runde 60, Anna nur Fehlwuerfe; das Leg endet per
            // Punktvergleich mit Annas letzter Aufnahme (Gewinner Tom, nicht Werferin).
            repeat(CountUpState.ROUNDS) { round ->
                vm.threeSingle20()
                vm.onContinue()
                assertNull("Kein Leg-Ende vor Runde ${round + 1}", vm.delightEvents.value)
                vm.onOut(); vm.onOut(); vm.onOut()
                if (round < CountUpState.ROUNDS - 1) {
                    assertNull(vm.delightEvents.value)
                    vm.onContinue()
                }
            }

            vm.uiState.first { it is GameUiState.MatchWon }
            val event = vm.delightEvents.value!!
            assertEquals("legEnd", event.triggerId)
            assertTrue(event.visit.legEnded)
            assertFalse(event.visit.checkout)
            assertEquals(anna, event.playerId)
            assertEquals(List(3) { Dart.miss() }, event.visit.darts)
        }

    @Test
    fun cricket_scoredIstModusWertung_dartSumIstRoheSumme() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = GameViewModel(
                matchRepository, playerRepository, listOf(tom, anna), GameConfig(),
                CricketMode(), CricketUiAdapter(),
                DelightRegistry(listOf(trigger("cricket") { it.modeKey == GameModeCatalog.CRICKET })),
            )
            start(vm)

            // Triple 20 schliesst die 20 (0 Punkte), zwei Single 20 zaehlen je 20,
            // da Anna die 20 noch offen hat.
            vm.onToggleTriple(); vm.onNumber(20)
            vm.onNumber(20); vm.onNumber(20)

            val visit = vm.delightEvents.value!!.visit
            assertEquals(40, visit.scored)
            assertEquals(100, visit.dartSum)
            assertFalse(visit.checkout)
            assertFalse(visit.legEnded)
        }

    @Test
    fun doppelterDismiss_verlaengertDiePauseNicht() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = x01(listOf(tom, anna))
            start(vm)

            vm.threeSingle20()
            val id = vm.delightEvents.value!!.id

            vm.onDelightDismissed(id)
            advanceTimeBy(GameViewModel.TURN_REVIEW_MILLIS - 500)
            runCurrent()
            // Zweites Dismiss derselben ID darf den Timer nicht neu starten.
            vm.onDelightDismissed(id)
            assertNotNull(vm.playing.turnReview)

            advanceTimeBy(501)
            runCurrent()
            assertNull("Pause endet nach EINER vollen Dauer ab erstem Dismiss", vm.playing.turnReview)
            assertEquals("Anna", vm.playing.currentName)
        }

    // --- Dedupe-Zustand je ViewModel-Instanz (ADR-0039) -------------------------

    @Test
    fun angezeigtMerktSichDieIdOhneZuQuittieren() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = x01(listOf(tom, anna))
            start(vm)
            assertEquals(0L, vm.lastShownDelightId)

            vm.threeSingle20()
            val id = vm.delightEvents.value!!.id
            vm.onDelightShown(id)

            assertEquals(id, vm.lastShownDelightId)
            // Anzeigen ist kein Dismiss: Event steht noch, Pause bleibt gehalten.
            assertNotNull(vm.delightEvents.value)
            assertTrue(vm.playing.turnReview!!.heldForDelight)
        }

    @Test
    fun neueViewModelInstanz_beginntMitFrischemDedupeZustandUndNeuerKennung() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Simuliert Prozess-Tod: altes ViewModel hat Feier 1 gezeigt, das neue
            // vergibt wieder ID 1 - diese darf nicht als "schon gezeigt" gelten.
            val (tom, anna) = twoPlayers()
            val old = x01(listOf(tom, anna))
            start(old)
            old.threeSingle20()
            old.onDelightShown(old.delightEvents.value!!.id)
            assertEquals(1L, old.lastShownDelightId)

            val fresh = x01(listOf(tom, anna))
            start(fresh)
            fresh.threeSingle20()

            val event = fresh.delightEvents.value!!
            assertEquals(1L, event.id)
            assertEquals(0L, fresh.lastShownDelightId)
            assertTrue(old.delightSessionToken != fresh.delightSessionToken)
            val plan = planDelightIntake(
                eventId = event.id,
                enabled = true,
                lastShownId = fresh.lastShownDelightId,
                activeId = null,
            )
            assertTrue("Erste Feier des neuen ViewModels wird angezeigt", plan.show)
        }
}
