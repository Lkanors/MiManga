package com.mimanga.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Единственный экземпляр DataStore для всего приложения.
 * Все места должны использовать этот delegate, чтобы избежать
 * "multiple DataStores active for the same file".
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
