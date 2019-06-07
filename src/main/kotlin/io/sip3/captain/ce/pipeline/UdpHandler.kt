package io.sip3.captain.ce.pipeline

import io.netty.buffer.ByteBuf
import io.sip3.captain.ce.domain.Packet
import io.vertx.core.Vertx

/**
 * Handles UDP packets
 */
class UdpHandler(vertx: Vertx, bulkOperationsEnabled: Boolean) : Handler(vertx, bulkOperationsEnabled) {

    private val routerHandler = RouterHandler(vertx, bulkOperationsEnabled)

    override fun onPacket(buffer: ByteBuf, packet: Packet) {
        val offset = buffer.readerIndex()

        // Source Port
        packet.srcPort = buffer.readUnsignedShort()
        // Destination Port
        packet.dstPort = buffer.readUnsignedShort()
        // Length
        val length = buffer.readUnsignedShort()
        // Checksum
        buffer.skipBytes(2)

        buffer.capacity(offset + length)

        routerHandler.handle(buffer, packet)
    }
}