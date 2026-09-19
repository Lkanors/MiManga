package com.mimanga.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Состояние тайтла у пользователя. Порядок здесь = порядок вкладок на
 * странице аккаунта, `key` совпадает с тем, что понимает сервер.
 */
enum class LibraryStatus(val key: String, val title: String) {
    READING("reading", "Читаю"),
    PLANNED("planned", "В планах"),
    DROPPED("dropped", "Заброшено"),
    COMPLETED("completed", "Прочитано");

    companion object {
        fun from(key: String?): LibraryStatus? = entries.firstOrNull { it.key == key }
    }
}

/** Профиль аккаунта со счётчиками для вкладок. */
@Serializable
data class Account(
    val id: Int,
    val username: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("read_chapters") val readChapters: Int = 0,
    @SerialName("read_titles") val readTitles: Int = 0,
    val favorites: Int = 0,
    val rated: Int = 0,
    val comments: Int = 0,
    val statuses: Map<String, Int> = emptyMap(),
    /** Дата рождения «ГГГГ-ММ-ДД»; пусто — возраст не указан. */
    @SerialName("birth_date") val birthDate: String = "",
    /** Полных лет; null — дата рождения не указана. */
    val age: Int? = null,
    /** С какого возраста сервер показывает 18+ (сейчас 18). */
    @SerialName("adult_age") val adultAge: Int = 18,
    /** Переключатель «показывать 18+» в настройках аккаунта. */
    @SerialName("show_adult") val showAdult: Boolean = true,
    /** Показывается ли 18+ прямо сейчас: и возраст подошёл, и переключатель включён. */
    @SerialName("adult_allowed") val adultAllowed: Boolean = false,
) {
    fun count(status: LibraryStatus): Int = statuses[status.key] ?: 0

    /** Возраст указан и его хватает: переключатель 18+ имеет смысл показывать. */
    val isAdultByAge: Boolean get() = (age ?: -1) >= adultAge
}

@Serializable
data class AuthResponse(val token: String, val user: Account)

/** Оценка тайтла: отдельно сайты, отдельно приложение, и общий итог. */
@Serializable
data class RatingDetails(
    val rating: Float? = null,
    val votes: Int = 0,
    @SerialName("site_rating") val siteRating: Float? = null,
    @SerialName("site_votes") val siteVotes: Int = 0,
    @SerialName("app_rating") val appRating: Float? = null,
    @SerialName("app_votes") val appVotes: Int = 0,
)

@Serializable
data class RatingResponse(
    @SerialName("manga_key") val mangaKey: String,
    @SerialName("user_rating") val userRating: Float? = null,
    @SerialName("rating_details") val ratingDetails: RatingDetails = RatingDetails(),
)

