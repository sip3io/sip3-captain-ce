package io.sip3.captain.ce.util

import io.netty.buffer.Unpooled
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
}