package com.wallora.app.data.remote

import com.wallora.app.data.remote.api.NasaApi
import com.wallora.app.data.remote.dto.NasaItem
import com.wallora.app.domain.WallpaperSource
import com.wallora.app.domain.model.Category
import com.wallora.app.domain.model.Page
import com.wallora.app.domain.model.SourceId
import com.wallora.app.domain.model.Wallpaper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NasaSource @Inject constructor(
    private val api: NasaApi,
) : WallpaperSource {

    override val id: SourceId = SourceId.NASA
    override val isConfigured: Boolean = true  // keyless public API

    override suspend fun browse(categories: List<Category>, page: String): Page<Wallpaper> {
        val pageNum = page.toIntOrNull() ?: 1
        val query = if (categories.isEmpty()) "galaxy nebula space"
                    else categories.joinToString(" ") { it.nasaQuery }
        return fetchPage(query, pageNum, categories.firstOrNull())
    }

    override suspend fun search(query: String, page: String): Page<Wallpaper> {
        val pageNum = page.toIntOrNull() ?: 1
        return fetchPage("$query space astronomy", pageNum, null)
    }

    private suspend fun fetchPage(query: String, pageNum: Int, category: Category?): Page<Wallpaper> {
        val resp = api.search(
            query = query,
            mediaType = NasaApi.MEDIA_TYPE,
            page = pageNum,
            pageSize = NasaApi.PAGE_SIZE,
        )
        val items = resp.collection.items
            .filter { it.data.isNotEmpty() && it.links.any { l -> l.rel == "preview" } }
            .mapNotNull { it.toDomain(category) }
        val hasNext = resp.collection.links.any { it.rel == "next" }
        return Page(items = items, nextPage = if (hasNext) (pageNum + 1).toString() else null)
    }
}

internal fun NasaItem.toDomain(category: Category?): Wallpaper? {
    val assetData = data.firstOrNull() ?: return null
    val nasaId = assetData.nasaId.ifBlank { return null }
    val previewLink = links.firstOrNull { it.rel == "preview" } ?: return null
    val thumbUrl = previewLink.href

    // Derive a higher-resolution URL from the thumbnail pattern.
    // NASA CDN pattern: .../image/{nasaId}/{nasaId}~thumb.jpg → ~large.jpg / ~orig.jpg
    val fullUrl = when {
        thumbUrl.contains("~thumb.jpg") -> thumbUrl.replace("~thumb.jpg", "~orig.jpg")
        thumbUrl.contains("~small.jpg") -> thumbUrl.replace("~small.jpg", "~orig.jpg")
        else -> thumbUrl
    }

    val author = assetData.photographer.ifBlank { assetData.center.ifBlank { "NASA" } }
    return Wallpaper(
        id = nasaId,
        sourceId = SourceId.NASA,
        thumbUrl = thumbUrl,
        fullUrl = fullUrl,
        width = 0,   // NASA API doesn't provide dimensions in search results
        height = 0,
        author = author,
        authorUrl = "https://www.nasa.gov",
        sourcePageUrl = "https://images.nasa.gov/details/$nasaId",
        colorHint = null,
        category = category,
        tags = listOf(assetData.title).filter { it.isNotBlank() },
    )
}
