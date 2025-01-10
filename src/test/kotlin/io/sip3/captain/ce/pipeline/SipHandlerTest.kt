/*
 * Copyright 2018-2025 SIP3.IO, Corp.
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
import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.ProtocolCodes
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.vertx.test.VertxTest
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SipHandlerTest : VertxTest() {

    companion object {

        // Payload: SIP (1 message)
        val PACKET_1 = byteArrayOf(
            0x53.toByte(), 0x49.toByte(), 0x50.toByte(), 0x2f.toByte(), 0x32.toByte(), 0x2e.toByte(), 0x30.toByte(),
            0x20.toByte(), 0x32.toByte(), 0x30.toByte(), 0x30.toByte(), 0x20.toByte(), 0x4f.toByte(), 0x4b.toByte(),
            0x0d.toByte(), 0x0a.toByte(), 0x0d.toByte(), 0x0a.toByte()
        )
    }

    @Test
    fun `Parse SIP packet containing one message`() {
        runTest(
            deploy = {
                // Do nothing...
            },
            execute = {
                val sipHandler = SipHandler(vertx, JsonObject(), false)
                val packet = Packet().apply {
                    timestamp = System.currentTimeMillis()
                    srcAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
                    dstAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
                    srcPort = 5060
                    dstPort = 5060
                    payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_1))
                }
                sipHandler.handle(packet)
            },
            assert = {
                vertx.eventBus().consumer<List<Packet>>(RoutesCE.encoder) { event ->
                    val packets = event.body()
                    context.verify {
                        assertEquals(1, packets.size)
                        val packet = packets[0]
                        assertEquals(ProtocolCodes.SIP, packet.protocolCode)
                        val buffer = (packet.payload as Encodable).encode()
                        assertEquals(18, buffer.readableBytes())
                    }
                    context.completeNow()
                }
            }
        )
    }
}
