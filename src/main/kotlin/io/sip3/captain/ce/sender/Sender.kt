package io.sip3.captain.ce.sender

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import io.sip3.captain.ce.Routes
import io.vertx.core.AbstractVerticle
import io.vertx.core.buffer.Buffer
import io.vertx.core.datagram.DatagramSocket
import io.vertx.core.net.NetClientOptions
import io.vertx.core.net.NetSocket
import mu.KotlinLogging
import java.net.URI

/**
 * Sends encoded packets to `SIP3 Salto`.
 */
class Sender : AbstractVerticle() {

    private val logger = KotlinLogging.logger {}

    lateinit var uri: URI
    var reconnectionTimeout: Long? = null
    var isSSl = false
    var keyStore: String? = null
    var keyStorePassword: String? = null

    var udp: DatagramSocket? = null
    var tcp: NetSocket? = null

    private val packetsSent = Counter.builder("packets_sent").register(Metrics.globalRegistry)

    override fun start() {
        config().getJsonObject("sender").let { config ->
            uri = URI(config.getString("uri") ?: throw IllegalArgumentException("uri"))
            reconnectionTimeout = config.getLong("reconnection-timeout")
            config.getJsonObject("ssl")?.let { sslConfig ->
                isSSl = true
                keyStore = sslConfig.getString("key-store")
                keyStorePassword = sslConfig.getString("key-store-password")
            }
        }
        when (uri.scheme) {
            "udp" -> openUdpConnection()
            "tcp" -> openTcpConnection()
            else -> throw NotImplementedError("Unknown protocol: $uri")
        }
        vertx.eventBus().localConsumer<List<Buffer>>(Routes.sender) { event ->
            try {
                val buffers = event.body()
                send(buffers)
            } catch (e: Exception) {
                logger.error("Sender 'send()' failed.", e)
            }
        }
    }

    fun openUdpConnection() {
        logger.info("UDP connection opened: $uri")
        udp = vertx.createDatagramSocket()
    }

    fun openTcpConnection() {
        val options = NetClientOptions()
        if (isSSl) {
            options.apply {
                isSsl = true
                isTrustAll = true
            }
        }
        vertx.createNetClient(options)
                .connect(uri.port, uri.host) { asr ->
                    if (asr.succeeded()) {
                        logger.info("TCP connection opened: $uri")
                        tcp = asr.result()
                                .closeHandler {
                                    logger.info("TCP connection closed: $uri")
                                    reconnectionTimeout?.let { timeout ->
                                        vertx.setTimer(timeout) { openTcpConnection() }
                                    }
                                }
                    } else {
                        logger.error("SenderVerticle 'openTcpConnection()' failed.", asr.cause())
                        reconnectionTimeout?.let { timeout ->
                            vertx.setTimer(timeout) { openTcpConnection() }
                        }
                    }
                }
    }

    fun send(buffers: List<Buffer>) {
        packetsSent.increment(buffers.size.toDouble())

        buffers.forEach { buffer ->
            udp?.let { udp ->
                udp.send(buffer, uri.port, uri.host) {}
            }
            tcp?.let { tcp ->
                tcp.write(buffer) {}
            }
        }
    }
}