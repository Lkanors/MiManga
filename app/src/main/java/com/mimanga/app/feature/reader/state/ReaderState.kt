package com.mimanga.app.feature.reader.state

import com.mimanga.app.core.translate.TranslationResult
import com.mimanga.app.domain.model.Chapter
import com.mimanga.app.domain.model.Page

/** Режим чтения: вертикальная лента или горизонтальное листание */
enum class ReadingMode(val label: String) {
    VERTICAL("Сверху вниз"),
    HORIZONTAL("Слева направо");

    companion object {
        fun fromName(name: String?): ReadingMode =
            entries.firstOrNull { it.name == name } ?: VERTICAL
    }
}

/**
 * Элемент потока чтения: либо разделитель с названием главы (маркер новой главы),
 * либо страница.
 */
sealed interface ReaderElement {
    data class ChapterDivider(
        val chapter: Chapter,
        val isAvailable: Boolean = true,
    ) : ReaderElement

    data class PageElement(
        val page: Page,
        val chapterIndex: Int,
    ) : ReaderElement
}

data class ReaderState(
    val mangaTitle: String = "",
    val chapter: Chapter? = null,
    /** Поток элементов: разделители глав + страницы. Главы добавляются в конец по мере чтения */
    val elements: List<ReaderElement> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val readingMode: ReadingMode = ReadingMode.VERTICAL,
    /** Индекс видимого элемента потока */
    val currentElementIndex: Int = 0,
    /** Номер текущей страницы среди всех страниц (для счётчика) */
    val currentPageNumber: Int = 0,
    /** Всего страниц в потоке */
    val totalPageCount: Int = 0,
    /** Сколько страниц уже загружено (картинки) */
    val loadedPageCount: Int = 0,
    /** Есть ли следующая глава, которую можно подгрузить */
    val hasNextChapter: Boolean = false,
    /** Всплывающая пометка о входе в новую главу */
    val chapterToast: String? = null,
    val isChromeVisible: Boolean = true,
    /**
     * Элемент, на который нужно перевести экран: так читалка открывается на
     * сохранённой странице. Сбрасывается сразу после прокрутки.
     */
    val scrollToIndex: Int? = null,
    /** Переводить ли текст на страницах (переключается в меню читалки). */
    val translateEnabled: Boolean = false,
    /**
     * Результат перевода по адресу картинки. Держится здесь, а не в самом
     * экране: при повороте и при уходе в другую главу перевод не должен
     * считаться заново — распознавание страницы занимает секунды.
     */
    val translations: Map<String, TranslationResult> = emptyMap(),
) {
    /** Доля загруженных картинок 0..1 (1 если нет страниц) */
    val loadProgress: Float
        get() = if (totalPageCount <= 0) 1f else loadedPageCount.toFloat() / totalPageCount

    /** Все ли картинки загружены */
    val isImagesLoading: Boolean
        get() = totalPageCount > 0 && loadedPageCount < totalPageCount
}
