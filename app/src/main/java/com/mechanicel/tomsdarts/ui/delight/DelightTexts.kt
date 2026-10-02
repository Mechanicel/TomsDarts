package com.mechanicel.tomsdarts.ui.delight

import androidx.annotation.StringRes
import com.mechanicel.tomsdarts.R
import com.mechanicel.tomsdarts.delight.DelightTextKeys

/**
 * String-Ressourcen einer Feier: Titel und optionaler Untertitel.
 *
 * @param title Titel der Feier (z.B. "180!").
 * @param subtitle Untertitel oder `null`, wenn die Feier keinen hat.
 */
data class DelightTextRes(
    @param:StringRes val title: Int,
    @param:StringRes val subtitle: Int?,
)

/**
 * Bildet einen [DelightTextKeys]-Schluessel auf seine String-Ressourcen ab;
 * `null` bei unbekanntem Schluessel. Die Zuordnung lebt bewusst nur hier in der
 * UI (ADR-0038: das Delight-Paket bleibt frei von Android-Ressourcen).
 */
internal fun delightTextResOrNull(textKey: String): DelightTextRes? = when (textKey) {
    DelightTextKeys.ONE_EIGHTY ->
        DelightTextRes(R.string.delight_180_title, R.string.delight_180_subtitle)
    DelightTextKeys.WASHING_MACHINE ->
        DelightTextRes(
            R.string.delight_washing_machine_title,
            R.string.delight_washing_machine_subtitle,
        )
    DelightTextKeys.RENTNERDREIECK ->
        DelightTextRes(
            R.string.delight_rentnerdreieck_title,
            R.string.delight_rentnerdreieck_subtitle,
        )
    DelightTextKeys.MADHOUSE ->
        DelightTextRes(R.string.delight_madhouse_title, R.string.delight_madhouse_subtitle)
    DelightTextKeys.BULL_FINISH ->
        DelightTextRes(R.string.delight_bull_title, R.string.delight_bull_subtitle)
    DelightTextKeys.TON ->
        DelightTextRes(R.string.delight_ton_title, R.string.delight_ton_subtitle)
    DelightTextKeys.GENERIC -> GENERIC_TEXT
    else -> null
}

/**
 * Wie [delightTextResOrNull], faellt bei unbekanntem Schluessel aber auf den
 * allgemeinen Text ([DelightTextKeys.GENERIC]) zurueck - eine Feier zeigt nie
 * einen leeren oder rohen Schluessel.
 */
fun delightTextRes(textKey: String): DelightTextRes = delightTextResOrNull(textKey) ?: GENERIC_TEXT

private val GENERIC_TEXT = DelightTextRes(R.string.delight_generic_title, subtitle = null)
