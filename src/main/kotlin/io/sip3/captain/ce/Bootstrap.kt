/*
 * Copyright 2018-2020 SIP3.IO, Inc.
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

package io.sip3.captain.ce

import io.sip3.captain.ce.capturing.DpdkEngine
import io.sip3.captain.ce.capturing.PcapEngine
import io.sip3.captain.ce.encoder.Encoder
import io.sip3.captain.ce.pipeline.Ipv4FragmentHandler
import io.sip3.captain.ce.pipeline.TcpHandler
import io.sip3.captain.ce.sender.Sender
import io.sip3.captain.ce.socket.ManagementSocket
import io.sip3.commons.vertx.AbstractBootstrap
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.eventbus.deliveryOptionsOf

val USE_LOCAL_CODEC = deliveryOptionsOf(codecName = "local", localOnly = true)

open class Bootstrap : AbstractBootstrap() {

    override val configLocations = listOf("config.location")

    override fun deployVerticles(config: JsonObject) {
        // Read `vertx.instances`
        val instances = config.getJsonObject("vertx")?.getInteger("instances") ?: 1
        // Deploy verticles
        vertx.deployVerticle(Ipv4FragmentHandler::class, config)
        vertx.deployVerticle(TcpHandler::class, config)
        vertx.deployVerticle(Encoder::class, config, instances)
        vertx.deployVerticle(Sender::class, config, instances)
        if (config.containsKey("management")) {
            vertx.deployVerticle(ManagementSocket::class, config)
        }
        if (config.containsKey("pcap")) {
            vertx.deployVerticle(PcapEngine::class, config)
        }
        if (config.containsKey("dpdk")) {
            vertx.deployVerticle(DpdkEngine::class, config)
        }
    }
}