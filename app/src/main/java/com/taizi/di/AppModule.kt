package com.taizi.di

import android.content.Context
import com.taizi.data.local.BoxArtDao
import com.taizi.data.local.BoxArtDatabase
import com.taizi.data.local.LocalDataSource
import com.taizi.data.repository.LibraryRepositoryImpl
import com.taizi.domain.repository.LibraryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideLocalDataSource(@ApplicationContext context: Context): LocalDataSource {
        return LocalDataSource(context)
    }

    @Provides
    @Singleton
    fun provideBoxArtDatabase(@ApplicationContext context: Context): BoxArtDatabase {
        return BoxArtDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideBoxArtDao(database: BoxArtDatabase): BoxArtDao {
        return database.boxArtDao()
    }

    @Provides
    @Singleton
    fun provideLibraryRepository(
        @ApplicationContext context: Context,
        localDataSource: LocalDataSource,
        boxArtDao: BoxArtDao
    ): LibraryRepository {
        return LibraryRepositoryImpl(context, localDataSource, boxArtDao)
    }
}
