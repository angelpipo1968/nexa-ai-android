package com.nexa.ai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nexa.ai.data.local.MessageEntity
import com.nexa.ai.data.local.NexaDatabase
import com.nexa.ai.data.local.SessionEntity
import com.nexa.ai.data.local.SessionWithMessages
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.activeSessionStore: DataStore<Preferences> by preferencesDataStore(name = "nexa_active_session")

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

/**
 * SessionStore now uses Room for session/message storage (no size limits)
 * and DataStore only for the active session ID preference.
 */
class SessionStore(private val context: Context) {

    private val db = NexaDatabase.getInstance(context)
    private val dao = db.sessionDao()

    private val KEY_ACTIVE_ID = stringPreferencesKey("active_session_id")

    /** Observe all sessions with their messages. */
    val sessions: Flow<List<PersistedSession>> = kotlinx.coroutines.flow.flow {
        val data = dao.getAllSessions()
        emit(data.map { it.toPersisted() })
    }

    /** Observe the active session ID. */
    val activeSessionId: Flow<String?> = context.activeSessionStore.data.map { prefs ->
        prefs[KEY_ACTIVE_ID]
    }

    /** Save all sessions and the active session ID. Used for bulk updates. */
    suspend fun save(sessions: List<PersistedSession>, activeId: String?) {
        // Transacción Room: todo o nada — previene FOREIGN KEY constraint failed
        db.withTransaction {
            // 1. Limpiar mensajes PRIMERO (por si CASCADE falla o hay timing issues)
            dao.deleteAllMessages()

            // 2. Limpiar sesiones
            dao.deleteAll()

            // 3. Re-insertar todo de forma segura
            for (session in sessions) {
                dao.insertSession(
                    SessionEntity(
                        id = session.id,
                        title = session.title,
                        createdAt = session.createdAt,
                        updatedAt = session.updatedAt
                    )
                )
                if (session.messages.isNotEmpty()) {
                    dao.insertMessages(
                        session.messages.map { msg ->
                            MessageEntity(
                                sessionId = session.id,
                                messageId = msg.id,
                                role = msg.role,
                                content = msg.content
                            )
                        }
                    )
                }
            }
        }

        // Guardar active session ID fuera de la transacción Room
        if (activeId != null) {
            context.activeSessionStore.edit { prefs ->
                prefs[KEY_ACTIVE_ID] = activeId
            }
        }
    }

    /** Clear all data (sessions, messages, active ID). */
    suspend fun clear() {
        dao.deleteAll()
        context.activeSessionStore.edit { it.clear() }
    }
}

/** Convert Room entity to domain model. */
private fun SessionWithMessages.toPersisted(): PersistedSession {
    return PersistedSession(
        id = session.id,
        title = session.title,
        messages = messages.map { msg ->
            PersistedMessage(
                id = msg.messageId,
                role = msg.role,
                content = msg.content
            )
        },
        createdAt = session.createdAt,
        updatedAt = session.updatedAt
    )
}
