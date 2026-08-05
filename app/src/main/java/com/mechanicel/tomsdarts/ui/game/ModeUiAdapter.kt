package com.mechanicel.tomsdarts.ui.game

import com.mechanicel.tomsdarts.game.AroundTheClockState
import com.mechanicel.tomsdarts.game.CountUpState
import com.mechanicel.tomsdarts.game.CricketState
import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.KillerState
import com.mechanicel.tomsdarts.game.ShanghaiState
import com.mechanicel.tomsdarts.game.X01State
import com.mechanicel.tomsdarts.game.checkoutSuggestion

/**
 * Uebersetzt den modus-spezifischen Spielerzustand [S] in modus-agnostische
 * UI-Bausteine. Damit bleibt das [GameViewModel] generisch ueber [S]: es kennt
 * weder [X01State] noch die X01-Checkout-Logik direkt, sondern delegiert das
 * Ableiten von Anzeige-Kern ([board]) und Checkout-Vorschlag ([checkout]) an den
 * passenden Adapter. Ein weiterer Modus (z.B. Cricket) bringt seinen eigenen
 * Adapter mit; das ViewModel bleibt unveraendert. Reine UI-Domaenenlogik
 * (offline-first, kein Android-/Room-Bezug).
 *
 * @param S Modus-spezifischer Spielerzustand (z.B. [X01State]).
 */
interface ModeUiAdapter<S : Any> {

    /** Leitet den modus-spezifischen Anzeige-Kern der Karte aus [state] ab. */
    fun board(state: S): PlayerBoardUi

    /**
     * Gegner-bewusste Variante von [board] fuer Modi, deren Anzeige abgeleitete
     * Cross-Player-Werte braucht (z.B. Killer: die verbleibenden Leben eines
     * Spielers ergeben sich aus den Treffern der Mitspieler).
     *
     * [opponents] enthaelt die Zustaende der MITSPIELER (ohne diesen Spieler) und
     * ist strikt nur lesend zu nutzen - der Vertrag spiegelt bewusst den von
     * [com.mechanicel.tomsdarts.game.GameMode.applyDart]. Der Default delegiert an
     * [board] und ignoriert die Liste, sodass Adapter ohne Gegnerbezug (X01,
     * Cricket, ...) unveraendert bleiben.
     */
    fun board(state: S, opponents: List<S>): PlayerBoardUi = board(state)

    /**
     * Empfohlene Checkout-Kombination fuer den aktuellen Werfer aus [state] und
     * [config], oder `null`, wenn der Modus keinen Vorschlag kennt bzw. der
     * Zustand nicht auscheckbar ist.
     */
    fun checkout(state: S, config: GameConfig): List<Dart>?
}

/**
 * UI-Adapter fuer den X01-Modus: Anzeige-Kern ist die Restpunktzahl, der
 * Checkout-Vorschlag stammt aus der bestehenden [checkoutSuggestion]-Logik
 * (nur bei Double-Out relevant).
 */
class X01UiAdapter : ModeUiAdapter<X01State> {

    override fun board(state: X01State): PlayerBoardUi = PlayerBoardUi.X01(state.remaining)

    override fun checkout(state: X01State, config: GameConfig): List<Dart>? =
        checkoutSuggestion(state.remaining, config.doubleOut)
}

/**
 * UI-Adapter fuer den Cricket-Modus: Anzeige-Kern sind die Marks je Feld (in der
 * festen Anzeigereihenfolge 20,19,18,17,16,15,Bull, auf 3 gekappt) plus der
 * Punktestand. Cricket kennt keinen Checkout-Vorschlag ([checkout] == null).
 */
class CricketUiAdapter : ModeUiAdapter<CricketState> {

    override fun board(state: CricketState): PlayerBoardUi = PlayerBoardUi.Cricket(
        fields = DISPLAY_ORDER.map { target ->
            CricketFieldUi(
                target = target,
                marks = state.marksOf(target).coerceIn(0, CricketState.CLOSED_MARKS),
            )
        },
        points = state.points,
    )

    override fun checkout(state: CricketState, config: GameConfig): List<Dart>? = null

    companion object {
        /** Feste Anzeigereihenfolge der Cricket-Felder (20 oben, Bull unten). */
        private val DISPLAY_ORDER: List<Int> = listOf(20, 19, 18, 17, 16, 15, CricketState.BULL)
    }
}

