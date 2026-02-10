/*
 * Copyright 2018-2026 SIP3.IO, Corp.
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
import io.sip3.captain.ce.pipeline.SmppHandler
import io.sip3.captain.ce.util.SmppUtil
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject

/**
 * SMPP over TCP Connection
 */
open class SmppConnection(vertx: Vertx, config: JsonObject, aggregationTimeout: Long) : TcpConnection(SmppHandler(vertx, config, false), aggregationTimeout) {

    companion object {

        fun assert(buffer: ByteBuf): Boolean {
            return SmppUtil.isPdu(buffer)
        }
    }

    override fun assert(buffer: ByteBuf): Boolean {
        return SmppConnection.assert(buffer)
    }
}