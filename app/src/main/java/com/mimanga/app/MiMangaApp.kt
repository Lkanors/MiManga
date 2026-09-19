package com.mimanga.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.mimanga.app.core.network.ImageProgress
import com.mimanga.app.core.network.ServerTls
import dagger.hilt.android.HiltAndroidApp
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class MiMangaApp : Application(), SingletonImageLoader.Factory {

    /**
     * Referer, который CDN конкретных источников требуют при отдаче картинок.
     * Сервер по ТЗ отдаёт прямые ссылки (url), поэтому заголовки проставляет клиент.
     * Ключи — узнаваемые части домена (совпадают со списком на сервере).
     */
    private val imageReferers: List<Pair<String, String>> = listOf(
        "cdnlibs.org" to "https://mangalib.me/",
        "mangalib.me" to "https://mangalib.me/",
        "imglib.info" to "https://mangalib.me/",
        "one-way.work" to "https://mangalib.me/",
        // reimg.org — CDN ReadManga, reimg2/reimg3 — зеркала Remanga
        "reimg.org" to "https://readmanga.me/",
        "reimg2.org" to "https://remanga.org/",
        "reimg3.org" to "https://remanga.org/",
        "reimg" to "https://remanga.org/",
        "remanga.org" to "https://remanga.org/",
        "rmr.rocks" to "https://readmanga.me/",
        "readmanga" to "https://readmanga.me/",
        "mangapoisk" to "https://mangapoisk.me/",
        "yaoipoisk" to "https://mangapoisk.me/",
        "senkuro" to "https://senkuro.com/",
        "mangabuff" to "https://mangabuff.ru/",
        "mangadex" to "https://mangadex.org/",
        "2xstorage.com" to "https://mangadex.org/",
        "asurascans" to "https://asurascans.com/",
        "pstatic.net" to "https://www.webtoons.com/",
        "webtoons.com" to "https://www.webtoons.com/",
    )

    /**
     * OkHttpClient для загрузки картинок: длинные таймауты для медленных CDN
     * + Referer по домену (часть CDN, например img3.cdnlibs.org у MangaLib,
     * отдаёт 403 без него).
     */
    private val imageHttpClient: OkHttpClient by lazy {
        // Картинки идут с чужих CDN (системные центры сертификации) и изредка
        // через прокси нашего сервера — его самоподписанный сертификат
        // добавлен к системным, иначе прокси-путь не работал бы.
        val trustManager = ServerTls.imageTrustManager(this)
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Обложки идут почти все с одного хоста — нашего сервера, — а
            // OkHttp по умолчанию держит к одному хосту только 5 запросов
            // сразу. На экране каталога их дюжина, и остальные ждали в
            // очереди: карточки заполнялись волнами. Сервер отдаёт обложки
            // асинхронно, так что дюжина сразу ему не мешает.
            .dispatcher(Dispatcher().apply {
                maxRequests = 32
                maxRequestsPerHost = 12
            })
            .sslSocketFactory(ServerTls.sslContext(trustManager).socketFactory, trustManager)
            .addInterceptor(refererInterceptor())
            // Считает скачанные байты: по ним страница рисует свой прогресс.
            .addNetworkInterceptor(ImageProgress.interceptor())
            .build()
    }

    private fun refererInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val host = request.url.host
        val referer = imageReferers.firstOrNull { (needle, _) -> host.contains(needle) }?.second
            ?: ("https://" + host + "/")
        chain.proceed(request.newBuilder().header("Referer", referer).build())
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Адрес сервера больше не хранится в настройках: он зашит в сборку.
    }

    /**
     * Общий ImageLoader: кэш в памяти и на диске.
     *
     * Кэш в памяти задан явно и щедро (треть доступной приложению памяти):
     * при листании каталога одни и те же обложки уходят с экрана и
     * возвращаются десятки раз, и каждое возвращение без кэша — это чтение
     * файла и раскодирование JPEG, то есть пропущенные кадры. Обложки мелкие,
     * так что в эту треть их влезают сотни.
     *
     * Кэш на диске нужен для другого: чтобы обложки и страницы не качались
     * заново после перезапуска приложения.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = imageHttpClient))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("manga_images"))
                    .maxSizeBytes(512L * 1024 * 1024) // 512 MB
                    .build()
            }
            .build()
    }
}
