/*
 * Copyright 2018-2023 SIP3.IO, Corp.
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
        .map(Any::toString)
        .toMutableList()
        .apply {
            add("SIP/2.0 ")
        }
        .map { word -> word.toByteArray() }
        .toList()

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
}
