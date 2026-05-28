package com.nexa.ai.domain.usecase;

import com.nexa.ai.data.SessionStore;
import com.nexa.ai.viewmodel.AuthManager;
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
public final class AuthUseCase_Factory implements Factory<AuthUseCase> {
  private final Provider<AuthManager> authManagerProvider;

  private final Provider<SessionStore> sessionStoreProvider;

  public AuthUseCase_Factory(Provider<AuthManager> authManagerProvider,
      Provider<SessionStore> sessionStoreProvider) {
    this.authManagerProvider = authManagerProvider;
    this.sessionStoreProvider = sessionStoreProvider;
  }

  @Override
  public AuthUseCase get() {
    return newInstance(authManagerProvider.get(), sessionStoreProvider.get());
  }

  public static AuthUseCase_Factory create(Provider<AuthManager> authManagerProvider,
      Provider<SessionStore> sessionStoreProvider) {
    return new AuthUseCase_Factory(authManagerProvider, sessionStoreProvider);
  }

  public static AuthUseCase newInstance(AuthManager authManager, SessionStore sessionStore) {
    return new AuthUseCase(authManager, sessionStore);
  }
}
