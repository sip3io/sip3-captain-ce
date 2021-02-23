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

package io.sip3.captain.ce.pipeline

import io.netty.buffer.ByteBuf
import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.PacketTypes
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.domain.payload.RtpHeaderPayload
import io.sip3.commons.util.toIntRange
import io.sip3.commons.vertx.util.localSend
import io.vertx.core.Context
import kotlin.experimental.and

/**
 * Handles RTP packets
 */
class RtpHandler(context: Context, bulkOperationsEnabled: Boolean) : Handler(context, bulkOperationsEnabled) {

    private val packets = mutableMapOf<Long, MutableList<Packet>>()
    private var bulkSize = 1

    private var instances: Int = 1
    private var payloadTypes = mutableSetOf<Byte>()
    private var recordingEnabled = false
    private var collectorEnabled = false

    private val vertx = context.owner()

    private val recordingHandler: RecordingHandler by lazy {
        RecordingHandler(context, bulkOperationsEnabled)
    }

    init {
        context.config().getJsonObject("vertx")?.getInteger("instances")?.let {
            instances = it
        }
        context.config().getJsonObject("rtp")?.let { config ->
            if (bulkOperationsEnabled) {
                config.getInteger("bulk-size")?.let {
                    bulkSize = it
                }
            }

            config.getJsonArray("payload-types")?.forEach { payloadType ->
                when (payloadType) {
                    is Int -> payloadTypes.add(payloadType.toByte())
                    is String -> {
                        payloadType.toIntRange().forEach { payloadTypes.add(it.toByte()) }
                    }
                }
            }

            config.getJsonObject("recording")?.getBoolean("enabled")?.let {
                recordingEnabled = it
            }

            config.getJsonObject("collector")?.getBoolean("enabled")?.let {
                collectorEnabled = it
            }
        }
    }

    override fun onPacket(packet: Packet) {
        packet.protocolCode = PacketTypes.RTP

        // Retrieve RTP packet buffer and mark it for further usage in `RecordingHandler`
        val buffer = (packet.payload as Encodable).encode()
        buffer.markReaderIndex()

        // Read RTP header
        val header = readRtpHeader(buffer)

        // Filter non-RTP packets
        if (header.ssrc <= 0 || header.payloadType < 0) {
            return
        }
        // Filter packets by payloadType
        if (payloadTypes.isNotEmpty() && !payloadTypes.contains(header.payloadType)) {
            return
        }

        if (recordingEnabled) {
            recordingHandler.onPacket(packet)
        }

        if (collectorEnabled) {
            val index = header.ssrc % instances
            val packetsByIndex = packets.getOrPut(index) { mutableListOf() }

            val p = Packet().apply {
                timestamp = packet.timestamp
                srcAddr = packet.srcAddr
                dstAddr = packet.dstAddr
                srcPort = packet.srcPort
                dstPort = packet.dstPort
                protocolCode = PacketTypes.RTP
                payload = header
            }
            packetsByIndex.add(p)
            if (packetsByIndex.size >= bulkSize) {
                vertx.eventBus().localSend(RoutesCE.rtp + "_$index", packetsByIndex.toList())
                packetsByIndex.clear()
            }
        }
    }

    private fun readRtpHeader(buffer: ByteBuf): RtpHeaderPayload {
        return RtpHeaderPayload().apply {
            // Version & P & X & CC
            buffer.skipBytes(1)
            buffer.readUnsignedByte().let { byte ->
                payloadType = byte.and(127).toByte()
                marker = (byte.and(128) == 128.toShort())
            }
            sequenceNumber = buffer.readUnsignedShort()
            timestamp = buffer.readUnsignedInt()
            ssrc = buffer.readUnsignedInt()
        }
    }
}
