package com.mimanga.app.core.network

import com.mimanga.app.BuildConfig
import com.mimanga.app.domain.model.Manga

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

    fun cover(key: String, width: Int = 0): String = when {
        key.isBlank() -> ""
        width > 0 -> "${BuildConfig.SERVER_URL}/api/cover/$key?w=$width"
        else -> "${BuildConfig.SERVER_URL}/api/cover/$key"
    }

    fun cover(manga: Manga, width: Int = 0): String = cover(manga.actionKey, width)
}
