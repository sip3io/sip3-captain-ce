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

package io.sip3.captain.ce.socket

import io.sip3.captain.ce.RoutesCE
import io.sip3.commons.domain.media.MediaControl
import io.sip3.commons.vertx.annotations.ConditionalOnProperty
import io.sip3.commons.vertx.annotations.Instance
import io.sip3.commons.vertx.util.closeAndExitProcess
import io.sip3.commons.vertx.util.localPublish
import io.sip3.commons.vertx.util.setPeriodic
import io.vertx.core.AbstractVerticle
import io.vertx.core.datagram.DatagramSocket
import io.vertx.core.json.JsonObject
import mu.KotlinLogging
import java.net.URI
import java.nio.charset.Charset

/**
 * Management socket
 */
@Instance(singleton = true)
@ConditionalOnProperty("/management")
class ManagementSocket : AbstractVerticle() {

    private val logger = KotlinLogging.logger {}

    companion object {

        const val TYPE_SHUTDOWN = "shutdown"
        const val TYPE_REGISTER = "register"
        const val TYPE_MEDIA_CONTROL = "media_control"
        const val TYPE_MEDIA_RECORDING_RESET = "media_recording_reset"
    }

    lateinit var uri: URI
    private var registerDelay: Long = 60000

    private var udp: DatagramSocket? = null

    override fun start() {
        config().getJsonObject("management").let { config ->
            uri = URI(config.getString("uri") ?: throw IllegalArgumentException("uri"))
            config.getLong("register-delay")?.let { registerDelay = it }
        }

        when (uri.scheme) {
            "udp" -> startUdpSocket()
            else -> throw NotImplementedError("Unknown protocol: $uri")
        }
    }

    private fun startUdpSocket() {
        udp = vertx.createDatagramSocket().handler { packet ->
            val buffer = packet.data()
            try {
                val message = buffer.toJsonObject()
                handle(message)
            } catch (e: Exception) {
                logger.error("ManagementSocket 'handle()' failed. Message: ${buffer.toString(Charset.defaultCharset())}", e)
            }
        }

        vertx.setPeriodic(0, registerDelay) {
            val registerMessage = JsonObject().apply {
                put("type", TYPE_REGISTER)
                put("payload", JsonObject().apply {
                    put("timestamp", System.currentTimeMillis())
                    put("name", deploymentID())
                    put("config", config())
                })
            }

            udp?.send(registerMessage.toBuffer(), uri.port, uri.host) { asr ->
                if (asr.failed()) {
                    logger.error(asr.cause()) { "DatagramSocket 'send()' failed." }
                }
            }
        }
    }

    private fun handle(message: JsonObject) {
        val type = message.getString("type")

        when (type) {
            TYPE_SHUTDOWN -> {
                message.getJsonObject("payload")?.getString("name")?.let { name ->
                    if (name == config().getJsonObject("host")?.getString("name") || name == deploymentID()) {
                        logger.warn { "Shutting down the process via management socket: $message" }
                        vertx.closeAndExitProcess()
                    }
                }
            }
            TYPE_MEDIA_CONTROL -> {
                val mediaControl = message.getJsonObject("payload").mapTo(MediaControl::class.java)
                vertx.eventBus().localPublish(RoutesCE.media + "_control", mediaControl)
            }
            TYPE_MEDIA_RECORDING_RESET -> {
                logger.info { "Media recording reset via management socket: $message" }
                vertx.eventBus().localPublish(RoutesCE.media + "_recording_reset", message.getJsonObject("payload"))
            }

            else -> logger.error("Unknown message type. Message: ${message.encodePrettily()}")
        }
    }
}