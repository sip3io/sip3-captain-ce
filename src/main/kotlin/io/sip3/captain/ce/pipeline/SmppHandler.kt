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

import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.domain.Packet
import io.sip3.captain.ce.util.SmppUtil
import io.sip3.commons.ProtocolCodes
import io.sip3.commons.domain.payload.ByteArrayPayload
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.util.getBytes
import io.sip3.commons.vertx.util.localSend
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject

/**
 * Handles SMPP packets
 */
class SmppHandler(vertx: Vertx, config: JsonObject, bulkOperationsEnabled: Boolean) : Handler(vertx, config, bulkOperationsEnabled) {

    private val packets = mutableListOf<Packet>()
    private var bulkSize = 1

    init {
        if (bulkOperationsEnabled) {
            config.getJsonObject("smpp")?.let { smppConfig ->
                smppConfig.getInteger("bulk_size")?.let { bulkSize = it }
            }
        }
    }

    override fun onPacket(packet: Packet) {
        val buffer = (packet.payload as Encodable).encode()

        if (SmppUtil.checkMinPduLength(buffer)) {
            val offset = buffer.readerIndex()

            val length = buffer.getUnsignedInt(offset).toInt()
            if (buffer.readableBytes() >= length) {

                val command = buffer.getUnsignedInt(offset + 4)
                if (SmppUtil.isPduCommand(command)) {
                    val p = packet.clone()
                    p.apply {
                        protocolCode = ProtocolCodes.SMPP
                        payload = run {
                            val bytes = buffer.getBytes(offset, length)
                            return@run ByteArrayPayload(bytes)
                        }
                    }
                    packets.add(p)

                    if (packets.size >= bulkSize) {
                        vertx.eventBus().localSend(RoutesCE.encoder, packets.toList())
                        packets.clear()
                    }

                    buffer.readerIndex(offset + length)
                    if (buffer.readableBytes() > 0) {
                        onPacket(packet)
                    }
                }
            }
        }
    }
}