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
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.net.InetAddress

class ManagementSocketTest : VertxTest() {

    companion object {

        val HOST = JsonObject().apply {
            put("name", "sbc1")
            put("sip", arrayListOf("10.10.10.10", "10.10.20.10:5060"))
        }

        val SDP_INFO = JsonObject().apply {
            put("type", ManagementSocket.TYPE_SDP_SESSION)
            put("payload", JsonObject().apply {
                put("id", 10070L)
                put("timestamp", System.currentTimeMillis())
                put("codec", JsonObject().apply {
                    put("name", "PCMU")
                    put("ie", 1F)
                    put("bpl", 2F)
                    put("payload_type", 0)
                    put("clock_rate", 8000)
                })
                put("ptime", 20)
                put("call_id", "f81d4fae-7dec-11d0-a765-00a0c91e6bf6@foo.bar.com")
            })
        }
    }

    @Test
    fun `Send 'REGISTER' to remote host and receive back 'SDP_INFO'`() {
        val remoteAddr = InetAddress.getLoopbackAddress().hostAddress
        val remotePort = findRandomPort()

        runTest(
                deploy = {
                    vertx.deployTestVerticle(ManagementSocket::class, JsonObject().apply {
                        put("management", JsonObject().apply {
                            put("uri", "udp://$remoteAddr:$remotePort")
                            put("register-delay", 100L)
                        })
                        put("host", HOST)
                    })
                },
                assert = {
                    val remoteSocket = vertx.createDatagramSocket().listen(remotePort, remoteAddr) {}

                    // 1. Retrieve and assert `REGISTER` message
                    // 2. Send back `SDP_INFO`
                    remoteSocket.handler { packet ->
                        val json = packet.data().toJsonObject()
                        context.verify {
                            assertEquals(ManagementSocket.TYPE_REGISTER, json.getString("type"))

                            val payload = json.getJsonObject("payload")

                            assertNotNull(payload.getLong("timestamp"))
                            assertNotNull(payload.getString("name"))
                            assertEquals(HOST, payload.getJsonObject("config")?.getJsonObject("host"))
                        }

                        val sender = packet.sender()
                        remoteSocket.send(SDP_INFO.toBuffer(), sender.port(), sender.host()) {}
                    }

                    // 1. Retrieve and assert `SDP_INFO`
                    vertx.eventBus().consumer<SdpSession>(RoutesCE.sdp) { event ->
                        context.verify {
                            val payload = JsonObject(Json.encode(event.body()))
                            assertEquals(SDP_INFO.getJsonObject("payload"), payload)
                        }
                        context.completeNow()
                    }
                }
        )
    }
}