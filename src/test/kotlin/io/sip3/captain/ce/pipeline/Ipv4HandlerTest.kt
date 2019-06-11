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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.InetAddress

@ExtendWith(MockKExtension::class)
class Ipv4HandlerTest {

    companion object {

        // IPv4 Payload: UDP
        val PACKET_1 = byteArrayOf(
                0x45.toByte(), 0xa0.toByte(), 0x00.toByte(), 0x1c.toByte(), 0xe8.toByte(), 0xdd.toByte(), 0x00.toByte(),
                0x00.toByte(), 0x3c.toByte(), 0x11.toByte(), 0x75.toByte(), 0x6e.toByte(), 0x0a.toByte(), 0xfa.toByte(),
                0xf4.toByte(), 0x05.toByte(), 0x0a.toByte(), 0xc5.toByte(), 0x15.toByte(), 0x75.toByte(), 0x32.toByte(),
                0x40.toByte(), 0xe8.toByte(), 0x3c.toByte(), 0x00.toByte(), 0xb4.toByte(), 0x13.toByte(), 0x0b.toByte()
        )

        // IPv4 Payload: TCP
        val PACKET_2 = byteArrayOf(
                0x45.toByte(), 0xa0.toByte(), 0x00.toByte(), 0x1c.toByte(), 0xe8.toByte(), 0xdd.toByte(), 0x00.toByte(),
                0x00.toByte(), 0x3c.toByte(), 0x06.toByte(), 0x75.toByte(), 0x6e.toByte(), 0x0a.toByte(), 0xfa.toByte(),
                0xf4.toByte(), 0x05.toByte(), 0x0a.toByte(), 0xc5.toByte(), 0x15.toByte(), 0x75.toByte(), 0x32.toByte(),
                0x40.toByte(), 0xe8.toByte(), 0x3c.toByte(), 0x00.toByte(), 0xb4.toByte(), 0x13.toByte(), 0x0b.toByte()
        )

        // IPv4 Payload: IPv4
        val PACKET_3 = byteArrayOf(
                0x45.toByte(), 0x00.toByte(), 0x00.toByte(), 0x30.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
                0x00.toByte(), 0x7c.toByte(), 0x04.toByte(), 0xfa.toByte(), 0x79.toByte(), 0x0a.toByte(), 0x4d.toByte(),
                0x0f.toByte(), 0x61.toByte(), 0x0a.toByte(), 0x4d.toByte(), 0x1f.toByte(), 0xaa.toByte(), 0x45.toByte(),
                0xa0.toByte(), 0x00.toByte(), 0x1c.toByte(), 0xe8.toByte(), 0xdd.toByte(), 0x00.toByte(), 0x00.toByte(),
                0x3c.toByte(), 0x11.toByte(), 0x75.toByte(), 0x6e.toByte(), 0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(),
                0x05.toByte(), 0x0a.toByte(), 0xc5.toByte(), 0x15.toByte(), 0x75.toByte(), 0x32.toByte(), 0x40.toByte(),
                0xe8.toByte(), 0x3c.toByte(), 0x00.toByte(), 0xb4.toByte(), 0x13.toByte(), 0x0b.toByte()
        )

        // IPv4 Payload: ICMP
        val PACKET_4 = byteArrayOf(
                0x45.toByte(), 0xa0.toByte(), 0x00.toByte(), 0x1c.toByte(), 0xe8.toByte(), 0xdd.toByte(), 0x00.toByte(),
                0x00.toByte(), 0x3c.toByte(), 0x01.toByte(), 0x75.toByte(), 0x6e.toByte(), 0x0a.toByte(), 0xfa.toByte(),
                0xf4.toByte(), 0x05.toByte(), 0x0a.toByte(), 0xc5.toByte(), 0x15.toByte(), 0x75.toByte(), 0x32.toByte(),
                0x40.toByte(), 0xe8.toByte(), 0x3c.toByte(), 0x00.toByte(), 0xb4.toByte(), 0x13.toByte(), 0x0b.toByte()
        )
    }

