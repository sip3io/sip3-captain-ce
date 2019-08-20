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
import io.sip3.captain.ce.util.SipUtil.CR
import io.sip3.captain.ce.util.SipUtil.LF
import io.sip3.captain.ce.util.SipUtil.SIP_WORDS
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SipUtilTest {

    @Test
    fun `check random SIP word`() {
        val word = SIP_WORDS.random()
        assertTrue(SipUtil.startsWithSipWord(Unpooled.wrappedBuffer(word)))
    }

    @Test
    fun `check EOL`() {
        val line = byteArrayOf(CR, LF)
        assertTrue(SipUtil.isNewLine(Unpooled.wrappedBuffer(line), offset = 2))
    }
}