package com.mimanga.app.feature.account.state

import com.mimanga.app.domain.model.Account
import com.mimanga.app.domain.model.LibraryStatus
import com.mimanga.app.domain.model.Manga
import com.mimanga.app.domain.model.MyComment
import com.mimanga.app.domain.model.PrivacySettings
import com.mimanga.app.domain.model.ReadChapterEntry

/**
 * Вкладки страницы аккаунта.
 *
 * История чтения живёт здесь же: отдельной вкладки в нижней панели у неё
 * больше нет — всё, что относится к пользователю, собрано на одном экране.
 */
enum class AccountTab(val title: String, val status: LibraryStatus? = null) {
    FAVORITES("Избранное"),
    READING("Читаю", LibraryStatus.READING),
    PLANNED("В планах", LibraryStatus.PLANNED),
    DROPPED("Заброшено", LibraryStatus.DROPPED),
    COMPLETED("Прочитано", LibraryStatus.COMPLETED),
    RATED("Оценённые"),
    HISTORY("История"),
    PRIVACY("Приватность"),
}

/**
 * Что открывается по нажатию на счётчик в шапке профиля.
 *
 * Раньше счётчики были просто числами: «глав 412» — и всё, посмотреть, каких
 * именно, было негде. У каждого счётчика свой список.
 */
enum class AccountDetail(val title: String, val subtitle: String) {
    CHAPTERS("Прочитанные главы", "Что и когда вы читали"),
    TITLES("Прочитанные тайтлы", "Тайтлы, в которых открыта хотя бы одна глава"),
    RATINGS("Мои оценки", "Тайтлы, которым вы поставили оценку"),
    FAVORITES("Избранное", "Тайтлы, отмеченные сердечком"),
    COMMENTS("Мои комментарии", "Где и что вы написали"),
}

data class AccountState(
    val isLoggedIn: Boolean = false,
    val account: Account? = null,
    val tab: AccountTab = AccountTab.READING,
    val items: List<Manga> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    // Форма входа
    val registerMode: Boolean = false,
    val username: String = "",
    val password: String = "",
    /**
     * Дата рождения в форме регистрации, «ГГГГ-ММ-ДД». Пусто — не выбрана, и
     * кнопка «Зарегистрироваться» недоступна: без возраста сервер откажет.
     */
    val birthDate: String = "",
    val isSubmitting: Boolean = false,
    val authError: String? = null,
    /** Что из профиля видно другим пользователям. */
    val privacy: PrivacySettings = PrivacySettings(),
    /** Ошибка в разделе возраста (например, «дата уже указана»). */
    val adultError: String? = null,
    /** Открытый список за счётчиком; null — обычный экран аккаунта. */
    val detail: AccountDetail? = null,
    val detailItems: List<Manga> = emptyList(),
    val detailChapters: List<ReadChapterEntry> = emptyList(),
    val detailComments: List<MyComment> = emptyList(),
    /** Карточки тайтлов, упомянутых в списке глав или комментариев. */
    val detailManga: Map<String, Manga> = emptyMap(),
    val isDetailLoading: Boolean = false,
)
