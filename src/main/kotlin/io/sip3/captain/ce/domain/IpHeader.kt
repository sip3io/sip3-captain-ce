/*
 * Copyright 2018-2025 SIP3.IO, Corp.
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

class IpHeader(addrSize: Int = 4) {

    var headerLength = 0
    var totalLength = 0
    var identification = 0
    var moreFragments = false
    var fragmentOffset = 0
    var protocolNumber = 0
    var srcAddr: ByteArray = ByteArray(addrSize)
    var dstAddr: ByteArray = ByteArray(addrSize)

    fun copy(): IpHeader {
        val h = IpHeader()
        h.headerLength = headerLength
        h.totalLength = totalLength
        h.identification = identification
        h.moreFragments = moreFragments
        h.fragmentOffset = fragmentOffset
        h.protocolNumber = protocolNumber
        h.srcAddr = srcAddr.copyOf()
        h.dstAddr = dstAddr.copyOf()
        return h
    }
}