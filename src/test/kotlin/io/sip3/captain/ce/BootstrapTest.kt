/*
 * Copyright 2018-2020 SIP3.IO, Inc.
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
import io.vertx.kotlin.core.datagram.listenAwait
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.net.InetAddress

class BootstrapTest : VertxTest() {

    companion object {

        val PCAP_FILE = File("src/test/resources/pcap/BootstrapTest.pcap")
    }

    @Test
    fun `Send some packets trough the entire pipeline to SIP3 Salto`() {
        val tempdir = createTempDir()
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
                    vertx.setPeriodic(100) { file.setLastModified(System.currentTimeMillis()) }
                },
                assert = {
                    vertx.createDatagramSocket()
                            .handler { packet ->
                                val buffer = packet.data()
                                context.verify {
                                    assertEquals(989, buffer.length())
                                    // Prefix
                                    assertArrayEquals(Encoder.PREFIX, buffer.getBytes(0, 4))
                                    // Compressed
                                    assertEquals(0, buffer.getByte(4))
                                    // Type
                                    assertEquals(1, buffer.getByte(5))
                                    // Version
                                    assertEquals(1, buffer.getByte(6))
                                    // Length
                                    assertEquals(984, buffer.getShort(7))
                                    // Milliseconds
                                    assertEquals(1, buffer.getByte(9))
                                    assertEquals(11, buffer.getShort(10))
                                    assertEquals(1549880240852, buffer.getLong(12))
                                    // Nanoseconds
                                    assertEquals(2, buffer.getByte(20))
                                    assertEquals(7, buffer.getShort(21))
                                    assertEquals(852000000, buffer.getInt(23))
                                    // Source Address
                                    assertEquals(3, buffer.getByte(27))
                                    assertEquals(7, buffer.getShort(28).toInt())
                                    assertArrayEquals(byteArrayOf(0x7c.toByte(), 0xad.toByte(), 0xd9.toByte(), 0x6b.toByte()), buffer.getBytes(30, 34))
                                    // Destination Address
                                    assertEquals(4, buffer.getByte(34))
                                    assertEquals(7, buffer.getShort(35).toInt())
                                    assertArrayEquals(byteArrayOf(0xe5.toByte(), 0x23.toByte(), 0xc1.toByte(), 0xc9.toByte()), buffer.getBytes(37, 41))
                                    // Source Port
                                    assertEquals(5, buffer.getByte(41))
                                    assertEquals(5, buffer.getShort(42))
                                    assertEquals(11236, buffer.getShort(44).toInt())
                                    // Destination Port
                                    assertEquals(6, buffer.getByte(46))
                                    assertEquals(5, buffer.getShort(47))
                                    assertEquals(3535, buffer.getShort(49).toInt())
                                    // Protocol Code
                                    assertEquals(7, buffer.getByte(51))
                                    assertEquals(4, buffer.getShort(52))
                                    assertEquals(PacketTypes.SIP, buffer.getByte(54))
                                    // Payload
                                    assertEquals(8, buffer.getByte(55))
                                    assertEquals(934, buffer.getShort(56).toInt())
                                }
                                context.completeNow()
                            }
                            .listenAwait(port, address)
                },
                cleanup = {
                    tempdir.deleteRecursively()
                }
        )
    }
}