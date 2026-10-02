package com.mechanicel.tomsdarts.delight

/**
 * Stabile Text-Schluessel fuer [DelightPresentation.textKey] (ADR-0038/ADR-0039).
 *
 * Einzige Definitionsstelle: Produkt-Trigger in [ProductDelightTriggers.ALL]
 * referenzieren diese Konstanten, die UI bildet sie auf String-Ressourcen ab
 * (`ui.delight.delightTextRes`). So kann ein Schluessel weder doppelt noch
 * abweichend geschrieben werden. Unbekannte Schluessel zeigt die UI mit dem
 * Text von [GENERIC] an.
 *
 * Ein neuer Schluessel braucht: eine Konstante hier, einen Eintrag in [ALL] und
 * den passenden Zweig im UI-Mapping samt String-Ressourcen (ein Test prueft,
 * dass jeder Schluessel in [ALL] einen eigenen Text hat). Das vollstaendige
 * Rezept fuer einen neuen Trigger steht in ADR-0042.
 */
object DelightTextKeys {

    /** 180 - drei Triple 20. */
    const val ONE_EIGHTY: String = "delight_180"

    /** Waschmaschine - alle drei Darts in {20, 5, 1}, mind. zwei verschiedene Segmente (ADR-0006, ADR-0042). */
    const val WASHING_MACHINE: String = "delight_washing_machine"

    /** Rentnerdreieck - alle drei Darts in {19, 7, 3}, mind. zwei verschiedene Segmente (ADR-0041, ADR-0042). */
    const val RENTNERDREIECK: String = "delight_rentnerdreieck"

    /** Madhouse - Checkout mit dem letzten Dart auf Doppel 1 (ADR-0042). */
    const val MADHOUSE: String = "delight_madhouse"

    /** Bull-Finish - Checkout mit dem letzten Dart auf Bullseye/Doppel-Bull (ADR-0042). */
    const val BULL_FINISH: String = "delight_bull"

    /** Ton - X01-Aufnahme mit mindestens 100 Punkten (ADR-0042). */
    const val TON: String = "delight_ton"

    /** Allgemeine Feier ohne eigenes Motiv (auch Fallback fuer unbekannte Schluessel). */
    const val GENERIC: String = "delight_generic"

    /** Alle bekannten Schluessel. */
    val ALL: List<String> = listOf(
        ONE_EIGHTY,
        WASHING_MACHINE,
        RENTNERDREIECK,
        MADHOUSE,
        BULL_FINISH,
        TON,
        GENERIC,
    )
}
