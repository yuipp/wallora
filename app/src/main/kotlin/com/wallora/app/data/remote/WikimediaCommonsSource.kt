package com.wallora.app.data.remote

import com.wallora.app.data.remote.api.WikimediaCommonsApi
import com.wallora.app.data.remote.dto.WikimediaPage
import com.wallora.app.domain.WallpaperSource
import com.wallora.app.domain.model.Category
import com.wallora.app.domain.model.Page
import com.wallora.app.domain.model.SourceId
import com.wallora.app.domain.model.Wallpaper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WikimediaCommonsSource @Inject constructor(
    private val api: WikimediaCommonsApi,
) : WallpaperSource {

    override val id: SourceId = SourceId.WIKIMEDIA
    override val isConfigured: Boolean = true  // keyless (User-Agent set via interceptor)

    override suspend fun browse(categories: List<Category>, page: String): Page<Wallpaper> {
        val query = if (categories.isEmpty()) "wallpaper landscape"
                    else categories.joinToString(" ") { it.wikimediaQuery }
        return fetchPage(query, page, categories.firstOrNull())
    }

    override suspend fun search(query: String, page: String): Page<Wallpaper> {
        return fetchPage(query, page, null)
    }

    /**
     * [page] is either "1" (first page, no offset) or the gsroffset value from a prior response.
     */
    private suspend fun fetchPage(query: String, page: String, category: Category?): Page<Wallpaper> {
        val offset = if (page == "1") null else page.toIntOrNull()
        val resp = api.search(
            action = WikimediaCommonsApi.ACTION,
            format = WikimediaCommonsApi.FORMAT,
            formatVersion = WikimediaCommonsApi.FORMAT_VERSION,
            generator = WikimediaCommonsApi.GENERATOR,
            gsrNamespace = WikimediaCommonsApi.NS_FILE,
            query = query,
            limit = WikimediaCommonsApi.PAGE_SIZE,
            offset = offset,
            prop = WikimediaCommonsApi.PROP,
            iiProp = WikimediaCommonsApi.II_PROP,
            iiUrlWidth = WikimediaCommonsApi.THUMB_WIDTH,
        )
        val pages = resp.query?.pages?.values ?: emptyList()
        val items = pages
            .filter { it.pageid > 0 }           // negative pageid = not found
            .filter { it.imageinfo.isNotEmpty() }
            .filter { it.isPortraitCompatible() }
            .mapNotNull { it.toDomain(category) }
        val nextPage = resp.continueData?.gsroffset?.toString()
        return Page(items = items, nextPage = nextPage)
    }
}

private fun WikimediaPage.isPortraitCompatible(): Boolean {
    val info = imageinfo.firstOrNull() ?: return false
    if (info.width <= 0 || info.height <= 0) return true  // unknown dims → allow
    return info.height.toFloat() / info.width.toFloat() >= 0.5f
}

internal fun WikimediaPage.toDomain(category: Category?): Wallpaper? {
    val info = imageinfo.firstOrNull() ?: return null
    if (info.url.isBlank()) return null
    // title is "File:Example.jpg" — strip the namespace prefix for display
    val displayTitle = title.removePrefix("File:")
    val artist = info.extmetadata.artist?.value
        ?.replace(Regex("<[^>]+>"), "")  // strip HTML tags from Artist field
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "Wikimedia Commons"
    return Wallpaper(
        id = pageid.toString(),
        sourceId = SourceId.WIKIMEDIA,
        thumbUrl = info.thumburl.ifBlank { info.url },
        fullUrl = info.url,
        width = info.width,
        height = info.height,
        author = artist,
        authorUrl = "https://commons.wikimedia.org",
        sourcePageUrl = info.descriptionurl.ifBlank { "https://commons.wikimedia.org/wiki/$title" },
        colorHint = null,
        category = category,
        tags = listOf(displayTitle).filter { it.isNotBlank() },
    )
}
