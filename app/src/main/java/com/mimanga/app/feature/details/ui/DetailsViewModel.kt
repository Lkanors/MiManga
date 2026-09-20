package com.mimanga.app.feature.details.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimanga.app.core.network.isAgeBlocked
import com.mimanga.app.core.network.userMessage
import com.mimanga.app.domain.model.LibraryStatus
import com.mimanga.app.domain.model.Manga
import com.mimanga.app.domain.model.MangaSource
import com.mimanga.app.domain.repository.AccountRepository
import com.mimanga.app.domain.repository.MangaRepository
import com.mimanga.app.domain.repository.ProgressRepository
import com.mimanga.app.feature.details.state.DetailsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val accountRepository: AccountRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailsState())
    val state: StateFlow<DetailsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.token.collect { token ->
                _state.update { it.copy(isLoggedIn = token != null) }
                // Свой id нужен, чтобы показать кнопку удаления у своих
                // комментариев и не показывать её у чужих.
                val userId = if (token == null) null else {
                    runCatching { accountRepository.account().id }.getOrNull()
                }
                _state.update { it.copy(userId = userId) }
            }
        }
        // Вход и выход меняют права на 18+. Открытая страница тайтла об этом
        // сама не узнает: она уже загружена. Поэтому на смену аккаунта
        // страница перечитывается — пустят на неё или нет, решает сервер.
        viewModelScope.launch {
            accountRepository.token.drop(1).distinctUntilChanged().collect {
                if (_state.value.manga != null) refresh()
            }
        }
    }

    /**
     * Инициализация данными карточки. Безопасно вызывать многократно: если
     * та же манга уже загружена, ничего не делает.
     *
     * Сверяется именно тайтл, а не «загружено ли хоть что-то». ViewModel живёт
     * дольше экрана, и если ей достанется другой тайтл (ключи ViewModel
     * совпали), она обязана начать заново, а не показывать предыдущий.
     */
    fun initFromManga(manga: Manga) {
        val current = _state.value.manga
        if (current != null && current.actionKey == manga.actionKey) return
        if (current != null) {
            // Другой тайтл: от прежнего не должно остаться ни глав, ни
            // выбранного источника, ни комментариев.
            triedSources.clear()
            _state.value = DetailsState(isLoggedIn = _state.value.isLoggedIn,
                                        userId = _state.value.userId)
        }
        _state.update { it.copy(manga = manga, isLoadingDetail = true) }
        viewModelScope.launch {
            val loaded = runCatching { mangaRepository.getMangaById(manga.id) }
            if (blockedByAge(loaded.exceptionOrNull())) return@launch
            val full = loaded.getOrElse {
                Timber.w(it, "Карточка не загрузилась, показываем то, что пришло из списка")
                manga
            }
            _state.update {
                it.copy(
                    manga = full,
                    isLoadingDetail = false,
                    libraryStatus = full.libraryStatus,
                    isFavorite = full.isFavorite,
                    userRating = full.userRating,
                    rating = full.ratingDetails,
                )
            }
            val defaultSource = full.ruSources.firstOrNull() ?: full.sources.firstOrNull()
            if (defaultSource != null) selectSource(defaultSource, auto = true)
            loadComments()
            loadSimilar(full.id)
        }
    }

    /**
     * Отказ по возрасту: тайтл 18+, а его не подтвердили.
     *
     * Отличать это от обычной сетевой ошибки обязательно: при обычной экран
     * показывает то, что пришло из каталога, и человек продолжает читать. Тут
     * показывать нечего — сервер не отдаст ни описания, ни глав.
     */
    private fun blockedByAge(error: Throwable?): Boolean {
        if (error == null || !error.isAgeBlocked()) return false
        _state.update {
            it.copy(isLoadingDetail = false, isLoadingChapters = false,
                    ageBlocked = true, error = error.userMessage("Тайтл недоступен"))
        }
        return true
    }

    // ------------------------------------------------------------------ главы

    /**
     * Показывает главы источника.
     *
     * [auto] — источник выбрало само приложение (при открытии карточки или
     * потому, что в предыдущем глав не оказалось). В этом случае пустой ответ
     * не тупик: берём следующий источник, который ещё не пробовали. Сервер
     * такой источник у себя помечает и из выбора убирает, но ждать
     * следующего открытия карточки человеку незачем.
     *
     * Если источник выбрал человек, его выбор не подменяется: на месте списка
     * глав будет написано, что их здесь нет.
     */
    fun selectSource(source: MangaSource, auto: Boolean = false) {
        _state.update { it.copy(selectedSource = source, chapters = emptyList(),
                                progress = emptyMap(), lastRead = null) }
        viewModelScope.launch {
            _state.update { it.copy(isLoadingChapters = true, error = null) }
            try {
                val chapters = mangaRepository.getChapters(source.sourceId, source.url ?: "")
                _state.update { it.copy(chapters = chapters, isLoadingChapters = false) }
                if (chapters.isEmpty()) {
                    triedSources += source.sourceId
                    val next = if (auto) nextUntried() else null
                    if (next != null) {
                        selectSource(next, auto = true)
                        return@launch
                    }
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(isLoadingChapters = false,
                            error = error.userMessage("Главы не загрузились"))
                }
            }
            loadProgress(source)
        }
    }

    /** Источники, в которых глав не нашлось: второй раз туда не идём. */
    private val triedSources = mutableSetOf<String>()

    private fun nextUntried(): MangaSource? {
        val manga = _state.value.manga ?: return null
        return (manga.ruSources + manga.enSources)
            .firstOrNull { it.sourceId !in triedSources }
    }

    /**
     * Подтягивает места остановки для выбранного источника: полоски под
     * главами и кнопку «продолжить». У гостя это читается из памяти телефона,
     * у вошедшего — с сервера.
     */
    private fun loadProgress(source: MangaSource) {
        val key = _state.value.manga?.actionKey ?: return
        viewModelScope.launch {
            val progress = runCatching { progressRepository.of(key) }.getOrNull() ?: return@launch
            _state.update {
                it.copy(progress = progress.byChapter(source.sourceId),
                        lastRead = progress.lastOf(source.sourceId))
            }
        }
    }

    /** Показывает подсказку, что оценка доступна только после входа. */
    fun notifyLoginNeeded() {
        _state.update { it.copy(notice = "Оценку могут ставить только зарегистрированные") }
    }

    /** Обновляет прогресс после выхода из читалки. */
    fun refreshProgress() {
        _state.value.selectedSource?.let { loadProgress(it) }
    }

    /**
     * Перечитывает всё, что видно на странице: свайп сверху вниз.
     *
     * Главы на источниках выходят каждый день, и после возвращения к
     * открытой карточке список должен обновляться тем же жестом, что и везде.
     */
    fun refresh() {
        val manga = _state.value.manga ?: return
        viewModelScope.launch {
            // ageBlocked снимается здесь же: этот же тайтл после входа в
            // аккаунт может и открыться.
            _state.update { it.copy(isLoadingDetail = true, error = null, ageBlocked = false) }
            val loaded = runCatching { mangaRepository.getMangaById(manga.id) }
            if (blockedByAge(loaded.exceptionOrNull())) return@launch
            val full = loaded.getOrElse {
                Timber.w(it, "Карточка не обновилась")
                manga
            }
            _state.update {
                it.copy(manga = full, isLoadingDetail = false,
                        libraryStatus = full.libraryStatus, isFavorite = full.isFavorite,
                        userRating = full.userRating, rating = full.ratingDetails)
            }
            triedSources.clear()
            _state.value.selectedSource?.let { selectSource(it, auto = true) }
            loadComments()
            loadSimilar(full.id)
        }
    }

    fun toggleForeignSources(show: Boolean) {
        _state.update { it.copy(showForeignSources = show) }
        val available = if (show) _state.value.manga?.enSources.orEmpty()
                        else _state.value.manga?.ruSources.orEmpty()
        val current = _state.value.selectedSource
        if (current == null || available.none { it.sourceId == current.sourceId }) {
            val next = available.firstOrNull()
            if (next != null) selectSource(next)
            else _state.update { it.copy(selectedSource = null, chapters = emptyList()) }
        }
    }

    private fun loadSimilar(mangaId: String) {
        viewModelScope.launch {
            val similar = mangaRepository.similar(mangaId)
            _state.update { it.copy(similar = similar) }
        }
    }

    // ---------------------------------------------------------------- аккаунт

    fun setStatus(status: LibraryStatus?) {
        val key = _state.value.manga?.actionKey ?: return
        val next = if (_state.value.libraryStatus == status) null else status
        _state.update { it.copy(libraryStatus = next) }
        viewModelScope.launch {
            runCatching { accountRepository.setStatus(key, next) }
                .onFailure { failed(it, "Не удалось сохранить состояние") }
        }
    }

    fun toggleFavorite() {
        val key = _state.value.manga?.actionKey ?: return
        val next = !_state.value.isFavorite
        _state.update { it.copy(isFavorite = next) }
        viewModelScope.launch {
            runCatching { accountRepository.setFavorite(key, next) }
                .onFailure { failed(it, "Не удалось изменить избранное") }
        }
    }

    fun rate(value: Float) {
        val key = _state.value.manga?.actionKey ?: return
        viewModelScope.launch {
            runCatching { accountRepository.rate(key, value) }
                .onSuccess { response ->
                    _state.update {
                        it.copy(userRating = response.userRating, rating = response.ratingDetails,
                                notice = "Оценка сохранена")
                    }
                }
                .onFailure { failed(it, "Не удалось поставить оценку") }
        }
    }

    fun removeRating() {
        val key = _state.value.manga?.actionKey ?: return
        viewModelScope.launch {
            runCatching { accountRepository.removeRating(key) }
                .onSuccess { response ->
                    _state.update {
                        it.copy(userRating = null, rating = response.ratingDetails,
                                notice = "Оценка убрана")
                    }
                }
                .onFailure { failed(it, "Не удалось убрать оценку") }
        }
    }

    // ------------------------------------------------------------- комментарии

    fun loadComments() {
        val key = _state.value.manga?.actionKey ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingComments = true) }
            runCatching { accountRepository.comments(key) }
                .onSuccess { response ->
                    _state.update {
                        it.copy(comments = response.results, commentsTotal = response.total,
                                isLoadingComments = false)
                    }
                }
                .onFailure {
                    Timber.w(it, "Комментарии не загрузились")
                    _state.update { state -> state.copy(isLoadingComments = false) }
                }
        }
    }

    fun addComment(text: String) {
        val key = _state.value.manga?.actionKey ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching { accountRepository.addComment(key, text.trim()) }
                .onSuccess { comment ->
                    _state.update {
                        it.copy(comments = listOf(comment) + it.comments,
                                commentsTotal = it.commentsTotal + 1)
                    }
                }
                .onFailure { failed(it, "Комментарий не отправился") }
        }
    }

    fun deleteComment(commentId: Int) {
        viewModelScope.launch {
            runCatching { accountRepository.deleteComment(commentId) }
                .onSuccess {
                    _state.update {
                        it.copy(comments = it.comments.filterNot { comment -> comment.id == commentId },
                                commentsTotal = (it.commentsTotal - 1).coerceAtLeast(0))
                    }
                }
                .onFailure { failed(it, "Не удалось удалить комментарий") }
        }
    }

    /** Отмечает главу прочитанной: из этого складываются история и счётчик. */
    fun markChapterRead(chapterUrl: String, sourceId: String, number: Float, title: String) {
        if (!_state.value.isLoggedIn) return
        val key = _state.value.manga?.actionKey ?: return
        viewModelScope.launch {
            runCatching {
                accountRepository.markChapterRead(key, "$sourceId:$chapterUrl", sourceId, number, title)
            }.onFailure { Timber.w(it, "Не удалось отметить главу прочитанной") }
            // Вкладку «читаю» здесь НЕ проставляем: список ведётся руками, и
            // чтение главы попадает только в историю. Раньше отметка ставилась
            // сама — вслед за сервером, — и список повторял историю.
        }
    }

    fun clearNotice() = _state.update { it.copy(notice = null, error = null) }

    private fun failed(error: Throwable, fallback: String) {
        Timber.w(error, fallback)
        _state.update { it.copy(error = error.userMessage(fallback)) }
    }
}
