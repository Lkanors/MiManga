package com.mimanga.app.domain.model

/** Направление листания в читалке. */
enum class ReaderDirection(val key: String, val title: String) {
    VERTICAL("vertical", "Вертикально (лента)"),
    HORIZONTAL("horizontal", "Горизонтально"),
    RIGHT_TO_LEFT("rtl", "Справа налево (манга)");

    companion object {
        fun from(key: String?): ReaderDirection = entries.firstOrNull { it.key == key } ?: VERTICAL
    }
}

/** Фон читалки: у разных сканов комфортнее разный. */
enum class ReaderBackground(val key: String, val title: String) {
    BLACK("black", "Чёрный"),
    DARK("dark", "Тёмно-серый"),
    WHITE("white", "Белый");

    companion object {
        fun from(key: String?): ReaderBackground = entries.firstOrNull { it.key == key } ?: BLACK
    }
}

/**
 * Все настройки приложения одним объектом: экраны получают их потоком и не
 * знают, что под ними DataStore.
 */
data class AppSettings(
    // Внешний вид
    val themeMode: String = "SYSTEM",
    val gridColumns: Int = 3,
    val showRatingOnCard: Boolean = true,
    val showChaptersOnCard: Boolean = true,
    val showStatusBadge: Boolean = true,
    val compactCards: Boolean = false,
    // Каталог
    val includeForeign: Boolean = false,
    val defaultSort: String = CatalogSort.POPULAR.key,
    val homeRowSize: Int = 20,
    // Читалка
    val readerDirection: String = ReaderDirection.VERTICAL.key,
    val readerBackground: String = ReaderBackground.BLACK.key,
    val keepScreenOn: Boolean = true,
    val fullscreenReader: Boolean = true,
    /**
     * Сколько страниц читалка качает вперёд, не дожидаясь, пока до них
     * долистают. Пять — это примерно полминуты чтения в запасе даже на
     * медленной сети, и столько раскодированных страниц спокойно помещается
     * в кэш памяти.
     */
    val prefetchPages: Int = 5,
    val markChapterReadAutomatically: Boolean = true,
    val showPageProgress: Boolean = true,
    /**
     * Переводить текст на страницах прямо во время чтения.
     *
     * Выключено по умолчанию: распознавание и перевод идут на самом телефоне
     * (ML Kit), а модели языков докачиваются при первом включении.
     * Переключатель стоит в меню читалки, а не в настройках, — включать его
     * хочется ровно тогда, когда попалась непереведённая глава.
     */
    val translatePages: Boolean = false,
)
