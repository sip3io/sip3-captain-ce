/*
 * Copyright 2018-2024 SIP3.IO, Corp.
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
import io.netty.buffer.Unpooled
import io.sip3.captain.ce.pipeline.SipHandler
import io.sip3.captain.ce.util.SipUtil
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.domain.payload.Encodable
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject

/**
 * SIP over TCP Connection
 */
open class SipConnection(vertx: Vertx, config: JsonObject, aggregationTimeout: Long) : TcpConnection(SipHandler(vertx, config, false), aggregationTimeout) {

    companion object {

        fun assert(buffer: ByteBuf): Boolean {
            return SipUtil.startsWithSipWord(buffer)
        }
    }

    override fun assert(buffer: ByteBuf): Boolean {
        return SipConnection.assert(buffer)
    }

    override fun processTcpSegments() {
        val sequenceNumbers = mutableListOf<Long>()
        walkThroughTcpSegments(sequenceNumbers)

        while (sequenceNumbers.isNotEmpty()) {
            val lastSegment = segments[sequenceNumbers.last()] ?: continue
            if (lastSegment.timestamp + aggregationTimeout < System.currentTimeMillis()) {
                var compositeBuffer = Unpooled.compositeBuffer()
                sequenceNumbers.forEach { sequenceNumber ->
                    val segment = segments.remove(sequenceNumber) ?: return@forEach
                    val buffer = (segment.packet.payload as Encodable).encode()

                    if (compositeBuffer.readableBytes() > BUFFER_SIZE_THRESHOLD) {
                        val sipWordPosition = SipUtil.findSipWord(buffer)
                        when  {
                            sipWordPosition < 0 -> {
                                compositeBuffer.addComponent(true, buffer)
                            }

                            sipWordPosition == 0 -> {
                                val nextSipWordPosition = SipUtil.findSipWord(buffer, 4)
                                val packet = segment.packet

                                var remainBuffer: ByteBuf? = null
                                if (nextSipWordPosition < 0) {
                                    compositeBuffer.addComponent(true, buffer)
                                } else {
                                    compositeBuffer.addComponent(true, buffer.slice(0, nextSipWordPosition))
                                    remainBuffer = buffer.slice(nextSipWordPosition, buffer.writerIndex() - nextSipWordPosition)
                                }

                                packet.payload = ByteBufPayload(compositeBuffer)
                                handler.handle(packet)
                                compositeBuffer = Unpooled.compositeBuffer()

                                remainBuffer?.let { compositeBuffer.addComponent(true, it) }
                            }

                            else -> {
                                compositeBuffer.addComponent(true, buffer.slice(0, sipWordPosition))

                                val packet = segment.packet
                                packet.payload = ByteBufPayload(compositeBuffer)

                                handler.handle(packet)
                                compositeBuffer = Unpooled.compositeBuffer()

                                val remainBuffer = buffer.slice(sipWordPosition, buffer.writerIndex() - sipWordPosition)
                                compositeBuffer.addComponent(true, remainBuffer)
                            }
                        }
                    } else if (compositeBuffer.readableBytes() > 0 || assert(buffer)) {
                        compositeBuffer.addComponent(true, buffer)
                    }
                }

                val packet = lastSegment.packet
                packet.payload = ByteBufPayload(compositeBuffer)

                handler.handle(packet)
            }

            sequenceNumbers.clear()
            walkThroughTcpSegments(sequenceNumbers, lastSegment.sequenceNumber + lastSegment.payloadLength)
        }
    }
}