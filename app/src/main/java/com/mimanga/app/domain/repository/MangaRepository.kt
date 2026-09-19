package com.mimanga.app.domain.repository

import com.mimanga.app.data.remote.CatalogResponse
import com.mimanga.app.domain.model.CatalogFilters
import com.mimanga.app.domain.model.CatalogSort
import com.mimanga.app.domain.model.Chapter
import com.mimanga.app.domain.model.FilterOptions
import com.mimanga.app.domain.model.HomeRow
import com.mimanga.app.domain.model.Manga
import com.mimanga.app.domain.model.Page

interface MangaRepository {
    suspend fun catalog(
        page: Int = 1,
        sort: CatalogSort = CatalogSort.POPULAR,
        includeForeign: Boolean = false,
        filters: CatalogFilters = CatalogFilters(),
    ): CatalogResponse

    suspend fun search(
        query: String,
        page: Int = 1,
        includeForeign: Boolean = false,
        filters: CatalogFilters = CatalogFilters(),
    ): CatalogResponse

    /** Строки главной каталога: популярное, история чтения, рекомендации. */
    suspend fun home(rows: Int, includeForeign: Boolean): List<HomeRow>

    suspend fun filterOptions(): FilterOptions

    suspend fun getMangaById(mangaId: String): Manga

    suspend fun similar(mangaId: String): List<Manga>

    suspend fun getChaptersByMangaId(mangaId: String, sourceId: String): List<Chapter>

    suspend fun getChapters(sourceId: String, url: String): List<Chapter>

    suspend fun getPages(sourceId: String, chapterUrl: String): List<Page>
}
