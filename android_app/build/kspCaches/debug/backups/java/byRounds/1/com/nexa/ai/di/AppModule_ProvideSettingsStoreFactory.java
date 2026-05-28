package com.nexa.ai.di;

import android.content.Context;
import com.nexa.ai.data.SettingsStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class AppModule_ProvideSettingsStoreFactory implements Factory<SettingsStore> {
  private final Provider<Context> contextProvider;

  public AppModule_ProvideSettingsStoreFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SettingsStore get() {
    return provideSettingsStore(contextProvider.get());
  }

  public static AppModule_ProvideSettingsStoreFactory create(Provider<Context> contextProvider) {
    return new AppModule_ProvideSettingsStoreFactory(contextProvider);
  }

  public static SettingsStore provideSettingsStore(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideSettingsStore(context));
  }
}
