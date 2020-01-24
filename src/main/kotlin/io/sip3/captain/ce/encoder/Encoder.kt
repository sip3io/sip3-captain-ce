/*
 * Copyright 2018-2020 SIP3.IO, Inc.
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

import io.netty.buffer.Unpooled
import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.USE_LOCAL_CODEC
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.util.writeTlv
import io.vertx.core.AbstractVerticle
import io.vertx.core.buffer.Buffer
import mu.KotlinLogging

/**
 * Encodes packets to SIP3 protocol
 */
class Encoder : AbstractVerticle() {

    private val logger = KotlinLogging.logger {}

    companion object {

        val PREFIX = byteArrayOf(
                0x53.toByte(), // S
                0x49.toByte(), // I
                0x50.toByte(), // P
                0x33.toByte()  // 3
        )

        const val COMPRESSED = 0x00 // Compressed
        const val TYPE = 0x01 // Type
        const val VERSION = 0x01 // Version

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
    private var bulkSize = 1

    override fun start() {
        config().getJsonObject("encoder")?.let { config ->
            config.getInteger("bulk-size")?.let { bulkSize = it }
        }
        vertx.eventBus().localConsumer<List<Packet>>(RoutesCE.encoder) { event ->
            try {
                val packets = event.body()
                encode(packets)
            } catch (e: Exception) {
                logger.error("Encoder 'encode()' failed.", e)
            }
        }
    }

    fun encode(packets: List<Packet>) {
        packets.forEach { packet ->
            val srcAddrLength = packet.srcAddr.size
            val dstAddrLength = packet.dstAddr.size

            val payload = (packet.payload as Encodable).encode()
            val payloadLength = payload.capacity()

            val packetLength = arrayListOf(
                    4,                         // Prefix
                    3,                         // Compressed & Type & Version
                    2,                         // Length
                    11,                        // Milliseconds
                    7,                         // Nanoseconds
                    3 + srcAddrLength,         // Source Address
                    3 + dstAddrLength,         // Destination Address
                    5,                         // Source Port
                    5,                         // Destination Port
                    4,                         // Protocol Code
                    3 + payloadLength          // Payload

            ).sum()

            val buffer = Unpooled.buffer(packetLength).apply {
                // Prefix
                writeBytes(PREFIX)
                // Compressed & Type & Version
                writeByte(COMPRESSED)
                writeByte(TYPE)
                writeByte(VERSION)
                // Length
                writeShort(packetLength - 5)

                writeTlv(TAG_TIMESTAMP_TIME, packet.timestamp.time)
                writeTlv(TAG_TIMESTAMP_NANOS, packet.timestamp.nanos)

                writeTlv(TAG_SRC_ADDR, packet.srcAddr)
                writeTlv(TAG_DST_ADDR, packet.dstAddr)
                writeTlv(TAG_SRC_PORT, packet.srcPort.toShort())
                writeTlv(TAG_DST_PORT, packet.dstPort.toShort())

                writeTlv(TAG_PROTOCOL_CODE, packet.protocolCode)
                writeTlv(TAG_PAYLOAD, payload)
            }
            buffers.add(Buffer.buffer(buffer))
        }

        if (buffers.size >= bulkSize) {
            vertx.eventBus().send(RoutesCE.sender, buffers.toList(), USE_LOCAL_CODEC)
            buffers.clear()
        }
    }
}