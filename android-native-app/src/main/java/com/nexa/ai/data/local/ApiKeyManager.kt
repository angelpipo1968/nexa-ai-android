package com.nexa.ai.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("nexa_keys")

class ApiKeyManager(private val context: Context) {

    companion object {
        val OPENAI_KEY = stringPreferencesKey("openai_api_key")
        val REPLICATE_KEY = stringPreferencesKey("replicate_api_key")
    }

    fun getOpenAiKey(): Flow<String?> = context.dataStore.data.map { it[OPENAI_KEY] }
    fun getReplicateKey(): Flow<String?> = context.dataStore.data.map { it[REPLICATE_KEY] }

    suspend fun saveOpenAiKey(key: String) {
        context.dataStore.edit { it[OPENAI_KEY] = key }
    }

    suspend fun saveReplicateKey(key: String) {
        context.dataStore.edit { it[REPLICATE_KEY] = key }
    }
}
