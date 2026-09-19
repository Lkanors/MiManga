package com.mimanga.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Источник манги (один из сайтов).
 */
@Serializable
data class MangaSource(
    @SerialName("source_id") val sourceId: String,
    @SerialName("source_name") val sourceName: String? = null,
    val name: String? = null, // New API uses 'name' instead of 'source_name'
    val url: String? = null,
    @SerialName("is_foreign") val isForeign: Boolean = false,
    val language: String = "ru",
) {
    val displayName: String get() = sourceName ?: name ?: sourceId
}

/**
 * Агрегированная манга с несколькими источниками.
 * Supports both old (search) and new (catalog) API formats.
 */
@Serializable
data class Manga(
    val id: String, // Can be aggregated ID or database ID
    /**
     * Постоянный ключ карточки на сервере. К нему привязаны оценки,
     * комментарии и списки: числовой id после пересборки каталога меняется,
     * ключ — нет. Все действия аккаунта отправляются именно по ключу.
     */
    @SerialName("manga_key") val mangaKey: String = "",
    val title: String,
    @SerialName("alt_titles") val altTitles: List<String> = emptyList(),
    @SerialName("cover_url") val coverUrl: String = "",
    @SerialName("cover_urls") val coverUrls: Map<String, String> = emptyMap(),
    val description: String = "",
    val authors: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val status: String = "unknown",
    /** Тип издания: manga / manhwa / manhua / oel / comics / rumanga. */
    val kind: String = "",
    /** Готовая русская подпись типа («Манхва»); пусто — тип неизвестен. */
    @SerialName("kind_title") val kindTitle: String = "",
    val rating: Float? = null,
    /** Сколько человек оценило: и на сайтах-источниках, и в приложении. */
    @SerialName("rating_count") val ratingCount: Int = 0,
    val views: Int = 0,
    /** Число глав — максимум по источникам. */
    @SerialName("chapters_count") val chaptersCount: Int = 0,
    val year: Int? = null,
    @SerialName("source_count") val sourceCount: Int? = null,
    /**
     * Материал 18+. Сервер такие карточки не отдаёт тем, кто не подтвердил
     * возраст, — пометка нужна взрослому: по ней видно, почему этого тайтла
     * не видят остальные.
     */
    @SerialName("is_adult") val isAdult: Boolean = false,
    /** Пометка пользователя: читаю / в планах / заброшено / прочитано. */
    @SerialName("user_status") val userStatus: String? = null,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("user_rating") val userRating: Float? = null,
    /** Оценка ВЛАДЕЛЬЦА профиля, когда карточка показана в чужом профиле. */
    @SerialName("their_rating") val theirRating: Float? = null,
    @SerialName("rating_details") val ratingDetails: RatingDetails? = null,
    @SerialName("tags_source") val tagsSource: String? = null,
    val sources: List<MangaSource> = emptyList(),
    @SerialName("first_seen") val firstSeen: String? = null,
    @SerialName("last_updated") val lastUpdated: String? = null,
) {
    /** Ключ для действий аккаунта: ключ карточки, а при его отсутствии — id. */
    val actionKey: String get() = mangaKey.ifBlank { id }

    val libraryStatus: LibraryStatus? get() = LibraryStatus.from(userStatus)

    /** Русские источники */
    val ruSources: List<MangaSource> get() = sources.filter { !it.isForeign }

    /** Иностранные источники */
    val enSources: List<MangaSource> get() = sources.filter { it.isForeign }

    /** Есть ли русские источники */
    val hasRuSources: Boolean get() = ruSources.isNotEmpty()

    /** Есть ли иностранные источники */
    val hasEnSources: Boolean get() = enSources.isNotEmpty()
    
    /** Database ID as int (for new API) */
    val dbId: Int? get() = id.toIntOrNull()
    
    /** Is this from database catalog? */
    val isFromCatalog: Boolean get() = dbId != null
}
