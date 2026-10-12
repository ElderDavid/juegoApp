package com.elderdavid.quientienelarazon.core.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** El catálogo del servidor (migración de economía) debe coincidir con el de la app. */
class EconomyCatalogTest {
    private val sql: String = listOf(File("../supabase/migrations"), File("supabase/migrations"))
        .first { it.isDirectory }
        .listFiles { f -> f.name.endsWith("_economia.sql") }!!
        .single()
        .readText()

    @Test
    fun cosmeticsMatchTheServer() {
        val row = Regex("""\('(\w+)', '(\w+)', '([^']+)', (\d+|null), (\d+|null)\)""")
        val server = row.findAll(sql).map { m ->
            val (id, kind, name, level, price) = m.destructured
            Cosmetic(id, CosmeticKind.valueOf(kind), name, level.toIntOrNull(), price.toIntOrNull())
        }.toList()
        assertEquals(Cosmetics.all.sortedBy { it.id }, server.sortedBy { it.id })
    }

    @Test
    fun achievementsMatchTheServer() {
        val row = Regex("""\('(\w+)', '([^']+)', (\d+)\)""")
        val server = row.findAll(sql).associate { m -> m.groupValues[1] to m.groupValues[3].toInt() }
            // El mismo formato lo tienen los productos de Google Play: se excluyen.
            .filterKeys { id -> !id.startsWith("coins_") && id != "remove_ads" }
        assertEquals(Achievements.all.associate { it.id to it.coins }, server)
    }

    @Test
    fun serverUsesTheSameNumbers() {
        val e = EconomyRules
        assertTrue(sql.contains("array[${e.STREAK_REWARDS.joinToString(", ")}]"))
        assertTrue(sql.contains("${e.LEVEL_BONUS_BASE} + ${e.LEVEL_BONUS_PER_LEVEL} * lvl"))
        assertTrue(sql.contains("_move_coins(uid, -${e.MATCH_PACK_PRICE}, 'match_pack'"))
        assertTrue(sql.contains("_move_coins(p_user, ${e.VIDEO_REWARD}, 'video'"))
        assertTrue(sql.contains("and credited) < ${e.VIDEOS_PER_DAY}"))
        assertTrue(sql.contains("gained_xp := ${e.LOCAL_GAME_XP}"))
        assertTrue(sql.contains("and xp > 0) < ${e.LOCAL_GAMES_XP_PER_DAY}"))
        assertTrue(sql.contains("_daily_capped(uid, 'game', ${e.GAME_COINS}, 1, ${e.GAMES_COINS_PER_DAY})"))
        assertTrue(sql.contains("'online_win', ${e.ONLINE_WIN_COINS}, new.wins - old.wins, ${e.ONLINE_WINS_COINS_PER_DAY})"))
    }
}
