package com.mechanicel.tomsdarts.ui.game

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
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
