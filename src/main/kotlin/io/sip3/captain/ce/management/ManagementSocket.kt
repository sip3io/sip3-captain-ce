/*
 * Copyright 2018-2024 SIP3.IO, Corp.
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

package io.sip3.captain.ce.management

import io.netty.buffer.ByteBufUtil
import io.sip3.captain.ce.RoutesCE
import io.sip3.commons.domain.media.MediaControl
import io.sip3.commons.vertx.annotations.ConditionalOnProperty
import io.sip3.commons.vertx.annotations.Instance
import io.sip3.commons.vertx.util.closeAndExitProcess
import io.sip3.commons.vertx.util.localPublish
import io.vertx.core.AbstractVerticle
import io.vertx.core.buffer.Buffer
import io.vertx.core.datagram.DatagramSocket
import io.vertx.core.datagram.DatagramSocketOptions
import io.vertx.core.http.WebSocket
import io.vertx.core.json.JsonObject
import io.vertx.core.net.NetSocket
import io.vertx.core.parsetools.RecordParser
import mu.KotlinLogging
import java.net.URI
import java.nio.charset.Charset

/**
 * Management socket
 */
@Instance(singleton = true)
@ConditionalOnProperty("/management")
open class ManagementSocket : AbstractVerticle() {

    private val logger = KotlinLogging.logger {}

    companion object {

        const val TYPE_SHUTDOWN = "shutdown"
        const val TYPE_REGISTER = "register"
        const val TYPE_MEDIA_CONTROL = "media_control"
        const val TYPE_MEDIA_RECORDING_RESET = "media_recording_reset"
    }

    lateinit var uri: URI
    var registerDelay: Long = 60000
    var reconnectionTimeout: Long? = null
    var delimiter = "\r\n\r\n3PIS\r\n\r\n"

    private var udp: DatagramSocket? = null
    private var tcp: NetSocket? = null
    protected var ws: WebSocket? = null

    override fun start() {
        config().getJsonObject("management").let { config ->
            uri = URI(config.getString("uri") ?: throw IllegalArgumentException("uri"))
            config.getLong("register_delay")?.let { registerDelay = it }
            config.getLong("reconnection_timeout")?.let { reconnectionTimeout = it}
            config.getString("delimiter")?.let { delimiter = it }
        }

        when (uri.scheme) {
            "udp" -> openUdpConnection()
            "tcp" -> openTcpConnection()
            "ws", "wss" -> openWsConnection()
            else -> throw NotImplementedError("Unknown protocol: $uri")
        }
    }

    open fun sendRegister(send: ((Buffer) -> Unit)) {
            val registerMessage = JsonObject().apply {
                put("type", TYPE_REGISTER)
                put("payload", JsonObject().apply {
                    put("timestamp", System.currentTimeMillis())
                    put("deployment_id", deploymentID())
                    put("config", config())
                })
            }

            send(registerMessage.toBuffer())
    }

    private fun openUdpConnection() {
        val options = DatagramSocketOptions().apply {
            isIpV6 = uri.host.matches(Regex("\\[.*]"))
        }
        udp = vertx.createDatagramSocket(options).handler { packet ->
            val buffer = packet.data()
            try {
                val message = buffer.toJsonObject()
                handle(message)
            } catch (e: Exception) {
                logger.error("ManagementSocket 'handle()' failed. Message: ${buffer.toString(Charset.defaultCharset())}", e)
            }
        }

        vertx.setPeriodic(0, registerDelay) {
            sendRegister { buffer ->
                udp?.send(buffer, uri.port, uri.host)
                    ?.onFailure { logger.error(it) { "DatagramSocket 'send()' failed." } }
            }
        }
    }

    private fun openTcpConnection() {
        vertx.createNetClient().connect(uri.port, uri.host)
            .onFailure { t ->
                logger.error(t) { "Failed to connect to $uri" }
                reconnectionTimeout?.let { timeout ->
                    vertx.setTimer(timeout) { openTcpConnection() }
                }
            }
            .onSuccess { socket ->
                val parser = RecordParser.newDelimited(delimiter) { buffer ->
                    try {
                        val message = buffer.toJsonObject()
                        handle(message)
                    } catch (e: Exception) {
                        logger.error("ManagementSocket 'handle()' failed. Message: ${buffer.toString(Charset.defaultCharset())}", e)
                    }
                }

                tcp = socket.handler { buffer ->
                    try {
                        parser.handle(buffer)
                    } catch (e: Exception) {
                        logger.error(e) { "RecordParser 'handle()' failed." }
                        logger.debug { "Sender: $uri, buffer: ${ByteBufUtil.prettyHexDump(buffer.byteBuf)}" }
                    }
                }.closeHandler {
                    logger.info("TCP connection closed: $uri")
                    reconnectionTimeout?.let { timeout ->
                        vertx.setTimer(timeout) { openTcpConnection() }
                    }
                }

                vertx.setPeriodic(0, registerDelay) {
                    sendRegister { buffer ->
                        tcp?.write(buffer.appendString(delimiter))
                            ?.onFailure { logger.error(it) { "NetSocket 'write()' failed." } }
                    }
                }
            }
    }

    open fun openWsConnection() {
        throw NotImplementedError("WebSocket transport is available in EE version")
    }

    open fun handle(message: JsonObject) {
        val type = message.getString("type")
        val payload = message.getJsonObject("payload")

        when (type) {
            TYPE_SHUTDOWN -> {
                val exitCode = payload.getInteger("exit_code") ?: -1
                if (payload.getString("deployment_id") == deploymentID()) {
                    logger.warn { "Shutting down the process via management socket: $message" }
                    vertx.closeAndExitProcess(exitCode)
                }

                payload.getString("name")?.let { name ->
                    if (name == config().getJsonObject("host")?.getString("name") || name == deploymentID()) {
                        logger.warn { "Shutting down the process via management socket: $message" }
                        vertx.closeAndExitProcess(exitCode)
                    }
                }
            }
            TYPE_MEDIA_CONTROL -> {
                val mediaControl = payload.mapTo(MediaControl::class.java)
                vertx.eventBus().localPublish(RoutesCE.media + "_control", mediaControl)
            }
            TYPE_MEDIA_RECORDING_RESET -> {
                logger.info { "Media recording reset via management socket: $message" }
                vertx.eventBus().localPublish(RoutesCE.media + "_recording_reset", payload)
            }

            else -> logger.debug {
                "Unknown message type '$type'. Message: ${message.encodePrettily()}"
            }
        }
    }
}