/*
 * Copyright 2018-2026 SIP3.IO, Corp.
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
import io.sip3.captain.ce.util.SipUtil.SIP_WORDS
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.charset.Charset

class SipUtilTest {

    private val BUFFER_1 = Unpooled.wrappedBuffer("INVITE request\r\nSIP/2.0 200 Ok response".toByteArray(Charset.defaultCharset()))
    private val BUFFER_2 = Unpooled.wrappedBuffer("ITE request\r\n\r\nSIP/2.0 200 Ok response".toByteArray(Charset.defaultCharset()))
    private val BUFFER_3 = Unpooled.wrappedBuffer("ITE request\r\n\r\n\r\n\r\nSIP/2.0 200 Ok response".toByteArray(Charset.defaultCharset()))
    private val BUFFER_4 = Unpooled.wrappedBuffer("INVITE request\r\n\r\n\r\n\r\nSIP/2.0 200 Ok response\r\n\r\n".toByteArray(Charset.defaultCharset()))
    private val BUFFER_5 = Unpooled.wrappedBuffer("offset_INVITE request\r\n\r\nSIP/2.0 200 Ok response\r\n\r\n".toByteArray(Charset.defaultCharset()))
        .readerIndex(7)
    private val BUFFER_6 = Unpooled.wrappedBuffer("ITE request\r\n\r\n--boundary\r\ndata\r\n--boundary--INVITE request\r\n".toByteArray(Charset.defaultCharset()))
    @Test
    fun `Сheck SIP words`() {
        SIP_WORDS.forEach { word ->
            val buffer = Unpooled.wrappedBuffer(word)
            assertTrue(SipUtil.startsWithSipWord(buffer))
        }
    }

    @Test
    fun `Check SIP Word search`() {
        assertEquals(0, SipUtil.findSipWord(BUFFER_1))
        assertEquals(16, SipUtil.findSipWord(BUFFER_1, 1))
        assertEquals(15, SipUtil.findSipWord(BUFFER_2))
        assertEquals(19, SipUtil.findSipWord(BUFFER_3))

        assertEquals(0, SipUtil.findSipWord(BUFFER_4))
        assertEquals(22, SipUtil.findSipWord(BUFFER_4, 4))

        assertEquals(0, SipUtil.findSipWord(BUFFER_5))
        assertEquals(18, SipUtil.findSipWord(BUFFER_5, 7))
        assertEquals(45, SipUtil.findSipWord(BUFFER_6, 7))

    }
}
