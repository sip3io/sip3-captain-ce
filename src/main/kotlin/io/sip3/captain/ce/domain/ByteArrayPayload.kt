package io.sip3.captain.ce.domain

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled

class ByteArrayPayload : Payload {

    lateinit var bytes: ByteArray

    override fun encode(): ByteBuf {
        return Unpooled.wrappedBuffer(bytes)
    }
}