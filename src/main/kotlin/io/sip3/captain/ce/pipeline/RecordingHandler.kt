/*
 * Copyright 2018-2021 SIP3.IO, Inc.
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
import io.sip3.commons.PacketTypes
import io.sip3.commons.domain.media.MediaControl
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.domain.payload.RecordingPayload
import io.sip3.commons.util.MediaUtil.sdpSessionId
import io.sip3.commons.util.getBytes
import io.sip3.commons.vertx.util.localSend
import io.vertx.core.Context
import mu.KotlinLogging

/**
 * Records RTP packets
 */
class RecordingHandler(context: Context, bulkOperationsEnabled: Boolean) : Handler(context, bulkOperationsEnabled) {

    private val logger = KotlinLogging.logger {}

    private val packets = mutableListOf<Packet>()
    private var bulkSize = 1

    private var expirationDelay: Long = 4000
    private var aggregationTimeout: Long = 30000

    private val vertx = context.owner()

    private val recordingStreams = mutableMapOf<Long, RecordingStream>()

    init {
        context.config().getJsonObject("rtp")?.getJsonObject("recording")?.let { config ->
            if (bulkOperationsEnabled) {
                config.getInteger("bulk-size")?.let {
                    bulkSize = it
                }
            }
            config.getLong("expiration-delay")?.let {
                expirationDelay = it
            }
            config.getLong("aggregation-timeout")?.let {
                aggregationTimeout = it
            }
        }

        vertx.setPeriodic(expirationDelay) {
            terminateExpiredRecordingStreams()
        }

        vertx.eventBus().localConsumer<MediaControl>(RoutesCE.media + "_control") { event ->
            val mediaControl = event.body()
            try {
                handleMediaControl(mediaControl)
            } catch (e: Exception) {
                logger.error(e) { "RecordingHandler 'handleMediaControl()' failed." }
            }
        }
    }

    override fun onPacket(packet: Packet) {
        val recordingStream = recordingStreams[sdpSessionId(packet.srcAddr, packet.srcPort)]
            ?: recordingStreams[sdpSessionId(packet.dstAddr, packet.dstPort)]

        if (recordingStream != null) {
            recordingStream.updatedAt = System.currentTimeMillis()

            val buffer = (packet.payload as Encodable).encode()

            val p = Packet().apply {
                timestamp = packet.timestamp
                srcAddr = packet.srcAddr
                dstAddr = packet.dstAddr
                srcPort = packet.srcPort
                dstPort = packet.dstPort
                protocolCode = PacketTypes.REC
                payload = RecordingPayload().apply {
                    type = recordingStream.type
                    callId = recordingStream.callId

                    payload = when (type) {
                        RecordingPayload.TYPE_RTP -> {
                            buffer.resetReaderIndex()
                            buffer.getBytes()
                        }
                        RecordingPayload.TYPE_RTP_GDPR -> {
                            val offset = buffer.readerIndex()
                            buffer.resetReaderIndex()
                            buffer.getBytes(buffer.readerIndex(), offset - buffer.readerIndex())
                        }
                        else -> throw IllegalArgumentException("Unsupported recording type: $type")
                    }
                }
            }
            packets.add(p)

            if (packets.size >= bulkSize) {
                vertx.eventBus().localSend(RoutesCE.encoder, packets.toList())
                packets.clear()
            }
        }
    }

    private fun handleMediaControl(mediaControl: MediaControl) {
        mediaControl.recording?.let { recording ->
            val recordingStream = RecordingStream().apply {
                type = when (recording.mode) {
                    0x01.toByte() -> RecordingPayload.TYPE_RTP
                    0x02.toByte() -> RecordingPayload.TYPE_RTP_GDPR
                    else -> throw IllegalArgumentException("Unsupported recording mode: ${recording.mode}")
                }
                callId = mediaControl.callId
            }

            val src = mediaControl.sdpSession.src
            recordingStreams[src.rtpId] = recordingStream

            val dst = mediaControl.sdpSession.dst
            recordingStreams[dst.rtpId] = recordingStream
        }
    }

    private fun terminateExpiredRecordingStreams() {
        val now = System.currentTimeMillis()

        recordingStreams.filterValues { recordingStream ->
            recordingStream.updatedAt + aggregationTimeout < now
        }.forEach { (rtpId, _) ->
            recordingStreams.remove(rtpId)
        }
    }

    inner class RecordingStream {

        var updatedAt: Long = System.currentTimeMillis()
        var type: Byte = 0
        lateinit var callId: String
    }
}