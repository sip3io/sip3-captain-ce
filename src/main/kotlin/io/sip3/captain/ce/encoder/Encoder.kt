/*
 * Copyright 2018-2021 SIP3.IO, Inc.
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

package io.sip3.captain.ce.encoder

import io.netty.buffer.ByteBuf
import io.netty.buffer.CompositeByteBuf
import io.netty.buffer.Unpooled
import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.util.getBytes
import io.sip3.commons.util.writeTlv
import io.sip3.commons.vertx.annotations.Instance
import io.sip3.commons.vertx.util.localSend
import io.vertx.core.AbstractVerticle
import io.vertx.core.buffer.Buffer
import mu.KotlinLogging
import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream

/**
 * Encodes packets to SIP3 protocol
 */
@Instance
class Encoder : AbstractVerticle() {

    private val logger = KotlinLogging.logger {}

    companion object {

        val PREFIX = byteArrayOf(
            0x53.toByte(), // S
            0x49.toByte(), // I
            0x50.toByte(), // P
            0x33.toByte(), // 3
        )

        const val PROTO_VERSION = 0x02
        const val PACKET_TYPE = 0x01
        const val PACKET_VERSION = 0x01

        const val TAG_TIMESTAMP_TIME = 1
        const val TAG_TIMESTAMP_NANOS = 2
        const val TAG_SRC_ADDR = 3
        const val TAG_DST_ADDR = 4
        const val TAG_SRC_PORT = 5
        const val TAG_DST_PORT = 6
        const val TAG_PROTOCOL_CODE = 7
        const val TAG_PAYLOAD = 8
    }

    private val buffers = mutableListOf<Buffer>()
    private var mtuSize = 1450
    private var bulkSize = 1

    override fun start() {
        config().getJsonObject("encoder")?.let { config ->
            config.getInteger("mtu-size")?.let {
                mtuSize = it
            }
            config.getInteger("bulk-size")?.let {
                bulkSize = it
            }
        }

        vertx.eventBus().localConsumer<List<Packet>>(RoutesCE.encoder) { event ->
            try {
                val packets = event.body()
                handle(packets)
            } catch (e: Exception) {
                logger.error("Encoder 'handle()' failed.", e)
            }
        }
    }

    fun handle(packets: List<Packet>) {
        var cumulativeBuffer = encodeHeader()

        packets.map { encodePacket(it) }.forEach { packet ->
            // Check if packet is smaller than MTU
            // Otherwise, compress and send it separately
            if (packet.writerIndex() >= mtuSize) {
                val bytes = packet.getBytes()
                InflaterInputStream(ByteArrayInputStream(bytes)).use { inflater ->
                    val compressedBuffer = encodeHeader(1).apply {
                        addComponent(true, Unpooled.wrappedBuffer(inflater.readBytes()))
                    }
                    send(compressedBuffer)
                }
            }

            // Check if cumulative buffer has enough capacity
            // Otherwise, send it further and create a new one
            if (cumulativeBuffer.writerIndex() + packet.writerIndex() >= mtuSize) {
                send(cumulativeBuffer)
                cumulativeBuffer = encodeHeader()
            }

            cumulativeBuffer.addComponent(true, packet)
        }

        if (cumulativeBuffer.writerIndex() > 0) {
            send(cumulativeBuffer)
        }
    }

    private fun encodeHeader(compressed: Int = 0): CompositeByteBuf {
        val header = Unpooled.buffer(6).apply {
            writeBytes(PREFIX)
            writeByte(PROTO_VERSION)
            writeByte(compressed)
        }

        return Unpooled.compositeBuffer().apply {
            addComponent(true, header)
        }
    }

    private fun encodePacket(packet: Packet): ByteBuf {
        val srcAddrLength = packet.srcAddr.size
        val dstAddrLength = packet.dstAddr.size

        val payload = (packet.payload as Encodable).encode().getBytes()
        val payloadLength = payload.size

        // Packet length will be calculated accordingly to the following formula
        //
        // Fixed Part:
        // 2  - Type & Version
        // 2  - Length
        //
        // Tag-Length-Value Part:
        // 11 - Milliseconds
        // 7  - Nanoseconds
        // 3+ - Source Address
        // 3+ - Destination Address
        // 5  - Source Port
        // 5  - Destination Port
        // 4  - Protocol Code
        // 3+ - Payload
        //
        // In total it will be 45+
        val packetLength = 45 + srcAddrLength + dstAddrLength + payloadLength

        return Unpooled.buffer(packetLength).apply {
            writeByte(PACKET_TYPE)
            writeByte(PACKET_VERSION)
            writeShort(packetLength)

            writeTlv(TAG_TIMESTAMP_TIME, packet.timestamp.time)
            writeTlv(TAG_TIMESTAMP_NANOS, packet.timestamp.nanos)

            writeTlv(TAG_SRC_ADDR, packet.srcAddr)
            writeTlv(TAG_DST_ADDR, packet.dstAddr)
            writeTlv(TAG_SRC_PORT, packet.srcPort.toShort())
            writeTlv(TAG_DST_PORT, packet.dstPort.toShort())

            writeTlv(TAG_PROTOCOL_CODE, packet.protocolCode)
            writeTlv(TAG_PAYLOAD, payload)
        }
    }

    private fun send(buffer: ByteBuf) {
        buffers.add(Buffer.buffer(buffer))

        if (buffers.size >= bulkSize) {
            vertx.eventBus().localSend(RoutesCE.sender, buffers.toList())
            buffers.clear()
        }
    }
}