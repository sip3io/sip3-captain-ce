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

package io.sip3.captain.ce

import io.vertx.core.Verticle
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.core.deployVerticleAwait
import io.vertx.kotlin.core.deploymentOptionsOf
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

@ExtendWith(VertxExtension::class)
open class VertxTest {

    lateinit var context: VertxTestContext
    lateinit var vertx: Vertx

    fun runTest(deploy: suspend () -> Unit, execute: suspend () -> Unit, assert: suspend () -> Unit, cleanup: (() -> Unit)? = null) {
        context = VertxTestContext()
        vertx = Vertx.vertx()
        vertx.registerLocalCodec()
        GlobalScope.launch(vertx.dispatcher()) {
            assert.invoke()
            deploy.invoke()
            execute.invoke()
        }
        assertTrue(context.awaitCompletion(10, TimeUnit.SECONDS))
        cleanup?.invoke()
        if (context.failed()) {
            throw context.causeOfFailure()
        }
    }

    suspend fun Vertx.deployTestVerticle(verticle: KClass<out Verticle>, config: JsonObject = JsonObject()) {
        deployVerticleAwait(verticle.java.canonicalName, deploymentOptionsOf(config))
    }
}