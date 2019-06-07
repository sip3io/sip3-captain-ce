package io.sip3.captain.ce.pipeline

import io.netty.buffer.ByteBuf
import io.sip3.captain.ce.Routes
import io.sip3.captain.ce.USE_LOCAL_CODEC
import io.sip3.captain.ce.domain.ByteArrayPayload
import io.sip3.captain.ce.domain.Ipv4Header
import io.sip3.captain.ce.domain.Packet
import io.vertx.core.Vertx

/**
 * Handles IPv4 packets
 */
class Ipv4Handler(vertx: Vertx, bulkOperationsEnabled: Boolean) : Handler(vertx, bulkOperationsEnabled) {

    companion object {

        const val TYPE_TCP = 0x06
        const val TYPE_UDP = 0x11
        const val TYPE_ICMP = 0x01
        const val TYPE_IPV4 = 0x04
    }

    private val tcpHandler = TcpHandler(vertx, bulkOperationsEnabled)
    private val udpHandler = UdpHandler(vertx, bulkOperationsEnabled)
    private val icmpHandler = IcmpHandler(vertx, bulkOperationsEnabled)

    private val packets = mutableListOf<Pair<Ipv4Header, Packet>>()
    private var bulkSize = 1

    init {
        if (bulkOperationsEnabled) {
            vertx.orCreateContext.config().getJsonObject("ipv4")?.let { config ->
                config.getInteger("bulk-size")?.let { bulkSize = it }
            }
        }
    }

    override fun onPacket(buffer: ByteBuf, packet: Packet) {
        val offset = buffer.readerIndex()

        val ipv4Header = readIpv4Header(buffer)

        if (ipv4Header.moreFragments || ipv4Header.fragmentOffset > 0) {
            packet.payload = ByteArrayPayload().apply {
                val slice = buffer.slice()
                bytes = ByteArray(slice.capacity())
                slice.readBytes(bytes)
            }
            packets.add(Pair(ipv4Header, packet))

            if (packets.size >= bulkSize) {
                vertx.eventBus().send(Routes.fragment, packets.toList(), USE_LOCAL_CODEC)
                packets.clear()
            }
        } else {
            packet.srcAddr = ipv4Header.srcAddr
            packet.dstAddr = ipv4Header.dstAddr

            buffer.readerIndex(offset + ipv4Header.headerLength)
            buffer.capacity(offset + ipv4Header.totalLength)

            when (ipv4Header.protocolNumber) {
                TYPE_UDP -> udpHandler.handle(buffer, packet)
                TYPE_TCP -> tcpHandler.handle(buffer, packet)
                TYPE_ICMP -> icmpHandler.handle(buffer, packet)
                TYPE_IPV4 -> onPacket(buffer, packet)
            }
        }
    }

    fun onDefragmentedPacket(protocolNumber: Int, buffer: ByteBuf, packet: Packet) {
        when (protocolNumber) {
            TYPE_UDP -> udpHandler.handle(buffer, packet)
            TYPE_TCP -> tcpHandler.handle(buffer, packet)
            TYPE_ICMP -> icmpHandler.handle(buffer, packet)
            TYPE_IPV4 -> onPacket(buffer, packet)
        }
    }

    fun readIpv4Header(buffer: ByteBuf): Ipv4Header {
        return Ipv4Header().apply {
            // Version & IHL
            headerLength = 4 * buffer.readUnsignedByte().toInt().and(0x0f)
            // DSCP & ECN
            buffer.skipBytes(1)
            // Total Length
            totalLength = buffer.readUnsignedShort()
            // Identification
            identification = buffer.readUnsignedShort()
            // Flags & Fragment Offset
            val flagsAndFragmentOffset = buffer.readUnsignedShort()
            moreFragments = flagsAndFragmentOffset.and(0x2000) != 0
            fragmentOffset = flagsAndFragmentOffset.and(0x1fff)
            // Time To Live
            buffer.skipBytes(1)
            // Protocol
            protocolNumber = buffer.readUnsignedByte().toInt()
            // Header Checksum
            buffer.skipBytes(2)
            // Source IP
            buffer.readBytes(srcAddr)
            // Destination IP
            buffer.readBytes(dstAddr)
        }
    }
}