package com.elderdavid.quientienelarazon.core.economy

import com.elderdavid.quientienelarazon.core.game.GameState
import com.elderdavid.quientienelarazon.core.game.Phase
import com.elderdavid.quientienelarazon.core.profile.Levels
import com.elderdavid.quientienelarazon.core.questions.ContentMode

/**
 * Reglas de la economía del juego: monedas, premios por nivel, logros y racha diaria.
 *
 * El saldo y lo que cada jugador tiene los guarda y decide el servidor
 * (supabase/migrations/20261012050000_economia.sql). Estas constantes son las mismas que usa el
 * servidor; EconomyCatalogTest comprueba que el catálogo de la migración coincide con este.
 * Ningún premio bloquea contenido: todas las preguntas y modos son gratis.
 */
object EconomyRules {
    /** Bono al llegar a un nivel: 50 + 10 por nivel (nivel 5 = 100). */
    const val LEVEL_BONUS_BASE = 50
    const val LEVEL_BONUS_PER_LEVEL = 10

    const val VIDEO_REWARD = 20
    const val VIDEOS_PER_DAY = 5

    /** XP para el dueño de la cuenta por cada partida local terminada en su teléfono. */
    const val LOCAL_GAME_XP = 10
    const val LOCAL_GAMES_XP_PER_DAY = 5

    /** Monedas por terminar una partida (local u online) y por ganar una online. */
    const val GAME_COINS = 5
    const val GAMES_COINS_PER_DAY = 10
    const val ONLINE_WIN_COINS = 10
    const val ONLINE_WINS_COINS_PER_DAY = 10

    /** Paquete de partida: 1 comodín extra para cada jugador de la partida local. */
    const val MATCH_PACK_PRICE = 60

    /** Premio de la racha diaria por día seguido (1 a 7). Desde el día 7 se repite el último. */
    val STREAK_REWARDS = listOf(10, 15, 20, 30, 40, 60, 100)

    /** Los días se cuentan en hora de Colombia, igual que en el servidor. */
    const val TIME_ZONE = "America/Bogota"

    fun levelUpBonus(level: Int): Int {
        require(level >= 2) { "El nivel 1 no tiene bono" }
        return LEVEL_BONUS_BASE + LEVEL_BONUS_PER_LEVEL * level
    }

    /** Monedas por los niveles ganados al pasar de [fromXp] a [toXp]. */
    fun levelUpCoins(fromXp: Int, toXp: Int): Int =
        ((Levels.levelFor(fromXp) + 1)..Levels.levelFor(toXp)).sumOf(::levelUpBonus)

    /** Premio del día [streakDay] de la racha (1 = primer día). */
    fun streakReward(streakDay: Int): Int {
        require(streakDay >= 1) { "La racha empieza en el día 1" }
        return STREAK_REWARDS[minOf(streakDay, STREAK_REWARDS.size) - 1]
    }
}

/** Progreso dentro del nivel actual, para la barra del perfil. */
data class LevelProgress(val level: Int, val xpIntoLevel: Int, val xpForNextLevel: Int) {
    val fraction: Float get() = xpIntoLevel.toFloat() / xpForNextLevel

    companion object {
        /** Misma curva que [Levels.levelFor]: cada nivel cuesta 100 XP más que el anterior. */
        fun of(xp: Int): LevelProgress {
            var level = 1
            var needed = 100
            var remaining = xp.coerceAtLeast(0)
            while (remaining >= needed) {
                remaining -= needed
                level++
                needed += 100
            }
            return LevelProgress(level, remaining, needed)
        }
    }
}

enum class CosmeticKind(val label: String) {
    FRAME("Marco de avatar"),
    TITLE("Título"),
    THEME("Tema de color"),
    ROULETTE_SOUND("Sonido de la ruleta"),
    VICTORY("Animación de victoria"),
}

/**
 * Objeto decorativo. Se consigue al llegar a [unlockLevel] o comprándolo por [price] monedas;
 * nunca las dos cosas. Los ids son estables: el servidor los guarda.
 */
data class Cosmetic(
    val id: String,
    val kind: CosmeticKind,
    val name: String,
    val unlockLevel: Int? = null,
    val price: Int? = null,
) {
    init {
        require((unlockLevel == null) != (price == null)) { "$id: se gana por nivel o se compra, no ambas" }
    }
}

