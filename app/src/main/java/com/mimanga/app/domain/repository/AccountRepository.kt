package com.mimanga.app.domain.repository

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
import kotlinx.coroutines.flow.StateFlow

/**
 * Аккаунт и всё, что к нему привязано: списки, история, оценки, комментарии.
 * Данные живут на сервере, поэтому доступны с любого устройства.
 */
interface AccountRepository {
    /** null — пользователь не вошёл. */
    val token: StateFlow<String?>

    /** Дата рождения «ГГГГ-ММ-ДД» обязательна: без возраста каталог неполный. */
    suspend fun register(username: String, password: String, birthDate: String): Account
    suspend fun login(username: String, password: String): Account
    suspend fun logout()
    suspend fun account(): Account

    /** Дата рождения для аккаунта, заведённого до этого вопроса. Задаётся один раз. */
    suspend fun setBirthDate(birthDate: String): Account

    /** Переключатель «показывать 18+»; возраст он не отменяет. */
    suspend fun setAdultVisibility(showAdult: Boolean): Account

    suspend fun library(status: LibraryStatus?): List<Manga>
    suspend fun favorites(): List<Manga>
    suspend fun setStatus(mangaKey: String, status: LibraryStatus?)
    suspend fun setFavorite(mangaKey: String, favorite: Boolean)

    suspend fun history(limit: Int = 50): List<Manga>
    suspend fun removeFromHistory(mangaKey: String)
    suspend fun markChapterRead(
        mangaKey: String,
        chapterKey: String,
        sourceId: String,
        number: Float,
        title: String,
    )

    suspend fun rated(): List<Manga>
    suspend fun rate(mangaKey: String, value: Float): RatingResponse
    suspend fun removeRating(mangaKey: String): RatingResponse
    suspend fun recommendations(limit: Int = 20): List<Manga>

    suspend fun privacy(): PrivacySettings
    suspend fun updatePrivacy(settings: PrivacySettings): PrivacySettings
    suspend fun profile(username: String): PublicProfile

    /** Прочитанные главы и свои комментарии — списки за счётчиками профиля. */
    suspend fun readChapters(limit: Int = 200): ReadChaptersResponse
    suspend fun myComments(limit: Int = 200): MyCommentsResponse

    suspend fun comments(mangaKey: String, page: Int = 1): CommentsResponse
    suspend fun addComment(mangaKey: String, text: String): Comment
    suspend fun deleteComment(commentId: Int)
}
