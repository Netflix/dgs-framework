/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.graphql.dgs.logging.internal

class LogSanitizer {
    @Suppress("UNCHECKED_CAST")
    fun sanitizeMap(map: Map<String, Any>): Map<String, Any> {
        val newMap = mutableMapOf<String,Any>()

        map.forEach {
            when (it.value) {
                is String -> newMap[it.key] = "***"
                is Map<*, *> -> newMap[it.key] = sanitizeMap(it.value as Map<String,Any>)
                is List<*> -> newMap[it.key] = sanitizeList(it.value as List<Any>)
                else -> newMap[it.key] = it.value
            }
        }

        return newMap
    }

    @Suppress("UNCHECKED_CAST")
    private fun sanitizeList(list: List<*>): List<Any?> {
        return list.map {
            when (it) {
                is String -> "***"
                is Map<*,*> -> sanitizeMap(it as Map<String,Any>)
                is List<*> -> sanitizeList(it)
                else -> it
            }
        }
    }

    fun sanitizeQuery(query: String): String {
        return query.replace(":[\\u0020]?\"[a-zA-Z!?_\\s-]+\"".toRegex(), ": \"***\"")
    }
}