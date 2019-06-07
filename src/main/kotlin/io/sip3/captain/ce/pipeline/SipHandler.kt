package io.sip3.captain.ce.pipeline

import io.netty.buffer.ByteBuf
import io.sip3.captain.ce.Routes
import io.sip3.captain.ce.USE_LOCAL_CODEC
import io.sip3.captain.ce.domain.ByteArrayPayload
import io.sip3.captain.ce.domain.Packet
import io.vertx.core.Vertx

/**
 * Handles SIP packets
 */
class SipHandler(vertx: Vertx, bulkOperationsEnabled: Boolean) : Handler(vertx, bulkOperationsEnabled) {

    companion object {

        private val SIP_WORDS = arrayOf(
                // RFC 3261
                "SIP/2.0 ", "INVITE", "REGISTER", "ACK", "CANCEL", "BYE", "OPTIONS",
                // RFC 3262
                "PRACK",
                // RFC 3428
                "MESSAGE",
                // RFC 6665
                "SUBSCRIBE", "NOTIFY"
        ).map { word -> word.toByteArray() }.toList()

        private val CR: Byte = 0x0d
        private val LF: Byte = 0x0a
    }

    private val packets = mutableListOf<Packet>()
    private var bulkSize = 1

    init {
        if (bulkOperationsEnabled) {
            vertx.orCreateContext.config().getJsonObject("sip")?.let { config ->
                config.getInteger("bulk-size")?.let { bulkSize = it }
            }
        }
    }

    override fun onPacket(buffer: ByteBuf, packet: Packet) {
        var offset = 0
        var mark = -1
        while (offset + buffer.readerIndex() < buffer.capacity()) {
            if (isNewLine(offset, buffer) && startsWithSipWord(offset, buffer)) {
                if (mark > -1) {
                    val p = Packet().apply {
                        timestamp = packet.timestamp
                        srcAddr = packet.srcAddr
                        dstAddr = packet.dstAddr
                        srcPort = packet.srcPort
                        dstPort = packet.dstPort
                        protocolCode = Packet.TYPE_SIP
                        payload = ByteArrayPayload().apply {
                            val slice = buffer.slice(buffer.readerIndex() + mark, offset - mark)
                            bytes = ByteArray(slice.capacity())
                            slice.readBytes(bytes)
                        }
                    }
                    packets.add(p)
                }
                mark = offset
            }
            offset++
        }
        if (mark > -1) {
            val p = Packet().apply {
                timestamp = packet.timestamp
                srcAddr = packet.srcAddr
                dstAddr = packet.dstAddr
                srcPort = packet.srcPort
                dstPort = packet.dstPort
                protocolCode = Packet.TYPE_SIP
                payload = ByteArrayPayload().apply {
                    val slice = buffer.slice(buffer.readerIndex() + mark, offset - mark)
                    bytes = ByteArray(slice.capacity())
                    slice.readBytes(bytes)
                }
            }
            packets.add(p)
        }
        if (packets.size >= bulkSize) {
            vertx.eventBus().send(Routes.encoder, packets.toList(), USE_LOCAL_CODEC)
            packets.clear()
        }
    }

    fun isNewLine(offset: Int, buffer: ByteBuf): Boolean {
        if (offset < 2) return true
        val i = buffer.readerIndex() + offset
        return buffer.getByte(i - 2) == CR && buffer.getByte(i - 1) == LF
    }

    fun startsWithSipWord(offset: Int, buffer: ByteBuf): Boolean {
        val i = offset + buffer.readerIndex()
        return SIP_WORDS.any { word ->
            if (i + word.size < buffer.capacity()) {
                word.forEachIndexed { j, b ->
                    if (b != buffer.getByte(i + j)) {
                        return@any false
                    }
                }
            }
            return@any true
        }
    }
}