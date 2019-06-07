package io.sip3.captain.ce.pipeline

import io.netty.buffer.ByteBuf
import io.sip3.captain.ce.domain.Packet
import io.vertx.core.Vertx
import mu.KotlinLogging

abstract class Handler(val vertx: Vertx, val bulkOperationsEnabled: Boolean = true) {

    private val logger = KotlinLogging.logger {}

    protected abstract fun onPacket(buffer: ByteBuf, packet: Packet)

    fun handle(buffer: ByteBuf, packet: Packet) {
        try {
            onPacket(buffer, packet)
        } catch (e: Exception) {
            logger.error("Handler 'onPacket()' failed.", e)
        }
    }
}