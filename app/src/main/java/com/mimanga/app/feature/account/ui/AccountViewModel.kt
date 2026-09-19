package com.mimanga.app.feature.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimanga.app.domain.model.Manga
import com.mimanga.app.domain.repository.AccountRepository
import com.mimanga.app.feature.account.state.AccountDetail
import com.mimanga.app.feature.account.state.AccountState
import com.mimanga.app.feature.account.state.AccountTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.token.collect { token ->
                val loggedIn = token != null
                _state.update { it.copy(isLoggedIn = loggedIn) }
                if (loggedIn) {
                    refreshProfile()
                    loadTab(_state.value.tab)
                } else {
                    _state.update { it.copy(account = null, items = emptyList()) }
                }
            }
        }
    }

    // -------------------------------------------------------------------- вход

    fun setUsername(value: String) = _state.update { it.copy(username = value, authError = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, authError = null) }
    fun toggleMode() = _state.update { it.copy(registerMode = !it.registerMode, authError = null) }

    /** Дата рождения в форме регистрации, «ГГГГ-ММ-ДД». */
    fun setBirthDate(value: String) = _state.update { it.copy(birthDate = value, authError = null) }

    fun submit() {
        val current = _state.value
        if (current.username.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(authError = "Заполните имя и пароль") }
            return
        }
        if (current.registerMode && current.birthDate.isBlank()) {
            _state.update { it.copy(authError = "Укажите дату рождения") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, authError = null) }
            val result = runCatching {
                if (current.registerMode) {
                    accountRepository.register(current.username.trim(), current.password,
                                               current.birthDate)
                } else {
                    accountRepository.login(current.username.trim(), current.password)
                }
            }
            result
                .onSuccess { account ->
                    _state.update {
                        it.copy(account = account, isLoggedIn = true, isSubmitting = false,
                                username = "", password = "", birthDate = "")
                    }
                    loadTab(_state.value.tab)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isSubmitting = false,
                                authError = error.message ?: "Не получилось войти")
                    }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            accountRepository.logout()
            _state.update {
                AccountState(isLoggedIn = false, registerMode = it.registerMode)
            }
        }
    }

    // ------------------------------------------------------------------ вкладки

    fun refreshProfile() {
        viewModelScope.launch {
            runCatching { accountRepository.account() }
                .onSuccess { account -> _state.update { it.copy(account = account) } }
                .onFailure { Timber.w(it, "Профиль не загрузился") }
        }
    }

    /**
     * Дата рождения для аккаунта, заведённого до того, как её стали
     * спрашивать. Сервер принимает её один раз — поэтому ошибку показываем,
     * а не проглатываем: человек должен понять, почему ничего не изменилось.
     */
    fun submitBirthDate(value: String) {
        if (value.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(adultError = null) }
            runCatching { accountRepository.setBirthDate(value) }
                .onSuccess { account -> _state.update { it.copy(account = account) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(adultError = error.message ?: "Дату рождения не приняли")
                    }
                }
        }
    }

    /** Переключатель «показывать 18+». Каталог после него другой, поэтому
     *  вместе с профилем перечитывается и открытая вкладка. */
    fun setAdultVisibility(show: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(adultError = null) }
            runCatching { accountRepository.setAdultVisibility(show) }
                .onSuccess { account ->
                    _state.update { it.copy(account = account) }
                    loadTab(_state.value.tab)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(adultError = error.message ?: "Настройку не удалось сохранить")
                    }
                }
        }
    }

    fun selectTab(tab: AccountTab) {
        _state.update { it.copy(tab = tab) }
        loadTab(tab)
        // Счётчики на самих вкладках берутся из профиля, а меняются они не
        // здесь, а на странице тайтла. Перечитываем их вместе со списком,
        // иначе на вкладке «Читаю» так и оставалось прежнее число.
        refreshProfile()
    }

    /** Вкладка, на которую просили открыть экран, уже применена. */
    private var initialTabApplied = false

    /**
     * Появление экрана: в первый раз открываем запрошенную вкладку, дальше —
     * просто перечитываем ту, на которой человек остался.
     *
     * Перечитываем каждый раз, потому что меняются списки не здесь, а на
     * странице тайтла («читаю», «прочитано», избранное, оценка), а эта
     * ViewModel живёт дольше своего экрана. Раньше список после возвращения
     * действительно перезагружался, но счётчики на вкладках — нет, и число
     * тайтлов в разделе оставалось тем же до перезапуска приложения.
     */
    fun openTab(tab: AccountTab) {
        if (!initialTabApplied) {
            initialTabApplied = true
            selectTab(tab)
            return
        }
        if (!_state.value.isLoggedIn) return
        refresh()
    }

    fun loadTab(tab: AccountTab) {
        if (!_state.value.isLoggedIn) return
        if (tab == AccountTab.PRIVACY) {
            loadPrivacy()
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                when (tab) {
                    AccountTab.FAVORITES -> accountRepository.favorites()
                    AccountTab.RATED -> accountRepository.rated()
                    AccountTab.HISTORY -> accountRepository.history()
                    else -> accountRepository.library(tab.status)
                }
            }
                .onSuccess { items -> _state.update { it.copy(items = items, isLoading = false) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false,
                                error = error.message ?: "Список не загрузился")
                    }
                }
        }
    }

    // ------------------------------------------------------------ приватность

    /**
     * Настройки приватности: что из профиля видно другим.
     *
     * Профиль открывается по имени автора комментария, поэтому каждый решает
     * сам, показывать ли списки, оценки и — отдельно — историю чтения.
     */
    private fun loadPrivacy() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, items = emptyList()) }
            runCatching { accountRepository.privacy() }
                .onSuccess { privacy ->
                    _state.update { it.copy(privacy = privacy, isLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false,
                                error = error.message ?: "Настройки не загрузились")
                    }
                }
        }
    }

    fun updatePrivacy(change: (com.mimanga.app.domain.model.PrivacySettings) ->
                      com.mimanga.app.domain.model.PrivacySettings) {
        val next = change(_state.value.privacy)
        _state.update { it.copy(privacy = next) }
        viewModelScope.launch {
            runCatching { accountRepository.updatePrivacy(next) }
                .onSuccess { saved -> _state.update { it.copy(privacy = saved) } }
                .onFailure { Timber.w(it, "Настройки приватности не сохранились") }
        }
    }

    /** Убирает тайтл из истории чтения (список «читаю» остаётся). */
    fun removeFromHistory(mangaKey: String) {
        viewModelScope.launch {
            runCatching { accountRepository.removeFromHistory(mangaKey) }
                .onSuccess {
                    _state.update { state ->
                        state.copy(items = state.items.filterNot { it.mangaKey == mangaKey })
                    }
                    refreshProfile()
                }
                .onFailure { Timber.w(it, "Не удалось убрать из истории") }
        }
    }

    fun refresh() {
        refreshProfile()
        loadTab(_state.value.tab)
        _state.value.detail?.let { openDetail(it) }
    }

    // ------------------------------------------------ списки за счётчиками

    /**
     * Открывает список за счётчиком: главы, тайтлы, оценки, избранное или
     * комментарии. Каждый счётчик ведёт в свой список, а не в общую свалку.
     */
    fun openDetail(detail: AccountDetail) {
        _state.update { it.copy(detail = detail, isDetailLoading = true, error = null,
                                detailItems = emptyList(), detailChapters = emptyList(),
                                detailComments = emptyList(), detailManga = emptyMap()) }
        viewModelScope.launch {
            runCatching {
                when (detail) {
                    AccountDetail.CHAPTERS -> {
                        val response = accountRepository.readChapters()
                        _state.update { state ->
                            state.copy(detailChapters = response.results,
                                       detailManga = response.manga.byKey())
                        }
                    }
                    AccountDetail.COMMENTS -> {
                        val response = accountRepository.myComments()
                        _state.update { state ->
                            state.copy(detailComments = response.results,
                                       detailManga = response.manga.byKey())
                        }
                    }
                    AccountDetail.TITLES -> {
                        val items = accountRepository.history(limit = 200)
                        _state.update { it.copy(detailItems = items) }
                    }
                    AccountDetail.RATINGS -> {
                        val items = accountRepository.rated()
                        _state.update { it.copy(detailItems = items) }
                    }
                    AccountDetail.FAVORITES -> {
                        val items = accountRepository.favorites()
                        _state.update { it.copy(detailItems = items) }
                    }
                }
            }
                .onSuccess { _state.update { it.copy(isDetailLoading = false) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(isDetailLoading = false,
                                error = error.message ?: "Список не загрузился")
                    }
                }
        }
    }

    fun closeDetail() {
        _state.update { it.copy(detail = null, detailItems = emptyList(),
                                detailChapters = emptyList(), detailComments = emptyList(),
                                detailManga = emptyMap()) }
    }

    private fun List<Manga>.byKey(): Map<String, Manga> = associateBy { it.actionKey }
}
