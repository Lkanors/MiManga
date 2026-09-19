package com.mimanga.app.data.remote

import com.mimanga.app.BuildConfig
import com.mimanga.app.domain.model.Account
import com.mimanga.app.domain.model.AuthResponse
import com.mimanga.app.domain.model.CatalogFilters
import com.mimanga.app.domain.model.CatalogSort
import com.mimanga.app.domain.model.Chapter
import com.mimanga.app.domain.model.ChapterProgress
import com.mimanga.app.domain.model.Comment
import com.mimanga.app.domain.model.CommentsResponse
import com.mimanga.app.domain.model.FilterOptions
import com.mimanga.app.domain.model.HistoryResponse
import com.mimanga.app.domain.model.HomeResponse
import com.mimanga.app.domain.model.LibraryState
import com.mimanga.app.domain.model.Manga
import com.mimanga.app.domain.model.MangaListResponse
import com.mimanga.app.domain.model.MangaProgress
import com.mimanga.app.domain.model.MyCommentsResponse
import com.mimanga.app.domain.model.ReadChaptersResponse
import com.mimanga.app.domain.model.Page
import com.mimanga.app.domain.model.PrivacyResponse
import com.mimanga.app.domain.model.PrivacySettings
import com.mimanga.app.domain.model.PublicProfile
import com.mimanga.app.domain.model.RatingResponse
import com.mimanga.app.domain.model.ReadChapterResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CatalogResponse(
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0,
    val pages: Int = 0,
    val sort: String? = null,
    val results: List<Manga> = emptyList(),
)

@Serializable
data class ChaptersResponse(
    @SerialName("source_id") val sourceId: String,
    @SerialName("manga_id") val mangaId: Int? = null,
    val chapters: List<Chapter>,
    val total: Int,
)

@Serializable
data class PagesResponse(
    @SerialName("source_id") val sourceId: String,
    val pages: List<Page>,
    val total: Int,
)

@Serializable
data class SourceInfo(
    val id: String,
    val name: String,
    @SerialName("base_url") val baseUrl: String,
    @SerialName("is_foreign") val isForeign: Boolean,
    @SerialName("is_active") val isActive: Boolean? = null,
    val language: String,
    @SerialName("last_catalog_parse") val lastCatalogParse: String? = null,
)

@Serializable
data class SourcesResponse(val sources: List<SourceInfo>, val total: Int)

@Serializable
private data class CredentialsBody(
    val username: String,
    val password: String,
    /** Дата рождения «ГГГГ-ММ-ДД». При входе не нужна, при регистрации обязательна. */
    @SerialName("birth_date") val birthDate: String = "",
)

@Serializable
private data class BirthDateBody(@SerialName("birth_date") val birthDate: String)

@Serializable
private data class AdultBody(@SerialName("show_adult") val showAdult: Boolean)

@Serializable
private data class LibraryBody(val status: String?, @SerialName("is_favorite") val isFavorite: Boolean?)

@Serializable
private data class RatingBody(val value: Float)

@Serializable
private data class CommentBody(val text: String, @SerialName("parent_id") val parentId: Int? = null)

@Serializable
private data class ReadBody(
    @SerialName("manga_key") val mangaKey: String,
    @SerialName("chapter_key") val chapterKey: String,
    @SerialName("source_id") val sourceId: String,
    val number: Float,
    val title: String,
)

@Serializable
private data class ErrorBody(val detail: String? = null)

/** Ошибка сервера с человеческим текстом (его показывает экран). */
class ServerException(val status: Int, override val message: String) : Exception(message)

/**
 * Фильтры боковой панели в параметрах запроса. Один и тот же набор уходит и в
 * каталог, и в поиск: выбранный жанр не должен слетать, стоило человеку
 * начать набирать название.
 */
private fun HttpRequestBuilder.catalogFilters(filters: CatalogFilters) {
    filters.tags.forEach { parameter("tags", it) }
    filters.excludeTags.forEach { parameter("exclude_tags", it) }
    filters.statuses.forEach { parameter("status", it) }
    if (filters.anyTag) parameter("any_tag", true)
    filters.minChapters?.let { parameter("min_chapters", it) }
    filters.maxChapters?.let { parameter("max_chapters", it) }
    filters.yearFrom?.let { parameter("year_from", it) }
    filters.yearTo?.let { parameter("year_to", it) }
    filters.minRating?.let { parameter("min_rating", it) }
    filters.kinds.forEach { parameter("kind", it) }
}

/**
 * Клиент сервера MiManga.
 *
 * Адрес сервера ЗАШИТ в приложение (BuildConfig.SERVER_URL) и соединение идёт
 * по HTTPS с закреплённым сертификатом: выбора адреса в настройках больше нет,
 * поэтому подключиться к чужому серверу приложение не может.
 */