object Cosmetics {
    val all: List<Cosmetic> = listOf(
        // Premios por nivel.
        Cosmetic("frame_bronze", CosmeticKind.FRAME, "Marco Bronce", unlockLevel = 2),
        Cosmetic("sound_retro", CosmeticKind.ROULETTE_SOUND, "Ruleta retro", unlockLevel = 3),
        Cosmetic("theme_neon_caribe", CosmeticKind.THEME, "Neón Caribe", unlockLevel = 5),
        Cosmetic("title_sabelotodo", CosmeticKind.TITLE, "Sabelotodo", unlockLevel = 7),
        Cosmetic("victory_confetti", CosmeticKind.VICTORY, "Lluvia de confeti", unlockLevel = 10),
        Cosmetic("frame_costa", CosmeticKind.FRAME, "Marco Costa", unlockLevel = 15),
        Cosmetic("frame_paisa", CosmeticKind.FRAME, "Marco Paisa", unlockLevel = 20),
        Cosmetic("frame_llanero", CosmeticKind.FRAME, "Marco Llanero", unlockLevel = 25),
        Cosmetic("frame_pacifico", CosmeticKind.FRAME, "Marco Pacífico", unlockLevel = 30),
        // Tienda.
        Cosmetic("title_el_que_sabe", CosmeticKind.TITLE, "El que sabe", price = 150),
        Cosmetic("title_profe", CosmeticKind.TITLE, "Profe", price = 150),
        Cosmetic("sound_campana", CosmeticKind.ROULETTE_SOUND, "Campana", price = 200),
        Cosmetic("frame_oro", CosmeticKind.FRAME, "Marco Oro", price = 300),
        Cosmetic("theme_atardecer", CosmeticKind.THEME, "Atardecer", price = 400),
        Cosmetic("victory_fireworks", CosmeticKind.VICTORY, "Fuegos artificiales", price = 600),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String?): Cosmetic? = id?.let(byId::get)

    /** Premios que da cada nivel, en orden. */
    val levelRewards: List<Cosmetic> = all.filter { it.unlockLevel != null }.sortedBy { it.unlockLevel }

    val shop: List<Cosmetic> = all.filter { it.price != null }.sortedBy { it.price }

    /** Lo próximo que se gana por nivel a partir de [level], o null si ya se tiene todo. */
    fun nextLevelReward(level: Int): Cosmetic? = levelRewards.firstOrNull { it.unlockLevel!! > level }
}

/** Logro: se desbloquea una sola vez y da [coins] monedas. Lo decide el servidor. */
data class Achievement(val id: String, val name: String, val description: String, val coins: Int)

object Achievements {
    val all: List<Achievement> = listOf(
        Achievement("first_local", "Primera partida", "Termina una partida en este teléfono.", 20),
        Achievement("local_25", "Fiestero", "Termina 25 partidas en este teléfono.", 100),
        Achievement("regional_10", "Experto en De aquí somos", "Termina 10 partidas de «De aquí somos».", 100),
        Achievement("perfect_round", "Ronda perfecta", "Alguien acierta todas las preguntas de una ronda.", 50),
        Achievement("first_online_win", "Primera victoria online", "Gana una partida online.", 50),
        Achievement("online_wins_10", "10 victorias", "Gana 10 partidas online.", 150),
        Achievement("level_10", "Nivel 10", "Llega al nivel 10.", 100),
        Achievement("streak_7", "Semana completa", "Reclama la racha diaria 7 días seguidos.", 100),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): Achievement? = byId[id]
}

/**
 * Lo que la app cuenta al servidor de una partida local terminada, para el XP y los logros.
 * Las partidas locales no pasan por el servidor, así que estos datos los aporta el teléfono;
 * por eso el servidor limita cuánto pueden dar al día.
 */
data class LocalGameReport(val regional: Boolean, val perfectRound: Boolean) {
    companion object {
        fun of(state: GameState): LocalGameReport {
            require(state.phase == Phase.FINISHED) { "La partida no ha terminado" }
            // Ronda perfecta: alguien acierta todas las preguntas objetivas de una ronda (al menos 2).
            val perfect = state.results
                .filter { it.scored && it.voteCounts.isEmpty() }
                .groupBy { it.round }
                .values
                .filter { it.size >= 2 }
                .any { round -> state.players.any { p -> round.all { r -> r.outcomes.any { it.playerId == p.id && it.won == true } } } }
            return LocalGameReport(regional = state.config.mode is ContentMode.Regional, perfectRound = perfect)
        }
    }
}
