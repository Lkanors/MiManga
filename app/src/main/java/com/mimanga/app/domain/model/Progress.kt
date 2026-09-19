package com.mimanga.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Место, на котором человек остановился в главе.
 *
 * Хранится у КАЖДОГО ИСТОЧНИКА своё: у сайтов разная нарезка на главы, и
 * прогресс remanga нельзя выдавать за прогресс mangalib.
 *
 * У зарегистрированного пользователя это живёт на сервере (переустановка
 * приложения ничего не теряет), у гостя — в памяти телефона.
 */
@Serializable
data class ChapterProgress(
    @SerialName("manga_key") val mangaKey: String = "",
    @SerialName("source_id") val sourceId: String = "",
    @SerialName("chapter_key") val chapterKey: String = "",
    @SerialName("chapter_title") val chapterTitle: String = "",
    @SerialName("chapter_number") val chapterNumber: Float = 0f,
    /** Место главы в списке источника: с ним читалка открывается сразу на ней. */
    @SerialName("chapter_index") val chapterIndex: Int = 0,
    val page: Int = 0,
    val pages: Int = 0,
    /** Доля прочитанного, 0..1 — ею рисуется полоска под главой. */
    val position: Float = 0f,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    /** Начата, но не дочитана: полоска видна, цвет главы обычный. */
    val isStarted: Boolean get() = position > 0f && !isRead

    companion object {
        /** С какой доли глава считается прочитанной (столько же на сервере). */
        const val DONE = 0.95f

        fun key(sourceId: String, chapterKey: String) = "$sourceId|$chapterKey"
    }
}

/** Прогресс по одному тайтлу: полоски под главами и «продолжить читать». */
@Serializable
data class MangaProgress(
    @SerialName("manga_key") val mangaKey: String = "",
    val chapters: List<ChapterProgress> = emptyList(),
    /** Последнее место чтения — куда ведёт кнопка «продолжить». */
    val last: ChapterProgress? = null,
) {
    /** Прогресс по ключу главы для одного источника. */
    fun byChapter(sourceId: String): Map<String, ChapterProgress> =
        chapters.filter { it.sourceId == sourceId }.associateBy { it.chapterKey }

    /**
     * Куда ведёт «продолжить» на этом источнике — САМАЯ СВЕЖАЯ запись.
     *
     * Выбирается по времени, а не по месту в списке: порядок у сервера и у
     * памяти телефона разный, и раньше кнопка уводила на первую открытую
     * когда-либо главу (обычно — на первую главу тайтла, на нулевой странице).
     */
    fun lastOf(sourceId: String): ChapterProgress? =
        chapters.filter { it.sourceId == sourceId }.maxByOrNull { it.updatedAt.orEmpty() }
            ?: last?.takeIf { it.sourceId == sourceId }
}
