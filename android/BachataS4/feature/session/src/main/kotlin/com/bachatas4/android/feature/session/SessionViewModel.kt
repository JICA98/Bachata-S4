package com.bachatas4.android.feature.session

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bachatas4.android.data.GameRepository
import com.bachatas4.android.model.RuntimeErrorCode
import com.bachatas4.android.runtime.session.ManagedSession
import com.bachatas4.android.runtime.session.ManagedSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repository: GameRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val state: StateFlow<ManagedSessionState> = ManagedSession.state

    fun launch(gameId: String) {
        if (state.value is ManagedSessionState.Running || state.value is ManagedSessionState.Preparing) return
        viewModelScope.launch {
            val game = repository.getGame(gameId)
            if (game == null) {
                ManagedSession.update(ManagedSessionState.Failed(RuntimeErrorCode.CONTENT_INVALID, "Game not found"))
                return@launch
            }
            context.startForegroundService(
                Intent(ManagedSession.ACTION_START).setClassName(context.packageName, ManagedSession.SERVICE_CLASS)
                    .putExtra(ManagedSession.EXTRA_GAME_ID, game.id)
                    .putExtra(ManagedSession.EXTRA_GAME_PATH, game.relativePath),
            )
        }
    }
}
