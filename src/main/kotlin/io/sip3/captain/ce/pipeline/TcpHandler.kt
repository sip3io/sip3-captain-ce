/*
 * Copyright 2018-2019 SIP3.IO, Inc.
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

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.sip3.captain.ce.Routes
import io.sip3.captain.ce.domain.Packet
import io.sip3.captain.ce.domain.TcpSegment
import io.sip3.captain.ce.util.SipUtil
import io.sip3.captain.ce.util.SmppUtil
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.util.IpUtil
import io.sip3.commons.util.remainingCapacity
import io.vertx.core.AbstractVerticle
import mu.KotlinLogging
import java.util.*

/**
 * Handles TCP packets
 */
class TcpHandler : AbstractVerticle() {

    private val logger = KotlinLogging.logger {}

    private var expirationDelay: Long = 100
    private var aggregationTimeout: Long = 100
    private var idleConnectionTimeout: Long = 300000

    private val connections = mutableMapOf<Long, TcpConnection>()

    override fun start() {
        config().getJsonObject("tcp")?.let { config ->
            config.getLong("expiration-delay")?.let { expirationDelay = it }
            config.getLong("aggregation-timeout")?.let { aggregationTimeout = it }
            config.getLong("idle-connection-timeout")?.let { idleConnectionTimeout = it }
        }

        vertx.setPeriodic(expirationDelay) {
            try {
                processTcpConnections()
            } catch (e: Exception) {
                logger.error("TcpHandler 'processTcpConnections()' failed.", e)
            }
        }

        vertx.eventBus().localConsumer<List<Packet>>(Routes.tcp) { event ->
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
        if (buffer.remainingCapacity() == 0) {
            return
        }

        // Calculate TCP connection identifier
        val srcAddr = IpUtil.convertToInt(packet.srcAddr).toLong()
        val srcPort = packet.srcPort.toLong()
        val connectionId = (srcAddr shl 32) or srcPort

        // Find existing connection or add a new one, but only after it's type defined
        var connection = connections[connectionId]
        if (connection == null) {
            connection = when {
                SipUtil.startsWithSipWord(buffer) ->
                    TcpConnection(SipHandler(vertx, false)) { buffer: ByteBuf -> SipUtil.startsWithSipWord(buffer) }
                SmppUtil.isPdu(buffer) ->
                    TcpConnection(SmppHandler(vertx, false)) { buffer: ByteBuf -> SmppUtil.isPdu(buffer) }
                else -> return
            }
            connections[connectionId] = connection
        }

        // Re-assign packet payload to `ByteBufPayload`
        packet.payload = ByteBufPayload(buffer)

        // Handle TCP packet and update connection timestamp
        connection.lastUpdated = System.currentTimeMillis()
        connection.onTcpSegment(sequenceNumber, packet)
    }

    private fun processTcpConnections() {
        val now = System.currentTimeMillis()

        connections.toMap().forEach { (connectionId, connection) ->
            when {
                connection.lastUpdated + idleConnectionTimeout < now -> connections.remove(connectionId)
                else -> connection.processTcpSegments()
            }
        }
    }

    // Important! To make `TcpConnection` code easier we decided to ignore a very rare scenario:
    // 1. Application packet is split into separate segments. Last segment got over MAX sequence number.
    inner class TcpConnection(val handler: Handler, val assert: (buffer: ByteBuf) -> Boolean) {

        var lastUpdated = System.currentTimeMillis()

        private val segments = TreeMap<Long, TcpSegment>()

        fun onTcpSegment(sequenceNumber: Long, packet: Packet) {
            val buffer = (packet.payload as Encodable).encode()

            // Add to segments map
            val segment = TcpSegment().apply {
                this.sequenceNumber = sequenceNumber
                this.packet = packet
                this.payloadLength = buffer.remainingCapacity()
            }
            segments[sequenceNumber] = segment

            // Check previous segment if current segment starts from SIP word
            if (assert.invoke(buffer)) {
                processPreviousTcpSegment(segment)
            }
        }

        fun processTcpSegments() {
            val sequenceNumbers = mutableListOf<Long>()
            walkThroughTcpSegments(sequenceNumbers)

            while (sequenceNumbers.isNotEmpty()) {
                val lastSegment = segments[sequenceNumbers.last()] ?: continue

                if (lastSegment.timestamp + aggregationTimeout < System.currentTimeMillis()) {
                    val compositeBuffer = Unpooled.compositeBuffer()

                    sequenceNumbers.forEach { sequenceNumber ->
                        val segment = segments.remove(sequenceNumber)
                        if (segment != null) {
                            val payload = segment.packet.payload as Encodable
                            compositeBuffer.addComponent(true, payload.encode())
                        }
                    }

                    val packet = lastSegment.packet
                    packet.payload = ByteBufPayload(compositeBuffer)

                    handler.handle(packet)
                }

                sequenceNumbers.clear()
                walkThroughTcpSegments(sequenceNumbers, lastSegment.sequenceNumber + lastSegment.payloadLength)
            }
        }

        private fun walkThroughTcpSegments(sequenceNumbers: MutableList<Long>, nextSequenceNumber: Long = -1) {
            val (sequenceNumber, segment) = segments.ceilingEntry(nextSequenceNumber) ?: return

            if (nextSequenceNumber == -1L || nextSequenceNumber == sequenceNumber) {
                sequenceNumbers.add(sequenceNumber)
                walkThroughTcpSegments(sequenceNumbers, sequenceNumber + segment.payloadLength)
            }
        }

        private fun processPreviousTcpSegment(currentSegment: TcpSegment) {
            val (sequenceNumber, segment) = segments.lowerEntry(currentSegment.sequenceNumber) ?: return

            if (sequenceNumber + segment.payloadLength == currentSegment.sequenceNumber) {
                val packet = segment.packet
                val buffer = (packet.payload as Encodable).encode()

                if (assert.invoke(buffer)) {
                    handler.handle(packet)
                    segments.remove(sequenceNumber)
                }
            }
        }
    }
}