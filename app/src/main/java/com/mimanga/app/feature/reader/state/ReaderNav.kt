package com.mimanga.app.feature.reader.state

import com.mimanga.app.domain.model.Chapter
import com.mimanga.app.domain.model.MangaSource
import kotlinx.serialization.Serializable

/**
 * Данные для открытия ридера: список глав, стартовая глава и источник.
 * Сериализуется для переживания поворота экрана.
 *
 * Список глав может быть ПУСТЫМ — тогда читалка догрузит его сама по
 * `pendingChapterKey`. Так открывается «продолжить чтение»: раньше список глав
 * запрашивался ДО перехода, и между нажатием и появлением читалки проходила
 * пара секунд, в которые на экране не менялось ничего.
 */
@Serializable
data class ReaderNav(
    /** Постоянный ключ манги: по нему глава отмечается прочитанной. */
    val mangaKey: String = "",
    val mangaTitle: String,
    val chapters: List<Chapter> = emptyList(),
    val startIndex: Int = 0,
    val source: MangaSource,
    /**
     * Страница внутри стартовой главы: читалка открывается ровно там, где
     * человек закрыл её в прошлый раз.
     */
    val startPage: Int = 0,
    /**
     * Какую главу открыть, когда список глав ещё не загружен («источник:ссылка»).
     * Если такой главы в загруженном списке не окажется, читалка возьмёт
     * `startIndex`.
     */
    val pendingChapterKey: String? = null,
)

/**
 * Ключ ViewModel читалки: тайтл, источник и открываемая глава.
 *
 * Тайтл в ключе обязателен, и это не перестраховка. Когда «Читать» нажимают
 * до того, как подгрузились главы, а этот тайтл человек ещё не открывал,
 * ключа главы нет — его неоткуда взять. Раньше ключ сводился в этом случае к
 * одному источнику («reader-readmanga-0»), все такие открытия делили одну
 * ViewModel, а живёт она дольше экрана — и второй тайтл получал уже
 * загруженные главы ПЕРВОГО, то есть читалка открывала чужой тайтл.
 */
fun ReaderNav.viewModelKey(): String {
    val chapter = chapters.getOrNull(startIndex)?.url ?: pendingChapterKey ?: ""
    return "reader-$mangaKey-${source.sourceId}-${chapter.hashCode()}"
}
