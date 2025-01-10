/*
 * Copyright 2018-2025 SIP3.IO, Corp.
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

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.domain.IpHeader
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.util.IpUtil
import io.sip3.commons.vertx.annotations.Instance
import io.sip3.commons.vertx.collections.PeriodicallyExpiringHashMap
import io.vertx.core.AbstractVerticle
import mu.KotlinLogging
import java.util.*

/**
 * Handles IPv4 and IPv6 fragments
 */
@Instance(singleton = true)
class IpFragmentHandler : AbstractVerticle() {

    private val logger = KotlinLogging.logger {}

    private var expirationDelay: Long = 5000
    private var aggregationTimeout: Long = 30000

    private lateinit var defragmentators: PeriodicallyExpiringHashMap<String, Defragmentator>

    private lateinit var ipv4Handler: Ipv4Handler
    private lateinit var ipv6Handler: Ipv6Handler

    override fun start() {
        config().getJsonObject("ip")?.getJsonObject("fragment")?.let { config ->
            config.getLong("expiration_delay")?.let {
                expirationDelay = it
            }
            config.getLong("aggregation_timeout")?.let {
                aggregationTimeout = it
            }
        }

        defragmentators = PeriodicallyExpiringHashMap.Builder<String, Defragmentator>()
            .delay(expirationDelay)
            .period((aggregationTimeout / expirationDelay).toInt())
            .expireAt { _, defragmentator -> defragmentator.timestamp + aggregationTimeout }
            .build(vertx)

        ipv4Handler = Ipv4Handler(vertx, config(), false)
        ipv6Handler = Ipv6Handler(vertx, config(), false)

        vertx.eventBus().localConsumer<List<Pair<IpHeader, Packet>>>(RoutesCE.fragment) { event ->
            val packets = event.body()
            packets.forEach { (header, packet) ->
                try {
                    onPacket(header, packet)
                } catch (e: Exception) {
                    logger.error("IpFragmentHandler 'onPacket()' failed.", e)
                }
            }
        }
    }

    fun onPacket(header: IpHeader, packet: Packet) {
        val key = "${IpUtil.convertToString(header.srcAddr)}:${IpUtil.convertToString(header.srcAddr)}:${header.identification}"

        val defragmentator = defragmentators.getOrPut(key) {
            Defragmentator().apply {
                this.timestamp = packet.timestamp
                this.nanos = packet.nanos
            }
        }
        defragmentator.onPacket(header, (packet.payload as Encodable).encode())?.let { buffer ->
            val p = Packet().apply {
                this.timestamp = defragmentator.timestamp
                this.nanos = defragmentator.nanos
                this.srcAddr = header.srcAddr
                this.dstAddr = header.dstAddr
                this.protocolNumber = header.protocolNumber
                this.payload = ByteBufPayload(buffer)
            }

            if (header.srcAddr.size == 4) {
                ipv4Handler.routePacket(p)
            } else {
                ipv6Handler.routePacket(p)
            }

            defragmentators.remove(key)
        }
    }

    class Defragmentator {

        var timestamp: Long = 0
        var nanos: Int = 0

        private val headers = TreeMap<Int, IpHeader>()
        private val buffers = TreeMap<Int, ByteBuf>()
        private var lastFragmentReceived = false

        fun onPacket(header: IpHeader, buffer: ByteBuf): ByteBuf? {
            headers[8 * header.fragmentOffset] = header
            buffers[8 * header.fragmentOffset] = buffer
            lastFragmentReceived = lastFragmentReceived || !header.moreFragments
            // Check that last fragment received
            if (!lastFragmentReceived) {
                return null
            }
            // Check that all fragments received
            var expectedOffset = 0
            headers.forEach { (offset, header) ->
                if (offset != expectedOffset) {
                    return null
                }
                expectedOffset = offset + header.totalLength - header.headerLength
            }
            // Concat all fragments
            return Unpooled.buffer(expectedOffset).apply {
                buffers.forEach { (_, b) -> writeBytes(b) }
            }
        }
    }
}