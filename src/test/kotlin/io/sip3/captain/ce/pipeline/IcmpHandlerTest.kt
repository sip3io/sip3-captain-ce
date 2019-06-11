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

import io.netty.buffer.Unpooled
import io.sip3.captain.ce.Routes
import io.sip3.captain.ce.VertxTest
import io.sip3.captain.ce.domain.Packet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IcmpHandlerTest : VertxTest() {

    companion object {

        // Payload: ICMP
        val PACKET_1 = byteArrayOf(
                0x03.toByte(), 0x03.toByte(), 0x79.toByte(), 0xc4.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
                0x00.toByte(), 0x45.toByte(), 0xb8.toByte(), 0x00.toByte(), 0xc8.toByte(), 0xbf.toByte(), 0xd3.toByte(),
                0x00.toByte(), 0x00.toByte())
    }

    @Test
    fun `Parse ICMP - Destination port unreachable`() {
        runTest(
                deploy = {
                    // Do nothing...
                },
                execute = {
                    val icmpHandler = IcmpHandler(vertx, false)
                    icmpHandler.handle(Unpooled.wrappedBuffer(PACKET_1), Packet())
                },
                assert = {
                    vertx.eventBus().consumer<List<Packet>>(Routes.encoder) { event ->
                        val packets = event.body()
                        context.verify {
                            assertEquals(1, packets.size)
                            val packet = packets[0]
                            assertEquals(Packet.TYPE_ICMP, packet.protocolCode)
                        }
                        context.completeNow()
                    }
                }
        )
    }
}