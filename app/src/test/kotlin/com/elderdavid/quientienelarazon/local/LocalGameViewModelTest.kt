package com.elderdavid.quientienelarazon.local

import com.elderdavid.quientienelarazon.core.game.GameRules
import com.elderdavid.quientienelarazon.core.local.LocalStage
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
    fun playerCountStaysBetweenTwoAndFour() = runTest(dispatcher) {
        val vm = viewModel()
        vm.removePlayer(0)
        assertEquals(2, vm.state.value.names.size)
        repeat(5) { vm.addPlayer() }
        assertEquals(GameRules.MAX_PLAYERS, vm.state.value.names.size)
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
