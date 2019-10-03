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

package io.sip3.captain.ce.encoder

import io.sip3.captain.ce.Routes
import io.sip3.captain.ce.USE_LOCAL_CODEC
import io.sip3.captain.ce.domain.Packet
import io.sip3.captain.ce.domain.payload.ByteArrayPayload
import io.sip3.commons.vertx.test.VertxTest
import io.vertx.core.buffer.Buffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Timestamp

class EncoderTest : VertxTest() {

    companion object {

        // Payload: ICMP
        val PACKET_1 = byteArrayOf(
                0x03.toByte(), 0x03.toByte(), 0x79.toByte(), 0xc4.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
                0x00.toByte(), 0x45.toByte(), 0xb8.toByte(), 0x00.toByte(), 0xc8.toByte(), 0xbf.toByte(), 0xd3.toByte(),
                0x00.toByte(), 0x00.toByte())
    }

    @Test
    fun `Encode ICMP`() {
        val timestamp = Timestamp(System.currentTimeMillis())
        val srcAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
        val dstAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x06.toByte())
        val srcPort = 5060
        val dstPort = 5061
        runTest(
                deploy = {
                    vertx.deployTestVerticle(Encoder::class)
                },
                execute = {
                    val packet = Packet().apply {
                        this.timestamp = timestamp
                        this.srcAddr = srcAddr
                        this.dstAddr = dstAddr
                        this.srcPort = srcPort
                        this.dstPort = dstPort
                        protocolCode = Packet.TYPE_ICMP
                        payload = ByteArrayPayload(PACKET_1)
                    }
                    vertx.eventBus().send(Routes.encoder, listOf(packet), USE_LOCAL_CODEC)
                },
                assert = {
                    vertx.eventBus().consumer<List<Buffer>>(Routes.sender) { event ->
                        val buffers = event.body()
                        context.verify {
                            assertEquals(1, buffers.size)

                            val buffer = buffers[0]

                            assertEquals(74, buffer.length())
                            // Prefix
                            assertArrayEquals(Encoder.PREFIX, buffer.getBytes(0, 4))
                            // Compressed
                            assertEquals(0, buffer.getByte(4))
                            // Type
                            assertEquals(1, buffer.getByte(5))
                            // Version
                            assertEquals(1, buffer.getByte(6))
                            // Length
                            assertEquals(69, buffer.getShort(7))
                            // Milliseconds
                            assertEquals(1, buffer.getByte(9))
                            assertEquals(11, buffer.getShort(10))
                            assertEquals(timestamp.time, buffer.getLong(12))
                            // Nanoseconds
                            assertEquals(2, buffer.getByte(20))
                            assertEquals(7, buffer.getShort(21))
                            assertEquals(timestamp.nanos, buffer.getInt(23))
                            // Source Address
                            assertEquals(3, buffer.getByte(27))
                            assertEquals(3 + srcAddr.size, buffer.getShort(28).toInt())
                            assertArrayEquals(srcAddr, buffer.getBytes(30, 30 + srcAddr.size))
                            // Destination Address
                            assertEquals(4, buffer.getByte(34))
                            assertEquals(3 + dstAddr.size, buffer.getShort(35).toInt())
                            assertArrayEquals(dstAddr, buffer.getBytes(37, 37 + dstAddr.size))
                            // Source Port
                            assertEquals(5, buffer.getByte(41))
                            assertEquals(5, buffer.getShort(42))
                            assertEquals(srcPort, buffer.getShort(44).toInt())
                            // Destination Port
                            assertEquals(6, buffer.getByte(46))
                            assertEquals(5, buffer.getShort(47))
                            assertEquals(dstPort, buffer.getShort(49).toInt())
                            // Protocol Code
                            assertEquals(7, buffer.getByte(51))
                            assertEquals(4, buffer.getShort(52))
                            assertEquals(Packet.TYPE_ICMP, buffer.getByte(54))
                            // Payload
                            assertEquals(8, buffer.getByte(55))
                            assertEquals(3 + PACKET_1.size, buffer.getShort(56).toInt())
                            assertArrayEquals(PACKET_1, buffer.getBytes(58, 58 + PACKET_1.size))
                        }
                        context.completeNow()
                    }
                }
        )
    }
}