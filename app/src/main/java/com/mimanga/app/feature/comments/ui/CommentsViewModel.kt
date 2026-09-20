package com.mimanga.app.feature.comments.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimanga.app.core.network.userMessage
import com.mimanga.app.domain.model.Comment
import com.mimanga.app.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Комментарии одного тайтла: отдельный экран, а не хвост страницы тайтла.
 *
 * Свой ViewModel, а не общий с карточкой: экран открывается и закрывается
 * сам по себе, грузит свои страницы и не должен трогать состояние карточки.
 */
data class CommentsState(
    val comments: List<Comment> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pages: Int = 1,
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userId: Int? = null,
    val error: String? = null,
) {
    val hasMore: Boolean get() = page < pages
}

@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CommentsState())
    val state: StateFlow<CommentsState> = _state.asStateFlow()

    private var mangaKey: String = ""

    init {
        viewModelScope.launch {
            accountRepository.token.collect { token ->
                val loggedIn = token != null
                _state.update { it.copy(isLoggedIn = loggedIn) }
                if (loggedIn) {
                    runCatching { accountRepository.account() }
                        .onSuccess { account -> _state.update { it.copy(userId = account.id) } }
                        .onFailure { Timber.w(it, "Профиль не загрузился") }
                } else {
                    _state.update { it.copy(userId = null) }
                }
            }
        }
    }

    fun init(key: String) {
        if (mangaKey == key && _state.value.comments.isNotEmpty()) return
        mangaKey = key
        load(page = 1)
    }

    fun refresh() = load(page = 1)

    /** Следующая страница дописывается в конец: обсуждение читают сверху вниз. */
    fun loadMore() {
        val current = _state.value
        if (current.isLoading || !current.hasMore) return
        load(page = current.page + 1)
    }

    private fun load(page: Int) {
        val key = mangaKey.ifBlank { return }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { accountRepository.comments(key, page) }
                .onSuccess { response ->
                    _state.update { state ->
                        state.copy(
                            comments = if (page == 1) response.results
                                       else state.comments + response.results,
                            total = response.total,
                            page = response.page,
                            pages = response.pages,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false,
                                error = error.userMessage("Комментарии не загрузились"))
                    }
                }
        }
    }

    fun send(text: String) {
        val key = mangaKey.ifBlank { return }
        if (text.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSending = true, error = null) }
            runCatching { accountRepository.addComment(key, text.trim()) }
                .onSuccess { comment ->
                    // Свой комментарий встаёт первым сразу: ждать перезагрузки
                    // списка ради собственной реплики незачем.
                    _state.update {
                        it.copy(comments = listOf(comment) + it.comments,
                                total = it.total + 1, isSending = false)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isSending = false,
                                error = error.userMessage("Комментарий не отправился"))
                    }
                }
        }
    }

    fun delete(commentId: Int) {
        viewModelScope.launch {
            runCatching { accountRepository.deleteComment(commentId) }
                .onSuccess {
                    _state.update { state ->
                        state.copy(
                            comments = state.comments.filterNot { it.id == commentId },
                            total = (state.total - 1).coerceAtLeast(0),
                        )
                    }
                }
                .onFailure { Timber.w(it, "Комментарий не удалён") }
        }
    }
}
