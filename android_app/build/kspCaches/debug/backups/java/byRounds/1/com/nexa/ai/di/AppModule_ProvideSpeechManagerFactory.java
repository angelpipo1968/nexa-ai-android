package com.nexa.ai.di;

import android.app.Application;
import com.nexa.ai.viewmodel.SpeechManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class AppModule_ProvideSpeechManagerFactory implements Factory<SpeechManager> {
  private final Provider<Application> applicationProvider;

  public AppModule_ProvideSpeechManagerFactory(Provider<Application> applicationProvider) {
    this.applicationProvider = applicationProvider;
  }

  @Override
  public SpeechManager get() {
    return provideSpeechManager(applicationProvider.get());
  }

  public static AppModule_ProvideSpeechManagerFactory create(
      Provider<Application> applicationProvider) {
    return new AppModule_ProvideSpeechManagerFactory(applicationProvider);
  }

  public static SpeechManager provideSpeechManager(Application application) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideSpeechManager(application));
  }
}
