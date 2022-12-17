package dev.mr3n

import dev.mr3n.model.ConnectionInfo
import dev.mr3n.model.Filter
import dev.mr3n.model.Protocol
import dev.mr3n.model.ws.CreateTunnelRequest
import dev.mr3n.model.ws.PacketType
import dev.mr3n.model.ws.WebSocketPacket
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.engine.internal.*
import io.ktor.utils.io.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.SocketException

class TCPConnection(
    val user: String,
    var port: Int,
    val protocol: Protocol,
    filter: Filter,
    private val token: String,
    private val webSocketSession: WebSocketSession
) : Closeable, Thread() {
    private val selectorManager = SelectorManager(Dispatchers.IO)

    private val serverSocket = aSocket(selectorManager).tcp().bind("0.0.0.0", port)
    private val connections = mutableMapOf<String, MutableList<ConnectionSocket>>()

    val tunnelingServer = TunnelingServer(selectorManager)

    var filter: Filter = filter
        set(value) {
            connections.forEach { (address, sockets) ->
                val block = when (value.type) {
                    Filter.Type.BLACKLIST -> value.list.contains(address)
                    Filter.Type.WHITELIST -> !value.list.contains(address)
                }
                if (block) {
                    sockets.forEach { it.close() }
                }
            }
            field = value
        }


    override fun run() {
        runBlocking {
            try {
                while (true) {
                    val socket = serverSocket.accept()
                    val address = (socket.remoteAddress.toJavaAddress() as InetSocketAddress).hostString
                    val block = when (filter.type) {
                        Filter.Type.BLACKLIST -> filter.list.contains(address)
                        Filter.Type.WHITELIST -> !filter.list.contains(address)
                    }
                    if (block) {
                        withContext(Dispatchers.IO) { socket.close() }
                    } else {
                        val json = DefaultJson.encodeToString(
                            WebSocketPacket(
                                PacketType.CREATE_TUNNEL,
                                CreateTunnelRequest(
                                    tunnelingServer.port,
                                    Protocol.TCP,
                                    address,
                                    System.currentTimeMillis()
                                )
                            )
                        )
                        webSocketSession.send(json)
                        val tunnelingSocket = tunnelingServer.get()
                        val clientConnection = socket.connection()
                        val tunnelingConnection = tunnelingSocket.connection()
                        val clientConnectionSocket = ConnectionSocket(clientConnection, tunnelingConnection)
                        val tunnelingConnectionSocket = ConnectionSocket(tunnelingConnection, clientConnection)
                        connections[address] = (connections[address] ?: mutableListOf()).apply {
                            addAll(
                                listOf(
                                    clientConnectionSocket,
                                    tunnelingConnectionSocket
                                )
                            )
                        }
                    }
                }
            } catch (_: ClosedChannelException) {
            }
        }
    }

    override fun close() {
        Data.CONNECTIONS[user]?.remove(token)

        serverSocket.close()
        tunnelingServer.close()
        selectorManager.close()

        runBlocking { webSocketSession.close() }

        try {
            this.interrupt()
        } catch (_: Exception) {
        }
    }

    fun toConnectionInfo() = ConnectionInfo(user, port, protocol, filter, token)

    init {
        Data.CONNECTIONS[user] = (Data.CONNECTIONS[user] ?: mutableMapOf()).also { it[token] = this }
    }

    class ConnectionSocket(private val receive: Connection, private val send: Connection) : Thread() {
        fun close() {
            try {
                receive.socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                send.socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun run() {
            try {
                runBlocking {
                    val inputStream = receive.input
                    val outputStream = send.output
                    val buffer = ByteArray(Data.DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val bytesRead: Int = inputStream.readAvailable(buffer)
                        if (bytesRead == -1) throw SocketException() // end
                        outputStream.writeFully(buffer, 0, bytesRead)
                        outputStream.flush()
                    }
                }
            } catch (e: Exception) {
                this.close()
            }
        }

        init {
            this.start()
        }
    }

    init {
        this.start()
    }
}