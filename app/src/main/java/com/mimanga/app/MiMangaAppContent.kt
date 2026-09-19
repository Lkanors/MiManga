package com.mimanga.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.mimanga.app.domain.model.AppSettings
import com.mimanga.app.domain.model.Manga
import com.mimanga.app.feature.account.state.AccountTab
import com.mimanga.app.feature.account.ui.AccountScreen
import com.mimanga.app.feature.catalog.ui.CatalogScreen
import com.mimanga.app.feature.comments.ui.CommentsScreen
import com.mimanga.app.feature.details.ui.DetailsScreen
import com.mimanga.app.feature.home.ui.HomeScreen
import com.mimanga.app.feature.profile.ui.ProfileScreen
import com.mimanga.app.feature.reader.state.ReaderNav
import com.mimanga.app.feature.reader.ui.ReaderScreen
import com.mimanga.app.feature.settings.ui.SettingsScreen

/**
 * Оболочка приложения.
 *
 * Четыре вкладки: главное (популярное, продолжить чтение, рекомендации),
 * каталог (поиск, жанры и теги), аккаунт и настройки. Избранного отдельной
 * вкладкой нет: пятая подпись не помещалась в панель и переносилась на две
 * строки, а избранное и так первая вкладка аккаунта — вместе с «читаю»,
 * «в планах» и историей.
 *
 * Системная кнопка «назад» работает на каждом шаге: читалка -> комментарии ->
 * карточка -> профиль -> вкладка «Главное» -> выход из приложения. Экраны, у
 * которых есть своё состояние (открытая панель фильтров, поиск),
 * перехватывают «назад» сами — см. CatalogScreen.
 *
 * Отступ сверху (statusBarsPadding) стоит ЗДЕСЬ, один раз на всё приложение:
 * окно рисуется во весь экран (enableEdgeToEdge), и без отступа кнопки в
 * шапке страницы тайтла оказывались под шторкой уведомлений. Читалка
 * отступает от шторки сама — ей нужно учитывать спрятанные панели.
 *
 * Прокрутка не теряется (`rememberSaveableStateHolder`). Экраны здесь
 * подменяются целиком: открыл тайтл — каталог вышел из композиции вместе со
 * своим положением списка, ушёл читать главу — то же случилось со страницей
 * тайтла, и возврат кидал в начало. Держателя два: один хранит положение
 * каждой вкладки, другой — каждой открытой страницы тайтла (ключ — постоянный
 * ключ манги).
 */
