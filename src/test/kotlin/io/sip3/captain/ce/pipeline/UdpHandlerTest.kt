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

import io.mockk.*
import io.mockk.junit5.MockKExtension
import io.netty.buffer.Unpooled
import io.sip3.captain.ce.domain.ByteBufPayload
import io.sip3.captain.ce.domain.Packet
import io.vertx.core.Vertx
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class UdpHandlerTest {

    companion object {

        // Payload: UDP
        val PACKET_1 = byteArrayOf(
                0x33.toByte(), 0x40.toByte(), 0xdf.toByte(), 0x98.toByte(), 0x00.toByte(), 0xb4.toByte(), 0x31.toByte(),
                0x4e.toByte(), 0x80.toByte(), 0x08.toByte(), 0x00.toByte(), 0xb3.toByte(), 0x00.toByte(), 0x00.toByte(),
                0xf1.toByte(), 0x40.toByte(), 0xa0.toByte(), 0x40.toByte(), 0xb6.toByte(), 0x15.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte(), 0xd5.toByte()
        )
    }

    @Test
    fun `Parse UDP`() {
        // Init
        mockkConstructor(RouterHandler::class)
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<RouterHandler>().handle(capture(packetSlot))
        } just Runs
        // Execute
        val udpHandler = UdpHandler(Vertx.vertx(), false)
        var packet = Packet().apply {
            this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_1))
        }
        udpHandler.handle(packet)
        // Assert
        verify { anyConstructed<RouterHandler>().handle(any()) }
        packet = packetSlot.captured
        val buffer = packet.payload.encode()
        assertEquals(13120, packet.srcPort)
        assertEquals(57240, packet.dstPort)
        val payloadLength = buffer.capacity() - buffer.readerIndex()
        assertEquals(172, payloadLength)
    }

    @AfterEach
    fun `Unmock all`() {
        unmockkAll()
    }
}