/*
 * Copyright 2018-2021 SIP3.IO, Inc.
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class EthernetHandlerTest {

    companion object {

        // Header: Ethernet II
        val PACKET_1 = byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x5e.toByte(), 0x00.toByte(), 0x01.toByte(), 0x4b.toByte(), 0x00.toByte(),
            0x08.toByte(), 0x25.toByte(), 0x20.toByte(), 0x1a.toByte(), 0xe0.toByte(), 0x08.toByte(), 0x00.toByte()
        )

        // Header: Ethernet 802-1Q
        val PACKET_2 = byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x5e.toByte(), 0x00.toByte(), 0x01.toByte(), 0x4b.toByte(), 0x00.toByte(),
            0x08.toByte(), 0x25.toByte(), 0x20.toByte(), 0x1a.toByte(), 0xe0.toByte(), 0x81.toByte(), 0x00.toByte(),
            0x01.toByte(), 0x55.toByte(), 0x08.toByte(), 0x00.toByte()
        )

        // Header: Ethernet 802-1AD
        val PACKET_3 = byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x5e.toByte(), 0x00.toByte(), 0x01.toByte(), 0x4b.toByte(), 0x00.toByte(),
            0x08.toByte(), 0x25.toByte(), 0x20.toByte(), 0x1a.toByte(), 0xe0.toByte(), 0x88.toByte(), 0xa8.toByte(),
            0x01.toByte(), 0x55.toByte(), 0x81.toByte(), 0x00.toByte(), 0x01.toByte(), 0x55.toByte(), 0x08.toByte(),
            0x00.toByte()
        )

        // Header: Ethernet LinuxCookedCapture
        val PACKET_4 = byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x03.toByte(), 0x0a.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x08.toByte(), 0x00.toByte()
        )
    }

    @Test
    fun `Parse Ethernet - IPv4`() {
        // Init
        mockkConstructor(Ipv4Handler::class) {
            val packetSlot = slot<Packet>()
            every {
                anyConstructed<Ipv4Handler>().handle(capture(packetSlot))
            } just Runs
            // Execute
            val ethernetHandler = EthernetHandler(Vertx.vertx().orCreateContext, false)
            val packet = Packet().apply {
                this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_1))
            }
            ethernetHandler.handle(packet)
            // Assert
            verify(timeout = 10000) { anyConstructed<Ipv4Handler>().handle(any()) }
            val buffer = (packetSlot.captured.payload as Encodable).encode()
            assertEquals(14, buffer.readerIndex())
        }
    }

    @Test
    fun `Parse 802-1Q - IPv4`() {
        // Init
        mockkConstructor(Ipv4Handler::class) {
            val packetSlot = slot<Packet>()
            every {
                anyConstructed<Ipv4Handler>().handle(capture(packetSlot))
            } just Runs
            // Execute
            val ethernetHandler = EthernetHandler(Vertx.vertx().orCreateContext, false)
            val packet = Packet().apply {
                this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_2))
            }
            ethernetHandler.handle(packet)
            // Assert
            verify(timeout = 10000) { anyConstructed<Ipv4Handler>().handle(any()) }
            val buffer = (packetSlot.captured.payload as Encodable).encode()
            assertEquals(18, buffer.readerIndex())
        }
    }

    @Test
    fun `Parse 802-1AD - IPv4`() {
        // Init
        mockkConstructor(Ipv4Handler::class) {
            val packetSlot = slot<Packet>()
            every {
                anyConstructed<Ipv4Handler>().handle(capture(packetSlot))
            } just Runs
            // Execute
            val ethernetHandler = EthernetHandler(Vertx.vertx().orCreateContext, false)
            val packet = Packet().apply {
                this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_3))
            }
            ethernetHandler.handle(packet)
            // Assert
            verify(timeout = 10000) { anyConstructed<Ipv4Handler>().handle(any()) }
            val buffer = (packetSlot.captured.payload as Encodable).encode()
            assertEquals(22, buffer.readerIndex())
        }
    }

    @Test
    fun `Parse LinuxCookedCapture - IPv4`() {
        // Init
        mockkConstructor(Ipv4Handler::class) {
            val packetSlot = slot<Packet>()
            every {
                anyConstructed<Ipv4Handler>().handle(capture(packetSlot))
            } just Runs
            // Execute
            val ethernetHandler = EthernetHandler(Vertx.vertx().orCreateContext, false)
            val packet = Packet().apply {
                this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_4))
            }
            ethernetHandler.handle(packet)
            // Assert
            verify(timeout = 10000) { anyConstructed<Ipv4Handler>().handle(any()) }
            val buffer = (packetSlot.captured.payload as Encodable).encode()
            assertEquals(16, buffer.readerIndex())
        }
    }

    @AfterEach
    fun `Unmock all`() {
        unmockkAll()
    }
}