/*
 * Copyright 2018-2025 SIP3.IO, Corp.
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
import io.sip3.commons.ProtocolCodes
import io.sip3.commons.domain.payload.ByteArrayPayload
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.util.getBytes
import io.sip3.commons.vertx.util.localSend
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject

/**
 * Handles IPv6 packets
 */
class Ipv6Handler(vertx: Vertx, config: JsonObject, bulkOperationsEnabled: Boolean) : IpHandler(vertx, config, bulkOperationsEnabled) {

    companion object {

        private const val TYPE_ICMP = 0x3A
        private const val TYPE_IPV6 = 0x29
        private const val TYPE_TCP = 0x06
        private const val TYPE_UDP = 0x11

        val UPPER_LAYER_HEADERS = setOf(
            TYPE_ICMP,
            TYPE_IPV6,
            TYPE_TCP,
            TYPE_UDP,
            // `No Next Header` technically not an upper layer header.
            // However, in the SIP3 context it could be be considered such one
            0x3B
        )
    }

    // Important! We will reuse this object per each packet to reduce `IpHeader` memory footprint
    // It might bring some complexity in further `IpHeader` processing. So, please pay attention
    private val header = IpHeader(16)

    private val tcpPackets = mutableListOf<Packet>()

    private val udpHandler: UdpHandler by lazy {
        UdpHandler(vertx, config, bulkOperationsEnabled)
    }

    override fun readIpHeader(buffer: ByteBuf): IpHeader {
        var payloadLength: Int
        var nextHeader: Int

        // Explicitly reset header parameters which might be absent
        header.apply {
            identification = 0
            moreFragments = false
            fragmentOffset = 0
        }

        return header.apply {
            headerLength = 40
            // Version & Traffic Class & Flow Label
            buffer.skipBytes(4)
            // Payload Length
            payloadLength = buffer.readUnsignedShort()
            // Next Header
            nextHeader = buffer.readByte().toInt()
            // Hop Limit
            buffer.skipBytes(1)
            // Source IP
            buffer.readBytes(srcAddr)
            // Destination IP
            buffer.readBytes(dstAddr)

            // Extension Headers
            while (nextHeader !in UPPER_LAYER_HEADERS) {
                when (nextHeader) {
                    // Fragment Header
                    0x2C -> {
                        // Next Header
                        nextHeader = buffer.readByte().toInt()
                        // Reserved
                        buffer.skipBytes(1)
                        // Fragment Offset & Res & M
                        val fragmentOffsetAndFlags = buffer.readUnsignedShort()
                        fragmentOffset = fragmentOffsetAndFlags.shr(3)
                        moreFragments = fragmentOffsetAndFlags.and(0x01) != 0
                        // Identification
                        identification = buffer.readInt()

                        headerLength += 8
                    }
                    // Other headers
                    else -> {
                        // Next Header
                        nextHeader = buffer.readByte().toInt()
                        // Header Extension Length
                        val length = 8 * (buffer.readUnsignedByte() + 1)
                        // Specific Data
                        buffer.skipBytes(length - 2)

                        headerLength += length
                    }
                }
            }

            totalLength = headerLength + payloadLength
            protocolNumber = nextHeader
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
                    protocolCode = ProtocolCodes.ICMP
                    recordingMark = buffer.readerIndex()
                }

                // Type
                val type = buffer.readByte().toInt()
                // Code
                val code = buffer.readByte().toInt()
                // Checksum & Rest of Header
                buffer.skipBytes(6)

                // Destination Port Unreachable
                if (type == 1 && code == 4) {
                    val p = Packet().apply {
                        timestamp = packet.timestamp
                        nanos = packet.nanos
                        payload = packet.payload

                        rejected = packet
                    }
                    onPacket(p)
                }
            }
            // IPv6:
            TYPE_IPV6 -> {
                onPacket(packet)
            }
        }
    }
}