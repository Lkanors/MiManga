package com.mimanga.app.feature.catalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mimanga.app.core.ui.components.EmptyState
import com.mimanga.app.core.ui.components.ErrorState
import com.mimanga.app.core.ui.components.MangaCard
import com.mimanga.app.core.ui.components.MangaRowSection
import com.mimanga.app.core.ui.components.TopLoadingBar
import com.mimanga.app.core.ui.theme.LocalAppAccents
import com.mimanga.app.domain.model.AppSettings
import com.mimanga.app.domain.model.CatalogSort
import com.mimanga.app.domain.model.Manga
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Каталог: поиск, жанры и теги, сортировка.
 *
 * Только список тайтлов — популярное, история и рекомендации живут на вкладке
 * «Главное». Боковая панель отбирает по жанрам, тегам, статусу, числу глав,
 * годам и оценке, а сортировка вынесена отдельным списком НАД сеткой.
 *
 * Системная кнопка «назад» закрывает панель фильтров, а если она уже закрыта —
 * сбрасывает поиск и выбранные фильтры.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    settings: AppSettings,
    onMangaClick: (Manga) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Панель закрывается — клавиатура уходит вместе с ней. Внутри панели есть
    // поиск по жанрам и тегам, и после закрытия клавиатура оставалась висеть
    // поверх каталога, закрывая половину сетки. Следим за targetValue, а не за
    // isClosed: панель закрывают и кнопкой, и свайпом, и «назад», и по нажатию
    // на затемнение — все эти пути ведут сюда.
    LaunchedEffect(drawerState.targetValue) {
        if (drawerState.targetValue == DrawerValue.Closed) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    // «Назад» сначала закрывает боковую панель, потом снимает поиск и фильтры —
    // и только потом выходит из вкладки.
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    BackHandler(enabled = drawerState.isClosed && (state.isSearching || !state.filters.isEmpty)) {
        viewModel.clearQuery()
        viewModel.clearFilters()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
            ) {
                FilterPanel(
                    options = state.options,
                    filters = state.filters,
                    total = state.total,
                    // Фильтр применяется сразу при выборе, а кнопка внизу
                    // просто закрывает панель: так видно, что выбор работает.
                    onChange = viewModel::applyFilters,
                    onClose = { scope.launch { drawerState.close() } },
                    onClear = { viewModel.clearFilters() },
                )
            }
        },
    ) {
        Column(modifier = modifier.fillMaxSize()) {
            CatalogTopBar(
                query = state.query,
                onQueryChange = viewModel::onQueryChanged,
                sort = state.sort,
                sorts = CatalogSort.entries,
                onSortChange = viewModel::setSort,
                filtersCount = state.filters.activeCount,
                showSort = !state.isSearching,
                onOpenFilters = { scope.launch { drawerState.open() } },
                onBack = null,
            )
            TopLoadingBar(visible = state.isLoading)

            when {
                state.error != null && state.results.isEmpty() ->
                    ErrorState(state.error!!, onRetry = viewModel::refresh)

                // Свайп сверху вниз перезапрашивает выборку с первой
                // страницы — тем же жестом, что и на остальных экранах.
                else -> PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    CatalogGrid(
                        items = state.results,
                        settings = settings,
                        isLoading = state.isLoading,
                        isLoadingMore = state.isLoadingMore,
                        total = state.total,
                        resultsRevision = state.resultsRevision,
                        onMangaClick = onMangaClick,
                        onLoadMore = viewModel::loadMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    sort: CatalogSort,
    sorts: List<CatalogSort>,
    onSortChange: (CatalogSort) -> Unit,
    filtersCount: Int,
    showSort: Boolean,
    onOpenFilters: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val accents = LocalAppAccents.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(accents.screenGlow)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Поиск манги") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            Box {
                IconButton(onClick = onOpenFilters) {
                    Icon(Icons.Default.FilterList, contentDescription = "Фильтры")
                }
                if (filtersCount > 0) {
                    Text(
                        filtersCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 5.dp),
                    )
                }
            }
        }
        if (showSort) {
            SortDropdown(sort = sort, sorts = sorts, onSortChange = onSortChange,
                         modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        }
    }
}

/** Сортировка — выпадающим списком НАД сеткой, а не в боковой панели. */
@Composable
private fun SortDropdown(
    sort: CatalogSort,
    sorts: List<CatalogSort>,
    onSortChange: (CatalogSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(Icons.Default.SwapVert, contentDescription = null,
                 modifier = Modifier.size(16.dp),
                 tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "  ${sort.title}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sorts.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(option.title,
                             fontWeight = if (option == sort) FontWeight.Bold else FontWeight.Normal)
                    },
                    onClick = { expanded = false; onSortChange(option) },
                )
            }
        }
    }
}

@Composable
private fun CatalogGrid(
    items: List<Manga>,
    settings: AppSettings,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    total: Int,
    /** Растёт на каждую новую выдачу — по нему сетка перематывается наверх. */
    resultsRevision: Int,
    onMangaClick: (Manga) -> Unit,
    onLoadMore: () -> Unit,
) {
    if (items.isEmpty()) {
        if (!isLoading) {
            EmptyState(title = "Ничего не нашлось",
                       subtitle = "Попробуйте изменить запрос или снять часть фильтров",
                       icon = "🔍")
        }
        return
    }
    val gridState = rememberLazyGridState()
    // Новая выдача — новый список, и смотреть его надо с начала. Иначе после
    // смены поискового запроса человек оставался на той же высоте прокрутки,
    // посреди тайтлов, которых он в этой выдаче не видел.
    LaunchedEffect(resultsRevision) {
        if (resultsRevision > 0) gridState.scrollToItem(0)
    }
    // Догрузка следующей страницы: следим за последней видимой карточкой и
    // просим ещё, когда до конца осталось меньше двух строк.
    //
    // Почему через snapshotFlow, а не derivedStateOf с флагом «пора грузить»:
    // флаг оставался поднятым, и повторно он не срабатывал — каталог
    // догружался ровно один раз, а дальше упирался в конец списка. Вдобавок
    // размер списка попадал в замыкание один раз при первой композиции, то
    // есть порог считался от самой первой страницы навсегда. Ключ items.size
    // перезапускает наблюдение на каждой новой странице, а сам snapshotFlow
    // ничего не перекомпоновывает — проверка идёт в сторонке от отрисовки.
    LaunchedEffect(gridState, items.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (last >= items.size - 6) onLoadMore() }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(settings.gridColumns),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // contentType: в сетке кроме карточек есть кружок догрузки, и без
        // указания типа Compose пытался переиспользовать одно под другое.
        items(items, key = { it.id }, contentType = { "card" }) { manga ->
            MangaCard(
                manga = manga,
                onClick = { onMangaClick(manga) },
                showRating = settings.showRatingOnCard,
                showChapters = settings.showChaptersOnCard,
                showStatusBadge = settings.showStatusBadge,
                compact = settings.compactCards,
            )
        }
        if (isLoadingMore) {
            // span на всю строку: без него кружок занимал ОДНУ клетку сетки и
            // оказывался под первой карточкой слева, а не по центру экрана.
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
