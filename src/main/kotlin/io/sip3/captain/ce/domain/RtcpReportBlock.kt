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

class RtcpReportBlock {

    var ssrc: Long = 0
    var fractionLost: Short = 0
    var cumulativePacketLost: Long = 0
    var extendedSeqNumber: Long = 0
    var interarrivalJitter: Long = 0

    var lsrTimestamp: Long = 0
    var dlsrTimestamp: Long = 0
}