package com.wallora.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WikimediaSearchResponse(
    val query: WikimediaQuery? = null,
    @SerialName("continue") val continueData: WikimediaContinue? = null,
)

@Serializable
data class WikimediaQuery(
    val pages: Map<String, WikimediaPage> = emptyMap(),
)

@Serializable
data class WikimediaContinue(
    val gsroffset: Int = 0,
)

@Serializable
data class WikimediaPage(
    val pageid: Long = 0L,
    val title: String = "",
    val imageinfo: List<WikimediaImageInfo> = emptyList(),
)

@Serializable
data class WikimediaImageInfo(
    val url: String = "",
    val thumburl: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val descriptionurl: String = "",
    val extmetadata: WikimediaExtMetadata = WikimediaExtMetadata(),
)

@Serializable
data class WikimediaExtMetadata(
    @SerialName("Artist") val artist: WikimediaMetaValue? = null,
    @SerialName("LicenseShortName") val license: WikimediaMetaValue? = null,
)

@Serializable
data class WikimediaMetaValue(
    val value: String = "",
)
