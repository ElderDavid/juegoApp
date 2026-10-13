package com.elderdavid.quientienelarazon.core.economy

import com.elderdavid.quientienelarazon.core.profile.Levels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EconomyTest {
    @Test
    fun levelBonusMatchesTheProposal() {
        assertEquals(70, EconomyRules.levelUpBonus(2))
        assertEquals(100, EconomyRules.levelUpBonus(5))
    }

    @Test
    fun levelUpCoinsCountsEveryLevelGained() {
        assertEquals(0, EconomyRules.levelUpCoins(0, 99))
        assertEquals(70, EconomyRules.levelUpCoins(0, 100))
        // De nivel 1 a nivel 3 (300 XP) se ganan los bonos de 2 y 3.
        assertEquals(70 + 80, EconomyRules.levelUpCoins(50, 300))
        assertEquals(0, EconomyRules.levelUpCoins(300, 350))
    }

    @Test
    fun levelProgressFollowsTheLevelCurve() {
        for (xp in listOf(0, 50, 99, 100, 299, 300, 1_234, 10_000)) {
            assertEquals(Levels.levelFor(xp), LevelProgress.of(xp).level)
        }
        assertEquals(LevelProgress(2, 50, 200), LevelProgress.of(150))
    }

    @Test
    fun streakRewardsGrowAndThenStay() {
        assertEquals(10, EconomyRules.streakReward(1))
        assertEquals(100, EconomyRules.streakReward(7))
        assertEquals(100, EconomyRules.streakReward(30))
    }

    @Test
    fun cosmeticsAreEitherEarnedOrBoughtWithUniqueIds() {
        assertEquals(Cosmetics.all.size, Cosmetics.all.map { it.id }.toSet().size)
        assertTrue(Cosmetics.levelRewards.zipWithNext().all { (a, b) -> a.unlockLevel!! < b.unlockLevel!! })
        assertEquals("frame_bronze", Cosmetics.nextLevelReward(1)?.id)
        assertEquals("theme_neon_caribe", Cosmetics.nextLevelReward(4)?.id)
        assertNull(Cosmetics.nextLevelReward(30))
    }

    @Test
    fun achievementIdsAreUnique() {
        assertEquals(Achievements.all.size, Achievements.all.map { it.id }.toSet().size)
    }
}
