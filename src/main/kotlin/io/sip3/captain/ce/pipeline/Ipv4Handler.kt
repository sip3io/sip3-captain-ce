/*
 * Copyright 2018-2023 SIP3.IO, Corp.
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
import io.sip3.commons.PacketTypes
import io.sip3.commons.domain.payload.ByteArrayPayload
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.util.getBytes
import io.sip3.commons.vertx.util.localSend
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject

/**
 * Handles IPv4 packets
 */
class Ipv4Handler(vertx: Vertx, config: JsonObject, bulkOperationsEnabled: Boolean) : IpHandler(vertx, config, bulkOperationsEnabled) {

    companion object {

        const val TYPE_ICMP = 0x01
        const val TYPE_IPV4 = 0x04
        const val TYPE_TCP = 0x06
        const val TYPE_UDP = 0x11
        const val TYPE_GRE = 0x2F
        const val TYPE_SCTP = 0x84
    }

    // Important! We will reuse this object per each packet to reduce `IpHeader` memory footprint
    // It might bring some complexity in further `IpHeader` processing. So, please pay attention
    private val header = IpHeader()

    private val tcpPackets = mutableListOf<Packet>()
    private val sctpPackets = mutableListOf<Packet>()

    private val udpHandler: UdpHandler by lazy {
        UdpHandler(vertx, config, bulkOperationsEnabled)
    }
    private val greHandler: GreHandler by lazy {
        GreHandler(vertx, config, bulkOperationsEnabled)
    }

    override fun readIpHeader(buffer: ByteBuf): IpHeader {
        return header.apply {
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

    override fun routePacket(packet: Packet) {
        when (packet.protocolNumber) {
            // UDP:
            TYPE_UDP -> {
                udpHandler.handle(packet)
            }
            // TCP:
            TYPE_TCP -> {
                packet.payload = run {
                    val buffer = (packet.payload as Encodable).encode()
                    return@run ByteArrayPayload(buffer.getBytes())
                }
                tcpPackets.add(packet)

                if (tcpPackets.size >= bulkSize) {
                    vertx.eventBus().localSend(RoutesCE.tcp, tcpPackets.toList())
                    tcpPackets.clear()
                }
            }
            // ICMP:
            TYPE_ICMP -> {
                val buffer = (packet.payload as Encodable).encode()
                packet.apply {
                    protocolCode = PacketTypes.ICMP
                    recordingMark = buffer.readerIndex()
                }

                // Type
                val type = buffer.readByte().toInt()
                // Code
                val code = buffer.readByte().toInt()
                // Checksum & Rest of Header
                buffer.skipBytes(6)

                // Destination Port Unreachable
                if (type == 3 && code == 3) {
                    val p = Packet().apply {
                        timestamp = packet.timestamp
                        nanos = packet.nanos
                        payload = packet.payload

                        rejected = packet
                    }
                    onPacket(p)
                }
            }
            // IPv4:
            TYPE_IPV4 -> {
                onPacket(packet)
            }
            // GRE(Generic Routing Encapsulation):
            TYPE_GRE -> {
                greHandler.handle(packet)
            }
            // SCTP:
            TYPE_SCTP -> {
                packet.payload = run {
                    val buffer = (packet.payload as Encodable).encode()
                    return@run ByteArrayPayload(buffer.getBytes())
                }
                sctpPackets.add(packet)

                if (sctpPackets.size >= bulkSize) {
                    vertx.eventBus().localSend(RoutesCE.sctp, sctpPackets.toList())
                    sctpPackets.clear()
                }
            }
        }
    }
}