package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.DartOutcome
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
 * Leg-Ende (zwei Wege, gleiche Buchfuehrung):
 * - `legWon` -> der WERFER gewinnt das Leg direkt (Checkout/Ziel erreicht).
 * - `legEnded` -> das Leg ist entschieden, ohne dass der Werfer zwingend
 *   gewinnt (rundenbasierte Modi). Den Gewinner ermittelt die Engine per
 *   Rangvergleich ueber ALLE Spieler ([GameMode.legScore], hoechster Wert
 *   gewinnt). In beiden Faellen meldet [MatchDartResult.legWinnerId], wer das
 *   Leg fuer sich entschieden hat.
 *
 * Leg-/Set-Logik:
 * - Leg-Gewinn -> `legsWonInSet` des Gewinners +1.
 * - `legsWonInSet >= config.legsToWin` -> Set gewonnen: `setsWon` +1,
 *   `legsWonInSet` aller Spieler zurueckgesetzt.
 * - `setsWon >= config.setsToWin` -> Match gewonnen; danach ist [applyDart] ein
 *   No-op.
 * - Bei neuem Leg (innerhalb oder ueber Sets hinweg) rotiert der Startspieler:
 *   der Spieler NACH dem bisherigen Leg-Startspieler beginnt.
 *
 * Aufgeschobener Leg-Wechsel: Ein leg-gewinnender Dart schreibt die Zaehler und
 * Nummern SOFORT fort (damit Sieg-Anzeigen den neuen Stand lesen), setzt die
 * [LegEngine]s und die Undo-Historie aber noch NICHT zurueck. Dieser Reset ist
 * bis [commitLegTransition] aufgeschoben (spaetestens beim naechsten [applyDart]
 * nachgeholt). Dadurch laesst sich ein versehentlicher Sieg-Dart per
 * [undoLastDart] noch zuruecknehmen (Leg-/Match-Abschluss inklusive); erst nach
 * dem Commit ist die Leg-Grenze wieder die Undo-Grenze.
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
        MutableList(playerCount) { index -> createLegEngine(index) }

    /**
     * Erzeugt die [LegEngine] fuer den Spieler an [playerIndex] samt Gegner-
     * Provider. Der Provider liest bei jedem Wurf LIVE die Zustaende der jeweils
     * ANDEREN Spieler aus [legEngines] (ohne den Spieler selbst) - dadurch bleibt
     * er auch nach einem Undo-Voll-Replay korrekt, bei dem alle Engines frisch
     * ersetzt werden. Modi ohne Gegnerbezug (X01) ignorieren die Liste.
     *
     * Der [playerIndex] wird zusaetzlich an die [LegEngine] durchgereicht, damit
     * Modi mit per-Spieler-Identitaet ihren Startzustand vom Sitzplatz ableiten
     * koennen ([GameMode.initialState] mit Index). Weil ALLE drei Erzeugungspfade
     * (Konstruktor-Initialisierung, Undo-Voll-Replay in [undoLastDart] und
     * Leg-Wechsel in [commitLegTransition]) ueber diese eine Stelle laufen, bleibt
     * die Identitaet eines Spielers ueber Undo und Leg-Grenzen hinweg stabil -
     * vorausgesetzt, der Modus leitet sie deterministisch ab (siehe Vertrag dort).
     */
    private fun createLegEngine(playerIndex: Int): LegEngine<S> =
        LegEngine(
            mode = mode,
            config = config,
            opponents = {
                legEngines.filterIndexed { i, _ -> i != playerIndex }.map { it.state }
            },
            playerIndex = playerIndex,
        )

    /**
     * Alle im AKTUELLEN Leg akzeptierten Darts in exakter Wurfreihenfolge (ueber
     * Aufnahme- und Spielerwechsel-Grenzen hinweg). Grundlage fuer das
     * unbegrenzte [undoLastDart] innerhalb des Legs: bei einem Undo wird der
     * letzte Dart entfernt und der Rest deterministisch neu durchgespielt.
     *
     * Wird erst beim Vollzug des Leg-Wechsels geleert ([commitLegTransition]) -
     * NICHT schon beim leg-/matchgewinnenden Dart. Damit ist genau der
     * Sieg-Dart noch zuruecknehmbar; als LETZTER Eintrag kann die Historie also
     * einen leg-gewinnenden Dart tragen (nie an frueherer Stelle, weil jeder
     * weitere Wurf den Leg-Wechsel zuvor vollzieht).
     */
    private val legDartHistory: MutableList<Dart> = mutableListOf()

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

    /**
     * Art eines aufgeschobenen Leg-Wechsels. Zaehler/Nummern sind zum Zeitpunkt
     * des Sieg-Darts bereits fortgeschrieben; offen ist nur noch der Reset der
     * [LegEngine]s und der Undo-Historie.
     */
    private enum class PendingLegTransition {
        /** Naechstes Leg im selben Set. */
        NEXT_LEG,

        /** Naechstes Leg in einem neuen Set. */
        NEXT_SET,

        /** Match entschieden - es folgt kein weiteres Leg mehr. */
        MATCH_END,
    }

    /**
     * Aufgeschobener Leg-Wechsel nach einem leg-/matchgewinnenden Dart, sonst
     * `null`. Solange gesetzt, zeigen die [LegEngine]s noch den Endstand des
     * gewonnenen Legs und der Sieg-Dart liegt noch in der Historie - Grundlage
     * fuer das Zuruecknehmen eines versehentlichen Sieges ([undoLastDart]).
     */
    private var pendingLegTransition: PendingLegTransition? = null

    /**
     * Zaehler-Stand zu BEGINN des laufenden Legs. Wird beim Leg-Start gesichert
     * und beim Zuruecknehmen eines Sieg-Darts wiederhergestellt, damit Legs/Sets,
     * Nummern und Startspieler-Rotation exakt auf den Stand vor dem Sieg
     * zurueckfallen.
     *
     * @param legsWonInSet Gewonnene Legs im Set je Spieler (Kopie).
     * @param setsWon Gewonnene Sets je Spieler (Kopie).
     * @param setNumber Nummer des laufenden Sets.
     * @param legNumber Nummer des laufenden Legs im Set.
     * @param startIndex Startspieler-Index des laufenden Legs.
     */
    private class LegBaseline(
        val legsWonInSet: List<Int>,
        val setsWon: List<Int>,
        val setNumber: Int,
        val legNumber: Int,
        val startIndex: Int,
    )

    /** Gesicherter Zaehler-Stand zu Beginn des laufenden Legs. */
    private var legBaseline: LegBaseline = captureLegBaseline()

    /** Kennung des aktuell werfenden Spielers. */
    val currentPlayerId: Long get() = playerIds[currentPlayerIndex]

    /**
     * Anzahl der im aktuellen Leg bisher akzeptierten Darts (ueber alle Spieler
     * und Aufnahmen hinweg). `0` genau dann, wenn im laufenden Leg noch kein Dart
     * gefallen ist -> [undoLastDart] liefert dann `false`. Unmittelbar nach einem
     * Leg-/Match-Gewinn zaehlt der noch ruecknehmbare Sieg-Dart mit; erst
     * [commitLegTransition] setzt den Wert auf `0` zurueck.
     */
    val dartsThrownInCurrentLeg: Int get() = legDartHistory.size

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
     * - Gewinnt der Dart das Leg (`legWon` fuer den Werfer) oder entscheidet er
     *   es per Rangvergleich (`legEnded`, Gewinner ueber [GameMode.legScore]),
     *   werden Leg-/Set-/Match-Zaehler des GEWINNERS fortgeschrieben und die
     *   Startspieler-Rotation vorausberechnet; der Reset der LegEngines bleibt
     *   bis [commitLegTransition] aufgeschoben bzw. der Match-Gewinn wird
     *   gesetzt. Wer das Leg genommen hat, steht in
     *   [MatchDartResult.legWinnerId].
     *
     * No-op (kein Crash): Ist das Match bereits entschieden ([isMatchWon]),
     * bleibt der Zustand unveraendert; das Ergebnis hat `accepted == false` und
     * `dartResult == null`.
     *
     * Steht noch ein aufgeschobener Leg-Wechsel aus (der Aufrufer hat nach einem
     * Leg-Gewinn kein [commitLegTransition] gerufen), wird dieser hier
     * nachgeholt, BEVOR der neue Dart verarbeitet wird - der neue Wurf gehoert
     * bereits zum naechsten Leg.
     *
     * Der akzeptierte Dart wird zusaetzlich in [legDartHistory] aufgezeichnet, um
     * [undoLastDart] das Zurueckspulen ueber Aufnahme- und Spielerwechsel-Grenzen
     * zu ermoeglichen - inklusive eines leg-/matchgewinnenden Darts, solange der
     * Leg-Wechsel noch nicht vollzogen ist.
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
                legWinnerId = null,
                setWon = false,
                matchWon = true,
                matchWinnerId = matchWinnerId,
                nextPlayerId = currentPlayerId,
                legSnapshot = current,
                snapshot = snapshot(),
            )
        }

        // Ein noch offener Leg-Wechsel wird spaetestens jetzt vollzogen: der neue
        // Dart gehoert zum naechsten Leg und darf nicht auf den Endstand des
        // gewonnenen Legs treffen (raeumt zugleich die Undo-Historie ab).
        commitLegTransition()

        legDartHistory.add(dart)
        val result = applyDartCore(dart)
        // Defensiv: Wird der Dart wider Erwarten nicht angenommen (Aufnahme des
        // aktiven Spielers war bereits beendet), nicht in der Historie behalten.
        // Der leg-gewinnende Dart wird angenommen und bleibt daher erhalten.
        if (!result.accepted && legDartHistory.isNotEmpty()) {
            legDartHistory.removeAt(legDartHistory.size - 1)
        }
        return result
    }

    /**
     * Reine Kern-Verarbeitung genau eines Darts OHNE Historien-Aufzeichnung:
     * delegiert an die [LegEngine] des aktiven Spielers und schreibt Leg-/Set-/
     * Match-Zaehler bzw. den Spielerwechsel fort. Wird von [applyDart] (mit
     * vorheriger Historien-Aufnahme) und beim Zurueckspulen in [undoLastDart]
     * (Neu-Durchspielen der verbleibenden Darts) genutzt.
     */
    private fun applyDartCore(dart: Dart): MatchDartResult<S> {
        val throwerIndex = currentPlayerIndex
        val throwerId = playerIds[throwerIndex]
        val dartResult = legEngines[throwerIndex].applyDart(dart)
        val legSnapshot = dartResult.snapshot

        var setWon = false
        var matchWon = false
        var legWinnerId: Long? = null

        when {
            dartResult.legWon -> {
                // Klassischer Werfer-Sieg: der Werfer selbst nimmt das Leg.
                legWinnerId = throwerId
                val award = awardLeg(throwerIndex)
                setWon = award.setWon
                matchWon = award.matchWon
            }

            dartResult.legEnded -> {
                // Leg entschieden ohne Werfer-Sieg (rundenbasierte Modi): der
                // Gewinner ergibt sich aus dem Rangvergleich ueber alle Spieler.
                val winnerIndex = resolveLegScoreWinner()
                legWinnerId = playerIds[winnerIndex]
                val award = awardLeg(winnerIndex)
                setWon = award.setWon
                matchWon = award.matchWon
            }

            dartResult.turnEnded -> {
                // Regulaeres Aufnahme-Ende oder Bust: naechste Aufnahme + Spielerwechsel.
                // Eliminierte Spieler werden dabei uebersprungen (z.B. Killer).
                legEngines[throwerIndex].startNewTurn()
                currentPlayerIndex = nextActiveIndex(throwerIndex)
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
            legWinnerId = legWinnerId,
            setWon = setWon,
            matchWon = matchWon,
            matchWinnerId = matchWinnerId,
            nextPlayerId = currentPlayerId,
            legSnapshot = legSnapshot,
            snapshot = snapshot(),
        )
    }

    /**
     * Ermittelt den Gewinner eines Legs, das ohne Werfer-Sieg endet
     * (`legEnded`): hoechster [GameMode.legScore] ueber ALLE Spieler-Zustaende
     * (argmax). Bewertet wird index-parallel zu [playerIds] ueber die eigenen
     * [legEngines] - der Modus braucht dafuer KEINE Annahme ueber die
     * Reihenfolge der `opponents`-Liste.
     *
     * Gleichstand-Konvention: Es gewinnt deterministisch der ZUERST gelistete
     * Spieler (kleinster Index), weil nur ein echt groesserer Rangwert die
     * Fuehrung uebernimmt. Modi, die einen Gleichstand nicht so entscheiden
     * wollen, melden in diesem Fall schlicht kein `legEnded` (z.B. Sudden Death)
     * und lassen weiterspielen.
     */
    private fun resolveLegScoreWinner(): Int {
        var winnerIndex = 0
        var bestScore = mode.legScore(legEngines[0].state)
        for (i in 1 until playerCount) {
            val score = mode.legScore(legEngines[i].state)
            if (score > bestScore) {
                bestScore = score
                winnerIndex = i
            }
        }
        return winnerIndex
    }

    /**
     * Schreibt einen Leg-Gewinn fuer den Spieler an [winnerIndex] fort: Leg-
     * Zaehler, ggf. Set-/Match-Gewinn samt Startspieler-Rotation und das
     * Aufschieben des Leg-Wechsels. Gemeinsame Buchfuehrung BEIDER Leg-Ende-Wege
     * (Werfer-Sieg via `legWon` und Rangvergleich via `legEnded`) - der Gewinner
     * ist der einzige Unterschied, damit beide Pfade nicht auseinanderlaufen.
     */
    private fun awardLeg(winnerIndex: Int): LegAward {
        var setWon = false
        var matchWon = false
        legsWonInSet[winnerIndex]++
        if (legsWonInSet[winnerIndex] >= config.legsToWin) {
            setWon = true
            setsWon[winnerIndex]++
            for (i in legsWonInSet.indices) legsWonInSet[i] = 0
            if (setsWon[winnerIndex] >= config.setsToWin) {
                matchWon = true
                isMatchWon = true
                matchWinnerId = playerIds[winnerIndex]
                // Kein neues Leg/Set: Match ist entschieden. Historie und
                // LegEngines bleiben stehen, damit der Sieg-Dart per
                // undoLastDart zuruecknehmbar bleibt.
                pendingLegTransition = PendingLegTransition.MATCH_END
            } else {
                deferLegTransition(newSet = true)
            }
        } else {
            deferLegTransition(newSet = false)
        }
        return LegAward(setWon = setWon, matchWon = matchWon)
    }

    /**
     * Ergebnis von [awardLeg]: ob mit dem Leg zugleich ein Set bzw. das Match
     * entschieden wurde.
     */
    private class LegAward(val setWon: Boolean, val matchWon: Boolean)

    /**
     * Macht den zuletzt im AKTUELLEN Leg geworfenen Dart rueckgaengig -
     * unbegrenzt und ueber Aufnahme- sowie Spielerwechsel-Grenzen hinweg, aber
     * NICHT ueber vollzogene Leg-/Set-Grenzen (mit [commitLegTransition] wird die
     * Historie geleert). Jeder Aufruf nimmt genau einen Dart zurueck.
     *
     * Umsetzung als deterministisches Replay: Der letzte Dart wird aus
     * [legDartHistory] entfernt, alle [LegEngine]s werden frisch erzeugt, der
     * aktive Spieler auf den Leg-Startspieler zurueckgesetzt und die restliche
     * Historie ueber [applyDartCore] erneut durchgespielt. Das reproduziert
     * Aufnahme-Buendelung, Bust-Reverts und Spielerwechsel exakt (der Modus ist
     * pur). Leg-/Set-Zaehler und -Nummern bleiben unberuehrt.
     *
     * Sonderfall Sieg-Dart: Steht der Leg-Wechsel noch aus
     * ([pendingLegTransition] gesetzt, also unmittelbar nach dem leg- oder
     * matchgewinnenden Dart), wird zuerst der Zaehler-Stand zu Leg-Beginn
     * wiederhergestellt ([legBaseline]) und ein etwaiger Match-Gewinn
     * zurueckgenommen; danach laeuft dasselbe Replay wie sonst und die Aufnahme
     * des Werfers ist wieder offen. Ein Sieg-Dart kann im Replay nicht erneut
     * auftreten, weil genau er entfernt wurde.
     *
     * No-op (Rueckgabe `false`), wenn im laufenden Leg kein Dart mehr
     * zurueckzunehmen ist ([dartsThrownInCurrentLeg] == 0).
     */
    fun undoLastDart(): Boolean {
        // Sicherheitsnetz fuer kuenftige Pfade, in denen ein Match-Gewinn
        // endgueltig committet wird (z.B. ein spaeteres "Match-Ende bestaetigen"):
        // im aktuellen Zustandsmodell ist dieser Zweig unerreichbar, da isMatchWon
        // stets zusammen mit pendingLegTransition == MATCH_END gesetzt wird und
        // commitLegTransition bei MATCH_END bewusst frueh zurueckkehrt, ohne es
        // abzuraeumen (siehe dort) - einzig undoLastDart selbst loest beides
        // wieder auf (oben, im pendingLegTransition-Zweig).
        if (isMatchWon && pendingLegTransition == null) return false
        if (legDartHistory.isEmpty()) return false

        if (pendingLegTransition != null) {
            // Sieg-Dart zuruecknehmen: Zaehler/Nummern/Rotation auf den Stand zu
            // Leg-Beginn und Match wieder offen.
            restoreLegBaseline()
            pendingLegTransition = null
            isMatchWon = false
            matchWinnerId = null
        }

        legDartHistory.removeAt(legDartHistory.size - 1)
        for (i in legEngines.indices) {
            legEngines[i] = createLegEngine(i)
        }
        currentPlayerIndex = legStartIndex
        // Ueber eine Kopie iterieren: applyDartCore wuerde die Historie nur bei
        // einem Leg-/Match-Gewinn anfassen, der hier aber nie auftreten kann.
        for (dart in legDartHistory.toList()) {
            applyDartCore(dart)
        }
        return true
    }

    /**
     * Bereitet das naechste Leg vor, ohne es zu starten: Rotation des
     * Startspielers und Fortschreiben der Leg-/Set-Nummern passieren SOFORT
     * (Sieg-Anzeigen lesen diese Werte aus dem [snapshot]), der Reset der
     * [LegEngine]s und der Undo-Historie bleibt bis [commitLegTransition]
     * aufgeschoben.
     *
     * Bewusst OHNE Eliminierungs-Skip (anders als die Aufnahme-Rotation ueber
     * [nextActiveIndex]): Das neue Leg startet mit frischen [LegEngine]s, in denen
     * per Definition noch niemand eliminiert ist - ein Skip an der Leg-Grenze
     * waere also wirkungslos. Zudem wuerde er hier auf den NOCH nicht ersetzten
     * Engines des gerade beendeten Legs rechnen (der Reset ist aufgeschoben) und
     * damit den Startspieler des neuen Legs anhand veralteter Zustaende
     * verschieben.
     *
     * @param newSet True, wenn zugleich ein neues Set beginnt (Set-Nummer +1,
     *   Leg-Nummer zurueck auf 1); sonst nur Leg-Nummer +1 im selben Set.
     */
    private fun deferLegTransition(newSet: Boolean) {
        pendingLegTransition =
            if (newSet) PendingLegTransition.NEXT_SET else PendingLegTransition.NEXT_LEG
        legStartIndex = nextIndex(legStartIndex)
        currentPlayerIndex = legStartIndex
        if (newSet) {
            currentSetNumber++
            currentLegNumber = 1
        } else {
            currentLegNumber++
        }
    }

    /**
     * Vollzieht einen aufgeschobenen Leg-Wechsel: frische [LegEngine]s fuer alle
     * Spieler, geleerte Undo-Historie (das neue Leg startet ohne Undo-Tiefe) und
     * der Leg-Startspieler ist am Zug. Der Zaehler-Stand des neuen Legs wird als
     * [legBaseline] gesichert.
     *
     * Ruft der Aufrufer die Methode nicht selbst (z.B. beim Start des naechsten
     * Legs in der UI), holt [applyDart] den Vollzug beim naechsten Wurf nach.
     *
     * @return True, wenn ein Wechsel vollzogen wurde; `false`, wenn keiner
     *   aussteht oder das Match entschieden ist (dann folgt kein Leg mehr).
     */
    fun commitLegTransition(): Boolean {
        val pending = pendingLegTransition ?: return false
        if (pending == PendingLegTransition.MATCH_END) return false

        for (i in legEngines.indices) {
            legEngines[i] = createLegEngine(i)
        }
        // Undo-Historie gehoert zum abgeschlossenen Leg und darf nicht ins neue
        // Leg bluten (Undo spult nicht ueber vollzogene Leg-Grenzen zurueck).
        legDartHistory.clear()
        currentPlayerIndex = legStartIndex
        legBaseline = captureLegBaseline()
        pendingLegTransition = null
        return true
    }

    /** Sichert den aktuellen Zaehler-Stand als Leg-Ausgangspunkt. */
    private fun captureLegBaseline(): LegBaseline = LegBaseline(
        legsWonInSet = legsWonInSet.toList(),
        setsWon = setsWon.toList(),
        setNumber = currentSetNumber,
        legNumber = currentLegNumber,
        startIndex = legStartIndex,
    )

    /** Stellt den in [legBaseline] gesicherten Zaehler-Stand wieder her. */
    private fun restoreLegBaseline() {
        val baseline = legBaseline
        for (i in legsWonInSet.indices) legsWonInSet[i] = baseline.legsWonInSet[i]
        for (i in setsWon.indices) setsWon[i] = baseline.setsWon[i]
        currentSetNumber = baseline.setNumber
        currentLegNumber = baseline.legNumber
        legStartIndex = baseline.startIndex
    }

    private fun nextIndex(index: Int): Int = (index + 1) % playerCount

    /**
     * Naechster Spieler NACH [fromIndex], der noch wirft: reihum weiterzaehlen und
     * dabei alle vom Modus als eliminiert gemeldeten Spieler ueberspringen
     * ([GameMode.isEliminated], Default `false` -> Modi ohne Eliminierung
     * verhalten sich exakt wie bisher [nextIndex]).
     *
     * Der Werfer selbst ist der LETZTE Kandidat: bleibt er als Einziger uebrig
     * (alle anderen eliminiert), wirft er erneut. Er kann sich mit seinem Dart
     * auch selbst eliminiert haben - dann greift derselbe Skip wie fuer alle
     * anderen.
     *
     * Iterationsdeckel [playerCount]: mehr Kandidaten als Spieler gibt es nicht.
     * Sind wider Erwarten ALLE eliminiert (fachlich nicht erreichbar - der Modus
     * beendet das Leg spaetestens, wenn nur noch einer uebrig ist), faellt die
     * Rotation defensiv auf [nextIndex] zurueck, statt endlos zu drehen.
     *
     * Ein Skip greift erst zum Aufnahme-Ende ([DartResult.turnEnded]): Ein mitten
     * in der eigenen Aufnahme eliminierter Werfer wirft trotzdem seine restlichen
     * Darts zu Ende, denn [DartOutcome] kennt kein eigenes Aufnahme-Ende-Signal -
     * das entscheidet erst die [LegEngine] anhand der Dart-Anzahl.
     */
    private fun nextActiveIndex(fromIndex: Int): Int {
        var candidate = fromIndex
        repeat(playerCount) {
            candidate = nextIndex(candidate)
            if (!isEliminated(candidate)) return candidate
        }
        return nextIndex(fromIndex)
    }

    /**
     * Fragt den Modus, ob der Spieler an [index] nicht mehr wirft. Die
     * Gegner-Liste wird - wie beim Wurf-Provider in [createLegEngine] - LIVE aus
     * den [legEngines] gelesen (alle ANDEREN Spieler in aufsteigender
     * Index-Reihenfolge), damit Ableitungen ueber Spielergrenzen hinweg (z.B.
     * Killer-Leben) denselben Blick haben wie [GameMode.applyDart].
     */
    private fun isEliminated(index: Int): Boolean = mode.isEliminated(
        legEngines[index].state,
        legEngines.filterIndexed { i, _ -> i != index }.map { it.state },
    )
}
