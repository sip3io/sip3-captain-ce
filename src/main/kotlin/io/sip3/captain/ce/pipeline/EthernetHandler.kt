package io.sip3.captain.ce.pipeline

import io.netty.buffer.ByteBuf
import io.sip3.captain.ce.domain.Packet
import io.vertx.core.Vertx

/**
 * Handles Ethernet packets
 */
class EthernetHandler(vertx: Vertx, bulkOperationsEnabled: Boolean) : Handler(vertx, bulkOperationsEnabled) {

    companion object {

        const val TYPE_IPV4 = 0x0800
        const val TYPE_802_1_Q = 0x8100
        const val TYPE_802_1_AD = 0x88a8
    }

    private val ipv4Handler = Ipv4Handler(vertx, bulkOperationsEnabled)

    override fun onPacket(buffer: ByteBuf, packet: Packet) {
        // Source MAC and Destination MAC
        buffer.skipBytes(12)
        // Ethernet Type
        val etherType = readEthernetType(buffer)

        when (etherType) {
            TYPE_IPV4 -> ipv4Handler.handle(buffer, packet)
        }
    }

    private fun readEthernetType(buffer: ByteBuf): Int {
        // Ethernet Type or TPI
        val ethernetType = buffer.readUnsignedShort()
        if (ethernetType == TYPE_802_1_AD || ethernetType == TYPE_802_1_Q) {
            // TCI
            buffer.skipBytes(2)
            return readEthernetType(buffer)
        }
        return ethernetType
    }
}