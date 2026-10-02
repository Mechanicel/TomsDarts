package com.mechanicel.tomsdarts.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.mechanicel.tomsdarts.data.settings.AppSettings
import com.mechanicel.tomsdarts.data.settings.SettingsRepository
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Tests fuer [SettingsViewModel] (ADR-0040): Loading -> Content, Umschalten,
 * Schreibfehler -> [SettingsViewModel.saveError], idempotentes Umschalten.
 *
 * Das echte [SettingsRepository] laeuft gegen einen In-Memory-Fake-DataStore
 * (kein Dateisystem, keine eigenen Coroutines) - so bleibt nach dem Test nichts
 * nachlaufen. Der `viewModelScope` nutzt ueber die [MainDispatcherRule] denselben
 * Scheduler wie `runTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var store: FakeDataStore
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        store = FakeDataStore()
        viewModel = SettingsViewModel(SettingsRepository(store))
    }

    @Test
    fun startetMitLoadingUndLiefertDannContent() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        assertEquals(SettingsUiState.Loading, viewModel.uiState.value)

        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertEquals(SettingsUiState.Content(AppSettings.DEFAULT), viewModel.uiState.value)
    }

    @Test
    fun umschaltenKommtImZustandAn() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.setDelightEnabled(false)
        runCurrent()

        assertEquals(SettingsUiState.Content(AppSettings(delightEnabled = false)), viewModel.uiState.value)

        viewModel.setDelightEnabled(true)
        runCurrent()

        assertEquals(SettingsUiState.Content(AppSettings(delightEnabled = true)), viewModel.uiState.value)
        assertFalse(viewModel.saveError.value)
    }

    @Test
    fun schreibfehlerSetztSaveError_wertBleibtAlt() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        store.failWrites = true

        viewModel.setDelightEnabled(false)
        runCurrent()

        assertTrue(viewModel.saveError.value)
        // Kein optimistischer Zwischenstand: der gespeicherte Wert bleibt sichtbar.
        assertEquals(SettingsUiState.Content(AppSettings(delightEnabled = true)), viewModel.uiState.value)

        viewModel.onSaveErrorShown()

        assertFalse(viewModel.saveError.value)
    }

    @Test
    fun doppeltesUmschaltenIstIdempotent() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val states = mutableListOf<SettingsUiState>()
        backgroundScope.launch { viewModel.uiState.collect { states += it } }
        runCurrent()

        viewModel.setDelightEnabled(false)
        viewModel.setDelightEnabled(false)
        runCurrent()

        assertEquals(
            listOf(
                SettingsUiState.Content(AppSettings(delightEnabled = true)),
                SettingsUiState.Content(AppSettings(delightEnabled = false)),
            ),
            // Loading kann der StateFlow schon vor dem ersten Sammeln ersetzt haben.
            states.filterIsInstance<SettingsUiState.Content>(),
        )
        assertFalse(viewModel.saveError.value)
    }

    /** In-Memory-DataStore; [failWrites] simuliert einen Schreibfehler (IOException). */
    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        var failWrites = false

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            if (failWrites) throw IOException("Speicher voll")
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
