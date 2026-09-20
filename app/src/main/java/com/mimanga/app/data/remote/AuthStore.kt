package com.mimanga.app.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mimanga.app.core.network.ServerImages
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
                hold(prefs[TOKEN_KEY]?.takeIf { it.isNotBlank() })
            }
        }
    }

    /**
     * Запоминает токен — и сообщает о смене адресам картинок.
     *
     * Вход и выход меняют не только то, что отвечает сервер, но и то, что
     * человеку положено видеть. Картинки идут мимо этого класса, через свой
     * кэш, и без метки в адресе показывали бы прежнее, пока кэш не вытеснится
     * (см. ServerImages.viewer). Место одно на все пути смены токена, чтобы
     * ни один из них не оказался забыт.
     */
    private fun hold(token: String?) {
        state.value = token
        ServerImages.viewer(token)
    }

    fun current(): String? = state.value

    suspend fun save(token: String) {
        hold(token)
        dataStore.edit { prefs -> prefs[TOKEN_KEY] = token }
    }

    suspend fun clear() {
        hold(null)
        dataStore.edit { prefs -> prefs.remove(TOKEN_KEY) }
    }
}
