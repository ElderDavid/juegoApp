package com.elderdavid.quientienelarazon.account

import com.elderdavid.quientienelarazon.core.profile.PlayerStats
import com.elderdavid.quientienelarazon.core.profile.ProfileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeAccounts(
    var linkOutcome: LinkResult = LinkResult.Linked,
    var deleteOutcome: DeleteAccountResult = DeleteAccountResult.Deleted,
) : AccountRepository {
    override val state = MutableStateFlow<AccountState>(AccountState.SignedOut)
    var switched = false
    var deletes = 0
    private var nextGuest = "guest-1"

    override suspend fun ensureSignedIn(): Result<Unit> {
        if (state.value == AccountState.SignedOut) state.value = AccountState.Guest(nextGuest)
        return Result.success(Unit)
    }

    override suspend fun linkGoogle(token: GoogleToken): LinkResult {
        if (linkOutcome == LinkResult.Linked) state.value = AccountState.Google("guest-1", "a@b.co")
        return linkOutcome
    }

    override suspend fun switchToGoogle(token: GoogleToken): LinkResult {
        switched = true
        state.value = AccountState.Google("other-7", "a@b.co")
        return LinkResult.Linked
    }

    override suspend fun signOut() {
        state.value = AccountState.SignedOut
    }

    override suspend fun deleteAccount(): DeleteAccountResult {
        deletes++
        if (deleteOutcome == DeleteAccountResult.Deleted) {
            // El servidor borra al usuario: la siguiente sesión es un invitado nuevo.
            nextGuest = "guest-2"
            state.value = AccountState.SignedOut
        }
        return deleteOutcome
    }
}

private class FakeProfiles : ProfileRepository {
    val profiles = mutableMapOf(
        "guest-1" to Profile("guest-1", "Jugador ABCDE", 0),
        "guest-2" to Profile("guest-2", "Jugador FGHIJ", 0),
        "other-7" to Profile("other-7", "Elder", 4),
    )
    var saves = 0

    override suspend fun load(userId: String) = Result.success(profiles.getValue(userId) to PlayerStats(xp = 150))

    override suspend fun save(displayName: String, avatarId: Int): ProfileSaveResult {
        saves++
        val p = Profile("guest-1", ProfileName.clean(displayName), avatarId)
        profiles["guest-1"] = p
        return ProfileSaveResult.Saved(p)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val token = GoogleToken("id-token", "nonce")

    @Test
    fun withoutConfigurationShowsNotConfiguredAndDoesNothing() = runTest {
        val vm = ProfileViewModel(null, null, googleConfigured = false)
        assertEquals(AccountState.NotConfigured, vm.state.value.account)
        assertFalse(vm.state.value.googleAvailable)
    }

    @Test
    fun startsAsGuestAndLoadsProfile() = runTest {
        val vm = ProfileViewModel(FakeAccounts(), FakeProfiles(), googleConfigured = true)
        val s = vm.state.value
        assertEquals(AccountState.Guest("guest-1"), s.account)
        assertEquals("Jugador ABCDE", s.nameDraft)
        assertEquals(2, s.stats!!.level)
    }

    @Test
    fun offensiveNameCannotBeSaved() = runTest {
        val profiles = FakeProfiles()
        val vm = ProfileViewModel(FakeAccounts(), profiles, googleConfigured = true)
        vm.setName("Gonorrea")
        assertFalse(vm.state.value.canSave)
        vm.save()
        assertEquals(0, profiles.saves)
        vm.setName("  Ñandú  ")
        vm.setAvatar(5)
        vm.save()
        assertEquals("Ñandú", vm.state.value.profile!!.displayName)
        assertEquals(5, vm.state.value.profile!!.avatarId)
    }

    @Test
    fun linkingKeepsSameUserId() = runTest {
        val accounts = FakeAccounts()
        val vm = ProfileViewModel(accounts, FakeProfiles(), googleConfigured = true)
        vm.onGoogleCredential(GoogleCredentialResult.Success(token))
        assertEquals(AccountState.Google("guest-1", "a@b.co"), vm.state.value.account)
        assertFalse(vm.state.value.conflict)
    }

    @Test
    fun identityInUseAsksBeforeSwitching() = runTest {
        val accounts = FakeAccounts(linkOutcome = LinkResult.IdentityInUse)
        val vm = ProfileViewModel(accounts, FakeProfiles(), googleConfigured = true)
        vm.onGoogleCredential(GoogleCredentialResult.Success(token))
        assertTrue(vm.state.value.conflict)
        assertFalse(accounts.switched)
        assertEquals(AccountState.Guest("guest-1"), vm.state.value.account)

        vm.onGoogleCredential(GoogleCredentialResult.Success(token), switching = true)
        assertTrue(accounts.switched)
        assertEquals("Elder", vm.state.value.profile!!.displayName)
    }

    @Test
    fun cancelledGoogleChangesNothing() = runTest {
        val vm = ProfileViewModel(FakeAccounts(), FakeProfiles(), googleConfigured = true)
        vm.onGoogleCredential(GoogleCredentialResult.Cancelled)
        assertEquals(AccountState.Guest("guest-1"), vm.state.value.account)
        assertEquals(null, vm.state.value.message)
    }

    @Test
    fun deletingAsksFirstThenStartsAsNewGuest() = runTest {
        val accounts = FakeAccounts()
        val vm = ProfileViewModel(accounts, FakeProfiles(), googleConfigured = true)
        vm.askDelete()
        assertTrue(vm.state.value.confirmDelete)
        vm.dismissDelete()
        assertFalse(vm.state.value.confirmDelete)
        assertEquals(0, accounts.deletes)

        vm.askDelete()
        vm.deleteAccount()
        assertEquals(1, accounts.deletes)
        val s = vm.state.value
        assertFalse(s.confirmDelete)
        assertFalse(s.deleting)
        assertEquals(AccountState.Guest("guest-2"), s.account)
        assertEquals("Jugador FGHIJ", s.profile!!.displayName)
        assertTrue(s.message!!.contains("borraron"))
    }

    @Test
    fun deletingDuringAnOnlineGameKeepsTheAccount() = runTest {
        val accounts = FakeAccounts(deleteOutcome = DeleteAccountResult.InActiveRoom)
        val vm = ProfileViewModel(accounts, FakeProfiles(), googleConfigured = true)
        vm.askDelete()
        vm.deleteAccount()
        assertEquals(AccountState.Guest("guest-1"), vm.state.value.account)
        assertEquals("Jugador ABCDE", vm.state.value.profile!!.displayName)
        assertTrue(vm.state.value.message!!.contains("Sal de la partida"))
    }

    @Test
    fun tokenIsNeverPrinted() {
        assertFalse(token.toString().contains("id-token"))
    }
}
