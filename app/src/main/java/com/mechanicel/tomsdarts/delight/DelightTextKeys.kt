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
 * dass jeder Schluessel in [ALL] einen eigenen Text hat).
 */
object DelightTextKeys {

    /** 180 - drei Triple 20. */
    const val ONE_EIGHTY: String = "delight_180"

    /** Waschmaschine - alle drei Darts in {20, 5, 1}, nicht alle auf 20 (ADR-0006). */
    const val WASHING_MACHINE: String = "delight_washing_machine"

    /** Rentnerdreieck - alle drei Darts in {19, 7, 3} (ADR-0006). */
    const val RENTNERDREIECK: String = "delight_rentnerdreieck"

    /** Allgemeine Feier ohne eigenes Motiv (auch Fallback fuer unbekannte Schluessel). */
    const val GENERIC: String = "delight_generic"

    /** Alle bekannten Schluessel. */
    val ALL: List<String> = listOf(ONE_EIGHTY, WASHING_MACHINE, RENTNERDREIECK, GENERIC)
}
