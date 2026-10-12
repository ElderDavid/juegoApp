package com.elderdavid.quientienelarazon.online

import com.elderdavid.quientienelarazon.core.game.GameLength
import com.elderdavid.quientienelarazon.core.questions.QuestionBank
import com.elderdavid.quientienelarazon.core.questions.Region
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

private fun player(id: String, seat: Int, score: Int = 0) =
    RoomPlayer(userId = id, seat = seat, name = "J$seat", avatarId = 0, score = score, connected = true, left = false)

private fun view(
    status: String = "waiting",
    phase: String? = null,
    index: Int = 0,
    withQuestion: Boolean = false,
    remaining: Long? = null,
) = RoomView(
    room = RoomInfo(
        id = "room-1", code = "ABC234", visibility = "private", mode = "general", status = status, phase = phase,
        hostId = "me", questionIndex = index, phaseTotalMs = remaining?.let { 8000 }, phaseRemainingMs = remaining,
    ),
    me = "me",
    players = listOf(player("me", 0), player("other", 1)),
    current = if (phase == null) null else CurrentQuestion(
        index = index, isTiebreak = false, round = 1, categoryId = "ciencia",
        question = if (withQuestion) QuestionText("¿?", listOf("A", "B", "C"), "OBJECTIVE", "MULTIPLE_CHOICE", false) else null,
    ),
)

private class FakeOnline : OnlineRepository {
    var current: RoomView? = null
    var next: OnlineResult<RoomView> = OnlineResult.Ok(view())
    var stateResult: OnlineResult<RoomView> = OnlineResult.Ok(view())
    val answers = mutableListOf<Triple<Int, Int, Long>>()
    var leaves = 0
    var stateCalls = 0
    var joinedCode: String? = null
    val requestedLengths = mutableListOf<GameLength>()