@Singleton
class MangaServerApi @Inject constructor(
    private val httpClient: HttpClient,
    private val authStore: AuthStore,
) {
    companion object {
        /** Единственный адрес, по которому работает приложение. */
        const val BASE_URL: String = BuildConfig.SERVER_URL

        /** Значение status, которым снимается пометка состояния тайтла. */
        const val CLEAR_STATUS: String = "none"

        private val json = Json { ignoreUnknownKeys = true }
    }

    /** Разбирает ответ, превращая ошибку сервера в понятное сообщение. */
    private suspend inline fun <reified T> HttpResponse.parse(): T {
        if (status.isSuccess()) return body()
        // Сервер ответил «нужен вход» — значит, сохранённый токен больше не
        // действует (истёк или аккаунт забанен). Держать его дальше незачем:
        // приложение должно вернуться к форме входа само.
        if (status == HttpStatusCode.Unauthorized) authStore.clear()
        val text = runCatching { bodyAsText() }.getOrDefault("")
        val detail = runCatching { json.decodeFromString<ErrorBody>(text).detail }.getOrNull()
        throw ServerException(
            status.value,
            detail ?: when (status) {
                HttpStatusCode.Unauthorized -> "Нужно войти в аккаунт"
                HttpStatusCode.NotFound -> "Не найдено"
                else -> "Ошибка сервера ${status.value}"
            },
        )
    }

    // ------------------------------------------------------------------ каталог

    suspend fun getCatalog(
        page: Int = 1,
        limit: Int = 30,
        sort: CatalogSort = CatalogSort.POPULAR,
        includeForeign: Boolean = false,
        filters: CatalogFilters = CatalogFilters(),
    ): CatalogResponse = httpClient.get("$BASE_URL/api/catalog") {
        auth()
        parameter("page", page)
        parameter("limit", limit)
        parameter("sort", sort.key)
        parameter("include_foreign", includeForeign)
        catalogFilters(filters)
    }.parse()

    suspend fun searchCatalog(
        query: String,
        page: Int = 1,
        limit: Int = 30,
        includeForeign: Boolean = false,
        filters: CatalogFilters = CatalogFilters(),
    ): CatalogResponse = httpClient.get("$BASE_URL/api/catalog/search") {
        auth()
        parameter("q", query)
        parameter("page", page)
        parameter("limit", limit)
        parameter("include_foreign", includeForeign)
        catalogFilters(filters)
    }.parse()

    /** Главная каталога до поиска: популярное, история, рекомендации. */
    suspend fun getHome(rows: Int = 20, includeForeign: Boolean = false): HomeResponse =
        httpClient.get("$BASE_URL/api/catalog/home") {
            auth()
            parameter("rows", rows)
            parameter("include_foreign", includeForeign)
        }.parse()

    suspend fun getFilterOptions(): FilterOptions =
        httpClient.get("$BASE_URL/api/catalog/filters").parse()

    suspend fun getMangaById(mangaId: String): Manga =
        httpClient.get("$BASE_URL/api/manga/$mangaId") { auth() }.parse()

    suspend fun getSimilar(mangaId: String, limit: Int = 12): MangaListResponse =
        httpClient.get("$BASE_URL/api/manga/$mangaId/similar") {
            auth()
            parameter("limit", limit)
        }.parse()

    suspend fun getChaptersByMangaId(mangaId: String, sourceId: String): ChaptersResponse =
        httpClient.get("$BASE_URL/api/manga/$mangaId/chapters") {
            parameter("source_id", sourceId)
        }.parse()

    suspend fun getMangaDetail(sourceId: String, url: String): Manga =
        httpClient.get("$BASE_URL/api/manga/detail") {
            parameter("source_id", sourceId)
            parameter("url", url)
        }.parse()

    suspend fun getChapters(sourceId: String, url: String): ChaptersResponse =
        httpClient.get("$BASE_URL/api/manga/chapters") {
            parameter("source_id", sourceId)
            parameter("url", url)
        }.parse()

    suspend fun getPages(sourceId: String, chapterUrl: String): PagesResponse =
        httpClient.get("$BASE_URL/api/manga/pages") {
            parameter("source_id", sourceId)
            parameter("chapter_url", chapterUrl)
        }.parse()

    suspend fun getSources(): SourcesResponse = httpClient.get("$BASE_URL/api/sources").parse()

    // ----------------------------------------------------------------- аккаунт

    suspend fun register(username: String, password: String, birthDate: String): AuthResponse =
        httpClient.post("$BASE_URL/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(CredentialsBody(username, password, birthDate))
        }.parse<AuthResponse>().also { authStore.save(it.token) }

    suspend fun login(username: String, password: String): AuthResponse =
        httpClient.post("$BASE_URL/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(CredentialsBody(username, password))
        }.parse<AuthResponse>().also { authStore.save(it.token) }

    suspend fun logout() {
        runCatching { httpClient.post("$BASE_URL/api/auth/logout") { auth() } }
        authStore.clear()
    }

    suspend fun getAccount(): Account = httpClient.get("$BASE_URL/api/account/me") { auth() }.parse()

    /** Дата рождения для аккаунтов, заведённых до того, как её стали спрашивать. */
    suspend fun setBirthDate(birthDate: String): Account =
        httpClient.put("$BASE_URL/api/account/birth-date") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(BirthDateBody(birthDate))
        }.parse()

    /** Показывать ли 18+ тому, кому по возрасту можно. */
    suspend fun setAdultVisibility(showAdult: Boolean): Account =
        httpClient.put("$BASE_URL/api/account/adult") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(AdultBody(showAdult))
        }.parse()

    suspend fun getLibrary(status: String?): MangaListResponse =
        httpClient.get("$BASE_URL/api/account/library") {
            auth()
            status?.let { parameter("status", it) }
        }.parse()

    suspend fun setLibrary(mangaKey: String, status: String?, favorite: Boolean?): LibraryState =
        httpClient.put("$BASE_URL/api/account/library/$mangaKey") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(LibraryBody(status, favorite))
        }.parse()

    suspend fun getHistory(limit: Int = 50): HistoryResponse =
        httpClient.get("$BASE_URL/api/account/history") {
            auth()
            parameter("limit", limit)
        }.parse()

    suspend fun deleteHistory(mangaKey: String) {
        httpClient.delete("$BASE_URL/api/account/history/$mangaKey") { auth() }.parse<Map<String, String>>()
    }

    suspend fun markRead(
        mangaKey: String,
        chapterKey: String,
        sourceId: String,
        number: Float,
        title: String,
    ): ReadChapterResponse = httpClient.post("$BASE_URL/api/account/read") {
        auth()
        contentType(ContentType.Application.Json)
        setBody(ReadBody(mangaKey, chapterKey, sourceId, number, title))
    }.parse()

    suspend fun getRated(): MangaListResponse =
        httpClient.get("$BASE_URL/api/account/ratings") { auth() }.parse()

    /** Свои прочитанные главы: счётчик «глав» на странице аккаунта открывает их. */
    suspend fun getReadChapters(limit: Int = 200): ReadChaptersResponse =
        httpClient.get("$BASE_URL/api/account/chapters") {
            auth()
            parameter("limit", limit)
        }.parse()

    /** Свои комментарии вместе с тайтлами, под которыми они оставлены. */
    suspend fun getMyComments(limit: Int = 200): MyCommentsResponse =
        httpClient.get("$BASE_URL/api/account/comments") {
            auth()
            parameter("limit", limit)
        }.parse()

    suspend fun getRecommendations(limit: Int = 20): MangaListResponse =
        httpClient.get("$BASE_URL/api/account/recommendations") {
            auth()
            parameter("limit", limit)
        }.parse()

    suspend fun rate(mangaKey: String, value: Float): RatingResponse =
        httpClient.put("$BASE_URL/api/manga/$mangaKey/rating") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(RatingBody(value))
        }.parse()

    suspend fun removeRating(mangaKey: String): RatingResponse =
        httpClient.delete("$BASE_URL/api/manga/$mangaKey/rating") { auth() }.parse()

    suspend fun getComments(mangaKey: String, page: Int = 1): CommentsResponse =
        httpClient.get("$BASE_URL/api/manga/$mangaKey/comments") {
            parameter("page", page)
        }.parse()

    suspend fun addComment(mangaKey: String, text: String, parentId: Int? = null): Comment =
        httpClient.post("$BASE_URL/api/manga/$mangaKey/comments") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(CommentBody(text, parentId))
        }.parse()

    suspend fun deleteComment(commentId: Int) {
        httpClient.delete("$BASE_URL/api/comments/$commentId") { auth() }.parse<Map<String, String>>()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth() {
        authStore.current()?.let { header("Authorization", "Bearer $it") }
    }

    // ------------------------------------------------- место, где остановился

    /** Сохраняет место в главе на сервере (для вошедших в аккаунт). */
    suspend fun saveProgress(progress: ChapterProgress) {
        httpClient.post("$BASE_URL/api/account/progress") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(progress)
        }.parse<Map<String, kotlinx.serialization.json.JsonElement>>()
    }

    suspend fun getProgress(mangaKey: String, sourceId: String? = null): MangaProgress =
        httpClient.get("$BASE_URL/api/account/progress/$mangaKey") {
            auth()
            sourceId?.let { parameter("source_id", it) }
        }.parse()

    // ------------------------------------------------------ профили и приватность

    suspend fun getPrivacy(): PrivacyResponse =
        httpClient.get("$BASE_URL/api/account/privacy") { auth() }.parse()

    suspend fun updatePrivacy(privacy: PrivacySettings): PrivacyResponse =
        httpClient.put("$BASE_URL/api/account/privacy") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(privacy)
        }.parse()

    suspend fun getProfile(username: String): PublicProfile =
        httpClient.get("$BASE_URL/api/users/$username") { auth() }.parse()

    /** Карточки по списку ключей — история гостя хранится только в телефоне. */
    suspend fun getCards(keys: List<String>): MangaListResponse {
        if (keys.isEmpty()) return MangaListResponse()
        return httpClient.get("$BASE_URL/api/catalog/cards") {
            auth()
            parameter("keys", keys.joinToString(","))
        }.parse()
    }
}
