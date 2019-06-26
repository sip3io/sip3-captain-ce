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
 * Handles ICMP packets
 */
class IcmpHandler(val ipv4Handler: Ipv4Handler, vertx: Vertx, bulkOperationsEnabled: Boolean) : Handler(vertx, bulkOperationsEnabled) {

    override fun onPacket(buffer: ByteBuf, packet: Packet) {
        // Type
        val type = buffer.readByte().toInt()
        // Code
        val code = buffer.readByte().toInt()
        // Checksum & Rest of Header
        buffer.skipBytes(6)

        // Destination Port Unreachable
        if (type == 3 && code == 3) {
            packet.protocolCode = Packet.TYPE_ICMP
            packet.rejected = true
            ipv4Handler.handle(buffer, packet)
        }
    }
}