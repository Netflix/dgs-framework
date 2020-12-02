package com.netflix.graphql.dgs.client.codegen

import java.util.*

abstract class GraphQLQuery(val operation: String) {
    val input: MutableMap<String, Any> = LinkedHashMap()

    constructor() : this("query")

    open fun getOperationType(): String? {
        return operation
    }

    abstract fun getOperationName(): String
}