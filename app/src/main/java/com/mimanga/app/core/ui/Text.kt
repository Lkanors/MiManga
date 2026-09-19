package com.mimanga.app.core.ui

/**
 * Снятие разметки с текстов, пришедших с сайтов-источников.
 *
 * Сервер чистит описания при разборе, но карточка могла попасть в кэш раньше,
 * а часть источников присылает разметку в новых полях. Поэтому клиент ещё раз
 * убирает теги вроде `<p dir="ltr">` и `<br>` и возвращает HTML-сущности
 * (`&amp;`, `&#039;`) к обычным символам — служебных символов в описании
 * пользователь видеть не должен.
 */
private val BLOCK_TAGS = Regex("(?i)<\\s*br\\s*/?\\s*>|</\\s*(p|div|li|tr|h[1-6])\\s*>")
private val ANY_TAG = Regex("<[^<>]{0,400}?>")
private val NAMED_ENTITIES = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "nbsp" to " ", "laquo" to "«", "raquo" to "»", "mdash" to "—", "ndash" to "–",
    "hellip" to "…", "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“", "rdquo" to "”",
)
private val ENTITY = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]{2,8});")

fun String.stripHtml(): String {
    if (isBlank() || (!contains('<') && !contains('&'))) return trim()
    var text = BLOCK_TAGS.replace(this, "\n")
    text = ANY_TAG.replace(text, "")
    text = ENTITY.replace(text) { match ->
        val body = match.groupValues[1]
        when {
            body.startsWith("#x", ignoreCase = true) ->
                body.drop(2).toIntOrNull(16)?.toChar()?.toString() ?: match.value
            body.startsWith("#") -> body.drop(1).toIntOrNull()?.toChar()?.toString() ?: match.value
            else -> NAMED_ENTITIES[body.lowercase()] ?: match.value
        }
    }
    return text.lines().joinToString("\n") { it.trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
