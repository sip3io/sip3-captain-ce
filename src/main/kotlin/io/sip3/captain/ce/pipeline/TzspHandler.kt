/*
 * Copyright 2018-2022 SIP3.IO, Corp.
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

import io.netty.buffer.ByteBuf
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.domain.payload.Encodable
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject

/**
 * Handles TZSP packets
 */
class TzspHandler(vertx: Vertx, config: JsonObject, bulkOperationsEnabled: Boolean) : Handler(vertx, config, bulkOperationsEnabled) {

    companion object {

        const val TAG_END = 0x01
        const val TAG_PADDING = 0x00
    }

    private val ethernetHandler: EthernetHandler by lazy {
        EthernetHandler(vertx, config, bulkOperationsEnabled)
    }

    override fun onPacket(packet: Packet) {
        val buffer = (packet.payload as Encodable).encode()

        // Version & Type
        buffer.skipBytes(2)

        // Encapsulated protocol:
        // 0x01 - Ethernet
        // 0x12 - IEEE 802.11
        // 0x77 - Prism
        // 0x7F - WLAN AVS
        val proto = buffer.readUnsignedShort()

        if (proto == 0x01 && skipTaggedFields(buffer)) {
            ethernetHandler.handle(packet)
        }
    }

    private fun skipTaggedFields(buffer: ByteBuf): Boolean {
        if (buffer.readableBytes() < 2) {
            return false
        }

        val tag = buffer.readByte()

        when (tag.toInt()) {
            TAG_END -> {
                // TAG_END(0x01) means the end of all of the tagged fields
                return true
            }
            TAG_PADDING -> {
                // TAG_PADDING(0x00) can be inserted at any point and should be ignored
                return skipTaggedFields(buffer)
            }
            else -> {
                val length = buffer.readUnsignedByte()
                buffer.skipBytes(length.toInt())

                return skipTaggedFields(buffer)
            }
        }
    }
}
