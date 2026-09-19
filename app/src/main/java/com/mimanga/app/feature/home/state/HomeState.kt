package com.mimanga.app.feature.home.state

import com.mimanga.app.domain.model.ChapterProgress
import com.mimanga.app.domain.model.HomeRow
import com.mimanga.app.domain.model.Manga

/**
 * Главная: строки с популярным, историей чтения и личными рекомендациями.
 *
 * Каталог с поиском и фильтрами живёт на своей вкладке — здесь только то, что
 * можно открыть, ничего не набирая.
 */
data class HomeState(
    val rows: List<HomeRow> = emptyList(),
    /** Где человек остановился в каждом тайтле истории (ключ — manga_key). */
    val progress: Map<String, ChapterProgress> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    /**
     * Счётчик изменений содержимого: растёт, только когда строки главной
     * действительно стали другими. Экран по нему перематывает список наверх —
     * после чтения главы наверху появляется «Продолжить чтение» с этим
     * тайтлом, и показывать его надо, а не оставлять человека на той высоте,
     * где он был до чтения. Если ничего не изменилось, отметка та же, и
     * положение списка сохраняется.
     */
    val contentRevision: Int = 0,
) {
    fun progressOf(manga: Manga): ChapterProgress? = progress[manga.actionKey]

    /** Слепок содержимого: по нему видно, изменилось ли что-то на главной. */
    fun contentKey(): List<String> =
        rows.map { row -> row.key + ":" + row.results.joinToString(",") { it.actionKey } }
}
