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

import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.domain.Packet
import io.sip3.captain.ce.recording.RecordingManager
import io.sip3.commons.PacketTypes
import io.sip3.commons.domain.payload.ByteArrayPayload
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.util.getBytes
import io.sip3.commons.vertx.util.localSend
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject

/**
 * Handles RTCP packets
 */
class RtcpHandler(vertx: Vertx, config: JsonObject, bulkOperationsEnabled: Boolean) : Handler(vertx, config, bulkOperationsEnabled) {

    private val packets = mutableListOf<Packet>()
    private var bulkSize = 1

    private val recordingManager = RecordingManager.getInstance(vertx, config)

    init {
        config.getJsonObject("rtcp")?.let { rtcpConfig ->
            if (bulkOperationsEnabled) {
                rtcpConfig.getInteger("bulk-size")?.let { bulkSize = it }
            }
        }
    }

    override fun onPacket(packet: Packet) {
        val buffer = (packet.payload as Encodable).encode()

        packet.apply {
            protocolCode = PacketTypes.RTCP
            payload = run {
                val bytes = buffer.getBytes()
                return@run ByteArrayPayload(bytes)
            }
        }

        val recording = recordingManager.record(packet)

        if (recording != null) {
            val p = packet.rejected ?: packet
            p.apply {
                protocolCode = PacketTypes.REC
                payload = recording
            }
            packets.add(p)
        } else {
            packets.add(packet)
        }

        if (packets.size >= bulkSize) {
            vertx.eventBus().localSend(RoutesCE.encoder, packets.toList())
            packets.clear()
        }
    }
}