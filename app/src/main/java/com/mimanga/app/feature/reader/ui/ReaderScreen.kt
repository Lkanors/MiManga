package com.mimanga.app.feature.reader.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.BitmapImage
import com.mimanga.app.core.network.ImageProgress
import com.mimanga.app.core.translate.TranslatedBlock
import com.mimanga.app.core.translate.TranslationResult
import com.mimanga.app.core.translate.TranslatorStatus
import com.mimanga.app.domain.model.AppSettings
import com.mimanga.app.domain.model.ReaderBackground
import androidx.hilt.navigation.compose.hiltViewModel
import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.PlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Dimension
import coil3.size.Size
import com.mimanga.app.domain.model.Page
import com.mimanga.app.feature.reader.state.ReaderElement
import com.mimanga.app.feature.reader.state.ReaderNav
import com.mimanga.app.feature.reader.state.ReadingMode
import com.mimanga.app.feature.reader.state.viewModelKey

/**
 * Экран чтения. Поддерживает два режима:
 * - VERTICAL: непрерывная лента (свайп вверх/вниз)
 * - HORIZONTAL: постраничное листание (свайп влево/вправо)
 * Главы подгружаются бесшовно в конец потока, вход в новую главу
 * сопровождается разделителем и всплывающей пометкой.
 * Тап по странице скрывает/показывает интерфейс.
 */
// statusBarsIgnoringVisibility ещё помечен экспериментальным, но замены ему
// нет: только он даёт высоту спрятанной шторки.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    nav: ReaderNav,
    settings: AppSettings = AppSettings(),
    onBack: () -> Unit = {},
    // Ключ ViewModel — тайтл, источник и открываемая глава (см. viewModelKey).
    viewModel: ReaderViewModel = hiltViewModel(key = nav.viewModelKey()),
) {
    val state by viewModel.state.collectAsState()
    val translatorStatus by viewModel.translatorStatus.collectAsState()

    LaunchedEffect(nav) {
        viewModel.init(nav)
    }

    // «Не гасить экран» из настроек: читают долго, а страница не считается
    // взаимодействием, и экран гаснет посреди главы.
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Полноэкранный режим: системные панели прячутся, пока открыта глава, и
    // возвращаются при выходе из читалки.
    DisposableEffect(settings.fullscreenReader) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (settings.fullscreenReader) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    val background = when (ReaderBackground.from(settings.readerBackground)) {
        ReaderBackground.BLACK -> Color.Black
        ReaderBackground.DARK -> Color(0xFF14141A)
        ReaderBackground.WHITE -> Color.White
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            // Отступ под шторку уведомлений — ВСЕГДА, даже когда панель
            // спрятана (`statusBarsIgnoringVisibility`). Обычный
            // statusBarsPadding в читалке не работает: в полноэкранном режиме
            // панель скрыта, её отступ равен нулю, и страница уезжала под
            // шторку — стоило смахнуть её вниз, как она ложилась прямо на
            // картинку. Фон при этом залит до самого края, поэтому сверху не
            // полоса, а продолжение страницы.
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
    ) {
        when {
            state.isLoading && state.elements.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            state.error != null -> {
                ErrorContent(
                    message = state.error ?: "",
                    onRetry = viewModel::retry,
                )
            }
            state.elements.isNotEmpty() -> {
                when (state.readingMode) {
                    ReadingMode.VERTICAL -> VerticalReader(
                        elements = state.elements,
                        onTap = viewModel::toggleChrome,
                        onVisibleChanged = viewModel::onVisibleElementChanged,
                        onNearEnd = viewModel::requestNextChapter,
                        onPageLoaded = viewModel::onPageLoaded,
                        scrollToIndex = state.scrollToIndex,
                        onScrolled = viewModel::scrollHandled,
                        translations = state.translations,
                        onBitmapReady = viewModel::translatePage,
                    )
                    ReadingMode.HORIZONTAL -> HorizontalReader(
                        elements = state.elements,
                        onTap = viewModel::toggleChrome,
                        onVisibleChanged = viewModel::onVisibleElementChanged,
                        onNearEnd = viewModel::requestNextChapter,
                        onPageLoaded = viewModel::onPageLoaded,
                        scrollToIndex = state.scrollToIndex,
                        onScrolled = viewModel::scrollHandled,
                        translations = state.translations,
                        onBitmapReady = viewModel::translatePage,
                    )
                }
            }
        }

        // Всплывающая пометка о новой главе
        ChapterToast(
            text = state.chapterToast,
            modifier = Modifier.align(Alignment.Center),
        )

        PagePrefetch(
            elements = state.elements,
            currentIndex = state.currentElementIndex,
            count = settings.prefetchPages,
            size = rememberPageSize(
                fullScreen = state.readingMode == ReadingMode.HORIZONTAL),
        )

        ReaderChrome(
            visible = state.isChromeVisible,
            mangaTitle = state.mangaTitle,
            // Пока главы догружаются, названия ещё нет — показываем только
            // источник, без «· » в начале строки.
            chapterTitle = state.chapter?.let { it.title.ifBlank { "Глава ${it.number}" } } ?: "",
            sourceName = nav.source.displayName,
            readingMode = state.readingMode,
            currentPage = state.currentPageNumber,
            totalPages = state.totalPageCount,
            loadProgress = state.loadProgress,
            isImagesLoading = state.isImagesLoading && settings.showPageProgress,
            hasNextChapter = state.hasNextChapter,
            translateEnabled = state.translateEnabled,
            translatorStatus = translatorStatus,
            onTranslateChange = viewModel::setTranslate,
            onBack = onBack,
            onModeSelected = viewModel::setReadingMode,
        )
    }
}

