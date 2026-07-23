package com.mechanicel.tomsdarts.ui.game

/**
 * Test-Hilfe: entpackt die X01-Restpunktzahl aus [PlayerScoreUi.board].
 *
 * Nach dem Board-Refactor (das Feld `remaining` wich dem modus-agnostischen
 * [PlayerBoardUi]) haelt diese Extension die bestehenden `.remaining`-Assertions
 * unveraendert lauffaehig. Rein mechanische Adaption ohne Verhaltensaenderung;
 * liegt im selben Test-Package und ist damit ohne Import verfuegbar.
 */
val PlayerScoreUi.remaining: Int
    get() = (board as PlayerBoardUi.X01).remaining
