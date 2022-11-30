package dev.mr3n.tcp

import dev.mr3n.Data
import dev.mr3n.PublicServer
import dev.mr3n.TunnelingServer
import dev.mr3n.model.Filter
import dev.mr3n.model.Filter.Type.BLACKLIST
import dev.mr3n.model.Filter.Type.WHITELIST
import dev.mr3n.model.Protocol
import dev.mr3n.model.ws.CreateTunnelRequest
import dev.mr3n.model.ws.PacketType
import dev.mr3n.model.ws.WebSocketPacket
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.engine.internal.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import java.net.InetSocketAddress
import java.net.SocketException

class TCPPublicServer(val port: Int, val webSocketSession: List<WebSocketSession>, val tunnelingServer: TunnelingServer, @Transient val selectorManager: SelectorManager, filter: Filter) :
    PublicServer {
    val serverSocket = aSocket(selectorManager).tcp().bind("0.0.0.0", port)
    val connections = mutableMapOf<String, MutableList<ConnectionSocket>>()

    override var filter: Filter = filter
        set(value) {
            connections.forEach { (address, sockets) ->
                val block = when(value.type) {
                    BLACKLIST -> value.list.contains(address)
                    WHITELIST -> !value.list.contains(address)
                }
                if(block) { sockets.forEach { it.close() } }
            }
            field = value
        }

    override suspend fun run() {
        try {
            if (serverSocket.isClosed) { return }
            val socket = serverSocket.accept()
            val address = (socket.remoteAddress.toJavaAddress() as InetSocketAddress).hostString
            val block = when(filter.type) {
                BLACKLIST -> filter.list.contains(address)
                WHITELIST -> !filter.list.contains(address)
            }
            if(block) {
                withContext(Dispatchers.IO) { socket.close() }
            } else {
                val json = DefaultJson.encodeToString(WebSocketPacket(PacketType.CREATE_TUNNEL, CreateTunnelRequest(tunnelingServer.port, Protocol.TCP, address, System.currentTimeMillis())))
                webSocketSession.forEach { it.send(json) }
                val tunnelingSocket = tunnelingServer.get()
                val clientConnection = socket.connection()
                val tunnelingConnection = tunnelingSocket.connection()
                val clientConnectionSocket = ConnectionSocket(clientConnection, tunnelingConnection)
                val tunnelingConnectionSocket = ConnectionSocket(tunnelingConnection, clientConnection)
                connections[address] = (connections[address]?:mutableListOf()).apply { addAll(listOf(clientConnectionSocket, tunnelingConnectionSocket)) }
            }
        } catch (_: ClosedChannelException) { }
    }

    override fun close() {
        if (!serverSocket.isClosed) {
            serverSocket.close()
        }
    }


    class ConnectionSocket(val receive: Connection, val send: Connection) : Thread() {
        var closed = false
            private set

        fun close() {
            try { receive.socket.close() } catch (e: Exception) { e.printStackTrace() }
            try { send.socket.close() } catch (e: Exception) { e.printStackTrace() }
            closed = true
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
}