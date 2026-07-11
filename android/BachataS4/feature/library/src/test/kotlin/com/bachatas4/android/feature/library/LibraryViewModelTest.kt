package com.bachatas4.android.feature.library

import com.bachatas4.android.model.Game
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryViewModelTest {
    @Test
    fun sortsGamesByTitleThenId() {
        val viewModel = LibraryViewModel()

        viewModel.setGames(
            listOf(
                Game(id = "CUSA3", title = "zeta", relativePath = "games/CUSA3"),
                Game(id = "CUSA2", title = "Alpha", relativePath = "games/CUSA2"),
                Game(id = "CUSA1", title = "alpha", relativePath = "games/CUSA1"),
            ),
        )

        assertEquals(listOf("CUSA1", "CUSA2", "CUSA3"), viewModel.state.value.games.map { it.id })
    }

    @Test
    fun keepsSelectionWhenPresentOtherwiseSelectsFirstSortedGame() {
        val viewModel = LibraryViewModel()

        viewModel.setGames(listOf(game("B", "Beta"), game("A", "Alpha")))
        assertEquals("A", viewModel.state.value.selectedGameId)

        viewModel.selectGame("B")
        viewModel.setGames(listOf(game("B", "Beta"), game("A", "Alpha")))
        assertEquals("B", viewModel.state.value.selectedGameId)
    }

    private fun game(id: String, title: String) = Game(id, title, "games/$id")
}