/**
 * Предзагрузка следующих страниц.
 *
 * Coil качает картинку, когда она появляется на экране, — при быстром
 * пролистывании это пауза на каждой странице. Здесь следующие несколько
 * страниц просят загрузиться заранее: страница ложится и в дисковый кэш, и в
 * кэш памяти — ровно тем же запросом, каким её потом покажет сама читалка
 * (см. rememberPageSize), поэтому показ обходится без повторной загрузки и
 * без повторного раскодирования. Сколько страниц вперёд — в настройках
 * (0 = не загружать заранее).
 *
 * Запросы уходят через `enqueue`, то есть живут в своей очереди загрузчика:
 * перелистнули страницу, эффект перезапустился — уже начатые загрузки
 * продолжаются, а не отменяются.
 */
@Composable
private fun PagePrefetch(elements: List<ReaderElement>, currentIndex: Int, count: Int,
                         size: Size) {
    if (count <= 0) return
    val context = LocalPlatformContext.current
    LaunchedEffect(currentIndex, elements.size, count, size) {
        val loader = SingletonImageLoader.get(context)
        elements.asSequence()
            .drop(currentIndex + 1)
            .filterIsInstance<ReaderElement.PageElement>()
            .take(count)
            .forEach { element ->
                val page = element.page
                loader.enqueue(
                    ImageRequest.Builder(context)
                        .data(page.url)
                        .size(size)
                        .listener(onError = { _, _ ->
                            // CDN не пустил по прямой ссылке — греем запасной
                            // путь, тот самый, на который переключится и сама
                            // страница. Иначе на таких источниках
                            // предзагрузка была бы впустую.
                            page.proxyUrl?.let { proxy ->
                                loader.enqueue(
                                    ImageRequest.Builder(context)
                                        .data(proxy)
                                        .size(size)
                                        .build()
                                )
                            }
                        })
                        .build()
                )
            }
    }
}

/**
 * Вертикальный режим: непрерывная лента элементов, свайп вверх/вниз.
 */
