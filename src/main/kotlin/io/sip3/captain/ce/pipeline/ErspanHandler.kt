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

import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.domain.payload.Encodable
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject

/**
 * Handles ERSPAN packets
 */
class ErspanHandler(vertx: Vertx, config: JsonObject, bulkOperationsEnabled: Boolean) : Handler(vertx, config, bulkOperationsEnabled) {

    private val ethernetHandler: EthernetHandler by lazy {
        EthernetHandler(vertx, config, bulkOperationsEnabled)
    }

    override fun onPacket(packet: Packet) {
        val buffer = (packet.payload as Encodable).encode()

        // Important! To make it easier we decided to skip header and send payload to `EthernetHandler`.
        // So far `sip3-captain` works with such type of encapsulations only.
        buffer.skipBytes(8)

        ethernetHandler.handle(packet)
    }
}
