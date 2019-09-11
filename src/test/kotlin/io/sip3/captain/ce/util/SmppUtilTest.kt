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

package io.sip3.captain.ce.util

import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmppUtilTest {

    companion object {

        // Payload: SMPP (Enquire Link)
        val PACKET_1 = byteArrayOf(
                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x10.toByte(), 0x80.toByte(), 0x00.toByte(), 0x00.toByte(),
                0x15.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x04.toByte(),
                0x58.toByte(), 0xae.toByte()
        )
    }

    @Test
    fun `Check SMPP message`() {
        val buffer = Unpooled.wrappedBuffer(PACKET_1)
        assertTrue(SmppUtil.isPdu(buffer))
    }

    @Test
    fun `Check PDU length`() {
        var buffer = Unpooled.buffer(1)
        assertFalse(SmppUtil.checkMinPduLength(buffer))
        buffer = Unpooled.buffer(16)
        assertTrue(SmppUtil.checkMinPduLength(buffer))
    }

    @Test
    fun `Check PDU commands`() {
        for (command in SmppUtil.COMMANDS) {
            assertTrue(SmppUtil.isPduCommand(command))
        }
    }
}