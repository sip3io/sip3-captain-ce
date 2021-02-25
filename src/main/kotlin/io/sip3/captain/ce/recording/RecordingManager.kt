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

package io.sip3.captain.ce.recording

import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.domain.media.MediaControl
import io.sip3.commons.domain.media.Recording
import io.sip3.commons.util.MediaUtil
import io.vertx.core.Vertx

/**
 * Records RTP/RTCP and related ICMP packets
 */
object RecordingManager {

    private val packets = mutableListOf<Packet>()
    private var bulkSize = 16

    private var expirationDelay: Long = 4000
    private var aggregationTimeout: Long = 30000

    private var vertx: Vertx? = null

    private var streams = mutableMapOf<Long, Stream>()

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
            config.getInteger("bulk-size")?.let {
                bulkSize = it
            }
            config.getLong("expiration-delay")?.let {
                expirationDelay = it
            }
            config.getLong("aggregation-timeout")?.let {
                expirationDelay = it
            }
        }

        vertx!!.setPeriodic(expirationDelay) {
            val now = System.currentTimeMillis()

            streams = streams.filterValues { steam ->
                steam.updatedAt + aggregationTimeout < now
            }.toMutableMap()
        }

        vertx!!.eventBus().localConsumer<MediaControl>(RoutesCE.media + "_control") { event ->
            val mediaControl = event.body()

            mediaControl.recording?.let { recording ->
                val stream = Stream().apply {
                    mode = recording.mode
                    callId = mediaControl.callId
                }

                val src = mediaControl.sdpSession.src
                streams[src.rtpId] = stream
                streams[src.rtcpId] = stream

                val dst = mediaControl.sdpSession.dst
                streams[dst.rtpId] = stream
                streams[dst.rtcpId] = stream
            }
        }
    }

    fun check(packet: Packet): Boolean {
        val stream = streams[MediaUtil.sdpSessionId(packet.srcAddr, packet.srcPort)]
            ?: streams[MediaUtil.sdpSessionId(packet.dstAddr, packet.dstPort)]

        return stream?.apply { updatedAt = System.currentTimeMillis() } == null
    }

    fun record(packet: Packet) {
        // TODO...
    }
}

private class Stream {

    var updatedAt = System.currentTimeMillis()

    var mode: Byte = Recording.RTP
    lateinit var callId: String
}