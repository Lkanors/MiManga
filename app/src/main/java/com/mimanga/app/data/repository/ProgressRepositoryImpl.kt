package com.mimanga.app.data.repository

import com.mimanga.app.data.local.ProgressStore
import com.mimanga.app.data.remote.AuthStore
import com.mimanga.app.data.remote.MangaServerApi
import com.mimanga.app.domain.model.ChapterProgress
import com.mimanga.app.domain.model.Manga
import com.mimanga.app.domain.model.MangaProgress
import com.mimanga.app.domain.repository.ProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressRepositoryImpl @Inject constructor(
    private val api: MangaServerApi,
    private val store: ProgressStore,
    private val authStore: AuthStore,
) : ProgressRepository {

    private val isGuest: Boolean get() = authStore.token.value.isNullOrBlank()

    /**
     * Своя область для записи «на выходе»: когда закрывают читалку, её
     * ViewModel умирает вместе со своими корутинами, и последнее место чтения
     * не успевало сохраниться.
     */
    private val detached = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun of(mangaKey: String): MangaProgress {
        if (mangaKey.isBlank()) return MangaProgress()
        if (isGuest) return local(mangaKey)
        return runCatching { api.getProgress(mangaKey) }
            .getOrElse { error ->
                // Сеть отвалилась — показываем то, что успели запомнить на месте,
                // это лучше пустого списка глав без полосок.
                Timber.w(error, "Прогресс с сервера не пришёл")
                local(mangaKey)
            }
    }

    override suspend fun save(progress: ChapterProgress) {
        // По времени выбирается запись, к которой ведёт «продолжить»; без него
        // все записи выглядели одинаково свежими, и кнопка уводила на первую
        // открытую когда-либо главу. Своё время записи важнее текущего: место
        // могло быть посчитано раньше (см. ReaderViewModel.onCleared) и не
        // должно притворяться самым свежим.
        val stamped = if (progress.updatedAt.isNullOrBlank()) {
            progress.copy(updatedAt = Instant.now().toString())
        } else progress
        // Локальная копия пишется всегда: по ней работают полоски сразу, без
        // ожидания ответа сервера, и она же остаётся при потере сети.
        store.save(stamped)
        if (isGuest) return
        runCatching { api.saveProgress(stamped) }
            .onFailure { Timber.w(it, "Место чтения не ушло на сервер") }
    }

    override fun saveDetached(progress: ChapterProgress) {
        detached.launch { runCatching { save(progress) } }
    }

    override suspend fun history(limit: Int): Pair<List<Manga>, Map<String, ChapterProgress>> {
        if (!isGuest) {
            val response = runCatching { api.getHistory(limit) }.getOrNull()
            if (response != null) return response.results to response.progress
        }
        val keys = store.history(limit)
        if (keys.isEmpty()) return emptyList<Manga>() to emptyMap()
        val cards = runCatching { api.getCards(keys).results }.getOrDefault(emptyList())
        val progress = keys.associateWith { key ->
            store.chapters(key).maxByOrNull { it.updatedAt.orEmpty() }
        }.filterValues { it != null }.mapValues { it.value!! }
        // Порядок задаёт история, а не сервер: карточки приходят в порядке ключей.
        return cards to progress
    }

    override suspend fun forget(mangaKey: String) {
        store.forget(mangaKey)
        if (!isGuest) runCatching { api.deleteHistory(mangaKey) }
    }

    override suspend fun syncGuestProgress() {
        if (isGuest) return
        val pending = runCatching { store.take() }.getOrDefault(emptyList())
        pending.forEach { progress ->
            runCatching { api.saveProgress(progress) }
                .onFailure { Timber.w(it, "Не удалось перенести прогресс гостя") }
        }
    }

    private suspend fun local(mangaKey: String): MangaProgress {
        // От свежих к старым — тем же порядком, что отдаёт сервер, чтобы
        // «продолжить» вело в одно и то же место у гостя и у вошедшего.
        val chapters = store.chapters(mangaKey).sortedByDescending { it.updatedAt.orEmpty() }
        return MangaProgress(mangaKey = mangaKey, chapters = chapters,
                             last = chapters.firstOrNull())
    }
}
