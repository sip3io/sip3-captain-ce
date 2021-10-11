/*
 * Copyright 2018-2021 SIP3.IO, Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sip3.captain.ce.pipeline

import io.mockk.*
import io.mockk.junit5.MockKExtension
import io.netty.buffer.Unpooled
import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.vertx.test.VertxTest
import io.sip3.commons.vertx.util.localSend
import io.sip3.commons.vertx.util.setPeriodic
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class TcpHandlerTest : VertxTest() {

    companion object {

        // Payload: TCP (SIP message - entire)
        val PACKET_1 = byteArrayOf(
            0x13.toByte(), 0xc4.toByte(), 0x9d.toByte(), 0x70.toByte(), 0xd3.toByte(), 0x7e.toByte(), 0xa1.toByte(),
            0xba.toByte(), 0x47.toByte(), 0x16.toByte(), 0xd2.toByte(), 0x7e.toByte(), 0x80.toByte(), 0x18.toByte(),
            0x80.toByte(), 0x00.toByte(), 0xf3.toByte(), 0xf6.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x01.toByte(), 0x08.toByte(), 0x0a.toByte(), 0x18.toByte(), 0x92.toByte(), 0x7a.toByte(), 0x0a.toByte(),
            0x86.toByte(), 0x37.toByte(), 0x26.toByte(), 0xb0.toByte(), 0x53.toByte(), 0x49.toByte(), 0x50.toByte(),
            0x2f.toByte(), 0x32.toByte(), 0x2e.toByte(), 0x30.toByte(), 0x20.toByte(), 0x31.toByte(), 0x30.toByte(),
            0x30.toByte(), 0x20.toByte(), 0x54.toByte(), 0x72.toByte(), 0x79.toByte(), 0x69.toByte(), 0x6e.toByte(),
            0x67.toByte(), 0x0a.toByte(), 0x0d.toByte(), 0x0a.toByte()
        )

        // Payload: TCP (SIP message - part 1)
        val PACKET_2 = byteArrayOf(
            0x13.toByte(), 0xc4.toByte(), 0x9d.toByte(), 0x70.toByte(), 0xd3.toByte(), 0x7e.toByte(), 0xa1.toByte(),
            0xa7.toByte(), 0x47.toByte(), 0x16.toByte(), 0xd2.toByte(), 0x7e.toByte(), 0x80.toByte(), 0x18.toByte(),
            0x80.toByte(), 0x00.toByte(), 0xf3.toByte(), 0xf6.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x01.toByte(), 0x08.toByte(), 0x0a.toByte(), 0x18.toByte(), 0x92.toByte(), 0x7a.toByte(), 0x0a.toByte(),
            0x86.toByte(), 0x37.toByte(), 0x26.toByte(), 0xb0.toByte(), 0x53.toByte(), 0x49.toByte(), 0x50.toByte(),
            0x2f.toByte(), 0x32.toByte(), 0x2e.toByte(), 0x30.toByte(), 0x20.toByte(), 0x31.toByte(), 0x30.toByte(),
            0x30.toByte(), 0x20.toByte(), 0x54.toByte(), 0x72.toByte(), 0x79.toByte(), 0x69.toByte(), 0x6e.toByte(),
            0x67.toByte(), 0x0a.toByte()
        )

        // Payload: TCP (SIP message - part 2)
        val PACKET_3 = byteArrayOf(
            0x13.toByte(), 0xc4.toByte(), 0x9d.toByte(), 0x70.toByte(), 0xd3.toByte(), 0x7e.toByte(), 0xa1.toByte(),
            0xba.toByte(), 0x47.toByte(), 0x16.toByte(), 0xd2.toByte(), 0x7e.toByte(), 0x80.toByte(), 0x18.toByte(),
            0x80.toByte(), 0x00.toByte(), 0xf3.toByte(), 0xf6.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x01.toByte(), 0x08.toByte(), 0x0a.toByte(), 0x18.toByte(), 0x92.toByte(), 0x7a.toByte(), 0x0a.toByte(),
            0x86.toByte(), 0x37.toByte(), 0x26.toByte(), 0xb0.toByte(), 0x0d.toByte(), 0x0a.toByte()
        )

        // Payload: TCP (SMPP message - entire)
        val PACKET_4 = byteArrayOf(
            0x13.toByte(), 0xc4.toByte(), 0x9d.toByte(), 0x70.toByte(), 0xd3.toByte(), 0x7e.toByte(), 0xa1.toByte(),
            0xba.toByte(), 0x47.toByte(), 0x16.toByte(), 0xd2.toByte(), 0x7e.toByte(), 0x80.toByte(), 0x18.toByte(),
            0x80.toByte(), 0x00.toByte(), 0xf3.toByte(), 0xf6.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x01.toByte(), 0x08.toByte(), 0x0a.toByte(), 0x18.toByte(), 0x92.toByte(), 0x7a.toByte(), 0x0a.toByte(),
            0x86.toByte(), 0x37.toByte(), 0x26.toByte(), 0xb0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x10.toByte(), 0x80.toByte(), 0x00.toByte(), 0x00.toByte(), 0x15.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x04.toByte(), 0x58.toByte(), 0xae.toByte()
        )
    }

    @Test
    fun `Parse TCP - SIP message fits in single packet`() {
        val packetSlot = slot<Packet>()
        mockkConstructor(SipHandler::class)
        every {
            anyConstructed<SipHandler>().handle(capture(packetSlot))
        } just Runs
        runTest(
            deploy = {
                vertx.deployTestVerticle(TcpHandler::class)
            },
            execute = {
                val packet = Packet().apply {
                    srcAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
                    dstAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
                    payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_1))
                }
                vertx.eventBus().localSend(RoutesCE.tcp, listOf(packet))
            },
            assert = {
                vertx.setPeriodic(300L, 500L) {
                    if (!packetSlot.isCaptured) return@setPeriodic

                    context.verify {
                        verify(timeout = 20000) { anyConstructed<SipHandler>().handle(any()) }
                        val packet = packetSlot.captured
                        assertEquals(5060, packet.srcPort)
                        assertEquals(40304, packet.dstPort)
                        val buffer = (packet.payload as Encodable).encode()
                        assertEquals(21, buffer.readableBytes())
                    }
                    context.completeNow()
                }
            }
        )
    }

    @Test
    fun `Parse TCP - Single SIP message is split between 2 packets`() {
        val packetSlot = slot<Packet>()
        mockkConstructor(SipHandler::class)
        every {
            anyConstructed<SipHandler>().handle(capture(packetSlot))
        } just Runs
        runTest(
            deploy = {
                vertx.deployTestVerticle(TcpHandler::class)
            },
            execute = {
                val packet1 = Packet().apply {
                    srcAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
                    dstAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
                    payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_2))
                }
                val packet2 = Packet().apply {
                    srcAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
                    dstAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
                    payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_3))
                }
                vertx.eventBus().localSend(RoutesCE.tcp, listOf(packet1, packet2))
            },
            assert = {
                vertx.setPeriodic(300L, 500L) {
                    if (!packetSlot.isCaptured) return@setPeriodic

                    context.verify {
                        verify(timeout = 20000) { anyConstructed<SipHandler>().handle(any()) }
                        val packet = packetSlot.captured
                        assertEquals(5060, packet.srcPort)
                        assertEquals(40304, packet.dstPort)
                        val buffer = (packet.payload as Encodable).encode()
                        assertEquals(21, buffer.readableBytes())
                    }
                    context.completeNow()
                }
            }
        )
    }

    @Test
    fun `Parse TCP - SMPP message fits in single packet`() {
        val packetSlot = slot<Packet>()
        mockkConstructor(SmppHandler::class)
        every {
            anyConstructed<SmppHandler>().handle(capture(packetSlot))
        } just Runs
        runTest(
            deploy = {
                vertx.deployTestVerticle(TcpHandler::class, config = JsonObject().apply {
                    put("smpp", JsonObject().apply {
                        put("enabled", true)
                    })
                })
            },
            execute = {
                val packet = Packet().apply {
                    srcAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
                    dstAddr = byteArrayOf(0x0a.toByte(), 0xfa.toByte(), 0xf4.toByte(), 0x05.toByte())
                    payload = ByteBufPayload(Unpooled.wrappedBuffer(PACKET_4))
                }
                vertx.eventBus().localSend(RoutesCE.tcp, listOf(packet))
            },
            assert = {
                vertx.setPeriodic(300L, 500L) {
                    if (!packetSlot.isCaptured) return@setPeriodic

                    context.verify {
                        verify(timeout = 20000) { anyConstructed<SmppHandler>().handle(any()) }
                        val packet = packetSlot.captured
                        assertEquals(5060, packet.srcPort)
                        assertEquals(40304, packet.dstPort)
                        val buffer = (packet.payload as Encodable).encode()
                        assertEquals(16, buffer.readableBytes())
                    }
                    context.completeNow()
                }
            }
        )
    }

    @AfterEach
    fun `Unmock all`() {
        unmockkAll()
    }
}