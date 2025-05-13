/*
 * Copyright 2018-2025 SIP3.IO, Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sip3.captain.ce.sender

import io.micrometer.core.instrument.Metrics
import io.sip3.captain.ce.RoutesCE
import io.sip3.commons.vertx.annotations.Instance
import io.vertx.core.AbstractVerticle
import io.vertx.core.buffer.Buffer
import io.vertx.core.datagram.DatagramSocket
import io.vertx.core.datagram.DatagramSocketOptions
import io.vertx.core.http.WebSocket
import io.vertx.core.net.NetClientOptions
import io.vertx.core.net.NetSocket
import mu.KotlinLogging
import java.net.URI

/**
 * Sends encoded packets to `SIP3 Salto`.
 */
@Instance
open class Sender : AbstractVerticle() {

    private val logger = KotlinLogging.logger {}

    lateinit var uri: URI
    var reconnectionTimeout = 1000L
    var reusePort = true
    private var delimiter = Buffer.buffer("\r\n\r\n3PIS\r\n\r\n")

    var udp: DatagramSocket? = null
    var tcp: NetSocket? = null
    var ws: WebSocket? = null

    private val packetsSent = Metrics.counter("packets_sent")

    override fun start() {
        config().getJsonObject("sender").let { config ->
            uri = URI(config.getString("uri") ?: throw IllegalArgumentException("uri"))
            config.getLong("reconnection_timeout")?.let { reconnectionTimeout = it }
            config.getBoolean("reuse_port")?.let { reusePort = it }

            config.getString("delimiter")?.let { delimiter = Buffer.buffer(it) }
        }

        when (uri.scheme) {
            "udp" -> openUdpConnection()
            "tcp" -> openTcpConnection()
            "ws" -> openWsConnection()
            else -> throw NotImplementedError("Unknown protocol: $uri")
        }

        vertx.eventBus().localConsumer<List<Buffer>>(RoutesCE.sender) { event ->
            try {
                val buffers = event.body()
                send(buffers)
            } catch (e: Exception) {
                logger.error("Sender 'send()' failed.", e)
            }
        }
    }

    open fun openUdpConnection() {
        val options = DatagramSocketOptions().apply {
            isIpV6 = uri.host.matches(Regex("\\[.*]"))
            isReusePort = reusePort
        }
        udp = vertx.createDatagramSocket(options)
        logger.info("UDP connection opened: $uri")
    }

    open fun openTcpConnection() {
        val options = tcpConnectionOptions()
        vertx.createNetClient(options).connect(uri.port, uri.host)
            .onFailure { t ->
                logger.error("Sender 'openTcpConnection()' failed.", t)
                tcp = null
                vertx.setTimer(reconnectionTimeout) { openTcpConnection() }
            }
            .onSuccess { socket ->
                logger.info("TCP connection opened: $uri")
                tcp = socket.closeHandler {
                    logger.info("TCP connection closed: $uri")
                    tcp = null
                    vertx.setTimer(reconnectionTimeout) { openTcpConnection() }
                }
            }
    }

    open fun tcpConnectionOptions(): NetClientOptions {
        return NetClientOptions().apply {
            isReusePort = reusePort
        }
    }

    open fun openWsConnection() {
        throw NotImplementedError("WebSocket transport is available in EE version")
    }


    fun send(buffers: List<Buffer>) {
        packetsSent.increment(buffers.size.toDouble())

        udp?.let { socket ->
            buffers.forEach { socket.send(it, uri.port, uri.host) }
        }
        tcp?.let { socket ->
            buffers.forEach { socket.write(it.appendBuffer(delimiter)) }
        }
        ws?.let { socket ->
            buffers.forEach { socket.write(it)}
        }
    }
}
