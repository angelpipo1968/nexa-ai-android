package com.nexa.ai.di

import android.app.Application
import com.nexa.ai.data.LocationStore
import com.nexa.ai.data.NexaRepository
import com.nexa.ai.data.SessionStore
import com.nexa.ai.data.SettingsStore
import com.nexa.ai.data.UpdateChecker
import com.nexa.ai.iot.IoTManager
import com.nexa.ai.media.VideoGenerator
import com.nexa.ai.ml.EnhancedEmotionAnalyzer
import com.nexa.ai.ml.OnDeviceMLEngine
import com.nexa.ai.ml.UserProfileManager
import com.nexa.ai.memory.EpisodicMemoryManager
import com.nexa.ai.sensors.NexaSensorManager
import com.nexa.ai.voice.NaturalConversationEngine
import com.nexa.ai.voice.VoiceEnhancer
import com.nexa.ai.web.WebResultProcessor
import com.nexa.ai.web.WebSearchManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI Module — Provides all manager dependencies as singletons.
 * This replaces the manual instantiation that was previously in NexaViewModel.
 */
@Module
@InstallIn(SingletonComponent::class)
object NexaAppModule {

    // ─── Core Data Stores ────────────────────────────────────

    @Provides
    @Singleton
    fun provideSettingsStore(app: Application): SettingsStore = SettingsStore(app)

    @Provides
    @Singleton
    fun provideSessionStore(app: Application): SessionStore = SessionStore(app)

    @Provides
    @Singleton
    fun provideLocationStore(app: Application): LocationStore = LocationStore(app)

    @Provides
    @Singleton
    fun provideNexaRepository(): NexaRepository = NexaRepository()

    @Provides
    @Singleton
    fun provideUpdateChecker(): UpdateChecker = UpdateChecker()

    // ─── AI & ML Managers ────────────────────────────────────

    @Provides
    @Singleton
    fun provideOnDeviceMLEngine(app: Application): OnDeviceMLEngine = OnDeviceMLEngine(app)

    @Provides
    @Singleton
    fun provideEnhancedEmotionAnalyzer(): EnhancedEmotionAnalyzer = EnhancedEmotionAnalyzer()

    @Provides
    @Singleton
    fun provideUserProfileManager(): UserProfileManager = UserProfileManager()

    @Provides
    @Singleton
    fun provideEpisodicMemoryManager(): EpisodicMemoryManager = EpisodicMemoryManager()

    // ─── Web Search ──────────────────────────────────────────

    @Provides
    @Singleton
    fun provideWebSearchManager(): WebSearchManager = WebSearchManager()

    @Provides
    @Singleton
    fun provideWebResultProcessor(searchManager: WebSearchManager): WebResultProcessor =
        WebResultProcessor(searchManager)

    // ─── Voice & Conversation ────────────────────────────────

    @Provides
    @Singleton
    fun provideVoiceEnhancer(app: Application): VoiceEnhancer = VoiceEnhancer(app)

    @Provides
    @Singleton
    fun provideNaturalConversationEngine(app: Application): NaturalConversationEngine =
        NaturalConversationEngine(app)

    // ─── Sensors & IoT ───────────────────────────────────────

    @Provides
    @Singleton
    fun provideSensorManager(app: Application): NexaSensorManager = NexaSensorManager(app)

    @Provides
    @Singleton
    fun provideIoTManager(app: Application): IoTManager = IoTManager(app)

    @Provides
    @Singleton
    fun provideVideoGenerator(app: Application): VideoGenerator = VideoGenerator(app)
}
