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

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.types.subscription.DataPayload
import graphql.ExecutionResult
import graphql.InvalidSyntaxError
import graphql.validation.ValidationError
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * This class is defined as "open" only for proxy/aop use cases. It is not considered part of the API, and backwards compatibility is not guaranteed.
 * Do not manually extend this class.
 */
@RestController
open class DgsSSESubscriptionHandler(open val dgsQueryExecutor: DgsQueryExecutor) {
    private val mapper = jacksonObjectMapper()

    @RequestMapping("/subscriptions", produces = ["text/event-stream"])
    fun subscriptionWithId(@RequestParam("query") queryBase64: String): ResponseEntity<SseEmitter> {
        val emitter = SseEmitter(-1)
        val sessionId = UUID.randomUUID().toString()
        val query = try {
            String(Base64.getDecoder().decode(queryBase64), StandardCharsets.UTF_8)
        } catch (ex: IllegalArgumentException) {
            emitter.send("Error decoding base64 encoded query")
            emitter.complete()
            return ResponseEntity.badRequest().body(emitter)
        }

        val queryPayload = try {
            mapper.readValue(query, QueryPayload::class.java)
        } catch (ex: Exception) {
            emitter.send("Error parsing query: ${ex.message}")
            emitter.complete()
            return ResponseEntity.badRequest().body(emitter)
        }

        val executionResult: ExecutionResult = dgsQueryExecutor.execute(queryPayload.query, queryPayload.variables)
        if (executionResult.errors.isNotEmpty()) {
            return if (executionResult.errors.asSequence().filterIsInstance<ValidationError>().any() || executionResult.errors.asSequence().filterIsInstance<InvalidSyntaxError>().any()) {
                val errorMessage = "Subscription query failed to validate: ${executionResult.errors.joinToString(", ")}"
                emitter.send(errorMessage)
                emitter.complete()
                ResponseEntity.badRequest().body(emitter)
            } else {
                val errorMessage = "Error executing subscription query: ${executionResult.errors.joinToString(", ")}"
                logger.error(errorMessage)
                emitter.send(errorMessage)
                emitter.complete()
                ResponseEntity.status(500).body(emitter)
            }
        }

        val subscriber = object : Subscriber<ExecutionResult> {
            lateinit var subscription: Subscription

            override fun onSubscribe(s: Subscription) {
                logger.info("Started subscription with id {} for request {}", sessionId, queryPayload)
                subscription = s
                s.request(1)
            }

            override fun onNext(t: ExecutionResult) {
                val event = SseEmitter.event()
                    .data(mapper.writeValueAsString(DataPayload(t.getData(), t.errors)), MediaType.APPLICATION_JSON)
                    .id(UUID.randomUUID().toString())
                emitter.send(event)

                subscription.request(1)
            }

            override fun onError(t: Throwable) {
                logger.error("Error on subscription {}", sessionId, t)
                val event = SseEmitter.event()
                    .data(mapper.writeValueAsString(DataPayload(null, listOf(Error(t.message)))), MediaType.APPLICATION_JSON)

                emitter.send(event)
                emitter.completeWithError(t)
            }

            override fun onComplete() {
                emitter.complete()
            }
        }

        emitter.onError {
            logger.warn("Subscription {} had a connection error", sessionId)
            subscriber.subscription.cancel()
        }

        emitter.onTimeout {
            logger.warn("Subscription {} timed out", sessionId)
            subscriber.subscription.cancel()
        }

        val publisher = try {
            executionResult.getData<Publisher<ExecutionResult>>()
        } catch (ex: ClassCastException) {
            return if (query.contains("subscription")) {
                logger.error("Invalid return type for subscription datafetcher. A subscription datafetcher must return a Publisher<ExecutionResult>. The query was $query", ex)
                emitter.send("Invalid return type for subscription datafetcher. Was a non-subscription query send to the subscription endpoint?")
                emitter.complete()
                ResponseEntity.status(500).body(emitter)
            } else {
                logger.warn("Invalid return type for subscription datafetcher. The query sent doesn't appear to be a subscription query: $query", ex)
                emitter.send("Invalid return type for subscription datafetcher. Was a non-subscription query send to the subscription endpoint?")
                emitter.complete()
                ResponseEntity.badRequest().body(emitter)
            }
        }

        publisher.subscribe(subscriber)

        return ResponseEntity.ok(emitter)
    }

    data class QueryPayload(
        @JsonProperty("variables") val variables: Map<String, Any> = emptyMap(),
        @JsonProperty("extensions") val extensions: Map<String, Any> = emptyMap(),
        @JsonProperty("operationName") val operationName: String?,
        @JsonProperty("query") val query: String
    )

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(DgsSSESubscriptionHandler::class.java)
    }
}
