package com.bachatas4.android.feature.library

import androidx.lifecycle.ViewModel
import com.bachatas4.android.model.Game
import com.bachatas4.android.runtime.input.GamepadInputManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class LibraryUiState(
    val games: List<Game> = emptyList(),
    val selectedGameId: String? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor() : ViewModel() {
    private val mutableState = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = mutableState
    private var focusedIndex: Int = 0
    private val launchRequest = MutableSharedFlow<String>(extraBufferCapacity = 4)

    fun setGames(games: List<Game>) {
        val sorted = sortGames(games)
        val selected = mutableState.value.selectedGameId
            ?.takeIf { id -> sorted.any { it.id == id } }
            ?: sorted.firstOrNull()?.id
        mutableState.value = mutableState.value.copy(games = sorted, selectedGameId = selected)
        focusedIndex = sorted.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    }

    fun selectGame(id: String) {
        val index = mutableState.value.games.indexOfFirst { it.id == id }
        if (index >= 0) focusedIndex = index
        mutableState.value = mutableState.value.copy(selectedGameId = id)
    }

    fun navigatePrev() = navigate(-1)
    fun navigateNext() = navigate(1)

    private fun navigate(direction: Int) {
        val games = mutableState.value.games
        if (games.isEmpty()) return
        focusedIndex = ((focusedIndex + direction) % games.size + games.size) % games.size
        mutableState.value = mutableState.value.copy(selectedGameId = games[focusedIndex].id)
    }

    fun attachNavListener() {
        GamepadInputManager.registerNavListener { event ->
            when {
                event.control == "dpad_left" && event.pressed -> { navigatePrev(); true }
                event.control == "dpad_right" && event.pressed -> { navigateNext(); true }
                event.control == "cross" && event.pressed -> {
                    mutableState.value.selectedGameId?.let { launchRequest.tryEmit(it) }
                    true
                }
                event.control == "circle" && event.pressed -> true
                else -> false
            }
        }
    }

    val launch: SharedFlow<String> = launchRequest

    override fun onCleared() {
        GamepadInputManager.unregisterNavListener()
        super.onCleared()
    }

    fun sortGames(games: List<Game>): List<Game> =
        games.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Game::title).thenBy(Game::id))
}
