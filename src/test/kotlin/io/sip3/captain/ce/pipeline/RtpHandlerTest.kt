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
import io.sip3.captain.ce.Routes
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.PacketTypes
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.domain.payload.RtpHeaderPayload
import io.sip3.commons.vertx.test.VertxTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RtpHandlerTest : VertxTest() {

    companion object {

        // Payload: RTP with marker bit = 0
        val PACKET_1 = byteArrayOf(
                0x80.toByte(), 0x08.toByte(), 0x01.toByte(), 0xdd.toByte(), 0x2b.toByte(), 0x76.toByte(), 0x37.toByte(),
                0x40.toByte(), 0x95.toByte(), 0x06.toByte(), 0xb9.toByte(), 0x73.toByte()
        )

        // Payload: RTP with marker bit = 1
        val PACKET_2 = byteArrayOf(
                0x80.toByte(), 0x88.toByte(), 0xd5.toByte(), 0x66.toByte(), 0x0a.toByte(), 0xdd.toByte(), 0xb8.toByte(),
                0xa0.toByte(), 0x7c.toByte(), 0x50.toByte(), 0xe6.toByte(), 0xef.toByte()
        )
    }

    @Test
    fun `Parse RTP`() {
        runTest(
                deploy = {
                    // Do nothing...
                },
                execute = {
                    val rtpHandler = RtpHandler(vertx, false)
                    val packet = Packet().apply {
                        this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_1))
                    }
                    rtpHandler.handle(packet)
                },
                assert = {
                    vertx.eventBus().consumer<List<Packet>>(Routes.rtp) { event ->
                        val packets = event.body()
                        context.verify {
                            assertEquals(1, packets.size)

                            val packet = packets[0]
                            assertEquals(PacketTypes.RTP, packet.protocolCode)
                            assertTrue(packet.payload is RtpHeaderPayload)

                            val payload = packet.payload as RtpHeaderPayload
                            assertEquals(22, payload.encode().capacity())
                            assertEquals(8, payload.payloadType)
                            assertEquals(477, payload.sequenceNumber)
                            assertEquals(729167680, payload.timestamp)
                            assertEquals(2500245875, payload.ssrc)
                            assertEquals(false, payload.marker)
                        }
                        context.completeNow()
                    }
                }
        )
    }

    @Test
    fun `Parse RTP with marker bit = 1`() {
        runTest(
                deploy = {
                    // Do nothing...
                },
                execute = {
                    val rtpHandler = RtpHandler(vertx, false)
                    val packet = Packet().apply {
                        this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_2))
                    }
                    rtpHandler.handle(packet)
                },
                assert = {
                    vertx.eventBus().consumer<List<Packet>>(Routes.rtp) { event ->
                        val packets = event.body()
                        context.verify {
                            assertEquals(1, packets.size)

                            val packet = packets[0]
                            assertEquals(PacketTypes.RTP, packet.protocolCode)
                            assertTrue(packet.payload is RtpHeaderPayload)

                            val payload = packet.payload as RtpHeaderPayload
                            assertEquals(22, payload.encode().capacity())
                            assertEquals(8, payload.payloadType)
                            assertEquals(54630, payload.sequenceNumber)
                            assertEquals(182302880, payload.timestamp)
                            assertEquals(2085676783, payload.ssrc)
                            assertEquals(true, payload.marker)
                        }
                        context.completeNow()
                    }
                }
        )
    }
}