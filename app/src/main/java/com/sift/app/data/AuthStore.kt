package com.sift.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "sift")
private val TOKEN_KEY = stringPreferencesKey("sift_token")

/**
 * JWT storage. Mirrors the extension's `chrome.storage.local` key
 * `sift_token`. Token is held in memory ([tokenFlow]) for the OkHttp
 * auth interceptor and persisted in DataStore across launches.
 * Never logged — the logging interceptor redacts Authorization.
 */
class AuthStore(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tokenFlow = MutableStateFlow<String?>(null)
    val tokenFlow: StateFlow<String?> = _tokenFlow.asStateFlow()

    init {
        scope.launch {
            _tokenFlow.value = context.dataStore.data.map { it[TOKEN_KEY] }.first()
        }
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[TOKEN_KEY] = token }
        _tokenFlow.value = token
    }

    suspend fun clear() {
        context.dataStore.edit { it.remove(TOKEN_KEY) }
        _tokenFlow.value = null
    }

    fun currentToken(): String? = _tokenFlow.value
}
