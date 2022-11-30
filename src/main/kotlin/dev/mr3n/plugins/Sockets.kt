package dev.mr3n.plugins

import dev.mr3n.TCPConnection
import dev.mr3n.Data
import dev.mr3n.model.auth.WebSocketAuth
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import java.time.Duration

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }

    routing {
        webSocket {
            val webSocketAuth = receiveDeserialized<WebSocketAuth>()
            val waitConnData = Data.WAIT_CONNECTIONS[webSocketAuth.user]?.get(webSocketAuth.token)?:return@webSocket close(
                CloseReason(CloseReason.Codes.GOING_AWAY,"認証情報が無効です"))
            Data.WAIT_CONNECTIONS[webSocketAuth.user]?.remove(webSocketAuth.token)
            val tcpConn = TCPConnection(webSocketAuth.user, waitConnData.port!!, waitConnData.protocol!!,waitConnData.filter, waitConnData.token, this)
            call.application.log.info("新しくサーバー(${call.request.host()},${tcpConn.user})との接続を確立しました。現在はクライアントからの新規接続を待機しています。")
            for(frame in incoming) { }
            tcpConn.close()
            call.application.log.info("サーバー(${call.request.host()},${tcpConn.user})との接続を切断しました。")
        }
    }
}