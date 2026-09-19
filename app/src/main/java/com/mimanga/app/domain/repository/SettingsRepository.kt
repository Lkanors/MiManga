package com.mimanga.app.domain.repository

import com.mimanga.app.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * Настройки приложения.
 *
 * Адреса сервера здесь нет намеренно: он зашит в сборку (BuildConfig.SERVER_URL),
 * и менять его из приложения нельзя.
 */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}
