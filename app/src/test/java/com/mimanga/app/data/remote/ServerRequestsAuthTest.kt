package com.mimanga.app.data.remote

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Каждый запрос к нашему серверу должен нести токен аккаунта.
 *
 * Зачем такой тест. По токену сервер решает не только что показать в списках,
 * но и что человеку открывать можно. Запрос без токена он считает гостевым —
 * и список глав приходил пустым у того, кто в аккаунт вошёл. Заметить это по
 * коду трудно: вызов выглядит совершенно обычно, просто в нём нет одной
 * строчки. Поэтому правило проверяется целиком, а не по памяти.
 *
 * Исключения ровно два: вход и регистрация — токена там ещё нет.
 */
class ServerRequestsAuthTest {

    private val allowedWithoutToken = setOf("register", "login")

    @Test
    fun `каждый запрос к серверу подписан токеном`() {
        val source = File("src/main/java/com/mimanga/app/data/remote/MangaServerApi.kt")
        assertTrue("Не найден ${source.absolutePath}", source.isFile)
        val text = source.readText()

        val starts = Regex("""suspend fun (\w+)\(""").findAll(text).toList()
        val withoutToken = starts.mapIndexedNotNull { index, match ->
            val name = match.groupValues[1]
            val end = starts.getOrNull(index + 1)?.range?.first ?: text.length
            val body = text.substring(match.range.first, end)
            name.takeIf {
                "\$BASE_URL/api/" in body && "auth()" !in body && it !in allowedWithoutToken
            }
        }
        assertTrue("Запросы без токена: $withoutToken", withoutToken.isEmpty())
    }
}
