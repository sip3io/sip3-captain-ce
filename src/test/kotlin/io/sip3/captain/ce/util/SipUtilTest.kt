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