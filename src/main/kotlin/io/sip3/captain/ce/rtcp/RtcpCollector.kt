/*
 * Copyright 2018-2019 SIP3.IO, Inc.
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

package io.sip3.captain.ce.rtcp

import io.sip3.captain.ce.Routes
import io.sip3.captain.ce.USE_LOCAL_CODEC
import io.sip3.captain.ce.domain.*
import io.sip3.captain.ce.domain.payload.ByteArrayPayload
import io.sip3.captain.ce.domain.payload.RtpReportPayload
import io.vertx.core.AbstractVerticle
import mu.KotlinLogging
import kotlin.experimental.and

/**
 * RTCP Packet collector
 */
class RtcpCollector : AbstractVerticle() {

    private val logger = KotlinLogging.logger {}

    companion object {

        const val MAX_VALID_JITTER = 10_000

        const val R0 = 93.2F
        const val I_DELAY_DEFAULT = 0.65
        const val MOS_MIN = 1F
        const val MOS_MAX = 4.5F
    }

    private var bulkSize: Int = 1
    private var expirationDelay: Long = 4000
    private var aggregationTimeout: Long = 30000

    private val sessions = mutableMapOf<Long, RtcpSession>()
    private val sdpSessions = mutableMapOf<Long, SdpSession>()
    private val reports = mutableListOf<Packet>()

    override fun start() {
        config().getJsonObject("rtcp")?.getJsonObject("collector")?.let { config ->
            config.getInteger("bulk-size")?.let { bulkSize = it }
            config.getLong("expiration-delay")?.let { expirationDelay = it }
            config.getLong("aggregation-timeout")?.let { aggregationTimeout = it }
        }

        // Consumer for sdpSession info from remote host
        vertx.eventBus().localConsumer<SdpSession>(Routes.sdp) { event ->
            try {
                val sdpSession = event.body()
                onSdpSession(sdpSession)
            } catch (e: Exception) {
                logger.error("RtcpCollector 'onSdpSession()' failed.", e)
            }
        }

        // Periodic task for session expiration
        vertx.setPeriodic(expirationDelay) {
            val now = System.currentTimeMillis()
            // Sessions cleanup
            sessions.filterValues { it.lastPacketTimestamp + aggregationTimeout < now }
                    .forEach { (sessionId, _) ->
                        sessions.remove(sessionId)
                    }

            // SDP sessions cleanup
            sdpSessions.filterValues { it.timestamp + aggregationTimeout < now }
                    .forEach { (key, _) ->
                        sdpSessions.remove(key)
                    }
        }

        vertx.eventBus().localConsumer<Packet>(Routes.rtcp) { event ->
            try {
                val packet = event.body()
                onPacket(packet)
            } catch (e: Exception) {
                logger.error("RtcpCollector 'onPacket()' failed.", e)
            }
        }

    }

    private fun onSdpSession(sdpSession: SdpSession) {
        sdpSessions.put(sdpSession.id, sdpSession)
    }

    private fun onPacket(packet: Packet) {
        val payload = packet.payload as ByteArrayPayload
        val buffer = payload.encode()
        val packetType = buffer.getUnsignedByte(1).toInt()

        when (packetType) {
            // SR: Sender Report RTCP Packet
            200 -> {
                val report = SenderReport().apply {
                    // Reception report count
                    reportCount = buffer.readByte().and(31)
                    // Packet Type
                    buffer.skipBytes(1)
                    // Length
                    length = buffer.readUnsignedShort()
                    // Sender SSRC
                    senderSsrc = buffer.readUnsignedInt()
                    // NTP Timestamp
                    ntpTimestampMsw = buffer.readUnsignedInt()
                    ntpTimestampLsw = buffer.readUnsignedInt()

                    // RTP Timestamp
                    rtpTimestamp = buffer.readUnsignedInt()
                    // Sender's packet count
                    senderPacketCount = buffer.readUnsignedInt()
                    // Sender's octet count
                    buffer.skipBytes(4)

                    // Reports
                    for (index in 0..reportCount) {
                        reportBlocks.add(RtcpReportBlock().apply {
                            // SSRC of
                            ssrc = buffer.readLong()
                            buffer.readLong().let { value ->
                                fractionLost = ((value and 0xF000) shr 24).toShort()
                                cumulativePacketLost = value and 0x0FFF
                            }
                            extendedSeqNumber = buffer.readLong()
                            interarrivalJitter = buffer.readLong()
                            lsrTimestamp = buffer.readLong()
                            dlsrTimestamp = buffer.readLong()
                        })
                    }
                }
                onSenderReport(packet, report)
            }
            else -> {
                // Ignore:
                // 201 RR: Receiver Report RTCP Packet
                // 202 SDES: Source Description RTCP Packet
                // 203 BYE: Goodbye RTCP Packet
                // 204 APP: Application-Defined RTCP Packet
                // Undefined RTCP packet
            }
        }


    }

