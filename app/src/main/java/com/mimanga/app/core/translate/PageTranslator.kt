package com.mimanga.app.core.translate

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Переведённый кусок текста и место, где он лежал на странице (в пикселях картинки). */
data class TranslatedBlock(
    val box: Rect,
    val original: String,
    val translated: String,
)

/**
 * Чем занят переводчик прямо сейчас — это показывает меню читалки.
 *
 * Доли процентов здесь нет намеренно: ML Kit скачивает языковую модель одной
 * задачей и о ходе загрузки не сообщает — ни размера, ни байтов. Поэтому
 * показываем честное «качается» и какой именно язык, а не выдуманные проценты.
 */
sealed interface TranslatorStatus {
    data object Idle : TranslatorStatus
    /** Качается языковая модель (первое включение для этого языка). */
    data class Downloading(val language: String) : TranslatorStatus
    /** Модель на месте: идёт распознавание и перевод страницы. */
    data object Working : TranslatorStatus
    data class Failed(val message: String) : TranslatorStatus
}

/** Что получилось со страницей: блоки, а при неудаче — что именно пошло не так. */
sealed interface TranslationResult {
    data class Ready(val blocks: List<TranslatedBlock>) : TranslationResult
    /** Текста на странице нет — это нормальный исход, а не ошибка. */
    data object NoText : TranslationResult
    data class Failed(val message: String) : TranslationResult
}

/** Названия языков в родительном падеже: «модель перевода с японского». */
private val LANGUAGE_NAMES = mapOf(
    TranslateLanguage.JAPANESE to "японского",
    TranslateLanguage.KOREAN to "корейского",
    TranslateLanguage.CHINESE to "китайского",
    TranslateLanguage.ENGLISH to "английского",
)

/**
 * Перевод текста прямо на странице манги — целиком на телефоне.
 *
 * Как это работает: ML Kit распознаёт текст, мы определяем ЯЗЫК по письму
 * распознанного (а не по тому, какая модель первой что-то нашла), и переводим
 * каждое облачко на русский. Ничего никуда не отправляется: и распознавание, и
 * перевод офлайновые, наружу уходит только разовая загрузка языковой модели.
 *
 * Почему язык определяется по письму, а не по модели. Японская модель читает и
 * латиницу, поэтому английская страница успешно «распознавалась» ею — и потом
 * переводилась как японская. Получалась бессмыслица. Теперь модель выбирается
 * для чтения, а язык перевода — по тому, что в тексте: кана значит японский,
 * хангыль — корейский, только ханьцзы без каны — китайский, латиница —
 * английский.
 *
 * Распознаватели и переводчики создаются один раз и живут, пока живёт
 * приложение: каждый держит загруженную модель, и пересоздавать их на каждую
 * страницу — самый дорогой способ всё замедлить.
 */
@Singleton
class PageTranslator @Inject constructor() {

