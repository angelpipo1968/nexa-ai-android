package com.nexa.ai.di;

import com.nexa.ai.data.NexaRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class AppModule_ProvideNexaRepositoryFactory implements Factory<NexaRepository> {
  @Override
  public NexaRepository get() {
    return provideNexaRepository();
  }

  public static AppModule_ProvideNexaRepositoryFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static NexaRepository provideNexaRepository() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideNexaRepository());
  }

  private static final class InstanceHolder {
    private static final AppModule_ProvideNexaRepositoryFactory INSTANCE = new AppModule_ProvideNexaRepositoryFactory();
  }
}
