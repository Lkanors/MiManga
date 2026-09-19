package com.mimanga.app.feature.account.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mimanga.app.core.ui.components.EmptyState
import com.mimanga.app.core.ui.components.MangaCard
import com.mimanga.app.core.ui.components.TopLoadingBar
import com.mimanga.app.core.ui.components.formatCount
import com.mimanga.app.core.ui.components.statusColor
import com.mimanga.app.core.ui.theme.LocalAppAccents
import com.mimanga.app.domain.model.Account
import com.mimanga.app.domain.model.AppSettings
import com.mimanga.app.domain.model.Manga
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import com.mimanga.app.domain.model.PrivacySettings
import com.mimanga.app.feature.account.state.AccountDetail
import com.mimanga.app.feature.account.state.AccountState
import com.mimanga.app.feature.account.state.AccountTab
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Страница аккаунта: счётчик прочитанных глав, избранное, вкладки состояний
 * (читаю / в планах / заброшено / прочитано), оценённые тайтлы и история
 * чтения — всё в одном месте.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    settings: AppSettings,
    onMangaClick: (Manga) -> Unit,
    modifier: Modifier = Modifier,
    initialTab: AccountTab = AccountTab.READING,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Вкладка «Избранное» в нижней панели открывает этот же экран сразу на
    // нужной вкладке — но только в первый раз. Возвращаясь с карточки тайтла,
    // человек должен попадать туда, где он был, и видеть свежие списки и
    // счётчики: меняются они на странице тайтла, а не здесь.
    LaunchedEffect(initialTab) { viewModel.openTab(initialTab) }

    if (!state.isLoggedIn) {
        AuthForm(
            registerMode = state.registerMode,
            username = state.username,
            password = state.password,
            birthDate = state.birthDate,
            isSubmitting = state.isSubmitting,
            error = state.authError,
            onUsername = viewModel::setUsername,
            onPassword = viewModel::setPassword,
            onBirthDate = viewModel::setBirthDate,
            onSubmit = viewModel::submit,
            onToggleMode = viewModel::toggleMode,
            modifier = modifier,
        )
        return
    }

    // Список за счётчиком — отдельный экран поверх аккаунта; «назад»
    // возвращает к профилю, а не выкидывает из вкладки.
    val detail = state.detail
    if (detail != null) {
        BackHandler { viewModel.closeDetail() }
        AccountDetailScreen(
            detail = detail,
            state = state,
            settings = settings,
            onBack = viewModel::closeDetail,
            onMangaClick = onMangaClick,
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        ProfileHeader(
            account = state.account,
            onLogout = viewModel::logout,
            onOpenDetail = viewModel::openDetail,
        )
        TabsRow(current = state.tab, account = state.account, onSelect = viewModel::selectTab)
        TopLoadingBar(visible = state.isLoading)

        // Свайп сверху вниз обновляет вкладку целиком — и список, и счётчики.
        // Он охватывает все состояния, включая пустое: на пустой вкладке
        // обновить как раз и хочется (тайтл только что отметили на его
        // странице). Пустым состояниям для этого нужна прокрутка — без неё
        // жест некуда передать.
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.tab == AccountTab.PRIVACY -> PrivacySection(
                    privacy = state.privacy,
                    account = state.account,
                    adultError = state.adultError,
                    onChange = viewModel::updatePrivacy,
                    onBirthDate = viewModel::submitBirthDate,
                    onAdultVisibility = viewModel::setAdultVisibility,
                )

                state.error != null && state.items.isEmpty() ->
                    ScrollableCenter {
                        EmptyState(title = "Не получилось", subtitle = state.error,
                                   action = "Обновить", onAction = viewModel::refresh, icon = "⚠️")
                    }

                state.items.isEmpty() && !state.isLoading -> ScrollableCenter {
                    EmptyState(
                        title = emptyTitle(state.tab),
                        subtitle = "Отмечайте тайтлы на их странице — они появятся здесь",
                        icon = "✨",
                    )
                }

                else ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(settings.gridColumns),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.items, key = { it.id }) { manga ->
                        Box {
                            MangaCard(
                                manga = manga,
                                onClick = { onMangaClick(manga) },
                                showRating = settings.showRatingOnCard,
                                showChapters = settings.showChaptersOnCard,
                                showStatusBadge = settings.showStatusBadge,
                                compact = settings.compactCards,
                            )
                            if (state.tab == AccountTab.HISTORY) {
                                Text(
                                    "✕",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(androidx.compose.ui.graphics.Color(0x99000000))
                                        .clickable { viewModel.removeFromHistory(manga.actionKey) }
                                        .padding(horizontal = 7.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Пустое состояние внутри жеста «потянуть и обновить».
 *
 * Жест передаётся через прокрутку, а прокручивать в пустом состоянии нечего —
 * поэтому оно кладётся в прокручиваемый на всю высоту столбец. Иначе на пустой
 * вкладке потянуть вниз было бы нельзя.
 */
@Composable
private fun ScrollableCenter(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screen = maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Ровно экран высотой: прокручивать нечего, но жест есть чему
            // передать — этого достаточно, чтобы сработало обновление.
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(screen)) { content() }
        }
    }
}

/**
 * Настройки приватности профиля.
 *
 * Профиль открывается по имени автора комментария, поэтому человек сам
 * решает, что показывать. История чтения по умолчанию закрыта — это самое
 * личное из списка.
 */
@Composable
private fun PrivacySection(
    privacy: PrivacySettings,
    account: Account?,
    adultError: String?,
    onChange: ((PrivacySettings) -> PrivacySettings) -> Unit,
    onBirthDate: (String) -> Unit,
    onAdultVisibility: (Boolean) -> Unit,
) {
    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())) {
        AdultSection(account = account, error = adultError,
                     onBirthDate = onBirthDate, onVisibility = onAdultVisibility)
        Text(
            "Что видят другие",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp),
        )
        PrivacyRow("Открытый профиль",
                   "Без этого другие видят только имя", privacy.profilePublic) { value ->
            onChange { it.copy(profilePublic = value) }
        }
        PrivacyRow("Счётчики прочитанного", null, privacy.showStats) { value ->
            onChange { it.copy(showStats = value) }
        }
        PrivacyRow("Списки", "Читаю, в планах, заброшено, прочитано", privacy.showLibrary) { value ->
            onChange { it.copy(showLibrary = value) }
        }
        PrivacyRow("Избранное", null, privacy.showFavorites) { value ->
            onChange { it.copy(showFavorites = value) }
        }
        PrivacyRow("Оценки", "Какие тайтлы и как вы оценили", privacy.showRatings) { value ->
            onChange { it.copy(showRatings = value) }
        }
        PrivacyRow("Комментарии", null, privacy.showComments) { value ->
            onChange { it.copy(showComments = value) }
        }
        PrivacyRow("История чтения", "Что и когда вы читали", privacy.showHistory) { value ->
            onChange { it.copy(showHistory = value) }
        }
    }
}

