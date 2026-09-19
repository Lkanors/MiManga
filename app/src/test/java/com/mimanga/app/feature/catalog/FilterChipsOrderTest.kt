package com.mimanga.app.feature.catalog

import com.mimanga.app.feature.catalog.ui.visibleFilterNames
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Порядок чипов в панели отбора.
 *
 * Почему на это есть тест: чипы разъезжались под пальцем. Выбранное
 * поднималось в начало списка, поэтому после нажатия на жанр следующее
 * нажатие в то же место экрана попадало уже по другому чипу — выглядело это
 * так, будто снятый жанр включается обратно сам, и по кругу.
 */
class FilterChipsOrderTest {

    private val genres = listOf("Боевик", "Драма", "Комедия", "Романтика", "Фэнтези", "Хоррор")

    @Test
    fun `выбор не меняет порядок чипов`() {
        val before = visibleFilterNames(genres, expanded = true, limit = 4,
                                        selected = emptySet(), excluded = emptySet(), query = "")
        val afterSelect = visibleFilterNames(genres, expanded = true, limit = 4,
                                             selected = setOf("Романтика"), excluded = emptySet(),
                                             query = "")
        val afterExclude = visibleFilterNames(genres, expanded = true, limit = 4,
                                              selected = emptySet(),
                                              excluded = setOf("Романтика"), query = "")
        assertEquals(genres, before)
        assertEquals(genres, afterSelect)
        assertEquals(genres, afterExclude)
    }

    @Test
    fun `в поиске выбранное тоже остаётся на своём месте`() {
        val query = "а"
        val before = visibleFilterNames(genres, expanded = true, limit = 4,
                                        selected = emptySet(), excluded = emptySet(), query = query)
        val after = visibleFilterNames(genres, expanded = true, limit = 4,
                                       selected = setOf(before.last()), excluded = emptySet(),
                                       query = query)
        assertEquals(before, after)
    }

    @Test
    fun `свёрнутый раздел показывает начало списка`() {
        val visible = visibleFilterNames(genres, expanded = false, limit = 3,
                                         selected = emptySet(), excluded = emptySet(), query = "")
        assertEquals(listOf("Боевик", "Драма", "Комедия"), visible)
    }

    @Test
    fun `выбранное за пределами начала видно, но не первым`() {
        val visible = visibleFilterNames(genres, expanded = false, limit = 3,
                                         selected = setOf("Хоррор"), excluded = emptySet(),
                                         query = "")
        assertEquals(listOf("Боевик", "Драма", "Комедия", "Хоррор"), visible)
    }

    @Test
    fun `снятие выбора не двигает соседей`() {
        val withChoice = visibleFilterNames(genres, expanded = false, limit = 3,
                                            selected = setOf("Драма"), excluded = emptySet(),
                                            query = "")
        val without = visibleFilterNames(genres, expanded = false, limit = 3,
                                         selected = emptySet(), excluded = emptySet(), query = "")
        assertEquals(without, withChoice)
    }
}
