package com.nexa.ai.domain.usecase

import com.nexa.ai.data.SettingsStore
import com.nexa.ai.viewmodel.ThemeMode
import com.nexa.ai.viewmodel.AppLanguage
import com.nexa.ai.viewmodel.VoiceType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SettingsUseCase @Inject constructor(
    private val settingsStore: SettingsStore
) {
    val themeMode: Flow<ThemeMode> = settingsStore.themeMode
    val language: Flow<AppLanguage> = settingsStore.language
    val voiceType: Flow<VoiceType> = settingsStore.voiceType
    
    suspend fun setThemeMode(mode: ThemeMode) = settingsStore.setThemeMode(mode)
    suspend fun setLanguage(lang: AppLanguage) = settingsStore.setLanguage(lang)
    suspend fun setVoiceType(type: VoiceType) = settingsStore.setVoiceType(type)
}
