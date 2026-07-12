package com.bachatas4.android.data

import android.content.Context
import com.bachatas4.android.database.GameDao
import com.bachatas4.android.database.GameEntity
import com.bachatas4.android.model.Game
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GameRepository @Inject constructor(
    private val gameDao: GameDao,
    @ApplicationContext private val context: Context,
) {
    fun observeGames(): Flow<List<Game>> =
        gameDao.observeAll().map { games -> games.map { it.toModel() } }

    suspend fun getGame(id: String): Game? = gameDao.getById(id)?.toModel()

    suspend fun addImportedGame(
        result: ContentImportResult,
        sourceUri: String,
        importedAtMs: Long,
    ) {
        gameDao.insert(
            GameEntity(
                id = result.game.id,
                title = result.game.title,
                relativePath = result.game.relativePath,
                sourceUri = sourceUri,
                importedAtMs = importedAtMs,
            ),
        )
    }

    /**
     * Re-read TITLE from on-disk param.sfo for games that were imported under folder names.
     * Does not rename game ids or directories.
     */
    suspend fun backfillTitlesFromSfo() {
        val entities = gameDao.getAll()
        val byId = entities.associateBy { it.id }
        val updates = titlesToUpdate(
            games = entities.map { it.id to it.title },
            sfoTitleFor = { id ->
                val entity = byId[id] ?: return@titlesToUpdate null
                val file = GameIconPaths.paramSfo(context.filesDir, entity.relativePath)
                if (!file.isFile) return@titlesToUpdate null
                runCatching { ParamSfoReader.parse(file.readBytes()).title }.getOrNull()
            },
        )
        updates.forEach { (id, title) -> gameDao.updateTitle(id, title) }
    }

    suspend fun deleteGame(id: String): Boolean {
        val game = gameDao.getById(id) ?: return false
        val gamesRoot = context.filesDir.resolve("games").canonicalFile
        val ownedPath = context.filesDir.resolve(game.relativePath).canonicalFile
        require(ownedPath.toPath().startsWith(gamesRoot.toPath())) { "Game path escapes app-owned storage" }
        if (ownedPath.exists() && !ownedPath.deleteRecursively()) return false
        return gameDao.deleteById(id) > 0
    }
}

private fun GameEntity.toModel(): Game =
    Game(
        id = id,
        title = title,
        relativePath = relativePath,
    )
