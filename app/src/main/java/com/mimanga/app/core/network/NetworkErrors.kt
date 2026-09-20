package com.mimanga.app.core.network

import com.mimanga.app.data.remote.ServerException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Текст ошибки, который можно показать человеку.
 *
 * Зачем. Сообщения сетевых ошибок содержат адрес, к которому не удалось
 * подключиться: «Failed to connect to /203.0.113.10:8443». Показывать это
 * нельзя — адрес сервера незачем знать никому, кто смотрит в экран (или в
 * скриншот), а человеку он всё равно ничего не объясняет.
 *
 * Поэтому известные сетевые ошибки заменяются на короткие фразы по делу, а
 * у всего остального из текста вырезается то, что похоже на адрес: ссылка,
 * IP и порт. Если после чистки ничего осмысленного не осталось, берётся
 * запасной текст экрана.
 *
 * Ошибки самого сервера (ServerException) не трогаем: их текст написан
 * сервером для человека — «Тайтл 18+…», «Нужно войти в аккаунт».
 */
fun Throwable.userMessage(fallback: String): String = when (this) {
    is ServerException -> message
    is UnknownHostException, is ConnectException, is NoRouteToHostException ->
        "Сервер недоступен. Проверьте подключение к интернету."
    is SocketTimeoutException -> "Сервер не ответил вовремя. Попробуйте ещё раз."
    is SSLException -> "Защищённое соединение с сервером не установилось."
    is IOException -> "Связь с сервером прервалась. Попробуйте ещё раз."
    else -> scrubAddresses(message).ifBlank { fallback }
}

/** Ссылки, адреса и порты в тексте — на «сервере»; остальное как было. */
internal fun scrubAddresses(text: String?): String {
    if (text.isNullOrBlank()) return ""
    return text
        .replace(Regex("""\b[a-z][a-z0-9+.-]*://\S+"""), "сервере")
        .replace(Regex("""/?\b\d{1,3}(\.\d{1,3}){3}(:\d+)?\b"""), "сервере")
        // Голый порт после чистки адреса («…: 8443») смысла уже не несёт.
        .replace(Regex("""(?<=сервере):\d+"""), "")
        .replace(Regex("""\s{2,}"""), " ")
        .trim()
}
