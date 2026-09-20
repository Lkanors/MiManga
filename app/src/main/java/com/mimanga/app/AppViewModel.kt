package com.mimanga.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimanga.app.domain.model.AppSettings
import com.mimanga.app.domain.repository.AccountRepository
import com.mimanga.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Настройки для всего приложения: тема и вид карточек нужны и оболочке
 * (MainActivity), и экранам, поэтому читаются один раз здесь.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    init {
        // Профиль читается при запуске, а не только при заходе на вкладку
        // аккаунта: из него берутся права на показ (см. ServerImages), а
        // каталог открывается раньше, чем человек доберётся до аккаунта.
        viewModelScope.launch {
            accountRepository.token.collect { token ->
                if (token == null) return@collect
                runCatching { accountRepository.account() }
                    .onFailure { Timber.w(it, "Профиль при запуске не загрузился") }
            }
        }
    }
}
