/*
 * Copyright 2018-2022 SIP3.IO, Corp.
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
import io.sip3.captain.ce.recording.RecordingManager
import io.sip3.commons.PacketTypes
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.domain.payload.RtpPacketPayload
import io.sip3.commons.util.toIntRange
import io.sip3.commons.vertx.util.localSend
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import kotlin.experimental.and

/**
 * Handles RTP packets
 */
class RtpHandler(vertx: Vertx, config: JsonObject, bulkOperationsEnabled: Boolean) : Handler(vertx, config, bulkOperationsEnabled) {

    companion object {

        val DYNAMIC_PT = (96..127).map { it.toByte() }.toSet()
    }

    private val packets = mutableMapOf<Long, MutableList<Packet>>()
    private val recordings = mutableListOf<Packet>()
    private var bulkSize = 1

    private var instances: Int = 1
    private var payloadTypes = mutableSetOf<Byte>()
    private var collectorEnabled = false
    private var rtpEventsEnabled = false

    private val recordingManager = RecordingManager.getInstance(vertx, config)

    init {
        config.getJsonObject("vertx")?.getInteger("instances")?.let {
            instances = it
        }
        config.getJsonObject("rtp")?.let { rtpConfig ->
            if (bulkOperationsEnabled) {
                rtpConfig.getInteger("bulk-size")?.let {
                    bulkSize = it
                }
            }

            rtpConfig.getJsonArray("payload-types")?.forEach { payloadType ->
                when (payloadType) {
                    is Int -> payloadTypes.add(payloadType.toByte())
                    is String -> {
                        payloadType.toIntRange().forEach { payloadTypes.add(it.toByte()) }
                    }
                }
            }

            rtpConfig.getJsonObject("collector")?.getBoolean("enabled")?.let {
                collectorEnabled = it
            }

            rtpConfig.getJsonObject("events")?.getBoolean("enabled")?.let {
                rtpEventsEnabled = it
            }
        }
    }

    override fun onPacket(packet: Packet) {
        // Retrieve RTP packet buffer and mark it for further usage in `RecordingHandler`
        val buffer = (packet.payload as Encodable).encode()
        packet.apply {
            protocolCode = PacketTypes.RTP
            recordingMark = buffer.readerIndex()
        }

        // Read RTP header
        val rtpPacketPayload = readRtpPacketPayload(buffer) ?: return

        val recording = recordingManager.record(packet)
        if (recording != null) {
            val p = packet.rejected ?: packet.clone()
            p.apply {
                protocolCode = PacketTypes.REC
                payload = recording
            }
            recordings.add(p)

            if (recordings.size >= bulkSize) {
                vertx.eventBus().localSend(RoutesCE.encoder, recordings.toList())
                recordings.clear()
            }

            rtpPacketPayload.recorded = true
        }

        if (collectorEnabled) {
            val index = rtpPacketPayload.ssrc % instances

            val packetsByIndex = packets.getOrPut(index) { mutableListOf() }
            packet.apply {
                payload = rtpPacketPayload
            }
            packetsByIndex.add(packet)

            if (packetsByIndex.size >= bulkSize) {
                vertx.eventBus().localSend(RoutesCE.rtp + "_$index", packetsByIndex.toList())
                packetsByIndex.clear()
            }
        }
    }

    private fun readRtpPacketPayload(buffer: ByteBuf): RtpPacketPayload? {
        return RtpPacketPayload().apply {
            // Version & P & X & CC
            val flags = buffer.readByte()
            val x = (flags.and(16) == 16.toByte())
            val cc = flags.and(15)
            // Marker & Payload Type
            buffer.readUnsignedByte().let { byte ->
                marker = (byte.and(128) == 128.toShort())
                payloadType = byte.and(127).toByte()
            }
            // Sequence Number
            sequenceNumber = buffer.readUnsignedShort()
            // Timestamp
            timestamp = buffer.readUnsignedInt()
            // SSRC
            ssrc = buffer.readUnsignedInt()

            // Make sure the payload is RTP packet before parsing the rest of it
            if (ssrc <= 0 || payloadType < 0 || (payloadTypes.isNotEmpty() && !payloadTypes.contains(payloadType))) {
                return null
            }

            // CSRC
            if (cc > 0) buffer.skipBytes(cc * 4)
            // Header Extension
            if (x) {
                // Profile-Specific Identifier
                buffer.skipBytes(2)
                // Length
                val length = buffer.readUnsignedShort()
                // Extension Header
                buffer.skipBytes(4 * length)
            }

            if (rtpEventsEnabled && DYNAMIC_PT.contains(payloadType) && isRtpEvent(buffer)) {
                event = buffer.readInt()
            }
        }
    }

    private fun isRtpEvent(buffer: ByteBuf): Boolean {
        return (buffer.readableBytes() == 4)
                && (buffer.getInt(buffer.readerIndex()) shr 22) and 1 == 0
    }
}