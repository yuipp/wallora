package com.wallora.app.data.remote.api

import com.wallora.app.data.remote.dto.NasaSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface NasaApi {

    /** Search the NASA Image and Video Library. No API key required. */
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("media_type") mediaType: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): NasaSearchResponse

    companion object {
        const val PAGE_SIZE = 20
        const val MEDIA_TYPE = "image"
    }
}
