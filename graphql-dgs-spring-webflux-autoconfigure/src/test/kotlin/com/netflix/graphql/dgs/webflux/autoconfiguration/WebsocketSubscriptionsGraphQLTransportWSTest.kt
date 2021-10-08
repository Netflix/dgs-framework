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
import com.netflix.graphql.types.subscription.websockets.CloseCode
import com.netflix.graphql.types.subscription.websockets.Message
import com.netflix.graphql.types.subscription.websockets.MessageType
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
import org.springframework.web.reactive.socket.CloseStatus
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
    classes = [HttpHandlerAutoConfiguration::class, ReactiveWebServerFactoryAutoConfiguration::class, WebFluxAutoConfiguration::class, DgsWebFluxAutoConfiguration::class, DgsAutoConfiguration::class, WebsocketSubscriptionsGraphQLTransportWSTest.ExampleSubscriptionImplementation::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
open class WebsocketSubscriptionsGraphQLTransportWSTest(@param:LocalServerPort val port: Int) {

    @Test
    fun `Basic subscription flow`() {

        val client: WebSocketClient = ReactorNettyWebSocketClient()
        val url = URI("ws://localhost:$port/subscriptions")
        val output: Sinks.Many<Message> = Sinks.many().replay().all()

        val query = "subscription {ticker}"
        val execute = clientExecute(client, url, output, query, null)
        StepVerifier.create(execute).expectComplete().verify()

        StepVerifier.create(output.asFlux().map { (it as Message.NextMessage).payload.toString() })
            .expectNext("ExecutionResult(data={ticker=1}, errors=[])")
            .expectNext("ExecutionResult(data={ticker=2}, errors=[])")
            .expectNext("ExecutionResult(data={ticker=3}, errors=[])")
            .verifyComplete()
    }

    @Test
    fun `Subscription with error flow`() {

        val client: WebSocketClient = ReactorNettyWebSocketClient()
        val url = URI("ws://localhost:$port/subscriptions")
        val output: Sinks.Many<Message> = Sinks.many().replay().all()

        val query = "subscription {withError}"
        val execute = clientExecute(client, url, output, query, null)

        StepVerifier.create(execute).expectComplete().verify()

        StepVerifier.create(
            output.asFlux().map {
                if (it.type == MessageType.NEXT) { (it as Message.NextMessage).payload.toString() } else (it as Message.ErrorMessage).payload.toString()
            }
        )
            .expectNext("ExecutionResult(data={withError=1}, errors=[])")
            .expectNext("ExecutionResult(data={withError=2}, errors=[])")
            .expectNext("ExecutionResult(data={withError=3}, errors=[])")
            .expectNext("[{message=Broken producer, locations=[], errorType=DataFetchingException, path=null, extensions=null}]")
            .verifyError()
    }

    @Test
    fun `Client stops subscription`() {

        val client: WebSocketClient = ReactorNettyWebSocketClient()
        val url = URI("ws://localhost:$port/subscriptions")
        val output: Sinks.Many<Message> = Sinks.many().replay().all()

        val query = "subscription {withDelay}"
        val execute = clientExecute(client, url, output, query, 2)

        StepVerifier.create(execute).expectComplete().verify()

        StepVerifier.create(output.asFlux().map { (it as Message.NextMessage).payload.toString() })
            .expectNext("ExecutionResult(data={withDelay=1}, errors=[])")
            .expectNext("ExecutionResult(data={withDelay=2}, errors=[])")
            .verifyComplete()
    }

    @Test
    fun `Multiple connection init error`() {

        val client: WebSocketClient = ReactorNettyWebSocketClient()
        val url = URI("ws://localhost:$port/subscriptions")
        val output: Sinks.Many<Message> = Sinks.many().replay().all()

        val query = "subscription {ticker}"
        val execute = clientExecute(client, url, output, query, sendMultipleConnectionInit = true)
        StepVerifier.create(execute).expectComplete().verify()

        StepVerifier.create(output.asFlux().map {})
            .expectErrorMatches { e -> e is CustomCloseException && e.closeCode == CloseCode.TooManyInitialisationRequests.code }
            .verify()
    }

    @Test
    fun `Delayed connection init error`() {

        val client: WebSocketClient = ReactorNettyWebSocketClient()
        val url = URI("ws://localhost:$port/subscriptions")
        val output: Sinks.Many<Message> = Sinks.many().replay().all()
        val execute = clientExecuteDelayedConnectionInit(client, url, output)
        val timeout = DgsWebfluxConfigurationProperties.DEFAULT_CONNECTION_INIT_TIMEOUT.trimEnd('s').toLong() + 2
        StepVerifier.withVirtualTime { execute }
            .thenAwait(Duration.ofSeconds(timeout))
            .expectComplete()
            .verify()

        StepVerifier.create(output.asFlux().map {})
            .expectErrorMatches { e -> e is CustomCloseException && e.closeCode == CloseCode.ConnectionInitialisationTimeout.code }
            .verify()
    }

    @Test
    fun `Multiple subscriptions error`() {

        val client: WebSocketClient = ReactorNettyWebSocketClient()
        val url = URI("ws://localhost:$port/subscriptions")
        val output: Sinks.Many<Message> = Sinks.many().replay().all()

        val query = "subscription {tickerRunning}"
        val execute = clientExecute(client, url, output, query, sendDuplicateSubscriptionRequest = true)
        StepVerifier.create(execute).expectComplete().verify()

        StepVerifier.create(output.asFlux().map {})
            .expectErrorMatches { e -> e is CustomCloseException && e.closeCode == CloseCode.SubscriberAlreadyExists.code }
            .verify()
    }

    private fun registerCloseHandler(session: WebSocketSession, output: Sinks.Many<Message>) {
        session.closeStatus()
            .defaultIfEmpty(CloseStatus.NO_STATUS_CODE)
            .doOnNext { closeStatus ->
                if (! closeStatus.code.equals(CloseStatus.NORMAL)) {
                    output.emitError(
                        CustomCloseException(closeStatus.code),
                        Sinks.EmitFailureHandler.FAIL_FAST
                    )
                }
            }
            .doOnError {
                output.emitError(RuntimeException(it.message), Sinks.EmitFailureHandler.FAIL_FAST)
            }
            .log()
            .subscribe()
    }

    private fun clientExecuteDelayedConnectionInit(
        client: WebSocketClient,
        url: URI,
        output: Sinks.Many<Message>
    ) =
        client.execute(
            url,
            object : WebSocketHandler {
                override fun getSubProtocols(): List<String> {
                    return listOf("graphql-transport-ws")
                }

                override fun handle(session: WebSocketSession): Mono<Void> {
                    registerCloseHandler(session, output)
                    var pingMessage: Publisher<WebSocketMessage> = Mono.just(toWebsocketMessage(Message.PingMessage(), session))
                    return session.send(pingMessage).thenMany(
                        session.receive().flatMap {
                            Flux.just(toWebsocketMessage(Message.PingMessage(), session))
                        }
                    ).then()
                }
            }
        )

    private fun clientExecute(
        client: WebSocketClient,
        url: URI,
        output: Sinks.Many<Message>,
        query: String,
        stopAfter: Int? = null,
        sendMultipleConnectionInit: Boolean = false,
        sendDuplicateSubscriptionRequest: Boolean = false
    ) =
        client.execute(
            url,
            object : WebSocketHandler {
                override fun getSubProtocols(): List<String> {
                    return listOf("graphql-transport-ws")
                }

                override fun handle(session: WebSocketSession): Mono<Void> {
                    registerCloseHandler(session, output)

                    var counter = 0
                    var clientConnectionInitRequest: Publisher<WebSocketMessage> = if (sendMultipleConnectionInit) {
                        Flux.just(toWebsocketMessage(Message.ConnectionInitMessage(), session), toWebsocketMessage(Message.ConnectionInitMessage(), session))
                    } else {
                        Mono.just(toWebsocketMessage(Message.ConnectionInitMessage(), session))
                    }
                    return session.send(clientConnectionInitRequest).thenMany(
                        session.receive().flatMap { message ->
                            val buffer: DataBuffer = DataBufferUtils.retain(message.payload)
                            val operationMessage: Message = decoder.decode(
                                buffer,
                                resolvableType,
                                MimeTypeUtils.APPLICATION_JSON,
                                null
                            ) as Message

                            when (operationMessage) {
                                is Message.ConnectionAckMessage -> {
                                    var subscriptionRequest: Publisher<WebSocketMessage>
                                    if (sendDuplicateSubscriptionRequest) {
                                        subscriptionRequest = Flux.just(
                                            toWebsocketMessage(
                                                Message.SubscribeMessage(
                                                    id = "1",
                                                    Message.SubscribeMessage.Payload(query = query)
                                                ),
                                                session
                                            ),
                                            toWebsocketMessage(
                                                Message.SubscribeMessage(
                                                    id = "1",
                                                    Message.SubscribeMessage.Payload(query = query)
                                                ),
                                                session
                                            )
                                        )
                                    } else {
                                        subscriptionRequest = Mono.just(
                                            toWebsocketMessage(
                                                Message.SubscribeMessage(
                                                    id = "1",
                                                    Message.SubscribeMessage.Payload(query = query)
                                                ),
                                                session
                                            )
                                        )
                                    }
                                    session.send(subscriptionRequest)
                                }
                                is Message.CompleteMessage -> {
                                    output.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST)
                                    session.close()
                                }
                                is Message.ErrorMessage -> {
                                    output.emitNext(operationMessage, Sinks.EmitFailureHandler.FAIL_FAST)
                                    output.emitError(RuntimeException(), Sinks.EmitFailureHandler.FAIL_FAST)
                                    session.close()
                                }
                                is Message.NextMessage -> {
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
                                else -> {
                                    Flux.empty()
                                }
                            }
                        }
                    ).log().then()
                }
            },
        )

    private val resolvableType = ResolvableType.forType(Message::class.java)
    private val decoder = Jackson2JsonDecoder()
    private val encoder = Jackson2JsonEncoder(decoder.objectMapper)

    private fun toWebsocketMessage(operationMessage: Message, session: WebSocketSession): WebSocketMessage {
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

    data class CustomCloseException(val closeCode: Int) : RuntimeException()

    @DgsComponent
    class ExampleSubscriptionImplementation {

        @DgsTypeDefinitionRegistry
        fun typeDefinitionRegistry(): TypeDefinitionRegistry {
            return SchemaParser().parse(
                """
                type Subscription {
                    ticker: Int
                    tickerRunning: Int
                    withError: Int
                    withDelay: Int
                }
                """.trimIndent()
            )
        }

        @DgsSubscription
        fun tickerRunning(): Publisher<Int> {
            return Flux.interval(Duration.ofSeconds(0), Duration.ofSeconds(1)).map { 100 }
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
