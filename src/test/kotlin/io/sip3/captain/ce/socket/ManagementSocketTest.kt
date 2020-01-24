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

package io.sip3.captain.ce.socket

import io.sip3.captain.ce.RoutesCE
import io.sip3.commons.domain.SdpSession
import io.sip3.commons.vertx.test.VertxTest
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.ServerSocket

class ManagementSocketTest : VertxTest() {

    companion object {

        private val host = JsonObject().apply {
            put("name", "sbc1")
            put("sip", arrayListOf("10.10.10.10", "10.10.20.10:5060"))
        }
    }

    lateinit var config: JsonObject
    private var localPort = -1
    private var remotePort = -1

    @BeforeEach
    fun init() {
        val localSocket = ServerSocket(0)
        localPort = localSocket.localPort
        val remoteSocket = ServerSocket(0)
        remotePort = remoteSocket.localPort
        localSocket.close()
        remoteSocket.close()

        config = JsonObject().apply {
            put("management", JsonObject().apply {
                put("protocol", "udp")
                put("local-host", "127.0.0.1:$localPort")
                put("remote-host", "127.0.0.1:$remotePort")
                put("register-delay", 2000L)
            })
            put("host", host)
        }
    }

    @Test
    fun `sending register to remote host`() {
        runTest(
                deploy = {
                    vertx.deployTestVerticle(ManagementSocket::class, config)
                },
                execute = {},
                assert = {
                    val socket = vertx.createDatagramSocket()
                    socket.listen(remotePort, "127.0.0.1") {}

                    socket.handler { packet ->
                        val jsonObject = packet.data().toJsonObject()
                        context.verify {
                            assertEquals(2, jsonObject.size())
                            assertEquals(ManagementSocket.TYPE_REGISTER, jsonObject.getString("type"))
                            val payload = jsonObject.getJsonObject("payload")
                            assertNotNull(payload.getString("name"))
                            assertEquals(host, payload.getJsonObject("host"))
                            assertEquals(localPort, packet.sender().port())
                        }

                        socket.close()
                        context.completeNow()
                    }
                }
        )
    }

    @Test
    fun `receive SDP info from remote host`() {
        val sdpMessage = JsonObject().apply {
            put("type", ManagementSocket.TYPE_SDP_SESSION)
            put("payload", JsonObject().apply {
                put("id", 10070L)
                put("timestamp", System.currentTimeMillis())
                put("clock_rate", 8000)
                put("codec_ie", 1F)
                put("codec_bpl", 2F)
                put("payload_type", 0)
                put("call_id", "f81d4fae-7dec-11d0-a765-00a0c91e6bf6@foo.bar.com")
            })
        }

        runTest(
                deploy = {
                    vertx.deployTestVerticle(ManagementSocket::class, config)
                },
                execute = {
                    val socket = vertx.createDatagramSocket()
                    socket.send(sdpMessage.toBuffer(), localPort, "127.0.0.1") {
                        socket.close()
                    }
                },
                assert = {
                    vertx.eventBus().consumer<SdpSession>(RoutesCE.sdp) { message ->
                        context.verify {
                            sdpMessage.getJsonObject("payload").apply {
                                assertEquals(getLong("id"), message.body().id)
                                assertEquals( getString("call_id"), message.body().callId)
                            }

                        }
                        context.completeNow()
                    }
                }
        )
    }
}