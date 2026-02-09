/*
 * Copyright 2018-2026 SIP3.IO, Corp.
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

import io.mockk.*
import io.mockk.junit5.MockKExtension
import io.netty.buffer.Unpooled
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.domain.payload.Encodable
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class UdpHandlerTest {

    companion object {

        // Payload: SIP
        val PACKET_1 = byteArrayOf(
            0x33.toByte(), 0x40.toByte(), 0xdf.toByte(), 0x98.toByte(), 0x00.toByte(), 0xb4.toByte(), 0x31.toByte(),
            0x4e.toByte(), 0x53.toByte(), 0x49.toByte(), 0x50.toByte(), 0x2f.toByte(), 0x32.toByte(), 0x2e.toByte(),
            0x30.toByte(), 0x20.toByte(), 0x31.toByte(), 0x30.toByte(), 0x30.toByte(), 0x20.toByte(), 0x54.toByte(),
            0x72.toByte(), 0x79.toByte(), 0x69.toByte()
        )

        // Payload: RTP
        val PACKET_2 = byteArrayOf(
            0x33.toByte(), 0x40.toByte(), 0xdf.toByte(), 0x98.toByte(), 0x00.toByte(), 0xb4.toByte(), 0x31.toByte(),
            0x4e.toByte(), 0x80.toByte(), 0x88.toByte(), 0x95.toByte(), 0x91.toByte(), 0xfd.toByte(), 0xe6.toByte(),
            0xf6.toByte(), 0xf6.toByte(), 0x4a.toByte(), 0xbf.toByte(), 0x30.toByte(), 0x13.toByte(), 0xd5.toByte(),
            0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte()
        )

        // Payload: RTCP
        val PACKET_3 = byteArrayOf(
            0x33.toByte(), 0x40.toByte(), 0xdf.toByte(), 0x98.toByte(), 0x00.toByte(), 0xb4.toByte(), 0x31.toByte(),
            0x4e.toByte(), 0x81.toByte(), 0xc8.toByte(), 0x00.toByte(), 0x0c.toByte(), 0x59.toByte(), 0xdb.toByte(),
            0xe3.toByte(), 0x0c.toByte(), 0x5c.toByte(), 0xac.toByte(), 0xde.toByte(), 0x40.toByte(), 0x00.toByte(),
            0x0d.toByte(), 0xe7.toByte(), 0xb1.toByte()
        )

        // Payload: TZSP
        val PACKET_4 = byteArrayOf(
            0x33.toByte(), 0x40.toByte(), 0xdf.toByte(), 0x98.toByte(), 0x00.toByte(), 0xb4.toByte(), 0x31.toByte(),
            0x4e.toByte(), 0x01.toByte(), 0x00.toByte(), 0x01.toByte(), 0x01.toByte(), 0x33.toByte(), 0x40.toByte(),
            0xdf.toByte(), 0x98.toByte(), 0x00.toByte(), 0xb4.toByte(), 0x31.toByte(), 0x4e.toByte()
        )

        // Payload: VXLAN
        val PACKET_5 = byteArrayOf(
            0xff.toByte(), 0xfc.toByte(), 0x12.toByte(), 0xb5.toByte(), 0x02.toByte(), 0x66.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x08.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xa1.toByte(), 0x49.toByte(),
            0x73.toByte(), 0x00.toByte(), 0x02.toByte(), 0x03.toByte(), 0xe0.toByte(), 0x87.toByte(), 0x1b.toByte(),
            0x04.toByte(), 0x02.toByte(), 0x73.toByte(), 0x95.toByte(), 0xb2.toByte(), 0xab.toByte(), 0xa0.toByte(),
            0x08.toByte(), 0x00.toByte()
        )

    }

    @Test
    fun `Parse UDP - SIP`() {
        // Init
        mockkConstructor(SipHandler::class)
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<SipHandler>().handle(capture(packetSlot))
        } just Runs
        // Execute
        val udpHandler = UdpHandler(Vertx.vertx(), JsonObject(), false)
        var packet = Packet().apply {
            this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_1))
        }
        udpHandler.handle(packet)
        // Assert
        verify { anyConstructed<SipHandler>().handle(any()) }
        packet = packetSlot.captured
        val buffer = (packet.payload as Encodable).encode()
        assertEquals(13120, packet.srcPort)
        assertEquals(57240, packet.dstPort)
        assertEquals(16, buffer.readableBytes())
    }

    @Test
    fun `Parse UDP - RTP`() {
        // Init
        mockkConstructor(RtpHandler::class)
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<RtpHandler>().handle(capture(packetSlot))
        } just Runs

        // Execute
        val udpHandler = UdpHandler(Vertx.vertx(), JsonObject().apply {
            put("rtp", JsonObject().apply {
                put("enabled", true)
            })
        }, false)
        var packet = Packet().apply {
            this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_2))
        }
        udpHandler.handle(packet)
        // Assert
        verify { anyConstructed<RtpHandler>().handle(any()) }
        packet = packetSlot.captured
        val buffer = (packet.payload as Encodable).encode()
        assertEquals(13120, packet.srcPort)
        assertEquals(57240, packet.dstPort)
        assertEquals(16, buffer.readableBytes())
    }

    @Test
    fun `Parse UDP - RTCP`() {
        // Init
        mockkConstructor(RtcpHandler::class)
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<RtcpHandler>().handle(capture(packetSlot))
        } just Runs

        // Execute
        val udpHandler = UdpHandler(Vertx.vertx(), JsonObject().apply {
            put("rtcp", JsonObject().apply {
                put("enabled", true)
            })
        }, false)
        var packet = Packet().apply {
            this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_3))
        }
        udpHandler.handle(packet)
        // Assert
        verify { anyConstructed<RtcpHandler>().handle(any()) }
        packet = packetSlot.captured
        val buffer = (packet.payload as Encodable).encode()
        assertEquals(13120, packet.srcPort)
        assertEquals(57240, packet.dstPort)
        assertEquals(16, buffer.readableBytes())
    }

    @Test
    fun `Route UDP - rejected RTP`() {
        // Init
        mockkConstructor(RtpHandler::class)
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<RtpHandler>().handle(capture(packetSlot))
        } just Runs

        // Execute
        val udpHandler = UdpHandler(Vertx.vertx(), JsonObject().apply {
            put("rtp", JsonObject().apply {
                put("enabled", true)
            })
        }, false)
        var packet = Packet().apply {
            this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_2))
            this.rejected = Packet()
        }
        udpHandler.handle(packet)
        // Assert
        verify { anyConstructed<RtpHandler>().handle(any()) }
        packet = packetSlot.captured
        val buffer = (packet.payload as Encodable).encode()
        assertEquals(13120, packet.srcPort)
        assertEquals(57240, packet.dstPort)
        assertEquals(16, buffer.readableBytes())
    }

    @Test
    fun `Parse UDP - TZSP`() {
        // Init
        mockkConstructor(TzspHandler::class)
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<TzspHandler>().handle(capture(packetSlot))
        } just Runs

        // Execute
        val udpHandler = UdpHandler(Vertx.vertx(), JsonObject().apply {
            put("tzsp", JsonObject().apply {
                put("enabled", true)
            })
        }, false)
        var packet = Packet().apply {
            this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_4))
        }
        udpHandler.handle(packet)
        // Assert
        verify { anyConstructed<TzspHandler>().handle(any()) }
        packet = packetSlot.captured
        val buffer = (packet.payload as Encodable).encode()
        assertEquals(13120, packet.srcPort)
        assertEquals(57240, packet.dstPort)
        assertEquals(12, buffer.readableBytes())
    }

    @Test
    fun `Parse UDP - VXLAN`() {
        // Init
        mockkConstructor(VxlanHandler::class)
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<VxlanHandler>().handle(capture(packetSlot))
        } just Runs

        // Execute
        val udpHandler = UdpHandler(Vertx.vertx(), JsonObject().apply {
            put("vxlan", JsonObject().apply {
                put("enabled", true)
            })
        }, false)
        var packet = Packet().apply {
            this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_5))
        }
        udpHandler.handle(packet)
        // Assert
        verify { anyConstructed<VxlanHandler>().handle(any()) }
        packet = packetSlot.captured
        val buffer = (packet.payload as Encodable).encode()
        assertEquals(65532, packet.srcPort)
        assertEquals(4789, packet.dstPort)
        assertEquals(22, buffer.readableBytes())
    }

    @AfterEach
    fun `Unmock all`() {
        unmockkAll()
    }
}
