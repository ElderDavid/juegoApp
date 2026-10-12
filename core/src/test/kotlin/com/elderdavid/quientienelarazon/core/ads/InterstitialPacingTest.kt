package com.elderdavid.quientienelarazon.core.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialPacingTest {
    private val minute = 60_000L

    @Test
    fun firstGameOfTheSessionNeverShowsAnAd() {
        val p = InterstitialPacing().gameFinished("a")
        assertFalse(p.canShow(0))
    }

    @Test
    fun secondGameCanShowTheFirstAd() {
        val p = InterstitialPacing().gameFinished("a").gameFinished("b")
        assertTrue(p.canShow(0))
    }

    @Test
    fun sameGameIsCountedOnce() {
        val p = InterstitialPacing().gameFinished("a").gameFinished("a").gameFinished("a")
        assertEquals(1, p.gamesSinceLastAd)
        assertFalse(p.canShow(0))
    }

    @Test
    fun afterAnAdTwoMoreGamesAndThreeMinutesAreNeeded() {
        var p = InterstitialPacing().gameFinished("a").gameFinished("b").shown(10 * minute)
        assertEquals(0, p.gamesSinceLastAd)
        p = p.gameFinished("c").gameFinished("d")
        // Dos partidas muy rápidas: aún no han pasado tres minutos.
        assertFalse(p.canShow(10 * minute + 2 * minute))
        assertTrue(p.canShow(10 * minute + 3 * minute))
    }

    @Test
    fun timeAloneIsNotEnough() {
        val p = InterstitialPacing().gameFinished("a").gameFinished("b").shown(0).gameFinished("c")
        assertFalse(p.canShow(60 * minute))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroGamesBetween() {
        InterstitialPacing(minGamesBetween = 0)
    }
}
