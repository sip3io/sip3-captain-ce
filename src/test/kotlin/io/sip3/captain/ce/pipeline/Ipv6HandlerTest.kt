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
import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.util.IpUtil
import io.sip3.commons.util.remainingCapacity
import io.sip3.commons.vertx.test.VertxTest
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class Ipv6HandlerTest : VertxTest() {

    companion object {

        // IPv6 Payload: UDP
        val PACKET_1 = byteArrayOf(
            0x60.toByte(), 0x00.toByte(), 0x95.toByte(), 0x08.toByte(), 0x00.toByte(), 0x08.toByte(), 0x2b.toByte(),
            0x40.toByte(), 0x2a.toByte(), 0x03.toByte(), 0xa9.toByte(), 0x60.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x02.toByte(), 0xab.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x26.toByte(), 0x03.toByte(), 0x00.toByte(), 0x05.toByte(),
            0x22.toByte(), 0x81.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0f.toByte(), 0x11.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x32.toByte(), 0x40.toByte(), 0xe8.toByte(), 0x3c.toByte(), 0x00.toByte(), 0xb4.toByte(), 0x13.toByte(),
            0x0b.toByte()
        )

        // IPv6 Payload: TCP
        val PACKET_2 = byteArrayOf(
            0x60.toByte(), 0x00.toByte(), 0x4b.toByte(), 0x73.toByte(), 0x00.toByte(), 0x08.toByte(), 0x06.toByte(),
            0x40.toByte(), 0x2a.toByte(), 0x03.toByte(), 0xa9.toByte(), 0x60.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x02.toByte(), 0xab.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x20.toByte(), 0x01.toByte(), 0x06.toByte(), 0x48.toByte(),
            0x2c.toByte(), 0x30.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x01.toByte(), 0x91.toByte(), 0x00.toByte(), 0x03.toByte(), 0x32.toByte(), 0x40.toByte(),
            0xe8.toByte(), 0x3c.toByte(), 0x00.toByte(), 0xb4.toByte(), 0x13.toByte(), 0x0b.toByte()
        )
    }

    @Test
    fun `Parse IPv6 - UDP`() {
        // Init
        mockkConstructor(UdpHandler::class)
        val packetSlot = slot<Packet>()
        every {
            anyConstructed<UdpHandler>().handle(capture(packetSlot))
        } just Runs
        // Execute
        val ipv6Handler = Ipv6Handler(Vertx.vertx(), JsonObject(), false)
        var packet = Packet().apply {
            this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_1))
        }
        ipv6Handler.handle(packet)
        // Assert
        verify { anyConstructed<UdpHandler>().handle(any()) }
        packet = packetSlot.captured
        val buffer = (packet.payload as Encodable).encode()
        Assertions.assertEquals("2a03:a960:1:2ab::", IpUtil.convertToString(packet.srcAddr))
        Assertions.assertEquals("2603:5:2281::f", IpUtil.convertToString(packet.dstAddr))
        Assertions.assertEquals(8, buffer.remainingCapacity())
    }

    @Test
    fun `Parse IPv6 - TCP`() {
        runTest(
            deploy = {
                // Do nothing...
            },
            execute = {
                val ipv6Handler = Ipv6Handler(vertx, JsonObject(), false)
                var packet = Packet().apply {
                    this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_2))
                }
                ipv6Handler.handle(packet)
            },
            assert = {
                vertx.eventBus().consumer<List<Packet>>(RoutesCE.tcp) { event ->
                    val packets = event.body()
                    context.verify {
                        Assertions.assertEquals(1, packets.size)
                        val packet = packets[0]
                        val buffer = (packet.payload as Encodable).encode()
                        Assertions.assertEquals("2a03:a960:1:2ab::", IpUtil.convertToString(packet.srcAddr))
                        Assertions.assertEquals("2001:648:2c30::191:3", IpUtil.convertToString(packet.dstAddr))
                        Assertions.assertEquals(8, buffer.remainingCapacity())
                    }
                    context.completeNow()
                }
            }
        )
    }
}