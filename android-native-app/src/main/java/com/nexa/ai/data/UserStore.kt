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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.userStore: DataStore<Preferences> by preferencesDataStore(name = "nexa_user")

data class PersistedUser(
    val email: String,
    val displayName: String
)

data class PersistedCredential(
    val email: String,
    val name: String,
    val passwordHash: String,
    val salt: String = ""
)

class UserStore(private val context: Context) {

    private val gson = Gson()
    private val KEY_USER = stringPreferencesKey("current_user")
    private val KEY_CREDENTIALS = stringPreferencesKey("credentials")

    val currentUser: Flow<PersistedUser?> = context.userStore.data.map { prefs ->
        val json = prefs[KEY_USER] ?: return@map null
        try {
            gson.fromJson(json, PersistedUser::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private val credentials: Flow<List<PersistedCredential>> = context.userStore.data.map { prefs ->
        val json = prefs[KEY_CREDENTIALS] ?: "[]"
        val type = object : TypeToken<List<PersistedCredential>>() {}.type
        try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Generate a random salt */
    private fun generateSalt(): String {
        val salt = ByteArray(16)
        java.security.SecureRandom().nextBytes(salt)
        return salt.joinToString("") { "%02x".format(it) }
    }

    /** Salted SHA-256 hash for local storage */
    private fun hashPassword(password: String, salt: String): String {
        val saltedPassword = salt + password
        val bytes = saltedPassword.toByteArray()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    suspend fun saveUser(user: PersistedUser) {
        context.userStore.edit { prefs ->
            prefs[KEY_USER] = gson.toJson(user)
        }
    }

    suspend fun clearUser() {
        context.userStore.edit { prefs ->
            prefs.remove(KEY_USER)
        }
    }

    /** Register a new credential. Returns true if success, false if email exists. */
    suspend fun register(name: String, email: String, password: String): Boolean {
        val existing = getCredentials()
        if (existing.any { it.email.equals(email, ignoreCase = true) }) return false

        val salt = generateSalt()
        val hash = hashPassword(password, salt)
        val updated = existing + PersistedCredential(email, name, hash, salt)
        saveCredentials(updated)
        return true
    }

    /** Validate login credentials. Returns the user's display name or null. */
    suspend fun validateLogin(email: String, password: String): String? {
        val existing = getCredentials()
        val cred = existing.find {
            it.email.equals(email, ignoreCase = true) && it.passwordHash == hashPassword(password, it.salt)
        }
        return cred?.name
    }

    /** Auto-register: if email not found, create account. Returns display name. */
    suspend fun loginOrAutoRegister(email: String, password: String): String {
        val existing = getCredentials()
        val cred = existing.find {
            it.email.equals(email, ignoreCase = true) && it.passwordHash == hashPassword(password, it.salt)
        }
        if (cred != null) return cred.name

        // Auto-register
        val name = email.substringBefore("@")
        val salt = generateSalt()
        val hash = hashPassword(password, salt)
        val updated = existing + PersistedCredential(email, name, hash, salt)
        saveCredentials(updated)
        return name
    }

    private suspend fun getCredentials(): List<PersistedCredential> {
        return context.userStore.data.map { prefs ->
            val json = prefs[KEY_CREDENTIALS] ?: "[]"
            val type = object : TypeToken<List<PersistedCredential>>() {}.type
            try {
                gson.fromJson<List<PersistedCredential>>(json, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }.first()
    }

    private suspend fun saveCredentials(creds: List<PersistedCredential>) {
        context.userStore.edit { prefs ->
            prefs[KEY_CREDENTIALS] = gson.toJson(creds)
        }
    }

    suspend fun clearAll() {
        context.userStore.edit { it.clear() }
    }
}
