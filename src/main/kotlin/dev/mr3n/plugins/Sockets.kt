package dev.mr3n.plugins

import dev.mr3n.CONNECTIONS
import dev.mr3n.TCPConnection
import dev.mr3n.TIMER
import dev.mr3n.WAIT_CONNECTIONS
import dev.mr3n.model.auth.WebSocketAuth
import dev.mr3n.model.ws.EmptyPacket
import dev.mr3n.model.ws.PacketType
import dev.mr3n.model.ws.WebSocketPacket
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import kotlin.concurrent.scheduleAtFixedRate

fun Application.configureSockets() {
    install(WebSockets) {
        timeout = Duration.ofSeconds(15)
        pingPeriod = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }

    routing {
        webSocket {
            val webSocketAuth = receiveDeserialized<WebSocketAuth>()
            val waitConnData =
                WAIT_CONNECTIONS[webSocketAuth.user]?.get(webSocketAuth.token) ?: return@webSocket close(
                    CloseReason(CloseReason.Codes.GOING_AWAY, "認証情報が無効です")
                )
            WAIT_CONNECTIONS[webSocketAuth.user]?.remove(webSocketAuth.token)
            val tcpConn = TCPConnection(
                waitConnData.name,
                webSocketAuth.user,
                waitConnData.port!!,
                waitConnData.protocol!!,
                waitConnData.filter,
                waitConnData.token,
                this
            )
            call.application.log.info("新しくサーバー(${call.request.host()},${tcpConn.user})との接続を確立しました。現在はクライアントからの新規接続を待機しています。")
            while(true) {
                try { incoming.receive() } catch(e: Exception) { break }
            }
            try { close() } catch(_: Exception) {}
            tcpConn.close()
            call.application.log.info("サーバー(${call.request.host()},${tcpConn.user})との接続を切断しました。")
        }
    }
}