@Composable
private fun VerticalReader(
    elements: List<ReaderElement>,
    onTap: () -> Unit,
    onVisibleChanged: (Int) -> Unit,
    onNearEnd: () -> Unit,
    onPageLoaded: (String, Boolean) -> Unit,
    scrollToIndex: Int?,
    onScrolled: () -> Unit,
    translations: Map<String, TranslationResult>,
    onBitmapReady: (String, Bitmap) -> Unit,
) {
    val listState = rememberLazyListState()

    // Открываем главу там, где её закрыли в прошлый раз.
    LaunchedEffect(scrollToIndex, elements.size) {
        val target = scrollToIndex ?: return@LaunchedEffect
        if (target in elements.indices) {
            listState.scrollToItem(target)
            onScrolled()
        }
    }

    // Видимый элемент -> счётчик страниц + триггер подгрузки следующей главы
    val lastVisibleIndex by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
    }
    LaunchedEffect(lastVisibleIndex, elements.size) {
        onVisibleChanged(lastVisibleIndex)
        if (elements.isNotEmpty() && lastVisibleIndex >= elements.size - 3) {
            onNearEnd()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items = elements, key = { index, _ -> index }) { _, element ->
            when (element) {
                is ReaderElement.ChapterDivider -> DividerVertical(element, onTap)
                is ReaderElement.PageElement -> {
                    val aspect = if (element.page.width > 0 && element.page.height > 0) {
                        element.page.width.toFloat() / element.page.height.toFloat()
                    } else {
                        2f / 3f
                    }
                    ReaderPageImage(
                        page = element.page,
                        aspect = aspect,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect),
                        onTap = onTap,
                        onLoaded = onPageLoaded,
                        translations = translations,
                        onBitmapReady = onBitmapReady,
                    )
                }
            }
        }
    }
}

/**
 * Горизонтальный режим: постраничное листание свайпом влево/вправо.
 */
@Composable
private fun HorizontalReader(
    elements: List<ReaderElement>,
    onTap: () -> Unit,
    onVisibleChanged: (Int) -> Unit,
    onNearEnd: () -> Unit,
    onPageLoaded: (String, Boolean) -> Unit,
    scrollToIndex: Int?,
    onScrolled: () -> Unit,
    translations: Map<String, TranslationResult>,
    onBitmapReady: (String, Bitmap) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { elements.size })

    LaunchedEffect(scrollToIndex, elements.size) {
        val target = scrollToIndex ?: return@LaunchedEffect
        if (target in elements.indices) {
            pagerState.scrollToPage(target)
            onScrolled()
        }
    }

    LaunchedEffect(pagerState, elements.size) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onVisibleChanged(page)
            if (elements.isNotEmpty() && page >= elements.size - 2) {
                onNearEnd()
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
    ) { index ->
        val element = elements.getOrNull(index) ?: return@HorizontalPager
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onTap() } },
            contentAlignment = Alignment.Center,
        ) {
            when (element) {
                is ReaderElement.ChapterDivider -> DividerHorizontal(element)
                is ReaderElement.PageElement -> ReaderPageImage(
                    page = element.page,
                    aspect = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    onTap = onTap,
                    onLoaded = onPageLoaded,
                    translations = translations,
                    onBitmapReady = onBitmapReady,
                )
            }
        }
    }
}

/**
 * Картинка страницы со спиннером во время загрузки и иконкой при ошибке.
 *
 * Основной путь — прямая ссылка CDN (`page.url`) с Referer-заголовком из
 * OkHttp-интерцептора. Если CDN блокирует прямой доступ, страница
 * перегружается через серверный прокси (`page.proxyUrl`).
 */
