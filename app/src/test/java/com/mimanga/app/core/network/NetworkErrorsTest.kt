package com.mimanga.app.core.network

import com.mimanga.app.data.remote.ServerException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Текст ошибки не должен выдавать адрес сервера.
 *
 * Сообщения сетевых ошибок содержат его дословно («Failed to connect to
 * /203.0.113.10:8443»), а экраны показывают их как есть. Проверяется именно
 * то, что адреса в итоговом тексте не остаётся, — глазами такое легко
 * пропустить: строка выглядит технической, но вполне читаемой.
 */
class NetworkErrorsTest {

    private val address = "203.0.113.10"

    @Test
    fun `отказ подключения не показывает адрес`() {
        val message = ConnectException("Failed to connect to /$address:8443")
            .userMessage("Не получилось")
        assertFalse(message, message.contains(address))
        assertFalse(message, message.contains("8443"))
        assertTrue(message, message.contains("Сервер недоступен"))
    }

    @Test
    fun `неизвестный хост и таймаут тоже без адреса`() {
        val host = UnknownHostException("Unable to resolve host \"$address\"")
            .userMessage("Не получилось")
        val slow = SocketTimeoutException("failed to connect to /$address:8443 after 15000ms")
            .userMessage("Не получилось")
        assertFalse(host, host.contains(address))
        assertFalse(slow, slow.contains(address))
    }

    @Test
    fun `из чужого текста вырезается и ссылка, и адрес с портом`() {
        val message = IllegalStateException(
            "Запрос https://$address:8443/api/catalog оборвался, хост $address:8443"
        ).userMessage("Не получилось")
        assertFalse(message, message.contains(address))
        assertFalse(message, message.contains("8443"))
    }

    @Test
    fun `текст сервера остаётся как есть`() {
        val text = "Тайтл 18+. Он открывается только после входа в аккаунт."
        assertEquals(text, ServerException(403, text).userMessage("Не получилось"))
    }

    @Test
    fun `пустое сообщение заменяется запасным текстом экрана`() {
        assertEquals("Список не загрузился",
                     IllegalStateException().userMessage("Список не загрузился"))
        // Сообщение из одного адреса после чистки осмысленного текста не несёт,
        // но и пустым остаться не должно.
        val onlyAddress = IllegalStateException("$address:8443").userMessage("Запасной текст")
        assertFalse(onlyAddress, onlyAddress.contains(address))
        assertTrue(onlyAddress, onlyAddress.isNotBlank())
    }
}
