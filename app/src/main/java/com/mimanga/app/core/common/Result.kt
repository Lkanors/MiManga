package com.mimanga.app.core.common

import com.mimanga.app.core.network.userMessage

/**
 * Универсальный тип результата операций.
 */
sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Error(val exception: Throwable, val message: String? = null) : Result<Nothing>
    data object Loading : Result<Nothing>
}

inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) action(data)
    return this
}

inline fun <T> Result<T>.onError(action: (Throwable) -> Unit): Result<T> {
    if (this is Result.Error) action(exception)
    return this
}

fun <T> Result<T>.getOrNull(): T? = when (this) {
    is Result.Success -> data
    else -> null
}

suspend fun <T> safeCall(block: suspend () -> T): Result<T> {
    return try {
        Result.Success(block())
    } catch (e: Exception) {
        // Текст ошибки может уйти на экран, а в сетевых сообщениях лежит адрес
        // сервера — поэтому чистим его здесь же (см. core/network/NetworkErrors).
        Result.Error(e, e.userMessage("Не получилось"))
    }
}
