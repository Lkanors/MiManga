package com.mimanga.app.core.translate

import com.google.mlkit.nl.translate.TranslateLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты на решения, от которых зависит качество перевода страницы.
 *
 * Сам ML Kit здесь не трогается: он требует устройства. Проверяется то, из-за
 * чего перевод был «криво» — выбор языка по письму и склейка строк облачка.
 */
class PageTranslatorLogicTest {

    @Test
    fun `кана означает японский`() {
        assertEquals(TranslateLanguage.JAPANESE, scriptLanguage("お前はもう死んでいる"))
        assertEquals(TranslateLanguage.JAPANESE, scriptLanguage("ナルト"))
    }

    @Test
    fun `хангыль означает корейский`() {
        assertEquals(TranslateLanguage.KOREAN, scriptLanguage("나 혼자만 레벨업"))
    }

    @Test
    fun `ханьцзы без каны означает китайский`() {
        assertEquals(TranslateLanguage.CHINESE, scriptLanguage("全职高手"))
    }

    @Test
    fun `латиница означает английский, а не японский`() {
        // Главная причина прежней бессмыслицы: японская модель читает и
        // латиницу, поэтому английская страница переводилась как японская.
        assertEquals(TranslateLanguage.ENGLISH, scriptLanguage("WHAT DID YOU SAY"))
    }

    @Test
    fun `по одному знаку язык не определяется`() {
        assertNull(scriptLanguage("!"))
        assertNull(scriptLanguage("?!"))
        assertNull(scriptLanguage("...123..."))
    }

    @Test
    fun `строки японского облачка склеиваются без пробелов`() {
        // Распознаватель рвёт фразу по ширине пузыря; пробел внутри японской
        // фразы означает границу слова и ломает перевод.
        assertEquals("お前はもう死んでいる",
                     normalize("お前は\nもう\n死んでいる", cjk = true))
        assertEquals("全职高手", normalize("全职 高手", cjk = true))
    }

    @Test
    fun `английские строки склеиваются через пробел`() {
        assertEquals("what did you say", normalize("what did\nyou   say", cjk = false))
    }

    @Test
    fun `звуки и одиночные знаки не переводятся`() {
        assertFalse(isMeaningful("!!"))
        assertFalse(isMeaningful("?"))
        assertFalse(isMeaningful("—"))
        assertTrue(isMeaningful("да"))
        assertTrue(isMeaningful("死ぬ"))
    }
}
