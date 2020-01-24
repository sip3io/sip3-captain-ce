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

package io.sip3.captain.ce.sender

import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.USE_LOCAL_CODEC
import io.sip3.commons.vertx.test.VertxTest
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.datagram.listenAwait
import io.vertx.kotlin.core.net.listenAwait
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SenderTest : VertxTest() {

    companion object {

        const val MESSAGE = "Hello, world!"
        const val PORT = 15061
        const val HOST = "127.0.0.1"
    }

    @Test
    fun `Send UDP packet`() {
        runTest(
                deploy = {
                    vertx.deployTestVerticle(Sender::class,
                            config = JsonObject().apply {
                                put("sender", JsonObject().apply {
                                    put("uri", "udp://$HOST:$PORT")
                                })
                            })
                },
                execute = {
                    val message = Buffer.buffer(MESSAGE)
                    vertx.eventBus().send(RoutesCE.sender, listOf(message), USE_LOCAL_CODEC)
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
                            .listenAwait(PORT, HOST)
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
                                    put("uri", "tcp://$HOST:$PORT")
                                })
                            })
                },
                execute = {
                    val message = Buffer.buffer(MESSAGE)
                    vertx.setPeriodic(100) { vertx.eventBus().send(RoutesCE.sender, listOf(message), USE_LOCAL_CODEC) }
                },
                assert = {
                    vertx.createNetServer()
                            .connectHandler { socket ->
                                socket.handler { buffer ->
                                    val message = buffer.toString()
                                    context.verify {
                                        assertEquals(MESSAGE, message)
                                    }
                                    context.completeNow()
                                }
                            }
                            .listenAwait(PORT, HOST)
                }
        )
    }
}