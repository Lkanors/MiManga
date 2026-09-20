package com.mimanga.app.core.network

import androidx.compose.runtime.mutableStateOf
import com.mimanga.app.BuildConfig
import com.mimanga.app.domain.model.Manga
import java.security.MessageDigest

/**
 * Адреса картинок, которые отдаёт наш сервер.
 *
 * Обложки берутся не с CDN источников, а через сервер: `cover.cdnlibs.org`
 * (mangalib — большая часть каталога) отвечает 403 без заголовка Referer и у
 * части провайдеров недоступен вовсе, remanga и mangapoisk тоже требуют «свой»
 * Referer. Сервер до всех CDN дотягивается, держит скачанное в кэше и отдаёт
 * картинку по тому же зашифрованному каналу, что и остальной каталог.
 *
 * Прямая ссылка из карточки остаётся запасным путём: если сервер обложку не
 * достал, клиент пробует сходить на CDN сам (см. CoverImage).
 */
object ServerImages {

    /**
     * Ширина обложки в карточке каталога и в строках главной.
     *
     * Ширину мы просим у сервера сами (`?w=`), потому что в его кэше лежит
     * оригинал с CDN: половина обложек — 75 КБ, а хвост доходит до 6 МБ и 13
     * мегапикселей. В карточке шириной около 330 точек такая картинка не нужна
     * ни сети, ни раскодировщику — а именно на ней и появлялись рывки при
     * листании и пустые карточки. Значения совпадают с covers.THUMB_WIDTHS на
     * сервере: чужие он округлит до ближайшей разрешённой.
     */
    const val CARD_WIDTH = 320

    /** Обложка на странице тайтла — 126 dp, то есть до 500 точек. */
    const val PAGE_WIDTH = 640

    /** Подложка шапки тайтла: размытые пятна, хватает самой мелкой копии. */
    const val BLUR_WIDTH = 64

    /**
     * Метка того, кто сейчас смотрит: по ней различаются адреса обложек.
     *
     * Обложку 18+ размывает сервер — он же решает по токену аккаунта, кому
     * какую отдать. Но кэш картинок в приложении знает только адрес: войди
     * человек в аккаунт — и на экране остались бы размытые обложки из кэша,
     * выйди — остались бы неразмытые. Поэтому в адрес дописывается отпечаток
     * токена: сервер этот параметр не читает, а кэш видит другую картинку и
     * идёт за ней заново. У гостя метки нет, и его адреса — те же, что были.
     *
     * Отпечаток, а не сам токен: адрес попадает в кэш на диске и в журналы,
     * а ключ от аккаунта там ни к чему.
     *
     * Значение — состояние Compose: адреса собираются прямо в отрисовке, и
     * после входа или выхода карточки перерисуются сами.
     */
    private val mark = mutableStateOf("")

    /** Вызывается при каждой смене токена — см. AuthStore. */
    fun viewer(token: String?) {
        mark.value = if (token.isNullOrBlank()) "" else fingerprint(token)
    }

    private fun fingerprint(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .take(4)
            .joinToString("") { "%02x".format(it) }

    fun cover(key: String, width: Int = 0): String {
        if (key.isBlank()) return ""
        val address = "${BuildConfig.SERVER_URL}/api/cover/$key"
        val parameters = buildList {
            if (width > 0) add("w=$width")
            if (mark.value.isNotEmpty()) add("u=${mark.value}")
        }
        return if (parameters.isEmpty()) address
        else address + "?" + parameters.joinToString("&")
    }

    fun cover(manga: Manga, width: Int = 0): String = cover(manga.actionKey, width)
}
