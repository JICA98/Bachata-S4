package com.bachatas4.android.data

import android.content.Context
import androidx.room.Room
import com.bachatas4.android.database.BachataDatabase
import com.bachatas4.android.database.GameDao
import com.bachatas4.android.database.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): BachataDatabase =
        Room.databaseBuilder(context, BachataDatabase::class.java, "bachata.db").build()

    @Provides
    fun gameDao(database: BachataDatabase): GameDao = database.gameDao()

    @Provides
    fun sessionDao(database: BachataDatabase): SessionDao = database.sessionDao()

    @Provides
    fun contentImporter(@ApplicationContext context: Context): ContentImporter =
        ContentImporter(context.filesDir, ContentResolverImportSource(context.contentResolver))
}
