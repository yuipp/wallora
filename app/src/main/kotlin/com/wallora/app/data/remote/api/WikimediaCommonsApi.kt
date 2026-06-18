package com.wallora.app.data.remote.api

import com.wallora.app.data.remote.dto.WikimediaSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface WikimediaCommonsApi {

    /**
     * Search Wikimedia Commons File namespace (ns=6) for images.
     *
     * Uses MediaWiki's generator=search to return pages with their imageinfo.
     * [gsroffset] is the pagination offset; null for the first page.
     * [iiUrlWidth] requests a thumbnail URL at the given pixel width.
     *
     * Note: Wikimedia's User-Agent policy requires a descriptive UA — added via an interceptor
     * in NetworkModule so it applies to every request on this Retrofit instance.
     */
    @GET("w/api.php")
    suspend fun search(
        @Query("action") action: String,
        @Query("format") format: String,
        @Query("formatversion") formatVersion: Int,
        @Query("generator") generator: String,
        @Query("gsrnamespace") gsrNamespace: Int,
        @Query("gsrsearch") query: String,
        @Query("gsrlimit") limit: Int,
        @Query("gsroffset") offset: Int?,
        @Query("prop") prop: String,
        @Query("iiprop") iiProp: String,
        @Query("iiurlwidth") iiUrlWidth: Int,
    ): WikimediaSearchResponse

    companion object {
        const val PAGE_SIZE = 20
        const val ACTION = "query"
        const val FORMAT = "json"
        const val FORMAT_VERSION = 2
        const val GENERATOR = "search"
        const val NS_FILE = 6            // File: namespace
        const val PROP = "imageinfo"
        const val II_PROP = "url|size|extmetadata"
        const val THUMB_WIDTH = 400
    }
}
