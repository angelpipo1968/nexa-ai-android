package com.nexa.ai.di;

import android.app.Application;
import com.nexa.ai.viewmodel.AuthManager;
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
public final class AppModule_ProvideAuthManagerFactory implements Factory<AuthManager> {
  private final Provider<Application> applicationProvider;

  public AppModule_ProvideAuthManagerFactory(Provider<Application> applicationProvider) {
    this.applicationProvider = applicationProvider;
  }

  @Override
  public AuthManager get() {
    return provideAuthManager(applicationProvider.get());
  }

  public static AppModule_ProvideAuthManagerFactory create(
      Provider<Application> applicationProvider) {
    return new AppModule_ProvideAuthManagerFactory(applicationProvider);
  }

  public static AuthManager provideAuthManager(Application application) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideAuthManager(application));
  }
}