    private val recognizers: Map<String, TextRecognizer> by lazy {
        linkedMapOf(
            TranslateLanguage.JAPANESE to
                TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
            TranslateLanguage.KOREAN to
                TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()),
            TranslateLanguage.CHINESE to
                TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
            TranslateLanguage.ENGLISH to
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
        )
    }

    private val translators = mutableMapOf<String, Translator>()
    private val translatorLock = Mutex()
    private val workLock = Mutex()

    private val _status = MutableStateFlow<TranslatorStatus>(TranslatorStatus.Idle)
    /** Чем занят переводчик: читалка рисует по этому полосу и подпись. */
    val status: StateFlow<TranslatorStatus> = _status.asStateFlow()

    /**
     * Переводит страницу. Вызывать можно свободно: тяжёлое кэшируется внутри.
     *
     * Страницы обрабатываются по одной (`workLock`). Предзагрузка приносит
     * несколько картинок сразу, и без очереди три распознавателя грызли бы
     * процессор одновременно — от этого медленнее становится всё, включая саму
     * прокрутку.
     */
    suspend fun translate(bitmap: Bitmap): TranslationResult = workLock.withLock {
        _status.value = TranslatorStatus.Working
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val page = read(image)
            if (page == null || page.blocks.isEmpty()) {
                _status.value = TranslatorStatus.Idle
                return@withLock TranslationResult.NoText
            }

            // Одинаковые реплики на странице встречаются часто («!!», «Что?»),
            // и переводить их по второму разу незачем.
            val cache = HashMap<String, String>()
            val blocks = mutableListOf<TranslatedBlock>()
            // Почему модель не сработала, надо сказать вслух: «перевода нет»
            // и «модель не скачалась» — это разные вещи для человека.
            var failure: String? = null
            for (block in page.blocks) {
                val language = scriptLanguage(block.text) ?: page.language
                val text = normalize(block.text, cjk = language != TranslateLanguage.ENGLISH)
                if (!isMeaningful(text)) continue
                val translated = cache.getOrPut(language + "\u0000" + text) {
                    val translator = try {
                        translatorFor(language)
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Exception) {
                        Timber.w(error, "Модель перевода с %s не готова", language)
                        failure = error.message ?: "Модель перевода не загрузилась"
                        return@getOrPut ""
                    }
                    _status.value = TranslatorStatus.Working
                    runCatching { translator.translate(text).await() }.getOrDefault("")
                }
                if (translated.isBlank()) continue
                blocks += TranslatedBlock(block.box, text, translated)
            }
            val problem = failure
            _status.value = if (problem == null) TranslatorStatus.Idle
                            else TranslatorStatus.Failed(problem)
            when {
                blocks.isNotEmpty() -> TranslationResult.Ready(blocks)
                problem != null -> TranslationResult.Failed(problem)
                else -> TranslationResult.NoText
            }
        } catch (cancel: CancellationException) {
            _status.value = TranslatorStatus.Idle
            throw cancel
        } catch (error: Exception) {
            Timber.w(error, "Перевод страницы не получился")
            val message = error.message ?: "Перевод не получился"
            _status.value = TranslatorStatus.Failed(message)
            TranslationResult.Failed(message)
        }
    }

    /** Распознанная страница: куски текста и язык, на котором она написана. */
    private class Recognized(val language: String, val blocks: List<PageBlock>)

    private class PageBlock(val box: Rect, val text: String)

    /**
     * Читает страницу и решает, на каком она языке.
     *
     * Сначала японской моделью — она же читает латиницу, поэтому одного
     * прохода хватает для японского и английского. Если письмо оказалось
     * другим (хангыль, ханьцзы без каны) или не нашлось ничего, страница
     * перечитывается моделью нужного письма: чужой моделью текст читается с
     * ошибками, а ошибка чтения — это ошибка перевода.
     */
    private suspend fun read(image: InputImage): Recognized? {
        val first = recognizeWith(TranslateLanguage.JAPANESE, image)
        val firstLanguage = first?.let { scriptLanguage(it.joinToString(" ") { b -> b.text }) }
        if (first != null && firstLanguage == TranslateLanguage.JAPANESE) {
            return Recognized(TranslateLanguage.JAPANESE, first)
        }
        // Порядок перечитывания: сначала то письмо, на которое похож текст,
        // потом остальные — вдруг японская модель не прочла ничего.
        val order = buildList {
            firstLanguage?.let { add(it) }
            addAll(listOf(TranslateLanguage.KOREAN, TranslateLanguage.CHINESE,
                          TranslateLanguage.ENGLISH))
        }.distinct()
        for (language in order) {
            val blocks = recognizeWith(language, image) ?: continue
            val detected = scriptLanguage(blocks.joinToString(" ") { it.text })
            if (detected == language) return Recognized(language, blocks)
        }
        // Ничего не сошлось: берём первое прочитанное с языком по письму.
        return first?.let { Recognized(firstLanguage ?: TranslateLanguage.ENGLISH, it) }
    }

    private suspend fun recognizeWith(language: String, image: InputImage): List<PageBlock>? {
        val recognizer = recognizers[language] ?: return null
        val text = runCatching { recognizer.process(image).await() }.getOrNull() ?: return null
        val blocks = text.textBlocks.mapNotNull { block ->
            val box = block.boundingBox ?: return@mapNotNull null
            if (block.text.isBlank()) return@mapNotNull null
            PageBlock(box, block.text)
        }
        return blocks.ifEmpty { null }
    }

    /**
     * Переводчик с языка на русский.
     *
     * Языковая модель скачивается один раз и потом живёт на телефоне. Условий
     * на сеть не ставим: человек включил перевод осознанно и ждёт, что он
     * заработает сейчас, а не «когда будет Wi-Fi». О загрузке читалка пишет под
     * самим переключателем.
     */
    private suspend fun translatorFor(source: String): Translator = translatorLock.withLock {
        translators[source]?.let { return@withLock it }
        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(TranslateLanguage.RUSSIAN)
                .build()
        )
        // Готовая модель отдаётся мгновенно, поэтому «качается» показываем
        // только тогда, когда её действительно нет на телефоне.
        if (!isDownloaded(source)) {
            _status.value = TranslatorStatus.Downloading(LANGUAGE_NAMES[source] ?: source)
        }
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        translators[source] = translator
        translator
    }

    /** Лежит ли языковая модель на телефоне. */
    private suspend fun isDownloaded(language: String): Boolean = runCatching {
        val model = TranslateRemoteModel.Builder(language).build()
        RemoteModelManager.getInstance().isModelDownloaded(model).await()
    }.getOrDefault(false)
}

