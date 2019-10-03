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

package io.sip3.captain.ce.domain.payload

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.sip3.commons.util.writeTlv

class RtpReportPayload : Payload {

    companion object {

        const val PAYLOAD_LENGTH = 99

        const val TAG_PAYLOAD_TYPE = 1
        const val TAG_SSRC = 2

        const val TAG_EXPECTED_PACKET_COUNT = 3
        const val TAG_RECEIVED_PACKET_COUNT = 4
        const val TAG_LOST_PACKET_COUNT = 5
        const val TAG_REJECTED_PACKET_COUNT = 6

        const val TAG_DURATION = 7

        const val TAG_LAST_JITTER = 8
        const val TAG_AVG_JITTER = 9
        const val TAG_MIN_JITTER = 10
        const val TAG_MAX_JITTER = 11

        const val TAG_R_FACTOR = 12
        const val TAG_MOS = 13
        const val TAG_FRACTION_LOST = 14
    }

    var payloadType: Byte = 0
    var ssrc: Long = 0

    var expectedPacketCount: Int = 0
    var receivedPacketCount: Int = 0
    var lostPacketCount: Int = 0
    var rejectedPacketCount: Int = 0

    var duration: Int = 0

    var lastJitter: Float = 0F
    var avgJitter: Float = 0F
    var minJitter: Float = 10000F
    var maxJitter: Float = 0F

    var rFactor: Float = 0F
    var mos: Float = 1.0F
    var fractionLost: Float = 0F

    override fun encode(): ByteBuf {
        return Unpooled.buffer(PAYLOAD_LENGTH).apply {
            writeTlv(TAG_PAYLOAD_TYPE, payloadType)
            writeTlv(TAG_SSRC, ssrc)

            writeTlv(TAG_EXPECTED_PACKET_COUNT, expectedPacketCount)
            writeTlv(TAG_RECEIVED_PACKET_COUNT, receivedPacketCount)
            writeTlv(TAG_LOST_PACKET_COUNT, lostPacketCount)
            writeTlv(TAG_REJECTED_PACKET_COUNT, rejectedPacketCount)

            writeTlv(TAG_DURATION, duration)

            writeTlv(TAG_LAST_JITTER, lastJitter)
            writeTlv(TAG_AVG_JITTER, avgJitter)
            writeTlv(TAG_MIN_JITTER, minJitter)
            writeTlv(TAG_MAX_JITTER, maxJitter)

            writeTlv(TAG_R_FACTOR, rFactor)
            writeTlv(TAG_MOS, mos)
            writeTlv(TAG_FRACTION_LOST, fractionLost)
        }
    }
}
