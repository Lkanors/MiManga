package com.mimanga.app.domain.repository

import com.mimanga.app.domain.model.ChapterProgress
import com.mimanga.app.domain.model.Manga
import com.mimanga.app.domain.model.MangaProgress

/**
 * Место, где человек остановился читать.
 *
 * Одна и та же работа для двух случаев: у вошедшего в аккаунт всё хранится на
 * сервере (переустановка приложения ничего не теряет), у гостя — в памяти
 * телефона. Экраны об этом не знают и просто спрашивают репозиторий.
 */
interface ProgressRepository {
    /** Прогресс по всем главам тайтла (у каждого источника свой). */
    suspend fun of(mangaKey: String): MangaProgress

    /** Запоминает, докуда дочитана глава. */
    suspend fun save(progress: ChapterProgress)

    /**
     * То же, но не привязано к вызывающему экрану: запись переживает его
     * закрытие. Нужно читалке — место чтения сохраняется в момент выхода.
     */
    fun saveDetached(progress: ChapterProgress)

    /** История чтения для главной: карточки в порядке последнего чтения. */
    suspend fun history(limit: Int = 30): Pair<List<Manga>, Map<String, ChapterProgress>>

    /** Убирает тайтл из истории. */
    suspend fun forget(mangaKey: String)

    /** Переносит на сервер то, что человек прочитал до входа в аккаунт. */
    suspend fun syncGuestProgress()
}
