/*
 * Copyright 2018-2022 SIP3.IO, Corp.
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
class TzspHandlerTest {

    companion object {

        // TZSP Payload: Ethernet without tagged fields
        val PACKET_1 = byteArrayOf(
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x01.toByte(), 0x4b.toByte(), 0x00.toByte(),
            0x08.toByte(), 0x25.toByte(), 0x20.toByte(), 0x1a.toByte(), 0xe0.toByte(), 0x08.toByte(), 0x00.toByte()
        )

        // TZSP Payload: Ethernet with tagged fields
        val PACKET_2 = byteArrayOf(
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x02.toByte(),
            0x05.toByte(), 0x5e.toByte(), 0x00.toByte(), 0x01.toByte(), 0x4b.toByte(), 0x02.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x5e.toByte(), 0x00.toByte(), 0x01.toByte(), 0x4b.toByte(), 0x00.toByte(),
            0x08.toByte(), 0x25.toByte(), 0x20.toByte(), 0x1a.toByte(), 0xe0.toByte(), 0x81.toByte(), 0x00.toByte(),
            0x01.toByte(), 0x55.toByte(), 0x08.toByte(), 0x00.toByte()
        )
    }

    @Test
    fun `Parse TZSP - Ethernet without tagged fields`() {
        // Init
        mockkConstructor(EthernetHandler::class)
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<EthernetHandler>().handle(capture(packetSlot))
        } just Runs
        // Execute
        val tzspHandler = TzspHandler(Vertx.vertx(), JsonObject(), false)
        val packet = Packet().apply {
            this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_1))
        }
        tzspHandler.handle(packet)
        // Assert
        verify { anyConstructed<EthernetHandler>().handle(any()) }
        val buffer = (packetSlot.captured.payload as Encodable).encode()
        assertEquals(5, buffer.readerIndex())
    }

    @Test
    fun `Parse TZSP - Ethernet with tagged fields`() {
        // Init
        mockkConstructor(EthernetHandler::class)
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<EthernetHandler>().handle(capture(packetSlot))
        } just Runs
        // Execute
        val tzspHandler = TzspHandler(Vertx.vertx(), JsonObject(), false)
        val packet = Packet().apply {
            this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_2))
        }
        tzspHandler.handle(packet)
        // Assert
        verify { anyConstructed<EthernetHandler>().handle(any()) }
        val buffer = (packetSlot.captured.payload as Encodable).encode()
        assertEquals(14, buffer.readerIndex())
    }

    @AfterEach
    fun `Unmock all`() {
        unmockkAll()
    }
}
