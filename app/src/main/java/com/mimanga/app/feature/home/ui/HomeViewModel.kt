package com.mimanga.app.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimanga.app.domain.model.HomeRow
import com.mimanga.app.domain.model.Manga
import com.mimanga.app.domain.repository.AccountRepository
import com.mimanga.app.domain.repository.MangaRepository
import com.mimanga.app.domain.repository.ProgressRepository
import com.mimanga.app.domain.repository.SettingsRepository
import com.mimanga.app.feature.home.state.HomeState
import com.mimanga.app.feature.reader.state.ReaderNav
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val accountRepository: AccountRepository,
    private val progressRepository: ProgressRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private var rowSize = 20
    private var includeForeign = false

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            rowSize = settings.homeRowSize
            includeForeign = settings.includeForeign
            load()
        }
        // Вход и выход из аккаунта меняют историю и рекомендации.
        viewModelScope.launch {
            var first = true
            accountRepository.token.collect { token ->
                _state.update { it.copy(isLoggedIn = token != null) }
                if (first) { first = false; return@collect }
                // После входа переносим на сервер то, что человек прочитал гостем.
                runCatching { progressRepository.syncGuestProgress() }
                load()
            }
        }
    }

    /**
     * Перечитывает главную, не показывая полосу загрузки.
     *
     * Вызывается, когда экран снова появляется — например, после чтения
     * главы: строка «Продолжить чтение» должна уже содержать тот тайтл,
     * который только что читали. Тихо — потому что это не жест человека, и
     * дёргать полосу и кружок обновления на ровном месте незачем.
     */
    fun refreshQuietly() = load(quiet = true)

    fun load(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _state.update { it.copy(isLoading = true, error = null) }
            try {
                val rows = mangaRepository.home(rowSize, includeForeign).toMutableList()
                // История у гостя лежит в телефоне, поэтому строку «продолжить
                // чтение» собираем сами — сервер о ней ничего не знает.
                val (historyCards, progress) = progressRepository.history(rowSize)
                rows.removeAll { it.key == "history" }
                if (historyCards.isNotEmpty()) {
                    rows.add(0, HomeRow("history", "Продолжить чтение", historyCards))
                }
                _state.update { state ->
                    val next = state.copy(rows = rows, progress = progress, isLoading = false)
                    // Отметка растёт, только если содержимое действительно
                    // другое: иначе экран перематывался бы наверх на каждое
                    // возвращение с карточки тайтла.
                    if (next.contentKey() == state.contentKey()) next
                    else next.copy(contentRevision = state.contentRevision + 1)
                }
            } catch (error: Exception) {
                Timber.w(error, "Главная не загрузилась")
                // Тихое обновление молчит и об ошибке: на экране уже есть
                // что показывать, и сообщение поверх него только мешало бы.
                if (!quiet) {
                    _state.update {
                        it.copy(isLoading = false,
                                error = error.message ?: "Сервер недоступен")
                    }
                } else {
                    _state.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun removeFromHistory(mangaKey: String) {
        viewModelScope.launch {
            runCatching { progressRepository.forget(mangaKey) }
                .onFailure { Timber.w(it, "Не удалось убрать из истории") }
            _state.update { state ->
                state.copy(rows = state.rows.map { row ->
                    if (row.key == "history") {
                        row.copy(results = row.results.filterNot { it.actionKey == mangaKey })
                    } else row
                })
            }
        }
    }

    /**
     * Открывает читалку ровно там, где человек остановился: на том же
     * источнике, той же главе и той же странице.
     *
     * Открывает СРАЗУ, не дожидаясь сети. Раньше здесь сначала запрашивался
     * список глав источника, и между нажатием и появлением читалки проходила
     * пара секунд, в которые на экране не менялось ничего — казалось, что
     * нажатие не сработало. Список догружает сама читалка (ReaderNav без глав,
     * с ключом нужной главы), показывая при этом название и кружок загрузки.
     */
    fun continueReading(manga: Manga, onOpen: (ReaderNav) -> Unit) {
        val progress = _state.value.progressOf(manga) ?: return
        val source = manga.sources.firstOrNull { it.sourceId == progress.sourceId } ?: return
        onOpen(
            ReaderNav(
                mangaKey = manga.actionKey,
                mangaTitle = manga.title,
                startIndex = progress.chapterIndex,
                source = source,
                startPage = progress.page,
                pendingChapterKey = progress.chapterKey,
            )
        )
    }
}
