package com.mimanga.app.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Токен аккаунта.
 *
 * Лежит в DataStore, чтобы вход переживал перезапуск, и держится копией в
 * памяти: заголовок Authorization подставляется на каждый запрос синхронно, и
 * ходить за токеном в файл каждый раз незачем.
 */
@Singleton
class AuthStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow<String?>(null)

    /** Текущий токен: null — пользователь не вошёл. */
    val token: StateFlow<String?> = state.asStateFlow()

    init {
        scope.launch {
            dataStore.data.collect { prefs ->
                state.value = prefs[TOKEN_KEY]?.takeIf { it.isNotBlank() }
            }
        }
    }

    fun current(): String? = state.value

    suspend fun save(token: String) {
        state.value = token
        dataStore.edit { prefs -> prefs[TOKEN_KEY] = token }
    }

    suspend fun clear() {
        state.value = null
        dataStore.edit { prefs -> prefs.remove(TOKEN_KEY) }
    }
}