@Composable
private fun ReaderPageImage(
    page: Page,
    aspect: Float?,
    contentScale: ContentScale,
    modifier: Modifier,
    onTap: () -> Unit,
    onLoaded: (String, Boolean) -> Unit,
    translations: Map<String, TranslationResult> = emptyMap(),
    onBitmapReady: (String, Bitmap) -> Unit = { _, _ -> },
) {
    val context = LocalPlatformContext.current
    // Постраничный режим вписывает страницу в экран целиком, лента — только
    // по ширине; от этого зависит, в каком размере её раскодировать.
    val decodeSize = rememberPageSize(fullScreen = contentScale == ContentScale.Fit)
    var useProxy by remember(page.url) { mutableStateOf(false) }
    // Номер попытки: кнопка «Повторить» его увеличивает, и картинка
    // запрашивается заново (Coil переиспользует прежний результат, пока
    // запрос тот же).
    var attempt by remember(page.url) { mutableIntStateOf(0) }
    val effectiveUrl = if (useProxy) page.proxyUrl ?: page.url else page.url
    var paintState by remember(effectiveUrl) { mutableStateOf<AsyncImagePainter.State?>(null) }
    // Размер самой картинки: по нему координаты распознанных кусков
    // пересчитываются в координаты экрана. Данные с сервера для этого не
    // годятся — сайты иногда сообщают размер с точностью «примерно».
    var sourceSize by remember(effectiveUrl) { mutableStateOf<IntSize?>(null) }

    val boxModifier = if (aspect != null) {
        modifier.aspectRatio(aspect)
    } else {
        modifier
    }

    Box(
        modifier = boxModifier.pointerInput(Unit) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center,
    ) {
        val st = paintState
        if (st == null || st is AsyncImagePainter.State.Loading) {
            PageLoadingIndicator(effectiveUrl)
        } else if (st is AsyncImagePainter.State.Error) {
            // Не просто значок: страница не открылась чаще всего из-за сети
            // или из-за CDN источника, и повторить попытку — рабочее решение,
            // которое должно быть под рукой, а не «выйти и зайти».
            PageErrorPlaceholder(
                pageNumber = page.index + 1,
                onRetry = {
                    // Начинаем снова с прямой ссылки: прокси — запасной путь,
                    // и если CDN уже отвечает, лучше идти к нему.
                    useProxy = false
                    paintState = null
                    attempt++
                },
            )
        }
        key(effectiveUrl, attempt) {
        AsyncImage(
            model = remember(effectiveUrl, decodeSize, attempt) {
                pageRequest(context, effectiveUrl, decodeSize, refresh = attempt > 0)
            },
            contentDescription = "Страница ${page.index + 1}",
            contentScale = contentScale,
            onState = { newState ->
                paintState = newState
                when (newState) {
                    is AsyncImagePainter.State.Success -> {
                        onLoaded(page.url, true)
                        // Распознавать можно только готовую картинку, и только
                        // растровую: страницы манги всегда растр.
                        (newState.result.image as? BitmapImage)?.let { image ->
                            sourceSize = IntSize(image.bitmap.width, image.bitmap.height)
                            onBitmapReady(effectiveUrl, image.bitmap)
                        }
                    }
                    is AsyncImagePainter.State.Error -> {
                        // Прямая ссылка не открылась — пробуем серверный прокси.
                        if (!useProxy && page.proxyUrl != null) {
                            useProxy = true
                        } else {
                            onLoaded(page.url, false)
                        }
                    }
                    else -> {}
                }
            },
            modifier = Modifier.matchParentSize(),
        )
        }

        // Перевод рисуется ПОВЕРХ страницы, на месте исходных облачков.
        val translation = translations[effectiveUrl]
        val size = sourceSize
        if (translation is TranslationResult.Ready && size != null) {
            TranslationOverlay(
                blocks = translation.blocks,
                source = size,
                fitInside = contentScale == ContentScale.Fit,
                modifier = Modifier.matchParentSize(),
            )
        }
        // Молчаливый отказ хуже отказа: если модель не скачалась или
        // распознавание упало, это должно быть видно на самой странице.
        if (translation is TranslationResult.Failed) {
            Text(
                "Перевод не получился: ${translation.message}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Место страницы, которая не загрузилась.
 *
 * Раньше здесь был один значок на чёрном фоне, и выйти из положения было
 * нечем: повторная попытка случалась только при прокрутке туда-обратно, да и
 * то не всегда. Кнопка делает это явно.
 */
@Composable
private fun PageErrorPlaceholder(pageNumber: Int, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp),
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(40.dp),
        )
        Text(
            "Страница $pageNumber не загрузилась",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text("Повторить")
        }
    }
}

/**
 * Перевод поверх страницы.
 *
 * Каждый распознанный кусок закрывается плашкой на том же месте, где на
 * странице был оригинал: так понятно, к какому облачку относится реплика, и
 * картинка не превращается в список фраз под ней.
 *
 * Координаты приходят в пикселях исходной картинки, а на экране она
 * растянута, поэтому их приходится пересчитывать: в ленте картинка занимает
 * всю ширину, при листании — вписана целиком, и коэффициенты у этих случаев
 * разные.
 */
@Composable
private fun TranslationOverlay(
    blocks: List<TranslatedBlock>,
    source: IntSize,
    fitInside: Boolean,
    modifier: Modifier = Modifier,
) {
    if (source.width <= 0 || source.height <= 0) return
    BoxWithConstraints(modifier = modifier) {
        val boxWidth = constraints.maxWidth.toFloat()
        val boxHeight = constraints.maxHeight.toFloat()
        val scale = if (fitInside) {
            minOf(boxWidth / source.width, boxHeight / source.height)
        } else {
            boxWidth / source.width
        }
        val drawnWidth = source.width * scale
        val drawnHeight = source.height * scale
        val offsetX = (boxWidth - drawnWidth) / 2f
        val offsetY = (boxHeight - drawnHeight) / 2f
        val density = LocalDensity.current

        blocks.forEach { block ->
            val left = offsetX + block.box.left * scale
            val top = offsetY + block.box.top * scale
            val width = block.box.width() * scale
            val height = block.box.height() * scale
            if (width < 8f || height < 8f) return@forEach
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { left.toDp() },
                        y = with(density) { top.toDp() },
                    )
                    .size(
                        width = with(density) { width.toDp() },
                        height = with(density) { height.toDp() },
                    )
                    .background(Color(0xF2FFFFFF), RoundedCornerShape(6.dp))
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = block.translated,
                    color = Color(0xFF101014),
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    // Размер подбирается под плашку: реплики бывают и в две
                    // строки, и в десять слов, а места ровно столько, сколько
                    // занимал оригинал.
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = with(density) {
                            (height * 0.34f).coerceIn(
                                with(density) { 8.dp.toPx() },
                                with(density) { 15.dp.toPx() },
                            ).toSp()
                        },
                        lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified,
                    ),
                )
            }
        }
    }
}

