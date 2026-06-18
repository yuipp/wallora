package com.wallora.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FlickrSearchResponse(
    val photos: FlickrPhotos = FlickrPhotos(),
    val stat: String = "",
)

@Serializable
data class FlickrPhotos(
    val page: Int = 1,
    val pages: Int = 0,
    val perpage: Int = 20,
    val total: Long = 0L,
    val photo: List<FlickrPhoto> = emptyList(),
)

@Serializable
data class FlickrPhoto(
    val id: String = "",
    val owner: String = "",
    val secret: String = "",
    val server: String = "",
    val farm: Int = 0,
    val title: String = "",
    val ownername: String = "",
    // Large (1024px longest side) — present when requested via extras=url_l
    @SerialName("url_l") val urlL: String = "",
    @SerialName("height_l") val heightL: String = "0",
    @SerialName("width_l") val widthL: String = "0",
    // Medium (500px) — fallback
    @SerialName("url_m") val urlM: String = "",
    @SerialName("height_m") val heightM: String = "0",
    @SerialName("width_m") val widthM: String = "0",
)
