package dev.mr3n.model.auth

import io.ktor.server.auth.*
import kotlinx.serialization.Serializable

@Serializable
data class JWTAuth(val name: String, val perm: List<String>,@Transient val iss: String,@Transient val aud: String, val exp: Long): Principal {
    fun hasPerm(perm: String) = this.perm.contains(perm)
}