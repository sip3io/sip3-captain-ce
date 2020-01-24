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

package io.sip3.captain.ce.pipeline

import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.USE_LOCAL_CODEC
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.PacketTypes
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.domain.payload.RtpHeaderPayload
import io.vertx.core.Vertx
import kotlin.experimental.and

/**
 * Handles RTP packets
 */
class RtpHandler(vertx: Vertx, bulkOperationsEnabled: Boolean) : Handler(vertx, bulkOperationsEnabled) {

    private val packets = mutableListOf<Packet>()
    private var bulkSize = 1

    init {
        if (bulkOperationsEnabled) {
            vertx.orCreateContext.config().getJsonObject("rtp")?.let { config ->
                config.getInteger("bulk-size")?.let { bulkSize = it }
            }
        }
    }

    override fun onPacket(packet: Packet) {
        packet.protocolCode = PacketTypes.RTP

        val buffer = (packet.payload as Encodable).encode()
        packet.payload = RtpHeaderPayload().apply {
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
        packets.add(packet)

        if (packets.size >= bulkSize) {
            vertx.eventBus().send(RoutesCE.rtp, packets.toList(), USE_LOCAL_CODEC)
            packets.clear()
        }
    }
}