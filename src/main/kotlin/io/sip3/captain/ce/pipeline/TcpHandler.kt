/*
 * Copyright 2018-2021 SIP3.IO, Corp.
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
import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.domain.Packet
import io.sip3.captain.ce.domain.TcpSegment
import io.sip3.captain.ce.util.SipUtil
import io.sip3.captain.ce.util.SmppUtil
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.util.IpUtil
import io.sip3.commons.vertx.annotations.Instance
import io.sip3.commons.vertx.collections.PeriodicallyExpiringHashMap
import io.vertx.core.AbstractVerticle
import mu.KotlinLogging
import java.util.*

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

    private lateinit var connections: PeriodicallyExpiringHashMap<String, TcpConnection>

    override fun start() {
        config().getJsonObject("tcp")?.let { config ->
            config.getLong("expiration-delay")?.let {
                expirationDelay = it
            }
            config.getLong("aggregation-timeout")?.let {
                aggregationTimeout = it
            }
            config.getLong("idle-connection-timeout")?.let {
                idleConnectionTimeout = it
            }
        }

        config().getJsonObject("sip")?.getBoolean("enabled")?.let {
            sipEnabled = it
        }
        config().getJsonObject("smpp")?.getBoolean("enabled")?.let {
            smppEnabled = it
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
                sipEnabled && SipUtil.startsWithSipWord(buffer) ->
                    TcpConnection(SipHandler(vertx, config(), false)) { b: ByteBuf -> SipUtil.startsWithSipWord(b) }
                smppEnabled && SmppUtil.isPdu(buffer) ->
                    TcpConnection(SmppHandler(vertx, config(), false)) { b: ByteBuf -> SmppUtil.isPdu(b) }
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
                this.payloadLength = buffer.readableBytes()
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
