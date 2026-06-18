package com.wallora.app.data.remote

import com.wallora.app.data.remote.api.FlickrApi
import com.wallora.app.data.remote.dto.FlickrPhoto
import com.wallora.app.di.UserKeyCache
import com.wallora.app.domain.WallpaperSource
import com.wallora.app.domain.model.Category
import com.wallora.app.domain.model.Page
import com.wallora.app.domain.model.SourceId
import com.wallora.app.domain.model.Wallpaper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlickrSource @Inject constructor(
    private val api: FlickrApi,
    private val userKeyCache: UserKeyCache,
) : WallpaperSource {

    override val id: SourceId = SourceId.FLICKR
    override val isConfigured: Boolean get() = userKeyCache.effectiveFlickrKey.isNotBlank()

    override suspend fun browse(categories: List<Category>, page: String): Page<Wallpaper> {
        val pageNum = page.toIntOrNull() ?: 1
        val query = if (categories.isEmpty()) "wallpaper hd nature"
                    else categories.joinToString(" ") { it.flickrQuery }
        return fetchPage(query, pageNum, categories.firstOrNull())
    }

    override suspend fun search(query: String, page: String): Page<Wallpaper> {
        val pageNum = page.toIntOrNull() ?: 1
        return fetchPage(query, pageNum, null)
    }

    private suspend fun fetchPage(query: String, pageNum: Int, category: Category?): Page<Wallpaper> {
        val key = userKeyCache.effectiveFlickrKey
        val resp = api.search(
            method = FlickrApi.METHOD_SEARCH,
            format = FlickrApi.FORMAT,
            noJsonCallback = FlickrApi.NO_JSON_CALLBACK,
            apiKey = key,
            query = query,
            safeSearch = FlickrApi.SAFE_SEARCH,
            contentType = FlickrApi.CONTENT_TYPE,
            license = FlickrApi.LICENSES,
            sort = FlickrApi.SORT,
            extras = FlickrApi.EXTRAS,
            perPage = FlickrApi.PAGE_SIZE,
            page = pageNum,
        )
        val items = resp.photos.photo
            .filter { it.isPortraitCompatible() }
            .map { it.toDomain(category) }
        val hasMore = pageNum < resp.photos.pages
        return Page(items = items, nextPage = if (hasMore) (pageNum + 1).toString() else null)
    }
}

private fun FlickrPhoto.isPortraitCompatible(): Boolean {
    val w = widthL.toIntOrNull()?.takeIf { it > 0 } ?: widthM.toIntOrNull()?.takeIf { it > 0 } ?: return true
    val h = heightL.toIntOrNull()?.takeIf { it > 0 } ?: heightM.toIntOrNull()?.takeIf { it > 0 } ?: return true
    return h.toFloat() / w.toFloat() >= 0.5f
}

internal fun FlickrPhoto.toDomain(category: Category?): Wallpaper {
    val fullUrl = urlL.ifBlank { urlM }
    val thumbUrl = urlM.ifBlank { fullUrl }
    val w = widthL.toIntOrNull()?.takeIf { it > 0 } ?: widthM.toIntOrNull() ?: 0
    val h = heightL.toIntOrNull()?.takeIf { it > 0 } ?: heightM.toIntOrNull() ?: 0
    return Wallpaper(
        id = id,
        sourceId = SourceId.FLICKR,
        thumbUrl = thumbUrl,
        fullUrl = fullUrl,
        width = w,
        height = h,
        author = ownername,
        authorUrl = "https://www.flickr.com/photos/$owner",
        sourcePageUrl = "https://www.flickr.com/photos/$owner/$id",
        colorHint = null,
        category = category,
        tags = if (title.isNotBlank()) listOf(title) else emptyList(),
    )
}
