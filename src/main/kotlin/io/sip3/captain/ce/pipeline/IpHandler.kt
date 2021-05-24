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
import io.sip3.captain.ce.domain.IpHeader
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.domain.payload.ByteArrayPayload
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.util.getBytes
import io.sip3.commons.vertx.util.localSend
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject

/**
 * Handles IPv4 and IPv6 packets
 */
abstract class IpHandler(vertx: Vertx, config: JsonObject, bulkOperationsEnabled: Boolean) : Handler(vertx, config, bulkOperationsEnabled) {

    protected var bulkSize = 1

    private val ipPackets = mutableListOf<Pair<IpHeader, Packet>>()

    init {
        if (bulkOperationsEnabled) {
            config.getJsonObject("ip")?.getInteger("bulk-size")?.let {
                bulkSize = it
            }
        }
    }

    abstract fun readIpHeader(buffer: ByteBuf): IpHeader

    abstract fun routePacket(packet: Packet)

    override fun onPacket(packet: Packet) {
        val buffer = (packet.payload as Encodable).encode()
        val offset = buffer.readerIndex()

        val header = readIpHeader(buffer)

        // Ignore packets with the payload size smaller then `totalLength` (e.g. ICMP encapsulation)
        val capacity = offset + header.totalLength
        if (buffer.capacity() < capacity) {
            return
        }

        val slice = buffer.slice(0, capacity)
        slice.readerIndex(offset + header.headerLength)

        if (header.moreFragments || header.fragmentOffset > 0) {
            packet.payload = ByteArrayPayload(slice.getBytes())
            ipPackets.add(Pair(header, packet))

            if (ipPackets.size >= bulkSize) {
                vertx.eventBus().localSend(RoutesCE.fragment, ipPackets.toList())
                ipPackets.clear()
            }
        } else {
            packet.apply {
                srcAddr = header.srcAddr
                dstAddr = header.dstAddr
                protocolNumber = header.protocolNumber
                payload = ByteBufPayload(slice)
            }

            routePacket(packet)
        }
    }
}