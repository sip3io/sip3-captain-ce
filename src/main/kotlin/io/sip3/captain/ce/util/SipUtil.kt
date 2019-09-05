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

import io.netty.buffer.ByteBuf

object SipUtil {

    val SIP_WORDS = arrayOf(
            // RFC 3261
            "SIP/2.0 ", "INVITE", "REGISTER", "ACK", "CANCEL", "BYE", "OPTIONS",
            // RFC 3262
            "PRACK",
            // RFC 3428
            "MESSAGE",
            // RFC 6665
            "SUBSCRIBE", "NOTIFY",
            // RFC 3903
            "PUBLISH",
            // RFC 3311
            "UPDATE",
            // RFC 3515
            "REFER",
            // RFC 2976
            "INFO"
    ).map { word -> word.toByteArray() }.toList()

    const val CR: Byte = 0x0d
    const val LF: Byte = 0x0a

    fun startsWithSipWord(buffer: ByteBuf, offset: Int = 0): Boolean {
        val i = offset + buffer.readerIndex()
        return SIP_WORDS.any { word ->
            if (i + word.size > buffer.capacity()) {
                return@any false
            }
            word.forEachIndexed { j, b ->
                if (b != buffer.getByte(i + j)) {
                    return@any false
                }
            }
            return@any true
        }
    }

    fun isNewLine(buffer: ByteBuf, offset: Int = 0): Boolean {
        if (offset < 2) return true
        val i = buffer.readerIndex() + offset
        return buffer.getByte(i - 2) == CR && buffer.getByte(i - 1) == LF
    }
}
