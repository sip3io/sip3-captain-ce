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

package io.sip3.captain.ce.recording

import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.PacketTypes
import io.sip3.commons.domain.media.MediaControl
import io.sip3.commons.domain.media.Recording
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.domain.payload.RecordingPayload
import io.sip3.commons.util.MediaUtil
import io.sip3.commons.util.getBytes
import io.sip3.commons.vertx.collections.PeriodicallyExpiringHashMap
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import mu.KotlinLogging
import kotlin.math.min

/**
 * Records RTP/RTCP and related ICMP packets
 */
object RecordingManager {

    private val logger = KotlinLogging.logger {}

    private var expirationDelay: Long = 1000
    private var aggregationTimeout: Long = 30000
    private var durationTimeout: Long = 60000

    private var vertx: Vertx? = null

    private lateinit var streams: PeriodicallyExpiringHashMap<String, Stream>

    @Synchronized
    fun getInstance(vertx: Vertx): RecordingManager {
        if (this.vertx == null) {
            this.vertx = vertx
            init()
        }
        return this
    }

    private fun init() {
        vertx!!.orCreateContext.config().getJsonObject("recording")?.let { config ->
            config.getLong("expiration-delay")?.let {
                expirationDelay = it
            }
            config.getLong("aggregation-timeout")?.let {
                expirationDelay = it
            }
            config.getLong("duration-timeout")?.let {
                durationTimeout = it
            }
        }

        streams = PeriodicallyExpiringHashMap.Builder<String, Stream>()
            .delay(expirationDelay)
            .period((aggregationTimeout / expirationDelay).toInt())
            .expireAt { _, recording -> min(recording.updatedAt + aggregationTimeout, recording.createdAt + durationTimeout) }
            .build(vertx!!)

        vertx!!.eventBus().localConsumer<MediaControl>(RoutesCE.media + "_control") { event ->
            try {
                val mediaControl = event.body()
                handleMediaControl(mediaControl)
            } catch (e: Exception) {
                logger.error(e) { "RecordingManager 'handleMediaControl()' failed." }
            }
        }

        vertx!!.eventBus().localConsumer<JsonObject>(RoutesCE.media + "_recording_reset") {
            try {
                reset()
            } catch (e: Exception) {
                logger.error(e) { "RecordingManager 'reset()' failed." }
            }
        }
    }

    private fun handleMediaControl(mediaControl: MediaControl) {
        mediaControl.recording?.let { recording ->
            val stream = Stream().apply {
                mode = recording.mode
                callId = mediaControl.callId
            }

            val src = mediaControl.sdpSession.src
            streams.put(src.rtpId, stream)
            streams.put(src.rtcpId, stream)

            val dst = mediaControl.sdpSession.dst
            streams.put(dst.rtpId, stream)
            streams.put(dst.rtcpId, stream)
        }
    }

    fun record(packet: Packet): RecordingPayload? {
        if (streams.isEmpty()) return null

        val stream = streams.get(MediaUtil.sdpSessionId(packet.srcAddr, packet.srcPort))
            ?: streams.get(MediaUtil.sdpSessionId(packet.dstAddr, packet.dstPort)) ?: return null

        stream.apply {
            updatedAt = System.currentTimeMillis()
        }

        val buffer = (packet.payload as Encodable).encode()

        return RecordingPayload().apply {
            type = packet.rejected?.protocolCode ?: packet.protocolCode
            mode = stream.mode
            callId = stream.callId
            payload = when (packet.protocolCode) {
                PacketTypes.RTP -> {
                    val recordingMark = packet.rejected?.recordingMark ?: packet.recordingMark
                    if (stream.mode == Recording.GDPR) {
                        buffer.getBytes(recordingMark, buffer.readerIndex() - recordingMark)
                    } else {
                        buffer.getBytes(recordingMark, buffer.writerIndex() - recordingMark)
                    }
                }
                PacketTypes.RTCP -> {
                    val recordingMark = packet.rejected?.recordingMark ?: buffer.readerIndex()
                    buffer.getBytes(recordingMark, buffer.writerIndex() - recordingMark)
                }
                else -> return null
            }
        }
    }

    fun reset() {
        streams.clear()
    }
}

private class Stream {

    val createdAt = System.currentTimeMillis()
    var updatedAt = System.currentTimeMillis()
    var mode: Byte = Recording.FULL
    lateinit var callId: String
}