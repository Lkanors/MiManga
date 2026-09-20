package com.mimanga.app.feature.reader.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimanga.app.core.network.userMessage
import com.mimanga.app.core.translate.PageTranslator
import com.mimanga.app.core.translate.TranslationResult
import com.mimanga.app.core.translate.TranslatorStatus
import com.mimanga.app.domain.model.Chapter
import com.mimanga.app.domain.model.MangaSource
import com.mimanga.app.domain.model.ReaderDirection
import com.mimanga.app.domain.repository.AccountRepository
import com.mimanga.app.domain.model.ChapterProgress
import com.mimanga.app.domain.repository.MangaRepository
import com.mimanga.app.domain.repository.ProgressRepository
import com.mimanga.app.domain.repository.SettingsRepository
import com.mimanga.app.feature.reader.state.ReaderElement
import com.mimanga.app.feature.reader.state.ReaderState
import com.mimanga.app.feature.reader.state.ReaderNav
import com.mimanga.app.feature.reader.state.ReadingMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val accountRepository: AccountRepository,
    private val progressRepository: ProgressRepository,
    private val settingsRepository: SettingsRepository,
    private val pageTranslator: PageTranslator,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    /** Чем занят переводчик: меню читалки рисует по этому полосу и подпись. */
    val translatorStatus: StateFlow<TranslatorStatus> = pageTranslator.status

    /** Очередь глав, которые будут добавлены в поток по мере чтения */
    private var queue: List<Chapter> = emptyList()
    private var source: MangaSource? = null
    private var startedChapterUrl: String? = null
    private var loadingNextChapter = false
    private var mangaKey: String = ""
    private var markRead = true
    /** Главы источника целиком — по ним считается номер главы для прогресса. */
    private var sourceChapters: List<Chapter> = emptyList()
    private var firstChapterIndex = 0
    /** Страница, на которой в прошлый раз закрыли главу. */
    private var startPage = 0
    private var startPageApplied = false
    /** Запись места чтения откладывается: листают быстро, писать на каждый кадр незачем. */
    private var saveJob: Job? = null
    /** Последнее посчитанное место — его дописываем при выходе из читалки. */
    private var lastPlace: ChapterProgress? = null
    /** Главы, уже отмеченные прочитанными: повторно слать незачем. */
    private val markedChapters = mutableSetOf<String>()
    /** Страницы, для которых перевод уже запущен: дважды не гоняем. */
    private val translating = mutableSetOf<String>()
    /** Глава, которую ждём, когда читалку открыли без списка глав. */
    private var pendingKey: String? = null
    private var chaptersJob: Job? = null
    /** С чем открыли читалку — нужно, чтобы «Повторить» знало, что повторять. */
    private var lastNav: ReaderNav? = null

    init {
        // Направление чтения и отметка прочитанного берутся из настроек.
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                markRead = settings.markChapterReadAutomatically
                _state.update { it.copy(translateEnabled = settings.translatePages) }
                val mode = when (ReaderDirection.from(settings.readerDirection)) {
                    ReaderDirection.VERTICAL -> ReadingMode.VERTICAL
                    else -> ReadingMode.HORIZONTAL
                }
                _state.update { it.copy(readingMode = mode) }
            }
        }
    }

    /**
     * Инициализация списком глав. Идемпотентно: повторный вызов не перезагружает.
     *
     * Повторно эту главу открывают часто — вышли из читалки и вернулись. Эта
     * ViewModel живёт дольше экрана, поэтому страницы уже загружены и грузить
     * их заново не нужно, а вот ленту Compose создаёт новую, с нуля. Раньше
     * здесь стоял просто `return`, и потому «продолжить» на уже открывавшейся
     * главе всегда показывало её с первой страницы.
     */
    fun init(nav: ReaderNav) {
        lastNav = nav
        // Список глав не пришёл — значит, читалку открыли сразу по нажатию
        // «продолжить», не дожидаясь сети. Догружаем список здесь, а экран
        // уже показывает название и кружок: человек видит, что переход
        // произошёл.
        if (nav.chapters.isEmpty()) {
            loadChaptersAndStart(nav)
            return
        }
        start(nav, nav.chapters, nav.startIndex)
    }

    /**
     * Догружает список глав источника и открывает нужную.
     *
     * Повторный вызов (вернулись в читалку) список не перезапрашивает: главы
     * уже в `sourceChapters`, и открывать заново нечего — достаточно перевести
     * ленту на сохранённую страницу.
     */
    private fun loadChaptersAndStart(nav: ReaderNav) {
        // Тот ли это тайтл и тот ли источник. Проверка не формальность: эта
        // ViewModel живёт дольше экрана, и уже загруженные главы можно
        // переиспользовать только для того, для чего они грузились. Иначе
        // открытие другого тайтла выходило отсюда сразу и показывало главы
        // предыдущего.
        val sameTarget = mangaKey == nav.mangaKey && source?.sourceId == nav.source.sourceId
        if (sameTarget && pendingKey == nav.pendingChapterKey && sourceChapters.isNotEmpty()) {
            applyStartPage(nav.startPage)
            return
        }
        if (chaptersJob?.isActive == true) {
            if (sameTarget) return
            // Пока грузились главы, попросили другой тайтл — прежняя загрузка
            // больше не нужна: она открыла бы не то, что просили последним.
            chaptersJob?.cancel()
        }
        pendingKey = nav.pendingChapterKey
        loadedPages.clear()
        _state.value = ReaderState(
            readingMode = _state.value.readingMode,
            translateEnabled = _state.value.translateEnabled,
            mangaTitle = nav.mangaTitle,
            isLoading = true,
        )
        chaptersJob = viewModelScope.launch {
            val chapters = runCatching {
                mangaRepository.getChapters(nav.source.sourceId, nav.source.url ?: "")
            }.getOrElse {
                Timber.w(it, "Главы для продолжения не загрузились")
                _state.update {
                    it.copy(isLoading = false,
                            error = "Главы не загрузились. Проверьте подключение.")
                }
                return@launch
            }
            if (chapters.isEmpty()) {
                _state.update {
                    it.copy(isLoading = false, error = "У источника нет глав")
                }
                return@launch
            }
            val index = chapters
                .indexOfFirst { it.sourceId + ":" + it.url == nav.pendingChapterKey }
                .takeIf { it >= 0 }
                ?: nav.startIndex.coerceIn(0, chapters.lastIndex)
            start(nav, chapters, index)
        }
    }

    /** Общая часть: настроить очередь глав и открыть первую. */
    private fun start(nav: ReaderNav, chapters: List<Chapter>, startIndex: Int) {
        val first = chapters.getOrNull(startIndex) ?: return
        if (startedChapterUrl == first.url) {
            applyStartPage(nav.startPage)
            return
        }
        startedChapterUrl = first.url
        loadedPages.clear()
        source = nav.source
        mangaKey = nav.mangaKey
        sourceChapters = chapters
        firstChapterIndex = startIndex
        startPage = nav.startPage.coerceAtLeast(0)
        startPageApplied = startPage == 0
        queue = chapters.drop(startIndex + 1)

        _state.value = ReaderState(
            readingMode = _state.value.readingMode,
            translateEnabled = _state.value.translateEnabled,
            mangaTitle = nav.mangaTitle,
            chapter = first,
            hasNextChapter = queue.isNotEmpty(),
        )
        appendChapter(first, chapterIndex = 0)
        markChapterRead(first)
    }

    fun setReadingMode(mode: ReadingMode) {
        _state.update { it.copy(readingMode = mode) }
        viewModelScope.launch {
            settingsRepository.update { settings ->
                settings.copy(readerDirection = when (mode) {
                    ReadingMode.VERTICAL -> ReaderDirection.VERTICAL.key
                    ReadingMode.HORIZONTAL -> ReaderDirection.HORIZONTAL.key
                })
            }
        }
    }

    /**
     * Отмечает главу прочитанной на сервере: из этих отметок складываются
     * история чтения, счётчик глав и рекомендации. Без аккаунта запрос просто
     * не уходит.
     */
    private fun markChapterRead(chapter: Chapter) {
        if (!markRead || mangaKey.isBlank()) return
        val src = source ?: return
        val key = src.sourceId + ":" + chapter.url
        if (!markedChapters.add(key)) return
        viewModelScope.launch {
            runCatching {
                accountRepository.markChapterRead(
                    mangaKey = mangaKey,
                    chapterKey = key,
                    sourceId = src.sourceId,
                    number = chapter.number,
                    title = chapter.title.ifBlank { "Глава " + chapter.number },
                )
            }
        }
    }

    fun toggleChrome() {
        _state.update { it.copy(isChromeVisible = !it.isChromeVisible) }
    }

    // ------------------------------------------------- перевод страниц

    /**
     * Включает или выключает перевод страниц. Настройка общая, поэтому
     * переключатель из читалки её и сохраняет: вернувшись в другую главу,
     * человек застанет то же состояние.
     */
    fun setTranslate(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(translatePages = enabled) }
        }
    }

    /**
     * Просит перевести страницу. Вызывается, когда картинка уже отрисована:
     * распознавать нечего, пока нет самой картинки.
     *
     * Повторные вызовы для одной страницы отбрасываются — экран пересобирается
     * при каждой прокрутке, а распознавание стоит секунды процессорного времени.
     */
    fun translatePage(url: String, bitmap: Bitmap) {
        if (!_state.value.translateEnabled) return
        if (url in translating || _state.value.translations.containsKey(url)) return
        translating += url
        viewModelScope.launch {
            val result = pageTranslator.translate(bitmap)
            _state.update { it.copy(translations = it.translations + (url to result)) }
        }
    }

    fun onVisibleElementChanged(index: Int) {
        val elements = _state.value.elements
        if (index in elements.indices) {
            // Считаем номер страницы среди всех страниц потока
            val pageNumber = elements.take(index + 1).count { it is ReaderElement.PageElement }
            if (pageNumber != _state.value.currentPageNumber) {
                _state.update { it.copy(currentElementIndex = index, currentPageNumber = pageNumber) }
                // Пометка о входе в новую главу: перешли страницу из другой главы
                checkChapterTransition(index)
            } else {
                _state.update { it.copy(currentElementIndex = index) }
            }
            rememberPlace(index)
        }
    }

    /**
     * Просит экран перевести ленту на страницу [page] первой главы.
     *
     * В потоке перед страницами главы стоит её разделитель, поэтому страница
     * N — это элемент N+1.
     */
    private fun applyStartPage(page: Int) {
        val target = page.coerceAtLeast(0)
        val elements = _state.value.elements
        if (elements.isEmpty()) {
            // Страницы ещё грузятся — запомним, переведём после загрузки.
            startPage = target
            startPageApplied = target == 0
            return
        }
        _state.update {
            it.copy(scrollToIndex = if (target == 0) 0
                                    else (1 + target).coerceAtMost(elements.size - 1))
        }
    }

    /** Экран сообщил, что перевёл ленту на сохранённую страницу. */
    fun scrollHandled() {
        _state.update { it.copy(scrollToIndex = null) }
    }

    /**
     * Запоминает, докуда дочитано. У вошедшего в аккаунт место уходит на
     * сервер (переустановка приложения его не теряет), у гостя остаётся в
     * памяти телефона — и там и там отдельно по каждому источнику.
     */
    private fun rememberPlace(index: Int) {
        if (mangaKey.isBlank()) return
        val src = source ?: return
        val elements = _state.value.elements
        val element = elements.getOrNull(index)
        val streamIndex = when (element) {
            is ReaderElement.PageElement -> element.chapterIndex
            is ReaderElement.ChapterDivider ->
                elements.take(index + 1).count { it is ReaderElement.ChapterDivider } - 1
            else -> return
        }
        val chapter = sourceChapters.getOrNull(firstChapterIndex + streamIndex) ?: return
        val pagesInChapter = elements.count {
            it is ReaderElement.PageElement && it.chapterIndex == streamIndex
        }
        if (pagesInChapter == 0) return
        val pageInChapter = elements.take(index + 1).count {
            it is ReaderElement.PageElement && it.chapterIndex == streamIndex
        }.coerceAtLeast(1) - 1

        val place = ChapterProgress(
            mangaKey = mangaKey,
            sourceId = src.sourceId,
            chapterKey = src.sourceId + ":" + chapter.url,
            chapterTitle = chapter.title.ifBlank { "Глава " + chapter.number },
            chapterNumber = chapter.number,
            chapterIndex = firstChapterIndex + streamIndex,
            page = pageInChapter,
            pages = pagesInChapter,
            position = (pageInChapter + 1f) / pagesInChapter,
            // У гостя записи нигде не проверяются сервером,
            // поэтому признак «дочитано» ставим сразу.
            isRead = (pageInChapter + 1f) / pagesInChapter >= ChapterProgress.DONE,
            // Время — момент чтения, а не момент записи: дописывание при
            // закрытии читалки не должно выглядеть свежее того, что человек
            // успел прочитать в другой главе после.
            updatedAt = java.time.Instant.now().toString(),
        )
        lastPlace = place

        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(600)   // пока листают, запись не уходит
            runCatching { progressRepository.save(place) }
        }
    }

    /**
     * Выход из читалки. Отложенная запись здесь уже отменена вместе с
     * областью ViewModel, поэтому последнее место дописываем отдельно —
     * иначе быстрый выход сразу после пролистывания его терял.
     */
    override fun onCleared() {
        super.onCleared()
        lastPlace?.let { progressRepository.saveDetached(it) }
    }

    /** Пытается бесшовно подгрузить следующую главу в конец потока */
    fun requestNextChapter() {
        if (loadingNextChapter) return
        val next = queue.firstOrNull() ?: return
        val chapterIndex = _state.value.elements.count { it is ReaderElement.ChapterDivider }
        loadingNextChapter = true
        queue = queue.drop(1)
        appendChapter(next, chapterIndex)
    }

    /**
     * Страница либо загрузилась, либо нет.
     *
     * Считаются ТОЛЬКО удачи и только по одному разу на страницу. Раньше
     * счётчик увеличивался на любой ответ: и на отказ, и на повторную попытку,
     * и на каждое возвращение страницы на экран (лента пересоздаёт картинку,
     * когда прокручиваешь назад). Полоса «загрузка страниц» от этого ползла
     * вперёд там, где ничего не загрузилось, и доходила до конца при живых
     * дырах в главе.
     */
    fun onPageLoaded(pageUrl: String, loaded: Boolean) {
        if (!loaded) return
        if (!loadedPages.add(pageUrl)) return
        _state.update {
            it.copy(loadedPageCount = loadedPages.size.coerceAtMost(it.totalPageCount))
        }
    }

    /** Страницы, которые действительно загрузились: по ним считается полоса. */
    private val loadedPages = mutableSetOf<String>()

    fun retry() {
        val chapter = _state.value.chapter
        if (chapter == null) {
            // Дело не в страницах: не загрузился сам список глав (читалку
            // открыли сразу, по нажатию «продолжить»). Повторяем его.
            lastNav?.let { nav ->
                pendingKey = null
                loadChaptersAndStart(nav)
            }
            return
        }
        // Глава перезагружается с нуля, а не «если поток пуст». Проверка на
        // пустоту делала кнопку мёртвой ровно там, где она нужна: экран с
        // ошибкой показывается и при непустом потоке (например, остался
        // разделитель главы без страниц), и нажатие тогда не делало ничего.
        loadedPages.clear()
        _state.update {
            it.copy(elements = emptyList(), error = null, isLoading = true,
                    totalPageCount = 0, loadedPageCount = 0, translations = emptyMap())
        }
        appendChapter(chapter, chapterIndex = 0)
    }

    /**
     * Загружает страницы главы и добавляет разделитель + страницы в конец потока.
     */
    private fun appendChapter(chapter: Chapter, chapterIndex: Int) {
        val src = source ?: return
        viewModelScope.launch {
            if (chapterIndex == 0) _state.update { it.copy(isLoading = true, error = null) }
            try {
                val pages = mangaRepository.getPages(src.sourceId, chapter.url)
                when {
                    pages.isEmpty() && chapterIndex == 0 -> {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = "Не удалось загрузить страницы. " +
                                    "Возможно, глава платная или недоступна на ${src.sourceName}.",
                            )
                        }
                        loadingNextChapter = false
                    }
                    pages.isEmpty() -> {
                        // Пропускаем недоступную главу, но оставляем маркер в потоке
                        _state.update {
                            it.copy(
                                elements = it.elements + ReaderElement.ChapterDivider(
                                    chapter, isAvailable = false,
                                ),
                                hasNextChapter = queue.isNotEmpty(),
                            )
                        }
                        loadingNextChapter = false
                    }
                    else -> {
                        val newElements = buildList {
                            add(ReaderElement.ChapterDivider(chapter))
                            pages.forEach { add(ReaderElement.PageElement(it, chapterIndex)) }
                        }
                        _state.update {
                            it.copy(
                                elements = it.elements + newElements,
                                isLoading = false,
                                totalPageCount = it.totalPageCount + pages.size,
                                hasNextChapter = queue.isNotEmpty(),
                                // Первая глава загрузилась — открываем её на той
                                // странице, где человек остановился (1 — это
                                // разделитель главы перед страницами).
                                scrollToIndex = if (!startPageApplied && chapterIndex == 0) {
                                    startPageApplied = true
                                    (1 + startPage).coerceAtMost(newElements.size - 1)
                                } else it.scrollToIndex,
                            )
                        }
                        loadingNextChapter = false
                    }
                }
            } catch (e: Exception) {
                if (chapterIndex == 0) {
                    _state.update {
                        it.copy(isLoading = false, error = e.userMessage("Глава не загрузилась"))
                    }
                }
                loadingNextChapter = false
            }
        }
    }

    private fun checkChapterTransition(index: Int) {
        val elements = _state.value.elements
        // Если видимый элемент — разделитель не первой главы, показываем пометку
        val divider = elements.getOrNull(index) as? ReaderElement.ChapterDivider
        if (divider != null && index > 0 && divider.isAvailable) {
            showChapterToast(divider.chapter)
            markChapterRead(divider.chapter)
            return
        }
        // Пометка также при переходе первой страницы новой главы (мимо разделителя)
        val page = elements.getOrNull(index) as? ReaderElement.PageElement ?: return
        val prev = elements.getOrNull(index - 1) as? ReaderElement.PageElement
        if (prev != null && prev.chapterIndex != page.chapterIndex) {
            val dividerIdx = elements.indexOfLast { it is ReaderElement.ChapterDivider }
            val d = elements.getOrNull(dividerIdx) as? ReaderElement.ChapterDivider
            if (d != null) showChapterToast(d.chapter)
        }
    }

    private fun showChapterToast(chapter: Chapter) {
        val label = chapter.title.ifBlank { "Глава ${chapter.number}" }
        _state.update { it.copy(chapterToast = "$label · ${chapter.sourceName}") }
        viewModelScope.launch {
            kotlinx.coroutines.delay(2200)
            // Убираем пометку, если она ещё актуальна
            _state.update { if (it.chapterToast == "$label · ${chapter.sourceName}") it.copy(chapterToast = null) else it }
        }
    }
}
