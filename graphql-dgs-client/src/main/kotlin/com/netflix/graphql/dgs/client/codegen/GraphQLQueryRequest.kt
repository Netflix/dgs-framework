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

package com.netflix.graphql.dgs.client.codegen

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.util.*

class GraphQLQueryRequest(private val query: GraphQLQuery, private val projection: BaseProjectionNode?) {

    constructor(query: GraphQLQuery) : this(query, null)

    fun serialize(): String {
        val builder = StringBuilder()
        builder.append(query.getOperationType())
        if (query.name != null) {
            builder.append(" ").append(query.name)
        }
        builder.append(" {").append(query.getOperationName())
        val input: Map<String, Any?> = query.input
        if (input.isNotEmpty()) {
            builder.append("(")
            val inputEntryIterator = input.entries.iterator()
            while (inputEntryIterator.hasNext()) {
                val (key, value) = inputEntryIterator.next()
                if (value != null) {
                    builder.append(key)
                    builder.append(": ")
                    when (value) {
                        is String,
                        is OffsetDateTime,
                        is OffsetTime,
                        is LocalDate,
                        is Locale -> {
                            builder.append("\"")
                            builder.append(value.toString())
                            builder.append("\"")
                        }
                        is List<*> -> {
                            if (value.isNotEmpty() && value[0] is String) {
                                builder.append("[")
                                val result = value.joinToString(separator = "\", \"", prefix = "\"", postfix = "\"")
                                builder.append(result)
                                builder.append("]")
                            } else {
                                builder.append(value.toString())
                            }
                        }
                        else -> builder.append(value.toString())
                    }
                }
                if (inputEntryIterator.hasNext()) {
                    builder.append(", ")
                }
            }
            builder.append(")")
        }

        if (projection is BaseSubProjectionNode<*, *>) {
            builder.append(projection.root().toString())
        } else if (projection != null) {
            builder.append(projection.toString())
        }

        builder.append(" }")
        return builder.toString()
    }
}
