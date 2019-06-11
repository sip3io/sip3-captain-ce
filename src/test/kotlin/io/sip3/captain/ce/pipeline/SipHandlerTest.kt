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
import java.sql.Timestamp

class SipHandlerTest : VertxTest() {

    companion object {

        // Payload: SIP (1 message)
        val PACKET_1 = byteArrayOf(
                0x53.toByte(), 0x49.toByte(), 0x50.toByte(), 0x2f.toByte(), 0x32.toByte(), 0x2e.toByte(), 0x30.toByte(),
                0x20.toByte(), 0x32.toByte(), 0x30.toByte(), 0x30.toByte(), 0x20.toByte(), 0x4f.toByte(), 0x4b.toByte(),
                0x0d.toByte(), 0x0a.toByte()
        )

        // Payload: SIP (2 messages)
        val PACKET_2 = byteArrayOf(
                0x42.toByte(), 0x59.toByte(), 0x45.toByte(), 0x20.toByte(), 0x73.toByte(), 0x69.toByte(), 0x70.toByte(),
                0x3a.toByte(), 0x31.toByte(), 0x30.toByte(), 0x2e.toByte(), 0x32.toByte(), 0x35.toByte(), 0x30.toByte(),
                0x2e.toByte(), 0x32.toByte(), 0x34.toByte(), 0x32.toByte(), 0x2e.toByte(), 0x32.toByte(), 0x33.toByte(),
                0x38.toByte(), 0x3a.toByte(), 0x35.toByte(), 0x30.toByte(), 0x36.toByte(), 0x30.toByte(), 0x3b.toByte(),
                0x74.toByte(), 0x72.toByte(), 0x61.toByte(), 0x6e.toByte(), 0x73.toByte(), 0x70.toByte(), 0x6f.toByte(),
                0x72.toByte(), 0x74.toByte(), 0x3d.toByte(), 0x75.toByte(), 0x64.toByte(), 0x70.toByte(), 0x20.toByte(),
                0x53.toByte(), 0x49.toByte(), 0x50.toByte(), 0x2f.toByte(), 0x32.toByte(), 0x2e.toByte(), 0x30.toByte(),
                0x0d.toByte(), 0x0a.toByte(), 0x41.toByte(), 0x43.toByte(), 0x4b.toByte(), 0x20.toByte(), 0x73.toByte(),
                0x69.toByte(), 0x70.toByte(), 0x3a.toByte(), 0x31.toByte(), 0x30.toByte(), 0x2e.toByte(), 0x32.toByte(),
                0x35.toByte(), 0x30.toByte(), 0x2e.toByte(), 0x32.toByte(), 0x34.toByte(), 0x32.toByte(), 0x2e.toByte(),
                0x31.toByte(), 0x38.toByte(), 0x30.toByte(), 0x3a.toByte(), 0x35.toByte(), 0x30.toByte(), 0x36.toByte(),
                0x31.toByte(), 0x3b.toByte(), 0x74.toByte(), 0x72.toByte(), 0x61.toByte(), 0x6e.toByte(), 0x73.toByte(),
                0x70.toByte(), 0x6f.toByte(), 0x72.toByte(), 0x74.toByte(), 0x3d.toByte(), 0x75.toByte(), 0x64.toByte(),
                0x70.toByte(), 0x20.toByte(), 0x53.toByte(), 0x49.toByte(), 0x50.toByte(), 0x2f.toByte(), 0x32.toByte(),
                0x2e.toByte(), 0x30.toByte(), 0x0d.toByte(), 0x0a.toByte()
        )
    }

    @Test
    fun `Parse SIP packet containing one message`() {
        runTest(
                deploy = {
                    // Do nothing...
                },
                execute = {
                    val sipHandler = SipHandler(vertx, false)
                    val buffer = Unpooled.wrappedBuffer(PACKET_1)
                    val packet = Packet().apply {
                        timestamp = Timestamp(System.currentTimeMillis())
                        srcAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
                        dstAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
                        srcPort = 5060
                        dstPort = 5060
                    }
                    sipHandler.handle(buffer, packet)
                },
                assert = {
                    vertx.eventBus().consumer<List<Packet>>(Routes.encoder) { event ->
                        val packets = event.body()
                        context.verify {
                            assertEquals(1, packets.size)
                            val packet = packets[0]
                            assertEquals(Packet.TYPE_SIP, packet.protocolCode)
                            val buffer = packet.payload.encode()
                            val payloadLength = buffer.capacity() - buffer.readerIndex()
                            assertEquals(16, payloadLength)
                        }
                        context.completeNow()
                    }
                }
        )
    }

    @Test
    fun `Parse SIP packet containing two messages`() {
        runTest(
                deploy = {
                    // Do nothing...
                },
                execute = {
                    val sipHandler = SipHandler(vertx, false)
                    val buffer = Unpooled.wrappedBuffer(PACKET_2)
                    val packet = Packet().apply {
                        timestamp = Timestamp(System.currentTimeMillis())
                        srcAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
                        dstAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
                        srcPort = 5060
                        dstPort = 5060
                    }
                    sipHandler.handle(buffer, packet)
                },
                assert = {
                    vertx.eventBus().consumer<List<Packet>>(Routes.encoder) { event ->
                        val packets = event.body()
                        context.verify {
                            assertEquals(2, packets.size)
                            packets.forEach { packet ->
                                assertEquals(Packet.TYPE_SIP, packet.protocolCode)
                                val buffer = packet.payload.encode()
                                val payloadLength = buffer.capacity() - buffer.readerIndex()
                                assertEquals(51, payloadLength)
                            }
                        }
                        context.completeNow()
                    }
                }
        )
    }
}