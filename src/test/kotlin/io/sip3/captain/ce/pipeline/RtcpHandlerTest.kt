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

import io.netty.buffer.Unpooled
import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.USE_LOCAL_CODEC
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.PacketTypes
import io.sip3.commons.domain.SdpSession
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.domain.payload.RtpReportPayload
import io.sip3.commons.util.IpUtil
import io.sip3.commons.vertx.test.VertxTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Timestamp

class RtcpHandlerTest : VertxTest() {

    companion object {

        val SRC_ADDR = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
        const val SRC_PORT = 12057
        val DST_ADDR = byteArrayOf(0x0a.toByte(), 0xc5.toByte(), 0x15.toByte(), 0x75.toByte())
        const val DST_PORT = 13057

        val SESSION_ID = run {
            val srcAddrAsLong = IpUtil.convertToInt(SRC_ADDR).toLong()
            ((srcAddrAsLong shl 32) or (SRC_PORT - 1).toLong())
        }

        // RTCP Sender Report only
        val PACKET_1 = byteArrayOf(
                // SR
                0x81.toByte(), 0xc8.toByte(), 0x00.toByte(), 0x0c.toByte(), // Payload type & report count & length
                0x01.toByte(), 0xa8.toByte(), 0xbd.toByte(), 0xe3.toByte(), // SSRC
                0xe1.toByte(), 0x37.toByte(), 0x70.toByte(), 0x16.toByte(), // Timestamp MSW
                0xd0.toByte(), 0x84.toByte(), 0x1e.toByte(), 0xde.toByte(), // Timestamp LSW
                0x9f.toByte(), 0x3a.toByte(), 0x5d.toByte(), 0x06.toByte(), // RTP Timestamp
                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xc3.toByte(), // Senders packet count
                0x00.toByte(), 0x00.toByte(), 0x79.toByte(), 0xe0.toByte(), // Senders octet count
                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // 1st report block ssrc
                0x03.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), // Fraction Lost & cumulative packet lost
                0x00.toByte(), 0x00.toByte(), 0x2c.toByte(), 0x21.toByte(), // Extended sequence number
                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x1c.toByte(), // Interarrival jitter
                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // LSR Timestamp
                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()  // Delay since last SR
        )

        // RTCP Sender Report and Source description
        val PACKET_2 = byteArrayOf(
                // SR
                0x81.toByte(), 0xc8.toByte(), 0x00.toByte(), 0x0c.toByte(),
                0x01.toByte(), 0xa8.toByte(), 0xbd.toByte(), 0xe3.toByte(),
                0xe1.toByte(), 0x37.toByte(), 0x70.toByte(), 0x1a.toByte(),
                0xd5.toByte(), 0xa3.toByte(), 0x6e.toByte(), 0x2e.toByte(),
                0x00.toByte(), 0x00.toByte(), 0xfa.toByte(), 0xa0.toByte(),
                0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x8c.toByte(),
                0x00.toByte(), 0x00.toByte(), 0xf7.toByte(), 0x80.toByte(),
                0x00.toByte(), 0x8e.toByte(), 0xb0.toByte(), 0x44.toByte(),
                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
                0x00.toByte(), 0x00.toByte(), 0x2c.toByte(), 0xea.toByte(),
                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x37.toByte(),
                0x70.toByte(), 0x1a.toByte(), 0x03.toByte(), 0xd7.toByte(),
                0x00.toByte(), 0x00.toByte(), 0x80.toByte(), 0x01.toByte(),

                // SD
                0x81.toByte(), 0xca.toByte(), 0x00.toByte(), 0x0a.toByte(),
                0x23.toByte(), 0x43.toByte(), 0x50.toByte(), 0x1f.toByte(),
                0x01.toByte(), 0x07.toByte(), 0x4d.toByte(), 0x59.toByte(),
                0x43.toByte(), 0x4e.toByte(), 0x41.toByte(), 0x4d.toByte(),
                0x45.toByte(), 0x05.toByte(), 0x08.toByte(), 0x61.toByte(),
                0x17.toByte(), 0x01.toByte(), 0x40.toByte(), 0xff.toByte(),
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x07.toByte(),
                0x07.toByte(), 0xf8.toByte(), 0xfa.toByte(), 0xdf.toByte(),
                0xad.toByte(), 0x60.toByte(), 0x12.toByte(), 0x13.toByte(),
                0x08.toByte(), 0x02.toByte(), 0xdf.toByte(), 0xad.toByte(),
                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()
        )

