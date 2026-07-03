package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.GameMode

/**
 * Koppelt mehrere [LegEngine]-Instanzen zu einem vollstaendigen Match mit
 * Spielerwechsel sowie Leg-, Set- und Match-Aggregation.
 *
 * Verantwortlichkeiten (bewusst eng geschnitten):
 * - Mehrspieler: jeder Spieler besitzt im AKTUELLEN Leg seine eigene
 *   [LegEngine]; der aktuell aktive Spieler wirft.
 * - Aufnahme-Wechsel: endet die Aufnahme des aktiven Spielers (3 Darts, Bust
 *   oder Leg-Gewinn), wird AUTOMATISCH zum naechsten Spieler gewechselt.
 * - Leg-/Set-/Match-Aggregation: zaehlt gewonnene Legs pro Set und Sets pro
 *   Match, startet neue Legs/Sets und erkennt den Match-Gewinn.
 *
 * NICHT Aufgabe dieser Engine: UI, Persistenz, Spielerauswahl. Die Engine ist
 * modus-agnostisch ueber [GameMode] und reine Domaenenlogik: kein Android-/
 * Room-/Compose-Bezug, mit reinem JUnit testbar. Die echte Zuordnung der
 * Spieler-IDs leistet der Aufrufer (VM/Persistenz) ueber die uebergebene
 * [playerIds]-Liste; die Engine reicht diese IDs nur durch.
 *
 * Spielerwechsel-Modell (gewaehlte Variante): Der Wechsel passiert INTERN in
 * [applyDart]. Das noetige Signal liefert das [MatchDartResult] ueber
 * [MatchDartResult.turnEnded] sowie [MatchDartResult.playerId] (Werfer) und
 * [MatchDartResult.nextPlayerId] (danach aktiver Spieler). Eine separate
 * `startNewTurn`-Methode gibt es bewusst nicht.
 *
 * Leg-/Set-Logik:
 * - Leg-Gewinn -> `legsWonInSet` des Gewinners +1.
 * - `legsWonInSet >= config.legsToWin` -> Set gewonnen: `setsWon` +1,
 *   `legsWonInSet` aller Spieler zurueckgesetzt.
 * - `setsWon >= config.setsToWin` -> Match gewonnen; danach sind [applyDart]
 *   und [undoLastDart] No-ops.
 * - Bei neuem Leg (innerhalb oder ueber Sets hinweg) rotiert der Startspieler:
 *   der Spieler NACH dem bisherigen Leg-Startspieler beginnt.
 *
 * @param S Modus-spezifischer Spielerzustand (z.B. [com.mechanicel.tomsdarts.game.X01State]).
 * @param mode Die Modus-Strategie, identisch fuer alle Spieler/Legs.
 * @param config Die Regel-Konfiguration (insb. [GameConfig.legsToWin] und
 *   [GameConfig.setsToWin]).
 * @param playerIds Reihenfolge und Kennungen der Spieler (mindestens zwei).
 */
