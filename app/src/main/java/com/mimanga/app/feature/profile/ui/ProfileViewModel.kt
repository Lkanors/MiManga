package com.mimanga.app.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimanga.app.core.network.userMessage
import com.mimanga.app.domain.model.PublicProfile
import com.mimanga.app.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileState(
    val profile: PublicProfile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private var loaded: String? = null

    fun load(username: String) {
        if (loaded == username) return
        loaded = username
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { accountRepository.profile(username) }
                .onSuccess { profile -> _state.update { it.copy(profile = profile, isLoading = false) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false,
                                error = error.userMessage("Профиль не открылся"))
                    }
                }
        }
    }
}
