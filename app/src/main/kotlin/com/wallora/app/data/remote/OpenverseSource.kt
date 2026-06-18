package com.wallora.app.data.remote

import com.wallora.app.data.remote.api.OpenverseApi
import com.wallora.app.data.remote.dto.OpenverseImage
import com.wallora.app.domain.WallpaperSource
import com.wallora.app.domain.model.Category
import com.wallora.app.domain.model.Page
import com.wallora.app.domain.model.SourceId
import com.wallora.app.domain.model.Wallpaper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenverseSource @Inject constructor(
    private val api: OpenverseApi,
) : WallpaperSource {

    override val id: SourceId = SourceId.OPENVERSE
    override val isConfigured: Boolean = true  // keyless — no registration required

    override suspend fun browse(categories: List<Category>, page: String): Page<Wallpaper> {
        val pageNum = page.toIntOrNull() ?: 1
        val query = if (categories.isEmpty()) "wallpaper hd"
                    else categories.joinToString(" ") { it.openverseQuery }
        return fetchPage(query, pageNum, categories.firstOrNull())
    }

    override suspend fun search(query: String, page: String): Page<Wallpaper> {
        val pageNum = page.toIntOrNull() ?: 1
        return fetchPage(query, pageNum, null)
    }

    private suspend fun fetchPage(query: String, pageNum: Int, category: Category?): Page<Wallpaper> {
        val resp = api.search(
            query = query,
            page = pageNum,
            pageSize = OpenverseApi.PAGE_SIZE,
            licenseType = OpenverseApi.LICENSE_TYPES,
            mature = false,
        )
        val items = resp.results
            .filter { it.url.isNotBlank() && it.width > 0 && it.height > 0 }
            .filter { it.height.toFloat() / it.width.toFloat() >= 0.5f }  // portrait-compatible
            .map { it.toDomain(category) }
        val hasMore = pageNum < resp.pageCount
        return Page(items = items, nextPage = if (hasMore) (pageNum + 1).toString() else null)
    }
}

internal fun OpenverseImage.toDomain(category: Category?): Wallpaper = Wallpaper(
    id = id,
    sourceId = SourceId.OPENVERSE,
    thumbUrl = thumbnail.ifBlank { url },
    fullUrl = url,
    width = width,
    height = height,
    author = creator,
    authorUrl = creatorUrl,
    sourcePageUrl = foreignLandingUrl.ifBlank { url },
    colorHint = null,
    category = category,
    tags = if (title.isNotBlank()) listOf(title) else emptyList(),
)
