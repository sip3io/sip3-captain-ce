package io.sip3.captain.ce.capturing

import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Metrics
import io.netty.buffer.Unpooled
import io.sip3.captain.ce.domain.Packet
import io.sip3.captain.ce.pipeline.EthernetHandler
import io.vertx.core.AbstractVerticle
import mu.KotlinLogging
import org.pcap4j.core.*
import org.springframework.boot.devtools.filewatch.ChangedFile
import org.springframework.boot.devtools.filewatch.ChangedFiles
import org.springframework.boot.devtools.filewatch.FileSystemWatcher
import java.io.File
import java.util.concurrent.Executors

/**
 * Libpcap, WinPcap and Npcap capture engine
 */
class PcapEngine : AbstractVerticle() {

    private val logger = KotlinLogging.logger {}

    var dir: String? = null
    var dev: String? = null
    var bpfFilter: String? = null
    var timeoutMillis: Int? = null

    private val packetsCaptured = DistributionSummary.builder("packets_captured")
            .tag("source", "pcap")
            .register(Metrics.globalRegistry)

    private lateinit var ethernetHandler: EthernetHandler

    override fun start() {
        config().getJsonObject("pcap").let { config ->
            dir = config.getString("dir")
            dev = config.getString("dev")
            bpfFilter = config.getString("bpf-filter")
            timeoutMillis = config.getInteger("timeout-millis")
        }

        ethernetHandler = EthernetHandler(vertx, true)

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
        watcher.addSourceFolder(File(dir))
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
        val handle = PcapHandle.Builder(dev)
                .promiscuousMode(PcapNetworkInterface.PromiscuousMode.PROMISCUOUS)
                .snaplen(65535)
                .apply {
                    timeoutMillis?.let { timeoutMillis(it) }
                }
                .build()

        // Vert.x asks to execute long blocking operations in separate application thread.
        Executors.newSingleThreadExecutor().execute {
            try {
                handle.loop()
            } catch (e: Exception) {
                logger.error("Got exception...", e)
                System.exit(-1)
            }
        }
    }

    fun PcapHandle.loop() {
        bpfFilter?.let {
            setFilter(it, BpfProgram.BpfCompileMode.OPTIMIZE)
        }
        loop(0, (RawPacketListener { buffer ->
            packetsCaptured.record(buffer.size.toDouble())

            val packet = Packet().apply {
                this.timestamp = getTimestamp()
            }
            ethernetHandler.handle(Unpooled.wrappedBuffer(buffer), packet)
        }))
    }
}