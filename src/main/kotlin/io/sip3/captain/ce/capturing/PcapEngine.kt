/*
 * Copyright 2018-2021 SIP3.IO, Inc.
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

import io.micrometer.core.instrument.Metrics
import io.netty.buffer.Unpooled
import io.sip3.captain.ce.domain.Packet
import io.sip3.captain.ce.pipeline.EthernetHandler
import io.sip3.captain.ce.pipeline.Ipv4Handler
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.vertx.annotations.ConditionalOnProperty
import io.sip3.commons.vertx.annotations.Instance
import io.vertx.core.AbstractVerticle
import mu.KotlinLogging
import org.pcap4j.core.*
import org.springframework.boot.devtools.filewatch.ChangedFile
import org.springframework.boot.devtools.filewatch.ChangedFiles
import org.springframework.boot.devtools.filewatch.FileSystemWatcher
import java.io.File
import java.nio.ByteBuffer
import java.sql.Timestamp
import java.util.concurrent.Executors
import kotlin.system.exitProcess

/**
 * Libpcap, WinPcap and Npcap capture engine
 */
@Instance(singleton = true)
@ConditionalOnProperty("/pcap")
class PcapEngine : AbstractVerticle() {

    private val logger = KotlinLogging.logger {}

    companion object {

        const val SNAP_LENGTH = 65535

        val DATA_LINK_TYPES = setOf(
            "EN10MB",
            "RAW"
        )
    }

    var dir: String? = null
    var dev: String? = null
    var dlt = "EN10MB"
    var bpfFilter = ""
    var bufferSize = 2097152
    var bulkSize = 256
    var timeoutMillis = 1
    private var useJniLib = false

    private lateinit var ethernetHandler: EthernetHandler
    private lateinit var ipv4Handler: Ipv4Handler

    private val packetsCaptured = Metrics.counter("packets_captured", "source", "pcap")

    init {
        try {
            System.loadLibrary("sip3-libpcap")
            useJniLib = true
            logger.info("Loaded `sip3-libpcap` JNI library.")
        } catch (t: Throwable) {
            // Do nothing...
        }
    }

    override fun start() {
        config().getJsonObject("pcap").let { config ->
            config.getString("dir")?.let {
                dir = it
            }
            config.getString("dev")?.let {
                dev = it
            }
            config.getString("dlt")?.let {
                require(DATA_LINK_TYPES.contains(it)) { "Unsupported datalink type: $it" }
                dlt = it
            }
            config.getString("bpf-filter")?.let {
                bpfFilter = it
            }
            config.getInteger("buffer-size")?.let {
                bufferSize = it
            }
            config.getInteger("bulk-size")?.let {
                bulkSize = it
            }
            config.getInteger("timeout-millis")?.let {
                timeoutMillis = it
            }
        }

        ethernetHandler = EthernetHandler(vertx.orCreateContext, true)
        ipv4Handler = Ipv4Handler(vertx.orCreateContext, true)

        dir?.let {
            logger.info("Listening folder: $it")
            offline()
        }
        dev?.let {
            logger.info("Listening network interface: $it")
            online()
        }
    }

    private fun offline() {
        // Standard java `WatchService` is not capable to see changes in mounted volumes,
        // that's why we use `FileSystemWatcher` from spring-boot-devtools.
        val watcher = FileSystemWatcher()
        watcher.addSourceDirectory(File(dir!!))
        watcher.addListener { changedFiles ->
            changedFiles.flatMap(ChangedFiles::getFiles)
                .map(ChangedFile::getFile)
                .forEach { file ->
                    if (file.exists()) {
                        logger.info("Started file reading: $file")
                        val handle = Pcaps.openOffline(file.absolutePath)
                        vertx.executeBlocking<Any>({
                            try {
                                handle.loop()
                            } catch (e: Exception) {
                                logger.error("Got exception...", e)
                            }
                        }, {
                            handle.breakLoop()
                            logger.info("Finished file reading: $file")
                        })
                    }
                }
        }
        watcher.start()
    }

    private fun online() {
        if (useJniLib) {
            val handle = object : PacketHandle() {

                override fun onPacket(packet: Packet) {
                    packetsCaptured.increment()
                    handle(packet)
                }
            }

            // Vert.x asks to execute long blocking operations in separate application thread.
            Executors.newSingleThreadExecutor().execute {
                try {
                    handle.loop(dev!!, bpfFilter, bufferSize, bulkSize, timeoutMillis)
                } catch (t: Throwable) {
                    logger.error("Got exception...", t)
                    exitProcess(-1)
                }
            }
        } else {
            val handle = PcapHandle.Builder(dev)
                .promiscuousMode(PcapNetworkInterface.PromiscuousMode.PROMISCUOUS)
                .snaplen(SNAP_LENGTH)
                .bufferSize(bufferSize)
                .timeoutMillis(timeoutMillis)
                .build()

            // Vert.x asks to execute long blocking operations in separate application thread.
            Executors.newSingleThreadExecutor().execute {
                try {
                    handle.loop()
                } catch (e: Exception) {
                    logger.error("Got exception...", e)
                    exitProcess(-1)
                }
            }
        }
    }

    fun PcapHandle.loop() {
        if (bpfFilter.isNotEmpty()) {
            setFilter(bpfFilter, BpfProgram.BpfCompileMode.OPTIMIZE)
        }
        loop(0, (RawPacketListener { buffer ->
            packetsCaptured.increment()

            val packet = Packet().apply {
                this.timestamp = getTimestamp()
                this.payload = ByteBufPayload(Unpooled.wrappedBuffer(buffer))
            }
            handle(packet)
        }), Executors.newSingleThreadExecutor())
    }

    private fun handle(packet: Packet) {
        when (dlt) {
            "EN10MB" -> ethernetHandler.handle(packet)
            "RAW" -> ipv4Handler.handle(packet)
        }
    }
}

/**
 * Represents `sip3-libpcap` JNI interface
 */
abstract class PacketHandle {

    private val logger = KotlinLogging.logger {}

    private lateinit var packets: Array<ByteBuffer>

    external fun loop(dev: String, bpfFilter: String, bufferSize: Int, bulkSize: Int, timeoutMillis: Int)

    fun init(packets: Array<ByteBuffer>) {
        this.packets = packets
    }

    fun dispatch(sec: Long, usec: Int, bulkSize: Int) {
        val timestamp = Timestamp(sec * 1000 + usec / 1000).apply { nanos += usec % 1000 }

        packets.take(bulkSize).forEach { buffer ->
            val packet = Packet().apply {
                this.timestamp = timestamp
                this.payload = ByteBufPayload(Unpooled.wrappedBuffer(buffer))
            }
            try {
                onPacket(packet)
            } catch (e: Exception) {
                logger.error("PacketHandle 'onPacket()' failed.", e)
            }
        }
    }

    abstract fun onPacket(packet: Packet)
}
