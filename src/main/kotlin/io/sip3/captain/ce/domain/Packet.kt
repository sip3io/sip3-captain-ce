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

package io.sip3.captain.ce.domain

import io.sip3.commons.domain.payload.Payload
import java.sql.Timestamp

class Packet {

    lateinit var timestamp: Timestamp
    lateinit var srcAddr: ByteArray
    lateinit var dstAddr: ByteArray
    var protocolNumber: Int = 0
    var srcPort: Int = 0
    var dstPort: Int = 0
    var protocolCode: Byte = 0
    lateinit var payload: Payload
    var rejected: Packet? = null
    var recordingMark = -1

    fun copy(): Packet {
        val p = Packet()
        p.timestamp = timestamp
        p.srcAddr = srcAddr
        p.dstAddr = dstAddr
        p.protocolNumber = protocolNumber
        p.srcPort = srcPort
        p.dstPort = dstPort
        p.protocolCode = protocolCode
        p.payload = payload
        p.rejected = rejected
        p.recordingMark = recordingMark
        return p
    }
}