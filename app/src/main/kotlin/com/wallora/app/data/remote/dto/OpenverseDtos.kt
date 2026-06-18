package com.wallora.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenverseSearchResponse(
    @SerialName("result_count") val resultCount: Long = 0L,
    @SerialName("page_count") val pageCount: Int = 0,
    @SerialName("page_size") val pageSize: Int = 20,
    val page: Int = 1,
    val results: List<OpenverseImage> = emptyList(),
)

@Serializable
data class OpenverseImage(
    val id: String = "",
    val title: String = "",
    val url: String = "",
    val thumbnail: String = "",
    val creator: String = "",
    @SerialName("creator_url") val creatorUrl: String = "",
    @SerialName("foreign_landing_url") val foreignLandingUrl: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val license: String = "",
    val attribution: String = "",
)
