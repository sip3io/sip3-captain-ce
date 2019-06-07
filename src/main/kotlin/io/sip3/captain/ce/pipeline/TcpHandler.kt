package io.sip3.captain.ce.pipeline

import io.netty.buffer.ByteBuf
import io.sip3.captain.ce.domain.Packet
import io.vertx.core.Vertx

/**
 * Handles TCP packets
 */
class TcpHandler(vertx: Vertx, bulkOperationsEnabled: Boolean) : Handler(vertx, bulkOperationsEnabled) {

    private val routerHandler = RouterHandler(vertx, bulkOperationsEnabled)

    override fun onPacket(buffer: ByteBuf, packet: Packet) {
        val offset = buffer.readerIndex()

        // Source Port
        packet.srcPort = buffer.readUnsignedShort()
        // Destination Port
        packet.dstPort = buffer.readUnsignedShort()
        // Sequence number
        buffer.skipBytes(4)
        // Acknowledgment number
        buffer.skipBytes(4)
        // Data offset
        val headerLength = 4 * buffer.readUnsignedByte().toInt().shr(4)

        buffer.readerIndex(offset + headerLength)

        routerHandler.handle(buffer, packet)
    }
}