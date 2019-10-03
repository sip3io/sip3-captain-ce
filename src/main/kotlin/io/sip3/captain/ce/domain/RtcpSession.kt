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

package io.sip3.captain.ce.domain

import io.sip3.commons.util.IpUtil
import java.sql.Timestamp

class RtcpSession {

    // Static session data
    lateinit var createdAt: Timestamp
    lateinit var dstAddr: ByteArray
    var dstPort: Int = 0
    lateinit var srcAddr: ByteArray
    var srcPort: Int = 0
    var ssrc: Long = 0
    var packetType: Byte = -1
    var payloadType: Int = -1

    // Counters
    var rtcpPacketCount: Int = 0
    var receivedPacketCount: Int = 0
    var lostPacketCount: Int = 0

    // Jitter
    var lastJitter = 0F
    var minJitter = Float.MAX_VALUE
    var maxJitter = 0F
    var jitterSum = 0F

    lateinit var firstReport: RtcpReportBlock
    lateinit var lastReport: RtcpReportBlock
    var lastPacketTimestamp: Long = 0

    // SDP session
    var sdpSession: SdpSession? = null
    val srcSdpSessionId: Long by lazy {
        val srcAddrAsLong = IpUtil.convertToInt(srcAddr).toLong()
        ((srcAddrAsLong shl 32) or srcPort.toLong()) - 1
    }
    val dstSdpSessionId: Long by lazy {
        val dstAddr = IpUtil.convertToInt(dstAddr).toLong()
        ((dstAddr shl 32) or dstPort.toLong()) - 1
    }
}