    override suspend fun ensureSignedIn(): OnlineResult<Unit> = OnlineResult.Ok(Unit)
    override suspend fun currentRoom(): OnlineResult<RoomView?> = OnlineResult.Ok(current)
    override suspend fun createRoom(publicRoom: Boolean, mode: String, length: GameLength): OnlineResult<RoomView> { requestedLengths += length; return next }
    override suspend fun joinByCode(code: String): OnlineResult<RoomView> { joinedCode = code; return next }
    override suspend fun findPublic(mode: String, length: GameLength): OnlineResult<RoomView> { requestedLengths += length; return next }
    override suspend fun state(roomId: String): OnlineResult<RoomView> { stateCalls++; return stateResult }
    override suspend fun start(roomId: String) = next
    override suspend fun leave(roomId: String): OnlineResult<Unit> { leaves++; return OnlineResult.Ok(Unit) }
    override suspend fun bet(roomId: String, index: Int, amount: Int) = next
    override suspend fun answer(roomId: String, index: Int, choice: Int, clientElapsedMs: Long): OnlineResult<RoomView> {
        answers += Triple(index, choice, clientElapsedMs)
        return next
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineGameViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var time = 10_000L
    private val bank = QuestionBank(emptyList(), emptyList(), listOf(Region("cesar", "Cesar")))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(repo: OnlineRepository?): OnlineGameViewModel =
        OnlineGameViewModel(repo, loadBank = { bank }, clock = { time }).also { runCurrent() }

    @Test
    fun withoutServerOnlineIsUnavailable() = runTest(dispatcher) {
        val vm = vm(null)
        assertFalse(vm.state.value.available)
        vm.createPrivate()
        runCurrent()
        assertNull(vm.state.value.view)
    }

    @Test
    fun offersRegionsFromTheBank() = runTest(dispatcher) {
        val vm = vm(FakeOnline())
        assertEquals(listOf("general", "cesar"), vm.state.value.modes.map { it.id })
    }

    @Test
    fun chosenLengthIsSentWhenCreatingOrSearching() = runTest(dispatcher) {
        val repo = FakeOnline()
        val vm = vm(repo)
        vm.createPrivate()
        runCurrent()
        vm.leave()
        runCurrent()
        vm.setLength(GameLength.SHORT)
        vm.findPublic()
        runCurrent()
        assertEquals(listOf(GameLength.STANDARD, GameLength.SHORT), repo.requestedLengths)
        // Dentro de una sala ya no se puede cambiar.
        vm.setLength(GameLength.MEDIUM)
        assertEquals(GameLength.SHORT, vm.state.value.length)
        vm.leave()
    }

    @Test
    fun resumesRoomThePlayerIsStillIn() = runTest(dispatcher) {
        val repo = FakeOnline().apply { current = view(status = "playing", phase = "roulette", remaining = 3000) }
        val vm = vm(repo)
        assertEquals("room-1", vm.state.value.view?.room?.id)
        vm.leave() // detiene la consulta periódica
    }

    @Test
    fun joinNeedsAValidCodeAndNormalizesIt() = runTest(dispatcher) {
        val repo = FakeOnline()
        val vm = vm(repo)
        vm.setCode("abc 1")
        vm.joinByCode()
        runCurrent()
        assertNull("código inválido: no llama al servidor", repo.joinedCode)
        vm.setCode("abc-234")
        vm.joinByCode()
        runCurrent()
        assertEquals("ABC234", repo.joinedCode)
        assertEquals("room-1", vm.state.value.view?.room?.id)
        vm.leave()
    }

    @Test
    fun serverErrorsAreShown() = runTest(dispatcher) {
        val repo = FakeOnline().apply { next = OnlineResult.Failed(OnlineError.ROOM_FULL) }
        val vm = vm(repo)
        vm.setCode("ABC234")
        vm.joinByCode()
        runCurrent()
        assertEquals(OnlineError.ROOM_FULL, vm.state.value.error)
        assertNull(vm.state.value.view)
    }

    @Test
    fun answerReportsTimeSinceTheQuestionWasShown() = runTest(dispatcher) {
        val shown = view(status = "playing", phase = "question", index = 4, withQuestion = true, remaining = 8000)
        val repo = FakeOnline().apply { current = shown; stateResult = OnlineResult.Ok(shown) }
        val vm = vm(repo)
        time += 2_300
        vm.answer(1)
        runCurrent()
        assertEquals(Triple(4, 1, 2_300L), repo.answers.single())
        vm.leave()
    }

    @Test
    fun lostConnectionShowsReconnectingAndRecovers() = runTest(dispatcher) {
        val playing = view(status = "playing", phase = "question", withQuestion = true, remaining = 8000)
        val repo = FakeOnline().apply { current = playing; stateResult = OnlineResult.Failed(OnlineError.NETWORK) }
        val vm = vm(repo)
        advanceTimeBy(1_100)
        runCurrent()
        assertTrue(vm.state.value.reconnecting)
        assertEquals("sigue en la sala", "room-1", vm.state.value.view?.room?.id)
        repo.stateResult = OnlineResult.Ok(playing)
        advanceTimeBy(5_100)
        runCurrent()
        assertFalse(vm.state.value.reconnecting)
        vm.leave()
    }

    @Test
    fun leavingAGameTellsTheServerButAFinishedOneDoesNot() = runTest(dispatcher) {
        val repo = FakeOnline().apply { current = view(status = "playing", phase = "roulette", remaining = 3000) }
        val vm = vm(repo)
        vm.leave()
        runCurrent()
        assertEquals(1, repo.leaves)
        assertNull(vm.state.value.view)

        val finished = FakeOnline().apply { current = view(status = "finished") }
        val vm2 = vm(finished)
        vm2.leave()
        runCurrent()
        assertEquals(0, finished.leaves)
    }

    @Test
    fun removedFromRoomGoesBackToMenu() = runTest(dispatcher) {
        val repo = FakeOnline().apply {
            current = view(status = "playing", phase = "roulette", remaining = 500)
            stateResult = OnlineResult.Failed(OnlineError.NOT_IN_ROOM)
        }
        val vm = vm(repo)
        advanceTimeBy(1_100)
        runCurrent()
        assertNull(vm.state.value.view)
        assertEquals(OnlineError.NOT_IN_ROOM, vm.state.value.error)
    }

    @Test
    fun errorCodesAreRecognized() {
        assertEquals(OnlineError.TIME_EXPIRED, OnlineError.from("time_expired"))
        assertEquals(OnlineError.ROOM_NOT_FOUND, OnlineError.from("room_not_found"))
        assertEquals(OnlineError.UNKNOWN, OnlineError.from("algo raro"))
        assertEquals(OnlineError.UNKNOWN, OnlineError.from(null))
    }
}
