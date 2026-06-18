package com.wallora.app.data.remote.api

import com.wallora.app.data.remote.dto.OpenverseSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenverseApi {

    /**
     * Search Creative Commons images.
     * Licensed under cc0 and by (free to share + adapt) to keep content legally safe.
     */
    @GET("v1/images/")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("license_type") licenseType: String,
        @Query("mature") mature: Boolean,
    ): OpenverseSearchResponse

    companion object {
        const val PAGE_SIZE = 20
        /** Permissive CC licenses: CC0 (public domain), CC BY, CC BY-SA. */
        const val LICENSE_TYPES = "cc0,by,by-sa"
    }
}
