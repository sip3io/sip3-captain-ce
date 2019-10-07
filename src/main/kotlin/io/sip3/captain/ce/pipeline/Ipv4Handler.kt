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
import io.sip3.captain.ce.Routes
import io.sip3.captain.ce.USE_LOCAL_CODEC
import io.sip3.captain.ce.domain.Ipv4Header
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.domain.payload.ByteArrayPayload
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.util.getBytes
import io.vertx.core.Vertx

/**
 * Handles IPv4 packets
 */
class Ipv4Handler(vertx: Vertx, bulkOperationsEnabled: Boolean) : Handler(vertx, bulkOperationsEnabled) {

    companion object {

        const val TYPE_TCP = 0x06
        const val TYPE_UDP = 0x11
        const val TYPE_ICMP = 0x01
        const val TYPE_IPV4 = 0x04
    }

    private val ipv4Packets = mutableListOf<Pair<Ipv4Header, Packet>>()
    private val tcpPackets = mutableListOf<Packet>()
    private var bulkSize = 1

    private val udpHandler = UdpHandler(vertx, bulkOperationsEnabled)

    init {
        if (bulkOperationsEnabled) {
            vertx.orCreateContext.config().getJsonObject("ipv4")?.let { config ->
                config.getInteger("bulk-size")?.let { bulkSize = it }
            }
        }
    }

    override fun onPacket(packet: Packet) {
        val buffer = (packet.payload as Encodable).encode()
        val offset = buffer.readerIndex()

        val ipv4Header = readIpv4Header(buffer)

        if (ipv4Header.moreFragments || ipv4Header.fragmentOffset > 0) {
            packet.payload = ByteArrayPayload(buffer.getBytes())
            ipv4Packets.add(Pair(ipv4Header, packet))

            if (ipv4Packets.size >= bulkSize) {
                vertx.eventBus().send(Routes.fragment, ipv4Packets.toList(), USE_LOCAL_CODEC)
                ipv4Packets.clear()
            }
        } else {
            packet.srcAddr = ipv4Header.srcAddr
            packet.dstAddr = ipv4Header.dstAddr
            packet.protocolNumber = ipv4Header.protocolNumber

            buffer.readerIndex(offset + ipv4Header.headerLength)

            routePacket(packet)
        }
    }

    fun readIpv4Header(buffer: ByteBuf): Ipv4Header {
        return Ipv4Header().apply {
            // Version & IHL
            headerLength = 4 * buffer.readUnsignedByte().toInt().and(0x0f)
            // DSCP & ECN
            buffer.skipBytes(1)
            // Total Length
            totalLength = buffer.readUnsignedShort()
            // Identification
            identification = buffer.readUnsignedShort()
            // Flags & Fragment Offset
            val flagsAndFragmentOffset = buffer.readUnsignedShort()
            moreFragments = flagsAndFragmentOffset.and(0x2000) != 0
            fragmentOffset = flagsAndFragmentOffset.and(0x1fff)
            // Time To Live
            buffer.skipBytes(1)
            // Protocol
            protocolNumber = buffer.readUnsignedByte().toInt()
            // Header Checksum
            buffer.skipBytes(2)
            // Source IP
            buffer.readBytes(srcAddr)
            // Destination IP
            buffer.readBytes(dstAddr)
        }
    }

    fun routePacket(packet: Packet) {
        when (packet.protocolNumber) {
            // UDP:
            TYPE_UDP -> udpHandler.handle(packet)
            // TCP:
            TYPE_TCP -> {
                packet.payload = run {
                    val buffer = (packet.payload as Encodable).encode()
                    return@run ByteArrayPayload(buffer.getBytes())
                }
                tcpPackets.add(packet)

                if (tcpPackets.size >= bulkSize) {
                    vertx.eventBus().send(Routes.tcp, tcpPackets.toList(), USE_LOCAL_CODEC)
                    tcpPackets.clear()
                }
            }
            // ICMP:
            TYPE_ICMP -> {
                val buffer = (packet.payload as Encodable).encode()
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
                    onPacket(packet)
                }
            }
            // IPv4:
            TYPE_IPV4 -> onPacket(packet)
        }
    }
}