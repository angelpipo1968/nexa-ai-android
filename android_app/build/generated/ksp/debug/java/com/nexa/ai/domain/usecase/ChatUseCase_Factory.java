package com.nexa.ai.domain.usecase;

import com.nexa.ai.data.NexaRepository;
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
public final class ChatUseCase_Factory implements Factory<ChatUseCase> {
  private final Provider<NexaRepository> repositoryProvider;

  public ChatUseCase_Factory(Provider<NexaRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public ChatUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static ChatUseCase_Factory create(Provider<NexaRepository> repositoryProvider) {
    return new ChatUseCase_Factory(repositoryProvider);
  }

  public static ChatUseCase newInstance(NexaRepository repository) {
    return new ChatUseCase(repository);
  }
}
