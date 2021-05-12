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

import graphql.schema.Coercing

class GraphQLQueryRequest(
    private val query: GraphQLQuery,
    private val projection: BaseProjectionNode?,
    private val scalars: Map<Class<*>, Coercing<*, *>>?
) {

    constructor(query: GraphQLQuery) : this(query, null, null)
    constructor(query: GraphQLQuery, projection: BaseProjectionNode?) : this(query, projection, null)

    private val inputValueSerializer = InputValueSerializer(scalars ?: emptyMap())

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
                    builder.append(inputValueSerializer.serialize(value))
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

class InputValueSerializer(private val scalars: Map<Class<*>, Coercing<*, *>> = emptyMap()) {
    fun serialize(input: Any?): String? {
        if (input == null) {
            return null
        }

        val type = input::class.java

        return if (scalars.contains(type)) {
            """"${scalars[type]!!.serialize(input)}""""
        } else if (type.isPrimitive || type == Integer::class.java || type == Long::class.java || type == Double::class.java || type == Float::class.java || type == Boolean::class.java || type == Short::class.java || type == Byte::class.java || type.isEnum) {
            input.toString()
        } else if (type == String::class.java) {
            """"$input""""
        } else if (input is List<*>) {
            """[${input.filterNotNull().joinToString(", ") { listItem -> serialize(listItem) ?: "" }}]"""
        } else {
            input.javaClass.declaredFields.filter { !it.type::class.isCompanion }.map {
                it.isAccessible = true
                val nestedValue = it.get(input)
                if (nestedValue != null && !nestedValue::class.isCompanion) {
                    """${it.name}:${serialize(nestedValue)}"""
                } else {
                    null
                }
            }.filterNotNull().joinToString(", ", "{", " }")
        }
    }
}