/**
 * Сколько страницы уже скачано.
 *
 * Пока размер картинки известен, кружок заполняется и под ним стоят проценты;
 * если CDN не прислал Content-Length, остаётся обычный бесконечный кружок —
 * врать про «47%» там не из чего.
 */
@Composable
private fun PageLoadingIndicator(url: String) {
    val progress by remember(url) { ImageProgress.progressOf(url) }
        .collectAsState()
    val determinate = progress > 0f

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (determinate) {
            CircularProgressIndicator(
                progress = { progress },
                color = Color.White.copy(alpha = 0.75f),
                trackColor = Color.White.copy(alpha = 0.18f),
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        } else {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(40.dp),
            )
        }
    }
}


/** Разделитель главы в вертикальной ленте */
@Composable
private fun DividerVertical(element: ReaderElement.ChapterDivider, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 16.dp)
            .pointerInput(Unit) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center,
    ) {
        DividerCard(element)
    }
}

/** Разделитель главы как отдельная страница в горизонтальном режиме */
@Composable
private fun DividerHorizontal(element: ReaderElement.ChapterDivider) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DividerCard(element)
    }
}

@Composable
private fun DividerCard(element: ReaderElement.ChapterDivider) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f),
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (element.isAvailable) "Новая глава" else "Глава недоступна",
                style = MaterialTheme.typography.labelMedium,
                color = if (element.isAvailable) {
                    Color.White.copy(alpha = 0.6f)
                } else {
                    Color(0xFFFFB4AB)
                },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = element.chapter.title.ifBlank { "Глава ${element.chapter.number}" },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = element.chapter.sourceName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

