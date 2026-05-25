package com.nexa.ai.di

import android.content.Context
import androidx.room.Room
import com.nexa.ai.data.local.NexaDatabase
import com.nexa.ai.data.local.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NexaDatabase {
        // En una app real, usaríamos Android Keystore para generar/guardar la clave.
        // Aquí usaremos una clave en tiempo de ejecución o guardada en SharedPreferences.
        // Por simplicidad en la migración, pasaremos una clave generada/guardada en DataStore
        // pero dado que es sincrónico el builder, usamos una clave del shared preferences.
        val prefs = context.getSharedPreferences("nexa_secure_prefs", Context.MODE_PRIVATE)
        var key = prefs.getString("db_key", null)
        if (key == null) {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            key = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            prefs.edit().putString("db_key", key).apply()
        }

        val passphrase = key.toByteArray()
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            NexaDatabase::class.java,
            "nexa_database"
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .openHelperFactory(factory)
            .build()
    }

    @Provides
    fun provideSessionDao(database: NexaDatabase): SessionDao {
        return database.sessionDao()
    }
}
