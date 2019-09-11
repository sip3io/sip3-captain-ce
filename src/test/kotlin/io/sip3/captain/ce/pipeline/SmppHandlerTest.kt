package io.sip3.captain.ce.pipeline

import io.netty.buffer.Unpooled
import io.sip3.captain.ce.Routes
import io.sip3.captain.ce.VertxTest
import io.sip3.captain.ce.domain.ByteBufPayload
import io.sip3.captain.ce.domain.Packet
import io.sip3.captain.ce.util.remainingCapacity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Timestamp

class SmppHandlerTest : VertxTest() {


    companion object {

        // Payload: SMPP (1 message)
        val PACKET_1 = byteArrayOf(
                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x10.toByte(), 0x80.toByte(), 0x00.toByte(), 0x00.toByte(),
                0x15.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x04.toByte(),
                0x58.toByte(), 0xae.toByte()
        )

        // Payload: SMPP (3 messages)
        val PACKET_2 = byteArrayOf(
                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x10.toByte(), 0x80.toByte(), 0x00.toByte(), 0x00.toByte(),
                0x15.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x04.toByte(),
                0x58.toByte(), 0xae.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x10.toByte(), 0x80.toByte(),
                0x00.toByte(), 0x00.toByte(), 0x15.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
                0x00.toByte(), 0x04.toByte(), 0x58.toByte(), 0xae.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
                0x10.toByte(), 0x80.toByte(), 0x00.toByte(), 0x00.toByte(), 0x15.toByte(), 0x00.toByte(), 0x00.toByte(),
                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x04.toByte(), 0x58.toByte(), 0xae.toByte()
        )
    }

    @Test
    fun `Parse SMPP packet containing one message`() {
        runTest(
                deploy = {
                    // Do nothing...
                },
                execute = {
                    val smppHandler = SmppHandler(vertx, false)
                    val packet = Packet().apply {
                        timestamp = Timestamp(System.currentTimeMillis())
                        srcAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
                        dstAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
                        srcPort = 2775
                        dstPort = 2775
                        payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_1))
                    }
                    smppHandler.handle(packet)
                },
                assert = {
                    vertx.eventBus().consumer<List<Packet>>(Routes.encoder) { event ->
                        val packets = event.body()
                        context.verify {
                            assertEquals(1, packets.size)
                            val packet = packets[0]
                            assertEquals(Packet.TYPE_SMPP, packet.protocolCode)
                            val buffer = packet.payload.encode()
                            assertEquals(16, buffer.remainingCapacity())
                        }
                        context.completeNow()
                    }
                }
        )
    }

    @Test
    fun `Parse SMPP packet containing three messages`() {
        var counter = 0
        runTest(
                deploy = {
                    // Do nothing...
                },
                execute = {
                    val smppHandler = SmppHandler(vertx, false)
                    val packet = Packet().apply {
                        timestamp = Timestamp(System.currentTimeMillis())
                        srcAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
                        dstAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
                        srcPort = 5060
                        dstPort = 5060
                        payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_2))
                    }
                    smppHandler.handle(packet)
                },
                assert = {
                    vertx.eventBus().consumer<List<Packet>>(Routes.encoder) { event ->
                        val packets = event.body()
                        counter++
                        context.verify {
                            assertEquals(1, packets.size)
                            val packet = packets[0]
                            assertEquals(Packet.TYPE_SMPP, packet.protocolCode)
                            val buffer = packet.payload.encode()
                            assertEquals(16, buffer.remainingCapacity())
                        }
                        if (counter == 3) {
                            context.completeNow()
                        }
                    }
                }
        )
    }
}