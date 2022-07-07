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

package com.netflix.graphql.dgs.subscriptions.sse

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.types.subscription.QueryPayload
import com.netflix.graphql.types.subscription.SSEDataPayload
import graphql.ExecutionResult
import graphql.InvalidSyntaxError
import graphql.language.OperationDefinition
import graphql.parser.InvalidSyntaxException
import graphql.parser.Parser
import graphql.validation.ValidationError
import org.reactivestreams.Publisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerErrorException
import org.springframework.web.server.ServerWebInputException
import reactor.core.publisher.Flux
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import com.netflix.graphql.types.subscription.Error as SseError

/**
 * This class is defined as "open" only for proxy/aop use cases. It is not considered part of the API, and backwards compatibility is not guaranteed.
 * Do not manually extend this class.
 */
@RestController
open class DgsSSESubscriptionHandler(open val dgsQueryExecutor: DgsQueryExecutor) {

    @GetMapping("\${dgs.graphql.sse.path:/subscriptions}", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscriptionWithId(@RequestParam("query") queryBase64: String): Flux<ServerSentEvent<String>> {
        val query = try {
            String(Base64.getDecoder().decode(queryBase64), StandardCharsets.UTF_8)
        } catch (ex: IllegalArgumentException) {
            throw ServerWebInputException("Error decoding base64-encoded query")
        }
        return handleSubscription(query)
    }

    @PostMapping("\${dgs.graphql.sse.path:/subscriptions}", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscriptionFromPost(
        @RequestBody body: String
    ): Flux<ServerSentEvent<String>> {
        return handleSubscription(body)
    }

    private fun handleSubscription(query: String): Flux<ServerSentEvent<String>> {
        val queryPayload = try {
            mapper.readValue(query, QueryPayload::class.java)
        } catch (ex: Exception) {
            throw ServerWebInputException("Error parsing query: ${ex.message}")
        }

        if (!isSubscriptionQuery(queryPayload.query)) {
            throw ServerWebInputException("Invalid query. operation type is not a subscription")
        }

        val executionResult: ExecutionResult = dgsQueryExecutor.execute(queryPayload.query, queryPayload.variables)
        if (executionResult.errors.isNotEmpty()) {
            val errorMessage = if (executionResult.errors.any { error -> error is ValidationError || error is InvalidSyntaxError }) {
                "Subscription query failed to validate: ${executionResult.errors.joinToString()}"
            } else {
                "Error executing subscription query: ${executionResult.errors.joinToString()}"
            }
            logger.error(errorMessage)
            throw ServerWebInputException(errorMessage)
        }

        val publisher = try {
            executionResult.getData<Publisher<ExecutionResult>>()
        } catch (exc: ClassCastException) {
            logger.error(
                "Invalid return type for subscription datafetcher. A subscription datafetcher must return a Publisher<ExecutionResult>. The query was {}",
                query, exc
            )
            throw ServerErrorException("Invalid return type for subscription datafetcher. Was a non-subscription query send to the subscription endpoint?", exc)
        }

        val subscriptionId = if (queryPayload.key == "") {
            UUID.randomUUID().toString()
        } else {
            queryPayload.key
        }
        return Flux.from(publisher)
            .map {
                val payload = SSEDataPayload(data = it.getData(), errors = it.errors, subId = subscriptionId)
                ServerSentEvent.builder(mapper.writeValueAsString(payload))
                    .id(UUID.randomUUID().toString())
                    .event("next")
                    .build()
            }.onErrorResume { exc ->
                logger.warn("An exception occurred on subscription {}", subscriptionId, exc)
                val errorMessage = exc.message ?: "An exception occurred"
                val payload = SSEDataPayload(data = null, errors = listOf(SseError(errorMessage)), subId = subscriptionId)
                Flux.just(
                    ServerSentEvent.builder(mapper.writeValueAsString(payload))
                        .id(UUID.randomUUID().toString())
                        .event("error")
                        .build()
                )
            }
    }

    private fun isSubscriptionQuery(query: String): Boolean {
        val document = try {
            Parser().parseDocument(query)
        } catch (exc: InvalidSyntaxException) {
            return false
        }
        val definitions = document.getDefinitionsOfType(OperationDefinition::class.java)
        return definitions.isNotEmpty() &&
            definitions.all { def -> def.operation == OperationDefinition.Operation.SUBSCRIPTION }
    }

    companion object {
        private val mapper = jacksonObjectMapper()
        private val logger: Logger = LoggerFactory.getLogger(DgsSSESubscriptionHandler::class.java)
    }
}
