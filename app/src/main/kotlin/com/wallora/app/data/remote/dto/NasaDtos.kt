package com.wallora.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NasaSearchResponse(
    val collection: NasaCollection = NasaCollection(),
)

@Serializable
data class NasaCollection(
    val href: String = "",
    val items: List<NasaItem> = emptyList(),
    val metadata: NasaMetadata = NasaMetadata(),
    val links: List<NasaCollectionLink> = emptyList(),
)

@Serializable
data class NasaItem(
    val data: List<NasaAssetData> = emptyList(),
    val links: List<NasaItemLink> = emptyList(),
    val href: String = "",
)

@Serializable
data class NasaAssetData(
    @SerialName("nasa_id") val nasaId: String = "",
    val title: String = "",
    val description: String = "",
    val photographer: String = "",
    val center: String = "",
    @SerialName("media_type") val mediaType: String = "",
    @SerialName("date_created") val dateCreated: String = "",
)

@Serializable
data class NasaItemLink(
    val href: String = "",
    val rel: String = "",
    val render: String = "",
)

@Serializable
data class NasaMetadata(
    @SerialName("total_hits") val totalHits: Long = 0L,
)

@Serializable
data class NasaCollectionLink(
    val rel: String = "",
    val prompt: String = "",
    val href: String = "",
)
