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

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.netty.buffer.Unpooled
import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.domain.Packet
import io.sip3.captain.ce.recording.RecordingManager
import io.sip3.commons.ProtocolCodes
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.domain.payload.RecordingPayload
import io.sip3.commons.domain.payload.RtpPacketPayload
import io.sip3.commons.vertx.test.VertxTest
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class RtpHandlerTest : VertxTest() {

    companion object {

        val NOW = System.currentTimeMillis()

        val SRC_ADDR = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
        const val SRC_PORT = 12057
        val DST_ADDR = byteArrayOf(0x0a.toByte(), 0xc5.toByte(), 0x15.toByte(), 0x75.toByte())
        const val DST_PORT = 13057

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

        // Payload: RTP payload type = 0
        val PACKET_3 = byteArrayOf(
            0x80.toByte(), 0x00.toByte(), 0x01.toByte(), 0xdd.toByte(), 0x2b.toByte(), 0x76.toByte(), 0x37.toByte(),
            0x40.toByte(), 0x95.toByte(), 0x06.toByte(), 0xb9.toByte(), 0x73.toByte()
        )

        // Payload: RTP Event payload type = 96
        val PACKET_4 = byteArrayOf(
            0x80.toByte(), 0xe5.toByte(), 0x5b.toByte(), 0x86.toByte(), 0xfa.toByte(), 0xf7.toByte(), 0xa9.toByte(),
            0xdd.toByte(), 0x67.toByte(), 0x71.toByte(), 0x10.toByte(), 0x61.toByte(), 0x03.toByte(), 0x0a.toByte(),
            0x00.toByte(), 0xa0.toByte()
        )
    }

    @Test
    fun `Parse RTP`() {
        mockkObject(RecordingManager)
        every {
            RecordingManager.record(any())
        } returns null

        runTest(
            execute = {
                val rtpHandler = RtpHandler(vertx, JsonObject().apply {
                    put("rtp", JsonObject().apply {
                        put("collector", JsonObject().apply {
                            put("enabled", true)
                        })
                    })
                }, false)
                val packet = Packet().apply {
                    timestamp = NOW
                    srcAddr = SRC_ADDR
                    srcPort = SRC_PORT
                    dstAddr = DST_ADDR
                    dstPort = DST_PORT
                    this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_1))
                }
                rtpHandler.handle(packet)
            },
            assert = {
                vertx.eventBus().consumer<List<Packet>>(RoutesCE.rtp + "_0") { event ->
                    val packets = event.body()
                    context.verify {
                        assertEquals(1, packets.size)

                        val packet = packets[0]
                        assertEquals(ProtocolCodes.RTP, packet.protocolCode)
                        assertTrue(packet.payload is RtpPacketPayload)

                        val payload = packet.payload as RtpPacketPayload
                        assertEquals(27, payload.encode().writerIndex())
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
        mockkObject(RecordingManager)
        every {
            RecordingManager.record(any())
        } returns null

        runTest(
            execute = {
                val rtpHandler = RtpHandler(vertx, JsonObject().apply {
                    put("rtp", JsonObject().apply {
                        put("collector", JsonObject().apply {
                            put("enabled", true)
                        })
                    })
                }, false)
                val packet = Packet().apply {
                    timestamp = NOW
                    srcAddr = SRC_ADDR
                    srcPort = SRC_PORT
                    dstAddr = DST_ADDR
                    dstPort = DST_PORT
                    this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_2))
                }
                rtpHandler.handle(packet)
            },
            assert = {
                vertx.eventBus().consumer<List<Packet>>(RoutesCE.rtp + "_0") { event ->
                    val packets = event.body()
                    context.verify {
                        assertEquals(1, packets.size)

                        val packet = packets[0]
                        assertEquals(ProtocolCodes.RTP, packet.protocolCode)
                        assertTrue(packet.payload is RtpPacketPayload)

                        val payload = packet.payload as RtpPacketPayload
                        assertEquals(27, payload.encode().writerIndex())
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

    @Test
    fun `Filter RTP by payload type`() {
        mockkObject(RecordingManager)
        every {
            RecordingManager.record(any())
        } returns null
        runTest(
            execute = {
                val rtpHandler = RtpHandler(vertx, JsonObject().apply {
                    put("rtp", JsonObject().apply {
                        put("payload_types", listOf("0..7", 100))
                        put("collector", JsonObject().apply {
                            put("enabled", true)
                        })
                    })
                }, false)
                listOf(PACKET_1, PACKET_2, PACKET_3).forEach { payload ->
                    val packet = Packet().apply {
                        timestamp = NOW
                        srcAddr = SRC_ADDR
                        srcPort = SRC_PORT
                        dstAddr = DST_ADDR
                        dstPort = DST_PORT
                        this.payload = ByteBufPayload(Unpooled.wrappedBuffer(payload))
                    }
                    rtpHandler.handle(packet)
                }
            },
            assert = {
                vertx.eventBus().consumer<List<Packet>>(RoutesCE.rtp + "_0") { event ->
                    val packets = event.body()
                    context.verify {
                        assertEquals(1, packets.size)

                        val packet = packets[0]
                        assertEquals(ProtocolCodes.RTP, packet.protocolCode)
                        assertTrue(packet.payload is RtpPacketPayload)

                        val payload = packet.payload as RtpPacketPayload
                        assertEquals(27, payload.encode().writerIndex())
                        assertEquals(0, payload.payloadType)
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
    fun `Filter RTP by minimal port`() {
        mockkObject(RecordingManager)
        every {
            RecordingManager.record(any())
        } returns null
        runTest(
            execute = {
                val rtpHandler = RtpHandler(vertx, JsonObject().apply {
                    put("rtp", JsonObject().apply {
                        put("port_ranges", JsonArray.of("54..$DST_PORT"))
                        put("collector", JsonObject().apply {
                            put("enabled", true)
                        })
                    })
                }, false)
                listOf(PACKET_1, PACKET_2, PACKET_3).mapIndexed { i, payload ->
                    val packet = Packet().apply {
                        timestamp = NOW
                        srcAddr = SRC_ADDR
                        srcPort = if (i < 2) 53 else SRC_PORT
                        dstAddr = DST_ADDR
                        dstPort = DST_PORT
                        this.payload = ByteBufPayload(Unpooled.wrappedBuffer(payload))
                    }

                    rtpHandler.handle(packet)
                }
            },
            assert = {
                vertx.eventBus().consumer<List<Packet>>(RoutesCE.rtp + "_0") { event ->
                    val packets = event.body()
                    context.verify {
                        assertEquals(1, packets.size)

                        val packet = packets[0]
                        assertEquals(ProtocolCodes.RTP, packet.protocolCode)
                        assertTrue(packet.payload is RtpPacketPayload)

                        val payload = packet.payload as RtpPacketPayload
                        assertEquals(27, payload.encode().writerIndex())
                        assertEquals(0, payload.payloadType)
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
    fun `Save RTP Event payload`() {
        mockkObject(RecordingManager)
        every {
            RecordingManager.record(any())
        } returns null

        runTest(
            execute = {
                val rtpHandler = RtpHandler(vertx, JsonObject().apply {
                    put("rtp", JsonObject().apply {
                        put("events", JsonObject().apply {
                            put("enabled", true)
                        })
                        put("collector", JsonObject().apply {
                            put("enabled", true)
                        })
                    })
                }, false)
                val packet = Packet().apply {
                    timestamp = NOW
                    srcAddr = SRC_ADDR
                    srcPort = SRC_PORT
                    dstAddr = DST_ADDR
                    dstPort = DST_PORT
                    this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_4))
                }
                rtpHandler.handle(packet)
            },
            assert = {

                vertx.eventBus().consumer<List<Packet>>(RoutesCE.rtp + "_0") { event ->
                    val packets = event.body()
                    context.verify {
                        assertEquals(1, packets.size)

                        val packet = packets[0]
                        assertEquals(ProtocolCodes.RTP, packet.protocolCode)
                        assertTrue(packet.payload is RtpPacketPayload)

                        val payload = packet.payload as RtpPacketPayload
                        assertEquals(27, payload.encode().writerIndex())
                        assertEquals(101, payload.payloadType)
                        assertEquals(23430, payload.sequenceNumber)
                        assertEquals(4210534877, payload.timestamp)
                        assertEquals(1735463009, payload.ssrc)
                        assertEquals(50987168, payload.event)
                        assertEquals(true, payload.marker)
                    }
                    context.completeNow()
                }
            }
        )
    }

    @Test
    fun `Record RTP packet`() {
        mockkObject(RecordingManager)
        every {
            RecordingManager.record(any())
        } returns RecordingPayload()
        runTest(
            deploy = {
                // Do nothing...
            },
            execute = {
                val rtpHandler = RtpHandler(vertx, JsonObject(), false)

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

                    rtpHandler.handle(packet)
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
                            assertEquals(ProtocolCodes.REC, protocolCode)
                            assertTrue(payload is RecordingPayload)
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
