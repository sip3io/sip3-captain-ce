/*
 * Copyright 2018-2024 SIP3.IO, Corp.
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

import io.sip3.captain.ce.domain.Packet
import io.sip3.captain.ce.util.SipUtil
import io.sip3.commons.domain.payload.Encodable
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject

/**
 * Handles UDP packets
 */
class UdpHandler(vertx: Vertx, config: JsonObject, bulkOperationsEnabled: Boolean) : Handler(vertx, config, bulkOperationsEnabled) {

    companion object {

        const val TYPE_VXLAN = 0x0800
    }

    private var rtcpEnabled = false
    private var rtpEnabled = false
    private var sipEnabled = true
    private var vxlanEnabled = false
    private var tzspEnabled = false

    private val rtcpHandler: RtcpHandler by lazy {
        RtcpHandler(vertx, config, bulkOperationsEnabled)
    }
    private val rtpHandler: RtpHandler by lazy {
        RtpHandler(vertx, config, bulkOperationsEnabled)
    }
    private val sipHandler: SipHandler by lazy {
        SipHandler(vertx, config, bulkOperationsEnabled)
    }
    private val vxlanHandler: VxlanHandler by lazy {
        VxlanHandler(vertx, config, bulkOperationsEnabled)
    }
    private val tzspHandler: TzspHandler by lazy {
        TzspHandler(vertx, config, bulkOperationsEnabled)
    }

    init {
        config.getJsonObject("rtcp")?.getBoolean("enabled")?.let {
            rtcpEnabled = it
        }
        config.getJsonObject("rtp")?.getBoolean("enabled")?.let {
            rtpEnabled = it
        }
        config.getJsonObject("sip")?.getBoolean("enabled")?.let {
            sipEnabled = it
        }
        config.getJsonObject("vxlan")?.getBoolean("enabled")?.let {
            vxlanEnabled = it
        }
        config.getJsonObject("tzsp")?.getBoolean("enabled")?.let {
            tzspEnabled = it
        }
    }

    override fun onPacket(packet: Packet) {
        val buffer = (packet.payload as Encodable).encode()

        // Source Port
        packet.srcPort = buffer.readUnsignedShort()
        // Destination Port
        packet.dstPort = buffer.readUnsignedShort()
        // Length
        buffer.skipBytes(2)
        // Checksum
        buffer.skipBytes(2)

        // Filter packets of size smaller than minimal RTP/RTCP or SIP
        if (buffer.readableBytes() < 8) {
            return
        }

        val offset = buffer.readerIndex()
        when {
            // RTP or RTCP packet
            buffer.getUnsignedByte(offset).toInt().shr(6) == 2 -> {
                val packetType = buffer.getUnsignedByte(offset + 1).toInt()
                if (packetType in 200..211) {
                    // Skip ICMP(RTCP) packet
                    if (rtcpEnabled && packet.rejected == null) {
                        rtcpHandler.handle(packet)
                    }
                } else if (rtpEnabled) {
                    rtpHandler.handle(packet)
                }
            }
            // SIP packet
            sipEnabled && SipUtil.startsWithSipWord(buffer) -> {
                // Skip ICMP(SIP) packet
                if (packet.rejected == null) {
                    sipHandler.handle(packet)
                }
            }
            // VXLAN packet
            vxlanEnabled && buffer.getUnsignedShort(offset) == TYPE_VXLAN -> {
                // Skip ICMP(VXLAN) packet
                if (packet.rejected == null) {
                    vxlanHandler.handle(packet)
                }
            }
            // TZSP packet
            tzspEnabled && buffer.getByte(offset).toInt() == 1 -> {
                // Skip ICMP(TZSP) packet
                if (packet.rejected == null) {
                    tzspHandler.handle(packet)
                }
            }
        }
    }
}
