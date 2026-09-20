package com.mimanga.app.feature.catalog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimanga.app.core.network.userMessage
import com.mimanga.app.domain.model.CatalogFilters
import com.mimanga.app.domain.model.CatalogSort
import com.mimanga.app.domain.repository.AccountRepository
import com.mimanga.app.domain.repository.MangaRepository
import com.mimanga.app.domain.repository.SettingsRepository
import com.mimanga.app.feature.catalog.state.CatalogState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogState())
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            _state.update {
                it.copy(sort = CatalogSort.from(settings.defaultSort),
                        includeForeign = settings.includeForeign)
            }
            loadFilterOptions()
            // Каталог — это всегда список: строки «популярное» и «история»
            // переехали на вкладку «Главное», здесь остались поиск и фильтры.
            loadFirstPage()
        }
        // Вход и выход из аккаунта меняют пометки на карточках.
        viewModelScope.launch {
            var first = true
            accountRepository.token.collect {
                if (first) { first = false; return@collect }
                loadFirstPage()
            }
        }
    }

    private fun loadFilterOptions() {
        viewModelScope.launch {
            runCatching { mangaRepository.filterOptions() }
                .onSuccess { options -> _state.update { it.copy(options = options) } }
                .onFailure { Timber.w(it, "Список фильтров недоступен") }
        }
    }

    // -------------------------------------------------------------------- поиск

    fun onQueryChanged(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            // Пустой запрос — это просто весь каталог с текущими фильтрами.
            _state.update { it.copy(error = null) }
            loadFirstPage()
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)  // пока человек печатает, запрос не уходит
            loadFirstPage()
        }
    }

    /** Сброс поиска: остаётся каталог с выбранными фильтрами. */
    fun clearQuery() {
        searchJob?.cancel()
        _state.update { it.copy(query = "") }
        loadFirstPage()
    }

    fun setSort(sort: CatalogSort) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort) }
        viewModelScope.launch { settingsRepository.update { it.copy(defaultSort = sort.key) } }
        loadFirstPage()
    }

    /**
     * Применяет выбранные жанры и теги.
     *
     * Список перезагружается сразу, ещё до закрытия панели: раньше результат
     * появлялся только после нажатия кнопки внизу, и выбор тега выглядел так,
     * будто он ничего не делает.
     */
    fun applyFilters(filters: CatalogFilters) {
        if (_state.value.filters == filters) return
        _state.update { it.copy(filters = filters) }
        loadFirstPage()
    }

    fun clearFilters() {
        _state.update { it.copy(filters = CatalogFilters()) }
        loadFirstPage()
    }

    fun setIncludeForeign(include: Boolean) {
        _state.update { it.copy(includeForeign = include) }
        viewModelScope.launch { settingsRepository.update { it.copy(includeForeign = include) } }
        loadFirstPage()
    }

    private fun loadFirstPage() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, page = 1) }
            try {
                val response = load(page = 1)
                _state.update {
                    it.copy(results = response.results, total = response.total,
                            hasMore = response.page < response.pages,
                            isLoading = false, page = 1,
                            resultsRevision = it.resultsRevision + 1)
                }
            } catch (error: Exception) {
                _state.update { it.copy(isLoading = false, error = message(error)) }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || !current.hasMore) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            try {
                val next = current.page + 1
                val response = load(page = next)
                _state.update {
                    it.copy(results = it.results + response.results,
                            page = next, hasMore = response.page < response.pages,
                            isLoadingMore = false)
                }
            } catch (error: Exception) {
                Timber.w(error, "Подгрузка каталога не удалась")
                _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    private suspend fun load(page: Int) = with(_state.value) {
        if (isSearching) {
            // Фильтры действуют и при поиске: выбранный жанр не должен слетать,
            // стоило человеку начать набирать название.
            mangaRepository.search(query, page = page, includeForeign = includeForeign,
                                   filters = filters)
        } else {
            mangaRepository.catalog(page = page, sort = sort,
                                    includeForeign = includeForeign, filters = filters)
        }
    }

    fun refresh() = loadFirstPage()

    private fun message(error: Exception): String =
        error.userMessage("Сервер недоступен. Проверьте подключение к интернету.")
}
