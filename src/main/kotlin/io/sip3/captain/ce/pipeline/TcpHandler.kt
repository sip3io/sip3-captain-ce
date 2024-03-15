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

package io.sip3.captain.ce.pipeline

import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.domain.*
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.util.IpUtil
import io.sip3.commons.vertx.annotations.Instance
import io.sip3.commons.vertx.collections.PeriodicallyExpiringHashMap
import io.vertx.core.AbstractVerticle
import mu.KotlinLogging

/**
 * Handles TCP packets
 */
@Instance(singleton = true)
class TcpHandler : AbstractVerticle() {

    private val logger = KotlinLogging.logger {}

    private var expirationDelay: Long = 100
    private var aggregationTimeout: Long = 200
    private var idleConnectionTimeout: Long = 300000

    private var sipEnabled = true
    private var smppEnabled = false
    private var websocketEnabled = false

    private lateinit var connections: PeriodicallyExpiringHashMap<String, TcpConnection>

    override fun start() {
        config().getJsonObject("tcp")?.let { config ->
            config.getLong("expiration_delay")?.let {
                expirationDelay = it
            }
            config.getLong("aggregation_timeout")?.let {
                aggregationTimeout = it
            }
            config.getLong("idle_connection_timeout")?.let {
                idleConnectionTimeout = it
            }
        }

        config().getJsonObject("sip")?.getBoolean("enabled")?.let {
            sipEnabled = it
        }
        config().getJsonObject("smpp")?.getBoolean("enabled")?.let {
            smppEnabled = it
        }
        config().getJsonObject("websocket")?.getBoolean("enabled")?.let {
            websocketEnabled = it
        }

        connections = PeriodicallyExpiringHashMap.Builder<String, TcpConnection>()
            .delay(expirationDelay)
            .period((aggregationTimeout / expirationDelay).toInt())
            .expireAt { _, connection -> connection.lastUpdated + idleConnectionTimeout }
            .onRemain { _, connection -> connection.processTcpSegments() }
            .build(vertx)

        vertx.eventBus().localConsumer<List<Packet>>(RoutesCE.tcp) { event ->
            val packets = event.body()
            packets.forEach { packet ->
                try {
                    onPacket(packet)
                } catch (e: Exception) {
                    logger.error("TcpHandler 'onPacket()' failed.", e)
                }
            }
        }
    }

    fun onPacket(packet: Packet) {
        val buffer = (packet.payload as Encodable).encode()

        val offset = buffer.readerIndex()
        // Source Port
        packet.srcPort = buffer.readUnsignedShort()
        // Destination Port
        packet.dstPort = buffer.readUnsignedShort()
        // Sequence number
        val sequenceNumber = buffer.readUnsignedInt()
        // Acknowledgment number
        buffer.skipBytes(4)
        // Data offset
        val headerLength = 4 * buffer.readUnsignedByte().toInt().shr(4)
        // Options
        buffer.readerIndex(offset + headerLength)

        // Skip TCP packets without payload
        if (buffer.readableBytes() <= 0) {
            return
        }

        // Calculate TCP connection identifier
        val srcAddr = IpUtil.convertToString(packet.srcAddr)
        val srcPort = packet.srcPort.toLong()
        val dstAddr = IpUtil.convertToString(packet.dstAddr)
        val dstPort = packet.dstPort.toLong()
        val connectionId = "$srcAddr:$srcPort:$dstAddr:$dstPort"

        // Find existing connection or add a new one, but only after it's type defined
        var connection = connections.get(connectionId)
        if (connection == null) {
            connection = when {
                sipEnabled && SipConnection.assert(buffer) -> SipConnection(vertx, config(), aggregationTimeout)
                smppEnabled && SmppConnection.assert(buffer) -> SmppConnection(vertx, config(), aggregationTimeout)
                websocketEnabled && WebSocketConnection.assert(buffer) -> WebSocketConnection(vertx, config(), aggregationTimeout)
                else -> return
            }
            connections.put(connectionId, connection)
        }

        // Re-assign packet payload to `ByteBufPayload`
        packet.payload = ByteBufPayload(buffer)

        // Handle TCP packet and update connection timestamp
        connection.lastUpdated = System.currentTimeMillis()
        connection.onTcpSegment(sequenceNumber, packet)
    }
}
