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
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ByteBufUtilTest {

    companion object {

        const val TAG = 1
    }

    @Test
    fun `Write byte as tlv`() {
        val byteValue = 1.toByte()

        createBuffer(byteValue, 4).also { buffer ->
            assertEquals(byteValue, buffer.readByte())
        }
    }

    @Test
    fun `Write short as tlv`() {
        val shortValue = 2.toShort()

        createBuffer(shortValue, 5).also { buffer ->
            assertEquals(shortValue, buffer.readShort())
        }
    }

    @Test
    fun `Write int as tlv`() {
        val intValue = 3

        createBuffer(intValue, 7).also { buffer ->
            assertEquals(intValue, buffer.readInt())
        }
    }

    @Test
    fun `Write long as tlv`() {
        val longValue = 4L

        createBuffer(longValue, 11).also { buffer ->
            assertEquals(longValue, buffer.readLong())
        }
    }

    @Test
    fun `Write float as tlv`() {
        val floatValue = 5.0F

        createBuffer(floatValue, 7).also { buffer ->
            assertEquals(floatValue, buffer.readFloat())
        }
    }

    @Test
    fun `Write ByteArray as tlv`() {
        val byteArrayValue = byteArrayOf(0x08, 0x08, 0x08, 0x0C)

        createBuffer(byteArrayValue, 7).also { buffer ->
            val actualBytes = ByteArray(4)
            buffer.readBytes(actualBytes)
            assertArrayEquals(byteArrayValue, actualBytes)
        }
    }

    @Test
    fun `Write ByteBuf as tlv`() {
        val byteArray = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val byteBufValue = Unpooled.wrappedBuffer(byteArray)

        createBuffer(byteBufValue, 7).also { buffer ->
            val actualBytes = ByteArray(4)
            buffer.readBytes(actualBytes)
            assertArrayEquals(byteArray, actualBytes)
        }
    }

    @Test
    fun `Check write for unsupported value type`() {
        assertThrows(IllegalArgumentException::class.java) {
            Unpooled.buffer(1).apply {
                writeTlv(TAG, "string value")
            }
        }
    }

    @Test
    fun `Read specific ByteBuffer bytes into ByteArray`() {
        val byteArray = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val byteBufValue = Unpooled.wrappedBuffer(byteArray)

        createBuffer(byteBufValue, 7).also { buffer ->
            assertArrayEquals(byteArray, buffer.readBytes(3, 4))
        }
    }

    @Test
    fun `Read remaining ByteBuffer bytes into ByteArray`() {
        val byteArray = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val byteBufValue = Unpooled.wrappedBuffer(byteArray)

        createBuffer(byteBufValue, 7).also { buffer ->
            assertArrayEquals(byteArray, buffer.readBytes())
        }
    }

    /**
     * Returns buffer with readerIndex on start of value.
     */
    private fun createBuffer(value: Any, length: Int): ByteBuf {
        val buffer = Unpooled.buffer(length).apply {
            writeTlv(TAG, value)
        }

        assertEquals(length, buffer.capacity(), "The buffer size must not be changed.")
        assertEquals(TAG, buffer.readByte().toInt())
        assertEquals(length, buffer.readShort().toInt())

        return buffer
    }
}