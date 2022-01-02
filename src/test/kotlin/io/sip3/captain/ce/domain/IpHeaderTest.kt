/*
 * Copyright 2018-2022 SIP3.IO, Corp.
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

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IpHeaderTest {

    @Test
    fun `Copy IP header`() {
        val original = IpHeader().apply {
            this.headerLength = 1
            this.totalLength = 2
            this.identification = 3
            this.moreFragments = true
            this.fragmentOffset = 4
            this.protocolNumber = 5
            this.srcAddr = byteArrayOf(0x00, 0x01, 0x02, 0x03)
            this.dstAddr = byteArrayOf(0x04, 0x05, 0x06, 0x07)
        }

        val copy = original.copy()

        assertEquals(original.headerLength, copy.headerLength)
        assertEquals(original.totalLength, copy.totalLength)
        assertEquals(original.identification, copy.identification)
        assertEquals(original.moreFragments, copy.moreFragments)
        assertEquals(original.fragmentOffset, copy.fragmentOffset)
        assertEquals(original.protocolNumber, copy.protocolNumber)
        assertNotEquals(original.srcAddr, copy.srcAddr)
        assertArrayEquals(original.srcAddr, copy.srcAddr)
        assertNotEquals(original.dstAddr, copy.dstAddr)
        assertArrayEquals(original.dstAddr, copy.dstAddr)
    }
}