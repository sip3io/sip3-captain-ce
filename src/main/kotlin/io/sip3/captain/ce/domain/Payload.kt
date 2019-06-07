package io.sip3.captain.ce.domain

import io.netty.buffer.ByteBuf

interface Payload {

    fun encode(): ByteBuf
}