@Serializable
data class Comment(
    val id: Int,
    @SerialName("user_id") val userId: Int,
    val username: String,
    @SerialName("parent_id") val parentId: Int? = null,
    val text: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class CommentsResponse(
    val total: Int = 0,
    val page: Int = 1,
    val pages: Int = 1,
    val results: List<Comment> = emptyList(),
)

@Serializable
data class LibraryState(
    @SerialName("manga_key") val mangaKey: String,
    val status: String? = null,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
)

/** Строка на главной каталога: популярное, история, рекомендации. */
@Serializable
data class HomeRow(
    val key: String,
    val title: String,
    val results: List<Manga> = emptyList(),
)

@Serializable
data class HomeResponse(
    val rows: List<HomeRow> = emptyList(),
    /** Где человек остановился в каждом тайтле истории (ключ — manga_key). */
    val progress: Map<String, ChapterProgress> = emptyMap(),
)

/** История чтения вместе с местом остановки по каждому тайтлу. */
@Serializable
data class HistoryResponse(
    val total: Int = 0,
    val results: List<Manga> = emptyList(),
    val progress: Map<String, ChapterProgress> = emptyMap(),
)

/** Настройки приватности: что из профиля видно другим. */
@Serializable
data class PrivacySettings(
    @SerialName("profile_public") val profilePublic: Boolean = true,
    @SerialName("show_stats") val showStats: Boolean = true,
    @SerialName("show_library") val showLibrary: Boolean = true,
    @SerialName("show_favorites") val showFavorites: Boolean = true,
    @SerialName("show_ratings") val showRatings: Boolean = true,
    @SerialName("show_comments") val showComments: Boolean = true,
    @SerialName("show_history") val showHistory: Boolean = false,
)

@Serializable
data class PrivacyResponse(val privacy: PrivacySettings = PrivacySettings())

/** Комментарий в чужом профиле — со ссылкой на тайтл. */
@Serializable
data class ProfileComment(
    val id: Int,
    @SerialName("manga_key") val mangaKey: String = "",
    @SerialName("manga_title") val mangaTitle: String = "",
    val text: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

/**
 * Профиль другого пользователя — ровно в том объёме, который он разрешил
 * показывать. Закрытый профиль приходит с `isPrivate = true` и без разделов.
 */
@Serializable
data class PublicProfile(
    val id: Int = 0,
    val username: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("is_private") val isPrivate: Boolean = false,
    @SerialName("is_me") val isMe: Boolean = false,
    val sections: Map<String, Boolean> = emptyMap(),
    val stats: Account? = null,
    val library: List<Manga> = emptyList(),
    val favorites: List<Manga> = emptyList(),
    val ratings: List<Manga> = emptyList(),
    val history: List<Manga> = emptyList(),
    val comments: List<ProfileComment> = emptyList(),
)

@Serializable
data class MangaListResponse(
    val total: Int = 0,
    val results: List<Manga> = emptyList(),
)

@Serializable
data class ReadChapterResponse(
    @SerialName("read_chapters") val readChapters: Int = 0,
)

// --------------------------------------------------------------- фильтры

@Serializable
data class TagOption(val name: String, val count: Int = 0)

/** Тип издания в фильтрах: ключ для сервера, подпись для человека. */
@Serializable
data class KindOption(
    val key: String,
    val name: String,
    val count: Int = 0,
)

@Serializable
data class SourceOption(
    val id: String,
    val name: String,
    @SerialName("is_foreign") val isForeign: Boolean = false,
)

/** Что можно выбрать в боковой панели фильтров (приходит с сервера). */
@Serializable
data class FilterOptions(
    val tags: List<TagOption> = emptyList(),
    val genres: List<TagOption> = emptyList(),
    /** Манга, манхва, маньхуа и прочее — отдельный отбор, а не тег. */
    val kinds: List<KindOption> = emptyList(),
    val years: List<Int> = emptyList(),
    @SerialName("max_chapters") val maxChapters: Int = 0,
    val statuses: List<String> = emptyList(),
    val sources: List<SourceOption> = emptyList(),
    val sorts: List<String> = emptyList(),
)

/** Сортировка над сеткой каталога (выпадающий список, не боковая панель). */
enum class CatalogSort(val key: String, val title: String) {
    POPULAR("popular", "По популярности"),
    RATING("rating", "По оценке"),
    VIEWS("views", "По просмотрам"),
    UPDATED("updated", "По обновлению"),
    CHAPTERS("chapters", "По числу глав"),
    YEAR("year", "По году"),
    TITLE("title", "По названию");

    companion object {
        fun from(key: String?): CatalogSort = entries.firstOrNull { it.key == key } ?: POPULAR
    }
}

/** Выбранные фильтры. Пустой объект = «ничего не выбрано». */
data class CatalogFilters(
    val tags: Set<String> = emptySet(),
    val excludeTags: Set<String> = emptySet(),
    val statuses: Set<String> = emptySet(),
    val minChapters: Int? = null,
    val maxChapters: Int? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val minRating: Float? = null,
    /** Ключи типов издания (manga / manhwa / manhua …). */
    val kinds: Set<String> = emptySet(),
    val anyTag: Boolean = false,
) {
    val isEmpty: Boolean
        get() = tags.isEmpty() && excludeTags.isEmpty() && statuses.isEmpty() &&
            kinds.isEmpty() && minChapters == null && maxChapters == null &&
            yearFrom == null && yearTo == null && minRating == null

    val activeCount: Int
        get() = tags.size + excludeTags.size + statuses.size + kinds.size +
            listOfNotNull(minChapters, maxChapters, yearFrom, yearTo).size +
            (if (minRating != null) 1 else 0)
}

/**
 * Подписи статусов выпуска. Ключи приходят с сервера латиницей — они общие для
 * всех сайтов-источников, а показывать их пользователю нужно по-русски:
 * иначе в фильтре попадались «axed» и «hiatus».
 */
val MANGA_STATUS_TITLES = mapOf(
    "ongoing" to "Выходит",
    "completed" to "Завершён",
    "hiatus" to "Приостановлен",
    "announced" to "Анонс",
    "cancelled" to "Отменён",
    "canceled" to "Отменён",
    "axed" to "Отменён",
    "dropped" to "Отменён",
    "unknown" to "Неизвестно",
)

/** Русская подпись статуса; незнакомый ключ показываем как «Неизвестно». */
fun mangaStatusTitle(status: String?): String =
    MANGA_STATUS_TITLES[status?.lowercase().orEmpty()] ?: "Неизвестно"


// --------------------------------------------- свои главы и свои комментарии

/**
 * Прочитанная глава в списке аккаунта. Вместе с главой приходит название
 * тайтла: список открывается со страницы аккаунта и должен быть понятен без
 * захода в карточку.
 */
@Serializable
data class ReadChapterEntry(
    @SerialName("manga_key") val mangaKey: String = "",
    @SerialName("manga_title") val mangaTitle: String = "",
    @SerialName("chapter_key") val chapterKey: String = "",
    @SerialName("source_id") val sourceId: String = "",
    val number: Float = 0f,
    val title: String = "",
    @SerialName("read_at") val readAt: String? = null,
)

@Serializable
data class ReadChaptersResponse(
    val total: Int = 0,
    val results: List<ReadChapterEntry> = emptyList(),
    val manga: List<Manga> = emptyList(),
)

/** Свой комментарий вместе с тайтлом, под которым он оставлен. */
@Serializable
data class MyComment(
    val id: Int = 0,
    @SerialName("manga_key") val mangaKey: String = "",
    @SerialName("manga_title") val mangaTitle: String = "",
    val text: String = "",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class MyCommentsResponse(
    val total: Int = 0,
    val results: List<MyComment> = emptyList(),
    val manga: List<Manga> = emptyList(),
)
