package dev.mr3n.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PortInfoResponse(@SerialName("port") val port: PortInfo) {
    @Serializable
    data class PortInfo(val start: Int, val end: Int, val using: List<Int>)
}
