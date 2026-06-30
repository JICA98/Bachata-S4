package com.bachatas4.android.feature.library

import androidx.lifecycle.ViewModel
import com.bachatas4.android.model.Game
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LibraryUiState(
    val games: List<Game> = emptyList(),
    val selectedGameId: String? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor() : ViewModel() {
    private val mutableState = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = mutableState

    fun setGames(games: List<Game>) {
        mutableState.value = mutableState.value.copy(games = sortGames(games))
    }

    fun selectGame(id: String) {
        mutableState.value = mutableState.value.copy(selectedGameId = id)
    }

    fun sortGames(games: List<Game>): List<Game> =
        games.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Game::title).thenBy(Game::id))
}
