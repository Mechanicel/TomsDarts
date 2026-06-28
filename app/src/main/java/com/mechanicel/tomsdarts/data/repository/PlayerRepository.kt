package com.mechanicel.tomsdarts.data.repository

import com.mechanicel.tomsdarts.data.dao.PlayerDao
import com.mechanicel.tomsdarts.data.entity.Player
import kotlinx.coroutines.flow.Flow

/**
 * Repository-Schicht ueber [PlayerDao]. Kapselt den Datenzugriff fuer Spieler
 * und bleibt rein lokal (offline-first, keine Cloud/Backend).
 *
 * Die suspend-DAO-Methoden laufen bereits auf Rooms eigenem Executor, daher
 * wird hier bewusst kein zusaetzliches `withContext(Dispatchers.IO)` verwendet.
 */
class PlayerRepository(private val dao: PlayerDao) {

    /** Beobachtet alle Spieler (nach Name sortiert) als reaktiven Stream. */
    fun observePlayers(): Flow<List<Player>> = dao.observeAll()

    /** Liefert den Spieler mit [id] oder `null`, falls nicht vorhanden. */
    suspend fun getPlayer(id: Long): Player? = dao.getById(id)

    /**
     * Legt einen neuen Spieler mit [name] an und gibt dessen neue ID zurueck.
     * Der Erstellungszeitpunkt wird auf die aktuelle Systemzeit gesetzt.
     */
    suspend fun addPlayer(name: String): Long =
        dao.insert(Player(name = name, createdAt = System.currentTimeMillis()))

    /** Aktualisiert einen bestehenden Spieler. */
    suspend fun updatePlayer(player: Player) = dao.update(player)

    /** Entfernt den uebergebenen Spieler. */
    suspend fun deletePlayer(player: Player) = dao.delete(player)
}