/**
 * Язык по письму текста.
 *
 * Кана — японский однозначно. Хангыль — корейский. Ханьцзы без каны —
 * китайский: японский текст почти никогда не обходится без каны, а китайский
 * её не знает вовсе. Латиница — английский. null значит «букв слишком мало,
 * чтобы решать».
 */
internal fun scriptLanguage(text: String): String? {
    var kana = 0
    var han = 0
    var hangul = 0
    var latin = 0
    for (ch in text) {
        when {
            ch in '\u3040'..'\u30FF' -> kana++
            ch in '\uAC00'..'\uD7A3' || ch in '\u1100'..'\u11FF' -> hangul++
            ch in '\u4E00'..'\u9FFF' -> han++
            ch.code < 0x0250 && ch.isLetter() -> latin++
        }
    }
    val total = kana + han + hangul + latin
    if (total < 2) return null
    return when {
        hangul > total / 4 -> TranslateLanguage.KOREAN
        kana > 0 -> TranslateLanguage.JAPANESE
        han > 0 -> TranslateLanguage.CHINESE
        latin > 0 -> TranslateLanguage.ENGLISH
        else -> null
    }
}

/**
 * Приводит распознанный кусок к виду, пригодному для перевода.
 *
 * Распознаватель отдаёт облачко построчно, а в облачке фраза разорвана
 * переносами по ширине пузыря. Для японского, корейского и китайского строки
 * склеиваются БЕЗ пробелов — пробел внутри фразы для этих языков означает
 * границу слова, и перевод от него ломался. Для латиницы, наоборот, пробел
 * нужен.
 */
internal fun normalize(raw: String, cjk: Boolean): String {
    val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val joined = if (cjk) lines.joinToString("") else lines.joinToString(" ")
    return if (cjk) joined.replace(" ", "") else joined.replace(Regex("\\s+"), " ")
}

/** Годится ли кусок для перевода: звуки и одиночные знаки только мешают. */
internal fun isMeaningful(text: String): Boolean = text.count { it.isLetter() } >= 2

/**
 * Task из Google Play Services в виде suspend-функции.
 *
 * Обычно это делает kotlinx-coroutines-play-services, но ради одной функции
 * тащить отдельную библиотеку незачем.
 */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value -> continuation.resume(value) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
