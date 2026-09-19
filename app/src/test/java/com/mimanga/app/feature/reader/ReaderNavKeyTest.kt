package com.mimanga.app.feature.reader

import com.mimanga.app.domain.model.Chapter
import com.mimanga.app.domain.model.MangaSource
import com.mimanga.app.feature.reader.state.ReaderNav
import com.mimanga.app.feature.reader.state.viewModelKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Ключ ViewModel читалки.
 *
 * Почему на это есть тест: ViewModel живёт дольше своего экрана, и два разных
 * тайтла с одинаковым ключом делят её состояние — то есть уже загруженные
 * главы одного. Так и было: «Читать», нажатое до загрузки глав у тайтла,
 * который человек ещё не открывал, давало ключ из одного источника, и читалка
 * открывала предыдущий тайтл.
 */
class ReaderNavKeyTest {

    private val readmanga = MangaSource(sourceId = "readmanga",
                                        url = "https://readmanga.me/title")

    private fun nav(key: String, pending: String? = null,
                    chapters: List<Chapter> = emptyList()) =
        ReaderNav(mangaKey = key, mangaTitle = "Тайтл", source = readmanga,
                  chapters = chapters, pendingChapterKey = pending)

    @Test
    fun `разные тайтлы без известной главы не делят ViewModel`() {
        assertNotEquals(nav("k-first").viewModelKey(), nav("k-second").viewModelKey())
    }

    @Test
    fun `тот же тайтл и та же глава — тот же ключ`() {
        assertEquals(nav("k-first").viewModelKey(), nav("k-first").viewModelKey())
        assertEquals(
            nav("k-first", pending = "readmanga:ch1").viewModelKey(),
            nav("k-first", pending = "readmanga:ch1").viewModelKey(),
        )
    }

    @Test
    fun `разные главы одного тайтла — разные ключи`() {
        assertNotEquals(
            nav("k-first", pending = "readmanga:ch1").viewModelKey(),
            nav("k-first", pending = "readmanga:ch2").viewModelKey(),
        )
    }

    @Test
    fun `открытие со списком глав отличается по самой главе`() {
        fun chapter(url: String) = Chapter(sourceId = "readmanga", sourceName = "ReadManga",
                                           chapterId = url, title = "Глава", url = url)
        val first = chapter("https://readmanga.me/c1")
        val second = chapter("https://readmanga.me/c2")
        assertNotEquals(
            nav("k-first", chapters = listOf(first)).viewModelKey(),
            nav("k-first", chapters = listOf(second)).viewModelKey(),
        )
    }

    @Test
    fun `разные источники одного тайтла не делят ViewModel`() {
        val remanga = MangaSource(sourceId = "remanga", url = "https://remanga.org/title")
        assertNotEquals(
            nav("k-first").viewModelKey(),
            ReaderNav(mangaKey = "k-first", mangaTitle = "Тайтл",
                      source = remanga).viewModelKey(),
        )
    }
}
