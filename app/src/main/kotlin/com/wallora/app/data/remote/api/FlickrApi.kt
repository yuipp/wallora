package com.wallora.app.data.remote.api

import com.wallora.app.data.remote.dto.FlickrSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface FlickrApi {

    /**
     * Search Flickr photos filtered to Creative Commons licenses and safe content.
     *
     * License codes: 4=CC BY, 5=CC BY-SA, 6=CC BY-ND, 9=CC0, 10=Public Domain Mark.
     * safe_search=1 = safe content only.
     * content_type=1 = photos only (no screenshots / illustrations).
     * sort=interestingness-desc = Flickr's quality ranking.
     * extras=url_l,url_m,owner_name = include image URLs and photographer name.
     */
    @GET("services/rest/")
    suspend fun search(
        @Query("method") method: String,
        @Query("format") format: String,
        @Query("nojsoncallback") noJsonCallback: Int,
        @Query("api_key") apiKey: String,
        @Query("text") query: String,
        @Query("safe_search") safeSearch: Int,
        @Query("content_type") contentType: Int,
        @Query("license") license: String,
        @Query("sort") sort: String,
        @Query("extras") extras: String,
        @Query("per_page") perPage: Int,
        @Query("page") page: Int,
    ): FlickrSearchResponse

    companion object {
        const val PAGE_SIZE = 20
        const val METHOD_SEARCH = "flickr.photos.search"
        const val FORMAT = "json"
        const val NO_JSON_CALLBACK = 1
        const val SAFE_SEARCH = 1         // safe content only
        const val CONTENT_TYPE = 1        // photos only
        /** CC BY + CC BY-SA + CC BY-ND + CC0 + Public Domain Mark */
        const val LICENSES = "4,5,6,9,10"
        const val SORT = "interestingness-desc"
        const val EXTRAS = "url_l,url_m,owner_name"
    }
}
