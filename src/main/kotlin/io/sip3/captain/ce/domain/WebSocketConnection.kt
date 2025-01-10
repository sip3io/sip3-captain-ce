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

import io.netty.buffer.ByteBuf
import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.pipeline.Handler
import io.sip3.commons.vertx.util.localSend
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject

/**
 * Websocket Connection
 */
open class WebSocketConnection(vertx: Vertx, config: JsonObject, aggregationTimeout: Long) : TcpConnection(WebSocketHandler(vertx, config), aggregationTimeout) {

    companion object {

        fun assert(buffer: ByteBuf): Boolean {
            val firstByte = buffer.getByte(buffer.readerIndex())

            return (firstByte >= 0x80.toByte() && firstByte < 0x83.toByte())
                    || (firstByte >= 0x00.toByte() && firstByte < 0x03.toByte())
        }
    }

    override fun assert(buffer: ByteBuf): Boolean {
        return WebSocketConnection.assert(buffer)
    }

    class WebSocketHandler(vertx: Vertx, config: JsonObject) : Handler(vertx, config) {

        override fun onPacket(packet: Packet) {
            vertx.eventBus().localSend(RoutesCE.websocket, listOf(packet))
        }
    }
}