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
import io.sip3.captain.ce.pipeline.Handler
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.domain.payload.Encodable
import java.util.*

// Important! To make `TcpConnection` code easier we decided to ignore a very rare scenario:
// 1. Application packet is split into separate segments. Last segment got over MAX sequence number.
abstract class TcpConnection(val handler: Handler, val aggregationTimeout: Long) {

    companion object {

        const val BUFFER_SIZE_THRESHOLD = Short.MAX_VALUE
    }

    var lastUpdated = System.currentTimeMillis()

    protected val segments = TreeMap<Long, TcpSegment>()

    abstract fun assert(buffer: ByteBuf): Boolean

    fun onTcpSegment(sequenceNumber: Long, packet: Packet) {
        val buffer = (packet.payload as Encodable).encode()

        // Add to segments map
        val segment = TcpSegment().apply {
            this.sequenceNumber = sequenceNumber
            this.packet = packet
            this.payloadLength = buffer.readableBytes()
        }
        segments[sequenceNumber] = segment

        // Check previous segment if current segment starts from SIP word
        if (assert(buffer)) {
            processPreviousTcpSegment(segment)
        }
    }

    open fun processTcpSegments() {
        val sequenceNumbers = mutableListOf<Long>()
        walkThroughTcpSegments(sequenceNumbers)

        while (sequenceNumbers.isNotEmpty()) {
            val lastSegment = segments[sequenceNumbers.last()] ?: continue

            if (lastSegment.timestamp + aggregationTimeout < System.currentTimeMillis()) {
                val compositeBuffer = Unpooled.compositeBuffer()

                sequenceNumbers.forEach { sequenceNumber ->
                    val segment = segments.remove(sequenceNumber)
                    if (segment != null) {
                        val payload = segment.packet.payload as Encodable
                        compositeBuffer.addComponent(true, payload.encode())
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

    protected fun walkThroughTcpSegments(sequenceNumbers: MutableList<Long>, nextSequenceNumber: Long = -1) {
        val (sequenceNumber, segment) = segments.ceilingEntry(nextSequenceNumber) ?: return

        if (nextSequenceNumber == -1L || nextSequenceNumber == sequenceNumber) {
            sequenceNumbers.add(sequenceNumber)
            walkThroughTcpSegments(sequenceNumbers, sequenceNumber + segment.payloadLength)
        }
    }

    private fun processPreviousTcpSegment(currentSegment: TcpSegment) {
        val (sequenceNumber, segment) = segments.lowerEntry(currentSegment.sequenceNumber) ?: return

        if (sequenceNumber + segment.payloadLength == currentSegment.sequenceNumber) {
            val packet = segment.packet
            val buffer = (packet.payload as Encodable).encode()

            if (assert(buffer)) {
                handler.handle(packet)
                segments.remove(sequenceNumber)
            }
        }
    }
}