package com.mimanga.app.feature.details.state

import com.mimanga.app.domain.model.Chapter
import com.mimanga.app.domain.model.ChapterProgress
import com.mimanga.app.domain.model.Comment
import com.mimanga.app.domain.model.LibraryStatus
import com.mimanga.app.domain.model.Manga
import com.mimanga.app.domain.model.MangaSource
import com.mimanga.app.domain.model.RatingDetails

data class DetailsState(
    val manga: Manga? = null,
    val selectedSource: MangaSource? = null,
    val chapters: List<Chapter> = emptyList(),
    val similar: List<Manga> = emptyList(),
    val comments: List<Comment> = emptyList(),
    val commentsTotal: Int = 0,
    val isLoadingDetail: Boolean = false,
    val isLoadingChapters: Boolean = false,
    val isLoadingComments: Boolean = false,
    val showForeignSources: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userId: Int? = null,
    val libraryStatus: LibraryStatus? = null,
    val isFavorite: Boolean = false,
    val userRating: Float? = null,
    val rating: RatingDetails? = null,
    val error: String? = null,
    val notice: String? = null,
    /**
     * Прогресс по главам ВЫБРАННОГО источника: ключ — «источник:ссылка».
     * У каждого источника он свой, потому что главы у сайтов нарезаны
     * по-разному и «глава 10» у них — разные куски текста.
     */
    val progress: Map<String, ChapterProgress> = emptyMap(),
    /** Место, с которого продолжить чтение на выбранном источнике. */
    val lastRead: ChapterProgress? = null,
) {
    /** Прогресс конкретной главы (или null, если её не открывали). */
    fun progressOf(chapter: Chapter): ChapterProgress? =
        progress[chapter.sourceId + ":" + chapter.url]

    /** Источники для отображения (русские или все) */
    val availableSources: List<MangaSource>
        get() {
            val m = manga ?: return emptyList()
            return if (showForeignSources) m.enSources else m.ruSources
        }
}