/** Всплывающая пометка при входе в новую главу */
@Composable
private fun ChapterToast(text: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.8f),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = text ?: "",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * Верхняя и нижняя панели читалки.
 *
 * Появляются и прячутся не рывком: панели уезжают за свой край экрана и
 * одновременно растворяются. Уход быстрее прихода (180 мс против 240) — меню
 * закрывают, чтобы вернуться к чтению, и ждать анимацию в этот момент
 * особенно раздражает.
 */
@Composable
private fun BoxScope.ReaderChrome(
    visible: Boolean,
    mangaTitle: String,
    chapterTitle: String,
    sourceName: String,
    readingMode: ReadingMode,
    currentPage: Int,
    totalPages: Int,
    loadProgress: Float,
    isImagesLoading: Boolean,
    hasNextChapter: Boolean,
    translateEnabled: Boolean,
    translatorStatus: TranslatorStatus,
    onTranslateChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onModeSelected: (ReadingMode) -> Unit,
) {
    val appear = tween<Float>(durationMillis = 240, easing = FastOutSlowInEasing)
    val hide = tween<Float>(durationMillis = 180, easing = FastOutLinearInEasing)
    val slideIn = tween<IntOffset>(durationMillis = 240, easing = FastOutSlowInEasing)
    val slideOut = tween<IntOffset>(durationMillis = 180, easing = FastOutLinearInEasing)

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.TopCenter),
        enter = slideInVertically(animationSpec = slideIn) { -it } + fadeIn(appear),
        exit = slideOutVertically(animationSpec = slideOut) { -it } + fadeOut(hide),
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.75f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Row(
                    // Отступ под шторку уже дан всей читалке, здесь он
                    // добавился бы вторым.
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mangaTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (chapterTitle.isBlank()) sourceName
                                   else "$chapterTitle · $sourceName",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Общий прогресс загрузки картинок главы
                if (isImagesLoading) {
                    LinearProgressIndicator(
                        progress = { loadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        color = Color.White.copy(alpha = 0.8f),
                        trackColor = Color.White.copy(alpha = 0.15f),
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(animationSpec = slideIn) { it } + fadeIn(appear),
        exit = slideOutVertically(animationSpec = slideOut) { it } + fadeOut(hide),
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.75f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (totalPages > 0) "$currentPage / $totalPages" else "",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        isImagesLoading -> "Загрузка страниц… ${"%.0f".format(loadProgress * 100)}%"
                        hasNextChapter -> "Следующая глава подгрузится автоматически"
                        else -> "Последняя глава"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = readingMode == ReadingMode.VERTICAL,
                        onClick = { onModeSelected(ReadingMode.VERTICAL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    ) {
                        Text(ReadingMode.VERTICAL.label)
                    }
                    SegmentedButton(
                        selected = readingMode == ReadingMode.HORIZONTAL,
                        onClick = { onModeSelected(ReadingMode.HORIZONTAL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            Icon(
                                Icons.Filled.MoreHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    ) {
                        Text(ReadingMode.HORIZONTAL.label)
                    }
                }
                TranslateSwitch(
                    checked = translateEnabled,
                    status = translatorStatus,
                    onChange = onTranslateChange,
                )
            }
        }
    }
}

/**
 * Перевод страниц — галочка прямо в меню чтения.
 *
 * Здесь, а не в настройках: включать перевод хочется ровно в тот момент, когда
 * открыл непереведённую главу, и уходить за этим в настройки — значит потерять
 * место в чтении.
 *
 * Под галочкой — только состояние, и только пока что-то происходит: качается
 * языковая модель или идёт распознавание. Полоса бесконечная, без процентов:
 * ML Kit скачивает модель одной задачей и о ходе загрузки не сообщает, а
 * выдуманные проценты хуже честного «качается».
 */
@Composable
private fun TranslateSwitch(
    checked: Boolean,
    status: TranslatorStatus,
    onChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Переводить текст на страницах",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Checkbox(
                checked = checked,
                onCheckedChange = onChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = Color.White,
                    checkmarkColor = Color.Black,
                    uncheckedColor = Color.White.copy(alpha = 0.6f),
                ),
            )
        }
        val note = when (status) {
            is TranslatorStatus.Downloading -> "Скачиваем модель перевода с ${status.language}…"
            TranslatorStatus.Working -> "Распознаём и переводим страницу…"
            is TranslatorStatus.Failed -> status.message
            TranslatorStatus.Idle -> null
        }
        if (checked && note != null) {
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
            )
            if (status !is TranslatorStatus.Failed) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    color = Color.White.copy(alpha = 0.85f),
                    trackColor = Color.White.copy(alpha = 0.2f),
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry) {
                Text("Повторить")
            }
        }
    }
}

