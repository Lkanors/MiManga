package com.mimanga.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mimanga.app.domain.model.ChapterProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * История чтения и места остановки ДЛЯ ГОСТЯ — в памяти телефона.
 *
 * Пользователь без аккаунта тоже должен возвращаться туда, где закончил, но
 * отправлять его чтение на сервер некуда: записи там привязаны к аккаунту.
 * Поэтому у гостя всё лежит в DataStore: по одной записи на тайтл (JSON со
 * всеми главами) плюс список тайтлов в порядке последнего чтения.
 *
 * При входе в аккаунт накопленное не пропадает: [take] отдаёт всё разом, и
 * репозиторий переносит его на сервер.
 */
@Singleton
class ProgressStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun mangaKeyOf(mangaKey: String) = stringPreferencesKey("progress_$mangaKey")

    private val historyKey = stringPreferencesKey("progress_history")

    /** Все записи по тайтлу. */
    suspend fun chapters(mangaKey: String): List<ChapterProgress> {
        val raw = context.settingsDataStore.data.first()[mangaKeyOf(mangaKey)] ?: return emptyList()
        return decode(raw)
    }

    /** Ключи тайтлов в порядке последнего чтения — история для главной. */
    suspend fun history(limit: Int = 50): List<String> {
        val raw = context.settingsDataStore.data.first()[historyKey].orEmpty()
        return raw.split('\n').filter { it.isNotBlank() }.take(limit)
    }

    suspend fun save(progress: ChapterProgress) {
        val key = mangaKeyOf(progress.mangaKey)
        context.settingsDataStore.edit { prefs ->
            val current = decode(prefs[key].orEmpty())
            // Свежая запись — первой: по этому порядку история на главной и
            // кнопка «продолжить» находят последнее место чтения.
            val updated = listOf(progress) + current.filterNot {
                it.sourceId == progress.sourceId && it.chapterKey == progress.chapterKey
            }
            prefs[key] = json.encodeToString(ListSerializer(ChapterProgress.serializer()), updated)

            val history = prefs[historyKey].orEmpty().split('\n').filter { it.isNotBlank() }
            prefs[historyKey] = (listOf(progress.mangaKey) + history.filterNot { it == progress.mangaKey })
                .take(100).joinToString("\n")
        }
    }

    suspend fun forget(mangaKey: String) {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(mangaKeyOf(mangaKey))
            prefs[historyKey] = prefs[historyKey].orEmpty().split('\n')
                .filter { it.isNotBlank() && it != mangaKey }.joinToString("\n")
        }
    }

    /** Всё накопленное гостем — чтобы перенести на сервер после входа. */
    suspend fun take(): List<ChapterProgress> {
        val keys = history(limit = 100)
        return keys.flatMap { chapters(it) }
    }

    private fun decode(raw: String): List<ChapterProgress> {
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(ChapterProgress.serializer()), raw)
        } catch (error: Exception) {
            Timber.w(error, "Битая запись прогресса — начинаем с чистого листа")
            emptyList()
        }
    }
}
