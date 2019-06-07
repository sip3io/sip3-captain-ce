package io.sip3.captain.ce.encoder

import io.sip3.captain.ce.Routes
import io.sip3.captain.ce.USE_LOCAL_CODEC
import io.sip3.captain.ce.domain.Packet
import io.vertx.core.AbstractVerticle
import io.vertx.core.buffer.Buffer
import mu.KotlinLogging

/**
 * Encodes packets to SIP3 protocol
 */
class Encoder : AbstractVerticle() {

    private val logger = KotlinLogging.logger {}

    companion object {

        val PREFIX = byteArrayOf(
                0x83.toByte(), // S
                0x73.toByte(), // I
                0x80.toByte(), // P
                0x51.toByte(), // 3

                0x00.toByte(), // Compressed
                0x01.toByte(), // Type
                0x01.toByte()  // Version
        )
    }

    private val buffers = mutableListOf<Buffer>()
    private var bulkSize = 1

    override fun start() {
        config().getJsonObject("encoder")?.let { config ->
            config.getInteger("bulk-size")?.let { bulkSize = it }
        }
        vertx.eventBus().localConsumer<List<Packet>>(Routes.encoder) { event ->
            try {
                val packets = event.body()
                encode(packets)
            } catch (e: Exception) {
                logger.error("Encoder 'encode()' failed.", e)
            }
        }
    }

    fun encode(packets: List<Packet>) {
        packets.forEach { packet ->
            val srcAddrLength = packet.srcAddr.size
            val dstAddrLength = packet.dstAddr.size
            val payloadLength = packet.payload.encode().capacity()

            val packetLength = arrayListOf(
                    7,                         // Prefix
                    2,                         // Length
                    11,                        // Milliseconds
                    7,                         // Nanoseconds
                    3 + srcAddrLength,         // Source Address
                    3 + dstAddrLength,         // Destination Address
                    5,                         // Source Port
                    5,                         // Destination Port
                    4,                         // Protocol Code
                    3 + payloadLength   // Payload

            ).sum()

            val buffer = Buffer.buffer(packetLength).apply {
                // Prefix
                appendBytes(PREFIX)
                // Length
                appendShort(packetLength.toShort())
                // Milliseconds
                appendByte(1)
                appendShort(11)
                appendLong(packet.timestamp.time)
                // Nanoseconds
                appendByte(2)
                appendShort(7)
                appendInt(packet.timestamp.nanos)
                // Source Address
                appendByte(3)
                appendShort((3 + srcAddrLength).toShort())
                appendBytes(packet.srcAddr)
                // Destination Address
                appendByte(4)
                appendShort((3 + dstAddrLength).toShort())
                appendBytes(packet.dstAddr)
                // Source Port
                appendByte(5)
                appendShort(5)
                appendShort(packet.srcPort.toShort())
                // Destination Port
                appendByte(6)
                appendShort(5)
                appendShort(packet.dstPort.toShort())
                // Protocol Code
                appendByte(7)
                appendShort(4)
                appendByte(packet.protocolCode)
                // Payload
                appendByte(8)
                appendShort((3 + payloadLength).toShort())
                appendBuffer(Buffer.buffer(packet.payload.encode()))
            }
            buffers.add(buffer)
        }

        if (buffers.size >= bulkSize) {
            vertx.eventBus().send(Routes.sender, buffers.toList(), USE_LOCAL_CODEC)
            buffers.clear()
        }
    }
}