    @Test
    fun `Parse IPv4 - UDP`() {
        // Init
        mockkConstructor(UdpHandler::class)
        val bufferSlot = slot<ByteBuf>()
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<UdpHandler>().handle(capture(bufferSlot), capture(packetSlot))
        } just Runs
        // Execute
        val ipv4Handler = Ipv4Handler(Vertx.vertx(), false)
        ipv4Handler.handle(Unpooled.wrappedBuffer(PACKET_1), Packet())
        // Assert
        verify { anyConstructed<UdpHandler>().handle(any(), any()) }
        val buffer = bufferSlot.captured
        val packet = packetSlot.captured
        val srcAddr = InetAddress.getByAddress(byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte()))
        assertEquals(srcAddr, InetAddress.getByAddress(packet.srcAddr))
        val dstAddr = InetAddress.getByAddress(byteArrayOf(0x0a.toByte(), 0xc5.toByte(), 0x15.toByte(), 0x75.toByte()))
        assertEquals(dstAddr, InetAddress.getByAddress(packet.dstAddr))
        val payloadLength = buffer.capacity() - buffer.readerIndex()
        assertEquals(8, payloadLength)
    }

    @Test
    fun `Parse IPv4 - TCP`() {
        // Init
        mockkConstructor(TcpHandler::class)
        val bufferSlot = slot<ByteBuf>()
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<TcpHandler>().handle(capture(bufferSlot), capture(packetSlot))
        } just Runs
        // Execute
        val ipv4Handler = Ipv4Handler(Vertx.vertx(), false)
        ipv4Handler.handle(Unpooled.wrappedBuffer(PACKET_2), Packet())
        // Assert
        verify { anyConstructed<TcpHandler>().handle(any(), any()) }
        val buffer = bufferSlot.captured
        val packet = packetSlot.captured
        val srcAddr = InetAddress.getByAddress(byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte()))
        assertEquals(srcAddr, InetAddress.getByAddress(packet.srcAddr))
        val dstAddr = InetAddress.getByAddress(byteArrayOf(0x0a.toByte(), 0xc5.toByte(), 0x15.toByte(), 0x75.toByte()))
        assertEquals(dstAddr, InetAddress.getByAddress(packet.dstAddr))
        val payloadLength = buffer.capacity() - buffer.readerIndex()
        assertEquals(8, payloadLength)
    }

    @Test
    fun `Parse IPv4 - IPv4`() {
        // Init
        mockkConstructor(UdpHandler::class)
        val bufferSlot = slot<ByteBuf>()
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<UdpHandler>().handle(capture(bufferSlot), capture(packetSlot))
        } just Runs
        // Execute
        val ipv4Handler = Ipv4Handler(Vertx.vertx(), false)
        ipv4Handler.handle(Unpooled.wrappedBuffer(PACKET_3), Packet())
        // Assert
        verify { anyConstructed<UdpHandler>().handle(any(), any()) }
        val buffer = bufferSlot.captured
        val packet = packetSlot.captured
        val srcAddr = InetAddress.getByAddress(byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte()))
        assertEquals(srcAddr, InetAddress.getByAddress(packet.srcAddr))
        val dstAddr = InetAddress.getByAddress(byteArrayOf(0x0a.toByte(), 0xc5.toByte(), 0x15.toByte(), 0x75.toByte()))
        assertEquals(dstAddr, InetAddress.getByAddress(packet.dstAddr))
        val payloadLength = buffer.capacity() - buffer.readerIndex()
        assertEquals(8, payloadLength)
    }

    @Test
    fun `Parse IPv4 - ICMP`() {
        // Init
        mockkConstructor(IcmpHandler::class)
        val bufferSlot = slot<ByteBuf>()
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<IcmpHandler>().handle(capture(bufferSlot), capture(packetSlot))
        } just Runs
        // Execute
        val ipv4Handler = Ipv4Handler(Vertx.vertx(), false)
        ipv4Handler.handle(Unpooled.wrappedBuffer(PACKET_4), Packet())
        // Assert
        verify { anyConstructed<IcmpHandler>().handle(any(), any()) }
        val buffer = bufferSlot.captured
        val packet = packetSlot.captured
        val srcAddr = InetAddress.getByAddress(byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte()))
        assertEquals(srcAddr, InetAddress.getByAddress(packet.srcAddr))
        val dstAddr = InetAddress.getByAddress(byteArrayOf(0x0a.toByte(), 0xc5.toByte(), 0x15.toByte(), 0x75.toByte()))
        assertEquals(dstAddr, InetAddress.getByAddress(packet.dstAddr))
        val payloadLength = buffer.capacity() - buffer.readerIndex()
        assertEquals(8, payloadLength)
    }

    @AfterEach
    fun `Unmock all`() {
        unmockkAll()
    }
}