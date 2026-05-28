package com.nexa.ai.di;

import com.nexa.ai.data.UpdateChecker;
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
public final class AppModule_ProvideUpdateCheckerFactory implements Factory<UpdateChecker> {
  @Override
  public UpdateChecker get() {
    return provideUpdateChecker();
  }

  public static AppModule_ProvideUpdateCheckerFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static UpdateChecker provideUpdateChecker() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideUpdateChecker());
  }

  private static final class InstanceHolder {
    private static final AppModule_ProvideUpdateCheckerFactory INSTANCE = new AppModule_ProvideUpdateCheckerFactory();
  }
}
