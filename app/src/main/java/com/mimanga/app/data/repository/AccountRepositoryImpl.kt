package com.mimanga.app.data.repository

import com.mimanga.app.data.remote.AuthStore
import com.mimanga.app.data.remote.MangaServerApi
import com.mimanga.app.domain.model.Account
import com.mimanga.app.domain.model.Comment
import com.mimanga.app.domain.model.CommentsResponse
import com.mimanga.app.domain.model.LibraryStatus
import com.mimanga.app.domain.model.Manga
import com.mimanga.app.domain.model.MyCommentsResponse
import com.mimanga.app.domain.model.ReadChaptersResponse
import com.mimanga.app.domain.model.PrivacySettings
import com.mimanga.app.domain.model.PublicProfile
import com.mimanga.app.domain.model.RatingResponse
import com.mimanga.app.domain.repository.AccountRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val api: MangaServerApi,
    private val authStore: AuthStore,
) : AccountRepository {

    override val token: StateFlow<String?> = authStore.token

    override suspend fun register(username: String, password: String,
                                  birthDate: String): Account =
        api.register(username, password, birthDate).user

    override suspend fun login(username: String, password: String): Account =
        api.login(username, password).user

    override suspend fun logout() = api.logout()

    override suspend fun account(): Account = api.getAccount()

    override suspend fun setBirthDate(birthDate: String): Account = api.setBirthDate(birthDate)

    override suspend fun setAdultVisibility(showAdult: Boolean): Account =
        api.setAdultVisibility(showAdult)

    override suspend fun library(status: LibraryStatus?): List<Manga> =
        api.getLibrary(status?.key).results

    override suspend fun favorites(): List<Manga> = api.getLibrary("favorite").results

    override suspend fun setStatus(mangaKey: String, status: LibraryStatus?) {
        // Снятие пометки передаётся словом, а не null: поля со значением null
        // сериализатор в запрос не кладёт, и сервер счёл бы, что состояние
        // трогать не нужно.
        api.setLibrary(mangaKey, status?.key ?: MangaServerApi.CLEAR_STATUS, null)
    }

    override suspend fun setFavorite(mangaKey: String, favorite: Boolean) {
        api.setLibrary(mangaKey, null, favorite)
    }

    override suspend fun history(limit: Int): List<Manga> = api.getHistory(limit).results

    override suspend fun readChapters(limit: Int): ReadChaptersResponse =
        api.getReadChapters(limit)

    override suspend fun myComments(limit: Int): MyCommentsResponse = api.getMyComments(limit)

    override suspend fun privacy(): PrivacySettings = api.getPrivacy().privacy

    override suspend fun updatePrivacy(settings: PrivacySettings): PrivacySettings =
        api.updatePrivacy(settings).privacy

    override suspend fun profile(username: String): PublicProfile = api.getProfile(username)

    override suspend fun removeFromHistory(mangaKey: String) = api.deleteHistory(mangaKey)

    override suspend fun markChapterRead(
        mangaKey: String,
        chapterKey: String,
        sourceId: String,
        number: Float,
        title: String,
    ) {
        api.markRead(mangaKey, chapterKey, sourceId, number, title)
    }

    override suspend fun rated(): List<Manga> = api.getRated().results

    override suspend fun rate(mangaKey: String, value: Float): RatingResponse =
        api.rate(mangaKey, value)

    override suspend fun removeRating(mangaKey: String): RatingResponse =
        api.removeRating(mangaKey)

    override suspend fun recommendations(limit: Int): List<Manga> =
        api.getRecommendations(limit).results

    override suspend fun comments(mangaKey: String, page: Int): CommentsResponse =
        api.getComments(mangaKey, page)

    override suspend fun addComment(mangaKey: String, text: String): Comment =
        api.addComment(mangaKey, text)

    override suspend fun deleteComment(commentId: Int) = api.deleteComment(commentId)
}
