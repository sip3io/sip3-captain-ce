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

package io.sip3.captain.ce.socket

import io.sip3.captain.ce.RoutesCE
import io.sip3.commons.domain.media.MediaControl
import io.sip3.commons.domain.media.Recording
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

        val MEDIA_CONTROL = JsonObject().apply {
            put("type", ManagementSocket.TYPE_MEDIA_CONTROL)
            put("payload", JsonObject().apply {
                // Timestamp
                put("timestamp", System.currentTimeMillis())
                // Call ID
                put("call_id", "f81d4fae-7dec-11d0-a765-00a0c91e6bf6@foo.bar.com")
                // SDP Session
                put("sdp_session", JsonObject().apply {
                    put("src", JsonObject().apply {
                        put("addr", "127.0.0.1")
                        put("rtp_port", 1000)
                        put("rtcp_port", 1001)
                    })
                    put("dst", JsonObject().apply {
                        put("addr", "127.0.0.1")
                        put("rtp_port", 2000)
                        put("rtcp_port", 2001)
                    })
                    put("codecs", listOf(JsonObject().apply {
                        put("name", "PCMU")
                        put("payload_types", listOf(0))
                        put("clock_rate", 8000)
                        put("ie", 1F)
                        put("bpl", 2F)
                    }))
                    put("ptime", 20)
                })
                // Recording
                put("recording", JsonObject().apply {
                    put("mode", Recording.RTP_GDPR)
                })
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
                // 2. Send back `MEDIA_CONTROL`
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
                    remoteSocket.send(MEDIA_CONTROL.toBuffer(), sender.port(), sender.host()) {}
                }

                // 1. Retrieve and assert `MEDIA_CONTROL`
                vertx.eventBus().consumer<MediaControl>(RoutesCE.media + "_control") { event ->
                    context.verify {
                        val payload = JsonObject(Json.encode(event.body()))
                        assertEquals(MEDIA_CONTROL.getJsonObject("payload"), payload)
                    }
                    context.completeNow()
                }
            }
        )
    }
}