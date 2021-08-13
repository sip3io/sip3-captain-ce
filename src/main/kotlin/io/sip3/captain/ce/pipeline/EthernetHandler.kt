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
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.domain.payload.Encodable
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject

/**
 * Handles Ethernet packets
 */
class EthernetHandler(vertx: Vertx, config: JsonObject, bulkOperationsEnabled: Boolean) : Handler(vertx, config, bulkOperationsEnabled) {

    companion object {

        const val TYPE_IPV4 = 0x0800
        const val TYPE_IPV6 = 0x86dd
        const val TYPE_802_1_Q = 0x8100
        const val TYPE_802_1_AD = 0x88a8
        const val TYPE_LINUX_COOKED_CAPTURE = 0x0000
    }

    private val ipv4Handler: Ipv4Handler by lazy {
        Ipv4Handler(vertx, config, bulkOperationsEnabled)
    }
    private val ipv6Handler: Ipv6Handler by lazy {
        Ipv6Handler(vertx, config, bulkOperationsEnabled)
    }

    override fun onPacket(packet: Packet) {
        val buffer = (packet.payload as Encodable).encode()

        // Source MAC and Destination MAC
        buffer.skipBytes(12)
        // Ethernet Type
        val etherType = readEthernetType(buffer)

        when (etherType) {
            TYPE_IPV4 -> ipv4Handler.handle(packet)
            TYPE_IPV6 -> ipv6Handler.handle(packet)
        }
    }

    private fun readEthernetType(buffer: ByteBuf): Int {
        // Ethernet Type or TPI
        val ethernetType = buffer.readUnsignedShort()

        return when (ethernetType) {
            TYPE_802_1_AD, TYPE_802_1_Q -> {
                // TCI
                buffer.skipBytes(2)
                readEthernetType(buffer)
            }
            TYPE_LINUX_COOKED_CAPTURE -> readEthernetType(buffer)
            else -> ethernetType
        }
    }
}