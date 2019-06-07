package io.sip3.captain.ce.pipeline

import io.netty.buffer.ByteBuf
import io.sip3.captain.ce.Routes
import io.sip3.captain.ce.USE_LOCAL_CODEC
import io.sip3.captain.ce.domain.ByteArrayPayload
import io.sip3.captain.ce.domain.Packet
import io.vertx.core.Vertx

/**
 * Handles RTCP packets
 */
class RtcpHandler(vertx: Vertx, bulkOperationsEnabled: Boolean) : Handler(vertx, bulkOperationsEnabled) {

    private val packets = mutableListOf<Packet>()
    private var bulkSize = 1

    init {
        if (bulkOperationsEnabled) {
            vertx.orCreateContext.config().getJsonObject("rtcp")?.let { config ->
                config.getInteger("bulk-size")?.let { bulkSize = it }
            }
        }
    }

    override fun onPacket(buffer: ByteBuf, packet: Packet) {
        packet.protocolCode = Packet.TYPE_RTCP
        packet.payload = ByteArrayPayload().apply {
            val slice = buffer.slice()
            bytes = ByteArray(slice.capacity())
            slice.readBytes(bytes)
        }
        packets.add(packet)

        if (packets.size >= bulkSize) {
            vertx.eventBus().send(Routes.encoder, packets.toList(), USE_LOCAL_CODEC)
            packets.clear()
        }
    }
}