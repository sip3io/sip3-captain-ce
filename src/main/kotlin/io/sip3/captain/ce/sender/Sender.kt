/*
 * Copyright 2018-2024 SIP3.IO, Corp.
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
import io.vertx.core.net.NetClientOptions
import io.vertx.core.net.NetSocket
import mu.KotlinLogging
import java.net.URI

/**
 * Sends encoded packets to `SIP3 Salto`.
 */
@Instance
class Sender : AbstractVerticle() {

    private val logger = KotlinLogging.logger {}

    lateinit var uri: URI
    var reconnectionTimeout: Long? = null
    var isSSl = false
    var keyStore: String? = null
    var keyStorePassword: String? = null

    var udp: DatagramSocket? = null
    var tcp: NetSocket? = null

    private val packetsSent = Metrics.counter("packets_sent")

    override fun start() {
        config().getJsonObject("sender").let { config ->
            uri = URI(config.getString("uri") ?: throw IllegalArgumentException("uri"))
            reconnectionTimeout = config.getLong("reconnection_timeout")
            config.getJsonObject("ssl")?.let { sslConfig ->
                isSSl = true
                keyStore = sslConfig.getString("key_store")
                keyStorePassword = sslConfig.getString("key_store_password")
            }
        }

        when (uri.scheme) {
            "udp" -> openUdpConnection()
            "tcp" -> openTcpConnection()
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

    fun openUdpConnection() {
        val options = DatagramSocketOptions().apply {
            isIpV6 = uri.host.matches(Regex("\\[.*]"))
        }
        udp = vertx.createDatagramSocket(options)
        logger.info("UDP connection opened: $uri")
    }

    fun openTcpConnection() {
        val options = NetClientOptions()
        if (isSSl) {
            options.apply {
                isSsl = true
                isTrustAll = true
            }
        }
        vertx.createNetClient(options).connect(uri.port, uri.host) { asr ->
            if (asr.succeeded()) {
                logger.info("TCP connection opened: $uri")
                tcp = asr.result().closeHandler {
                    logger.info("TCP connection closed: $uri")
                    reconnectionTimeout?.let { timeout ->
                        vertx.setTimer(timeout) { openTcpConnection() }
                    }
                }
            } else {
                logger.error("Sender 'openTcpConnection()' failed.", asr.cause())
                reconnectionTimeout?.let { timeout ->
                    vertx.setTimer(timeout) { openTcpConnection() }
                }
            }
        }
    }

    fun send(buffers: List<Buffer>) {
        packetsSent.increment(buffers.size.toDouble())

        udp?.let { socket ->
            buffers.forEach { socket.send(it, uri.port, uri.host) }
        }
        tcp?.let { socket ->
            buffers.forEach { socket.write(it) }
        }
    }
}
