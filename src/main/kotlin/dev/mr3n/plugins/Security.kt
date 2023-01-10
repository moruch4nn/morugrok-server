package dev.mr3n.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.mr3n.*
import dev.mr3n.model.ConnectionRequest
import dev.mr3n.model.Protocol.*
import dev.mr3n.model.auth.JWTAuth
import dev.mr3n.model.response.PortInfoResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.text.SimpleDateFormat
import java.util.*


fun Application.configureSecurity() {
    val algorithm = Algorithm.HMAC512(System.getenv("SECRET"))
    authentication {
        jwt("auth-jwt") {
            verifier(JWT.require(algorithm).build())
            validate { credential ->
                val name = credential.getClaim("name", String::class) ?: return@validate null
                val perm = credential.getListClaim("perm", String::class)
                val iss = credential.getClaim("iss", String::class) ?: return@validate null
                val aud = credential.getClaim("aud", String::class) ?: return@validate null
                val exp = credential.getClaim("exp", Long::class) ?: return@validate null
                if (System.currentTimeMillis() >= exp) {
                    return@validate null
                }
                return@validate JWTAuth(name, perm, iss, aud, exp)
            }
            challenge { defaultScheme, realm ->
                call.respond(HttpStatusCode.Unauthorized, "認証情報が無効です。")
            }
        }
    }

    routing {
        authenticate("auth-jwt") {
            route("check") {
                route("auth") {
                    get {
                        call.respond(call.principal<JWTAuth>()!!)
                    }
                }
            }
            route("con") {
                post {
                    val principal = call.principal<JWTAuth>()!!
                    val sdf = SimpleDateFormat("yyyy'/'MM'/'dd k'時'mm'分'ss'秒'")
                    call.application.log.info(
                        "新しいユーザーを認証しました。(名前:${principal.name}, 権限:${principal.perm}, 発行者:${principal.iss}, 対象者:${principal.aud}, 期限:${
                            sdf.format(
                                Date(principal.exp)
                            )
                        }, アドレス:${call.request.host()})"
                    )
                    val body = call.receive<ConnectionRequest>()
                    when (body.protocol) {
                        TCP -> check(principal.hasPerm("con.new.tcp")) {
                            return@post call.respond(
                                HttpStatusCode.Forbidden,
                                "新しいTCPコネクションを作成する権限がありません。"
                            )
                        }

                        UDP -> check(principal.hasPerm("con.new.udp")) {
                            return@post call.respond(
                                HttpStatusCode.Forbidden,
                                "新しいUDPコネクションを作成する権限がありません。"
                            )
                        }

                        null -> throw BadRequestException("プロトコルを指定してください。")
                    }

                    val subPort =
                        (PORT_START..PORT_END).toMutableList().apply { removeAll(USING_PORT) }.random()
                    if (USING_PORT.contains(body.port)) {
                        call.respond(HttpStatusCode.Conflict, "ポートが重複しているためコネクションを作成できませんでした。")
                    } else {
                        val waitConn = body.copy(
                            name = body.name,
                            user = principal.name,
                            port = if (principal.hasPerm("port.select")) { body.port ?: throw BadRequestException("") } else { subPort }
                        )
                        WAIT_CONNECTIONS[principal.name] = (WAIT_CONNECTIONS[principal.name] ?: mutableMapOf()).apply { put(waitConn.token, waitConn) }
                        call.respond(HttpStatusCode.OK, waitConn)
                    }
                }
                get {
                    val principal = call.principal<JWTAuth>()!!
                    val connections = CONNECTIONS[principal.name]?.values ?: mutableListOf()
                    call.respond(connections.map { it.toConnectionInfo() })
                }
                delete {
                    val principal = call.principal<JWTAuth>()!!
                    CONNECTIONS[principal.name]?.values?.forEach { it.close() }
                    call.respond("すべてのコネクションを停止しました。")
                }
                route("{token}") {
                    delete {
                        val principal = call.principal<JWTAuth>()!!
                        val token = call.parameters["token"]
                        val connection =
                            CONNECTIONS[principal.name]?.get(token) ?: throw NotFoundException("コネクションが見つかりません")
                        connection.close()
                    }
                    get {
                        val principal = call.principal<JWTAuth>()!!
                        val token = call.parameters["token"]
                        val connection =
                            CONNECTIONS[principal.name]?.get(token) ?: throw NotFoundException("コネクションが見つかりません")
                        call.respond(connection.toConnectionInfo())
                    }
                    patch {
                        val principal = call.principal<JWTAuth>()!!
                        val token = call.parameters["token"]
                        val connection =
                            CONNECTIONS[principal.name]?.get(token) ?: throw NotFoundException("コネクションが見つかりません")
                        val body = call.receive<ConnectionRequest>()
                        if (body.protocol != null && body.protocol != connection.protocol) {
                            return@patch call.respond(HttpStatusCode.BadRequest, "プロトコル情報は変更できません。")
                        }
                        if (body.port != null && body.port != connection.port) {
                            connection.port = body.port
                        }
                        if (body.filter != connection.filter) {
                            connection.filter = body.filter
                        }
                        call.respond(connection.toConnectionInfo())
                    }
                    route("tunneling_server") {
                        get {
                            val principal = call.principal<JWTAuth>()!!
                            val token = call.parameters["token"]
                            val connection = CONNECTIONS[principal.name]?.get(token)
                                ?: throw NotFoundException("コネクションが見つかりません")
                            call.respond(mapOf("tunneling_server" to connection.tunnelingServer))
                        }
                        route("port") {
                            get {
                                val principal = call.principal<JWTAuth>()!!
                                val token = call.parameters["token"]
                                val connection = CONNECTIONS[principal.name]?.get(token)
                                    ?: throw NotFoundException("コネクションが見つかりません")
                                call.respond(mapOf("port" to connection.tunnelingServer.port))
                            }
                        }
                    }
                    route("name") {
                        get {
                            val principal = call.principal<JWTAuth>()!!
                            val token = call.parameters["token"]
                            val connection = CONNECTIONS[principal.name]?.get(token)
                                ?: throw NotFoundException("コネクションが見つかりません")
                            call.respond(mapOf("name" to connection.name))
                        }
                        patch {
                            val principal = call.principal<JWTAuth>()!!
                            val token = call.parameters["token"]
                            val connection = CONNECTIONS[principal.name]?.get(token)?: throw NotFoundException("コネクションが見つかりません")
                            val body = call.receive<ConnectionRequest>()
                            if (body.name != connection.name) {
                                connection.name = body.name
                            }
                            call.respond(mapOf("name" to connection.name))
                        }
                    }
                    route("port") {
                        get {
                            val principal = call.principal<JWTAuth>()!!
                            val token = call.parameters["token"]
                            val connection = CONNECTIONS[principal.name]?.get(token)
                                ?: throw NotFoundException("コネクションが見つかりません")
                            call.respond(mapOf("port" to connection.port))
                        }
                    }
                    route("filter") {
                        get {
                            val principal = call.principal<JWTAuth>()!!
                            val token = call.parameters["token"]
                            val connection = CONNECTIONS[principal.name]?.get(token)
                                ?: throw NotFoundException("コネクションが見つかりません")
                            call.respond(mapOf("filter" to connection.filter))
                        }
                        patch {
                            val principal = call.principal<JWTAuth>()!!
                            val token = call.parameters["token"]
                            val connection = CONNECTIONS[principal.name]?.get(token)
                                ?: throw NotFoundException("コネクションが見つかりません")
                            val body = call.receive<ConnectionRequest>()
                            if (body.filter != connection.filter) {
                                connection.filter = body.filter
                            }
                            call.respond(mapOf("filter" to connection.filter))
                        }
                    }
                    route("protocol") {
                        get {
                            val principal = call.principal<JWTAuth>()!!
                            val token = call.parameters["token"]
                            val connection = CONNECTIONS[principal.name]?.get(token)
                                ?: throw NotFoundException("コネクションが見つかりません")
                            call.respond(mapOf("protocol" to connection.protocol))
                        }
                    }
                    route("user") {
                        get {
                            val principal = call.principal<JWTAuth>()!!
                            val token = call.parameters["token"]
                            val connection = CONNECTIONS[principal.name]?.get(token)
                                ?: throw NotFoundException("コネクションが見つかりません")
                            call.respond(mapOf("user" to connection.user))
                        }
                    }
                }
            }
            route("usage") {
                get {
                    call.respond(
                        PortInfoResponse(
                            PortInfoResponse.PortInfo(
                                PORT_START,
                                PORT_END,
                                USING_PORT
                            )
                        )
                    )
                }
            }
        }
    }
}
