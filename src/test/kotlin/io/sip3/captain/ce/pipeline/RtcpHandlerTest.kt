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
import io.sip3.captain.ce.domain.ByteBufPayload
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.vertx.test.VertxTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RtcpHandlerTest : VertxTest() {

    companion object {

        // Payload: RTCP
        val PACKET_1 = byteArrayOf(
                0x81.toByte(), 0xc8.toByte(), 0x00.toByte(), 0x0c.toByte(), 0x59.toByte(), 0xdb.toByte(), 0xe3.toByte(),
                0x0c.toByte(), 0x5c.toByte(), 0xac.toByte(), 0xde.toByte(), 0x40.toByte(), 0x00.toByte(), 0x0d.toByte(),
                0xe7.toByte(), 0xb1.toByte()
        )
    }

    @Test
    fun `Parse RTCP`() {
        runTest(
                deploy = {
                    // Do nothing...
                },
                execute = {
                    val rtcpHandler = RtcpHandler(vertx, false)
                    var packet = Packet().apply {
                        this.payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_1))
                    }
                    rtcpHandler.handle(packet)
                },
                assert = {
                    vertx.eventBus().consumer<List<Packet>>(Routes.encoder) { event ->
                        val packets = event.body()
                        context.verify {
                            assertEquals(1, packets.size)
                            val packet = packets[0]
                            assertEquals(Packet.TYPE_RTCP, packet.protocolCode)
                        }
                        context.completeNow()
                    }
                }
        )
    }
}