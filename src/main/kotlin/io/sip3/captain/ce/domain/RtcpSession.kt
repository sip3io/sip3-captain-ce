/*
 * Copyright 2018-2020 SIP3.IO, Inc.
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

import io.sip3.commons.domain.SdpSession
import io.sip3.commons.domain.payload.RtpReportPayload
import io.sip3.commons.util.IpUtil
import java.sql.Timestamp

class RtcpSession {

    // Static session data
    lateinit var createdAt: Timestamp
    lateinit var dstAddr: ByteArray
    var dstPort: Int = 0
    lateinit var srcAddr: ByteArray
    var srcPort: Int = 0

    // Jitter
    var lastJitter = 0F

    lateinit var previousReport: RtcpReportBlock
    var lastNtpTimestamp: Long = 0
    var lastPacketTimestamp: Long = 0

    // SDP session
    var sdpSession: SdpSession? = null
    val srcSdpSessionId: Long by lazy {
        val srcAddrAsLong = IpUtil.convertToInt(srcAddr).toLong()
        ((srcAddrAsLong shl 32) or (srcPort - 1).toLong())
    }
    val dstSdpSessionId: Long by lazy {
        val dstAddr = IpUtil.convertToInt(dstAddr).toLong()
        ((dstAddr shl 32) or (dstPort - 1).toLong())
    }

    var rtcpReportCount = 0
    val cumulative: RtpReportPayload = RtpReportPayload().apply { cumulative = true }
}