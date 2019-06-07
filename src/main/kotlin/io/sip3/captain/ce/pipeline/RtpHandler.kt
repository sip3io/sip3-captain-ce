package io.sip3.captain.ce.pipeline

import io.netty.buffer.ByteBuf
import io.sip3.captain.ce.Routes
import io.sip3.captain.ce.USE_LOCAL_CODEC
import io.sip3.captain.ce.domain.Packet
import io.sip3.captain.ce.domain.RtpHeaderPayload
import io.vertx.core.Vertx

/**
 * Handles RTP packets
 */
class RtpHandler(vertx: Vertx, bulkOperationsEnabled: Boolean) : Handler(vertx, bulkOperationsEnabled) {

    private val packets = mutableListOf<Packet>()
    private var bulkSize = 1

    init {
        if (bulkOperationsEnabled) {
            vertx.orCreateContext.config().getJsonObject("rtp")?.let { config ->
                config.getInteger("bulk-size")?.let { bulkSize = it }
            }
        }
    }

    override fun onPacket(buffer: ByteBuf, packet: Packet) {
        packet.protocolCode = Packet.TYPE_RTP
        packet.payload = RtpHeaderPayload().apply {
            // TODO...
        }
        packets.add(packet)

        if (packets.size >= bulkSize) {
            vertx.eventBus().send(Routes.rtp, packets.toList(), USE_LOCAL_CODEC)
            packets.clear()
        }
    }
}