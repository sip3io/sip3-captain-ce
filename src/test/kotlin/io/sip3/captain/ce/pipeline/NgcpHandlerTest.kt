/*
 * Copyright 2018-2026 SIP3.IO, Corp.
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
import io.sip3.commons.domain.payload.ByteArrayPayload
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.vertx.test.VertxTest
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NgcpHandlerTest : VertxTest() {

    companion object {

        val NOW = System.currentTimeMillis()

        val SRC_ADDR = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
        const val SRC_PORT = 12057
        val DST_ADDR = byteArrayOf(0x0a.toByte(), 0xc5.toByte(), 0x15.toByte(), 0x75.toByte())
        const val DST_PORT = 2223

        // Payload: SI3
        val PACKET_1 = byteArrayOf(0x53.toByte(), 0x49.toByte(), 0x33.toByte())
    }

    @Test
    fun `Handle NGCP packet`() {
        runTest(
            execute = {
                val ngcpHandler = NgcpHandler(vertx, JsonObject().apply {
                    put("ngcp", JsonObject().apply {
                        put("enabled", true)
                        put("port_ranges", JsonArray().apply {
                            add("2223")
                        })
                    })
                }, false)
                val packet = Packet().apply {
                    timestamp = NOW
                    srcAddr = SRC_ADDR
                    srcPort = SRC_PORT
                    dstAddr = DST_ADDR
                    dstPort = DST_PORT
                    this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_1))
                }
                ngcpHandler.handle(packet)
            },
            assert = {
                vertx.eventBus().consumer<List<Packet>>(RoutesCE.encoder) { event ->
                    val packets = event.body()
                    context.verify {
                        assertEquals(1, packets.size)

                        val packet = packets[0]
                        assertEquals(ProtocolCodes.NGCP, packet.protocolCode)
                        print(packet.payload.javaClass.canonicalName)
                        assertTrue(packet.payload is ByteArrayPayload)

                        val payload = packet.payload as ByteArrayPayload
                        assertEquals(3, payload.encode().writerIndex())
                        assertEquals("SI3", String(payload.bytes))
                    }
                    context.completeNow()
                }
            }
        )
    }
}
