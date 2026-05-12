package com.nexa.ai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionStore: DataStore<Preferences> by preferencesDataStore(name = "nexa_sessions")

data class PersistedSession(
    val id: String,
    val title: String,
    val messages: List<PersistedMessage>,
    val createdAt: Long,
    val updatedAt: Long
)

data class PersistedMessage(
    val id: String,
    val role: String,
    val content: String
)

class SessionStore(private val context: Context) {

    private val gson = Gson()
    private val KEY_SESSIONS = stringPreferencesKey("sessions_json")
    private val KEY_ACTIVE_ID = stringPreferencesKey("active_session_id")

    val sessions: Flow<List<PersistedSession>> = context.sessionStore.data.map { prefs ->
        val json = prefs[KEY_SESSIONS] ?: "[]"
        val type = object : TypeToken<List<PersistedSession>>() {}.type
        try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    val activeSessionId: Flow<String?> = context.sessionStore.data.map { prefs ->
        prefs[KEY_ACTIVE_ID]
    }

    suspend fun save(sessions: List<PersistedSession>, activeId: String?) {
        context.sessionStore.edit { prefs ->
            prefs[KEY_SESSIONS] = gson.toJson(sessions)
            if (activeId != null) {
                prefs[KEY_ACTIVE_ID] = activeId
            }
        }
    }

    suspend fun clear() {
        context.sessionStore.edit { it.clear() }
    }
}
