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
import io.sip3.captain.ce.domain.Packet
import io.vertx.core.Vertx

/**
 * Handles Ethernet packets
 */
class EthernetHandler(vertx: Vertx, bulkOperationsEnabled: Boolean) : Handler(vertx, bulkOperationsEnabled) {

    companion object {

        const val TYPE_IPV4 = 0x0800
        const val TYPE_802_1_Q = 0x8100
        const val TYPE_802_1_AD = 0x88a8
        const val TYPE_LINUX_COOKED_CAPTURE = 0x0000
    }

    private val ipv4Handler = Ipv4Handler(vertx, bulkOperationsEnabled)

    override fun onPacket(packet: Packet) {
        val buffer = packet.payload.encode()

        // Source MAC and Destination MAC
        buffer.skipBytes(12)
        // Ethernet Type
        val etherType = readEthernetType(buffer)

        when (etherType) {
            TYPE_IPV4 -> ipv4Handler.handle(packet)
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