package com.elderdavid.quientienelarazon.local

import com.elderdavid.quientienelarazon.core.economy.EconomyRules
import com.elderdavid.quientienelarazon.core.local.LocalStage
import com.elderdavid.quientienelarazon.core.local.Wildcard
import com.elderdavid.quientienelarazon.core.local.WildcardRules
import com.elderdavid.quientienelarazon.core.questions.QuestionBankLoader
import com.elderdavid.quientienelarazon.economy.FakeEconomy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalGameEconomyTest {
    private val dispatcher = StandardTestDispatcher()
    private val bank = QuestionBankLoader.fromResources()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private var keys = 0

    private fun TestScope.started(economy: FakeEconomy): LocalGameViewModel {
        val vm = LocalGameViewModel(
            loadBank = { bank },
            clock = { testScheduler.currentTime },
            newSeed = { 42L },
            economy = economy,
            newGameKey = { "partida-${++keys}" },
        )
        runCurrent()
        vm.setName(0, "Ana")
        vm.setName(1, "Luis")
        vm.start()
        runCurrent()
        return vm
    }

    private fun TestScope.playToEnd(vm: LocalGameViewModel) {
        var guard = 0
        while (vm.state.value.match!!.stage !is LocalStage.Finished && guard++ < 1_000) {
            when (val stage = vm.state.value.match!!.stage) {
                is LocalStage.Roulette -> vm.rouletteDone()
                is LocalStage.Handoff -> vm.ready()
                is LocalStage.Betting -> vm.bet(stage.allowedBets.minOrNull() ?: 0)
                is LocalStage.Answering -> vm.answer(if (stage.playerId == vm.state.value.match!!.players.first().id) 0 else 1)
                is LocalStage.Reveal, is LocalStage.RoundSummary, is LocalStage.TiebreakReveal -> vm.next()
                is LocalStage.Finished -> Unit
            }
            runCurrent()
        }
    }

    @Test
    fun matchPackChargesTheOwnerAndGivesEveryoneOneMore() = runTest(dispatcher) {
        val economy = FakeEconomy(coins = 100)
        val vm = started(economy)
        assertEquals(100, vm.state.value.coins)
        vm.buyMatchPack()
        runCurrent()
        val match = vm.state.value.match!!
        assertTrue(match.wildcards.values.all { it == WildcardRules.FREE_PER_PLAYER + 1 })
        assertEquals(100 - EconomyRules.MATCH_PACK_PRICE, vm.state.value.coins)
        // Solo uno por partida.
        vm.buyMatchPack()
        runCurrent()
        assertEquals(100 - EconomyRules.MATCH_PACK_PRICE, vm.state.value.coins)
    }

    @Test
    fun withoutCoinsThePackIsNotApplied() = runTest(dispatcher) {
        val vm = started(FakeEconomy(coins = 10))
        vm.buyMatchPack()
        runCurrent()
        assertFalse(vm.state.value.match!!.matchPackUsed)
        assertNotNull(vm.state.value.notice)
    }

    @Test
    fun wildcardsWorkWithoutAccount() = runTest(dispatcher) {
        val vm = started(FakeEconomy(online = false))
        vm.rouletteDone()
        vm.ready()
        val before = vm.state.value.match!!.stage as LocalStage.Answering
        vm.useWildcard(Wildcard.EXTRA_TIME)
        val after = vm.state.value.match!!.stage as LocalStage.Answering
        assertEquals(before.deadline + WildcardRules.EXTRA_TIME_MILLIS, after.deadline)
        assertNull(vm.state.value.coins)
    }

    @Test
    fun finishedGameGivesXpToThePhoneAccountOnce() = runTest(dispatcher) {
        val economy = FakeEconomy()
        val vm = started(economy)
        playToEnd(vm)
        assertEquals(1, economy.recorded.size)
        assertEquals("partida-1", economy.recorded.single().first)
        assertEquals(EconomyRules.LOCAL_GAME_XP, vm.state.value.reward?.xp)
        vm.rematch()
        runCurrent()
        assertNull(vm.state.value.reward)
        playToEnd(vm)
        assertEquals(listOf("partida-1", "partida-2"), economy.recorded.map { it.first })
    }
}
