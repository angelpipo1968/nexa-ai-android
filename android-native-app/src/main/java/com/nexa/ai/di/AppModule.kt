package com.nexa.ai.di

import android.app.Application
import android.content.Context
import com.nexa.ai.data.NexaRepository
import com.nexa.ai.data.SettingsStore
import com.nexa.ai.data.UpdateChecker
import com.nexa.ai.viewmodel.AuthManager
import com.nexa.ai.viewmodel.SpeechManager
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
    fun provideNexaRepository(): NexaRepository = NexaRepository()

    @Provides
    @Singleton
    fun provideUpdateChecker(): UpdateChecker = UpdateChecker()

    @Provides
    @Singleton
    fun provideAuthManager(application: Application): AuthManager = AuthManager(application)

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore = SettingsStore(context)

    @Provides
    @Singleton
    fun provideSpeechManager(application: Application): SpeechManager = SpeechManager(application)
}
