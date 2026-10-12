package com.elderdavid.quientienelarazon.stats

import com.elderdavid.quientienelarazon.account.AccountRepository
import com.elderdavid.quientienelarazon.account.AccountState
import com.elderdavid.quientienelarazon.account.DeleteAccountResult
import com.elderdavid.quientienelarazon.account.GoogleToken
import com.elderdavid.quientienelarazon.account.LinkResult
import com.elderdavid.quientienelarazon.account.Profile
import com.elderdavid.quientienelarazon.account.ProfileRepository
import com.elderdavid.quientienelarazon.account.ProfileSaveResult
import com.elderdavid.quientienelarazon.core.game.GameState
import com.elderdavid.quientienelarazon.core.profile.LocalGameSummary
import com.elderdavid.quientienelarazon.core.profile.LocalHistory
import com.elderdavid.quientienelarazon.core.profile.LocalPlayerScore
import com.elderdavid.quientienelarazon.core.profile.PlayerStats
import com.elderdavid.quientienelarazon.core.questions.QuestionBank
import com.elderdavid.quientienelarazon.core.questions.Region
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeAccounts(private val online: Boolean = true) : AccountRepository {
    override val state = MutableStateFlow<AccountState>(AccountState.SignedOut)
    override suspend fun ensureSignedIn(): Result<Unit> {
        if (!online) return Result.failure(java.io.IOException("sin red"))
        state.value = AccountState.Guest("u1")
        return Result.success(Unit)
    }
    override suspend fun linkGoogle(token: GoogleToken) = LinkResult.Linked
    override suspend fun switchToGoogle(token: GoogleToken) = LinkResult.Linked
    override suspend fun signOut() = Unit
    override suspend fun deleteAccount() = DeleteAccountResult.Failed("no usado")
}

private class FakeProfiles(var stats: PlayerStats = PlayerStats(gamesPlayed = 3, wins = 2, losses = 1, xp = 150)) : ProfileRepository {
    var loads = 0
    override suspend fun load(userId: String): Result<Pair<Profile, PlayerStats>> {
        loads++
        return Result.success(Profile(userId, "Ana", 0) to stats)
    }
    override suspend fun save(displayName: String, avatarId: Int) = ProfileSaveResult.Failed("no usado")
}

private class FakeBoard(var result: Result<Leaderboard>) : LeaderboardRepository {
    override suspend fun load(limit: Int) = result
}

private class FakeHistory(initial: LocalHistory) : LocalHistoryRepository {
    val flow = MutableStateFlow(initial)
    override val history: StateFlow<LocalHistory> = flow
    override suspend fun record(finished: GameState) = error("no usado")
    override suspend fun clear() { flow.value = LocalHistory() }
}

private val oneGame = LocalHistory(
    gamesFinished = 1,
    bestScore = 120,
    bestScoreName = "Luis",
    recent = listOf(LocalGameSummary(1_000, "cesar", listOf(LocalPlayerScore("Luis", 120), LocalPlayerScore("Ana", 80)), listOf("Luis"))),
)

private val bank = QuestionBank(emptyList(), emptyList(), listOf(Region("cesar", "Cesar")))

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val board = Leaderboard(
        top = listOf(LeaderboardEntry(1, "Beto", 2, 500, 4, 6), LeaderboardEntry(2, "Ana", 0, 150, 2, 3, me = true)),
        me = LeaderboardEntry(2, "Ana", 0, 150, 2, 3, me = true),
    )

    @Test
    fun onlineAndLocalAreShownSeparatelyAndNeverAdded() = runTest {
        val vm = StatsViewModel(FakeAccounts(), FakeProfiles(), FakeBoard(Result.success(board)), FakeHistory(oneGame)) { bank }
        val s = vm.state.value
        val online = s.online as OnlineStats.Ready
        assertEquals(3, online.stats.gamesPlayed)
        assertEquals("Beto", online.leaderboard!!.top.first().name)
        assertEquals(1, s.local.gamesFinished)
        assertEquals("Cesar", s.regionNames["cesar"])
    }

    @Test
    fun withoutServerOnlyLocalHistoryIsShown() = runTest {
        val vm = StatsViewModel(null, null, null, FakeHistory(oneGame)) { bank }
        assertEquals(OnlineStats.NotConfigured, vm.state.value.online)
        assertEquals(1, vm.state.value.local.gamesFinished)
    }

    @Test
    fun offlineShowsUnavailableInsteadOfOldNumbers() = runTest {
        val profiles = FakeProfiles()
        val vm = StatsViewModel(FakeAccounts(online = false), profiles, FakeBoard(Result.success(board)), FakeHistory(LocalHistory())) { bank }
        assertEquals(OnlineStats.Unavailable, vm.state.value.online)
        assertEquals(0, profiles.loads)
    }

    @Test
    fun leaderboardFailureKeepsOwnStats() = runTest {
        val vm = StatsViewModel(FakeAccounts(), FakeProfiles(), FakeBoard(Result.failure(java.io.IOException())), FakeHistory(LocalHistory())) { bank }
        val online = vm.state.value.online as OnlineStats.Ready
        assertEquals(150, online.stats.xp)
        assertNull(online.leaderboard)
    }

    @Test
    fun clearingLocalHistoryAsksFirstAndKeepsOnlineStats() = runTest {
        val history = FakeHistory(oneGame)
        val vm = StatsViewModel(FakeAccounts(), FakeProfiles(), FakeBoard(Result.success(board)), history) { bank }
        vm.askClear()
        assertTrue(vm.state.value.confirmClear)
        vm.dismissClear()
        assertEquals(1, vm.state.value.local.gamesFinished)
        vm.askClear()
        vm.clearLocal()
        assertFalse(vm.state.value.confirmClear)
        assertEquals(0, vm.state.value.local.gamesFinished)
        assertTrue(vm.state.value.online is OnlineStats.Ready)
    }
}
