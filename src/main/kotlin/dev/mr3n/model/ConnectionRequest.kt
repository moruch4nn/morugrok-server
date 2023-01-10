package dev.mr3n.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.security.SecureRandom
import java.util.*

@Serializable
data class ConnectionRequest(val name: String?, val user: String? = null,val port: Int? = null, val protocol: Protocol? = null, @SerialName("filter") val filter: Filter = Filter(Filter.Type.BLACKLIST, listOf())) {
    @Transient
    val randomBytes = ByteArray(128).also { SecureRandom().nextBytes(it) }
    val token = Base64.getUrlEncoder().encodeToString(randomBytes)
}