package com.mimanga.app.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Сколько процентов картинки уже скачано.
 *
 * Страницы манги весят по несколько мегабайт, и на медленной сети между
 * нажатием и картинкой проходят секунды. Крутящийся кружок в это время не
 * говорит ничего: непонятно, идёт загрузка или всё встало. Поэтому байты
 * считаются прямо в ответе OkHttp ([interceptor]), а экран страницы
 * подписывается на долю по адресу картинки.
 *
 * Хранится только то, что качается сейчас: записи старше [CAPACITY] последних
 * выбрасываются, иначе за главу из двухсот страниц карта росла бы без предела.
 *
 * Считается только то, за чем действительно следят. Обложек в каталоге на
 * экране десятки, и раньше на каждый их кусок в 8 КБ создавалась и
 * обновлялась запись в карте — работа, результат которой никто не читал:
 * проценты показывает только страница главы. Теперь адрес попадает в карту
 * лишь через [progressOf] (его вызывает индикатор страницы), а всё остальное
 * проходит мимо счётчика.
 */
object ImageProgress {

    private const val CAPACITY = 96

    /** Шаг обновления. Полоса и проценты меняются раз в процент, а не на каждый
     *  прочитанный буфер: сотни лишних обновлений состояния в секунду дают
     *  только мусор в памяти и лишние кадры. */
    private const val STEP = 0.01f

    /** Доля 0..1; -1 означает «размер неизвестен, показываем бесконечный кружок». */
    const val UNKNOWN = -1f

    private val flows = ConcurrentHashMap<String, MutableStateFlow<Float>>()
    private val order = ArrayDeque<String>()

    /** Подписка на прогресс. Только с этого момента адрес начинают считать. */
    fun progressOf(url: String): StateFlow<Float> = stateOf(url)

    /** Следит ли кто-нибудь за этим адресом. */
    fun isWatched(url: String): Boolean = flows.containsKey(url)

    private fun stateOf(url: String): MutableStateFlow<Float> =
        flows.getOrPut(url) {
            synchronized(order) {
                order.addLast(url)
                while (order.size > CAPACITY) {
                    val oldest = order.removeFirst()
                    // Только что добавленный адрес не выбрасываем: экран уже
                    // держит на него ссылку и перестал бы получать обновления.
                    if (oldest != url) flows.remove(oldest)
                }
            }
            MutableStateFlow(0f)
        }

    fun report(url: String, read: Long, total: Long) {
        val flow = flows[url] ?: return
        if (total <= 0) {
            flow.value = UNKNOWN
            return
        }
        val value = (read.toFloat() / total).coerceIn(0f, 1f)
        val shown = flow.value
        // Последний кусок пропускать нельзя: на нём кружок доходит до конца.
        if (value < 1f && shown >= 0f && value - shown < STEP) return
        flow.value = value
    }

    fun finish(url: String) {
        flows[url]?.value = 1f
    }

    /**
     * Интерцептор OkHttp: подменяет тело ответа на считающее байты.
     *
     * Ставится сетевым (addNetworkInterceptor), чтобы не срабатывать на
     * картинках, которые Coil достал из своего дискового кэша, — там считать
     * нечего, картинка появляется сразу.
     */
    fun interceptor(): Interceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val url = chain.request().url.toString()
        val body = response.body
        // За обложками никто не следит — считать их байты незачем.
        if (body == null || !isWatched(url)) return@Interceptor response
        response.newBuilder()
            .body(CountingBody(body, url))
            .build()
    }

    private class CountingBody(
        private val body: ResponseBody,
        private val url: String,
    ) : ResponseBody() {

        // Имя `delegate` здесь занято: ForwardingSource ниже объявляет своё,
        // и внутри него ссылка на тело ответа перестала бы разрешаться.
        private val counted: BufferedSource by lazy { counting(body.source()).buffer() }

        override fun contentType(): MediaType? = body.contentType()
        override fun contentLength(): Long = body.contentLength()
        override fun source(): BufferedSource = counted

        private fun counting(origin: Source): Source = object : ForwardingSource(origin) {
            private var read = 0L
            private val total = body.contentLength()

            override fun read(sink: Buffer, byteCount: Long): Long {
                val count = super.read(sink, byteCount)
                if (count == -1L) {
                    finish(url)
                } else {
                    read += count
                    report(url, read, total)
                }
                return count
            }
        }
    }
}
