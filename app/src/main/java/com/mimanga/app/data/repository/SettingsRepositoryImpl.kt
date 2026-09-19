package com.mimanga.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mimanga.app.domain.model.AppSettings
import com.mimanga.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val gridColumns = intPreferencesKey("grid_columns")
        val showRating = booleanPreferencesKey("show_rating_on_card")
        val showChapters = booleanPreferencesKey("show_chapters_on_card")
        val showStatus = booleanPreferencesKey("show_status_badge")
        val compactCards = booleanPreferencesKey("compact_cards")
        val includeForeign = booleanPreferencesKey("include_foreign")
        val defaultSort = stringPreferencesKey("default_sort")
        val homeRowSize = intPreferencesKey("home_row_size")
        val readerDirection = stringPreferencesKey("reader_direction")
        val readerBackground = stringPreferencesKey("reader_background")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val fullscreen = booleanPreferencesKey("fullscreen_reader")
        val prefetch = intPreferencesKey("prefetch_pages")
        val autoMarkRead = booleanPreferencesKey("mark_chapter_read")
        val pageProgress = booleanPreferencesKey("show_page_progress")
        val translatePages = booleanPreferencesKey("translate_pages")
    }

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            themeMode = prefs[Keys.theme] ?: defaults.themeMode,
            gridColumns = prefs[Keys.gridColumns] ?: defaults.gridColumns,
            showRatingOnCard = prefs[Keys.showRating] ?: defaults.showRatingOnCard,
            showChaptersOnCard = prefs[Keys.showChapters] ?: defaults.showChaptersOnCard,
            showStatusBadge = prefs[Keys.showStatus] ?: defaults.showStatusBadge,
            compactCards = prefs[Keys.compactCards] ?: defaults.compactCards,
            includeForeign = prefs[Keys.includeForeign] ?: defaults.includeForeign,
            defaultSort = prefs[Keys.defaultSort] ?: defaults.defaultSort,
            homeRowSize = prefs[Keys.homeRowSize] ?: defaults.homeRowSize,
            readerDirection = prefs[Keys.readerDirection] ?: defaults.readerDirection,
            readerBackground = prefs[Keys.readerBackground] ?: defaults.readerBackground,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: defaults.keepScreenOn,
            fullscreenReader = prefs[Keys.fullscreen] ?: defaults.fullscreenReader,
            prefetchPages = prefs[Keys.prefetch] ?: defaults.prefetchPages,
            markChapterReadAutomatically = prefs[Keys.autoMarkRead] ?: defaults.markChapterReadAutomatically,
            showPageProgress = prefs[Keys.pageProgress] ?: defaults.showPageProgress,
            translatePages = prefs[Keys.translatePages] ?: defaults.translatePages,
        )
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(settings.first())
        dataStore.edit { prefs ->
            prefs[Keys.theme] = updated.themeMode
            prefs[Keys.gridColumns] = updated.gridColumns
            prefs[Keys.showRating] = updated.showRatingOnCard
            prefs[Keys.showChapters] = updated.showChaptersOnCard
            prefs[Keys.showStatus] = updated.showStatusBadge
            prefs[Keys.compactCards] = updated.compactCards
            prefs[Keys.includeForeign] = updated.includeForeign
            prefs[Keys.defaultSort] = updated.defaultSort
            prefs[Keys.homeRowSize] = updated.homeRowSize
            prefs[Keys.readerDirection] = updated.readerDirection
            prefs[Keys.readerBackground] = updated.readerBackground
            prefs[Keys.keepScreenOn] = updated.keepScreenOn
            prefs[Keys.fullscreen] = updated.fullscreenReader
            prefs[Keys.prefetch] = updated.prefetchPages
            prefs[Keys.autoMarkRead] = updated.markChapterReadAutomatically
            prefs[Keys.translatePages] = updated.translatePages
            prefs[Keys.pageProgress] = updated.showPageProgress
        }
    }
}