        // RTCP Sender Report and Source Description is first
        val PACKET_3 = byteArrayOf(
                // SD
                0x81.toByte(), 0xca.toByte(), 0x00.toByte(), 0x0a.toByte(),
                0x23.toByte(), 0x43.toByte(), 0x50.toByte(), 0x1f.toByte(),
                0x01.toByte(), 0x07.toByte(), 0x4d.toByte(), 0x59.toByte(),
                0x43.toByte(), 0x4e.toByte(), 0x41.toByte(), 0x4d.toByte(),
                0x45.toByte(), 0x05.toByte(), 0x08.toByte(), 0x61.toByte(),
                0x17.toByte(), 0x01.toByte(), 0x40.toByte(), 0xff.toByte(),
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x07.toByte(),
                0x07.toByte(), 0xf8.toByte(), 0xfa.toByte(), 0xdf.toByte(),
                0xad.toByte(), 0x60.toByte(), 0x12.toByte(), 0x13.toByte(),
                0x08.toByte(), 0x02.toByte(), 0xdf.toByte(), 0xad.toByte(),
                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),

                // SR
                0x81.toByte(), 0xc8.toByte(), 0x00.toByte(), 0x0c.toByte(),
                0x01.toByte(), 0xa8.toByte(), 0xbd.toByte(), 0xe3.toByte(),
                0xe1.toByte(), 0x37.toByte(), 0x70.toByte(), 0x1e.toByte(),
                0xda.toByte(), 0xc1.toByte(), 0x4c.toByte(), 0x66.toByte(),
                0x00.toByte(), 0x01.toByte(), 0x78.toByte(), 0x40.toByte(),
                0x00.toByte(), 0x00.toByte(), 0x02.toByte(), 0x55.toByte(),
                0x00.toByte(), 0x01.toByte(), 0x75.toByte(), 0x20.toByte(),
                0x00.toByte(), 0x8e.toByte(), 0xb0.toByte(), 0x44.toByte(),
                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
                0x00.toByte(), 0x00.toByte(), 0x2d.toByte(), 0xb3.toByte(),
                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x4a.toByte(),
                0x70.toByte(), 0x1a.toByte(), 0x03.toByte(), 0xd7.toByte(),
                0x00.toByte(), 0x04.toByte(), 0x85.toByte(), 0x1f.toByte()
        )

        // SDP
        val SDP_SESSION = SdpSession().apply {
            callId = "callId_uuid@domain.io"
            clockRate = 8000
            codecBpl = 4.3F
            codecIe = 0F
            id = SESSION_ID
            payloadType = 0
            codecName = "PCMU"
            timestamp = System.currentTimeMillis()
        }
    }

    @Test
    fun `Parse RTCP`() {
        runTest(
                deploy = {
                    // Do nothing...
                },
                execute = {
                    val rtcpHandler = RtcpHandler(vertx, false)

                    vertx.eventBus().publish(RoutesCE.sdp, SDP_SESSION, USE_LOCAL_CODEC)
                    vertx.setTimer(1000L) {
                        listOf(PACKET_1, PACKET_2, PACKET_3).map { payload ->
                            Packet().apply {
                                srcAddr = SRC_ADDR
                                srcPort = SRC_PORT
                                dstAddr = DST_ADDR
                                dstPort = DST_PORT
                                this.payload = ByteBufPayload(Unpooled.wrappedBuffer(payload))
                                timestamp = Timestamp(System.currentTimeMillis())
                            }
                        }.forEach { rtcpHandler.handle(it) }
                    }
                },
                assert = {
                    var packetCount = 0
                    vertx.eventBus().consumer<List<Packet>>(RoutesCE.encoder) { event ->
                        context.verify {
                            val packets = event.body()
                            assertEquals(1, packets.size)
                            packetCount++
                            val packet = packets.first()
                            val report = packet.payload as RtpReportPayload
                            assertEquals(PacketTypes.RTPR, packet.protocolCode)
                            // Assert SDP session data in RTPR
                            assertEquals(SDP_SESSION.callId, report.callId)
                            assertEquals(SDP_SESSION.codecName, report.codecName)
                            assertEquals(SDP_SESSION.payloadType, report.payloadType)

                            when (packetCount) {
                                1 -> {
                                    assertEquals(196, report.expectedPacketCount)
                                    assertEquals(28F, report.lastJitter)
                                    assertEquals(1, report.lostPacketCount)
                                }
                                2 -> {
                                    assertEquals(201, report.expectedPacketCount)
                                    assertEquals(55F, report.lastJitter)
                                    assertEquals(0, report.lostPacketCount)
                                }
                                3 -> {
                                    assertEquals(201, report.expectedPacketCount)
                                    assertEquals(74F, report.lastJitter)
                                    assertEquals(0, report.lostPacketCount)
                                }
                            }
                        }

                        if (packetCount == 3) {
                            context.completeNow()
                        }
                    }
                }
        )
    }
}