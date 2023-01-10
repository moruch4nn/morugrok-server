package dev.mr3n

import dev.mr3n.model.ConnectionRequest
import java.util.*

// >>> 使用可能なポート範囲 >>>
const val PORT_START = 10000
const val PORT_END = 60000
// <<< 使用可能なポート範囲 <<<

// 使用されているポート一覧
val USING_PORT: List<Int>
    get() = CONNECTIONS.values.map { it.values }.flatten().map { listOf(it.port, it.tunnelingServer.port) }.flatten()

val WAIT_CONNECTIONS = mutableMapOf<String, MutableMap<String, ConnectionRequest>>()

val CONNECTIONS = mutableMapOf<String, MutableMap<String, TCPConnection>>()

const val DEFAULT_BUFFER_SIZE = 30000

val TIMER = Timer()