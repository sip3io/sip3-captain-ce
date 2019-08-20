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
class TcpHandlerTest {

    companion object {

        // Payload: TCP
        val PACKET_1 = byteArrayOf(
                0xbc.toByte(), 0xdc.toByte(), 0x13.toByte(), 0xc4.toByte(), 0x05.toByte(), 0x73.toByte(), 0x86.toByte(),
                0xff.toByte(), 0xa9.toByte(), 0x5b.toByte(), 0xa4.toByte(), 0x02.toByte(), 0x80.toByte(), 0x10.toByte(),
                0x05.toByte(), 0x90.toByte(), 0x63.toByte(), 0xfb.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
                0x01.toByte(), 0x08.toByte(), 0x0a.toByte(), 0x72.toByte(), 0xec.toByte(), 0x85.toByte(), 0x8e.toByte(),
                0x06.toByte(), 0xe1.toByte(), 0xdc.toByte(), 0x87.toByte()
        )
    }

    @Test
    fun `Parse TCP`() {
        // Init
        mockkConstructor(RouterHandler::class)
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<RouterHandler>().handle(capture(packetSlot))
        } just Runs
        // Execute
        val tcpHandler = TcpHandler(Vertx.vertx(), false)
        var packet = Packet().apply {
            this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_1))
        }
        tcpHandler.handle(packet)
        // Assert
        verify { anyConstructed<RouterHandler>().handle(any()) }
        packet = packetSlot.captured
        val buffer = packet.payload.encode()
        assertEquals(48348, packet.srcPort)
        assertEquals(5060, packet.dstPort)
        val payloadLength = buffer.capacity() - buffer.readerIndex()
        assertEquals(0, payloadLength)
    }

    @AfterEach
    fun `Unmock all`() {
        unmockkAll()
    }
}