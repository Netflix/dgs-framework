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