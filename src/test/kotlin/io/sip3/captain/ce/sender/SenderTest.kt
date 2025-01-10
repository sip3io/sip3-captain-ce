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

package io.sip3.captain.ce.sender

import io.sip3.captain.ce.RoutesCE
import io.sip3.commons.vertx.test.VertxTest
import io.sip3.commons.vertx.util.localSend
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.coAwait
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress

class SenderTest : VertxTest() {

    companion object {

        const val MESSAGE = "Hello, world!"
    }

    private val address = InetAddress.getLoopbackAddress().hostAddress
    private var port = -1

    @BeforeEach
    fun init() {
        port = findRandomPort()
    }

    @Test
    fun `Send UDP packet`() {
        runTest(
            deploy = {
                vertx.deployTestVerticle(Sender::class,
                    config = JsonObject().apply {
                        put("sender", JsonObject().apply {
                            put("uri", "udp://$address:$port")
                        })
                    })
            },
            execute = {
                val message = Buffer.buffer(MESSAGE)
                vertx.eventBus().localSend(RoutesCE.sender, listOf(message))
            },
            assert = {
                vertx.createDatagramSocket()
                    .handler { packet ->
                        val message = packet.data().toString()
                        context.verify {
                            assertEquals(MESSAGE, message)
                        }
                        context.completeNow()
                    }
                    .listen(port, address).coAwait()
            }
        )
    }

    @Test
    fun `Send TCP packet`() {
        runTest(
            deploy = {
                vertx.deployTestVerticle(Sender::class,
                    config = JsonObject().apply {
                        put("sender", JsonObject().apply {
                            put("uri", "tcp://$address:$port")
                        })
                    })
            },
            execute = {
                val message = Buffer.buffer(MESSAGE)
                vertx.setPeriodic(100) { vertx.eventBus().localSend(RoutesCE.sender, listOf(message)) }
            },
            assert = {
                vertx.createNetServer()
                    .connectHandler { socket ->
                        socket.handler { buffer ->
                            val message = buffer.toString()
                            context.verify {
                                assertEquals("$MESSAGE\r\n\r\n3PIS\r\n\r\n", message)
                            }
                            context.completeNow()
                        }
                    }
                    .listen(port, address).coAwait()
            }
        )
    }
}