    private fun onSenderReport(packet: Packet, senderReport: SenderReport) {
        senderReport.reportBlocks.forEach { report ->
            val sessionId = (senderReport.senderSsrc shl 32 or report.ssrc)
            val rtcpSession = sessions.getOrPut(sessionId) {
                RtcpSession().apply {
                    createdAt = packet.timestamp
                    dstAddr = packet.dstAddr
                    dstPort = packet.dstPort
                    srcAddr = packet.srcAddr
                    srcPort = packet.srcPort

                    packetType = senderReport.packetType.toByte()
                    ssrc = report.ssrc
                    firstReport = report

                    sdpSession = sdpSessions[srcSdpSessionId] ?: sdpSessions[dstSdpSessionId]
                }
            }.apply {
                rtcpPacketCount++

                receivedPacketCount = senderReport.senderPacketCount.toInt()
                lostPacketCount = report.cumulativePacketLost.toInt()

                // If interarrival jitter is greater than maximum, current jitter is bad
                if (report.interarrivalJitter < MAX_VALID_JITTER) {
                    lastJitter = report.interarrivalJitter.toFloat()
                }
                // Save jitter statistic
                when {
                    lastJitter < minJitter -> minJitter = lastJitter
                    lastJitter > maxJitter -> maxJitter = lastJitter
                }
                jitterSum += lastJitter

                lastReport = report
                lastPacketTimestamp = packet.timestamp.time
            }

            val rtpReport = createRtpReport(rtcpSession)
            processRtpReport(rtpReport)
        }

    }

    private fun processRtpReport(rtpReport: Packet) {
        reports.add(rtpReport)
        if (reports.size >= bulkSize) {
            vertx.eventBus().send(Routes.encoder, reports.toList(), USE_LOCAL_CODEC)
            reports.clear()
        }
    }

    private fun createRtpReport(session: RtcpSession): Packet {
        val rtpReport = Packet().apply {
            timestamp = session.createdAt
            dstAddr = session.dstAddr
            dstPort = session.dstPort
            srcAddr = session.srcAddr
            srcPort = session.srcPort
            protocolCode = Packet.TYPE_RTPR
        }

        rtpReport.payload = RtpReportPayload().apply {
            ssrc = session.ssrc
            payloadType = session.payloadType.toByte()
            receivedPacketCount = session.receivedPacketCount
            lostPacketCount = session.lostPacketCount
            minJitter = session.minJitter
            maxJitter = session.maxJitter
            lastJitter = session.lastJitter

            if (receivedPacketCount > 0) {
                // Duration in ms
                duration = (session.lastPacketTimestamp - session.createdAt.time).toInt()
                expectedPacketCount = receivedPacketCount + lostPacketCount
            } else {
                duration = 0
                expectedPacketCount = session.lostPacketCount
                // Drop min jitter if no valid packetQueue in session
                minJitter = 0F
            }
            lostPacketCount = expectedPacketCount - receivedPacketCount

            if (lostPacketCount < 0) {
                lostPacketCount = 0
            }
            // Packet lost in percents
            fractionLost = lostPacketCount.toFloat() / expectedPacketCount

            // Finalize avgJitter calculation
            if (receivedPacketCount > 1) {
                avgJitter = session.jitterSum / (receivedPacketCount - 1)
            }

            // Perform calculations only if codec information persists
            session.sdpSession?.let { sdpSession ->
                // Raw rFactor value
                val ppl = (1 - receivedPacketCount.toFloat() / expectedPacketCount) * 100
                val ieEff = sdpSession.codecIe + (95 - sdpSession.codecIe) * ppl / (ppl + sdpSession.codecBpl)
                var iDelay = I_DELAY_DEFAULT
                if (lastJitter - 177.3F >= 0) {
                    iDelay += (lastJitter - 15.93)
                }
                rFactor = (R0 - ieEff - iDelay).toFloat()

                // MoS
                // TODO: Change to `when`
                if (rFactor < 0) {
                    mos = MOS_MIN
                } else if (rFactor > 100F) {
                    mos = MOS_MAX
                } else {
                    mos = (1 + rFactor * 0.035 + rFactor * (100 - rFactor) * (rFactor - 60) * 0.000007).toFloat()
                }
            }
        }

        return rtpReport
    }
}