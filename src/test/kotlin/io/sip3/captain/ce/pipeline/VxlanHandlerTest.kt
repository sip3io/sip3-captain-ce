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
class VxlanHandlerTest {

    companion object {

        // VXLAN Payload
        val PACKET_1 = byteArrayOf(
            0x08.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xa1.toByte(), 0x49.toByte(), 0x73.toByte(),
            0x00.toByte(), 0x02.toByte(), 0x03.toByte(), 0xe0.toByte(), 0x87.toByte(), 0x1b.toByte(), 0x04.toByte(),
            0x02.toByte(), 0x73.toByte(), 0x95.toByte(), 0xb2.toByte(), 0xab.toByte(), 0xa0.toByte(), 0x08.toByte(),
            0x00.toByte(), 0x45.toByte(), 0x00.toByte(), 0x02.toByte(), 0x48.toByte(), 0x5b.toByte(), 0x5c.toByte(),
            0x40.toByte(), 0x00.toByte(), 0x40.toByte(), 0x11.toByte(), 0x58.toByte(), 0x38.toByte(), 0x0a.toByte(),
            0x66.toByte(), 0x70.toByte(), 0x0a.toByte(), 0x0a.toByte(), 0x66.toByte(), 0x00.toByte(), 0x3b.toByte()
        )
    }

    @Test
    fun `Parse VXLAN packet`() {
        // Init
        mockkConstructor(EthernetHandler::class)
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<EthernetHandler>().handle(capture(packetSlot))
        } just Runs
        // Execute
        val vxlanHandler = VxlanHandler(Vertx.vertx(), JsonObject(), false)
        val packet = Packet().apply {
            this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_1))
        }
        vxlanHandler.handle(packet)
        // Assert
        verify { anyConstructed<EthernetHandler>().handle(any()) }
        val buffer = (packetSlot.captured.payload as Encodable).encode()
        assertEquals(8, buffer.readerIndex())
    }

    @AfterEach
    fun `Unmock all`() {
        unmockkAll()
    }
}