class MatchEngine<S : Any>(
    private val mode: GameMode<S>,
    private val config: GameConfig,
    private val playerIds: List<Long>,
) {

    init {
        require(playerIds.size >= 2) {
            "MatchEngine benoetigt mindestens zwei Spieler, war: ${playerIds.size}"
        }
    }

    private val playerCount: Int = playerIds.size

    /** Je Spieler die LegEngine des aktuell laufenden Legs (wird je Leg neu erzeugt). */
    private val legEngines: MutableList<LegEngine<S>> =
        MutableList(playerCount) { LegEngine(mode, config) }

    /** Im aktuellen Set gewonnene Legs je Spieler (Index-parallel zu [playerIds]). */
    private val legsWonInSet: IntArray = IntArray(playerCount)

    /** Im Match gewonnene Sets je Spieler (Index-parallel zu [playerIds]). */
    private val setsWon: IntArray = IntArray(playerCount)

    /** Startspieler-Index des aktuellen Legs; rotiert mit jedem neuen Leg. */
    private var legStartIndex: Int = 0

    /** Index des aktuell werfenden Spielers. */
    var currentPlayerIndex: Int = 0
        private set

    /** 1-basierte Nummer des laufenden Sets. */
    var currentSetNumber: Int = 1
        private set

    /** 1-basierte Nummer des laufenden Legs IM aktuellen Set. */
    var currentLegNumber: Int = 1
        private set

    /** True, sobald das Match entschieden ist. */
    var isMatchWon: Boolean = false
        private set

    /** Kennung des Match-Gewinners, sonst null. */
    var matchWinnerId: Long? = null
        private set

    /** Kennung des aktuell werfenden Spielers. */
    val currentPlayerId: Long get() = playerIds[currentPlayerIndex]

    /** Zustaende aller Spieler in Konstruktor-Reihenfolge. */
    val playerStates: List<PlayerMatchState<S>>
        get() = playerIds.indices.map { i ->
            val snap = legEngines[i].snapshot()
            PlayerMatchState(
                playerId = playerIds[i],
                state = snap.state,
                legsWonInSet = legsWonInSet[i],
                setsWon = setsWon[i],
                legSnapshot = snap,
            )
        }

    /** Liefert den aktuellen Match-Zustand als unveraenderliche Momentaufnahme. */
    fun snapshot(): MatchSnapshot<S> = MatchSnapshot(
        playerStates = playerStates,
        currentPlayerIndex = currentPlayerIndex,
        currentPlayerId = currentPlayerId,
        currentSetNumber = currentSetNumber,
        currentLegNumber = currentLegNumber,
        isMatchWon = isMatchWon,
        matchWinnerId = matchWinnerId,
    )

    /**
     * Verarbeitet GENAU EINEN Dart des aktuell aktiven Spielers.
     *
     * Delegiert an dessen [LegEngine] und wertet anschliessend die Folgen aus:
     * - Endet die Aufnahme regulaer oder per Bust, wird die LegEngine des
     *   Spielers via [LegEngine.startNewTurn] auf die naechste Aufnahme gestellt
     *   und zum naechsten Spieler gewechselt.
     * - Gewinnt der Dart das Leg, werden Leg-/Set-/Match-Zaehler fortgeschrieben
     *   und ggf. ein neues Leg/Set gestartet (LegEngines reset, Startspieler
     *   rotiert) bzw. der Match-Gewinn gesetzt.
     *
     * No-op (kein Crash): Ist das Match bereits entschieden ([isMatchWon]),
     * bleibt der Zustand unveraendert; das Ergebnis hat `accepted == false` und
     * `dartResult == null`.
     */
    fun applyDart(dart: Dart): MatchDartResult<S> {
        if (isMatchWon) {
            val current = legEngines[currentPlayerIndex].snapshot()
            return MatchDartResult(
                accepted = false,
                dartResult = null,
                playerId = currentPlayerId,
                turnEnded = false,
                bust = false,
                legWon = false,
                setWon = false,
                matchWon = true,
                matchWinnerId = matchWinnerId,
                nextPlayerId = currentPlayerId,
                legSnapshot = current,
                snapshot = snapshot(),
            )
        }

        val throwerIndex = currentPlayerIndex
        val throwerId = playerIds[throwerIndex]
        val dartResult = legEngines[throwerIndex].applyDart(dart)
        val legSnapshot = dartResult.snapshot

        var setWon = false
        var matchWon = false

        when {
            dartResult.legWon -> {
                legsWonInSet[throwerIndex]++
                if (legsWonInSet[throwerIndex] >= config.legsToWin) {
                    setWon = true
                    setsWon[throwerIndex]++
                    for (i in legsWonInSet.indices) legsWonInSet[i] = 0
                    if (setsWon[throwerIndex] >= config.setsToWin) {
                        matchWon = true
                        isMatchWon = true
                        matchWinnerId = throwerId
                        // Kein neues Leg/Set: Match ist entschieden.
                    } else {
                        startNextLeg(newSet = true)
                    }
                } else {
                    startNextLeg(newSet = false)
                }
            }

            dartResult.turnEnded -> {
                // Regulaeres Aufnahme-Ende oder Bust: naechste Aufnahme + Spielerwechsel.
                legEngines[throwerIndex].startNewTurn()
                currentPlayerIndex = nextIndex(throwerIndex)
            }

            // Sonst: regulaerer Dart, Aufnahme laeuft weiter, kein Wechsel.
        }

        return MatchDartResult(
            accepted = true,
            dartResult = dartResult,
            playerId = throwerId,
            turnEnded = dartResult.turnEnded,
            bust = dartResult.bust,
            legWon = dartResult.legWon,
            setWon = setWon,
            matchWon = matchWon,
            matchWinnerId = matchWinnerId,
            nextPlayerId = currentPlayerId,
            legSnapshot = legSnapshot,
            snapshot = snapshot(),
        )
    }

    /**
     * Macht den zuletzt geworfenen Dart der LAUFENDEN Aufnahme des aktuellen
     * Spielers rueckgaengig (delegiert an dessen [LegEngine.undoLastDart]).
     *
     * Nur innerhalb der laufenden Aufnahme moeglich: Nach einem Spielerwechsel
     * oder Leg-/Set-Uebergang laesst sich die vorherige Aufnahme nicht mehr
     * zurueckholen. No-op (Rueckgabe `false`), wenn das Match entschieden ist.
     */
    fun undoLastDart(): Boolean {
        if (isMatchWon) return false
        return legEngines[currentPlayerIndex].undoLastDart()
    }

    /**
     * Startet das naechste Leg: frische LegEngines fuer alle Spieler, Rotation
     * des Startspielers und Fortschreiben der Leg-/Set-Nummern.
     *
     * @param newSet True, wenn zugleich ein neues Set beginnt (Set-Nummer +1,
     *   Leg-Nummer zurueck auf 1); sonst nur Leg-Nummer +1 im selben Set.
     */
    private fun startNextLeg(newSet: Boolean) {
        for (i in legEngines.indices) {
            legEngines[i] = LegEngine(mode, config)
        }
        legStartIndex = nextIndex(legStartIndex)
        currentPlayerIndex = legStartIndex
        if (newSet) {
            currentSetNumber++
            currentLegNumber = 1
        } else {
            currentLegNumber++
        }
    }

    private fun nextIndex(index: Int): Int = (index + 1) % playerCount
}
