package com.taizi.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taizi.domain.model.*
import com.taizi.domain.model.LibraryChange
import com.taizi.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class MainUiState {
    object Initial : MainUiState()
    object Scanning : MainUiState()
    data class LibraryLoaded(val library: Library) : MainUiState()
    data class Error(val message: String) : MainUiState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: LibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Initial)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _currentScreen = MutableStateFlow<Screen>(Screen.SystemList)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private var scanJob: Job? = null
    private var fileObserverJob: Job? = null

    init {
        // Load cached library on init
        viewModelScope.launch {
            repository.loadCachedLibraryIfAvailable()
        }

        // Start file observer if we have a ROM root
        viewModelScope.launch {
            val currentLibrary = repository.getLibrary().value
            if (currentLibrary.romRoot.isNotEmpty()) {
                startFileObserver(currentLibrary.romRoot)
            }
        }
    }

    fun setScreen(screen: Screen) {
        _currentScreen.value = screen
    }

    fun navigateToSystem(systemId: String) {
        _currentScreen.value = Screen.GameList(systemId)
    }

    fun navigateBack() {
        _currentScreen.value = Screen.SystemList
    }

    /**
     * Trigger a full library scan
     */
    fun triggerFullScan(romRoot: String? = null) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.value = MainUiState.Scanning

            val root = romRoot ?: repository.getLibrary().value.romRoot
            if (root.isEmpty()) {
                _uiState.value = MainUiState.Error("No ROM root configured")
                return@launch
            }

            val result = repository.scanLibrary(root, force = true)
            result.fold(
                onSuccess = { library ->
                    _uiState.value = MainUiState.LibraryLoaded(library)
                    startFileObserver(library.romRoot)
                },
                onFailure = { error ->
                    _uiState.value = MainUiState.Error(error.message ?: "Scan failed")
                }
            )
        }
    }

    private fun startFileObserver(romRoot: String) {
        fileObserverJob?.cancel()
        repository.startFileObserver(romRoot) { change ->
            // Debounce and handle file changes
            viewModelScope.launch {
                delay(500) // Debounce
                triggerFullScan(romRoot)
            }
        }
    }

    fun launchGame(game: Game) {
        viewModelScope.launch {
            val result = repository.launchGame(game)
            if (result.isFailure) {
                // TODO: Show error snackbar
                val error = result.exceptionOrNull()
                // _uiState.value = MainUiState.Error(error?.message ?: "Launch failed")
            }
        }
    }

    fun toggleFavorite(gamePath: String, favorite: Boolean) {
        viewModelScope.launch {
            repository.updateGameFavorite(gamePath, favorite)
        }
    }

    fun getSystemById(systemId: String): System? {
        return repository.getLibrary().value.systems.find { system: System -> system.id == systemId }
    }

    fun getGamesForSystem(systemId: String): List<Game> {
        return repository.getLibrary().value.gamesBySystem[systemId] ?: emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        fileObserverJob?.cancel()
        repository.stopFileObserver()
    }
}

/**
 * Navigation screens
 */
sealed class Screen {
    object SystemList : Screen()
    data class GameList(val systemId: String) : Screen()
    object Settings : Screen()
    object SystemManager : Screen()
    object EmulatorManager : Screen()
    object Scraper : Screen()
    object BiosStatus : Screen()
}
