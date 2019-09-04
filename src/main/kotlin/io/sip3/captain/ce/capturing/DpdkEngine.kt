/*
 * Copyright 2018-2019 SIP3.IO, Inc.
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

package io.sip3.captain.ce.capturing

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import io.netty.buffer.Unpooled
import io.sip3.captain.ce.domain.ByteBufPayload
import io.sip3.captain.ce.domain.DpdkPacket
import io.sip3.captain.ce.domain.Packet
import io.sip3.captain.ce.pipeline.EthernetHandler
import io.vertx.core.AbstractVerticle
import mu.KotlinLogging
import java.sql.Timestamp
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess

/**
 * DPDK capture engine
 */
class DpdkEngine : AbstractVerticle() {

    private val logger = KotlinLogging.logger {}

    init {
        try {
            System.loadLibrary("sip3-dpdk")
        } catch (t: Throwable) {
            logger.error("System 'loadLibrary()' failed. Make sure that you are using SIP3 Captain `Enterprise Edition`.", t)
            exitProcess(-1)
        }
    }

    external fun bind(port: Int, rxQueueSize: Int, bulkSize: Int)

    external fun unbind()

    var port: Int = 0
    var rxQueueSize: Int = 1
    var bulkSize: Int = 1

    private val packetsCaptured = Counter.builder("packets_captured")
            .tag("source", "dpdk")
            .register(Metrics.globalRegistry)

    private val cores = mutableMapOf<Int, Core>()

    override fun start() {
        config().getJsonObject("dpdk").let { config ->
            config.getInteger("port")?.let { port = it }
            config.getInteger("rx-queue-size")?.let { rxQueueSize = it }
            config.getInteger("bulk-size")?.let { bulkSize = it }
        }

        // Vert.x asks to execute long blocking operations in separate application thread.
        Executors.newSingleThreadExecutor().execute {
            try {
                bind(port, rxQueueSize, bulkSize)
            } catch (e: Exception) {
                logger.error("Got exception...", e)
                System.exit(-1)
            }
        }

        vertx.setPeriodic(1000) {
            // Run period task in Vert.x `worker pool` to do not block `event loop`
            vertx.executeBlocking<Any>({
                var packetsCapturedSum: Long = 0
                cores.forEach { (i, core) ->
                    packetsCapturedSum += core.packetsCaptured.getAndSet(0)
                }
                packetsCaptured.increment(packetsCapturedSum.toDouble())
            }, {})
        }
    }

    @Synchronized
    fun initDpdkCore(coreId: Int, packets: Array<DpdkPacket>) {
        cores[coreId] = Core().apply {
            this.packetsCaptured = AtomicLong(0)
            this.packets = packets
            this.ethernetHandler = EthernetHandler(vertx, true)
        }
    }

    fun onDpdkPackets(coreId: Int, packetsReceived: Long) {
        cores[coreId]?.let { core ->
            core.packetsCaptured.addAndGet(packetsReceived)

            core.packets.forEachIndexed { i, packet ->
                if (i >= packetsReceived) {
                    return@forEachIndexed
                }
                val packet = Packet().apply {
                    this.timestamp = Timestamp(packet.sec * 1000 + packet.usec / 1000).apply { nanos = packet.usec % 1000 }
                    this.payload = ByteBufPayload(Unpooled.wrappedBuffer(packet.buffer))
                }
                core.ethernetHandler.handle(packet)
            }
        }
    }

    override fun stop() {
        unbind()
    }

    class Core {

        lateinit var packetsCaptured: AtomicLong
        lateinit var packets: Array<DpdkPacket>

        lateinit var ethernetHandler: EthernetHandler
    }
}