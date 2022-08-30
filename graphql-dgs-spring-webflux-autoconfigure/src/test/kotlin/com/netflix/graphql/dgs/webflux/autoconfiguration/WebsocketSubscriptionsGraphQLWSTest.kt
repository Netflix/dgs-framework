/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.graphql.dgs.webflux.autoconfiguration

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsSubscription
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration
import com.netflix.graphql.types.subscription.GQL_COMPLETE
import com.netflix.graphql.types.subscription.GQL_CONNECTION_ACK
import com.netflix.graphql.types.subscription.GQL_CONNECTION_INIT
import com.netflix.graphql.types.subscription.GQL_ERROR
import com.netflix.graphql.types.subscription.GQL_START
import com.netflix.graphql.types.subscription.OperationMessage
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerFactoryAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.core.ResolvableType
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.util.MimeTypeUtils
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import org.springframework.web.reactive.socket.client.WebSocketClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier
import java.net.URI
import java.time.Duration

@EnableWebFlux
@SpringBootTest(
    classes = [HttpHandlerAutoConfiguration::class, ReactiveWebServerFactoryAutoConfiguration::class, WebFluxAutoConfiguration::class, DgsWebFluxAutoConfiguration::class, DgsAutoConfiguration::class, WebsocketSubscriptionsGraphQLWSTest.ExampleSubscriptionImplementation::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
open class WebsocketSubscriptionsGraphQLWSTest(@param:LocalServerPort val port: Int) {

    @Test
    fun `Basic subscription flow`() {

        val client: WebSocketClient = ReactorNettyWebSocketClient()
        val url = URI("ws://localhost:$port/subscriptions")
        val output: Sinks.Many<OperationMessage> = Sinks.many().replay().all()

        val query = "subscription {ticker}"
        val execute = clientExecute(client, url, output, query, null)
        StepVerifier.create(execute).expectComplete().verify()

        StepVerifier.create(output.asFlux().map { it.payload.toString() })
            .expectNext("DataPayload(data={ticker=1}, errors=[])")
            .expectNext("DataPayload(data={ticker=2}, errors=[])")
            .expectNext("DataPayload(data={ticker=3}, errors=[])")
            .verifyComplete()
    }

    @Test
    fun `Subscription with error flow`() {

        val client: WebSocketClient = ReactorNettyWebSocketClient()
        val url = URI("ws://localhost:$port/subscriptions")
        val output: Sinks.Many<OperationMessage> = Sinks.many().replay().all()

        val query = "subscription {withError}"
        val execute = clientExecute(client, url, output, query, null)

        StepVerifier.create(execute).expectComplete().verify()

        StepVerifier.create(output.asFlux().map { it.payload.toString() })
            .expectNext("DataPayload(data={withError=1}, errors=[])")
            .expectNext("DataPayload(data={withError=2}, errors=[])")
            .expectNext("DataPayload(data={withError=3}, errors=[])")
            .expectNext("DataPayload(data=null, errors=[Broken producer])")
            .verifyError()
    }

    @Test
    fun `Client stops subscription`() {

        val client: WebSocketClient = ReactorNettyWebSocketClient()
        val url = URI("ws://localhost:$port/subscriptions")
        val output: Sinks.Many<OperationMessage> = Sinks.many().replay().all()

        val query = "subscription {withDelay}"
        val execute = clientExecute(client, url, output, query, 2)

        StepVerifier.create(execute).expectComplete().verify()

        StepVerifier.create(output.asFlux().map { it.payload.toString() })
            .expectNext("DataPayload(data={withDelay=1}, errors=[])")
            .expectNext("DataPayload(data={withDelay=2}, errors=[])")
            .verifyComplete()
    }

    private fun clientExecute(
        client: WebSocketClient,
        url: URI,
        output: Sinks.Many<OperationMessage>,
        query: String,
        stopAfter: Int? = null
    ) =
        client.execute(
            url,
            object : WebSocketHandler {
                override fun getSubProtocols(): List<String> {
                    return listOf("graphql-ws")
                }

                override fun handle(session: WebSocketSession): Mono<Void> {
                    var counter = 0

                    return session.send(Mono.just(toWebsocketMessage(OperationMessage(GQL_CONNECTION_INIT), session)))
                        .thenMany(
                            session.receive().flatMap { message ->
                                val buffer: DataBuffer = DataBufferUtils.retain(message.payload)
                                val operationMessage: OperationMessage = decoder.decode(
                                    buffer,
                                    resolvableType,
                                    MimeTypeUtils.APPLICATION_JSON,
                                    null
                                ) as OperationMessage

                                when (operationMessage.type) {
                                    GQL_CONNECTION_ACK -> {

                                        session.send(
                                            Mono.just(
                                                toWebsocketMessage(
                                                    OperationMessage(
                                                        GQL_START,
                                                        mapOf("query" to query), "1"
                                                    ),
                                                    session
                                                )
                                            )
                                        )
                                    }
                                    GQL_COMPLETE -> {
                                        output.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST)
                                        session.close()
                                    }
                                    GQL_ERROR -> {
                                        output.emitNext(operationMessage, Sinks.EmitFailureHandler.FAIL_FAST)
                                        output.emitError(RuntimeException(), Sinks.EmitFailureHandler.FAIL_FAST)
                                        session.close()
                                    }
                                    else -> {
                                        counter += 1
                                        output.emitNext(operationMessage, Sinks.EmitFailureHandler.FAIL_FAST)
                                        if (stopAfter != null && counter == stopAfter) {
                                            Flux.just(operationMessage).flatMap {
                                                output.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST)
                                                session.close()
                                            }
                                        } else {
                                            Flux.just(operationMessage)
                                        }
                                    }
                                }
                            }
                        ).log().then()
                }
            },
        )

    private val resolvableType = ResolvableType.forType(OperationMessage::class.java)
    private val decoder = Jackson2JsonDecoder()
    private val encoder = Jackson2JsonEncoder()

    private fun toWebsocketMessage(operationMessage: OperationMessage, session: WebSocketSession): WebSocketMessage {
        return WebSocketMessage(
            WebSocketMessage.Type.TEXT,
            encoder.encodeValue(
                operationMessage,
                session.bufferFactory(),
                resolvableType,
                MimeTypeUtils.APPLICATION_JSON,
                null
            )
        )
    }

    @DgsComponent
    class ExampleSubscriptionImplementation {

        @DgsTypeDefinitionRegistry
        fun typeDefinitionRegistry(): TypeDefinitionRegistry {
            return SchemaParser().parse(
                """
                type Subscription {
                    ticker: Int
                    withError: Int
                    withDelay: Int
                }
                """.trimIndent()
            )
        }

        @DgsSubscription
        fun ticker(): Publisher<Int> {
            return Flux.just(1, 2, 3)
        }

        @DgsSubscription
        fun withError(): Publisher<Int> {
            return Flux.just(1, 2, 3).concatWith(Flux.error(RuntimeException("Broken producer")))
        }

        @DgsSubscription
        fun withDelay(): Publisher<Int> {
            return Flux.just(1, 2).concatWith(Mono.delay(Duration.ofSeconds(1)).map { it.toInt() })
        }
    }
}
