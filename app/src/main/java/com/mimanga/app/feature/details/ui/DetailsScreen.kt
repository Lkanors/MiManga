package com.mimanga.app.feature.details.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mimanga.app.core.ui.stripHtml
import com.mimanga.app.core.network.ServerImages
import com.mimanga.app.core.ui.components.CoverBackdrop
import com.mimanga.app.core.ui.components.CoverImage
import com.mimanga.app.core.ui.components.MangaRowCard
import com.mimanga.app.core.ui.components.TagChip
import com.mimanga.app.core.ui.components.formatCount
import com.mimanga.app.core.ui.components.formatRating
import com.mimanga.app.core.ui.components.statusColor
import com.mimanga.app.core.ui.theme.LocalAppAccents
import com.mimanga.app.domain.model.Chapter
import com.mimanga.app.domain.model.ChapterProgress
import com.mimanga.app.domain.model.Comment
import com.mimanga.app.domain.model.LibraryStatus
import com.mimanga.app.domain.model.mangaStatusTitle
import com.mimanga.app.domain.model.Manga
import com.mimanga.app.domain.model.MangaSource
import com.mimanga.app.feature.details.state.DetailsState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog

/**
 * Страница тайтла: обложка, оценка с числом оценок, число глав, состояние в
 * библиотеке, описание, главы выбранного источника, похожее и комментарии.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    mangaParam: Manga,
    onBack: () -> Unit = {},
    onMangaClick: (Manga) -> Unit = {},
    onOpenProfile: (String) -> Unit = {},
    /** Открыть комментарии тайтла отдельным экраном. */
    onOpenComments: () -> Unit = {},
    /**
     * Продолжить чтение, когда список глав ещё не загружен: источник, ключ
     * главы («источник:ссылка») и страница. Читалка догрузит главы сама.
     */
    onContinuePending: (MangaSource, String?, Int) -> Unit = { _, _, _ -> },
    /** Глава, источник, весь список и страница, с которой открыть главу. */
    onReadChapter: (Chapter, MangaSource, List<Chapter>, Int) -> Unit = { _, _, _, _ -> },
    // Ключ — постоянный ключ тайтла, а не id: id у карточки бывает и
    // «сборный», и из разных списков приходит разным, а ViewModel живёт
    // дольше экрана, и перепутать в ней тайтлы нельзя.
    viewModel: DetailsViewModel = hiltViewModel(key = "detail-${mangaParam.actionKey}"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var ratingDialog by remember { mutableStateOf(false) }
    var descriptionExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(mangaParam.actionKey) { viewModel.initFromManga(mangaParam) }
    // Возврат из читалки — перечитываем места остановки: полоски под главами
    // должны показывать то, что человек только что прочитал.
    LaunchedEffect(Unit) { viewModel.refreshProgress() }

    val manga = state.manga ?: mangaParam
    // Разметку сайта-источника («<p dir="ltr">», «&amp;») пользователь видеть
    // не должен: сервер её снимает, клиент подстраховывается.
    val description = remember(manga.description) { manga.description.stripHtml() }

    // Свайп сверху вниз перечитывает карточку: главы на источниках выходят
    // постоянно, и «обновить» должно быть тем же жестом, что и везде.
    PullToRefreshBox(
        isRefreshing = state.isLoadingDetail || state.isLoadingChapters,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                DetailsHeader(
                    manga = manga,
                    state = state,
                    onBack = onBack,
                    onFavorite = viewModel::toggleFavorite,
                    // Оценку ставят только зарегистрированные: она идёт в
                    // общий рейтинг тайтла и должна быть привязана к аккаунту.
                    onRate = {
                        if (state.isLoggedIn) ratingDialog = true
                        else viewModel.notifyLoginNeeded()
                    },
                    onRead = {
                        val source = state.selectedSource
                        val chapters = state.chapters
                        val last = state.lastRead
                        when {
                            // «Продолжить» ведёт ровно туда, где человек
                            // остановился на ЭТОМ источнике; если он ничего
                            // здесь не читал — на первую главу.
                            source != null && chapters.isNotEmpty() -> {
                                val index = chapters.indexOfFirst {
                                    it.sourceId + ":" + it.url == last?.chapterKey
                                }
                                if (last != null && index >= 0) {
                                    onReadChapter(chapters[index], source, chapters, last.page)
                                } else {
                                    onReadChapter(chapters.first(), source, chapters, 0)
                                }
                            }
                            // Главы ещё грузятся — кнопка всё равно должна
                            // работать сразу: читалка откроется и догрузит их.
                            source != null ->
                                onContinuePending(source, last?.chapterKey, last?.page ?: 0)
                        }
                    },
                )
            }

            item { StatusRow(state.libraryStatus, viewModel::setStatus) }

            if (description.isNotBlank()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (descriptionExpanded) Int.MAX_VALUE else 5,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { descriptionExpanded = !descriptionExpanded },
                        )
                        if (!descriptionExpanded && description.length > 240) {
                            TextButton(onClick = { descriptionExpanded = true },
                                       contentPadding = PaddingValues(0.dp)) {
                                Text("Читать дальше")
                            }
                        }
                    }
                }
            }

            if (manga.tags.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(manga.tags) { tag -> TagChip(tag) }
                    }
                }
            }

            item {
                SourcesRow(
                    sources = manga.sources,
                    selected = state.selectedSource,
                    onSelect = viewModel::selectSource,
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Главы", style = MaterialTheme.typography.titleLarge)
                    if (state.isLoadingChapters) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Text("${state.chapters.size}",
                             style = MaterialTheme.typography.labelMedium,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Пустой список глав раньше выглядел как ошибка приложения:
            // заголовок «Главы 0» и пустота под ним. Причины две, и обе стоит
            // называть — сеть не ответила или у источника этих глав нет.
            if (!state.isLoadingChapters && state.chapters.isEmpty()) {
                item {
                    Text(
                        text = state.error
                            ?: if (manga.sources.isEmpty()) {
                                "У этого тайтла нет источников с главами"
                            } else {
                                "В этом источнике нет ни одной главы — выберите другой"
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            items(state.chapters, key = { "${it.sourceId}:${it.url}" }) { chapter ->
                val chapterProgress = state.progressOf(chapter)
                ChapterRow(chapter, chapterProgress) {
                    val source = state.selectedSource
                    // Недочитанная глава открывается на той же странице, где
                    // её закрыли; дочитанная — с начала.
                    val page = if (chapterProgress != null && !chapterProgress.isRead) {
                        chapterProgress.page
                    } else 0
                    if (source != null) onReadChapter(chapter, source, state.chapters, page)
                }
            }

            if (state.similar.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text("Похожее", style = MaterialTheme.typography.titleLarge,
                             modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.similar, key = { it.id }) { item ->
                                MangaRowCard(manga = item, onClick = { onMangaClick(item) })
                            }
                        }
                    }
                }
            }

            // Обсуждение живёт на своём экране: на карточке оно тонуло под
            // списком глав, а поле ввода уезжало под клавиатуру.
            item {
                CommentsButton(
                    total = state.commentsTotal,
                    preview = state.comments.firstOrNull(),
                    onClick = onOpenComments,
                )
            }
        }
    }

    if (ratingDialog) {
        RatingDialog(
            current = state.userRating,
            onDismiss = { ratingDialog = false },
            onRate = { value -> viewModel.rate(value); ratingDialog = false },
            onRemove = { viewModel.removeRating(); ratingDialog = false },
        )
    }
}

/**
 * Полное название тайтла отдельным окном.
 *
 * В шапке название обрезано тремя строками, а у части тайтлов оно длиной в
 * абзац — вместе со всеми альтернативными названиями это единственное место,
 * где его видно целиком. Окно закрывается крестиком, кнопкой «назад» и
 * нажатием мимо: системную «назад» Dialog разбирает сам, и до общей навигации
 * приложения она не доходит.
 */
@Composable
private fun FullTitleDialog(manga: Manga, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(start = 20.dp, end = 8.dp,
                                               top = 12.dp, bottom = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Название",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(end = 12.dp),
                ) {
                    // Название можно выделить и скопировать: его часто ищут
                    // на других сайтах.
                    SelectionContainer {
                        Text(manga.title, style = MaterialTheme.typography.titleLarge)
                    }
                    val others = manga.altTitles.filter { it.isNotBlank() && it != manga.title }
                    if (others.isNotEmpty()) {
                        Text(
                            "Другие названия",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                        )
                        SelectionContainer {
                            Column {
                                others.forEach { alt ->
                                    Text(alt, style = MaterialTheme.typography.bodyMedium,
                                         modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsHeader(
    manga: Manga,
    state: DetailsState,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onRate: () -> Unit,
    onRead: () -> Unit,
) {
    val accents = LocalAppAccents.current
    var titleDialog by remember { mutableStateOf(false) }
    if (titleDialog) {
        FullTitleDialog(manga = manga, onClose = { titleDialog = false })
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        // Размытая обложка вместо однотонной шапки: цвет берётся у самой манги,
        // поэтому экран у каждого тайтла свой, но без пестроты. Размытие
        // делается растяжением крошечной копии обложки, а не Modifier.blur —
        // см. CoverBackdrop: тот пересчитывался каждый кадр прокрутки.
        CoverBackdrop(
            manga = manga,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x66000000), MaterialTheme.colorScheme.background)
                    )
                ),
        )
        Column {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад",
                         tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "В избранное",
                        tint = if (state.isFavorite) accents.favorite else Color.White,
                    )
                }
            }
            Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                CoverImage(
                    manga = manga,
                    width = ServerImages.PAGE_WIDTH,
                    modifier = Modifier
                        .width(126.dp)
                        .aspectRatio(0.68f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Column(modifier = Modifier
                    .padding(start = 14.dp)
                    .weight(1f)) {
                    // Название нажимается: длинные названия здесь всё равно
                    // обрезаются тремя строками, и прочитать их целиком было
                    // негде.
                    Text(manga.title, style = MaterialTheme.typography.headlineSmall,
                         maxLines = 3, overflow = TextOverflow.Ellipsis,
                         modifier = Modifier.clickable { titleDialog = true })
                    manga.altTitles.firstOrNull()?.let { alt ->
                        Text(alt, style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant,
                             maxLines = 2, overflow = TextOverflow.Ellipsis,
                             modifier = Modifier
                                 .padding(top = 2.dp)
                                 .clickable { titleDialog = true })
                    }
                    Spacer(Modifier.height(10.dp))
                    // Оценка и число оценивших + число глав — как на карточке.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = accents.rating,
                             modifier = Modifier.size(16.dp))
                        Text(
                            "  ${formatRating(state.rating?.rating ?: manga.rating)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val votes = state.rating?.votes ?: manga.ratingCount
                        if (votes > 0) {
                            Text("  (${formatCount(votes)})",
                                 style = MaterialTheme.typography.labelMedium,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (manga.chaptersCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, null,
                                 tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                 modifier = Modifier.size(15.dp))
                            Text("  ${manga.chaptersCount} глав",
                                 style = MaterialTheme.typography.labelMedium,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        listOfNotNull(
                            // Тип («Манхва») стоит первым: по нему сразу
                            // понятно, чего ждать от нарезки и цвета.
                            manga.kindTitle.ifBlank { null },
                            // Такой тайтл показывается только тем, кто
                            // подтвердил возраст, — пометка объясняет, почему
                            // его нет у остальных.
                            "18+".takeIf { manga.isAdult },
                            mangaStatusTitle(manga.status),
                            manga.year?.toString(),
                            manga.authors.firstOrNull(),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = onRead, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    // Если на этом источнике уже читали — ведём туда, где
                    // остановились, и честно называем кнопку.
                    val last = state.lastRead
                    Text(
                        if (last != null) {
                            "  Продолжить · ${last.chapterTitle.ifBlank { "гл. ${last.chapterNumber}" }}"
                        } else "  Читать",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(onClick = onRate) {
                    Icon(Icons.Default.Star, null, modifier = Modifier.size(18.dp))
                    Text(
                        when {
                            state.userRating != null -> "  ${formatRating(state.userRating)}"
                            !state.isLoggedIn -> "  Войдите"
                            else -> "  Оценить"
                        }
                    )
                }
            }
        }
    }
}

/** Вкладки состояния: читаю, в планах, заброшено, прочитано. */
@Composable
private fun StatusRow(current: LibraryStatus?, onSelect: (LibraryStatus?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryStatus.entries.forEach { status ->
            val selected = current == status
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) statusColor(status).copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .clickable { onSelect(status) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    status.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) statusColor(status)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SourcesRow(
    sources: List<MangaSource>,
    selected: MangaSource?,
    onSelect: (MangaSource) -> Unit,
) {
    if (sources.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Источник", style = MaterialTheme.typography.labelMedium,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            Row(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected?.displayName ?: "Выбрать",
                     style = MaterialTheme.typography.titleSmall)
                Text("  ▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                sources.forEach { source ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                source.displayName + if (source.isForeign) "  (EN)" else "",
                                fontWeight = if (source.sourceId == selected?.sourceId)
                                    FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = { expanded = false; onSelect(source) },
                    )
                }
            }
        }
    }
}

/**
 * Строка главы.
 *
 * Прочитанная до конца глава показана приглушённой, недочитанная выглядит как
 * обычная, но под ней — тонкая полоска: видно, сколько осталось. Прогресс свой
 * у каждого источника, поэтому строка получает его снаружи.
 */
@Composable
private fun ChapterRow(
    chapter: Chapter,
    progress: ChapterProgress?,
    onClick: () -> Unit,
) {
    val isRead = progress?.isRead == true
    val titleColor = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                     else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = if (isRead) Modifier.background(
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
        ) else Modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    chapter.title.ifBlank { "Глава ${chapter.number}" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress != null && progress.isStarted) {
                    Text(
                        "Остановились на ${progress.page + 1} из ${progress.pages}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Text(
                if (isRead) "прочитано" else chapter.sourceName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (progress != null && progress.position > 0f && !isRead) {
            LinearProgressIndicator(
                progress = { progress.position.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * Вход в комментарии: сколько их и последняя реплика одной строкой.
 *
 * Показать «последнее сказанное» важнее, чем число: по нему видно, живое
 * обсуждение или пустое, и стоит ли туда заходить.
 */
@Composable
private fun CommentsButton(
    total: Int,
    preview: Comment?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (total > 0) "Комментарии · $total" else "Комментарии",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                preview?.let { "${it.username}: ${it.text}" } ?: "Пока никто ничего не написал",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text("›", style = MaterialTheme.typography.headlineSmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


@Composable
private fun RatingDialog(
    current: Float?,
    onDismiss: () -> Unit,
    onRate: (Float) -> Unit,
    onRemove: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ваша оценка") },
        text = {
            Column {
                Text("Оценка идёт в общий рейтинг вместе с оценками сайтов-источников.",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                (1..10).chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)) {
                        row.forEach { value ->
                            val selected = current?.toInt() == value
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    .clickable { onRate(value.toFloat()) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    value.toString(),
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (current != null) TextButton(onClick = onRemove) { Text("Убрать оценку") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
