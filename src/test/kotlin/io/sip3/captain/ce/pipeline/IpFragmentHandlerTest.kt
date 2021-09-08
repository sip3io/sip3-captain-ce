/*
 * Copyright 2018-2021 SIP3.IO, Corp.
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
import io.sip3.commons.util.getBytes
import io.sip3.commons.vertx.test.VertxTest
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class IpFragmentHandlerTest : VertxTest() {

    companion object {

        // Payload: IPv4 (Fragment 1/2)
        val PACKET_1 = byteArrayOf(
            0x45.toByte(), 0xa0.toByte(), 0x00.toByte(), 0x1c.toByte(), 0xe8.toByte(), 0xdd.toByte(), 0x20.toByte(),
            0x00.toByte(), 0x3c.toByte(), 0x11.toByte(), 0x75.toByte(), 0x6e.toByte(), 0x0a.toByte(), 0xfa.toByte(),
            0xf4.toByte(), 0x05.toByte(), 0x0a.toByte(), 0xc5.toByte(), 0x15.toByte(), 0x75.toByte(), 0x32.toByte(),
            0x40.toByte(), 0xe8.toByte(), 0x3c.toByte(), 0x00.toByte(), 0xb4.toByte(), 0x13.toByte(), 0x0b.toByte(),
            0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte()
        )

        // Payload: IPv4 (Fragment 2/2)
        val PACKET_2 = byteArrayOf(
            0x45.toByte(), 0xa0.toByte(), 0x00.toByte(), 0x1c.toByte(), 0xe8.toByte(), 0xdd.toByte(), 0x00.toByte(),
            0x01.toByte(), 0x3c.toByte(), 0x11.toByte(), 0x75.toByte(), 0x6e.toByte(), 0x0a.toByte(), 0xfa.toByte(),
            0xf4.toByte(), 0x05.toByte(), 0x0a.toByte(), 0xc5.toByte(), 0x15.toByte(), 0x75.toByte(), 0x32.toByte(),
            0x40.toByte(), 0xe8.toByte(), 0x3c.toByte(), 0x00.toByte(), 0xb4.toByte(), 0x13.toByte(), 0x0b.toByte(),
            0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte()
        )
    }

    @Test
    fun `Assemble 2 fragments sent in order`() {
        val expectedTimestamp = System.currentTimeMillis() - 10000
        val expectedNanos = 42

        val packetSlot = slot<Packet>()
        mockkConstructor(Ipv4Handler::class)
        every {
            anyConstructed<Ipv4Handler>().routePacket(capture(packetSlot))
        } just Runs
        runTest(
            deploy = {
                vertx.deployTestVerticle(IpFragmentHandler::class)
            },
            execute = {
                val ipv4Handler = Ipv4Handler(vertx, JsonObject(), false)
                val packet1 = Packet().apply {
                    timestamp = expectedTimestamp
                    nanos = 42
                    payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_1))
                }
                ipv4Handler.handle(packet1)
                val packet2 = Packet().apply {
                    timestamp = System.currentTimeMillis()
                    nanos = 24
                    payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_2))
                }
                ipv4Handler.handle(packet2)
            },
            assert = {
                vertx.executeBlocking<Any>({
                    context.verify {
                        verify(timeout = 10000) { anyConstructed<Ipv4Handler>().routePacket(any()) }
                        val packet = packetSlot.captured
                        assertEquals(expectedTimestamp, packet.timestamp)
                        assertEquals(expectedNanos, packet.nanos)
                        assertEquals(Ipv4Handler.TYPE_UDP, packet.protocolNumber)
                        val buffer = (packet.payload as Encodable).encode()
                        assertEquals(16, buffer.writerIndex())
                        assertFalse(buffer.getBytes().contains(0xfe.toByte()))
                    }
                    context.completeNow()
                }, {})
            }
        )
    }

    @Test
    fun `Assemble 2 fragments sent not in order`() {
        val expectedTimestamp = System.currentTimeMillis() - 10000
        val expectedNanos = 42

        val packetSlot = slot<Packet>()
        mockkConstructor(Ipv4Handler::class)
        every {
            anyConstructed<Ipv4Handler>().routePacket(capture(packetSlot))
        } just Runs
        runTest(
            deploy = {
                vertx.deployTestVerticle(IpFragmentHandler::class)
            },
            execute = {
                val ipv4Handler = Ipv4Handler(vertx, JsonObject(), false)
                val packet2 = Packet().apply {
                    timestamp = expectedTimestamp
                    nanos = expectedNanos
                    payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_2))
                }
                ipv4Handler.handle(packet2)
                val packet1 = Packet().apply {
                    timestamp = System.currentTimeMillis()
                    nanos = 24
                    payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_1))
                }
                ipv4Handler.handle(packet1)
            },
            assert = {
                vertx.executeBlocking<Any>({
                    context.verify {
                        verify(timeout = 10000) { anyConstructed<Ipv4Handler>().routePacket(any()) }
                        val packet = packetSlot.captured
                        assertEquals(expectedTimestamp, packet.timestamp)
                        assertEquals(expectedNanos, packet.nanos)
                        assertEquals(Ipv4Handler.TYPE_UDP, packet.protocolNumber)
                        val buffer = (packet.payload as Encodable).encode()
                        assertEquals(16, buffer.writerIndex())
                        assertFalse(buffer.getBytes().contains(0xfe.toByte()))
                    }
                    context.completeNow()
                }, {})
            }
        )
    }

    @AfterEach
    fun `Unmock all`() {
        unmockkAll()
    }
}