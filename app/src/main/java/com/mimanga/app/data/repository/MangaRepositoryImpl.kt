package com.mimanga.app.data.repository

import com.mimanga.app.data.remote.CatalogResponse
import com.mimanga.app.data.remote.MangaServerApi
import com.mimanga.app.domain.model.CatalogFilters
import com.mimanga.app.domain.model.CatalogSort
import com.mimanga.app.domain.model.Chapter
import com.mimanga.app.domain.model.FilterOptions
import com.mimanga.app.domain.model.HomeRow
import com.mimanga.app.domain.model.Manga
import com.mimanga.app.domain.model.Page
import com.mimanga.app.domain.repository.MangaRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaRepositoryImpl @Inject constructor(
    private val api: MangaServerApi,
) : MangaRepository {

    override suspend fun catalog(
        page: Int,
        sort: CatalogSort,
        includeForeign: Boolean,
        filters: CatalogFilters,
    ): CatalogResponse = api.getCatalog(page = page, sort = sort,
                                        includeForeign = includeForeign, filters = filters)

    override suspend fun search(
        query: String,
        page: Int,
        includeForeign: Boolean,
        filters: CatalogFilters,
    ): CatalogResponse =
        api.searchCatalog(query, page = page, includeForeign = includeForeign, filters = filters)

    override suspend fun home(rows: Int, includeForeign: Boolean): List<HomeRow> =
        api.getHome(rows = rows, includeForeign = includeForeign).rows

    override suspend fun filterOptions(): FilterOptions = api.getFilterOptions()

    override suspend fun getMangaById(mangaId: String): Manga = api.getMangaById(mangaId)

    /** Похожее — вещь необязательная: ошибка здесь не должна ломать экран. */
    override suspend fun similar(mangaId: String): List<Manga> = runCatching {
        api.getSimilar(mangaId).results
    }.onFailure { Timber.w(it, "Похожие тайтлы недоступны") }.getOrDefault(emptyList())

    override suspend fun getChaptersByMangaId(mangaId: String, sourceId: String): List<Chapter> =
        api.getChaptersByMangaId(mangaId, sourceId).chapters

    override suspend fun getChapters(sourceId: String, url: String): List<Chapter> =
        api.getChapters(sourceId, url).chapters

    override suspend fun getPages(sourceId: String, chapterUrl: String,
                                  mangaKey: String): List<Page> =
        api.getPages(sourceId, chapterUrl, mangaKey).pages
}
