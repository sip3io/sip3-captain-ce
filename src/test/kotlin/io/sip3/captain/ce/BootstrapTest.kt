/*
 * Copyright 2018-2021 SIP3.IO, Inc.
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

package io.sip3.captain.ce

import io.sip3.captain.ce.encoder.Encoder
import io.sip3.commons.PacketTypes
import io.sip3.commons.vertx.test.VertxTest
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.net.InetAddress
import java.nio.file.Files.createTempDirectory

class BootstrapTest : VertxTest() {

    companion object {

        val PCAP_FILE = File("src/test/resources/pcap/BootstrapTest.pcap")
    }

    @Test
    fun `Send some packets trough the entire pipeline to SIP3 Salto`() {
        val tempdir = createTempDirectory("tmp").toFile()
        val file = tempdir.resolve(PCAP_FILE.name)

        val address = InetAddress.getLoopbackAddress().hostAddress
        val port = findRandomPort()

        PCAP_FILE.copyTo(file)
        runTest(
            deploy = {
                vertx.deployTestVerticle(Bootstrap::class, JsonObject().apply {
                    put("pcap", JsonObject().apply {
                        put("dir", tempdir.absolutePath)
                    })
                    put("sender", JsonObject().apply {
                        put("uri", "udp://$address:$port")
                    })
                })
            },
            execute = {
                vertx.setPeriodic(1000) { file.setLastModified(System.currentTimeMillis()) }
            },
            assert = {
                vertx.createDatagramSocket()
                    .handler { packet ->
                        val buffer = packet.data().byteBuf
                        context.verify {
                            assertEquals(990, buffer.capacity())
                            // Prefix
                            var prefix = ByteArray(4)
                            buffer.readBytes(prefix)
                            assertArrayEquals(Encoder.PREFIX, prefix)
                            // Version
                            assertEquals(2, buffer.readByte())
                            // Compressed
                            assertEquals(0, buffer.readByte())
                            // Type
                            assertEquals(1, buffer.readByte())
                            // Version
                            assertEquals(1, buffer.readByte())
                            // Length
                            assertEquals(984, buffer.readShort())
                            // Milliseconds
                            assertEquals(1, buffer.readByte())
                            assertEquals(11, buffer.readShort())
                            assertEquals(1549880240852, buffer.readLong())
                            // Nanoseconds
                            assertEquals(2, buffer.readByte())
                            assertEquals(7, buffer.readShort())
                            assertEquals(852000000, buffer.readInt())
                            // Source Address
                            assertEquals(3, buffer.readByte())
                            assertEquals(7, buffer.readShort())
                            val srcAddr = ByteArray(4)
                            buffer.readBytes(srcAddr)
                            assertArrayEquals(byteArrayOf(0x7c.toByte(), 0xad.toByte(), 0xd9.toByte(), 0x6b.toByte()), srcAddr)
                            // Destination Address
                            assertEquals(4, buffer.readByte())
                            assertEquals(7, buffer.readShort())
                            val dstAddr = ByteArray(4)
                            buffer.readBytes(dstAddr)
                            assertArrayEquals(byteArrayOf(0xe5.toByte(), 0x23.toByte(), 0xc1.toByte(), 0xc9.toByte()), dstAddr)
                            // Source Port
                            assertEquals(5, buffer.readByte())
                            assertEquals(5, buffer.readShort())
                            assertEquals(11236, buffer.readShort())
                            // Destination Port
                            assertEquals(6, buffer.readByte())
                            assertEquals(5, buffer.readShort())
                            assertEquals(3535, buffer.readShort())
                            // Protocol Code
                            assertEquals(7, buffer.readByte())
                            assertEquals(4, buffer.readShort())
                            assertEquals(PacketTypes.SIP, buffer.readByte())
                            // Payload
                            assertEquals(8, buffer.readByte())
                            assertEquals(934, buffer.readShort())
                        }
                        context.completeNow()
                    }
                    .listen(port, address) {}
            },
            cleanup = {
                tempdir.deleteRecursively()
            }
        )
    }
}