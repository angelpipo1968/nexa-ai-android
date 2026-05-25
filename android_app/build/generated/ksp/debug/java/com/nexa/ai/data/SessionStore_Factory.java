package com.nexa.ai.data;

import android.content.Context;
import com.nexa.ai.data.local.NexaDatabase;
import com.nexa.ai.data.local.SessionDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class SessionStore_Factory implements Factory<SessionStore> {
  private final Provider<Context> contextProvider;

  private final Provider<SessionDao> daoProvider;

  private final Provider<NexaDatabase> dbProvider;

  public SessionStore_Factory(Provider<Context> contextProvider, Provider<SessionDao> daoProvider,
      Provider<NexaDatabase> dbProvider) {
    this.contextProvider = contextProvider;
    this.daoProvider = daoProvider;
    this.dbProvider = dbProvider;
  }

  @Override
  public SessionStore get() {
    return newInstance(contextProvider.get(), daoProvider.get(), dbProvider.get());
  }

  public static SessionStore_Factory create(Provider<Context> contextProvider,
      Provider<SessionDao> daoProvider, Provider<NexaDatabase> dbProvider) {
    return new SessionStore_Factory(contextProvider, daoProvider, dbProvider);
  }

  public static SessionStore newInstance(Context context, SessionDao dao, NexaDatabase db) {
    return new SessionStore(context, dao, db);
  }
}
