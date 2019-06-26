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

import java.sql.Timestamp

class Packet {

    companion object {

        // Real-Time Transport Protocol
        const val TYPE_RTCP: Byte = 1
        // Real-time Transport Control Protocol
        const val TYPE_RTP: Byte = 2
        // Session Initiation Protocol
        const val TYPE_SIP: Byte = 3
        // Internet Control Message Protocol
        const val TYPE_ICMP: Byte = 4
        // Real-Time Transport Protocol Report (Internal SIP3 protocol supported in SIP3 `Enterprise Edition` only)
        const val TYPE_RTPR: Byte = 5
    }

    lateinit var timestamp: Timestamp
    lateinit var srcAddr: ByteArray
    lateinit var dstAddr: ByteArray
    var protocolNumber: Int = 0
    var srcPort: Int = 0
    var dstPort: Int = 0
    var protocolCode: Byte = 0
    lateinit var payload: Payload
    var rejected: Boolean = false
}