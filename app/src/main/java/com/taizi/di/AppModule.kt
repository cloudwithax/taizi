package com.taizi.di

import android.content.Context
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
    fun provideLibraryRepository(
        @ApplicationContext context: Context,
        localDataSource: LocalDataSource
    ): LibraryRepository {
        return LibraryRepositoryImpl(context, localDataSource)
    }
}
