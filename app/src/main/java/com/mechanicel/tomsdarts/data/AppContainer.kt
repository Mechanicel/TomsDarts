package com.mechanicel.tomsdarts.data

import android.content.Context
import com.mechanicel.tomsdarts.data.repository.MatchRepository
import com.mechanicel.tomsdarts.data.repository.PlayerRepository

/**
 * Minimaler, manueller DI-Container (bewusst ohne DI-Framework). Baut die
 * lokale [TomsDartsDatabase] und stellt die Repositories bereit.
 *
 * Einziger Einstiegspunkt fuer die spaetere UI-/ViewModel-Schicht. Bleibt rein
 * lokal (offline-first, keine Cloud/Backend).
 */
class AppContainer(context: Context) {

    private val database: TomsDartsDatabase = TomsDartsDatabase.getInstance(context)

    /** Repository fuer Spieler. */
    val playerRepository: PlayerRepository by lazy {
        PlayerRepository(database.playerDao())
    }

    /** Repository fuer Matches und deren Legs/Turns/Throws/Teilnehmer. */
    val matchRepository: MatchRepository by lazy {
        MatchRepository(
            matchDao = database.matchDao(),
            legDao = database.legDao(),
            turnDao = database.turnDao(),
            throwDao = database.throwDao(),
            matchPlayerDao = database.matchPlayerDao(),
        )
    }
}
