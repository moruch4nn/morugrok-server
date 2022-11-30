package dev.mr3n.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConnectionRequest(val port: Int? = null, val protocol: Protocol? = null, @SerialName("filter") val filter: Filter = Filter(Filter.Type.BLACKLIST, listOf()))