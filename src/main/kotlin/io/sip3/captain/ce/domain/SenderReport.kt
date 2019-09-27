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

class SenderReport {

    val packetType = 200
    var reportCount: Byte = 0
    var length: Int = 0
    var senderSsrc: Long = 0

    var ntpTimestampMsw: Long = 0
    var ntpTimestampLsw: Long = 0
    val ntpTimestamp by lazy {
        (((ntpTimestampMsw and 0x0000FFFF) shl 16) or ((ntpTimestampLsw and 0xFFFF0000) shr 16)) and 0xFFFF_FFFF
    }
    var rtpTimestamp: Long = 0

    var senderPacketCount: Long = 0

    var reportBlocks = mutableListOf<RtcpReportBlock>()
}