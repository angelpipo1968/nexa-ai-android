package com.nexa.ai.di

import android.app.Application
import android.content.Context
import com.nexa.ai.data.NexaRepository
import com.nexa.ai.data.SettingsStore
import com.nexa.ai.data.UpdateChecker
import com.nexa.ai.data.LocationStore
import com.nexa.ai.data.SessionStore
import com.nexa.ai.data.repository.ImageRepository
import com.nexa.ai.data.repository.VideoRepository
import com.nexa.ai.data.remote.NexaMediaApi
import com.nexa.ai.iot.IoTManager
import com.nexa.ai.media.VideoGenerator
import com.nexa.ai.memory.EpisodicMemoryManager
import com.nexa.ai.ml.OnDeviceMLEngine
import com.nexa.ai.sensors.NexaSensorManager
import com.nexa.ai.viewmodel.AuthManager
import com.nexa.ai.voice.NaturalConversationEngine
import com.nexa.ai.voice.SpeechManager
import com.nexa.ai.voice.VoiceEnhancer
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

    @Provides
    @Singleton
    fun provideWebSearchManager(): com.nexa.ai.web.WebSearchManager = com.nexa.ai.web.WebSearchManager()

    @Provides
    @Singleton
    fun provideWebResultProcessor(
        searchManager: com.nexa.ai.web.WebSearchManager
    ): com.nexa.ai.web.WebResultProcessor = com.nexa.ai.web.WebResultProcessor(searchManager)

    @Provides
    @Singleton
    fun provideEnhancedEmotionAnalyzer(): com.nexa.ai.ml.EnhancedEmotionAnalyzer = com.nexa.ai.ml.EnhancedEmotionAnalyzer()

    @Provides
    @Singleton
    fun provideUserProfileManager(): com.nexa.ai.ml.UserProfileManager = com.nexa.ai.ml.UserProfileManager()

    @Provides
    @Singleton
    fun provideLocationStore(application: Application): LocationStore = LocationStore(application)

    @Provides
    @Singleton
    fun provideSessionStore(application: Application): SessionStore = SessionStore(application)

    @Provides
    @Singleton
    fun provideOnDeviceInferenceManager(
        @ApplicationContext context: Context
    ): com.nexa.ai.ml.OnDeviceInferenceManager = com.nexa.ai.ml.OnDeviceInferenceManager(context)

    // ─── Voice subsystem providers ───────────────────────────────

    @Provides
    @Singleton
    fun provideVoiceEnhancer(application: Application): VoiceEnhancer = VoiceEnhancer(application)

    @Provides
    @Singleton
    fun provideNaturalConversationEngine(application: Application): NaturalConversationEngine =
        NaturalConversationEngine(application)

    // ─── IoT provider ────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideIoTManager(application: Application): IoTManager = IoTManager(application)

    // ─── Media providers ─────────────────────────────────────────

    @Provides
    @Singleton
    fun provideImageRepository(nexaMediaApi: NexaMediaApi): ImageRepository =
        ImageRepository(nexaMediaApi)

    @Provides
    @Singleton
    fun provideVideoRepository(nexaMediaApi: NexaMediaApi): VideoRepository =
        VideoRepository(nexaMediaApi)

    @Provides
    @Singleton
    fun provideVideoGenerator(application: Application): VideoGenerator = VideoGenerator(application)

    // ─── ML provider ─────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideOnDeviceMLEngine(application: Application): OnDeviceMLEngine = OnDeviceMLEngine(application)

    // ─── Memory provider ─────────────────────────────────────────

    @Provides
    @Singleton
    fun provideEpisodicMemoryManager(@ApplicationContext context: Context): EpisodicMemoryManager =
        EpisodicMemoryManager(context)

    // ─── Sensor provider ─────────────────────────────────────────

    @Provides
    @Singleton
    fun provideNexaSensorManager(application: Application): NexaSensorManager = NexaSensorManager(application)

    // Note: The following classes are already Hilt-injectable via @Inject constructor
    // and do NOT need @Provides methods:
    // - VoiceUseCase (@Singleton @Inject constructor)
    // - ChatUseCase (@Singleton @Inject constructor)
    // - LocalLLMManager (@Singleton @Inject constructor)
    // - NexaRepository (@Singleton @Inject constructor)
    // - ContextProvider (@Singleton @Inject constructor)
    // - ResponseSanitizer (@Singleton @Inject constructor)
}
