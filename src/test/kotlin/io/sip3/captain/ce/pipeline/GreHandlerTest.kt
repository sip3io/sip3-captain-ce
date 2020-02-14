/*
 * Copyright 2018-2020 SIP3.IO, Inc.
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
import io.netty.buffer.Unpooled
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.util.remainingCapacity
import io.vertx.core.Vertx
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GreHandlerTest {

    companion object {

        // GRE (without optional Sequence Number) Payload: ERSPAN
        val PACKET_1 = byteArrayOf(
                0x10.toByte(), 0x00.toByte(), 0x88.toByte(), 0xbe.toByte(), 0x01.toByte(), 0xf6.toByte(), 0xa4.toByte(),
                0x40.toByte(), 0x45.toByte(), 0xa0.toByte(), 0x00.toByte(), 0x1c.toByte(), 0xe8.toByte(), 0xdd.toByte()
        )
    }

    @Test
    fun `Parse GRE (with optional Sequence Number) - ERSPAN`() {
        // Init
        mockkConstructor(ErspanHandler::class)
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<ErspanHandler>().handle(capture(packetSlot))
        } just Runs
        // Execute
        val greHandler = GreHandler(Vertx.vertx(), false)
        val packet = Packet().apply {
            this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_1))
        }
        greHandler.handle(packet)
        // Assert
        verify { anyConstructed<ErspanHandler>().handle(any()) }
        val buffer = (packetSlot.captured.payload as Encodable).encode()
        assertEquals(6, buffer.remainingCapacity())
    }
}