/**
 * Размер, в котором раскодируется страница: ширина экрана, высота по
 * пропорциям картинки.
 *
 * Размер задан явно, и это не мелочь. Без него предзагрузка качала страницу в
 * ИСХОДНОМ разрешении (у сканов это легко 2000×3000, то есть 24 МБ на
 * страницу в памяти), а показывалась она потом уменьшенной — то есть заранее
 * считалась не та картинка, которая появится на экране, и вдобавок такие
 * картинки вытесняли из кэша памяти всё подряд. Теперь и предзагрузка, и
 * показ просят одно и то же, и готовая страница берётся из памяти целиком.
 */
@Composable
private fun rememberPageSize(fullScreen: Boolean): Size {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    return remember(config.screenWidthDp, config.screenHeightDp, fullScreen, density) {
        with(density) {
            val width = config.screenWidthDp.dp.roundToPx()
            // Постраничный режим: страница вписывается в экран целиком.
            if (fullScreen) return@remember Size(width, config.screenHeightDp.dp.roundToPx())
            // Лента: ширина экрана, высота по пропорциям — но не любая.
            // Страницы вебтунов бывают длиной в десятки тысяч точек, и такая
            // страница в ширину экрана — это под сотню мегабайт одной
            // картинкой: телефон её не осиливает, и вместо страницы человек
            // видит «страница не загрузилась» РОВНО ПОСЛЕ того, как она
            // скачалась на сто процентов. Выше потолка страница ужимается —
            // мельче, зато видно.
            Size(width, Dimension.Pixels((MAX_PAGE_PIXELS / width).coerceAtLeast(width)))
        }
    }
}

/**
 * Потолок раскодированной страницы в точках. Восемь мегапикселей — это 32 МБ
 * в памяти на страницу; обычная страница манги (1080×1540) занимает 1,7 из
 * них, так что упирается в потолок только по-настоящему длинная полоса.
 */
private const val MAX_PAGE_PIXELS = 8_000_000

/**
 * Запрос страницы для показа.
 *
 * Без слушателей и прочих лямбд: запрос пересобирается на каждую
 * перекомпоновку, и если он перестанет быть равен прежнему, AsyncImage
 * начнёт загрузку заново.
 */
private fun pageRequest(context: PlatformContext, url: String, size: Size,
                        refresh: Boolean = false): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .size(size)
        .crossfade(false)
        .apply {
            if (refresh) {
                // «Повторить» должно именно ПЕРЕКАЧАТЬ страницу. Coil кладёт
                // скачанное на диск ещё до того, как попробует раскодировать,
                // поэтому битый ответ CDN (обрезанный файл, страница-заглушка
                // вместо картинки) оставался в кэше — и повторная попытка
                // разбирала те же самые байты, сколько по кнопке ни нажимай.
                memoryCachePolicy(CachePolicy.WRITE_ONLY)
                diskCachePolicy(CachePolicy.WRITE_ONLY)
            }
        }
        .build()
