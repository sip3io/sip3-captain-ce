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
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.sip3.captain.ce.domain.Packet
import io.vertx.core.Vertx
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class RouterHandlerTest {

    companion object {

        // Payload: SIP
        val PACKET_1 = byteArrayOf(
                0x53.toByte(), 0x49.toByte(), 0x50.toByte(), 0x2f.toByte(), 0x32.toByte(), 0x2e.toByte(), 0x30.toByte(),
                0x20.toByte(), 0x31.toByte(), 0x30.toByte(), 0x30.toByte(), 0x20.toByte(), 0x54.toByte(), 0x72.toByte(),
                0x79.toByte(), 0x69.toByte()
        )

        // Payload: RTP
        val PACKET_2 = byteArrayOf(
                0x80.toByte(), 0x88.toByte(), 0x95.toByte(), 0x91.toByte(), 0xfd.toByte(), 0xe6.toByte(), 0xf6.toByte(),
                0xf6.toByte(), 0x4a.toByte(), 0xbf.toByte(), 0x30.toByte(), 0x13.toByte(), 0xd5.toByte(), 0xd5.toByte(),
                0xd5.toByte(), 0xd5.toByte()
        )

        // Payload: RTCP
        val PACKET_3 = byteArrayOf(
                0x81.toByte(), 0xc8.toByte(), 0x00.toByte(), 0x0c.toByte(), 0x59.toByte(), 0xdb.toByte(), 0xe3.toByte(),
                0x0c.toByte(), 0x5c.toByte(), 0xac.toByte(), 0xde.toByte(), 0x40.toByte(), 0x00.toByte(), 0x0d.toByte(),
                0xe7.toByte(), 0xb1.toByte()
        )
    }

    @Test
    fun `Route SIP`() {
        // Init
        mockkConstructor(SipHandler::class)
        val bufferSlot = slot<ByteBuf>()
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<SipHandler>().handle(capture(bufferSlot), capture(packetSlot))
        } just Runs
        // Execute
        val routerHandler = RouterHandler(Vertx.vertx(), false)
        routerHandler.handle(Unpooled.wrappedBuffer(PACKET_1), Packet())
        // Assert
        verify { anyConstructed<SipHandler>().handle(any(), any()) }
    }

    @Test
    fun `Route RTP`() {
        // Init
        mockkConstructor(RtpHandler::class)
        val bufferSlot = slot<ByteBuf>()
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<RtpHandler>().handle(capture(bufferSlot), capture(packetSlot))
        } just Runs
        // Execute
        val routerHandler = RouterHandler(Vertx.vertx(), false)
        routerHandler.handle(Unpooled.wrappedBuffer(PACKET_2), Packet())
        // Assert
        verify { anyConstructed<RtpHandler>().handle(any(), any()) }
    }

    @Test
    fun `Route RTCP`() {
        // Init
        mockkConstructor(RtcpHandler::class)
        val bufferSlot = slot<ByteBuf>()
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<RtcpHandler>().handle(capture(bufferSlot), capture(packetSlot))
        } just Runs
        // Execute
        val routerHandler = RouterHandler(Vertx.vertx(), false)
        routerHandler.handle(Unpooled.wrappedBuffer(PACKET_3), Packet())
        // Assert
        verify { anyConstructed<RtcpHandler>().handle(any(), any()) }
    }

    @AfterEach
    fun `Unmock all`() {
        unmockkAll()
    }
}