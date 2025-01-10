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

package io.sip3.captain.ce.domain

import io.netty.buffer.Unpooled
import io.sip3.captain.ce.RoutesCE
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.util.readByteBuf
import io.sip3.commons.vertx.test.VertxTest
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WebSocketConnectionTest : VertxTest() {

    val NOT_FIN_CONTINUATION = Unpooled.wrappedBuffer(byteArrayOf(0x00.toByte()))
    val NOT_FIN_TEXT = Unpooled.wrappedBuffer(byteArrayOf(0x01.toByte()))
    val NOT_FIN_BINARY = Unpooled.wrappedBuffer(byteArrayOf(0x02.toByte()))

    val FIN_CONTINUATION = Unpooled.wrappedBuffer(byteArrayOf(0x80.toByte()))
    val FIN_TEXT = Unpooled.wrappedBuffer(byteArrayOf(0x81.toByte()))
    val FIN_BINARY = Unpooled.wrappedBuffer(byteArrayOf(0x82.toByte()))

    val FIN_PING = Unpooled.wrappedBuffer(byteArrayOf(0x89.toByte()))
    val FIN_PONG = Unpooled.wrappedBuffer(byteArrayOf(0x8A.toByte()))

    val WS_MESSAGE = readByteBuf("/raw/ws_message")

    val PACKET_1 = Packet().apply {
        timestamp = System.currentTimeMillis()
        nanos = 0
        srcAddr = byteArrayOf(0x0a, 0x0a, 0x0a, 0x0a)
        srcPort = 5060
        dstAddr = byteArrayOf(0x0a, 0x0a, 0x0a, 0x0b)
        dstPort = 35060

        payload = ByteBufPayload(WS_MESSAGE)
    }


    @Test
    fun `Validate Websocket connection assert`() {
        assertTrue(WebSocketConnection.assert(NOT_FIN_CONTINUATION))
        assertTrue(WebSocketConnection.assert(NOT_FIN_TEXT))
        assertTrue(WebSocketConnection.assert(NOT_FIN_BINARY))

        assertTrue(WebSocketConnection.assert(FIN_CONTINUATION))
        assertTrue(WebSocketConnection.assert(FIN_TEXT))
        assertTrue(WebSocketConnection.assert(FIN_BINARY))

        assertFalse(WebSocketConnection.assert(FIN_PING))
        assertFalse(WebSocketConnection.assert(FIN_PONG))

        assertTrue(WebSocketConnection.assert((PACKET_1.payload as ByteBufPayload).encode()))
    }

    @Test
    fun `Validate Websocket connection processing`() {
        lateinit var connection: WebSocketConnection
        runTest(
            deploy = {
                connection = WebSocketConnection(vertx, JsonObject(), 100L)
            },
            execute = {
                connection.onTcpSegment(1, PACKET_1)
                vertx.setTimer(200L) {
                    connection.processTcpSegments()
                }
            },
            assert = {
                vertx.eventBus().localConsumer<List<Packet>>(RoutesCE.websocket) { event ->
                    val packet = event.body().first()
                    context.verify {
                        assertEquals(PACKET_1.timestamp, packet.timestamp)
                        assertEquals(PACKET_1.srcAddr, packet.srcAddr)
                        assertEquals(PACKET_1.srcPort, packet.srcPort)
                        assertEquals(PACKET_1.dstAddr, packet.dstAddr)
                        assertEquals(PACKET_1.dstPort, packet.dstPort)
                        assertEquals(PACKET_1.dstPort, packet.dstPort)
                        assertTrue(packet.payload is ByteBufPayload)
                        val payload = (packet.payload as ByteBufPayload).encode()
                        assertEquals(WS_MESSAGE.readableBytes(), payload.readableBytes())
                    }
                    context.completeNow()
                }
            }
        )
    }
}