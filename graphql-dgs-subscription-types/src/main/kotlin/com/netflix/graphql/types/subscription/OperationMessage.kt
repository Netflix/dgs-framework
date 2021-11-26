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

package com.netflix.graphql.types.subscription

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

// OperationMessage types
const val GQL_CONNECTION_INIT = "connection_init"
const val GQL_CONNECTION_ACK = "connection_ack"
const val GQL_CONNECTION_ERROR = "connection_error"
const val GQL_START = "start"
const val GQL_STOP = "stop"
const val GQL_DATA = "data"
const val GQL_ERROR = "error"
const val GQL_COMPLETE = "complete"
const val GQL_CONNECTION_TERMINATE = "connection_terminate"
const val GQL_CONNECTION_KEEP_ALIVE = "ka"

/** Used only when expressing the data type for SSE Subscriptions. */
const val SSE_GQL_SUBSCRIPTION_DATA = "SUBSCRIPTION_DATA"

data class OperationMessage(
    @JsonProperty("type")
    val type: String,

    @JsonProperty("payload")
    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION, defaultImpl = EmptyPayload::class)
    @JsonSubTypes(
        JsonSubTypes.Type(value = EmptyPayload::class),
        JsonSubTypes.Type(value = DataPayload::class),
        JsonSubTypes.Type(value = QueryPayload::class)
    )
    val payload: Any? = null,
    @JsonProperty("id", required = false)
    val id: String? = ""
)

sealed interface MessagePayload

object EmptyPayload : HashMap<String, Any?>(), MessagePayload {
    @JvmStatic
    @JsonCreator
    @SuppressWarnings("unused")
    fun emptyPayload(): EmptyPayload {
        return EmptyPayload
    }
}

data class DataPayload(
    @JsonProperty("data")
    val data: Any?,
    @JsonProperty("errors")
    val errors: List<Any>? = emptyList()
) : MessagePayload

data class SSEDataPayload(
    @JsonProperty("data")
    val data: Any?,
    @JsonProperty("errors")
    val errors: List<Any>? = emptyList(),
    @JsonProperty("subId")
    val subId: String,
    @JsonProperty("type")
    val type: String = SSE_GQL_SUBSCRIPTION_DATA
) : MessagePayload

data class QueryPayload(
    @JsonProperty("variables")
    val variables: Map<String, Any>? = emptyMap(),
    @JsonProperty("extensions")
    val extensions: Map<String, Any>? = emptyMap(),
    @JsonProperty("operationName")
    val operationName: String? = null,
    @JsonProperty("query")
    val query: String
) : MessagePayload

data class Error(@JsonProperty val message: String = "")
