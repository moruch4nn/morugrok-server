package dev.mr3n.model.auth

import kotlinx.serialization.Serializable

@Serializable
data class WebSocketAuth(val user: String,val token: String)