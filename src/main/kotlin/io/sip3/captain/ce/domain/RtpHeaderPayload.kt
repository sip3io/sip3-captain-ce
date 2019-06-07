package io.sip3.captain.ce.domain

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled

class RtpHeaderPayload : Payload {

    override fun encode(): ByteBuf {
        // TODO...
        return Unpooled.EMPTY_BUFFER
    }
}