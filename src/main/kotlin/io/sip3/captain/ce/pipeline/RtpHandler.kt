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
import io.sip3.captain.ce.domain.Packet
import io.sip3.captain.ce.util.toIntRange
import io.sip3.commons.PacketTypes
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.domain.payload.RtpHeaderPayload
import io.sip3.commons.vertx.util.localRequest
import io.vertx.core.Context
import io.vertx.core.Vertx
import kotlin.experimental.and

/**
 * Handles RTP packets
 */
class RtpHandler(context: Context, bulkOperationsEnabled: Boolean) : Handler(context, bulkOperationsEnabled) {

    private val packets = mutableListOf<Packet>()
    private var bulkSize = 1
    private var payloadTypes: Set<Byte> = emptySet()

    private val vertx: Vertx

    init {
        context.config().getJsonObject("rtp")?.let { config ->
            if (bulkOperationsEnabled) {
                config.getInteger("bulk-size")?.let { bulkSize = it }
            }

            config.getJsonArray("payload-types")?.let { array ->
                val result = mutableSetOf<Byte>()
                array.forEach { element ->
                    when (element) {
                        is Int -> result.add(element.toByte())
                        is String -> {
                            element.toIntRange()
                                    .forEach { result.add(it.toByte()) }
                        }
                    }
                }
                payloadTypes = result
            }
        }

        vertx = context.owner()
    }

    override fun onPacket(packet: Packet) {
        packet.protocolCode = PacketTypes.RTP

        val buffer = (packet.payload as Encodable).encode()
        // Version & P & X & CC
        buffer.skipBytes(1)

        val payloadType: Byte
        val marker: Boolean
        buffer.readUnsignedByte().let { byte ->
            payloadType = byte.and(127).toByte()
            marker = (byte.and(128) == 128.toShort())
        }

        if (payloadTypes.isEmpty() || payloadType in payloadTypes) {
            packet.payload = RtpHeaderPayload().apply {
                this.payloadType = payloadType
                this.marker = marker
                sequenceNumber = buffer.readUnsignedShort()
                timestamp = buffer.readUnsignedInt()
                ssrc = buffer.readUnsignedInt()
            }

            packets.add(packet)

            if (packets.size >= bulkSize) {
                vertx.eventBus().localRequest<Any>(RoutesCE.rtp, packets.toList())
                packets.clear()
            }
        }
    }
}