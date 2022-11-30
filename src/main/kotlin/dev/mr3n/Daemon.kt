package dev.mr3n

import dev.mr3n.model.ConnectionInfo
import dev.mr3n.model.Filter
import dev.mr3n.model.Protocol
import dev.mr3n.tcp.TCPPublicServer
import io.ktor.network.selector.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.security.SecureRandom
import java.util.*

class Daemon(val user: String, var port: Int, val protocol: Protocol, filter: Filter, val permission: List<String> = listOf()): Closeable, Thread() {
    val randomBytes = ByteArray(128).also { SecureRandom().nextBytes(it) }
    val token = Base64.getUrlEncoder().encodeToString(randomBytes)

    val selectorManager = SelectorManager(Dispatchers.IO)

    val tunnelingServer = TunnelingServer(selectorManager)

    val webSocketSession = mutableListOf<WebSocketSession>()
    var closed = false
        private set

    val publicServer: PublicServer = when(protocol) {
        Protocol.TCP -> {
            TCPPublicServer(port, webSocketSession, tunnelingServer,selectorManager,filter)
        }
        else -> throw Exception()
    }


    var filter: Filter
        set(value) { publicServer.filter = value }
        get() = publicServer.filter


    override fun run() {
        while(!closed) { runBlocking { publicServer.run() } }
    }

    override fun close() {
        closed = true
        Data.CONNECTIONS[user]?.remove(token)

        publicServer.close()
        tunnelingServer.close()
        selectorManager.close()

        webSocketSession.forEach { runBlocking { try{it.close()}catch(_:Exception){} } }
        webSocketSession.clear()

        try { this.interrupt() } catch(_: Exception) {}
    }

    fun addWebsocketConnection(wsSes: WebSocketSession) {
        if(webSocketSession.isEmpty()) { this.start() }
        webSocketSession.add(wsSes)
    }

    suspend fun removeWebSocketConnection(wsSes: WebSocketSession) {
        if(wsSes.isActive) { wsSes.close(CloseReason(CloseReason.Codes.GOING_AWAY,"Good bye...")) }
        webSocketSession.remove(wsSes)
        if(webSocketSession.isEmpty()) { close() }
    }

    fun toConnectionInfo() = ConnectionInfo(user,port,protocol,filter,token)

    init { Data.CONNECTIONS[user] = (Data.CONNECTIONS[user]?: mutableMapOf()).also { it[token] = this } }
}

interface PublicServer: Closeable {
    suspend fun run()

    var filter: Filter
}