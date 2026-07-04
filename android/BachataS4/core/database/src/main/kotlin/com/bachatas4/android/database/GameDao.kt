package com.bachatas4.android.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val id: String,
    val title: String,
    val relativePath: String,
    val sourceUri: String,
    val importedAtMs: Long,
)

@Dao
interface GameDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(game: GameEntity)

    @Query("SELECT * FROM games ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getById(id: String): GameEntity?

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