/**
 * UI-Adapter fuer den Around-the-Clock-Modus: Anzeige-Kern sind die aktuelle
 * Zielzahl und der Fortschritt (Anzahl bereits erreichter Zahlen == `target - 1`,
 * defensiv auf 0..[AroundTheClockState.TOTAL] gekappt, falls nach dem Leg-Gewinn
 * ein Ziel > [AroundTheClockState.TOTAL] doch abgeleitet wird). Around the Clock
 * kennt keinen Checkout-Vorschlag ([checkout] == null).
 */
class AroundTheClockUiAdapter : ModeUiAdapter<AroundTheClockState> {

    override fun board(state: AroundTheClockState): PlayerBoardUi = PlayerBoardUi.AroundTheClock(
        target = state.target,
        completed = (state.target - 1).coerceIn(0, AroundTheClockState.TOTAL),
    )

    override fun checkout(state: AroundTheClockState, config: GameConfig): List<Dart>? = null
}

/**
 * UI-Adapter fuer den Shanghai-Modus: Anzeige-Kern sind Runde, Zielzahl,
 * Punktestand und die Treffer der laufenden Aufnahme. Runde und Ziel kommen aus
 * den abgeleiteten Eigenschaften des [ShanghaiState] - die UI rechnet die Formel
 * bewusst nicht nach, damit es nur EINE Quelle gibt. Shanghai kennt keinen
 * Checkout-Vorschlag ([checkout] == null).
 */
class ShanghaiUiAdapter : ModeUiAdapter<ShanghaiState> {

    override fun board(state: ShanghaiState): PlayerBoardUi = PlayerBoardUi.Shanghai(
        round = state.round,
        target = state.target,
        points = state.points,
        visitHits = state.visitHits,
    )

    override fun checkout(state: ShanghaiState, config: GameConfig): List<Dart>? = null
}

/**
 * UI-Adapter fuer den Count-Up-Modus: Anzeige-Kern sind Runde und Punktestand.
 * Die Runde kommt aus der abgeleiteten Eigenschaft des [CountUpState] - die UI
 * rechnet die Formel bewusst nicht nach, damit es nur EINE Quelle gibt. Count Up
 * kennt keinen Checkout-Vorschlag ([checkout] == null).
 */
class CountUpUiAdapter : ModeUiAdapter<CountUpState> {

    override fun board(state: CountUpState): PlayerBoardUi = PlayerBoardUi.CountUp(
        round = state.round,
        points = state.points,
    )

    override fun checkout(state: CountUpState, config: GameConfig): List<Dart>? = null
}

/**
 * UI-Adapter fuer den Killer-Modus: Anzeige-Kern sind die eigene Zahl, der
 * Killer-Status und die verbleibenden Leben.
 *
 * Killer ist der erste Modus mit GEGNER-abhaengiger Anzeige (Inversions-Muster,
 * ADR-0031): die Leben eines Spielers stecken nicht in seinem eigenen Zustand,
 * sondern in den Treffern der Mitspieler. Deshalb ueberschreibt dieser Adapter
 * als einziger die gegner-bewusste [board]-Variante und leitet die Leben ueber
 * die EINE Formel-Quelle [KillerState.livesOf] ab - nachgerechnet wird hier
 * nichts. Killer kennt keinen Checkout-Vorschlag ([checkout] == null).
 */
class KillerUiAdapter : ModeUiAdapter<KillerState> {

    /**
     * Gegner-loser Fallback des Interfaces: ohne Mitspieler-Zustaende sind keine
     * Treffer bekannt, also zeigt die Karte die vollen Leben. Die regulaere
     * Anzeige laeuft ueber die gegner-bewusste Variante, die das ViewModel
     * ([GameViewModel.buildPlayers]) nutzt.
     */
    override fun board(state: KillerState): PlayerBoardUi = PlayerBoardUi.Killer(
        number = state.number,
        isKiller = state.isKiller,
        lives = KillerState.LIVES,
        maxLives = KillerState.LIVES,
    )

    override fun board(state: KillerState, opponents: List<KillerState>): PlayerBoardUi =
        PlayerBoardUi.Killer(
            number = state.number,
            isKiller = state.isKiller,
            lives = KillerState.livesOf(state.number, opponents),
            maxLives = KillerState.LIVES,
        )

    override fun checkout(state: KillerState, config: GameConfig): List<Dart>? = null
}
