package com.example.resqmesh.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "resq_prefs")

class ResQStorage(private val context: Context) {
    companion object {
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_ROLE = stringPreferencesKey("user_role")
        // We'll store trusted keys as: peer_address -> public_key_base64
        // For simplicity today, we'll store a single current "Partner" key
        val TRUSTED_PARTNER_KEY = stringPreferencesKey("partner_key")
    }

    val userName: Flow<String?> = context.dataStore.data.map { it[USER_NAME] }
    val userRole: Flow<String?> = context.dataStore.data.map { it[USER_ROLE] }
    val partnerKey: Flow<String?> = context.dataStore.data.map { it[TRUSTED_PARTNER_KEY] }

    suspend fun saveProfile(name: String, role: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_NAME] = name
            prefs[USER_ROLE] = role
        }
    }

    suspend fun savePartnerKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[TRUSTED_PARTNER_KEY] = key
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
