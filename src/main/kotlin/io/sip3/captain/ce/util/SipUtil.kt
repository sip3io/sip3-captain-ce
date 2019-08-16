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
            "UPDATE"
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