/**
 * Возраст и материалы 18+.
 *
 * Дата рождения задаётся один раз: возраст — не настройка. Пока её нет,
 * каталог показывается без 18+, и здесь об этом сказано прямо — иначе
 * человеку остаётся гадать, почему часть тайтлов не находится.
 */
@Composable
private fun AdultSection(
    account: Account?,
    error: String?,
    onBirthDate: (String) -> Unit,
    onVisibility: (Boolean) -> Unit,
) {
    Text(
        "Возраст и 18+",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp),
    )
    val birthDate = account?.birthDate.orEmpty()
    if (birthDate.isBlank()) {
        Text(
            "Пока дата рождения не указана, материалы 18+ не показываются. " +
                "Указать её можно один раз.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        BirthDateField(
            value = "",
            label = "Дата рождения",
            hint = null,
            onPick = onBirthDate,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Дата рождения", style = MaterialTheme.typography.bodyLarge)
                Text(
                    formatBirthDate(birthDate) +
                        (account?.age?.let { " · $it полных лет" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (account?.isAdultByAge == true) {
            PrivacyRow(
                "Показывать 18+",
                "Хентай, эротика и подобное в каталоге, поиске и подборках",
                account.showAdult,
                onVisibility,
            )
        } else {
            Text(
                "Материалы 18+ показываются с ${account?.adultAge ?: 18} лет.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
    }
    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error,
             style = MaterialTheme.typography.bodySmall,
             modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    }
}

@Composable
private fun PrivacyRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun emptyTitle(tab: AccountTab): String = when (tab) {
    AccountTab.FAVORITES -> "В избранном пусто"
    AccountTab.RATED -> "Вы пока ничего не оценили"
    AccountTab.HISTORY -> "История пуста"
    else -> "Список «${tab.title}» пуст"
}

@Composable
private fun ProfileHeader(
    account: Account?,
    onLogout: () -> Unit,
    onOpenDetail: (AccountDetail) -> Unit,
) {
    val accents = LocalAppAccents.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(accents.screenGlow)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accents.brand),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (account?.username ?: "?").take(1).uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
            Column(modifier = Modifier
                .padding(start = 14.dp)
                .weight(1f)) {
                Text(account?.username ?: "Аккаунт",
                     style = MaterialTheme.typography.titleLarge)
                Text(
                    "Прочитано глав: ${formatCount(account?.readChapters ?: 0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onLogout) { Text("Выйти") }
        }

        // Счётчики — это кнопки: по каждому открывается свой список.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTile("Глав", account?.readChapters ?: 0, Modifier.weight(1f)) {
                onOpenDetail(AccountDetail.CHAPTERS)
            }
            StatTile("Тайтлов", account?.readTitles ?: 0, Modifier.weight(1f)) {
                onOpenDetail(AccountDetail.TITLES)
            }
            StatTile("Оценок", account?.rated ?: 0, Modifier.weight(1f)) {
                onOpenDetail(AccountDetail.RATINGS)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTile("Избранного", account?.favorites ?: 0, Modifier.weight(1f)) {
                onOpenDetail(AccountDetail.FAVORITES)
            }
            StatTile("Комментариев", account?.comments ?: 0, Modifier.weight(1f)) {
                onOpenDetail(AccountDetail.COMMENTS)
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(formatCount(value), style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant,
             textAlign = TextAlign.Center)
    }
}

@Composable
private fun TabsRow(current: AccountTab, account: Account?, onSelect: (AccountTab) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(AccountTab.entries) { tab ->
            val selected = tab == current
            val color = tab.status?.let { statusColor(it) } ?: MaterialTheme.colorScheme.primary
            val count = when (tab) {
                AccountTab.FAVORITES -> account?.favorites
                AccountTab.RATED -> account?.rated
                AccountTab.HISTORY -> account?.readTitles
                else -> tab.status?.let { account?.count(it) }
            } ?: 0
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) color.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color))
                Text(
                    "  ${tab.title}" + if (count > 0) "  $count" else "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Дата «ГГГГ-ММ-ДД» в привычном виде «15.06.1990»; пусто — «не указана». */
private fun formatBirthDate(value: String): String = runCatching {
    val date = LocalDate.parse(value)
    "%02d.%02d.%d".format(date.dayOfMonth, date.monthValue, date.year)
}.getOrDefault(value)

/**
 * Выбор даты рождения.
 *
 * Поле только для чтения, а дату выбирают в календаре: ввод руками здесь
 * означал бы разбор «15.06.90», «15/06/1990» и прочих написаний, а серверу
 * нужен один вид — ГГГГ-ММ-ДД.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDateField(
    value: String,
    label: String,
    hint: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = if (value.isBlank()) "" else formatBirthDate(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Выберите дату") },
            supportingText = hint?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Нажатие по полю только для чтения до него не доходит — поверх лежит
        // прозрачный слой, он и открывает календарь.
        Box(modifier = Modifier
            .matchParentSize()
            .clickable { showPicker = true })
    }
    if (showPicker) {
        val today = LocalDate.now()
        val chosen = runCatching { LocalDate.parse(value) }.getOrNull()
        val state = rememberDatePickerState(
            // По умолчанию — ровно восемнадцать лет назад: так до своей даты
            // ближе и взрослому, и подростку, чем от сегодняшнего дня.
            initialSelectedDateMillis = (chosen ?: today.minusYears(18))
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = (today.year - 100)..today.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= today.plusDays(1).atStartOfDay(ZoneOffset.UTC)
                        .toInstant().toEpochMilli()
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC)
                            .toLocalDate().toString())
                    }
                    showPicker = false
                }) { Text("Готово") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Отмена") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

/**
 * Требования к паролю. Тот же текст выдаёт сервер при отказе — человек должен
 * узнавать правило до попытки, а не после неё.
 */
private const val PASSWORD_RULES =
    "Не короче 8 символов, хотя бы одна буква и одна цифра, без пробелов"

/** Проверка на стороне клиента: ровно те же правила, что и на сервере. */
private fun passwordProblem(password: String): String? = when {
    password.length < 8 -> PASSWORD_RULES
    password.none { it.isLetter() } -> PASSWORD_RULES
    password.none { it.isDigit() } -> PASSWORD_RULES
    password.any { it.isWhitespace() } -> PASSWORD_RULES
    else -> null
}

@Composable
private fun AuthForm(
    registerMode: Boolean,
    username: String,
    password: String,
    birthDate: String,
    isSubmitting: Boolean,
    error: String?,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onBirthDate: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalAppAccents.current
    Box(modifier = modifier
        .fillMaxSize()
        .background(accents.screenGlow), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (registerMode) "Создать аккаунт" else "Вход",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "Аккаунт нужен для истории чтения, списков, оценок и комментариев",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
            )
            OutlinedTextField(
                value = username,
                onValueChange = onUsername,
                label = { Text("Имя пользователя") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            // Требования к паролю показываются только при регистрации: при
            // входе пароль уже есть, и правила там только пугают.
            val passwordError = if (registerMode && password.isNotEmpty()) {
                passwordProblem(password)
            } else null
            OutlinedTextField(
                value = password,
                onValueChange = onPassword,
                label = { Text("Пароль") },
                singleLine = true,
                isError = passwordError != null,
                supportingText = if (registerMode) {
                    { Text(passwordError ?: PASSWORD_RULES,
                           style = MaterialTheme.typography.bodySmall) }
                } else null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            // Дата рождения спрашивается сразу, а не потом: по ней сервер
            // решает, показывать ли 18+, и человек с невыясненным возрастом
            // видел бы неполный каталог, не понимая почему.
            if (registerMode) {
                Spacer(Modifier.height(12.dp))
                BirthDateField(
                    value = birthDate,
                    label = "Дата рождения",
                    hint = "Нужна, чтобы показывать материалы 18+",
                    onPick = onBirthDate,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error,
                     style = MaterialTheme.typography.bodySmall,
                     textAlign = TextAlign.Center,
                     modifier = Modifier.padding(top = 10.dp))
            }
            Button(
                onClick = onSubmit,
                // При регистрации кнопка недоступна, пока пароль не годится:
                // отказ сервера после нажатия — худший способ узнать правила.
                enabled = !isSubmitting && (!registerMode ||
                        (passwordProblem(password) == null && birthDate.isNotBlank())),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text(if (registerMode) "Зарегистрироваться" else "Войти")
                }
            }
            TextButton(onClick = onToggleMode) {
                Text(if (registerMode) "У меня уже есть аккаунт" else "Создать аккаунт")
            }
        }
    }
}

/**
 * Список за счётчиком профиля: главы, тайтлы, оценки, избранное, комментарии.
 *
 * У каждого счётчика свой вид списка: главы и комментарии — строки с текстом
 * и названием тайтла, остальное — обычная сетка карточек. Из любой строки
 * можно перейти в сам тайтл.
 */
@Composable
private fun AccountDetailScreen(
    detail: AccountDetail,
    state: AccountState,
    settings: AppSettings,
    onBack: () -> Unit,
    onMangaClick: (Manga) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalAppAccents.current
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(accents.screenGlow)
                .padding(end = 16.dp, top = 4.dp, bottom = 10.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(detail.title, style = MaterialTheme.typography.titleLarge)
                Text(detail.subtitle, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TopLoadingBar(visible = state.isDetailLoading)

        val isEmpty = when (detail) {
            AccountDetail.CHAPTERS -> state.detailChapters.isEmpty()
            AccountDetail.COMMENTS -> state.detailComments.isEmpty()
            else -> state.detailItems.isEmpty()
        }
        if (isEmpty && !state.isDetailLoading) {
            EmptyState(
                title = "Пока пусто",
                subtitle = when (detail) {
                    AccountDetail.CHAPTERS -> "Откройте любую главу — она появится здесь"
                    AccountDetail.COMMENTS -> "Вы ещё ничего не написали"
                    AccountDetail.RATINGS -> "Оцените тайтл на его странице"
                    AccountDetail.FAVORITES -> "Отмечайте тайтлы сердечком"
                    AccountDetail.TITLES -> "Здесь будут тайтлы, которые вы читали"
                },
                icon = "✨",
            )
            return@Column
        }

        when (detail) {
            AccountDetail.CHAPTERS -> LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(state.detailChapters, key = { it.chapterKey + it.mangaKey }) { entry ->
                    TextEntryRow(
                        title = entry.title.ifBlank { "Глава ${entry.number}" },
                        subtitle = entry.mangaTitle,
                        meta = entry.readAt?.take(10).orEmpty(),
                        onClick = { state.detailManga[entry.mangaKey]?.let(onMangaClick) },
                    )
                }
            }

            AccountDetail.COMMENTS -> LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(state.detailComments, key = { it.id }) { comment ->
                    TextEntryRow(
                        title = comment.mangaTitle.ifBlank { "Тайтл" },
                        subtitle = comment.text,
                        meta = comment.createdAt?.take(10).orEmpty(),
                        onClick = { state.detailManga[comment.mangaKey]?.let(onMangaClick) },
                    )
                }
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(settings.gridColumns),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.detailItems, key = { it.id }) { manga ->
                    MangaCard(
                        manga = manga,
                        onClick = { onMangaClick(manga) },
                        showRating = settings.showRatingOnCard,
                        showChapters = settings.showChaptersOnCard,
                        showStatusBadge = settings.showStatusBadge,
                        compact = settings.compactCards,
                    )
                }
            }
        }
    }
}

/** Строка списка глав или комментариев: заголовок, текст и дата. */
@Composable
private fun TextEntryRow(
    title: String,
    subtitle: String,
    meta: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                 maxLines = 1, overflow = TextOverflow.Ellipsis,
                 modifier = Modifier.weight(1f))
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                 maxLines = 2, overflow = TextOverflow.Ellipsis,
                 modifier = Modifier.padding(top = 2.dp))
        }
    }
}
