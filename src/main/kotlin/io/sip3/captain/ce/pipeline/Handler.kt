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

package io.sip3.captain.ce.pipeline

import io.netty.buffer.ByteBufUtil
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.domain.payload.Encodable
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import mu.KotlinLogging

abstract class Handler(val vertx: Vertx, val config: JsonObject, val bulkOperationsEnabled: Boolean = true) {

    private val logger = KotlinLogging.logger {}

    abstract fun onPacket(packet: Packet)

    fun handle(packet: Packet) {
        try {
            onPacket(packet)
        } catch (e: Exception) {
            logger.error("Handler 'onPacket()' failed.", e)
            logger.debug {
                val buffer = (packet.payload as Encodable).encode()
                buffer.resetReaderIndex()
                return@debug ByteBufUtil.prettyHexDump(buffer)
            }
        }
    }
}