@Composable
fun MiMangaAppContent(
    settings: AppSettings,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var detailManga by rememberSaveable(stateSaver = MangaSaver) { mutableStateOf<Manga?>(null) }
    var readerNav by rememberSaveable(stateSaver = ReaderNavSaver) {
        mutableStateOf<ReaderNav?>(null)
    }
    var profileUser by rememberSaveable { mutableStateOf<String?>(null) }
    // Комментарии — отдельный экран поверх карточки: на самой карточке поле
    // ввода уезжало под клавиатуру, а список комментариев мешал списку глав.
    var commentsFor by rememberSaveable(stateSaver = MangaSaver) { mutableStateOf<Manga?>(null) }
    // Положение списков каждой вкладки: переживает и уход на карточку тайтла,
    // и переключение вкладок.
    val tabState = rememberSaveableStateHolder()
    // То же самое для страницы тайтла. Чтение главы убирает её из композиции
    // целиком (читалка рисуется вместо всего остального), и без держателя
    // возврат из читалки бросал в самое начало страницы — мимо списка глав,
    // ради которого человек и возвращается. Ключ — постоянный ключ манги,
    // поэтому у каждого тайтла своё место, и открытый второй раз тайтл
    // показывается там же, где его оставили.
    val detailState = rememberSaveableStateHolder()

    // Возврат по шагам: сначала закрывается верхний экран, потом — переход на
    // первую вкладку; выход из приложения остаётся системе.
    BackHandler(enabled = readerNav != null || commentsFor != null || profileUser != null ||
                          detailManga != null || selectedTab != 0) {
        when {
            readerNav != null -> readerNav = null
            commentsFor != null -> commentsFor = null
            profileUser != null -> profileUser = null
            detailManga != null -> detailManga = null
            else -> selectedTab = 0
        }
    }

    val currentReader = readerNav
    val currentDetail = detailManga
    val currentProfile = profileUser
    val currentComments = commentsFor

    if (currentReader != null) {
        ReaderScreen(
            nav = currentReader,
            settings = settings,
            onBack = { readerNav = null },
        )
        return
    }

    // Всё, кроме читалки, живёт под системной шторкой.
    Box(modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()) {
        when {
            currentComments != null -> CommentsScreen(
                manga = currentComments,
                onBack = { commentsFor = null },
                onOpenProfile = { username -> commentsFor = null; profileUser = username },
            )

            currentProfile != null -> ProfileScreen(
                username = currentProfile,
                settings = settings,
                onBack = { profileUser = null },
                onMangaClick = { manga -> profileUser = null; detailManga = manga },
            )

            currentDetail != null -> detailState.SaveableStateProvider(
                key = "detail-" + currentDetail.actionKey,
            ) {
                DetailsScreen(
                    mangaParam = currentDetail,
                    onBack = { detailManga = null },
                    onMangaClick = { manga -> detailManga = manga },
                    onOpenProfile = { username -> profileUser = username },
                    onOpenComments = { commentsFor = currentDetail },
                    onContinuePending = { source, chapterKey, page ->
                        readerNav = ReaderNav(
                            mangaKey = currentDetail.actionKey,
                            mangaTitle = currentDetail.title,
                            source = source,
                            startPage = page,
                            pendingChapterKey = chapterKey,
                        )
                    },
                    onReadChapter = { chapter, source, chapters, page ->
                        val startIndex = chapters.indexOfFirst {
                            it.url == chapter.url && it.sourceId == chapter.sourceId
                        }.coerceAtLeast(0)
                        readerNav = ReaderNav(
                            mangaKey = currentDetail.actionKey,
                            mangaTitle = currentDetail.title,
                            chapters = chapters,
                            startIndex = startIndex,
                            source = source,
                            startPage = page,
                        )
                    },
                )
            }

            else -> Scaffold(
                // Отступы окна уже сняты рамкой выше; нижняя панель добавляет
                // свой отступ под кнопки навигации сама.
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar {
                        TABS.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = { Icon(tab.icon(), contentDescription = null) },
                                // Подпись только в одну строку: перенос ломал
                                // высоту панели и обрезал соседние вкладки.
                                label = {
                                    Text(
                                        tab.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(),
                            )
                        }
                    }
                },
            ) { padding ->
                val contentModifier = Modifier.padding(padding)
                // Номер вкладки переживает перезапуск экрана, а их число со
                // временем меняется: без этого сохранённая пятая вкладка показала
                // бы пустоту.
                val tab = selectedTab.coerceIn(TABS.indices)
                // Ключ — имя вкладки: по нему держатель находит сохранённое
                // положение списков, когда вкладка снова попадает на экран.
                tabState.SaveableStateProvider(TABS[tab].label) {
                    when (tab) {
                        0 -> HomeScreen(
                            settings = settings,
                            modifier = contentModifier,
                            onMangaClick = { manga -> detailManga = manga },
                            onOpenReader = { nav -> readerNav = nav },
                        )
                        1 -> CatalogScreen(
                            settings = settings,
                            modifier = contentModifier,
                            onMangaClick = { manga -> detailManga = manga },
                        )
                        2 -> AccountScreen(
                            settings = settings,
                            modifier = contentModifier,
                            initialTab = AccountTab.FAVORITES,
                            onMangaClick = { manga -> detailManga = manga },
                        )
                        3 -> SettingsScreen(modifier = contentModifier)
                    }
                }
            }
        }
    }
}

private data class BottomTab(val label: String, val icon: @Composable () -> androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    BottomTab("Главное") { Icons.Default.Home },
    BottomTab("Каталог") { Icons.Default.Search },
    BottomTab("Аккаунт") { Icons.Default.Person },
    BottomTab("Настройки") { Icons.Default.Settings },
)

private val MangaSaver = androidx.compose.runtime.saveable.Saver<Manga?, String>(
    save = { it?.let { manga ->
        kotlinx.serialization.json.Json.encodeToString(Manga.serializer(), manga)
    } },
    restore = { json ->
        try {
            kotlinx.serialization.json.Json.decodeFromString(Manga.serializer(), json)
        } catch (_: Exception) {
            null
        }
    },
)

private val ReaderNavSaver = androidx.compose.runtime.saveable.Saver<ReaderNav?, String>(
    save = { it?.let { nav ->
        kotlinx.serialization.json.Json.encodeToString(ReaderNav.serializer(), nav)
    } },
    restore = { json ->
        try {
            kotlinx.serialization.json.Json.decodeFromString(ReaderNav.serializer(), json)
        } catch (_: Exception) {
            null
        }
    },
)
