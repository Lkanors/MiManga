package com.mimanga.app.feature.home.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mimanga.app.R
import com.mimanga.app.core.ui.components.EmptyState
import com.mimanga.app.core.ui.components.ErrorState
import com.mimanga.app.core.ui.components.MangaRowSection
import com.mimanga.app.core.ui.components.TopLoadingBar
import com.mimanga.app.core.ui.theme.LocalAppAccents
import com.mimanga.app.domain.model.AppSettings
import com.mimanga.app.domain.model.Manga
import com.mimanga.app.feature.reader.state.ReaderNav

/**
 * Главная.
 *
 * Здесь то, что можно открыть, ничего не набирая: продолжить чтение,
 * популярное, личные рекомендации и новинки. Поиск с фильтрами живёт на
 * соседней вкладке «Каталог».
 *
 * Строка «Продолжить чтение» работает и без аккаунта: у гостя места остановки
 * хранятся в самом приложении. Нажатие на карточку в этой строке открывает
 * ровно ту главу и ту страницу, на которой человек закончил.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: AppSettings,
    onMangaClick: (Manga) -> Unit,
    onOpenReader: (ReaderNav) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accents = LocalAppAccents.current
    val listState = rememberLazyListState()

    // Экран снова на виду — перечитываем главную. Чаще всего это возвращение
    // из читалки, и строка «Продолжить чтение» должна уже содержать тот тайтл,
    // который только что читали, а не тот, что был до него.
    LaunchedEffect(Unit) { viewModel.refreshQuietly() }

    // Содержимое стало другим — показываем его с начала. Положение списка
    // переживает уход на карточку и в читалку (SaveableStateHolder), и без
    // этого человек возвращался бы на прежнюю высоту, мимо новой строки
    // сверху. Если ничего не изменилось, отметка та же и список не дёргается.
    LaunchedEffect(state.contentRevision) {
        if (state.contentRevision > 1) listState.animateScrollToItem(0)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Шапка приложения: значок и название. Вкладка и так подписана
        // «Главное», а название здесь — единственное место, где оно видно
        // изнутри приложения.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(accents.screenGlow)
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        ) {
            // Значок берётся из drawable, а НЕ из @mipmap/ic_launcher_round:
            // у иконки запуска есть вариант mipmap-anydpi-v26 (adaptive-icon
            // XML), на Android 8+ система отдаёт именно его, а painterResource
            // умеет только векторы и растр — и роняет экран.
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(38.dp).clip(CircleShape),
            )
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        TopLoadingBar(visible = state.isLoading)

        when {
            state.error != null && state.rows.isEmpty() ->
                ErrorState(state.error!!, onRetry = viewModel::load)

            state.rows.isEmpty() && !state.isLoading ->
                EmptyState(
                    title = "Пока пусто",
                    subtitle = "Сервер ещё не собрал карточки. Попробуйте позже.",
                    action = "Обновить",
                    onAction = viewModel::load,
                )

            // Свайп сверху вниз перечитывает главную: строки собираются на
            // сервере, и «обновить» должно быть жестом, а не кнопкой в углу.
            else -> PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = viewModel::load,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(state = listState,
                           contentPadding = PaddingValues(vertical = 8.dp)) {
                    // Ключ по имени строки: без него обновление главной
                    // (новая строка «продолжить чтение» сверху) считалось
                    // изменением ВСЕХ строк, и они собирались заново.
                    items(state.rows.size, key = { state.rows[it].key }) { index ->
                        val row = state.rows[index]
                        val isHistory = row.key == "history"
                        MangaRowSection(
                            title = row.title,
                            items = row.results,
                            // Обложка везде ведёт на страницу тайтла — в том
                            // числе в «продолжить чтение»: посмотреть описание
                            // и список глав нужно не реже, чем читать дальше.
                            onMangaClick = { manga -> onMangaClick(manga) },
                            showRating = settings.showRatingOnCard,
                            showChapters = settings.showChaptersOnCard,
                            showStatusBadge = settings.showStatusBadge,
                            onMangaLongClick = if (isHistory) {
                                { manga -> viewModel.removeFromHistory(manga.actionKey) }
                            } else null,
                            progressOf = if (isHistory) {
                                { manga -> state.progressOf(manga)?.position }
                            } else null,
                            captionOf = if (isHistory) {
                                { manga ->
                                    state.progressOf(manga)?.let { progress ->
                                        val percent = (progress.position * 100).toInt()
                                        // Треугольник — подсказка, что подпись
                                        // нажимается и продолжает чтение.
                                        "▶ ${progress.chapterTitle} · $percent%"
                                    }
                                }
                            } else null,
                            // А название с подписью — сразу в главу, с места
                            // остановки.
                            onTitleClick = if (isHistory) {
                                { manga -> viewModel.continueReading(manga, onOpenReader) }
                            } else null,
                        )
                    }
                    item {
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp))
                    }
                }
            }
        }
    }
}
