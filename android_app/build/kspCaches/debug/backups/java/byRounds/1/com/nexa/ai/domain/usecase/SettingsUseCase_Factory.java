package com.nexa.ai.domain.usecase;

import com.nexa.ai.data.SettingsStore;
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
public final class SettingsUseCase_Factory implements Factory<SettingsUseCase> {
  private final Provider<SettingsStore> settingsStoreProvider;

  public SettingsUseCase_Factory(Provider<SettingsStore> settingsStoreProvider) {
    this.settingsStoreProvider = settingsStoreProvider;
  }

  @Override
  public SettingsUseCase get() {
    return newInstance(settingsStoreProvider.get());
  }

  public static SettingsUseCase_Factory create(Provider<SettingsStore> settingsStoreProvider) {
    return new SettingsUseCase_Factory(settingsStoreProvider);
  }

  public static SettingsUseCase newInstance(SettingsStore settingsStore) {
    return new SettingsUseCase(settingsStore);
  }
}
