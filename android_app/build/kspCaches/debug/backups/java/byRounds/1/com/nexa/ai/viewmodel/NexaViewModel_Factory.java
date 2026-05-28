package com.nexa.ai.viewmodel;

import android.app.Application;
import com.nexa.ai.data.SessionStore;
import com.nexa.ai.data.UpdateChecker;
import com.nexa.ai.domain.usecase.AuthUseCase;
import com.nexa.ai.domain.usecase.ChatUseCase;
import com.nexa.ai.domain.usecase.SettingsUseCase;
import com.nexa.ai.domain.usecase.VoiceUseCase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class NexaViewModel_Factory implements Factory<NexaViewModel> {
  private final Provider<Application> applicationProvider;

  private final Provider<VoiceUseCase> voiceUseCaseProvider;

  private final Provider<AuthUseCase> authUseCaseProvider;

  private final Provider<ChatUseCase> chatUseCaseProvider;

  private final Provider<UpdateChecker> updateCheckerProvider;

  private final Provider<SessionStore> sessionStoreProvider;

  private final Provider<SettingsUseCase> settingsUseCaseProvider;

  public NexaViewModel_Factory(Provider<Application> applicationProvider,
      Provider<VoiceUseCase> voiceUseCaseProvider, Provider<AuthUseCase> authUseCaseProvider,
      Provider<ChatUseCase> chatUseCaseProvider, Provider<UpdateChecker> updateCheckerProvider,
      Provider<SessionStore> sessionStoreProvider,
      Provider<SettingsUseCase> settingsUseCaseProvider) {
    this.applicationProvider = applicationProvider;
    this.voiceUseCaseProvider = voiceUseCaseProvider;
    this.authUseCaseProvider = authUseCaseProvider;
    this.chatUseCaseProvider = chatUseCaseProvider;
    this.updateCheckerProvider = updateCheckerProvider;
    this.sessionStoreProvider = sessionStoreProvider;
    this.settingsUseCaseProvider = settingsUseCaseProvider;
  }

  @Override
  public NexaViewModel get() {
    return newInstance(applicationProvider.get(), voiceUseCaseProvider.get(), authUseCaseProvider.get(), chatUseCaseProvider.get(), updateCheckerProvider.get(), sessionStoreProvider.get(), settingsUseCaseProvider.get());
  }

  public static NexaViewModel_Factory create(Provider<Application> applicationProvider,
      Provider<VoiceUseCase> voiceUseCaseProvider, Provider<AuthUseCase> authUseCaseProvider,
      Provider<ChatUseCase> chatUseCaseProvider, Provider<UpdateChecker> updateCheckerProvider,
      Provider<SessionStore> sessionStoreProvider,
      Provider<SettingsUseCase> settingsUseCaseProvider) {
    return new NexaViewModel_Factory(applicationProvider, voiceUseCaseProvider, authUseCaseProvider, chatUseCaseProvider, updateCheckerProvider, sessionStoreProvider, settingsUseCaseProvider);
  }

  public static NexaViewModel newInstance(Application application, VoiceUseCase voiceUseCase,
      AuthUseCase authUseCase, ChatUseCase chatUseCase, UpdateChecker updateChecker,
      SessionStore sessionStore, SettingsUseCase settingsUseCase) {
    return new NexaViewModel(application, voiceUseCase, authUseCase, chatUseCase, updateChecker, sessionStore, settingsUseCase);
  }
}
