package io.sip3.captain.ce.pipeline

import io.netty.buffer.ByteBuf
import io.sip3.captain.ce.domain.Packet
import io.vertx.core.Vertx

/**
 * Routes application layer packets
 */
class RouterHandler(vertx: Vertx, bulkOperationsEnabled: Boolean) : Handler(vertx, bulkOperationsEnabled) {

    private val rtcpHandler = RtpHandler(vertx, bulkOperationsEnabled)
    private val rtpHandler = RtpHandler(vertx, bulkOperationsEnabled)
    private val sipHandler = SipHandler(vertx, bulkOperationsEnabled)

    override fun onPacket(buffer: ByteBuf, packet: Packet) {
        val offset = buffer.readerIndex()

        // Filter packets with the size smaller than minimal RTP/RTCP or SIP
        if (buffer.capacity() - offset < 8) {
            return
        }

        if (buffer.getUnsignedByte(offset).toInt().shr(6) == 2) {
            // RTP or RTCP packet
            val packetType = buffer.getUnsignedByte(offset + 1).toInt()
            if (packetType in 200..211) {
                rtcpHandler.handle(buffer, packet)
            } else {
                rtpHandler.handle(buffer, packet)
            }
        } else {
            // SIP packet (as long as we have SIP, RTP and RTCP packets only)
            sipHandler.handle(buffer, packet)
        }
    }
}