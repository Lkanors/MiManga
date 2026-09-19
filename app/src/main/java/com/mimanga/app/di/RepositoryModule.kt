package com.mimanga.app.di

import com.mimanga.app.data.repository.AccountRepositoryImpl
import com.mimanga.app.data.repository.MangaRepositoryImpl
import com.mimanga.app.data.repository.ProgressRepositoryImpl
import com.mimanga.app.data.repository.SettingsRepositoryImpl
import com.mimanga.app.domain.repository.AccountRepository
import com.mimanga.app.domain.repository.MangaRepository
import com.mimanga.app.domain.repository.ProgressRepository
import com.mimanga.app.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMangaRepository(impl: MangaRepositoryImpl): MangaRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindProgressRepository(impl: ProgressRepositoryImpl): ProgressRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
