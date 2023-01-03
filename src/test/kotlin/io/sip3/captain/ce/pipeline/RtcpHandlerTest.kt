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

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.netty.buffer.Unpooled
import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.domain.Packet
import io.sip3.captain.ce.recording.RecordingManager
import io.sip3.commons.PacketTypes
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.domain.payload.RecordingPayload
import io.sip3.commons.vertx.test.VertxTest
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class RtcpHandlerTest : VertxTest() {

    companion object {

        val NOW = System.currentTimeMillis()

        val SRC_ADDR = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
        const val SRC_PORT = 12057
        val DST_ADDR = byteArrayOf(0x0a.toByte(), 0xc5.toByte(), 0x15.toByte(), 0x75.toByte())
        const val DST_PORT = 13057

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
    }

    @Test
    fun `Send REC packet to 'Encoder'`() {
        mockkObject(RecordingManager)
        every {
            RecordingManager.record(any())
        } returns RecordingPayload()
        runTest(
            deploy = {
                // Do nothing...
            },
            execute = {
                val rtcpHandler = RtcpHandler(vertx, JsonObject(), false)

                vertx.setTimer(200L) {
                    val packet = Packet().apply {
                        timestamp = NOW
                        nanos = 42
                        srcAddr = SRC_ADDR
                        srcPort = SRC_PORT
                        dstAddr = DST_ADDR
                        dstPort = DST_PORT
                        this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_1))
                    }

                    rtcpHandler.handle(packet)
                }
            },
            assert = {
                vertx.eventBus().consumer<List<Packet>>(RoutesCE.encoder) { event ->
                    context.verify {
                        val packets = event.body()
                        assertEquals(1, packets.size)

                        with(packets.first()) {
                            assertEquals(NOW, timestamp)
                            assertEquals(42, nanos)
                            assertEquals(SRC_ADDR, srcAddr)
                            assertEquals(SRC_PORT, srcPort)
                            assertEquals(DST_ADDR, dstAddr)
                            assertEquals(DST_PORT, dstPort)
                            assertEquals(PacketTypes.REC, protocolCode)
                            assertTrue(payload is RecordingPayload)
                        }
                    }

                    context.completeNow()
                }
            }
        )
    }

    @Test
    fun `Send RTCP packet to 'Encoder'`() {
        mockkObject(RecordingManager)
        every {
            RecordingManager.record(any())
        } returns null
        runTest(
            deploy = {
                // Do nothing...
            },
            execute = {
                val rtcpHandler = RtcpHandler(vertx, JsonObject(), false)

                vertx.setTimer(200L) {
                    val packet = Packet().apply {
                        timestamp = NOW
                        nanos = 42
                        srcAddr = SRC_ADDR
                        srcPort = SRC_PORT
                        dstAddr = DST_ADDR
                        dstPort = DST_PORT
                        this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_1))
                    }

                    rtcpHandler.handle(packet)
                }
            },
            assert = {
                vertx.eventBus().consumer<List<Packet>>(RoutesCE.encoder) { event ->
                    context.verify {
                        val packets = event.body()
                        assertEquals(1, packets.size)

                        with(packets.first()) {
                            assertEquals(NOW, timestamp)
                            assertEquals(42, nanos)
                            assertEquals(SRC_ADDR, srcAddr)
                            assertEquals(SRC_PORT, srcPort)
                            assertEquals(DST_ADDR, dstAddr)
                            assertEquals(DST_PORT, dstPort)
                            assertEquals(PacketTypes.RTCP, protocolCode)
                            assertArrayEquals(PACKET_1, (payload as Encodable).encode().array())
                        }
                    }

                    context.completeNow()
                }
            }
        )
    }

    @AfterEach
    fun `Unmock all`() {
        unmockkAll()
    }
}