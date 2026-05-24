package com.nexa.ai.domain.usecase;

import com.nexa.ai.viewmodel.SpeechManager;
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
public final class VoiceUseCase_Factory implements Factory<VoiceUseCase> {
  private final Provider<SpeechManager> speechManagerProvider;

  public VoiceUseCase_Factory(Provider<SpeechManager> speechManagerProvider) {
    this.speechManagerProvider = speechManagerProvider;
  }

  @Override
  public VoiceUseCase get() {
    return newInstance(speechManagerProvider.get());
  }

  public static VoiceUseCase_Factory create(Provider<SpeechManager> speechManagerProvider) {
    return new VoiceUseCase_Factory(speechManagerProvider);
  }

  public static VoiceUseCase newInstance(SpeechManager speechManager) {
    return new VoiceUseCase(speechManager);
  }
}
