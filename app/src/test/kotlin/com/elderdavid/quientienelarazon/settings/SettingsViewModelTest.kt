package com.elderdavid.quientienelarazon.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

private class FakeSettingsRepository : SettingsRepository {
    val state = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = state
    override suspend fun setSoundEnabled(enabled: Boolean) = state.update { it.copy(soundEnabled = enabled) }
    override suspend fun setVibrationEnabled(enabled: Boolean) = state.update { it.copy(vibrationEnabled = enabled) }
    override suspend fun setReducedMotion(enabled: Boolean) = state.update { it.copy(reducedMotion = enabled) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun togglesArePersistedInRepository() = runTest {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)

        vm.setSoundEnabled(false)
        vm.setVibrationEnabled(false)
        vm.setReducedMotion(true)

        assertEquals(AppSettings(soundEnabled = false, vibrationEnabled = false, reducedMotion = true), repo.state.value)
    }

    @Test
    fun exposesRepositoryValues() = runTest {
        val repo = FakeSettingsRepository()
        repo.state.value = AppSettings(soundEnabled = false)
        val vm = SettingsViewModel(repo)

        assertFalse(vm.settings.first { !it.soundEnabled }.soundEnabled)
    }
}
