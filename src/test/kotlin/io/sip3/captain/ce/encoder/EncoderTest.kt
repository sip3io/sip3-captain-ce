/*
 * Copyright 2018-2022 SIP3.IO, Corp.
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

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.PacketTypes
import io.sip3.commons.domain.payload.ByteArrayPayload
import io.sip3.commons.util.getBytes
import io.sip3.commons.vertx.test.VertxTest
import io.sip3.commons.vertx.util.localSend
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream

class EncoderTest : VertxTest() {

    companion object {

        // Payload: ICMP
        val PAYLOAD = byteArrayOf(
            0x03.toByte(), 0x03.toByte(), 0x79.toByte(), 0xc4.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x45.toByte(), 0xb8.toByte(), 0x00.toByte(), 0xc8.toByte(), 0xbf.toByte(), 0xd3.toByte(),
            0x00.toByte(), 0x45.toByte(), 0xb8.toByte(), 0x00.toByte(), 0xc8.toByte(), 0xbf.toByte(), 0xd3.toByte(),
            0x00.toByte(), 0x45.toByte(), 0xb8.toByte(), 0x00.toByte(), 0xc8.toByte(), 0xbf.toByte(), 0xd3.toByte(),
            0x00.toByte(), 0x45.toByte(), 0xb8.toByte(), 0x00.toByte(), 0xc8.toByte(), 0xbf.toByte(), 0xd3.toByte(),
            0x00.toByte(), 0x45.toByte(), 0xb8.toByte(), 0x00.toByte(), 0xc8.toByte(), 0xbf.toByte(), 0xd3.toByte(),
            0x00.toByte(), 0x45.toByte(), 0xb8.toByte(), 0x00.toByte(), 0xc8.toByte(), 0xbf.toByte(), 0xd3.toByte(),
            0x00.toByte(), 0x45.toByte(), 0xb8.toByte(), 0x00.toByte(), 0xc8.toByte(), 0xbf.toByte(), 0xd3.toByte(),
            0x00.toByte(), 0x45.toByte(), 0xb8.toByte(), 0x00.toByte(), 0xc8.toByte(), 0xbf.toByte(), 0xd3.toByte(),
            0x00.toByte(), 0x45.toByte(), 0xb8.toByte(), 0x00.toByte(), 0xc8.toByte(), 0xbf.toByte(), 0xd3.toByte(),
            0x00.toByte(), 0x45.toByte(), 0xb8.toByte(), 0x00.toByte(), 0xc8.toByte(), 0xbf.toByte(), 0xd3.toByte(),
            0x00.toByte(), 0x45.toByte(), 0xb8.toByte(), 0x00.toByte(), 0xc8.toByte(), 0xbf.toByte(), 0xd3.toByte(),
            0x00.toByte(), 0x45.toByte(), 0xb8.toByte(), 0x00.toByte(), 0xc8.toByte(), 0xbf.toByte(), 0xd3.toByte(),
            0x00.toByte(), 0x45.toByte(), 0xb8.toByte(), 0x00.toByte(), 0xc8.toByte(), 0xbf.toByte(), 0xd3.toByte(),
            0x00.toByte(), 0x45.toByte(), 0xb8.toByte(), 0x00.toByte(), 0xc8.toByte(), 0xbf.toByte(), 0xd3.toByte(),
            0x00.toByte(), 0x00.toByte()
        )

        // Packet: ICMP
        val PACKET = Packet().apply {
            timestamp = 1611254287666
            nanos = 42
            srcAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
            dstAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x06.toByte())
            srcPort = 5060
            dstPort = 5061
            protocolCode = PacketTypes.ICMP
            payload = ByteArrayPayload(PAYLOAD)
        }
    }

    @Test
    fun `Encode 3 packets in 1`() {
        runTest(
            deploy = {
                vertx.deployTestVerticle(Encoder::class)
            },
            execute = {
                vertx.eventBus().localSend(RoutesCE.encoder, listOf(PACKET, PACKET, PACKET))
            },
            assert = {
                vertx.eventBus().consumer<List<Buffer>>(RoutesCE.sender) { event ->
                    val buffers = event.body()
                    context.verify {
                        assertEquals(1, buffers.size)

                        val buffer = buffers[0].byteBuf

                        // Capacity
                        assertEquals(486, buffer.writerIndex())

                        val prefix = ByteArray(4)
                        buffer.readBytes(prefix)
                        // Prefix
                        assertArrayEquals(Encoder.PREFIX, prefix)
                        // Protocol Version
                        assertEquals(2, buffer.readByte())
                        // Compressed
                        assertEquals(0, buffer.readByte())
                        // Packet
                        assertPacketContent(buffer)
                        assertPacketContent(buffer)
                        assertPacketContent(buffer)
                    }
                    context.completeNow()
                }
            }
        )
    }

    @Test
    fun `Encode 3 packets in 2`() {
        runTest(
            deploy = {
                vertx.deployTestVerticle(Encoder::class, config = JsonObject().apply {
                    put("encoder", JsonObject().apply {
                        put("mtu-size", 350)
                        put("bulk-size", 2)
                    })
                })
            },
            execute = {
                vertx.eventBus().localSend(RoutesCE.encoder, listOf(PACKET, PACKET, PACKET))
            },
            assert = {
                vertx.eventBus().consumer<List<Buffer>>(RoutesCE.sender) { event ->
                    val buffers = event.body()
                    context.verify {
                        assertEquals(2, buffers.size)

                        // Packet 1
                        var buffer = buffers[0].byteBuf

                        // Capacity
                        assertEquals(326, buffer.writerIndex())

                        var prefix = ByteArray(4)
                        buffer.readBytes(prefix)
                        // Prefix
                        assertArrayEquals(Encoder.PREFIX, prefix)
                        // Protocol Version
                        assertEquals(2, buffer.readByte())
                        // Compressed
                        assertEquals(0, buffer.readByte())
                        // Packet
                        assertPacketContent(buffer)
                        assertPacketContent(buffer)

                        // Packet 2
                        buffer = buffers[1].byteBuf
                        // Capacity
                        assertEquals(166, buffer.writerIndex())

                        prefix = ByteArray(4)
                        buffer.readBytes(prefix)
                        // Prefix
                        assertArrayEquals(Encoder.PREFIX, prefix)
                        // Protocol Version
                        assertEquals(2, buffer.readByte())
                        // Compressed
                        assertEquals(0, buffer.readByte())
                        // Packet
                        assertPacketContent(buffer)
                    }
                    context.completeNow()
                }
            }
        )
    }

    @Test
    fun `Encode 2 packets in 2`() {
        runTest(
            deploy = {
                vertx.deployTestVerticle(Encoder::class, config = JsonObject().apply {
                    put("encoder", JsonObject().apply {
                        put("mtu-size", 160)
                        put("bulk-size", 2)
                    })
                })
            },
            execute = {
                vertx.eventBus().localSend(RoutesCE.encoder, listOf(PACKET, PACKET))
            },
            assert = {
                vertx.eventBus().consumer<List<Buffer>>(RoutesCE.sender) { event ->
                    val buffers = event.body()
                    context.verify {
                        assertEquals(2, buffers.size)

                        // Packet 1
                        var buffer = buffers[0].byteBuf

                        // Capacity
                        assertEquals(166, buffer.writerIndex())

                        var prefix = ByteArray(4)
                        buffer.readBytes(prefix)
                        // Prefix
                        assertArrayEquals(Encoder.PREFIX, prefix)
                        // Protocol Version
                        assertEquals(2, buffer.readByte())
                        // Compressed
                        assertEquals(0, buffer.readByte())
                        // Packet
                        assertPacketContent(buffer)

                        // Packet 2
                        buffer = buffers[1].byteBuf

                        // Capacity
                        assertEquals(166, buffer.writerIndex())

                        prefix = ByteArray(4)
                        buffer.readBytes(prefix)
                        // Prefix
                        assertArrayEquals(Encoder.PREFIX, prefix)
                        // Protocol Version
                        assertEquals(2, buffer.readByte())
                        // Compressed
                        assertEquals(0, buffer.readByte())
                        // Packet
                        assertPacketContent(buffer)
                    }
                    context.completeNow()
                }
            }
        )
    }

    @Test
    fun `Encode 2 packets in 2 compressed`() {
        runTest(
            deploy = {
                vertx.deployTestVerticle(Encoder::class, config = JsonObject().apply {
                    put("encoder", JsonObject().apply {
                        put("mtu-size", 100)
                        put("bulk-size", 2)
                    })
                })
            },
            execute = {
                vertx.eventBus().localSend(RoutesCE.encoder, listOf(PACKET, PACKET))
            },
            assert = {
                vertx.eventBus().consumer<List<Buffer>>(RoutesCE.sender) { event ->
                    val buffers = event.body()
                    context.verify {
                        assertEquals(2, buffers.size)

                        // Packet 1
                        var buffer = buffers[0].byteBuf

                        // Capacity
                        assertEquals(79, buffer.writerIndex())

                        var prefix = ByteArray(4)
                        buffer.readBytes(prefix)
                        // Prefix
                        assertArrayEquals(Encoder.PREFIX, prefix)
                        // Protocol Version
                        assertEquals(2, buffer.readByte())
                        // Compressed
                        assertEquals(1, buffer.readByte())
                        // Packet
                        InflaterInputStream(ByteArrayInputStream(buffer.getBytes())).use { inflater ->
                            assertPacketContent(Unpooled.wrappedBuffer(inflater.readBytes()))
                        }

                        // Packet 2
                        buffer = buffers[0].byteBuf

                        // Capacity
                        assertEquals(79, buffer.writerIndex())

                        prefix = ByteArray(4)
                        buffer.readBytes(prefix)
                        // Prefix
                        assertArrayEquals(Encoder.PREFIX, prefix)
                        // Protocol Version
                        assertEquals(2, buffer.readByte())
                        // Compressed
                        assertEquals(1, buffer.readByte())
                        // Packet
                        InflaterInputStream(ByteArrayInputStream(buffer.getBytes())).use { inflater ->
                            assertPacketContent(Unpooled.wrappedBuffer(inflater.readBytes()))
                        }
                    }
                    context.completeNow()
                }
            }
        )
    }

    private fun assertPacketContent(buffer: ByteBuf) {
        // Type
        assertEquals(1, buffer.readByte())
        // Version
        assertEquals(1, buffer.readByte())
        // Length
        assertEquals(160, buffer.readShort())

        // Milliseconds
        assertEquals(1, buffer.readByte())
        assertEquals(11, buffer.readShort())
        assertEquals(PACKET.timestamp, buffer.readLong())
        // Nanoseconds
        assertEquals(2, buffer.readByte())
        assertEquals(7, buffer.readShort())
        assertEquals(PACKET.nanos, buffer.readInt())

        // Source Address
        assertEquals(3, buffer.readByte())
        assertEquals(3 + PACKET.srcAddr.size, buffer.readUnsignedShort())
        val srcAddr = ByteArray(PACKET.srcAddr.size)
        buffer.readBytes(srcAddr)
        assertArrayEquals(PACKET.srcAddr, srcAddr)
        // Destination Address
        assertEquals(4, buffer.readByte())
        assertEquals(3 + PACKET.dstAddr.size, buffer.readUnsignedShort())
        val dstAddr = ByteArray(PACKET.dstAddr.size)
        buffer.readBytes(dstAddr)
        assertArrayEquals(PACKET.dstAddr, dstAddr)

        // Source Port
        assertEquals(5, buffer.readByte())
        assertEquals(5, buffer.readShort())
        assertEquals(PACKET.srcPort, buffer.readUnsignedShort())
        // Destination Port
        assertEquals(6, buffer.readByte())
        assertEquals(5, buffer.readShort())
        assertEquals(PACKET.dstPort, buffer.readUnsignedShort())

        // Protocol Code
        assertEquals(7, buffer.readByte())
        assertEquals(4, buffer.readShort())
        assertEquals(PacketTypes.ICMP, buffer.readByte())

        // Payload
        assertEquals(8, buffer.readByte())
        assertEquals(3 + PAYLOAD.size, buffer.readUnsignedShort())
        val payload = ByteArray(PAYLOAD.size)
        buffer.readBytes(payload)
        assertArrayEquals(PAYLOAD, payload)
    }
}
