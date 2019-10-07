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

import org.apache.commons.net.ntp.TimeStamp

class SenderReport {

    val packetType = 200
    var reportBlockCount: Byte = 0
    var length: Int = 0
    var senderSsrc: Long = 0

    var ntpTimestampMsw: Long = 0
    var ntpTimestampLsw: Long = 0
    val ntpTimestamp by lazy {
        TimeStamp("${ntpTimestampMsw.toString(16)}.${ntpTimestampLsw.toString(16)}").time
    }

    var senderPacketCount: Long = 0

    var reportBlocks = mutableListOf<RtcpReportBlock>()
}