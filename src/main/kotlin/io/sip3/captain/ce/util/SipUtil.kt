/*
 * Copyright 2018-2024 SIP3.IO, Corp.
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
import io.sip3.commons.SipMethods

object SipUtil {

    val SIP_WORDS = SipMethods.values()
        .map { "$it " }
        .toMutableList()
        .apply {
            add("SIP/2.0 ")
        }
        .map { word -> word.toByteArray() }
        .toList()

    const val CR = 0x0d.toByte()
    const val LF = 0x0a.toByte()
    const val DASH = 0x2d.toByte()

    fun startsWithSipWord(buffer: ByteBuf, offset: Int = 0): Boolean {
        val i = offset + buffer.readerIndex()
        return SIP_WORDS.any { word ->
            if (i + word.size > buffer.writerIndex()) {
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

    fun findSipWord(buffer: ByteBuf, offset: Int = 0): Int {
        if (startsWithSipWord(buffer, offset)) {
            return offset
        }

        var i = offset + buffer.readerIndex()
        while (i < buffer.writerIndex() - 2) {
            if (
                ((buffer.getByte(i) == CR && buffer.getByte(i + 1) == LF)
                        || (buffer.getByte(i) == DASH && buffer.getByte(i + 1) == DASH))
                && startsWithSipWord(buffer, i + 2 - buffer.readerIndex())
            ) {
                return i + 2 - buffer.readerIndex()
            }

            i++
        }

        return -1
    }
}
