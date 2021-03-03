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

import java.util.stream.Collectors


class GraphQLQueryRequest(private val query: GraphQLQuery, private val projection: BaseProjectionNode?) {

    constructor(query: GraphQLQuery) : this(query, null)

    fun serialize(): String {
        val builder = StringBuilder()
        builder.append(query.getOperationType()).append(" {")
        builder.append(query.getOperationName())
        val input: Map<String, Any?> = query.input
        if (input.isNotEmpty()) {
            builder.append("(")
            val inputEntryIterator = input.entries.iterator()
            while (inputEntryIterator.hasNext()) {
                val inputEntry = inputEntryIterator.next()
                if (inputEntry.value != null) {
                    builder.append(inputEntry.key)
                    builder.append(": ")
                    if (inputEntry.value is String) {
                        builder.append("\"")
                        builder.append(inputEntry.value.toString())
                        builder.append("\"")
                    } else if (inputEntry.value is List<*>) {
                        val listValues = inputEntry.value as List<*>
                        if (listValues.isNotEmpty() && listValues[0] is String) {
                            builder.append("[")
                            val elements = inputEntry.value as List<String>
                            val result: String = elements.stream()
                                    .collect(Collectors.joining("\", \"", "\"", "\""))
                            builder.append(result)
                            builder.append("]")
                        } else {
                            builder.append(inputEntry.value.toString())
                        }
                    } else {
                        builder.append(inputEntry.value.toString())
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