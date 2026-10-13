package com.elderdavid.quientienelarazon.local

import com.elderdavid.quientienelarazon.core.game.GameLength
import com.elderdavid.quientienelarazon.core.game.GameRules
import com.elderdavid.quientienelarazon.core.game.GameState
import com.elderdavid.quientienelarazon.core.local.LocalStage
import com.elderdavid.quientienelarazon.core.questions.Audience
import com.elderdavid.quientienelarazon.core.questions.ContentMode
import com.elderdavid.quientienelarazon.core.questions.QuestionBankLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalGameViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val bank = QuestionBankLoader.fromResources()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): LocalGameViewModel {
        val vm = LocalGameViewModel(loadBank = { bank }, clock = { testScheduler.currentTime }, newSeed = { 42L })
        runCurrent()
        return vm
    }

    @Test
    fun loadsBankAndExposesRegions() = runTest(dispatcher) {
        val vm = viewModel()
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(listOf("la-guajira", "magdalena", "cesar", "atlantico", "antioquia", "bogota").sorted(), s.regions.map { it.id }.sorted())
    }

    @Test
    fun cannotStartWithInvalidNames() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setName(0, "Ana")
        vm.setName(1, "ana")
        assertFalse(vm.state.value.canStart)
        vm.start()
        assertNull(vm.state.value.match)
        vm.setName(1, "Luis")
        assertTrue(vm.state.value.canStart)
    }

    @Test
    fun chosenLengthIsUsedForTheMatch() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(GameLength.STANDARD, vm.state.value.length)
        vm.setName(0, "Ana")
        vm.setName(1, "Luis")
        vm.setLength(GameLength.SHORT)
        vm.start()
        val match = vm.state.value.match!!
        assertEquals(GameLength.SHORT, match.state.config.length)
        assertEquals(8, match.state.plan.questions.size)
    }

    @Test
    fun playerCountStaysBetweenTwoAndFour() = runTest(dispatcher) {
        val vm = viewModel()
        vm.removePlayer(0)
        assertEquals(2, vm.state.value.names.size)
        repeat(5) { vm.addPlayer() }
        assertEquals(GameRules.MAX_PLAYERS, vm.state.value.names.size)
    }

    @Test
    fun kidsModeUsesKidsQuestionsAndLongerTimer() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setName(0, "Ana")
        vm.setName(1, "Luis")
        vm.setMode(ContentMode.Kids)
        vm.start()
        val match = vm.state.value.match!!
        assertTrue(match.state.config.kids)
        assertTrue(match.state.plan.questions.all { bank.question(it.questionId).audience == Audience.KIDS })
        vm.rouletteDone()
        vm.ready()
        advanceTimeBy(GameRules.QUESTION_TIME_MILLIS + 1)
        runCurrent()
        // Con el tiempo normal ya se habría agotado; en el modo niños sigue respondiendo.
        assertTrue(vm.state.value.match!!.stage is LocalStage.Answering)
        advanceTimeBy(GameRules.KIDS_QUESTION_TIME_MILLIS - GameRules.QUESTION_TIME_MILLIS)
        runCurrent()
        assertTrue(vm.state.value.match!!.stage is LocalStage.Handoff)
    }

    @Test
    fun turnTimesOutAutomatically() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setName(0, "Ana")
        vm.setName(1, "Luis")
        vm.setMode(ContentMode.Regional("cesar"))
        vm.start()
        vm.rouletteDone()
        vm.ready()
        assertTrue(vm.state.value.match!!.stage is LocalStage.Answering)
        advanceTimeBy(GameRules.QUESTION_TIME_MILLIS - 1)
        runCurrent()
        assertTrue(vm.state.value.match!!.stage is LocalStage.Answering)
        advanceTimeBy(2)
        runCurrent()
        val match = vm.state.value.match!!
        assertTrue(match.stage is LocalStage.Handoff)
        assertNull(match.state.answers.values.single().choice)
    }

    /** Juega hasta el final: el primer jugador elige siempre la opción A y los demás la B. */
    private fun TestScope.playToEnd(vm: LocalGameViewModel) {
        var guard = 0
        while (vm.state.value.match!!.stage !is LocalStage.Finished && guard++ < 1_000) {
            when (val stage = vm.state.value.match!!.stage) {
                is LocalStage.Roulette -> vm.rouletteDone()
                is LocalStage.Handoff -> vm.ready()
                is LocalStage.Betting -> vm.bet(stage.allowedBets.minOrNull() ?: 0)
                // Respuestas distintas para que la partida no quede empatada indefinidamente.
                is LocalStage.Answering -> vm.answer(if (stage.playerId == vm.state.value.match!!.players.first().id) 0 else 1)
                is LocalStage.Reveal, is LocalStage.RoundSummary, is LocalStage.TiebreakReveal -> vm.next()
                is LocalStage.Finished -> Unit
            }
            runCurrent()
        }
    }

    @Test
    fun finishedGamesAreRecordedOnceIncludingRematch() = runTest(dispatcher) {
        val recorded = mutableListOf<GameState>()
        val vm = LocalGameViewModel(
            loadBank = { bank },
            clock = { testScheduler.currentTime },
            newSeed = { 42L },
            recordFinished = { recorded += it },
        )
        runCurrent()
        vm.setName(0, "Ana")
        vm.setName(1, "Luis")
        vm.start()
        playToEnd(vm)
        assertTrue(vm.state.value.match!!.stage is LocalStage.Finished)
        assertEquals(1, recorded.size)
        // Acciones repetidas en la pantalla final no vuelven a guardar la partida.
        vm.next()
        vm.answer(0)
        runCurrent()
        assertEquals(1, recorded.size)

        vm.rematch()
        playToEnd(vm)
        assertEquals(2, recorded.size)
        assertEquals(listOf("Ana", "Luis"), recorded.last().players.map { it.name })
    }

    @Test
    fun failingToSaveHistoryDoesNotBreakTheGame() = runTest(dispatcher) {
        val vm = LocalGameViewModel(
            loadBank = { bank },
            clock = { testScheduler.currentTime },
            newSeed = { 42L },
            recordFinished = { throw java.io.IOException("disco lleno") },
        )
        runCurrent()
        vm.setName(0, "Ana")
        vm.setName(1, "Luis")
        vm.start()
        playToEnd(vm)
        assertTrue(vm.state.value.match!!.stage is LocalStage.Finished)
    }

    @Test
    fun answeringCancelsTimerAndQuitReturnsToSetup() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setName(0, "Ana")
        vm.setName(1, "Luis")
        vm.start()
        vm.rouletteDone()
        vm.ready()
        advanceTimeBy(1_000)
        vm.answer(0)
        advanceTimeBy(GameRules.QUESTION_TIME_MILLIS * 2)
        runCurrent()
        // Solo la respuesta dada: el temporizador anterior no registró un tiempo agotado extra.
        assertEquals(1, vm.state.value.match!!.state.answers.size)
        vm.quit()
        assertNull(vm.state.value.match)
        assertEquals(listOf("Ana", "Luis"), vm.state.value.names)
    }
}
