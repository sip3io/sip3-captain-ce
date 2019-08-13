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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ByteBufUtilKtTest {

    @Test
    fun `write byte as tlv`() {
        val byteValue = 1.toByte()
        val testByteBuf = Unpooled.buffer(4).apply {
            writeTlv(1, byteValue)
        }

        assertEquals(4, testByteBuf.capacity())

        assertEquals(1, testByteBuf.readByte().toInt())
        assertEquals(4, testByteBuf.readShort())
        assertEquals(byteValue, testByteBuf.readByte())

    }

    @Test
    fun `write short as tlv`() {
        val shortValue = 2.toShort()
        val testByteBuf = Unpooled.buffer(5).apply {
            writeTlv(1, shortValue)
        }

        assertEquals(5, testByteBuf.capacity())

        assertEquals(1, testByteBuf.readByte().toInt())
        assertEquals(5, testByteBuf.readShort())
        assertEquals(shortValue, testByteBuf.readShort())
    }

    @Test
    fun `write int as tlv`() {
        val intValue = 3
        val testByteBuf = Unpooled.buffer(7).apply {
            writeTlv(1, intValue)
        }

        assertEquals(7, testByteBuf.capacity())

        assertEquals(1, testByteBuf.readByte().toInt())
        assertEquals(7, testByteBuf.readShort())
        assertEquals(intValue, testByteBuf.readInt())
    }

    @Test
    fun `write long as tlv`() {
        val longValue = 4L
        val testByteBuf = Unpooled.buffer(11).apply {
            writeTlv(1, longValue)
        }

        assertEquals(11, testByteBuf.capacity())

        assertEquals(1, testByteBuf.readByte().toInt())
        assertEquals(11, testByteBuf.readShort())
        assertEquals(longValue, testByteBuf.readLong())
    }

    @Test
    fun `write float as tlv`() {
        val floatValue = 5.0F
        val testByteBuf = Unpooled.buffer(7).apply {
            writeTlv(1, floatValue)
        }

        assertEquals(7, testByteBuf.capacity())

        assertEquals(1, testByteBuf.readByte().toInt())
        assertEquals(7, testByteBuf.readShort())
        assertEquals(floatValue, testByteBuf.readFloat())
    }

    @Test
    fun `write ByteArray as tlv`() {
        val byteArrayValue = byteArrayOf(0x08, 0x08, 0x08, 0x0C)
        val testByteBuf = Unpooled.buffer(7).apply {
            writeTlv(1, byteArrayValue)
        }

        assertEquals(7, testByteBuf.capacity())

        assertEquals(1, testByteBuf.readByte().toInt())
        assertEquals(7, testByteBuf.readShort())

        assertEquals(8, testByteBuf.readByte())
        testByteBuf.skipBytes(2)
        assertEquals(12, testByteBuf.readByte())
    }


    @Test
    fun `write ByteBuf as tlv`() {
        val byteBufValue = Unpooled.buffer(10).apply {
            for (i in 1..10) {
                writeByte(i)
            }
        }

        val testByteBuf = Unpooled.buffer(13).apply {
            writeTlv(1, byteBufValue)
        }

        assertEquals(13, testByteBuf.capacity())

        assertEquals(1, testByteBuf.readByte().toInt())
        assertEquals(13, testByteBuf.readShort())

        assertEquals(1, testByteBuf.readByte())
        testByteBuf.skipBytes(8)
        assertEquals(10, testByteBuf.readByte())
    }

    @Test
    fun `check write for unsupported value type`() {
        assertThrows(IllegalArgumentException::class.java) {
            Unpooled.buffer(1).apply {
                writeTlv(8, "string value")
            }
        }
    }

}